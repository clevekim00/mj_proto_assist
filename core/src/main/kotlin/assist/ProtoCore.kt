package assist

import com.google.protobuf.*
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors.*
import com.google.protobuf.util.JsonFormat
import java.security.MessageDigest

class ProtoCore {
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
    data class Schema(val id: String, val types: Map<String, Descriptor>, val registry: JsonFormat.TypeRegistry)
    data class Decoded(val protoJson: String, val warnings: List<String>)
    private val schemas = linkedMapOf<String, Schema>()
    private val sizes = mutableMapOf<String, Int>()
    @Synchronized fun register(bytes: ByteArray): Schema {
        require(bytes.size <= MAX_BYTES) { "SIZE_LIMIT" }
        val set = FileDescriptorSet.parseFrom(bytes)
        require(set.fileCount > 0) { "EMPTY_SCHEMA" }
        require(set.fileList.all { it.syntax == "proto3" }) { "UNSUPPORTED_SYNTAX" }
        require(set.fileList.map { it.name }.distinct().size == set.fileCount) { "DUPLICATE_FILE" }
        val pending = set.fileList.toMutableList()
        val files = mutableMapOf<String, FileDescriptor>()
        val types = linkedMapOf<String, Descriptor>()
        val symbols = mutableSetOf<String>()
        fun symbol(name: String) { require(symbols.add(name)) { "DUPLICATE_SYMBOL" } }
        fun visit(d: Descriptor) {
            symbol(d.fullName); types[d.fullName] = d
            d.enumTypes.forEach { symbol(it.fullName) }
            d.nestedTypes.forEach(::visit)
        }
        while (pending.isNotEmpty()) {
            val ready = pending.filter { f -> f.dependencyList.all { it in files } }
            require(ready.isNotEmpty()) { "MISSING_OR_CYCLIC_IMPORT" }
            ready.forEach { f ->
                require(f.extensionCount == 0 && f.messageTypeList.all { it.extensionCount == 0 }) { "UNSUPPORTED_EXTENSION" }
                val built = FileDescriptor.buildFrom(f, f.dependencyList.map { files.getValue(it) }.toTypedArray())
                built.messageTypes.forEach(::visit)
                built.enumTypes.forEach { symbol(it.fullName) }
                files[f.name] = built
            }
            pending.removeAll(ready.toSet())
        }
        val id = MessageDigest.getInstance("SHA-256").digest(bytes + byteArrayOf(1)).joinToString("") { "%02x".format(it) }
        require(schemas.size < 16 || id in schemas) { "SCHEMA_LIMIT" }
        require(sizes.values.sum().toLong() - (sizes[id] ?: 0) + bytes.size <= 64L * 1024 * 1024) { "REGISTRY_SIZE_LIMIT" }
        val schema = Schema(id, types, JsonFormat.TypeRegistry.newBuilder().add(types.values).build())
        schemas[id] = schema
        sizes[id] = bytes.size
        return schema
    }
    @Synchronized fun release(id: String) { schemas.remove(id); sizes.remove(id) }
    @Synchronized fun schema(id: String) = schemas[id] ?: error("SCHEMA_NOT_FOUND")
    private fun descriptor(s: Schema, name: String) = s.types[name] ?: error("TYPE_NOT_FOUND")
    fun encode(id: String, name: String, json: String): ByteArray {
        require(json.toByteArray().size <= MAX_BYTES) { "SIZE_LIMIT" }
        val s = schema(id)
        val builder = DynamicMessage.newBuilder(descriptor(s, name))
        JsonFormat.parser().usingTypeRegistry(s.registry).merge(json, builder)
        fun depth(m: Message, level: Int) {
            require(level <= 64) { "RECURSION_LIMIT" }
            m.allFields.forEach { (f, v) -> if (f.javaType == FieldDescriptor.JavaType.MESSAGE) {
                if (f.isRepeated) (v as List<*>).forEach { depth(it as Message, level + 1) }
                else depth(v as Message, level + 1)
            } }
        }
        depth(builder.build(), 0)
        return builder.build().toByteArray().also { require(it.size <= MAX_BYTES) { "SIZE_LIMIT" } }
    }
    fun decode(id: String, name: String, bytes: ByteArray): Decoded {
        require(bytes.size <= MAX_BYTES) { "SIZE_LIMIT" }
        val s = schema(id)
        val input = CodedInputStream.newInstance(bytes).apply { setRecursionLimit(64); setSizeLimit(MAX_BYTES) }
        val message = DynamicMessage.newBuilder(descriptor(s, name)).mergeFrom(input).build()
        input.checkLastTagWas(0)
        fun unknown(m: Message): Boolean = m.unknownFields.asMap().isNotEmpty() || m.allFields.any { (f, value) ->
            f.javaType == FieldDescriptor.JavaType.MESSAGE &&
                (if (f.isRepeated) (value as List<*>).any { unknown(it as Message) } else unknown(value as Message))
        }
        return Decoded(JsonFormat.printer().usingTypeRegistry(s.registry).print(message),
            if (unknown(message)) listOf("UNKNOWN_FIELDS_PRESENT") else emptyList())
    }
}

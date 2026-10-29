package assist.cli

import assist.ProtoCore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.system.exitProcess

class Stdio(private val core: ProtoCore = ProtoCore()) {
    private val mapper = jacksonObjectMapper()
    private val seen = mutableSetOf<String>()
    var shutdown = false; private set
    fun dispatch(line: String): String {
        var id: JsonNode? = null
        return try {
            require(line.toByteArray().size <= 16 * 1024 * 1024) { "LINE_LIMIT" }
            val request = mapper.readTree(line)
            id = request.get("id")
            require(id != null && id.isTextual && id.asText().length in 1..128) { "INVALID_ID" }
            require(seen.size < 10000) { "REQUEST_LIMIT" }
            require(seen.add(id.asText())) { "DUPLICATE_ID" }
            require(request.path("protocolMajor").asInt() == 1) { "PROTOCOL_MISMATCH" }
            val p = request.path("params")
            fun text(key: String): String = p.get(key)?.takeIf { it.isTextual }?.asText() ?: error("MISSING_FIELD")
            fun bytes(key: String): ByteArray {
                val value = text(key)
                require(value.length <= ((ProtoCore.MAX_BYTES + 2) / 3) * 4) { "SIZE_LIMIT" }
                return Base64.getDecoder().decode(value)
            }
            val result: Any = when (request.path("method").asText()) {
                "system.hello" -> mapOf("protocolMajor" to 1,"protocolMinor" to 0,"version" to "0.1.0", "capabilities" to listOf("descriptor", "encode", "decode"), "maxBytes" to ProtoCore.MAX_BYTES)
                "schema.register" -> {val s=core.register(bytes("descriptorBase64"));mapOf("schemaId" to s.id)}
                "schema.listTypes" -> mapOf("types" to core.schema(text("schemaId")).types.keys.sorted())
                "schema.inspectType" -> {
                    val d=core.schema(text("schemaId")).types[text("messageType")] ?: error("TYPE_NOT_FOUND")
                    mapOf("name" to d.fullName,"fields" to d.fields.map { mapOf("name" to it.jsonName,"number" to it.number,"type" to it.type.name,"repeated" to it.isRepeated,"presence" to it.hasPresence()) })
                }
                "schema.release" -> {core.release(text("schemaId"));mapOf("released" to true)}
                "codec.encode" -> mapOf("payloadBase64" to Base64.getEncoder().encodeToString(core.encode(text("schemaId"),text("messageType"),p.get("protoJson")?.toString() ?: error("MISSING_FIELD"))))
                "codec.decode" -> {val d=core.decode(text("schemaId"),text("messageType"),bytes("payloadBase64"));mapOf("protoJson" to mapper.readTree(d.protoJson),"warnings" to d.warnings)}
                "system.shutdown" -> {shutdown=true;mapOf("closed" to true)}
                else -> error("METHOD_NOT_SUPPORTED")
            }
            mapper.writeValueAsString(mapOf("id" to id,"result" to result))
        } catch (e: Exception) {
            val allowed = setOf("PROTOCOL_MISMATCH","DUPLICATE_ID","METHOD_NOT_SUPPORTED","LINE_LIMIT","REQUEST_LIMIT","INVALID_ID","SIZE_LIMIT")
            mapper.writeValueAsString(mapOf("id" to id,"error" to mapOf("code" to (e.message?.takeIf { it in allowed } ?: "INVALID_INPUT"),"message" to "Invalid control request, schema, type or payload", "retryable" to false)))
        }
    }
}
fun main(args: Array<String>) {
    try {
        require(args.isNotEmpty())
        if (args[0] == "serve-stdio") {
            val stdio=Stdio()
            // Read incrementally so an untrusted line cannot allocate unbounded memory.
            val line=StringBuilder()
            val reader=System.`in`.reader(Charsets.UTF_8)
            while (!stdio.shutdown) {
                val ch=reader.read()
                if (ch == -1) {if(line.isNotEmpty()) println(stdio.dispatch(line.toString()));break}
                if (ch == 10) {println(stdio.dispatch(line.toString().trimEnd('\r')));System.out.flush();line.setLength(0)}
                else {require(line.length < 16 * 1024 * 1024);line.append(ch.toChar())}
            }
            return
        }
        require(args[0] in setOf("types","encode","decode"))
        val options=args.drop(1).chunked(2).associate { require(it.size == 2);it[0] to it[1] }
        fun option(name:String)=options[name] ?: error("MISSING_OPTION")
        val c=ProtoCore();val schema=c.register(Files.readAllBytes(Path.of(option("--descriptor"))))
        if(args[0] == "types") {schema.types.keys.sorted().forEach(::println);return}
        val input=if(option("--in") == "-") System.`in`.readNBytes(ProtoCore.MAX_BYTES+1) else Files.newInputStream(Path.of(option("--in"))).use {it.readNBytes(ProtoCore.MAX_BYTES+1)}
        require(input.size <= ProtoCore.MAX_BYTES)
        val output=if(args[0] == "encode") c.encode(schema.id,option("--type"),input.toString(Charsets.UTF_8))
            else c.decode(schema.id,option("--type"),input).also {it.warnings.forEach(System.err::println)}.protoJson.toByteArray()
        if(option("--out") == "-") System.out.write(output) else Files.write(Path.of(option("--out")),output)
    } catch (_: Exception) {
        System.err.println("Invalid input. Commands: types --descriptor FILE; encode|decode --descriptor FILE --type NAME --in FILE|- --out FILE|-; serve-stdio")
        exitProcess(2)
    }
}

package assist

import com.google.protobuf.DescriptorProtos.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProtoCoreTest {
    private val fixture = Files.readAllBytes(Path.of("../examples/echo.pb"))
    private fun loaded() = ProtoCore().also { it.register(fixture) }
    @Test fun `round trip preserves int64 bytes map repeated nested optional and oneof`() {
        val core = loaded(); val id = core.register(fixture).id
        val json = """{"text":"hello","revision":"9007199254740993","payload":"AQID","tags":["a","b"],"note":"","title":"x","labels":{"k":"v"},"child":{"name":"nested"}}"""
        val result = core.decode(id,"sample.v1.Echo",core.encode(id,"sample.v1.Echo",json))
        assertTrue(result.protoJson.contains("9007199254740993")); assertTrue(result.protoJson.contains("AQID"))
        assertTrue(result.protoJson.contains("\"note\": \"\"")); assertTrue(result.protoJson.contains("nested")); assertTrue(result.warnings.isEmpty())
    }
    @Test fun `unknown JSON and conflicting oneof rejected`() {
        val c=loaded(); val id=c.register(fixture).id
        for(json in listOf("""{"secret":"hidden"}""", """{"title":"a","number":1}""")) {
            assertThrows(Exception::class.java) { c.encode(id,"sample.v1.Echo",json) }
        }
    }
    @Test fun `malformed wire and unknown type rejected`() {
        val c=loaded();val id=c.register(fixture).id
        assertThrows(Exception::class.java) { c.decode(id,"sample.v1.Echo",byteArrayOf(10,127)) }
        assertThrows(Exception::class.java) { c.encode(id,"missing.Type","{}") }
    }
    @Test fun `unknown binary field warned and valid schema survives registration failure`() {
        val c=loaded();val id=c.register(fixture).id
        assertEquals(listOf("UNKNOWN_FIELDS_PRESENT"),c.decode(id,"sample.v1.Echo",byteArrayOf(0xA0.toByte(),6,1)).warnings)
        assertThrows(Exception::class.java) { c.register(byteArrayOf(10,127)) }
        assertEquals(0,c.encode(id,"sample.v1.Echo","{}").size)
    }
    @Test fun `empty proto2 missing imports duplicate files and symbols rejected`() {
        val c=ProtoCore()
        fun file(name:String)=FileDescriptorProto.newBuilder().setName(name).setSyntax("proto3").setPackage("p")
            .addMessageType(DescriptorProto.newBuilder().setName("M"))
        val a=file("a.proto").build()
        val bad=listOf(FileDescriptorSet.getDefaultInstance(),FileDescriptorSet.newBuilder().addFile(a.toBuilder().setSyntax("proto2")).build(),
            FileDescriptorSet.newBuilder().addFile(a.toBuilder().addDependency("missing.proto")).build(),
            FileDescriptorSet.newBuilder().addFile(a).addFile(a).build(),
            FileDescriptorSet.newBuilder().addFile(a).addFile(file("b.proto")).build())
        bad.forEach { assertThrows(Exception::class.java) { c.register(it.toByteArray()) } }
    }
    @Test fun `oversized binary rejected before parsing`() {
        val c=loaded();val id=c.register(fixture).id
        assertThrows(Exception::class.java) { c.decode(id,"sample.v1.Echo",ByteArray(ProtoCore.MAX_BYTES+1)) }
    }
    @Test fun `timestamp Any and enum use registered types only`() {
        val c=ProtoCore()
        val bytes=Files.readAllBytes(Path.of("../examples/well_known.pb"))
        val id=c.register(bytes).id
        val json="""{"createdAt":"2026-10-29T00:00:00Z","detail":{"@type":"type.googleapis.com/sample.wkt.Detail","text":"synthetic"},"state":"READY"}"""
        val decoded=c.decode(id,"sample.wkt.Envelope",c.encode(id,"sample.wkt.Envelope",json))
        assertTrue(decoded.protoJson.contains("2026-10-29T00:00:00Z"))
        assertTrue(decoded.protoJson.contains("synthetic"));assertTrue(decoded.protoJson.contains("READY"))
        assertThrows(Exception::class.java) {
            c.encode(id,"sample.wkt.Envelope","""{"detail":{"@type":"type.googleapis.com/unknown.Type"}}""")
        }
    }
    @Test fun `imports resolve regardless of descriptor order and release isolates schemas`() {
        val original=FileDescriptorSet.parseFrom(Files.readAllBytes(Path.of("../examples/well_known.pb")))
        val c=ProtoCore()
        val reversed=FileDescriptorSet.newBuilder().addAllFile(original.fileList.reversed()).build()
        val id=c.register(reversed.toByteArray()).id
        assertTrue(c.schema(id).types.containsKey("sample.wkt.Envelope"))
        val echo=c.register(fixture).id
        assertThrows(Exception::class.java) {c.encode(echo,"sample.wkt.Envelope","{}")}
        c.release(id)
        assertThrows(Exception::class.java) {c.schema(id)}
        assertEquals(0,c.encode(echo,"sample.v1.Echo","{}").size)
    }
    @Test fun `unknown nested fields produce a warning`() {
        val c=loaded();val id=c.register(fixture).id
        // Echo.child (field 9), containing unknown varint field 100.
        assertEquals(listOf("UNKNOWN_FIELDS_PRESENT"),c.decode(id,"sample.v1.Echo",byteArrayOf(74,3,0xA0.toByte(),6,1)).warnings)
    }

}

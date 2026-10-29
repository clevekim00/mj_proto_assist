package assist.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class StdioTest {
    private val mapper=jacksonObjectMapper()
    @Test fun `handshake major duplicate ID invalid JSON and shutdown`() {
        val s=Stdio()
        fun req(id:String,major:Int=1,method:String="system.hello")=mapper.readTree(s.dispatch("""{"id":"$id","protocolMajor":$major,"method":"$method"}"""))
        assertEquals(1,req("a").path("result").path("protocolMajor").asInt())
        assertEquals("DUPLICATE_ID",req("a").path("error").path("code").asText())
        assertEquals("PROTOCOL_MISMATCH",req("b",2).path("error").path("code").asText())
        assertTrue(mapper.readTree(s.dispatch("invalid")).has("error"))
        req("c",method="system.shutdown");assertTrue(s.shutdown)
    }
    @Test fun `stdio descriptor encode decode release keeps int64 string`() {
        val s=Stdio();var i=0
        fun call(method:String,params:Any):com.fasterxml.jackson.databind.JsonNode = mapper.readTree(s.dispatch(mapper.writeValueAsString(mapOf("id" to "r${i++}","protocolMajor" to 1,"method" to method,"params" to params)))).get("result")
        val descriptor=Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("../examples/echo.pb")))
        val id=call("schema.register",mapOf("descriptorBase64" to descriptor)).get("schemaId").asText()
        val p=mapOf("schemaId" to id,"messageType" to "sample.v1.Echo")
        val b=call("codec.encode",p+mapOf("protoJson" to mapOf("revision" to "9007199254740993"))).get("payloadBase64").asText()
        assertEquals("9007199254740993",call("codec.decode",p+mapOf("payloadBase64" to b)).path("protoJson").path("revision").asText())
        assertTrue(call("schema.release",mapOf("schemaId" to id)).path("released").asBoolean())
    }
}

package assist.web

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.http.HttpTimeoutException
import java.util.Base64

class HttpRunnerTest {
    private fun server(block:(HttpServer,String)->Unit) {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.start()
        try {block(server,"http://127.0.0.1:${server.address.port}")} finally {server.stop(0)}
    }
    @Test fun `POST preserves bytes and authentication header without retry or redirect`() = server { s,url ->
        var calls=0
        s.createContext("/echo") { x -> calls++; assertEquals("POST",x.requestMethod);assertEquals("Bearer synthetic",x.requestHeaders.getFirst("Authorization"));val b=x.requestBody.readAllBytes();x.responseHeaders.add("Content-Type","application/x-protobuf");x.sendResponseHeaders(400,b.size.toLong());x.responseBody.use {it.write(b)} }
        val bytes=byteArrayOf(0,1,127,-1)
        val r=HttpRunner().execute("$url/echo",mapOf("Authorization" to "Bearer synthetic"),bytes,2000,true)
        assertEquals(400,r.status);assertArrayEquals(bytes,Base64.getDecoder().decode(r.payloadBase64));assertEquals(1,calls)
        s.createContext("/redirect") { x -> x.responseHeaders.add("Location","$url/echo");x.sendResponseHeaders(302,-1);x.close() }
        assertEquals(302,HttpRunner().execute("$url/redirect",emptyMap(),bytes,2000,true).status);assertEquals(1,calls)
    }
    @Test fun `empty and HTML responses retain raw result`() = server { s,url ->
        s.createContext("/empty") { it.sendResponseHeaders(204,-1);it.close() }
        s.createContext("/html") { x -> val b="<h1>synthetic error</h1>".toByteArray();x.responseHeaders.add("Content-Type","text/html");x.sendResponseHeaders(500,b.size.toLong());x.responseBody.use {it.write(b)} }
        assertEquals("",HttpRunner().execute("$url/empty",emptyMap(),byteArrayOf(),2000,true).payloadBase64)
        assertEquals(500,HttpRunner().execute("$url/html",emptyMap(),byteArrayOf(),2000,true).status)
    }
    @Test fun `HTTP opt in invalid scheme and userinfo rejected`() {
        for(url in listOf("http://localhost/test","file:///tmp/a","https://user:pass@localhost/a")) {
            assertThrows(Exception::class.java) { HttpRunner().execute(url,emptyMap(),byteArrayOf(),1000,false) }
        }
    }
    @Test fun `timeout is bounded`() = server { s,url ->
        s.createContext("/slow") { x -> Thread.sleep(400);x.close() }
        assertThrows(HttpTimeoutException::class.java) { HttpRunner().execute("$url/slow",emptyMap(),byteArrayOf(),100,true) }
    }
    @Test fun `oversized response is rejected`() = server { s,url ->
        s.createContext("/large") { x -> val b=ByteArray(assist.ProtoCore.MAX_BYTES+1);try{x.sendResponseHeaders(200,b.size.toLong());x.responseBody.use {it.write(b)}}catch(_:Exception){x.close()} }
        assertThrows(Exception::class.java) { HttpRunner().execute("$url/large",emptyMap(),byteArrayOf(),5000,true) }
    }
}

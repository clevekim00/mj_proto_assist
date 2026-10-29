package assist.web

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class LocalGuardTest {
    @Test fun `foreign origin wrong host missing session and oversized requests rejected`() {
        val session=LocalSession().also{it.authority="127.0.0.1:12345"}; val guard=LocalGuard(session)
        fun request(origin:String,host:String="127.0.0.1:12345",body:ByteArray="{}".toByteArray()):Int {
            val req=MockHttpServletRequest("POST","/local/v1/codec/encode")
            req.addHeader("Host",host);req.addHeader("Origin",origin);req.contentType="application/json";req.setContent(body)
            val res=MockHttpServletResponse();guard.doFilter(req,res){_,_->fail<Unit>("must not reach controller")};return res.status
        }
        assertEquals(403,request("https://untrusted.example"))
        assertEquals(403,request("http://127.0.0.1:12345","evil.example"))
        assertEquals(401,request("http://127.0.0.1:12345"))
        assertEquals(403,request("http://127.0.0.1:12345",body=ByteArray(16*1024*1024+1)))
    }
    @Test fun `bootstrap is one use and session stays in memory`() {
        val session=LocalSession()
        val field=LocalSession::class.java.getDeclaredField("bootstrap").apply{isAccessible=true}
        val bootstrap=field.get(session) as String
        val token=session.exchange(bootstrap);assertTrue(session.valid(token));assertFalse(session.valid("invalid"))
        assertThrows(Exception::class.java){session.exchange(bootstrap)}
    }
}

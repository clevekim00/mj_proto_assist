package assist.web

import assist.ProtoCore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.filter.OncePerRequestFilter
import java.security.SecureRandom
import java.util.Base64

@SpringBootApplication(proxyBeanMethods = false)
class Application {
    @Bean fun core() = ProtoCore()
    @Bean fun runner() = HttpRunner()
}
fun main(args: Array<String>) { runApplication<Application>(*args) }

@Component
class LocalSession : ApplicationListener<WebServerInitializedEvent> {
    private fun token() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private var bootstrap: String? = token()
    private val sessions = mutableSetOf<String>()
    @Volatile var authority = ""
    @Synchronized fun exchange(value: String): String {
        require(value == bootstrap && bootstrap != null) { "INVALID_BOOTSTRAP" }
        bootstrap = null
        return token().also { sessions.add(it) }
    }
    @Synchronized fun valid(value: String?) = value != null && value in sessions
    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        authority = "127.0.0.1:${event.webServer.port}"
        // Deliberate local startup credential, never a target API credential or payload.
        System.err.println("Open http://$authority/#bootstrap=$bootstrap")
    }
}

@Component
class LocalGuard(private val session: LocalSession) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'")
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("Cache-Control", "no-store")
        if (request.getHeader("Host") != session.authority) { response.sendError(403); return }
        if (request.requestURI.startsWith("/local/")) {
            val origin = request.getHeader("Origin")
            if (request.method != "POST" || origin != "http://${session.authority}" ||
                request.contentType?.substringBefore(';') != "application/json" ||
                request.contentLengthLong > 16L * 1024 * 1024 || request.contentLengthLong < 0) {
                response.sendError(403); return
            }
            if (request.requestURI != "/local/v1/session" && !session.valid(request.getHeader("X-Local-Session"))) {
                response.sendError(401); return
            }
        }
        chain.doFilter(request, response)
    }
}

@RestController
@RequestMapping("/local/v1")
class LocalController(private val core: ProtoCore, private val runner: HttpRunner, private val session: LocalSession, private val mapper: ObjectMapper) {
    private fun JsonNode.text(key: String): String = get(key)?.takeIf { it.isTextual }?.asText() ?: error("MISSING_FIELD")
    private fun binary(value: String): ByteArray {
        require(value.length <= ((ProtoCore.MAX_BYTES + 2) / 3) * 4) { "SIZE_LIMIT" }
        return Base64.getDecoder().decode(value)
    }
    @PostMapping("/session") fun session(@RequestBody p: JsonNode) = mapOf("session" to session.exchange(p.text("bootstrap")))
    @PostMapping("/schema/register") fun register(@RequestBody p: JsonNode): Any {
        val s = core.register(binary(p.text("descriptorBase64")))
        return mapOf("schemaId" to s.id, "types" to s.types.keys.sorted())
    }
    @PostMapping("/schema/types") fun types(@RequestBody p: JsonNode): Any = mapOf("types" to core.schema(p.text("schemaId")).types.values.map { d ->
        mapOf("name" to d.fullName, "fields" to d.fields.map { mapOf("name" to it.jsonName, "number" to it.number, "type" to it.type.name) })
    })
    @PostMapping("/codec/encode") fun encode(@RequestBody p: JsonNode): Any {
        val bytes = core.encode(p.text("schemaId"), p.text("messageType"), p.get("protoJson")?.toString() ?: error("MISSING_FIELD"))
        return mapOf("payloadBase64" to Base64.getEncoder().encodeToString(bytes), "size" to bytes.size)
    }
    @PostMapping("/codec/decode") fun decode(@RequestBody p: JsonNode): Any {
        val decoded = core.decode(p.text("schemaId"), p.text("messageType"), binary(p.text("payloadBase64")))
        return mapOf("protoJson" to mapper.readTree(decoded.protoJson), "warnings" to decoded.warnings)
    }
    @PostMapping("/http/execute") fun execute(@RequestBody p: JsonNode): Any {
        val headers = p.get("headers")?.properties()?.associate { it.key to it.value.asText() } ?: emptyMap()
        val result = runner.execute(p.text("url"), headers, binary(p.text("payloadBase64")), p.path("timeoutMs").asLong(10000), p.path("allowHttp").asBoolean(false))
        val type = p.path("responseRules").get(result.status.toString())?.asText()
            ?: p.path("responseRules").get("${result.status / 100}xx")?.asText()
        val mediaType = result.headers["content-type"]?.firstOrNull()?.substringBefore(';')
        var decoded: JsonNode? = null
        var warnings = emptyList<String>()
        var decodeError: String? = null
        if (!type.isNullOrBlank() && result.payloadBase64.isNotEmpty() && mediaType in setOf("application/x-protobuf", "application/protobuf", "application/octet-stream")) {
            try {
                val output = core.decode(p.text("schemaId"), type, binary(result.payloadBase64))
                decoded = mapper.readTree(output.protoJson); warnings = output.warnings
            } catch (_: Exception) { decodeError = "DECODE_FAILED" }
        }
        return mapOf("http" to result, "messageType" to type, "protoJson" to decoded, "warnings" to warnings, "decodeError" to decodeError)
    }
    @ExceptionHandler(Exception::class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    fun invalid(e: Exception): Any = mapOf("error" to mapOf("code" to when (e) {
        is java.net.http.HttpTimeoutException -> "HTTP_TIMEOUT"
        is java.io.IOException -> "NETWORK_ERROR"
        else -> "INVALID_INPUT"
    }, "message" to "요청을 처리하지 못했습니다. 스키마, 타입, 입력 및 연결 설정을 확인하세요."))
}

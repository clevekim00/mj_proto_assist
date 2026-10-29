package assist.web

import assist.ProtoCore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class HttpRunner {
    data class Result(val status: Int, val headers: Map<String, List<String>>, val payloadBase64: String, val elapsedMs: Long)
    fun execute(url: String, headers: Map<String, String>, bytes: ByteArray, timeoutMs: Long, allowHttp: Boolean): Result {
        val uri = URI(url)
        require(uri.host != null && uri.userInfo == null && uri.fragment == null) { "INVALID_URL" }
        require(uri.scheme == "https" || (uri.scheme == "http" && allowHttp)) { "HTTPS_REQUIRED" }
        require(timeoutMs in 100..60000) { "INVALID_TIMEOUT" }
        require(bytes.size <= ProtoCore.MAX_BYTES) { "SIZE_LIMIT" }
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/x-protobuf").header("Accept", "application/x-protobuf")
        headers.forEach { (key, value) ->
            require(key.lowercase() !in setOf("host", "content-length", "connection", "transfer-encoding", "accept-encoding")) { "INVALID_HEADER" }
            request.setHeader(key, value)
        }
        val start = System.nanoTime()
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(timeoutMs)).build()
        // A bounded subscriber keeps both request timeout and response size limits active while reading the body.
        val response = client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            HttpResponse.BodyHandler { BoundedBodySubscriber(ProtoCore.MAX_BYTES) })
        return Result(response.statusCode(), response.headers().map(), Base64.getEncoder().encodeToString(response.body()),
            (System.nanoTime() - start) / 1_000_000)
    }
}

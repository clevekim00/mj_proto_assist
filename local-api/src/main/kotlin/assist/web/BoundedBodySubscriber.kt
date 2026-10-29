package assist.web

import java.io.ByteArrayOutputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

internal class BoundedBodySubscriber(private val limit: Int) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val output = ByteArrayOutputStream()
    private lateinit var subscription: Flow.Subscription
    override fun getBody(): CompletionStage<ByteArray> = result
    override fun onSubscribe(s: Flow.Subscription) { subscription = s; s.request(1) }
    override fun onNext(buffers: List<ByteBuffer>) {
        for (buffer in buffers) {
            if (buffer.remaining().toLong() + output.size() > limit) {
                subscription.cancel(); result.completeExceptionally(IllegalArgumentException("SIZE_LIMIT")); return
            }
            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); output.write(bytes)
        }
        subscription.request(1)
    }
    override fun onError(error: Throwable) { result.completeExceptionally(error) }
    override fun onComplete() { result.complete(output.toByteArray()) }
}

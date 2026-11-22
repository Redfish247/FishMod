package fishmod.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object Http {
    @JvmField
    val CLIENT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @JvmField
    val REDIRECT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    private const val MAX_CONCURRENT = 8
    private const val MAX_RETRIES = 3
    private var active = 0
    private val waiting = java.util.ArrayDeque<Runnable>()
    private val retryTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FishMod-HttpRetry").apply { isDaemon = true }
    }

    @JvmStatic
    fun <T> sendAsync(req: HttpRequest, handler: HttpResponse.BodyHandler<T>): java.util.concurrent.CompletableFuture<HttpResponse<T>> {
        val out = java.util.concurrent.CompletableFuture<HttpResponse<T>>()
        attempt(req, handler, out, 0)
        return out
    }

    private fun <T> attempt(req: HttpRequest, handler: HttpResponse.BodyHandler<T>, out: java.util.concurrent.CompletableFuture<HttpResponse<T>>, n: Int) {
        val task = Runnable {
            val f = try { CLIENT.sendAsync(req, handler) } catch (t: Throwable) { release(); out.completeExceptionally(t); return@Runnable }
            f.whenComplete { r, t ->
                release()
                val cause = (t as? java.util.concurrent.CompletionException)?.cause ?: t
                when {
                    t == null -> out.complete(r)
                    cause is IOException && n < MAX_RETRIES ->
                        retryTimer.schedule({ attempt(req, handler, out, n + 1) }, 500L shl n, java.util.concurrent.TimeUnit.MILLISECONDS)
                    else -> out.completeExceptionally(cause)
                }
            }
        }
        val runNow = synchronized(this) { (active < MAX_CONCURRENT).also { if (it) active++ else waiting.add(task) } }
        if (runNow) task.run()
    }

    private fun release() {
        val next = synchronized(this) { waiting.poll().also { if (it == null) active-- } }
        if (next != null) java.util.concurrent.ForkJoinPool.commonPool().execute(next)
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class)
    fun get(url: String, timeout: Duration, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    // Throws IOException on non-2xx or blank body.
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class)
    fun getJson(url: String, timeout: Duration, headers: Map<String, String> = emptyMap()): JsonElement {
        val response = get(url, timeout, headers)
        val body = response.body()
        if (response.statusCode() !in 200..299 || body.isNullOrBlank()) throw IOException("HTTP ${response.statusCode()} $url")
        return JsonParser.parseString(body)
    }
}

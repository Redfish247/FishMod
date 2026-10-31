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

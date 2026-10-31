package fishmod.utils.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.IoExecutor
import fishmod.utils.debug.FishDiag
import net.fabricmc.loader.api.FabricLoader
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

// NotEnoughUpdates-REPO items, cached on disk and refreshed daily.
object NeuRepo {

    private const val ZIP_URL = "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master"
    private const val REFRESH_MS = 24L * 60 * 60 * 1000L
    private val ZIP_PATH = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("neu-repo.zip")

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile private var items: Map<String, JsonObject> = emptyMap()
    private val loading = AtomicBoolean(false)
    private const val RETRY_MS = 5L * 60 * 1000L
    @Volatile private var retryAfter = 0L

    @JvmStatic fun isLoaded(): Boolean = items.isNotEmpty()

    @JvmStatic fun get(id: String?): JsonObject? = id?.let { items[it] }

    @JvmStatic fun ids(): Set<String> = items.keys

    @JvmStatic
    fun ensureLoaded() {
        if (isLoaded() || System.currentTimeMillis() < retryAfter || !loading.compareAndSet(false, true)) return
        IoExecutor.execute {
            try {
                val fresh = Files.exists(ZIP_PATH) && System.currentTimeMillis() - Files.getLastModifiedTime(ZIP_PATH).toMillis() < REFRESH_MS
                if (Files.exists(ZIP_PATH)) runCatching { parse(Files.readAllBytes(ZIP_PATH)) }
                    .onFailure { FishDiag.fail("NeuRepo.1", "cached NEU repo zip unreadable", it) }
                if (!fresh || !isLoaded()) download()
                if (!isLoaded()) retryAfter = System.currentTimeMillis() + RETRY_MS
            } finally {
                loading.set(false)
            }
        }
    }

    private fun download() {
        try {
            val req = HttpRequest.newBuilder(URI.create(ZIP_URL)).timeout(Duration.ofSeconds(60)).GET().build()
            val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() != 200) { FishDiag.fail("NeuRepo.2", "NEU repo download HTTP ${resp.statusCode()}"); return }
            val bytes = resp.body()
            parse(bytes)
            Files.createDirectories(ZIP_PATH.parent)
            Files.write(ZIP_PATH, bytes)
        } catch (e: Exception) {
            FishDiag.fail("NeuRepo.3", "NEU repo download failed", e)
        }
    }

    private fun parse(bytes: ByteArray) {
        val out = HashMap<String, JsonObject>(8192)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                val slash = name.indexOf('/')
                if (e.isDirectory || slash < 0 || !name.startsWith("items/", slash + 1) || !name.endsWith(".json")) continue
                try {
                    val o = JsonParser.parseString(String(zip.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject
                    val id = o.get("internalname")?.asString ?: name.substringAfterLast('/').removeSuffix(".json")
                    out[id] = o
                } catch (ex: Exception) {
                    FishDiag.fail("NeuRepo.4", "bad NEU item json $name", ex)
                }
            }
        }
        if (out.isNotEmpty()) items = out
    }
}

package fishmod.features.visuals

import com.google.gson.JsonParser
import fishmod.utils.debug.FishDiag
import net.fabricmc.loader.api.FabricLoader
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SkyBlock ID -> old (vanilla) model + head texture list, taken from Noamm's PackDisabler (CC0), which builds it from a
 * NotEnoughUpdates-REPO snapshot. Downloaded once and cached in the config folder, so it adds nothing to the jar.
 */
object ClassicItemData {
    class Entry(val model: String, val texture: String?)

    enum class State { IDLE, LOADING, READY, FAILED }

    private const val URL = "https://raw.githubusercontent.com/Noamm9/PackDisabler/master/src/main/resources/skyblock-items.json"
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    private val cache: Path = FabricLoader.getInstance().configDir.resolve("fishmod/skyblock-pack/old-items.json")
    private val started = AtomicBoolean(false)
    @Volatile private var map: Map<String, Entry> = emptyMap()
    @Volatile var state: State = State.IDLE
        private set

    @JvmStatic
    fun start() {
        if (!started.compareAndSet(false, true)) return
        state = State.LOADING
        val t = Thread({ load() }, "FishMod-OldItemData")
        t.isDaemon = true
        t.start()
    }

    @JvmStatic
    fun get(id: String?): Entry? = if (id == null) null else map[id]

    private fun load() {
        try {
            val stale = !Files.exists(cache) || System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() > MAX_AGE_MS
            if (stale) {
                try { download() } catch (t: Throwable) { FishDiag.fail("ClassicItemData.1", "old item data download failed", t) }
            }
            if (!Files.exists(cache)) { state = State.FAILED; return }
            map = parse(Files.readString(cache))
            state = if (map.isEmpty()) State.FAILED else State.READY
        } catch (t: Throwable) {
            FishDiag.fail("ClassicItemData.2", "old item data load failed", t)
            state = State.FAILED
        }
    }

    private fun download() {
        Files.createDirectories(cache.parent)
        val tmp = Files.createTempFile(cache.parent, "old-items", ".tmp")
        try {
            val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            val req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(60)).build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp))
            if (resp.statusCode() !in 200..299 || Files.size(tmp) < 100_000) error("HTTP ${resp.statusCode()}")
            Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun parse(text: String): Map<String, Entry> {
        val out = HashMap<String, Entry>(12000)
        for ((id, el) in JsonParser.parseReader(StringReader(text)).asJsonObject.entrySet()) {
            val o = el.asJsonObject
            val model = o.get("model")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val tex = o.get("texture")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
            out[id] = Entry(model, tex)
        }
        return out
    }
}

package fishmod.cosmetic

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.HypixelApi
import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

// Live cosmetics: one WebSocket to fishmod.dev/live; full set on connect, then pushed changes only.
object CosmeticLive {
    private const val URL = "wss://fishmod.dev/live"
    private const val PING_MS = 45_000L
    private const val MIN_BACKOFF_MS = 5_000L
    private const val MAX_BACKOFF_MS = 5 * 60_000L

    class Slice(
        val nicks: Map<String, String>, val items: Map<String, String>, val scales: Map<String, String>,
        val badges: Map<String, String>, val blocked: Set<String>, val cleared: Map<String, Long>,
    )

    @Volatile var ready = false; private set
    @Volatile var rev = 0L; private set

    private val nicks = ConcurrentHashMap<String, String>()
    private val items = ConcurrentHashMap<String, String>()
    private val scales = ConcurrentHashMap<String, String>()
    private val badges = ConcurrentHashMap<String, String>()
    private val cleared = ConcurrentHashMap<String, Long>()
    @Volatile private var blocked: Set<String> = emptySet()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var connecting = false
    private var retryAt = 0L
    private var backoff = MIN_BACKOFF_MS
    private var lastPing = 0L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("CosmeticLive.1", "live cosmetics tick failed") { tick(mc) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> close() }
    }

    private fun wanted(): Boolean =
        FishSettings.remoteNicksEnabled || FishSettings.remoteItemsEnabled || FishSettings.playerSizeShared || FishSettings.badgesEnabled

    private fun tick(mc: Minecraft) {
        val player = mc.player
        if (mc.connection == null || player == null || !wanted()) { if (ws != null) close(); return }
        val now = System.currentTimeMillis()
        val socket = ws
        if (socket != null) {
            if (now - lastPing >= PING_MS) { lastPing = now; socket.sendText("ping", true) }
            return
        }
        if (connecting || now < retryAt) return
        connecting = true
        fishmod.utils.Http.CLIENT.newWebSocketBuilder()
            .header("X-FishMod-Token", HypixelApi.MOD_TOKEN)
            .header("X-FishMod-Caller", player.uuid.toString().replace("-", ""))
            .buildAsync(URI.create(URL), Listener())
            .whenComplete { s, t ->
                connecting = false
                if (t != null || s == null) { failed(); return@whenComplete }
                ws = s
                lastPing = System.currentTimeMillis()
                backoff = MIN_BACKOFF_MS
            }
    }

    private fun failed() {
        ws = null
        ready = false
        retryAt = System.currentTimeMillis() + backoff
        backoff = minOf(backoff * 2, MAX_BACKOFF_MS)
    }

    private fun close() {
        val s = ws
        ws = null
        ready = false
        backoff = MIN_BACKOFF_MS
        retryAt = 0L
        try { s?.sendClose(WebSocket.NORMAL_CLOSURE, "bye") } catch (_: Exception) {}
    }

    /** Cosmetics for these uuids, or null while not connected (caller falls back to /sync). */
    fun slice(uuids: Set<String>): Slice? {
        if (!ready) return null
        fun <V> pick(m: Map<String, V>) = HashMap<String, V>().also { out -> for (u in uuids) m[u]?.let { out[u] = it } }
        val b = blocked
        return Slice(pick(nicks), pick(items), pick(scales), pick(badges), uuids.filterTo(HashSet()) { it in b }, pick(cleared))
    }

    private fun accept(text: String) {
        if (text == "pong") return
        val root = JsonParser.parseString(text).asJsonObject
        val full = root.get("t")?.asString == "snap"
        if (full) { nicks.clear(); items.clear(); scales.clear(); badges.clear(); cleared.clear() }
        mergeStrings(root, "nicks", nicks)
        mergeStrings(root, "items", items)
        mergeStrings(root, "scales", scales)
        mergeStrings(root, "badges", badges)
        root.getAsJsonObject("cleared")?.entrySet()?.forEach { (k, v) -> if (v.isJsonNull) cleared.remove(k) else cleared[k] = v.asLong }
        root.getAsJsonArray("blocked")?.let { arr -> blocked = arr.mapTo(HashSet()) { it.asString } }
        if (full) ready = true
        rev++
    }

    private fun mergeStrings(root: JsonObject, key: String, into: MutableMap<String, String>) {
        root.getAsJsonObject(key)?.entrySet()?.forEach { (k, v) -> if (v.isJsonNull) into.remove(k) else into[k] = v.asString }
    }

    private class Listener : WebSocket.Listener {
        private val buf = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buf.append(data)
            if (last) {
                val text = buf.toString()
                buf.setLength(0)
                FishDiag.guard("CosmeticLive.2", "live cosmetics message failed (${text.length} chars)") { accept(text) }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (ws === webSocket) failed()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (ws === webSocket || ws == null) failed()
        }
    }
}

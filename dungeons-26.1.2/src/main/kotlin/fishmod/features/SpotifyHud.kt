package fishmod.features

import com.google.gson.JsonParser
import fishmod.compat.hudHidden
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

// Now-playing pill from the local Spotify desktop window title; no account, Windows only. Lyrics come from lrclib.net
object SpotifyHud {
    private const val NAME = "Spotify"
    private const val PAD_X = 6
    private const val PAD_Y = 3
    private const val H = 9 + PAD_Y * 2
    private const val GAP = 2
    private const val SCROLL_MS = 150L
    private const val HOLD_MS = 1500L
    private const val SCRIPT = "while(\$true){\$t=(Get-Process Spotify -ErrorAction SilentlyContinue|Where-Object{\$_.MainWindowTitle}|Select-Object -First 1).MainWindowTitle;[Console]::Out.WriteLine(\$t);[Console]::Out.Flush();Start-Sleep -Seconds 2}"

    // Written by the reader thread, read by render; a swapped String reference is enough
    @Volatile private var title: String = ""
    @Volatile private var proc: Process? = null

    private class Line(val ms: Long, val text: String)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    // Track being timed; play time counts only while the title shows a song
    private var track = ""
    private var playMs = 0L
    private var lastNs = 0L
    @Volatile private var lyrics: List<Line>? = null
    private var fetchedKey = ""

    private val titleMarq = Marq()
    private val lyricMarq = Marq()

    // Fixed-width window over text longer than the cap, looping with a pause at the start
    private class Marq {
        var src = ""; var cap = 0; var fixedW = 0; var scroll = false; var loop = ""; var startMs = 0L
        fun set(s: String, c: Int, mc: Minecraft) {
            if (s == src && c == cap) return
            src = s; cap = c; startMs = System.currentTimeMillis()
            scroll = s.length > c
            loop = if (scroll) "$s   " else s
            fixedW = if (!scroll) mc.font.width(s) else (0 until loop.length).maxOf { mc.font.width(window(it)) }
        }
        fun window(i: Int): String = (loop + loop).substring(i, i + cap)
        fun view(now: Long): String {
            if (!scroll) return src
            val cycle = loop.length * SCROLL_MS + HOLD_MS
            val t = (now - startMs) % cycle
            return window(if (t < HOLD_MS) 0 else ((t - HOLD_MS) / SCROLL_MS).toInt())
        }
    }

    @JvmStatic
    fun init() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        FishHudEditor.register(NAME,
            { FishSettings.spotifyHudX }, { v -> FishSettings.spotifyHudX = v },
            { FishSettings.spotifyHudY }, { v -> FishSettings.spotifyHudY = v },
            120, 12,
            { FishSettings.spotifyHudScale }, { v -> FishSettings.spotifyHudScale = v },
            { true }, { FishSettings.spotifyHudEnabled })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spotify_hud")) { ctx, _ -> draw(ctx) }
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    private fun start() {
        if (proc != null) return
        val t = Thread({
            try {
                val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", SCRIPT).redirectErrorStream(true).start()
                proc = p
                BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { r ->
                    while (true) { title = r.readLine()?.trim() ?: break }
                }
            } catch (_: Throwable) {
            } finally { proc = null; title = "" }
        }, "fishmod-spotify")
        t.isDaemon = true
        t.start()
    }

    private fun stop() { proc?.destroy(); proc = null; title = ""; track = ""; lyrics = null }

    // Spotify shows these while idle or paused
    private fun isIdle(t: String) = t.isEmpty() || t == "Spotify" || t == "Spotify Free" || t == "Spotify Premium"

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private fun httpGet(url: String): String? = try {
        val req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("User-Agent", "FishModDungeons").GET().build()
        val r = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (r.statusCode() == 200) r.body() else null
    } catch (_: Throwable) { null }

    private fun parseSynced(s: String): List<Line> {
        val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)""")
        return s.lineSequence().mapNotNull { re.matchEntire(it.trim()) }
            .map { Line(((it.groupValues[1].toLong() * 60 + it.groupValues[2].toDouble()) * 1000).toLong(), it.groupValues[3]) }
            .toList()
    }

    private fun fetchLyrics(key: String) {
        val i = key.indexOf(" - ")
        if (i <= 0) { lyrics = emptyList(); return }
        val q = "artist_name=${enc(key.substring(0, i))}&track_name=${enc(key.substring(i + 3))}"
        Thread({
            var synced: String? = null
            try {
                httpGet("https://lrclib.net/api/get?$q")?.let { synced = JsonParser.parseString(it).asJsonObject.get("syncedLyrics")?.takeUnless { e -> e.isJsonNull }?.asString }
                if (synced == null) httpGet("https://lrclib.net/api/search?$q")?.let { b ->
                    synced = JsonParser.parseString(b).asJsonArray.firstNotNullOfOrNull { e -> e.asJsonObject.get("syncedLyrics")?.takeUnless { x -> x.isJsonNull }?.asString }
                }
            } catch (_: Throwable) {}
            if (track == key) lyrics = synced?.let { parseSynced(it) } ?: emptyList()
        }, "fishmod-lyrics").apply { isDaemon = true }.start()
    }

    private fun currentLyric(): String {
        val l = lyrics ?: return ""
        val at = playMs + FishSettings.spotifyHudLyricOffset
        var out = ""
        for (ln in l) { if (ln.ms <= at) out = ln.text else break }
        return out
    }

    // Rounded pill drawn as one-pixel rows
    private fun pill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val r = h / 2.0
        for (row in 0 until h) {
            val dy = r - minOf(row, h - 1 - row) - 0.5
            val inset = ceil(r - sqrt(maxOf(0.0, r * r - dy * dy))).toInt()
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color)
        }
    }

    private fun bars(ctx: GuiGraphicsExtractor, x: Int, y: Int, playing: Boolean, color: Int, now: Long) {
        for (b in 0 until 3) {
            val h = if (playing) 2 + ((sin(now / 140.0 + b * 1.7) + 1) * 2.5).toInt() else 2
            ctx.fill(x + b * 3, y + 9 - h, x + b * 3 + 2, y + 9, color)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor) {
        if (!FishSettings.spotifyHudEnabled) { if (proc != null) stop(); return }
        if (proc == null) { start(); return }
        val mc = Minecraft.getInstance()
        val nowNs = System.nanoTime()
        val dt = if (lastNs == 0L) 0L else (nowNs - lastNs) / 1_000_000L
        lastNs = nowNs
        if (mc.hudHidden || mc.player == null || FishHudEditor.isOpen()) return
        val t = title
        val playing = !isIdle(t)
        if (playing) {
            if (t != track) { track = t; playMs = 0; lyrics = null; fetchedKey = "" }
            else playMs += dt.coerceAtMost(500)
            if (FishSettings.spotifyHudLyrics && fetchedKey != t) { fetchedKey = t; fetchLyrics(t) }
        }
        val shown = if (playing) t else if (FishSettings.spotifyHudShowPaused) "Paused" else ""
        if (shown.isEmpty()) return

        val cap = FishSettings.spotifyHudMaxChars.coerceIn(8, 80)
        val now = System.currentTimeMillis()
        titleMarq.set(shown, cap, mc)
        val icon = FishSettings.spotifyHudIcon
        val col = FishSettings.spotifyHudTextColor
        val bg = FishSettings.spotifyHudBackground
        val sc = FishSettings.spotifyHudScale.toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(FishSettings.spotifyHudX.toFloat(), FishSettings.spotifyHudY.toFloat())
        pose.scale(sc, sc)

        val iconW = if (icon) 11 else 0
        val w = PAD_X * 2 + iconW + titleMarq.fixedW
        if (bg) pill(ctx, 0, 0, w, H, FishSettings.spotifyHudBgColor)
        if (icon) bars(ctx, PAD_X, PAD_Y, playing, col, now)
        ctx.text(mc.font, titleMarq.view(now), PAD_X + iconW, PAD_Y, col, true)

        if (playing && FishSettings.spotifyHudLyrics) {
            val lyric = currentLyric()
            if (lyric.isNotEmpty()) {
                lyricMarq.set(lyric, cap, mc)
                val lw = PAD_X * 2 + lyricMarq.fixedW
                val y = H + GAP
                if (bg) pill(ctx, 0, y, lw, H, FishSettings.spotifyHudBgColor)
                ctx.text(mc.font, lyricMarq.view(now), PAD_X, y + PAD_Y, col, true)
            }
        }
        pose.popMatrix()
    }

}

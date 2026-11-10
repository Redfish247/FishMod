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
object SpotifyLyricsHud {
    private const val NAME = "Spotify"
    private const val PAD_X = 6
    private const val PAD_Y = 3
    private const val H = 9 + PAD_Y * 2
    private const val GAP = 2
    private const val SPEED = 24f
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
        var src = ""; var cap = 0; var fixedW = 0; var scroll = false; var loopW = 0; var startMs = 0L
        fun set(s: String, c: Int, mc: Minecraft) {
            if (s == src && c == cap) return
            src = s; cap = c; startMs = System.currentTimeMillis()
            scroll = s.length > c
            fixedW = mc.font.width(if (scroll) s.take(c) else s)
            loopW = if (scroll) mc.font.width("$s   ") else 0
        }
        // Pixel-smooth scroll clipped to the box, held still for a moment at the start of each loop
        fun draw(ctx: GuiGraphicsExtractor, mc: Minecraft, x: Int, y: Int, col: Int, now: Long) {
            if (!scroll) { ctx.text(mc.font, src, x, y, col, true); return }
            val t = (now - startMs) % (HOLD_MS + loopW * 1000L / SPEED)
            val off = if (t < HOLD_MS) 0f else (t - HOLD_MS) * SPEED / 1000f
            ctx.enableScissor(x, y - 1, x + fixedW, y + 10)
            val pose = ctx.pose()
            pose.pushMatrix()
            pose.translate(x - off, y.toFloat())
            ctx.text(mc.font, src, 0, 0, col, true)
            ctx.text(mc.font, src, loopW, 0, col, true)
            pose.popMatrix()
            ctx.disableScissor()
        }
    }

    @JvmStatic
    fun init() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        FishHudEditor.register(NAME,
            { FishSettings.spotifyLyricsX }, { v -> FishSettings.spotifyLyricsX = v },
            { FishSettings.spotifyLyricsY }, { v -> FishSettings.spotifyLyricsY = v },
            120, 12,
            { FishSettings.spotifyLyricsScale }, { v -> FishSettings.spotifyLyricsScale = v },
            { true }, { FishSettings.spotifyLyricsEnabled })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spotify_lyrics_hud")) { ctx, _ -> draw(ctx) }
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
        val at = playMs + FishSettings.spotifyLyricsLyricOffset
        var out = ""
        for (ln in l) { if (ln.ms <= at) out = ln.text else break }
        return out
    }

    // Rounded pill drawn as one-pixel rows
    private fun pill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val r = minOf(h / 2.0, 7.0)
        val a = color ushr 24
        val rgb = color and 0xFFFFFF
        for (row in 0 until h) {
            val d = minOf(row, h - 1 - row)
            val inset = if (d >= r) 0.0 else { val dy = r - d - 0.5; r - sqrt(maxOf(0.0, r * r - dy * dy)) }
            val solid = ceil(inset).toInt()
            ctx.fill(x + solid, y + row, x + w - solid, y + row + 1, color)
            // Edge pixel at partial coverage for smooth corners
            val cov = solid - inset
            if (cov > 0.02 && solid > 0) {
                val edge = ((a * cov).toInt() shl 24) or rgb
                ctx.fill(x + solid - 1, y + row, x + solid, y + row + 1, edge)
                ctx.fill(x + w - solid, y + row, x + w - solid + 1, y + row + 1, edge)
            }
        }
    }

    // Sizes: separate pills stacked with a gap, or one box holding both rows
    private fun boxW(tm: Marq, lm: Marq, hasLyric: Boolean): Int {
        val tw = PAD_X * 2 + (if (FishSettings.spotifyLyricsIcon) 11 else 0) + tm.fixedW
        return if (hasLyric) maxOf(tw, PAD_X * 2 + lm.fixedW) else tw
    }
    private fun boxH(hasLyric: Boolean): Int =
        if (!hasLyric) H else if (FishSettings.spotifyLyricsConnected) PAD_Y * 2 + 9 + 11 else H * 2 + GAP

    // Draws at the current pose origin; used by the live HUD and the HUD editor example
    private fun body(ctx: GuiGraphicsExtractor, mc: Minecraft, tm: Marq, lm: Marq, shown: String, lyric: String, playing: Boolean) {
        val cap = FishSettings.spotifyLyricsMaxChars.coerceIn(8, 80)
        val now = System.currentTimeMillis()
        tm.set(shown, cap, mc)
        val hasLyric = lyric.isNotEmpty()
        if (hasLyric) lm.set(lyric, cap, mc)
        val icon = FishSettings.spotifyLyricsIcon
        val col = FishSettings.spotifyLyricsTextColor
        val bg = FishSettings.spotifyLyricsBackground
        val bgc = FishSettings.spotifyLyricsBgColor
        val conn = FishSettings.spotifyLyricsConnected
        val w = boxW(tm, lm, hasLyric)
        if (bg) {
            if (hasLyric && conn) pill(ctx, 0, 0, w, boxH(true), bgc)
            else {
                pill(ctx, 0, 0, if (hasLyric) PAD_X * 2 + (if (icon) 11 else 0) + tm.fixedW else w, H, bgc)
                if (hasLyric) pill(ctx, 0, H + GAP, PAD_X * 2 + lm.fixedW, H, bgc)
            }
        }
        if (icon) bars(ctx, PAD_X, PAD_Y, playing, col, now)
        tm.draw(ctx, mc, PAD_X + (if (icon) 11 else 0), PAD_Y, col, now)
        if (hasLyric) {
            val y = if (conn) PAD_Y + 11 else H + GAP + PAD_Y
            lm.draw(ctx, mc, PAD_X, y, col, now)
        }
    }

    private val pvT = Marq()
    private val pvL = Marq()
    private const val PV_TITLE = "Daft Punk - Digital Love"
    private const val PV_LYRIC = "Last night I had a dream about you"
    private fun pvLyric() = if (FishSettings.spotifyLyricsLyrics) PV_LYRIC else ""

    // HUD editor example
    @JvmStatic fun drawPreview(ctx: GuiGraphicsExtractor) = body(ctx, Minecraft.getInstance(), pvT, pvL, PV_TITLE, pvLyric(), true)
    @JvmStatic fun previewSize(): IntArray {
        val mc = Minecraft.getInstance()
        val cap = FishSettings.spotifyLyricsMaxChars.coerceIn(8, 80)
        pvT.set(PV_TITLE, cap, mc); pvL.set(PV_LYRIC, cap, mc)
        val has = pvLyric().isNotEmpty()
        return intArrayOf(boxW(pvT, pvL, has), boxH(has))
    }

    private fun bars(ctx: GuiGraphicsExtractor, x: Int, y: Int, playing: Boolean, color: Int, now: Long) {
        for (b in 0 until 3) {
            val h = if (playing) 2 + ((sin(now / 140.0 + b * 1.7) + 1) * 2.5).toInt() else 2
            ctx.fill(x + b * 3, y + 9 - h, x + b * 3 + 2, y + 9, color)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor) {
        if (!FishSettings.spotifyLyricsEnabled) { if (proc != null) stop(); return }
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
            if (FishSettings.spotifyLyricsLyrics && fetchedKey != t) { fetchedKey = t; fetchLyrics(t) }
        }
        val shown = if (playing) t else if (FishSettings.spotifyLyricsShowPaused) "Paused" else ""
        if (shown.isEmpty()) return

        val sc = FishSettings.spotifyLyricsScale.toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(FishSettings.spotifyLyricsX.toFloat(), FishSettings.spotifyLyricsY.toFloat())
        pose.scale(sc, sc)
        body(ctx, mc, titleMarq, lyricMarq, shown, if (playing && FishSettings.spotifyLyricsLyrics) currentLyric() else "", playing)
        pose.popMatrix()
    }

}

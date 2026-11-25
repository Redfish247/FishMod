package fishmod.features.hud

import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import fishmod.compat.hudHidden
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

object SpotifyLyricsHud {
    private const val NAME = "Spotify"
    private const val PAD_X = 6
    private const val PAD_Y = 3
    private const val H = 9 + PAD_Y * 2
    private const val GAP = 2
    private const val SPEED = 24f
    private const val HOLD_MS = 1500L
    private const val ART = 32
    private const val DPAD = 4

    // Media session script, falls back to the Spotify window title
    private val SCRIPT: String by lazy {
        SpotifyLyricsHud::class.java.getResourceAsStream("/assets/fishmod/spotify_media.ps1")?.use { String(it.readAllBytes(), Charsets.UTF_8) } ?: ""
    }

    @Volatile private var title: String = ""
    @Volatile private var proc: Process? = null

    private class Media(val artist: String, val song: String, val posMs: Long, val durMs: Long, val playing: Boolean, val artVer: Int, val atNs: Long)
    @Volatile private var media: Media? = null
    private var artVer = -1
    private var artId: Identifier? = null
    private val artPool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "fishmod-spotify-art").apply { isDaemon = true } }

    private class Line(val ms: Long, val text: String)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    @Volatile private var track = ""
    private var playMs = 0L
    private var lastNs = 0L
    @Volatile private var lyrics: List<Line>? = null
    private var fetchedKey = ""

    private val titleMarq = Marq()
    private val lyricMarq = Marq()
    private val artistMarq = Marq()

    private class Marq {
        var src = ""; var cap = 0; var fixedW = 0; var scroll = false; var loopW = 0; var startMs = 0L
        fun set(s: String, c: Int, mc: Minecraft) {
            if (s == src && c == cap) return
            src = s; cap = c; startMs = System.currentTimeMillis()
            scroll = s.length > c
            fixedW = mc.font.width(if (scroll) s.take(c) else s)
            loopW = if (scroll) mc.font.width("$s   ") else 0
        }
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
        HudPreview.register(NAME) { val s = previewSize(); HudExample(s[0], s[1]) { drawPreview(it) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spotify_lyrics_hud")) { ctx, _ -> draw(ctx) }
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    @Volatile private var running = false
    private var nextStartMs = 0L
    private val isWindows = System.getProperty("os.name", "").startsWith("Windows")

    private fun start() {
        val now = System.currentTimeMillis()
        if (running || !isWindows || now < nextStartMs || SCRIPT.isEmpty()) return
        running = true
        nextStartMs = now + 10_000L
        val t = Thread({
            try {
                val enc = java.util.Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))
                val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc).redirectErrorStream(true).start()
                proc = p
                if (!FishSettings.spotifyLyricsEnabled) p.destroy()
                BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { r ->
                    while (true) { parse(r.readLine() ?: break) }
                }
                p.destroy()
            } catch (_: Throwable) {
            } finally { proc = null; title = ""; media = null; running = false }
        }, "fishmod-spotify")
        t.isDaemon = true
        t.start()
    }

    private fun stop() { proc?.destroy(); proc = null; title = ""; track = ""; lyrics = null; media = null }

    // M = media session line, T = window title fallback
    private fun parse(line: String) {
        val p = line.trimStart('\uFEFF').trimEnd().split('\t')
        if (p[0] == "M" && p.size >= 7) {
            val m = Media(p[1].trim(), p[2].trim(), p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L, p[5] == "1", p[6].toIntOrNull() ?: 0, System.nanoTime())
            media = m
            title = if (!m.playing || m.song.isEmpty()) "" else if (m.artist.isEmpty()) m.song else "${m.artist} - ${m.song}"
        } else {
            media = null
            title = if (p[0] == "T") p.getOrElse(1) { "" }.trim() else ""
        }
    }

    // Loads the album art png the script saved into a texture
    private fun syncArt(v: Int) {
        if (v == artVer) return
        artVer = v
        val path = Path.of(System.getenv("TEMP") ?: System.getProperty("java.io.tmpdir"), "fishmod_spotify_art.png")
        artPool.execute {
            val img = try { if (Files.exists(path)) Files.newInputStream(path).use { NativeImage.read(it) } else null } catch (_: Throwable) { null }
            val mc = Minecraft.getInstance()
            mc.execute {
                if (artVer != v) { img?.close(); return@execute }
                val old = artId
                artId = null
                if (img != null) try {
                    val id = Identifier.fromNamespaceAndPath("fishmod", "spotify_art_$v")
                    mc.textureManager.register(id, DynamicTexture({ id.toString() }, img))
                    artId = id
                } catch (_: Throwable) {}
                if (old != null) try { mc.textureManager.release(old) } catch (_: Throwable) {}
            }
        }
    }

    private fun fmt(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return "%02d:%02d".format(s / 60, s % 60) }

    private fun timeText(m: Media, playing: Boolean, nowNs: Long): String {
        var pos = if (playing) m.posMs + (nowNs - m.atNs) / 1_000_000L else m.posMs
        if (m.durMs > 0) pos = pos.coerceAtMost(m.durMs)
        return if (m.durMs > 0) "${fmt(pos)} / ${fmt(m.durMs)}" else fmt(pos)
    }

    private fun isIdle(t: String) = t.isEmpty() || t == "Spotify" || t == "Spotify Free" || t == "Spotify Premium"

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private fun httpGet(url: String): String? = try {
        val req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("User-Agent", "FishModDungeons").GET().build()
        val r = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (r.statusCode() == 200) r.body() else null
    } catch (_: Throwable) { null }

    private val SYNCED_LINE = Regex("""\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)""")
    private val lyricsPool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "fishmod-lyrics").apply { isDaemon = true } }

    private fun parseSynced(s: String): List<Line> {
        return s.lineSequence().mapNotNull { SYNCED_LINE.matchEntire(it.trim()) }
            .map { Line(((it.groupValues[1].toLong() * 60 + it.groupValues[2].toDouble()) * 1000).toLong(), it.groupValues[3]) }
            .sortedBy { it.ms }
            .toList()
    }

    private fun fetchLyrics(key: String) {
        val i = key.indexOf(" - ")
        if (i <= 0) { lyrics = emptyList(); return }
        val q = "artist_name=${enc(key.substring(0, i))}&track_name=${enc(key.substring(i + 3))}"
        lyricsPool.execute {
            if (track != key) return@execute
            var synced: String? = null
            try {
                httpGet("https://lrclib.net/api/get?$q")?.let { synced = JsonParser.parseString(it).asJsonObject.get("syncedLyrics")?.takeUnless { e -> e.isJsonNull }?.asString }
                if (synced == null) httpGet("https://lrclib.net/api/search?$q")?.let { b ->
                    synced = JsonParser.parseString(b).asJsonArray.firstNotNullOfOrNull { e -> e.asJsonObject.get("syncedLyrics")?.takeUnless { x -> x.isJsonNull }?.asString }
                }
            } catch (_: Throwable) {}
            if (track == key) lyrics = synced?.let { parseSynced(it) } ?: emptyList()
        }
    }

    private fun currentLyric(): String {
        val l = lyrics ?: return ""
        val at = playMs + FishSettings.spotifyLyricsLyricOffset
        var lo = 0
        var hi = l.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (l[mid].ms <= at) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (idx >= 0) l[idx].text else ""
    }

    private fun pill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val r = minOf(h / 2.0, 7.0)
        val a = color ushr 24
        val rgb = color and 0xFFFFFF
        for (row in 0 until h) {
            val d = minOf(row, h - 1 - row)
            val inset = if (d >= r) 0.0 else { val dy = r - d - 0.5; r - sqrt(maxOf(0.0, r * r - dy * dy)) }
            val solid = ceil(inset).toInt()
            ctx.fill(x + solid, y + row, x + w - solid, y + row + 1, color)
            val cov = solid - inset
            if (cov > 0.02 && solid > 0) {
                val edge = ((a * cov).toInt() shl 24) or rgb
                ctx.fill(x + solid - 1, y + row, x + solid, y + row + 1, edge)
                ctx.fill(x + w - solid, y + row, x + w - solid + 1, y + row + 1, edge)
            }
        }
    }

    private fun boxW(tm: Marq, lm: Marq, hasLyric: Boolean): Int {
        val tw = PAD_X * 2 + (if (FishSettings.spotifyLyricsIcon) 11 else 0) + tm.fixedW
        return if (hasLyric) maxOf(tw, PAD_X * 2 + lm.fixedW) else tw
    }
    private fun boxH(hasLyric: Boolean): Int =
        if (!hasLyric) H else if (FishSettings.spotifyLyricsConnected) PAD_Y * 2 + 9 + 11 else H * 2 + GAP

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

    private fun detailed() = FishSettings.spotifyLyricsStyle == "Detailed"
    private fun cardTextX() = DPAD + ART + 5
    private fun cardTop() = DPAD * 2 + ART
    private fun cardW(mc: Minecraft, tm: Marq, am: Marq, lm: Marq, time: String, hasLyric: Boolean): Int {
        val tw = maxOf(tm.fixedW, am.fixedW, mc.font.width(time) + (if (FishSettings.spotifyLyricsIcon) 14 else 0))
        val w = cardTextX() + tw + DPAD + 2
        return if (hasLyric && FishSettings.spotifyLyricsConnected) maxOf(w, DPAD * 2 + lm.fixedW) else w
    }
    private fun cardH(hasLyric: Boolean): Int =
        cardTop() + if (!hasLyric) 0 else if (FishSettings.spotifyLyricsConnected) 11 else GAP + H

    // Detailed style: album art, title, artist and play time
    private fun card(ctx: GuiGraphicsExtractor, mc: Minecraft, tm: Marq, am: Marq, lm: Marq, song: String, artist: String, time: String, lyric: String, playing: Boolean, art: Identifier?) {
        val cap = FishSettings.spotifyLyricsMaxChars.coerceIn(8, 80)
        val now = System.currentTimeMillis()
        tm.set(song, cap, mc); am.set(artist, cap, mc)
        val hasLyric = lyric.isNotEmpty()
        if (hasLyric) lm.set(lyric, cap, mc)
        val col = FishSettings.spotifyLyricsTextColor
        val conn = FishSettings.spotifyLyricsConnected
        val w = cardW(mc, tm, am, lm, time, hasLyric)
        val top = cardTop()
        if (FishSettings.spotifyLyricsBackground) {
            val bgc = FishSettings.spotifyLyricsBgColor
            pill(ctx, 0, 0, w, if (hasLyric && conn) top + 11 else top, bgc)
            if (hasLyric && !conn) pill(ctx, 0, top + GAP, PAD_X * 2 + lm.fixedW, H, bgc)
        }
        if (art != null) ctx.blit(RenderPipelines.GUI_TEXTURED, art, DPAD, DPAD, 0.0f, 0.0f, ART, ART, ART, ART, -1)
        else ctx.fill(DPAD, DPAD, DPAD + ART, DPAD + ART, 0xFF282828.toInt())
        val tx = cardTextX()
        tm.draw(ctx, mc, tx, DPAD + 1, 0xFFFFFFFF.toInt(), now)
        am.draw(ctx, mc, tx, DPAD + 12, 0xFFBBBBBB.toInt(), now)
        ctx.text(mc.font, time, tx, DPAD + 23, 0xFF888888.toInt(), true)
        if (FishSettings.spotifyLyricsIcon) bars(ctx, w - DPAD - 10, DPAD + 23, playing, col, now)
        if (hasLyric) lm.draw(ctx, mc, if (conn) DPAD + 2 else PAD_X, if (conn) top else top + GAP + PAD_Y, col, now)
    }

    private val pvT = Marq()
    private val pvL = Marq()
    private val pvA = Marq()
    private const val PV_SONG = "Digital Love"
    private const val PV_ARTIST = "Daft Punk"
    private const val PV_TIME = "01:42 / 05:01"
    private const val PV_TITLE = "Daft Punk - Digital Love"
    private const val PV_LYRIC = "Last night I had a dream about you"
    private fun pvLyric() = if (FishSettings.spotifyLyricsLyrics) PV_LYRIC else ""

    @JvmStatic fun drawPreview(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (detailed()) card(ctx, mc, pvT, pvA, pvL, PV_SONG, PV_ARTIST, PV_TIME, pvLyric(), true, null)
        else body(ctx, mc, pvT, pvL, PV_TITLE, pvLyric(), true)
    }
    @JvmStatic fun previewSize(): IntArray {
        val mc = Minecraft.getInstance()
        val cap = FishSettings.spotifyLyricsMaxChars.coerceIn(8, 80)
        pvL.set(PV_LYRIC, cap, mc)
        val has = pvLyric().isNotEmpty()
        if (detailed()) {
            pvT.set(PV_SONG, cap, mc); pvA.set(PV_ARTIST, cap, mc)
            return intArrayOf(cardW(mc, pvT, pvA, pvL, PV_TIME, has), cardH(has))
        }
        pvT.set(PV_TITLE, cap, mc)
        return intArrayOf(boxW(pvT, pvL, has), boxH(has))
    }

    private fun bars(ctx: GuiGraphicsExtractor, x: Int, y: Int, playing: Boolean, color: Int, now: Long) {
        for (b in 0 until 3) {
            val h = if (playing) 2 + ((sin(now / 140.0 + b * 1.7) + 1) * 2.5).toInt() else 2
            ctx.fill(x + b * 3, y + 9 - h, x + b * 3 + 2, y + 9, color)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor) {
        if (!FishSettings.spotifyLyricsEnabled) { if (running) stop(); return }
        if (proc == null) { start(); return }
        val mc = Minecraft.getInstance()
        val nowNs = System.nanoTime()
        val dt = if (lastNs == 0L) 0L else (nowNs - lastNs) / 1_000_000L
        lastNs = nowNs
        if (mc.hudHidden || mc.player == null || FishHudEditor.isOpen()) return
        val t = title
        val m = media
        val playing = !isIdle(t)
        if (playing) {
            if (t != track) { track = t; playMs = 0; lyrics = null; fetchedKey = "" }
            else playMs += dt.coerceAtMost(500)
            // Real position from the media session keeps lyrics in sync
            if (m != null && m.playing) playMs = m.posMs + (nowNs - m.atNs) / 1_000_000L
            if (FishSettings.spotifyLyricsLyrics && fetchedKey != t) { fetchedKey = t; fetchLyrics(t) }
        }
        val useCard = detailed() && m != null && m.song.isNotEmpty()
        val titleCard = detailed() && !useCard && playing
        if (useCard) syncArt(m!!.artVer)
        val shown = if (playing) t else if (FishSettings.spotifyLyricsShowPaused) "Paused" else ""
        if (shown.isEmpty()) return

        val sc = FishSettings.spotifyLyricsScale.toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(FishSettings.spotifyLyricsX.toFloat(), FishSettings.spotifyLyricsY.toFloat())
        pose.scale(sc, sc)
        val lyric = if (playing && FishSettings.spotifyLyricsLyrics) currentLyric() else ""
        if (useCard) card(ctx, mc, titleMarq, artistMarq, lyricMarq, m!!.song, m.artist, timeText(m, playing, nowNs), lyric, playing, artId)
        else if (titleCard) {
            val i = t.indexOf(" - ")
            val artist = if (i > 0) t.substring(0, i).trim() else ""
            val song = if (i > 0) t.substring(i + 3).trim() else t
            card(ctx, mc, titleMarq, artistMarq, lyricMarq, song, artist, "", lyric, playing, null)
        }
        else body(ctx, mc, titleMarq, lyricMarq, shown, lyric, playing)
        pose.popMatrix()
    }

}

package fishmod.features.extras

import com.mojang.blaze3d.platform.NativeImage
import fishmod.features.FishHudEditor
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as S
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

// Now-playing HUD fed by the Windows media session (no login); one helper process, only while enabled
object SpotifyHud {
    private class Track(val title: String, val artist: String, val album: String,
                        val posMs: Long, val durMs: Long, val playing: Boolean, val at: Long)

    private val latest = AtomicReference<Track?>(null)
    @Volatile private var artImage: NativeImage? = null
    private var artGenSeen = -1
    private val artId = Identifier.fromNamespaceAndPath("fishmod", "spotify_art")
    private var artTex: DynamicTexture? = null
    private var proc: Process? = null
    @Volatile private var running = false
    private val artFile = File(System.getProperty("java.io.tmpdir"), "fishmod_spotify_art.bin")
    private const val ART = 40
    private const val W = 150
    private val isWin = System.getProperty("os.name").lowercase().contains("win")

    fun init() {
        FishHudEditor.register("Spotify", { S.spotifyHudX }, { S.spotifyHudX = it }, { S.spotifyHudY }, { S.spotifyHudY = it },
            W, ART, { S.spotifyHudScale }, { S.spotifyHudScale = it })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spotify_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("SpotifyHud.1", "spotify hud render failed", t) }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { stop() }
    }

    // Started lazily from the draw call, so a disabled HUD costs nothing
    private fun ensureRunning() {
        if (running || !isWin) return
        running = true
        artGenSeen = -1
        Thread({ pump() }, "FishMod-Spotify").also { it.isDaemon = true }.start()
    }

    private fun stop() {
        running = false
        proc?.destroyForcibly()
        proc = null
        latest.set(null)
    }

    private fun pump() {
        try {
            val script = SpotifyHud::class.java.getResourceAsStream("/assets/fishmod/spotify_smtc.ps1")!!.use { String(it.readBytes(), Charsets.UTF_8) }
            // -EncodedCommand rejects extra args (prints help), so run from a temp file
            val sf = File(System.getProperty("java.io.tmpdir"), "fishmod_spotify_smtc.ps1")
            sf.writeText(script, Charsets.UTF_8)
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File", sf.absolutePath, artFile.absolutePath).start()
            proc = p
            p.errorStream.close()
            BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { r ->
                while (running) parse(r.readLine() ?: break)
            }
        } catch (t: Throwable) {
            FishDiag.fail("SpotifyHud.2", "spotify helper failed", t)
        } finally {
            proc?.destroyForcibly(); proc = null; running = false; latest.set(null)
        }
    }

    private fun parse(line: String) {
        val f = line.trim().split(' ')
        if (f.size < 7) { latest.set(null); return }
        val dec = Base64.getDecoder()
        // Stray non-protocol helper output is skipped, not fatal
        val title: String; val artist: String; val album: String
        try { title = String(dec.decode(f[0]), Charsets.UTF_8); artist = String(dec.decode(f[1]), Charsets.UTF_8); album = String(dec.decode(f[2]), Charsets.UTF_8) } catch (_: IllegalArgumentException) { return }
        val gen = f[6].toIntOrNull() ?: 0
        if (gen != artGenSeen) { artGenSeen = gen; if (gen > 0 && artFile.exists()) loadArt() }
        latest.set(Track(title, artist, album, f[3].toLongOrNull() ?: 0, f[4].toLongOrNull() ?: 0, f[5] == "1", System.currentTimeMillis()))
    }

    // Decode and downscale off-thread; the render thread only uploads the finished image once per track
    private fun loadArt() {
        try {
            val src = ImageIO.read(artFile) ?: return
            val buf = BufferedImage(ART, ART, BufferedImage.TYPE_INT_ARGB)
            val g = buf.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(src, 0, 0, ART, ART, null); g.dispose()
            val img = NativeImage(NativeImage.Format.RGBA, ART, ART, false)
            for (y in 0 until ART) for (x in 0 until ART) {
                val c = buf.getRGB(x, y)
                img.setPixelABGR(x, y, (c and 0xFF00FF00.toInt()) or ((c shr 16) and 0xFF) or ((c and 0xFF) shl 16))
            }
            artImage?.close()
            artImage = img
        } catch (t: Throwable) {
            FishDiag.fail("SpotifyHud.3", "spotify art decode failed", t)
        }
    }

    // Render thread: swap in a pending image (once per track)
    private fun uploadArt(mc: Minecraft) {
        val img = artImage ?: return
        artImage = null
        if (artTex != null) mc.textureManager.release(artId)
        val tex = DynamicTexture({ artId.toString() }, img)
        mc.textureManager.register(artId, tex)
        artTex = tex
    }

    private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

    private fun render(ctx: GuiGraphicsExtractor) {
        if (!S.spotifyHud) { if (running) stop(); return }
        if (!isWin) return
        ensureRunning()
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        val t = latest.get() ?: return
        if (artImage != null) uploadArt(mc)
        draw(ctx, mc, t, artTex != null)
    }

    @JvmStatic fun previewSize() = intArrayOf(W, ART)

    // HUD editor example
    @JvmStatic fun drawPreview(ctx: GuiGraphicsExtractor) {
        draw(ctx, Minecraft.getInstance(), Track("Song Title", "Artist", "Album", 83000, 200000, true, System.currentTimeMillis()), false, true)
    }

    private fun draw(ctx: GuiGraphicsExtractor, mc: Minecraft, t: Track, hasArt: Boolean, preview: Boolean = false) {
        val shown = t.posMs + if (t.playing) System.currentTimeMillis() - t.at else 0L
        val pos = if (t.durMs > 0) shown.coerceIn(0, t.durMs) else shown
        val art = S.spotifyHudArt && hasArt
        val tx = if (art) ART + 5 else 4
        val maxW = W - tx - 3
        val bg = (S.spotifyHudBgOpacity.coerceIn(0, 100) * 255 / 100) shl 24
        ctx.pose().pushMatrix()
        try {
            if (!preview) {
                val sc = S.spotifyHudScale.toFloat()
                ctx.pose().translate(S.spotifyHudX.toFloat(), S.spotifyHudY.toFloat())
                ctx.pose().scale(sc, sc)
            }
            if (bg != 0) ctx.fill(0, 0, W, ART, bg)
            if (art) ctx.blit(RenderPipelines.GUI_TEXTURED, artId, 0, 0, 0f, 0f, ART, ART, ART, ART, -1)
            ctx.text(mc.font, clip(mc, t.title, maxW), tx, 4, S.spotifyHudTitleColor, true)
            val sub = if (S.spotifyHudAlbum && t.album.isNotEmpty()) "${t.artist} - ${t.album}" else t.artist
            ctx.text(mc.font, clip(mc, sub, maxW), tx, 15, S.spotifyHudTextColor, true)
            if (S.spotifyHudProgress && t.durMs > 0) {
                val by = ART - 7
                ctx.fill(tx, by, tx + maxW, by + 2, 0x66FFFFFF)
                ctx.fill(tx, by, tx + (maxW * pos / t.durMs).toInt(), by + 2, S.spotifyHudBarColor)
                ctx.text(mc.font, fmt(pos) + (if (t.playing) "" else " (paused)") + " / " + fmt(t.durMs), tx, by - 10, S.spotifyHudTextColor, false)
            }
        } finally {
            ctx.pose().popMatrix()
        }
    }

    private fun clip(mc: Minecraft, s: String, maxW: Int): String =
        if (mc.font.width(s) <= maxW) s else mc.font.plainSubstrByWidth(s, maxW - mc.font.width("...")) + "..."
}

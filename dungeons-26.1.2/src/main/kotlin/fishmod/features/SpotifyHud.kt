package fishmod.features

import fishmod.compat.hudHidden
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.io.BufferedReader
import java.io.InputStreamReader

// Now-playing line from the local Spotify desktop window title; no account, no API, Windows only
object SpotifyHud {
    private const val NAME = "Spotify"
    private const val PAD = 3
    private const val SCRIPT = "while(\$true){\$t=(Get-Process Spotify -ErrorAction SilentlyContinue|Where-Object{\$_.MainWindowTitle}|Select-Object -First 1).MainWindowTitle;[Console]::Out.WriteLine(\$t);[Console]::Out.Flush();Start-Sleep -Seconds 2}"

    // Written by the reader thread, read by render; a swapped String reference is enough
    @Volatile private var title: String = ""
    @Volatile private var proc: Process? = null

    // Render-side cache, rebuilt only when the title or settings change
    private var seen: String? = null
    private var seenIcon = false
    private var seenPaused = false
    private var text: String = ""
    private var width = 0

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

    private fun stop() { proc?.destroy(); proc = null; title = "" }

    // Spotify shows these while idle or paused
    private fun isIdle(t: String) = t.isEmpty() || t == "Spotify" || t == "Spotify Free" || t == "Spotify Premium"

    private fun refresh(t: String, mc: Minecraft) {
        seen = t; seenIcon = FishSettings.spotifyHudIcon; seenPaused = FishSettings.spotifyHudShowPaused
        val s = if (!isIdle(t)) t else if (seenPaused && proc != null) "Paused" else ""
        text = if (s.isEmpty()) "" else if (seenIcon) "♫ $s" else s
        width = if (text.isEmpty()) 0 else mc.font.width(text)
    }

    private fun draw(ctx: GuiGraphicsExtractor) {
        if (!FishSettings.spotifyHudEnabled) { if (proc != null) stop(); return }
        if (proc == null) { start(); return }
        val mc = Minecraft.getInstance()
        if (mc.hudHidden || mc.player == null || FishHudEditor.isOpen()) return
        val t = title
        if (t != seen || seenIcon != FishSettings.spotifyHudIcon || seenPaused != FishSettings.spotifyHudShowPaused) refresh(t, mc)
        if (width == 0) return
        val sc = FishSettings.spotifyHudScale.toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(FishSettings.spotifyHudX.toFloat(), FishSettings.spotifyHudY.toFloat())
        pose.scale(sc, sc)
        if (FishSettings.spotifyHudBackground) ctx.fill(0, 0, width + PAD * 2, 9 + PAD * 2, FishSettings.spotifyHudBgColor)
        ctx.text(mc.font, text, PAD, PAD, FishSettings.spotifyHudTextColor, true)
        pose.popMatrix()
    }
}

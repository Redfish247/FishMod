package fishmod.utils.update

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.Easing
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import fishmod.utils.update.UpdateManager.DownloadState as DS

class UpdateScreen(private val release: UpdateManager.Release) : Screen(Component.literal("FishMod Update")), HasUiOverlay {

    private companion object {
        private const val SCRIM = 0x99000000.toInt()
        private const val BG = 0xFF12151B.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val CARD = 0xFF181C23.toInt()
        private const val CARD_HOVER = 0xFF1E232C.toInt()
        private const val LOG_BG = 0xFF0B0E12.toInt()
        private const val ON_ACCENT = 0xFF06302F.toInt()
        private const val SUCCESS = 0xFF4FD08A.toInt()
        private const val LOG_SIZE = 6.5f
        private const val LOG_LINE = 9
    }

    private val panelW = 270
    private var panelH = 214
    private val pad = 12
    private val btnH = 20
    private var panelX = 0
    private var panelY = 0
    private var logY = 0
    private var logH = 0
    private var btnY = 0
    private var updateX = 0; private val updateW = 96
    private var laterX = 0; private val laterW = 54
    private var skipX = 0; private val skipW = 84
    private var lines: List<String> = emptyList()
    private var scroll = 0f
    private val open = Easing.Anim(180).apply { setTarget(true) }
    private val targetUrl = UpdateManager.modrinthUrl ?: release.htmlUrl.ifEmpty { null }
    private val canInstall = UpdateManager.canInstall(release)

    override fun init() {
        val vw = (this.width / UiScale.factor()).toInt()
        val vh = (this.height / UiScale.factor()).toInt()
        panelH = minOf(214, vh - 16).coerceAtLeast(150)
        panelX = (vw - panelW) / 2
        panelY = maxOf(8, (vh - panelH) / 2)
        logY = panelY + 50
        btnY = panelY + panelH - pad - btnH
        logH = btnY - 10 - logY
        updateX = panelX + panelW - pad - updateW
        laterX = updateX - 6 - laterW
        skipX = panelX + pad
        if (lines.isEmpty()) lines = wrap(changelog(release.body), panelW - pad * 2 - 16)
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    private fun maxScroll() = maxOf(0f, (lines.size * LOG_LINE + 12 - logH).toFloat())

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx <= x + w && my >= y && my <= y + h

    private fun onButtons(mx: Int, my: Int) = my >= btnY - 4

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, updateX, btnY, updateW, btnH)) { onUpdate(); return true }
        if (inside(mx, my, laterX, btnY, laterW, btnH)) { onClose(); return true }
        if (inside(mx, my, skipX, btnY, skipW, btnH)) { UpdateManager.dismiss(release); onClose(); return true }
        if (inside(mx, my, panelX, panelY, panelW, panelH) && !onButtons(mx, my)) { openPage(); return true }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll - verticalAmount.toFloat() * LOG_LINE * 2).coerceIn(0f, maxScroll())
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { onUpdate(); return true }
        return super.keyPressed(input)
    }

    private fun onUpdate() {
        if (!canInstall) { openPage(); return }
        when (UpdateManager.downloadState) {
            DS.IDLE, DS.FAILED -> UpdateManager.download(release)
            DS.STAGED -> Minecraft.getInstance().stop()
            else -> {}
        }
    }

    private fun openPage() {
        val url = targetUrl ?: return
        try { Util.getPlatform().openUri(url) } catch (ignored: Throwable) {}
    }

    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        val p = open.progress()
        UiRecorder.clear()
        val vw = this.width / UiScale.factor()
        val vh = this.height / UiScale.factor()
        UiRecorder.fillRect(0f, 0f, vw, vh, fade(SCRIM, p))

        val x = panelX
        val y = panelY + ((1f - p) * 10f).toInt()
        val dy = y - panelY
        val innerW = panelW - pad * 2
        val bodyHov = targetUrl != null && inside(mx, my, panelX, panelY, panelW, panelH) && !onButtons(mx, my)

        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), panelW.toFloat(), panelH.toFloat(), 10f, 16f, fade(0x70000000, p))
        ScreenTheme.nPanel(x, y, x + panelW, y + panelH, 10, fade(BG, p), fade(if (bodyHov) ScreenTheme.ACCENT else BORDER, p))
        UiRecorder.fillRect((x + 10).toFloat(), y.toFloat(), (panelW - 20).toFloat(), 2f, fade(ScreenTheme.ACCENT, p))

        // Header: arrow glyph, title, version delta, page link.
        ScreenTheme.nRoundedRect(x + pad, y + 12, 24, 24, 6, fade(0x2424B6B0, p))
        UiRecorder.fillRect((x + pad + 11).toFloat(), (y + 17).toFloat(), 2f, 11f, fade(ScreenTheme.ACCENT, p))
        UiRecorder.chevron((x + pad + 8).toFloat(), (y + 27).toFloat(), true, fade(ScreenTheme.ACCENT, p))
        val tx = (x + pad + 32).toFloat()
        UiRecorder.textBold("FishMod", tx, (y + 13).toFloat(), 9f, fade(ScreenTheme.TEXT_COLOR, p))
        UiRecorder.textBold("Update", tx + UiRecorder.textWidthBold("FishMod", 9f) + UiRecorder.textWidth(" ", 9f) + 1f, (y + 13).toFloat(), 9f, fade(ScreenTheme.ACCENT, p))
        UiRecorder.text("v${UpdateManager.currentVersion}  →  v${release.version}", tx, (y + 26).toFloat(), 6.5f, fade(ScreenTheme.SUBTEXT_COLOR, p))
        if (targetUrl != null) {
            val label = if (UpdateManager.modrinthUrl != null) "Modrinth" else "GitHub"
            val lw = UiRecorder.textWidth(label, 6.5f)
            val lx = x + panelW - pad - lw - 10
            val col = fade(if (bodyHov) ScreenTheme.ACCENT_HOVER else ScreenTheme.SUBTEXT_COLOR, p)
            UiRecorder.text(label, lx, (y + 16).toFloat(), 6.5f, col)
            UiRecorder.popOutIcon(lx + lw + 3f, (y + 16.5f), 6f, col)
        }
        ScreenTheme.nRect(x + pad, y + 44, innerW, 1, fade(BORDER, p))

        // Changelog (scrolls; clicking anywhere outside the buttons opens the mod page).
        val ly = logY + dy
        ScreenTheme.nRoundedRect(x + pad, ly, innerW, logH, 6, fade(LOG_BG, p))
        UiRecorder.pushScissor((x + pad).toFloat(), ly.toFloat(), innerW.toFloat(), logH.toFloat())
        var lineY = ly + 6 - scroll
        for (line in lines) {
            if (lineY > ly - LOG_LINE && lineY < ly + logH) {
                val heading = line.startsWith("\u0001")
                val s = if (heading) line.substring(1) else line
                if (heading) UiRecorder.textBold(s, (x + pad + 8).toFloat(), lineY, LOG_SIZE + 0.5f, fade(ScreenTheme.TEXT_COLOR, p))
                else UiRecorder.text(s, (x + pad + 8).toFloat(), lineY, LOG_SIZE, fade(0xFFB9C2CC.toInt(), p))
            }
            lineY += LOG_LINE
        }
        UiRecorder.popScissor()
        val max = maxScroll()
        if (max > 0f) {
            val trackH = logH - 8f
            val thumbH = maxOf(14f, trackH * logH / (logH + max))
            val thumbY = ly + 4f + (trackH - thumbH) * (scroll / max)
            UiRecorder.fillPillBar((x + pad + innerW - 5).toFloat(), thumbY, 2f, thumbH, fade(0x44FFFFFF, p))
        }
        if (bodyHov) {
            val hint = "Click to open on ${if (UpdateManager.modrinthUrl != null) "Modrinth" else "GitHub"}"
            val hw = UiRecorder.textWidth(hint, 6f)
            UiRecorder.fillRoundedRect(x + panelW / 2f - hw / 2f - 6f, ly + logH - 14f, hw + 12f, 11f, 5.5f, 0xCC000000.toInt())
            UiRecorder.text(hint, x + panelW / 2f - hw / 2f, ly + logH - 11.5f, 6f, ScreenTheme.ACCENT_HOVER)
        }

        // Buttons.
        val by = btnY + dy
        val sHov = inside(mx, my, skipX, btnY, skipW, btnH)
        ScreenTheme.nRoundedRectRing(skipX, by, skipW, btnH, btnH / 2, 1, fade(if (sHov) CARD_HOVER else CARD, p), fade(BORDER, p))
        centered("Don't show again", skipX + skipW / 2, by, fade(if (sHov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR, p))

        val lHov = inside(mx, my, laterX, btnY, laterW, btnH)
        ScreenTheme.nRoundedRectRing(laterX, by, laterW, btnH, btnH / 2, 1, fade(if (lHov) CARD_HOVER else CARD, p), fade(BORDER, p))
        centered("Later", laterX + laterW / 2, by, fade(if (lHov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR, p))

        drawUpdateButton(inside(mx, my, updateX, btnY, updateW, btnH), by, p)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawUpdateButton(hov: Boolean, by: Int, p: Float) {
        val state = UpdateManager.downloadState
        when {
            !canInstall -> {
                ScreenTheme.nRoundedRect(updateX, by, updateW, btnH, btnH / 2, fade(if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT, p))
                centered("Open page", updateX + updateW / 2, by, fade(ON_ACCENT, p))
            }
            state == DS.DOWNLOADING -> {
                ScreenTheme.nRoundedRectRing(updateX, by, updateW, btnH, btnH / 2, 1, fade(CARD, p), fade(ScreenTheme.ACCENT, p))
                val fillW = ((updateW - 2) * UpdateManager.downloadProgress).toInt()
                if (fillW > btnH / 2) ScreenTheme.nRoundedRect(updateX + 1, by + 1, fillW, btnH - 2, (btnH - 2) / 2, fade(0x5524B6B0, p))
                centered("Downloading ${(UpdateManager.downloadProgress * 100).toInt()}%", updateX + updateW / 2, by, fade(ScreenTheme.TEXT_COLOR, p))
            }
            state == DS.STAGED -> {
                ScreenTheme.nRoundedRectRing(updateX, by, updateW, btnH, btnH / 2, 1, fade(if (hov) CARD_HOVER else CARD, p), fade(SUCCESS, p))
                centered(if (hov) "Close game" else "Restart to apply", updateX + updateW / 2, by, fade(SUCCESS, p))
            }
            state == DS.FAILED -> {
                ScreenTheme.nRoundedRect(updateX, by, updateW, btnH, btnH / 2, fade(if (hov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER, p))
                centered("Retry", updateX + updateW / 2, by, fade(0xFF2A0A0A.toInt(), p))
                val err = UpdateManager.downloadError ?: "Download failed"
                UiRecorder.text(err, updateX.toFloat(), (by - 10).toFloat(), 6f, fade(ScreenTheme.DANGER, p))
            }
            else -> {
                ScreenTheme.nRoundedRect(updateX, by, updateW, btnH, btnH / 2, fade(if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT, p))
                centered("Update", updateX + updateW / 2, by, fade(ON_ACCENT, p))
            }
        }
    }

    private fun centered(s: String, cx: Int, y: Int, color: Int) {
        val size = 7.5f
        UiRecorder.textBold(s, cx - UiRecorder.textWidthBold(s, size) / 2f, y + (btnH - size) / 2f, size, color)
    }

    private fun fade(color: Int, p: Float): Int {
        val a = ((color ushr 24) * p).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    // GitHub markdown → plain lines; headings are tagged with \u0001 so they render bold.
    private fun changelog(body: String): List<String> {
        val out = ArrayList<String>()
        for (raw in body.replace("\r", "").lines()) {
            var s = raw.trim()
            if (s.startsWith("```") || s.matches(Regex("^[-*_]{3,}$"))) continue
            s = s.replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
                .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
                .replace(Regex("<[^>]+>"), "")
                .replace("**", "").replace("__", "").replace("`", "")
            val heading = s.startsWith("#")
            s = s.trimStart('#', ' ')
            s = s.replace(Regex("""^[-*+]\s+"""), "• ")
            if (s.isEmpty()) { if (out.isNotEmpty() && out.last().isNotEmpty()) out.add(""); continue }
            out.add(if (heading) "\u0001$s" else s)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        if (out.isEmpty()) out.add(release.name.ifBlank { "A new version of FishMod is available." })
        return out
    }

    private fun wrap(src: List<String>, maxW: Int): List<String> {
        val out = ArrayList<String>()
        for (line in src) {
            val heading = line.startsWith("\u0001")
            val size = if (heading) LOG_SIZE + 0.5f else LOG_SIZE
            val text = if (heading) line.substring(1) else line
            val indent = if (text.startsWith("• ")) "   " else ""
            var cur = StringBuilder()
            for (word in text.split(' ')) {
                val trial = if (cur.isEmpty()) word else "$cur $word"
                if (cur.isNotEmpty() && (if (heading) UiRecorder.textWidthBold(trial, size) else UiRecorder.textWidth(trial, size)) > maxW) {
                    out.add((if (heading) "\u0001" else "") + cur)
                    cur = StringBuilder(indent).append(word)
                } else {
                    cur = StringBuilder(trial)
                }
            }
            out.add((if (heading) "\u0001" else "") + cur)
            if (out.size > 400) break
        }
        return out
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(null)
    }

    override fun paintUiOverlay() {
        UiRenderer.paint(this.width, this.height, UiScale.factor())
    }

    override fun isPauseScreen(): Boolean = false
}

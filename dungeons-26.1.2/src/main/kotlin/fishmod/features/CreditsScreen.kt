package fishmod.features

import fishmod.utils.rendering.NvgContext
import fishmod.utils.rendering.NvgGlStateGuard
import fishmod.utils.rendering.NvgRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.lwjgl.nanovg.NanoVG
import kotlin.math.min

/** Small centered credits panel (matches the FishMod overlay style). */
class CreditsScreen(private val parent: Screen?) : Screen(Component.literal("Credits")), HasNvgOverlay {

    companion object {
        private const val ACCENT = 0xFF24B6B0.toInt()
        private const val ACCENT_HOVER = 0xFF3AD8D1.toInt()
        private const val BG_TOP = 0xFF11181F.toInt()
        private const val BG_BOT = 0xFF070B0F.toInt()
        private const val BORDER = 0xFF262F3A.toInt()
        private const val ROW_BG = 0xFF141B22.toInt()
        private const val ROW_BORDER = 0xFF232D38.toInt()
        private const val TEXT = 0xFFEDF1F5.toInt()
        private const val SUBTEXT = 0xFF8A96A3.toInt()
        private const val SCRIM = 0xB3000000.toInt()
        private const val DISCORD_BLURPLE = 0xFF5865F2.toInt()
        private const val DISCORD_BLURPLE_HOVER = 0xFF7289FF.toInt()
        private const val DISCORD = "discord.gg/3mSuQUB8kk"
        private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"

        private data class Credit(val name: String, val role: String, val badgeColor: Int)

        private val CREDITS = listOf(
            Credit("RedFish", "creator - everything else", 0xFF24B6B0.toInt()),
            Credit("BladeMasterGabe", "splits & dungeon features", 0xFFE0A63A.toInt()),
            Credit("22yrs", "shared the dungeon map and blessed the port", 0xFF7A8CE0.toInt()),
        )
    }

    private var backX = 0
    private var backY = 0
    private var backW = 0
    private var backH = 0
    private var linkX = 0
    private var linkY = 0
    private var linkW = 0
    private var linkH = 0

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun pw(): Int = min(340, vw() - 20)
    private fun ph(): Int = min(356, vh() - 20)
    private fun px(): Int = (vw() - pw()) / 2
    private fun py(): Int = (vh() - ph()) / 2

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX)
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY)
        NvgRecorder.clear()
        // virtual space so replay()'s uniform scale restores the scrim to full-screen
        ScreenTheme.nRect(0, 0, vw(), vh(), SCRIM)

        val lx = px()
        val ty = py()
        val rx = lx + pw()
        val by = ty + ph()
        val cx = (lx + rx) / 2
        val panelR = 10

        NvgRecorder.dropShadow(lx.toFloat(), ty.toFloat(), (rx - lx).toFloat(), (by - ty).toFloat(), panelR.toFloat(), 16f, 0x70000000)
        NvgRecorder.fillRectVGradient(lx.toFloat(), ty.toFloat(), (rx - lx).toFloat(), (by - ty).toFloat(), BG_TOP, BG_BOT, panelR.toFloat())
        ScreenTheme.nRoundedRectRing(lx, ty, rx - lx, by - ty, panelR, 1, 0, BORDER)
        NvgRecorder.fillRect((lx + panelR).toFloat(), ty.toFloat(), (rx - lx - 2 * panelR).toFloat(), 3f, ACCENT)

        centeredNst("FishMod Credits", cx, ty + 20, TEXT, 0.9f)
        ScreenTheme.nRect(lx + 24, ty + 40, rx - lx - 48, 1, BORDER)

        val rowH = 46
        val rowGap = 9
        val rowX = lx + 18
        val rowW = rx - lx - 36
        var y = ty + 52
        for (c in CREDITS) {
            drawCreditRow(rowX, y, rowW, rowH, c.name, c.role, c.badgeColor)
            y += rowH + rowGap
        }

        backW = 92
        backH = 26
        backX = cx - backW / 2
        backY = by - 20 - backH
        val backHov = inside(mouseX, mouseY, backX, backY, backW, backH)
        ScreenTheme.nRoundedRect(backX, backY, backW, backH, 5, if (backHov) ACCENT_HOVER else ACCENT)
        centeredNst("Back", cx, backY + (backH - 10) / 2, 0xFF052A29.toInt(), 0.85f)

        linkH = 24
        linkW = ScreenTheme.nstw(DISCORD, 0.7f) + 28
        linkX = cx - linkW / 2
        linkY = backY - 12 - linkH
        val linkHov = inside(mouseX, mouseY, linkX, linkY, linkW, linkH)
        NvgRecorder.fillRoundedRect(linkX.toFloat(), linkY.toFloat(), linkW.toFloat(), linkH.toFloat(), linkH / 2f, if (linkHov) 0xFF1B2733.toInt() else 0xFF131B22.toInt())
        ScreenTheme.nRoundedRectRing(linkX, linkY, linkW, linkH, linkH / 2, 1, 0, if (linkHov) DISCORD_BLURPLE_HOVER else DISCORD_BLURPLE)
        centeredNst(DISCORD, cx, linkY + (linkH - 10) / 2, if (linkHov) DISCORD_BLURPLE_HOVER else DISCORD_BLURPLE, 0.7f)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun centeredNst(s: String, cx: Int, y: Int, color: Int, scale: Float = ScreenTheme.TEXT_SCALE) {
        ScreenTheme.nst(s, cx - ScreenTheme.nstw(s, scale) / 2, y, color, scale)
    }

    private fun drawCreditRow(x: Int, y: Int, w: Int, h: Int, name: String, role: String, badgeColor: Int) {
        NvgRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 6f, ROW_BG)
        ScreenTheme.nRoundedRectRing(x, y, w, h, 6, 1, 0, ROW_BORDER)

        val badgeR = 12
        val badgeCx = x + 20
        val badgeCy = y + h / 2
        NvgRecorder.disc(badgeCx.toFloat(), badgeCy.toFloat(), badgeR.toFloat(), badgeColor)
        centeredNst(name.take(1).uppercase(), badgeCx, badgeCy - 5, 0xFF06121A.toInt(), 0.8f)

        val textX = x + 42
        ScreenTheme.nst(name, textX, y + 8, TEXT, 0.85f)
        ScreenTheme.nst(role, textX, y + 25, SUBTEXT, 0.62f)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(click.x())
        val my = fishmod.utils.rendering.UiScale.vx(click.y())
        if (inside(mx, my, backX, backY, backW, backH)) {
            onClose()
            return true
        }
        if (inside(mx, my, linkX, linkY, linkW, linkH)) {
            try {
                Util.getPlatform().openUri(DISCORD_URL)
            } catch (ignored: Throwable) {
            }
            return true
        }
        return super.mouseClicked(click, bl)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay(fishmod.utils.rendering.UiScale.factor())
            NanoVG.nvgEndFrame(ctx)
        } catch (t: Throwable) {
            if (!nvgFailureLogged) {
                nvgFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] CreditsScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }
}

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

/** Small centered credits panel (matches the FishMod overlay style) with a clickable Discord link. */
class CreditsScreen(private val parent: Screen?) : Screen(Component.literal("Credits")), HasNvgOverlay {

    companion object {
        private const val ACCENT = 0xFF24B6B0.toInt()
        private const val ACCENT_HOVER = 0xFF3AD8D1.toInt()
        private const val BG_TOP = 0xFF0C1318.toInt()
        private const val BG_BOT = 0xFF06090C.toInt()
        private const val BORDER = 0xFF24333C.toInt()
        private const val DIVIDER = 0xFF18222C.toInt()
        private const val TEXT = 0xFFEDF1F5.toInt()
        private const val SUBTEXT = 0xFF7E8A98.toInt()
        private const val SCRIM = 0xB3000000.toInt()
        private const val DISCORD_BLURPLE = 0xFF5865F2.toInt()

        private const val DISCORD = "discord.gg/3mSuQUB8kk"
        private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"
    }

    // hit rects (set during render, read on click)
    private var linkX = 0
    private var linkY = 0
    private var linkW = 0
    private var linkH = 0
    private var backX = 0
    private var backY = 0
    private var backW = 0
    private var backH = 0

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun pw(): Int = min(360, this.width - 20)
    private fun ph(): Int = min(260, this.height - 20)
    private fun px(): Int = (this.width - pw()) / 2
    private fun py(): Int = (this.height - ph()) / 2

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        NvgRecorder.clear()
        ScreenTheme.nRect(0, 0, this.width, this.height, SCRIM)

        val lx = px()
        val ty = py()
        val rx = lx + pw()
        val by = ty + ph()
        val cx = (lx + rx) / 2
        NvgRecorder.fillRectVGradient(lx.toFloat(), ty.toFloat(), (rx - lx).toFloat(), (by - ty).toFloat(), BG_TOP, BG_BOT)
        ScreenTheme.nRect(lx, ty, rx - lx, 1, BORDER)
        ScreenTheme.nRect(lx, by - 1, rx - lx, 1, BORDER)
        ScreenTheme.nRect(lx, ty, 1, by - ty, BORDER)
        ScreenTheme.nRect(rx - 1, ty, 1, by - ty, BORDER)

        centeredNst("FishMod", cx, ty + 14, TEXT)
        centeredNst("Credits", cx, ty + 26, SUBTEXT, 0.5f)
        ScreenTheme.nRect(lx + 24, ty + 40, rx - lx - 48, 1, DIVIDER)

        var y = ty + 52
        drawCredit(lx + 26, y, "RedFish", "creator - everything else")
        y += 28
        drawCredit(lx + 26, y, "BladeMasterGabe", "splits & dungeon features")
        y += 28
        drawCredit(lx + 26, y, "Sushiest", "dungeon help & UI changes")
        y += 28
        drawCredit(lx + 26, y, "22yrs", "dungeon map, ported with permission")
        y += 28

        linkW = ScreenTheme.nstw(DISCORD) + 24
        linkH = 18
        linkX = cx - linkW / 2
        linkY = by - 60
        val linkHov = inside(mouseX, mouseY, linkX, linkY, linkW, linkH)
        ScreenTheme.nRect(linkX, linkY, linkW, linkH, if (linkHov) 0xFF1B2733.toInt() else 0xFF131B22.toInt())
        ScreenTheme.nRect(linkX, linkY, linkW, 1, if (linkHov) ACCENT_HOVER else DISCORD_BLURPLE)
        centeredNst(DISCORD, cx, linkY + 5, DISCORD_BLURPLE)

        backW = 72
        backH = 22
        backX = cx - backW / 2
        backY = by - 32
        val backHov = inside(mouseX, mouseY, backX, backY, backW, backH)
        ScreenTheme.nRoundedRect(backX, backY, backW, backH, 4, if (backHov) ACCENT_HOVER else ACCENT)
        centeredNst("Back", cx, backY + (backH - 8) / 2, 0xFF052A29.toInt())

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun centeredNst(s: String, cx: Int, y: Int, color: Int, scale: Float = ScreenTheme.TEXT_SCALE) {
        ScreenTheme.nst(s, cx - ScreenTheme.nstw(s, scale) / 2, y, color, scale)
    }

    private fun drawCredit(x: Int, y: Int, name: String, role: String) {
        ScreenTheme.nst(name, x, y, TEXT)
        ScreenTheme.nst(role, x + 6, y + 11, SUBTEXT, 0.5f)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
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

    // ── NanoVG overlay ───────────────────────────────────────────────────────────

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay()
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

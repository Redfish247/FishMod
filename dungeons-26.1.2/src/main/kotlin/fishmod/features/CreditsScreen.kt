package fishmod.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.min

/** Small centered credits panel (matches the FishMod overlay style) with a clickable Discord link. */
class CreditsScreen(private val parent: Screen?) : Screen(Component.literal("Credits")) {

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
        ctx.fill(0, 0, this.width, this.height, SCRIM)

        val lx = px()
        val ty = py()
        val rx = lx + pw()
        val by = ty + ph()
        val cx = (lx + rx) / 2
        ctx.fillGradient(lx, ty, rx, by, BG_TOP, BG_BOT)
        ctx.fill(lx, ty, rx, ty + 1, BORDER)
        ctx.fill(lx, by - 1, rx, by, BORDER)
        ctx.fill(lx, ty, lx + 1, by, BORDER)
        ctx.fill(rx - 1, ty, rx, by, BORDER)

        ctx.centeredText(this.font, Component.literal("§lFish§b§lMod"), cx, ty + 14, TEXT)
        ctx.centeredText(this.font, Component.literal("Credits"), cx, ty + 26, SUBTEXT)
        ctx.fill(lx + 24, ty + 40, rx - 24, ty + 41, DIVIDER)

        var y = ty + 52
        drawCredit(ctx, lx + 26, y, "RedFish", "creator — everything else")
        y += 28
        drawCredit(ctx, lx + 26, y, "BladeMasterGabe", "splits & dungeon features")
        y += 28
        drawCredit(ctx, lx + 26, y, "Sushiest", "dungeon help & UI changes")
        y += 28
        drawCredit(ctx, lx + 26, y, "22yrs", "dungeon map, ported with permission")
        y += 28

        linkW = this.font.width(DISCORD) + 24
        linkH = 18
        linkX = cx - linkW / 2
        linkY = by - 60
        val linkHov = inside(mouseX, mouseY, linkX, linkY, linkW, linkH)
        ctx.fill(linkX, linkY, linkX + linkW, linkY + linkH, if (linkHov) 0xFF1B2733.toInt() else 0xFF131B22.toInt())
        ctx.fill(linkX, linkY, linkX + linkW, linkY + 1, if (linkHov) ACCENT_HOVER else DISCORD_BLURPLE)
        ctx.centeredText(
            this.font,
            Component.literal((if (linkHov) "§b" else "§9") + DISCORD), cx, linkY + 5, DISCORD_BLURPLE
        )

        backW = 72
        backH = 22
        backX = cx - backW / 2
        backY = by - 32
        val backHov = inside(mouseX, mouseY, backX, backY, backW, backH)
        ctx.fill(backX, backY, backX + backW, backY + backH, if (backHov) ACCENT_HOVER else ACCENT)
        ctx.centeredText(this.font, Component.literal("Back"), cx, backY + (backH - 8) / 2, 0xFF052A29.toInt())

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawCredit(ctx: GuiGraphicsExtractor, x: Int, y: Int, name: String, role: String) {
        ctx.text(this.font, Component.literal(name), x, y, TEXT, false)
        ctx.text(this.font, Component.literal("§7$role"), x + 6, y + 11, SUBTEXT, false)
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
}

package fishmod.features

import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.min

class CreditsScreen(private val parent: Screen?) : Screen(Component.literal("Credits")), HasUiOverlay {

    companion object {
        private const val BG_TOP = 0xFF11181F.toInt()
        private const val BG_BOT = 0xFF070B0F.toInt()
        private const val BORDER = 0xFF262F3A.toInt()
        private const val CARD_BG = 0xFF141B22.toInt()
        private const val CARD_BORDER = 0xFF232D38.toInt()
        private const val BTN_BG = 0xFF182029.toInt()
        private const val BTN_BG_HOVER = 0xFF1F2A35.toInt()
        private const val SCRIM = 0xB3000000.toInt()
        private const val ON_ACCENT = 0xFF052A29.toInt()
        private const val DISCORD = "discord.gg/3mSuQUB8kk"
        private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"

        private const val NAME_SIZE = 8f
        private const val ROLE_SIZE = 6.2f
        private const val BULLET_SIZE = 6f
        private const val LINE_H = 9

        private data class Credit(
            val name: String,
            val role: String,
            val badgeColor: Int,
            val details: List<String> = emptyList(),
        )

        private val CREATOR = Credit("RedFish", "Creator. Everything else.", 0xFF24B6B0.toInt())

        private val CREDITS = listOf(
            Credit("BladeMasterGabe", "splits & dungeon features", 0xFFE0A63A.toInt()),
            Credit("22yrs", "shared the dungeon map and blessed the port", 0xFF7A8CE0.toInt()),
            Credit(
                "Odin (odtheking)", "puzzle solvers, terminals & QoL ports", 0xFFB05FE0.toInt(),
                details = listOf(
                    "Puzzle solvers: Blaze, Boulder, TP Maze, Weirdos, Water Board, Ice Fill, Beams, Quiz/Oruo",
                    "F7 terminal, arrow align & simon says solvers",
                    "Etherwarp helper, extra stats, blessing display",
                    "Invincibility timer, render optimizer",
                    "AutoRequeue trigger, /dwp waypoint editor",
                ),
            ),
            Credit(
                "NoammAddons", "storage overlay, party finder & QoL ports", 0xFF5FD1E0.toInt(),
                details = listOf(
                    "Storage overlay, party finder auto-kick + in-menu head overlay/tooltip stats",
                    "Chat filter, leap menu, item price tooltip",
                    "Lava to water, ice fill, gyro helper",
                    "Wither Highlight, M7 relics, block overlay",
                    "Camera tweaks, time changer, arrow hit sound",
                    "Wither dragons (floor7), scrollable item tooltip",
                ),
            ),
        )
    }

    private var backX = 0
    private var backY = 0
    private val backW = 56
    private val backH = 18
    private var copyX = 0
    private var copyY = 0
    private val copyW = 40
    private val copyH = 16
    private var linkX = 0
    private var linkY = 0
    private var linkW = 0
    private var linkH = 0
    private var bodyY = 0
    private var bodyH = 0
    private var scroll = 0
    private var contentH = 0
    private var copiedAt = 0L

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / UiScale.factor()).toInt()
    private fun pw(): Int = min(440, vw() - 20)

    private val PAD = 14
    private val GAP = 8
    private val HEADER_H = 40
    private val CREATOR_H = 44

    private fun colW(): Int = (pw() - PAD * 2 - GAP) / 2

    private fun wrap(s: String, maxW: Float, size: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && UiRecorder.textWidth(next, size) > maxW) {
                out.add(line)
                line = word
            } else line = next
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    private fun textX(cardX: Int): Int = cardX + 10 + 22 + 8
    private fun roleLines(c: Credit, cardW: Int): List<String> = wrap(c.role, (cardW - 48).toFloat(), ROLE_SIZE)
    private fun headH(c: Credit, cardW: Int): Int = max(22, 10 + roleLines(c, cardW).size * LINE_H)
    private fun bulletLines(c: Credit, cardW: Int): List<List<String>> =
        c.details.map { wrap(it, (cardW - 20 - 8).toFloat(), BULLET_SIZE) }

    private fun cardHeight(c: Credit, cardW: Int): Int {
        var h = 10 + headH(c, cardW) + 10
        val bullets = bulletLines(c, cardW)
        if (bullets.isNotEmpty()) h += 8 + bullets.sumOf { it.size } * LINE_H
        return h
    }

    private fun gridHeight(): Int {
        val cw = colW()
        var total = 0
        var i = 0
        while (i < CREDITS.size) {
            val a = cardHeight(CREDITS[i], cw)
            val b = if (i + 1 < CREDITS.size) cardHeight(CREDITS[i + 1], cw) else 0
            total += max(a, b) + GAP
            i += 2
        }
        return total - GAP
    }

    private fun ph(): Int = min(HEADER_H + PAD + CREATOR_H + GAP + gridHeight() + PAD, vh() - 20)
    private fun px(): Int = (vw() - pw()) / 2
    private fun py(): Int = (vh() - ph()) / 2
    private fun maxScroll(): Int = max(0, contentH - bodyH)

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = UiScale.vx(mouseX)
        val mouseY = UiScale.vx(mouseY)
        UiRecorder.clear()
        ScreenTheme.nRect(0, 0, vw(), vh(), SCRIM)

        val lx = px()
        val ty = py()
        val w = pw()
        val h = ph()
        val r = 10

        UiRecorder.dropShadow(lx.toFloat(), ty.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), 16f, 0x70000000)
        UiRecorder.fillRectVGradient(lx.toFloat(), ty.toFloat(), w.toFloat(), h.toFloat(), BG_TOP, BG_BOT, r.toFloat())
        ScreenTheme.nRoundedRectRing(lx, ty, w, h, r, 1, 0, BORDER)

        UiRecorder.textBold("Credits", (lx + PAD).toFloat(), (ty + 14).toFloat(), 10f, ScreenTheme.TEXT_COLOR)
        backX = lx + w - PAD - backW
        backY = ty + (HEADER_H - backH) / 2
        val backHov = inside(mouseX, mouseY, backX, backY, backW, backH)
        ScreenTheme.nRoundedRect(backX, backY, backW, backH, backH / 2, if (backHov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        centered("Back", backX + backW / 2, backY, backH, ON_ACCENT, 7.5f)
        ScreenTheme.nRect(lx + PAD, ty + HEADER_H, w - PAD * 2, 1, BORDER)

        bodyY = ty + HEADER_H + 1
        bodyH = ty + h - 1 - bodyY
        contentH = PAD + CREATOR_H + GAP + gridHeight() + PAD
        scroll = scroll.coerceIn(0, maxScroll())

        UiRecorder.pushScissor(lx.toFloat(), bodyY.toFloat(), w.toFloat(), bodyH.toFloat())
        val cx0 = lx + PAD
        val innerW = w - PAD * 2
        var y = bodyY + PAD - scroll
        drawCreator(cx0, y, innerW, mouseX, mouseY)
        y += CREATOR_H + GAP

        val cw = colW()
        var i = 0
        while (i < CREDITS.size) {
            val a = CREDITS[i]
            val b = CREDITS.getOrNull(i + 1)
            val rowH = max(cardHeight(a, cw), b?.let { cardHeight(it, cw) } ?: 0)
            drawCard(cx0, y, cw, rowH, a)
            if (b != null) drawCard(cx0 + cw + GAP, y, cw, rowH, b)
            y += rowH + GAP
            i += 2
        }
        UiRecorder.popScissor()

        if (maxScroll() > 0) {
            val trackH = bodyH - 8
            val thumbH = max(16, trackH * bodyH / contentH)
            val thumbY = bodyY + 4 + (trackH - thumbH) * scroll / maxScroll()
            ScreenTheme.nRoundedRect(lx + w - 6, thumbY, 3, thumbH, 1, 0x60FFFFFF)
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawCreator(x: Int, y: Int, w: Int, mx: Int, my: Int) {
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), CREATOR_H.toFloat(), 8f, CARD_BG)
        UiRecorder.fillRectHGradient((x + 8).toFloat(), (y + 1).toFloat(), w * 0.7f, (CREATOR_H - 2).toFloat(), 0x3024B6B0, 0x0024B6B0)
        ScreenTheme.nRoundedRectRing(x, y, w, CREATOR_H, 8, 1, 0, 0x5924B6B0)

        val hs = 28
        val hx = x + 10
        val hy = y + (CREATOR_H - hs) / 2
        headBadge(hx, hy, hs, CREATOR)
        val tx = hx + hs + 10
        UiRecorder.textBold(CREATOR.name, tx.toFloat(), (y + 11).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        UiRecorder.text(CREATOR.role, tx.toFloat(), (y + 24).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)

        copyX = x + w - 10 - copyW
        copyY = y + (CREATOR_H - copyH) / 2
        val copied = System.currentTimeMillis() - copiedAt < 1500
        val cHov = inside(mx, my, copyX, copyY, copyW, copyH) && visible(copyY, copyH)
        ScreenTheme.nRoundedRectRing(copyX, copyY, copyW, copyH, copyH / 2, 1, if (cHov) BTN_BG_HOVER else BTN_BG, if (copied) ScreenTheme.ACCENT else BORDER)
        centered(if (copied) "Copied" else "Copy", copyX + copyW / 2, copyY, copyH, if (copied) ScreenTheme.ACCENT else ScreenTheme.TEXT_COLOR, 6.5f)

        val ls = 6.5f
        linkW = UiRecorder.textWidth(DISCORD, ls).toInt()
        linkH = copyH
        linkX = copyX - 8 - linkW
        linkY = copyY
        val lHov = inside(mx, my, linkX, linkY, linkW, linkH) && visible(linkY, linkH)
        val lColor = if (lHov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR
        UiRecorder.text(DISCORD, linkX.toFloat(), linkY + (linkH - ls) / 2f, ls, lColor)
        if (lHov) ScreenTheme.nRect(linkX, linkY + linkH - 2, linkW, 1, lColor)
    }

    private fun drawCard(x: Int, y: Int, w: Int, h: Int, c: Credit) {
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, CARD_BG)
        ScreenTheme.nRoundedRectRing(x, y, w, h, 8, 1, 0, CARD_BORDER)

        headBadge(x + 10, y + 10, 22, c)
        val tx = textX(x)
        UiRecorder.textBold(c.name, tx.toFloat(), (y + 10).toFloat(), NAME_SIZE, ScreenTheme.TEXT_COLOR)
        var ly = y + 20
        for (line in roleLines(c, w)) {
            UiRecorder.text(line, tx.toFloat(), ly.toFloat(), ROLE_SIZE, ScreenTheme.SUBTEXT_COLOR)
            ly += LINE_H
        }

        val bullets = bulletLines(c, w)
        if (bullets.isEmpty()) return
        var by = y + 20 + headH(c, w)
        ScreenTheme.nRect(x + 10, by - 5, w - 20, 1, CARD_BORDER)
        for (lines in bullets) {
            UiRecorder.disc((x + 13).toFloat(), by + BULLET_SIZE / 2f + 0.5f, 1.3f, ScreenTheme.SUBTEXT_COLOR)
            for (line in lines) {
                UiRecorder.text(line, (x + 20).toFloat(), by.toFloat(), BULLET_SIZE, ScreenTheme.SUBTEXT_COLOR)
                by += LINE_H
            }
        }
    }

    // Head-style rounded square with the contributor's initial.
    private fun headBadge(x: Int, y: Int, s: Int, c: Credit) {
        ScreenTheme.nRoundedRect(x, y, s, s, max(4, s / 5), c.badgeColor)
        val init = c.name.take(1).uppercase()
        val size = s * 0.45f
        val iw = UiRecorder.textWidth(init, size)
        UiRecorder.textBold(init, x + s / 2f - iw / 2f, y + (s - size) / 2f, size, 0xFFFFFFFF.toInt())
    }

    private fun centered(s: String, cx: Int, y: Int, h: Int, color: Int, size: Float) {
        UiRecorder.text(s, cx - UiRecorder.textWidth(s, size) / 2f, y + (h - size) / 2f, size, color)
    }

    private fun visible(y: Int, h: Int): Boolean = y >= bodyY && y + h <= bodyY + bodyH

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll - (verticalAmount * 20).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, backX, backY, backW, backH)) {
            onClose()
            return true
        }
        if (inside(mx, my, copyX, copyY, copyW, copyH) && visible(copyY, copyH)) {
            Minecraft.getInstance().keyboardHandler.clipboard = DISCORD_URL
            copiedAt = System.currentTimeMillis()
            return true
        }
        // Clicking the invite text itself opens it, as the old link pill did.
        if (inside(mx, my, linkX, linkY, linkW, linkH) && visible(linkY, linkH)) {
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

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, fishmod.utils.rendering.UiScale.factor())
    }
}

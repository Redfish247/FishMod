package fishmod.features

import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import net.minecraft.client.Minecraft
import kotlin.math.max

// Credits, drawn as a page inside the /fm window
class CreditsPage {

    companion object {
        private const val DISCORD = "discord.gg/3mSuQUB8kk"
        private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"

        private const val NAME_SIZE = 9f
        private const val ROLE_SIZE = 7.4f
        private const val BULLET_SIZE = 7.2f
        private const val LINE_H = 10

        private data class Credit(
            val name: String,
            val role: String,
            val badgeColor: Int,
            val details: List<String> = emptyList(),
        )

        private val CREATOR = Credit("RedFish", "Creator. Everything else.", 0)

        private val CREDITS = listOf(
            Credit("BladeMasterGabe", "splits & dungeon features", 0xFFE0A63A.toInt()),
            Credit("22yrs", "shared the dungeon map and blessed the port", 0xFF7A8CE0.toInt()),
            Credit(
                "Vyriv", "BetterPV: the profile viewer", 0xFF5B8CFF.toInt(),
                details = listOf(
                    "Profile viewer code ported from BetterPV (MPL-2.0, github.com/Vyriv/BetterPV)",
                    "Data: NEU-REPO, Athen & SkyHelper prices, EliteBot, SkyCofl",
                    "Font: Inter by The Inter Project Authors (SIL OFL 1.1)",
                ),
            ),
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
            Credit(
                "SkyblockOverhaul (SBO)", "Diana features reference", 0xFFE0C35F.toInt(),
                details = listOf(
                    "Spade guess, close burrow detection & hub warp logic",
                    "Rare mob share/receive, Diana tracker & announcers",
                    "Sphinx solver, party commands, message hider",
                ),
            ),
            Credit(
                "SidOfThe7Cs (SkyHanni)", "Diana arrow guess", 0xFF8FE05F.toInt(),
                details = listOf(
                    "Arrow direction guess & sub-guesses (SkyHanni PR #4916, via SBO)",
                ),
            ),
        )
    }

    private val copyW = 44
    private val copyH = 17
    private var copyX = 0
    private var copyY = 0
    private var linkX = 0
    private var linkY = 0
    private var linkW = 0
    private var linkH = 0
    private var bodyY = 0
    private var bodyH = 0
    var scroll = 0
    private var contentH = 0
    private var copiedAt = 0L

    private val GAP = 8
    private val CREATOR_H = 46

    private val wrapCache = HashMap<String, List<String>>()

    private fun wrap(s: String, maxW: Float, size: Float): List<String> {
        val key = "$maxW|$size|$s"
        wrapCache[key]?.let { return it }
        if (wrapCache.size > 512) wrapCache.clear()
        val out = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && UiRecorder.textWidth(next, size) > maxW) { out.add(line); line = word } else line = next
        }
        if (line.isNotEmpty()) out.add(line)
        return out.also { wrapCache[key] = it }
    }

    private fun cols(w: Int): Int = if (w >= 460) 2 else 1
    private fun colW(w: Int): Int = (w - (cols(w) - 1) * GAP) / cols(w)
    private fun roleLines(c: Credit, cardW: Int): List<String> = wrap(c.role, (cardW - 48).toFloat(), ROLE_SIZE)
    private fun headH(c: Credit, cardW: Int): Int = max(24, 11 + roleLines(c, cardW).size * LINE_H)
    private fun bulletLines(c: Credit, cardW: Int): List<List<String>> = c.details.map { wrap(it, (cardW - 28).toFloat(), BULLET_SIZE) }

    private fun cardHeight(c: Credit, cardW: Int): Int {
        var h = 10 + headH(c, cardW) + 10
        val bullets = bulletLines(c, cardW)
        if (bullets.isNotEmpty()) h += 8 + bullets.sumOf { it.size } * LINE_H
        return h
    }

    private fun gridHeight(w: Int): Int {
        val cw = colW(w); val n = cols(w)
        var total = 0
        var i = 0
        while (i < CREDITS.size) {
            total += (i until Math.min(i + n, CREDITS.size)).maxOf { cardHeight(CREDITS[it], cw) } + GAP
            i += n
        }
        return total - GAP
    }

    fun maxScroll(): Int = max(0, contentH - bodyH)

    fun render(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        bodyY = y; bodyH = h
        contentH = CREATOR_H + GAP + gridHeight(w) + 8
        scroll = scroll.coerceIn(0, maxScroll())
        UiRecorder.pushScissor(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        try {
            var cy = y - scroll
            drawCreator(x, cy, w, mx, my)
            cy += CREATOR_H + GAP
            val cw = colW(w); val n = cols(w)
            var i = 0
            while (i < CREDITS.size) {
                val row = (i until Math.min(i + n, CREDITS.size)).map { CREDITS[it] }
                val rowH = row.maxOf { cardHeight(it, cw) }
                // skip rows outside the view
                if (cy + rowH >= y && cy <= y + h) for ((j, c) in row.withIndex()) drawCard(x + j * (cw + GAP), cy, cw, rowH, c)
                cy += rowH + GAP
                i += n
            }
        } finally { UiRecorder.popScissor() }
        if (maxScroll() > 0) {
            val trackH = h - 8
            val thumbH = max(16, trackH * h / contentH)
            val thumbY = y + 4 + (trackH - thumbH) * scroll / maxScroll()
            ScreenTheme.nRoundedRect(x + w - 3, thumbY, 3, thumbH, 1, ScreenTheme.ink(0x60))
        }
    }

    private fun drawCreator(x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val accent = ScreenTheme.ACCENT
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), CREATOR_H.toFloat(), 8f, ScreenTheme.ink(0x0C))
        UiRecorder.fillRectHGradient((x + 8).toFloat(), (y + 1).toFloat(), w * 0.7f, (CREATOR_H - 2).toFloat(), (0x30 shl 24) or (accent and 0xFFFFFF), accent and 0xFFFFFF)
        ScreenTheme.nRoundedRectRing(x, y, w, CREATOR_H, 8, 1, 0, (0x59 shl 24) or (accent and 0xFFFFFF))

        val hs = 28
        val hx = x + 10
        val hy = y + (CREATOR_H - hs) / 2
        headBadge(hx, hy, hs, CREATOR.name, accent)
        val tx = hx + hs + 10
        UiRecorder.textBold(CREATOR.name, tx.toFloat(), (y + 10).toFloat(), 10f, ScreenTheme.TEXT_COLOR)
        UiRecorder.text(CREATOR.role, tx.toFloat(), (y + 25).toFloat(), 7.6f, ScreenTheme.SUBTEXT_COLOR)

        copyX = x + w - 10 - copyW
        copyY = y + (CREATOR_H - copyH) / 2
        val copied = System.currentTimeMillis() - copiedAt < 1500
        val cHov = inside(mx, my, copyX, copyY, copyW, copyH) && visible(copyY, copyH)
        ScreenTheme.nRoundedRectRing(copyX, copyY, copyW, copyH, copyH / 2, 1, ScreenTheme.ink(if (cHov) 0x1C else 0x10), if (copied) accent else ScreenTheme.ink(0x26))
        centered(if (copied) "Copied" else "Copy", copyX + copyW / 2, copyY, copyH, if (copied) accent else ScreenTheme.TEXT_COLOR, 7.4f)

        val ls = 7.4f
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
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, ScreenTheme.ink(0x0C))
        ScreenTheme.nRoundedRectRing(x, y, w, h, 8, 1, 0, ScreenTheme.ink(0x1F))
        headBadge(x + 10, y + 10, 22, c.name, c.badgeColor)
        val tx = x + 40
        UiRecorder.textBold(c.name, tx.toFloat(), (y + 10).toFloat(), NAME_SIZE, ScreenTheme.TEXT_COLOR)
        var ly = y + 21
        for (line in roleLines(c, w)) {
            UiRecorder.text(line, tx.toFloat(), ly.toFloat(), ROLE_SIZE, ScreenTheme.SUBTEXT_COLOR)
            ly += LINE_H
        }
        val bullets = bulletLines(c, w)
        if (bullets.isEmpty()) return
        var by = y + 20 + headH(c, w)
        ScreenTheme.nRect(x + 10, by - 5, w - 20, 1, ScreenTheme.ink(0x1F))
        for (lines in bullets) {
            UiRecorder.disc((x + 13).toFloat(), by + BULLET_SIZE / 2f + 0.5f, 1.3f, ScreenTheme.SUBTEXT_COLOR)
            for (line in lines) {
                UiRecorder.text(line, (x + 20).toFloat(), by.toFloat(), BULLET_SIZE, ScreenTheme.SUBTEXT_COLOR)
                by += LINE_H
            }
        }
    }

    private fun headBadge(x: Int, y: Int, s: Int, name: String, color: Int) {
        ScreenTheme.nRoundedRect(x, y, s, s, max(4, s / 5), color)
        val init = name.take(1).uppercase()
        val size = s * 0.45f
        val iw = UiRecorder.textWidth(init, size)
        UiRecorder.textBold(init, x + s / 2f - iw / 2f, y + (s - size) / 2f, size, ScreenTheme.textOn(color))
    }

    private fun centered(s: String, cx: Int, y: Int, h: Int, color: Int, size: Float) {
        UiRecorder.text(s, cx - UiRecorder.textWidth(s, size) / 2f, y + (h - size) / 2f, size, color)
    }

    private fun visible(y: Int, h: Int): Boolean = y >= bodyY && y + h <= bodyY + bodyH

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h

    fun scrollBy(amount: Double) { scroll = (scroll - (amount * 20).toInt()).coerceIn(0, maxScroll()) }

    fun click(mx: Int, my: Int): Boolean {
        if (inside(mx, my, copyX, copyY, copyW, copyH) && visible(copyY, copyH)) {
            Minecraft.getInstance().keyboardHandler.clipboard = DISCORD_URL
            copiedAt = System.currentTimeMillis()
            return true
        }
        if (inside(mx, my, linkX, linkY, linkW, linkH) && visible(linkY, linkH)) {
            try { fishmod.compat.GLFW.openUri(java.net.URI.create(DISCORD_URL)) } catch (t: Throwable) { FishDiag.fail("CreditsScreen.1", "could not open discord link", t) }
            return true
        }
        return false
    }
}

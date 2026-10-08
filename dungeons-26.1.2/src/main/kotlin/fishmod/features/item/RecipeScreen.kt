package fishmod.features.item

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class RecipeScreen(startId: String, private val parent: Screen?) : Screen(Component.literal("Recipe")), HasUiOverlay {

    private companion object {
        private const val SCRIM = 0xB3000000.toInt()
        private const val BG = 0xFF12151B.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val CARD = 0xFF171B22.toInt()
        private const val CARD_HOVER = 0xFF1E232C.toInt()
        private const val SLOT = 0xFF0C0F14.toInt()
        private const val SLOT_BORDER = 0xFF262A33.toInt()
        private const val GOLD = 0xFFF2C14E.toInt()
        private const val GOLD_SOFT = 0x24F2C14E
        private const val DIM = 0xFF5A6470.toInt()
        private const val W = 260
        private const val H = 200
        private const val PAD = 12
        private const val CELL = 22
        private const val SLOT_SIZE = 20
        private const val OUT = 30
        private const val TAB_H = 15
        private const val CARD_Y = 70
        private const val CARD_H = 84
    }

    private data class Slot(val x: Int, val y: Int, val size: Int, val id: String?, val count: Int)
    private data class Tab(val x: Int, val w: Int, val index: Int, val label: String)

    private var id = startId
    private var page = 0
    private val history = ArrayDeque<Pair<String, Int>>()

    private var px = 0
    private var py = 0

    override fun isPauseScreen(): Boolean = false
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (width / UiScale.factor()).toInt()
    private fun vh(): Int = (height / UiScale.factor()).toInt()

    override fun init() {
        RecipeViewer.clearCache()
        px = (vw() - W) / 2
        py = (vh() - H) / 2
    }

    private fun recipes() = RecipeViewer.recipesFor(id)
    private fun current() = recipes().getOrNull(page)

    private fun slots(): List<Slot> {
        val r = current() ?: return emptyList()
        val out = ArrayList<Slot>(10)
        val cardTop = py + CARD_Y
        val leftCx = px + PAD + 58
        if (r.grid) {
            val gx = leftCx - CELL * 3 / 2
            val gy = cardTop + (CARD_H - CELL * 3) / 2
            r.inputs.take(9).forEachIndexed { i, ing ->
                out.add(Slot(gx + (i % 3) * CELL + 1, gy + (i / 3) * CELL + 1, SLOT_SIZE, ing?.id, ing?.count ?: 0))
            }
        } else {
            val ins = r.inputs.filterNotNull().take(9)
            val cols = ins.size.coerceIn(1, 3)
            val rows = (ins.size + cols - 1) / cols
            val gx = leftCx - CELL * cols / 2
            val gy = cardTop + (CARD_H - CELL * rows) / 2
            ins.forEachIndexed { i, ing -> out.add(Slot(gx + (i % cols) * CELL + 1, gy + (i / cols) * CELL + 1, SLOT_SIZE, ing.id, ing.count)) }
        }
        out.add(Slot(px + W - PAD - 58 - OUT / 2, cardTop + (CARD_H - OUT) / 2, OUT, r.output.id, r.output.count))
        return out
    }

    private fun tabs(): List<Tab>? {
        val all = recipes()
        if (all.size < 2) return emptyList()
        val counts = all.groupingBy { it.kind }.eachCount()
        val seen = HashMap<String, Int>()
        val labels = all.map { r ->
            val n = seen.merge(r.kind, 1, Int::plus)!!
            if (counts[r.kind]!! > 1) "${r.kind} $n" else r.kind
        }
        var x = px + PAD
        val out = ArrayList<Tab>()
        labels.forEachIndexed { i, l ->
            val w = UiRecorder.textWidth(l, 6.5f).toInt() + 14
            out.add(Tab(x, w, i, l))
            x += w + 4
        }
        return if (x - 4 > px + W - PAD) null else out
    }

    private fun backX() = px + W - PAD - 46
    private fun tabY() = py + 50
    private fun prevX() = px + PAD
    private fun nextX() = px + W - PAD - 16

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        ctx.fill(0, 0, vw(), vh(), SCRIM)
        ScreenTheme.panel(ctx, px, py, px + W, py + H, 10, BG, BORDER)
        ScreenTheme.roundedRect(ctx, px + PAD, py + 12, 26, 26, 6, GOLD_SOFT)
        ScreenTheme.panel(ctx, px + PAD, py + CARD_Y, px + W - PAD, py + CARD_Y + CARD_H, 8, CARD, BORDER)
        try {
            ctx.item(RecipeViewer.stackFor(id), px + PAD + 5, py + 17)
            for (s in slots()) {
                val out = s.size == OUT
                ScreenTheme.panel(ctx, s.x, s.y, s.x + s.size, s.y + s.size, if (out) 6 else 4, SLOT, if (out) GOLD else SLOT_BORDER)
                if (s.id != null) {
                    val o = (s.size - 16) / 2
                    ctx.item(RecipeViewer.stackFor(s.id), s.x + o, s.y + o)
                }
            }
        } catch (e: Exception) {
            FishDiag.fail("RecipeScreen.1", "recipe item render failed for $id", e)
        }
        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, rawX: Int, rawY: Int, delta: Float) {
        val mx = UiScale.vx(rawX)
        val my = UiScale.vx(rawY)
        UiRecorder.clear()
        UiRecorder.fillRect((px + 10).toFloat(), py.toFloat(), (W - 20).toFloat(), 2f, GOLD)

        val tx = px + PAD + 34
        val all = recipes()
        val r = current()
        val name = RecipeViewer.displayName(id)
        val titleMax = (if (history.isNotEmpty()) backX() - 6 else px + W - PAD) - tx
        UiRecorder.textBold(fit(RecipeViewer.clean(name), titleMax, 9f), tx.toFloat(), (py + 14).toFloat(), 9f, RecipeViewer.colorOf(name))
        val sub = when {
            !NeuRepo.isLoaded() -> "Loading NEU repo…"
            r == null -> "No known recipe"
            all.size == 1 -> r.kind
            else -> "${all.size} ways to get"
        }
        UiRecorder.text(sub, tx.toFloat(), (py + 27).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        if (history.isNotEmpty()) button("← Back", backX(), py + 15, 46, mx, my, false)
        ScreenTheme.nRect(px + PAD, py + 44, W - PAD * 2, 1, BORDER)

        val tabs = tabs()
        when {
            tabs == null -> {
                button("<", prevX(), tabY(), 16, mx, my, false)
                button(">", nextX(), tabY(), 16, mx, my, false)
                val label = "${r?.kind ?: ""}  ${page + 1}/${all.size}"
                UiRecorder.text(label, px + W / 2f - UiRecorder.textWidth(label, 7f) / 2f, tabY() + 4f, 7f, ScreenTheme.TEXT_COLOR)
            }
            tabs.isEmpty() && r != null -> UiRecorder.textBold(r.kind, (px + PAD + 2).toFloat(), tabY() + 4f, 7f, ScreenTheme.TEXT_COLOR)
            else -> tabs.forEach { t -> button(t.label, t.x, tabY(), t.w, mx, my, t.index == page) }
        }

        if (r == null) {
            val lines = RecipeViewer.lore(id).filter { RecipeViewer.clean(it).isNotBlank() }.take(7)
            lines.forEachIndexed { i, l ->
                UiRecorder.text(fit(RecipeViewer.clean(l), W - PAD * 2 - 16, 6.5f), (px + PAD + 8).toFloat(), (py + CARD_Y + 8 + i * 10).toFloat(), 6.5f, RecipeViewer.colorOf(l))
            }
        } else {
            val ax = px + W / 2f + 3
            val ay = py + CARD_Y + CARD_H / 2f
            UiRecorder.fillRect(ax - 14, ay - 1, 18f, 2f, GOLD)
            UiRecorder.textBold("›", ax + 2, ay - 6.5f, 12f, GOLD)
            for (s in slots()) {
                if (s.id == null) continue
                if (inside(mx, my, s.x, s.y, s.size, s.size)) UiRecorder.fillRect(s.x + 2f, s.y + 2f, s.size - 4f, s.size - 4f, 0x40FFFFFF)
                if (s.count > 1) {
                    val c = abbr(s.count)
                    UiRecorder.textBold(c, s.x + s.size - 1f - UiRecorder.textWidth(c, 6f), s.y + s.size - 7.5f, 6f, ScreenTheme.TEXT_COLOR)
                }
            }
            val outName = RecipeViewer.displayName(r.output.id)
            val on = fit(RecipeViewer.clean(outName), 100, 6f)
            val ocx = px + W - PAD - 58f
            UiRecorder.text(on, ocx - UiRecorder.textWidth(on, 6f) / 2f, py + CARD_Y + CARD_H / 2f + OUT / 2f + 4, 6f, RecipeViewer.colorOf(outName))
            if (r.detail.isNotEmpty()) {
                val d = fit(r.detail, W - PAD * 2, 7f)
                UiRecorder.text(d, px + W / 2f - UiRecorder.textWidth(d, 7f) / 2f, (py + CARD_Y + CARD_H + 8).toFloat(), 7f, ScreenTheme.SUBTEXT_COLOR)
            }
        }

        val hint = if (history.isNotEmpty()) "Click an item to view it  ·  Backspace: back  ·  Esc: close" else "Click an item to view it  ·  Esc: close"
        UiRecorder.text(hint, px + W / 2f - UiRecorder.textWidth(hint, 6f) / 2f, (py + H - 14).toFloat(), 6f, DIM)

        super.extractRenderState(ctx, rawX, rawY, delta)

        slots().firstOrNull { it.id != null && inside(mx, my, it.x, it.y, it.size, it.size) }?.let { s ->
            val sid = s.id!!
            val lines = ArrayList<Component>()
            lines.add(Component.literal(RecipeViewer.displayName(sid)))
            RecipeViewer.lore(sid).forEach { lines.add(Component.literal(it)) }
            if (s.count > 1) lines.add(Component.literal("§8Amount: ${"%,d".format(s.count)}"))
            if (sid != id && hasPage(sid)) lines.add(Component.literal("§eClick to view"))
            ctx.setComponentTooltipForNextFrame(font, lines, rawX, rawY)
        }
    }

    private fun hasPage(target: String) = target != RecipeViewer.COIN && (RecipeViewer.recipesFor(target).isNotEmpty() || NeuRepo.get(target) != null)

    private fun button(label: String, x: Int, y: Int, w: Int, mx: Int, my: Int, selected: Boolean) {
        val hov = inside(mx, my, x, y, w, TAB_H)
        val fill = when { selected -> ScreenTheme.ACCENT; hov -> CARD_HOVER; else -> CARD }
        ScreenTheme.nRoundedRectRing(x, y, w, TAB_H, TAB_H / 2, 1, fill, if (selected) ScreenTheme.ACCENT else BORDER)
        val color = when { selected -> 0xFF06302F.toInt(); hov -> ScreenTheme.TEXT_COLOR; else -> ScreenTheme.SUBTEXT_COLOR }
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, 6.5f) / 2f, y + 4f, 6.5f, color)
    }

    private fun abbr(n: Int): String = when {
        n >= 1_000_000 -> trim(n / 1e6) + "m"
        n >= 1_000 -> trim(n / 1e3) + "k"
        else -> n.toString()
    }

    private fun trim(d: Double) = String.format(java.util.Locale.US, "%.1f", d).removeSuffix(".0")

    private fun fit(s: String, maxW: Int, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && UiRecorder.textWidth("$t...", size) > maxW) t = t.dropLast(1)
        return "$t..."
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx < x + w && my >= y && my < y + h

    private fun goBack(): Boolean {
        val prev = history.removeLastOrNull() ?: return false
        id = prev.first
        page = prev.second
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_4) { goBack(); return true }
        if (history.isNotEmpty() && inside(mx, my, backX(), py + 15, 46, TAB_H)) { goBack(); return true }
        val n = recipes().size
        val tabs = tabs()
        if (tabs == null) {
            if (inside(mx, my, prevX(), tabY(), 16, TAB_H)) { page = (page - 1 + n) % n; return true }
            if (inside(mx, my, nextX(), tabY(), 16, TAB_H)) { page = (page + 1) % n; return true }
        } else {
            tabs.firstOrNull { inside(mx, my, it.x, tabY(), it.w, TAB_H) }?.let { page = it.index; return true }
        }
        slots().firstOrNull { it.id != null && inside(mx, my, it.x, it.y, it.size, it.size) }?.let { s ->
            if (s.id != id && hasPage(s.id!!)) {
                history.addLast(id to page)
                id = s.id
                page = 0
            }
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_BACKSPACE -> { goBack(); return true }
            GLFW.GLFW_KEY_ESCAPE -> { onClose(); return true }
        }
        if (Minecraft.getInstance().options.keyInventory.matches(input)) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(width, height, UiScale.factor())
    }
}

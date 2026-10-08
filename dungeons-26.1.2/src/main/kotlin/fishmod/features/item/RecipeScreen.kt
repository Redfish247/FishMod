package fishmod.features.item

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
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
        private const val CARD = 0xFF181C23.toInt()
        private const val CARD_HOVER = 0xFF1E232C.toInt()
        private const val SLOT = 0xFF0D1015.toInt()
        private const val GOLD = 0xFFF2C14E.toInt()
        private const val GOLD_SOFT = 0x24F2C14E
        private const val W = 220
        private const val H = 172
        private const val PAD = 12
        private const val S = 20
    }

    private data class Slot(val x: Int, val y: Int, val id: String, val count: Int)

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
        px = (vw() - W) / 2
        py = (vh() - H) / 2
    }

    private fun recipes() = RecipeViewer.recipesFor(id)
    private fun current() = recipes().getOrNull(page)

    private fun slots(): List<Slot> {
        val r = current() ?: return emptyList()
        val gx = px + 34
        val gy = py + 56
        val out = ArrayList<Slot>(10)
        r.inputs.take(9).forEachIndexed { i, ing ->
            if (ing != null) out.add(Slot(gx + (i % 3) * S, gy + (i / 3) * S, ing.id, ing.count))
        }
        out.add(Slot(px + 150, gy + S, r.output.id, r.output.count))
        return out
    }

    private fun backX() = px + W - PAD - 44
    private fun prevX() = px + W / 2 - 40
    private fun nextX() = px + W / 2 + 24
    private fun pagerY() = py + H - PAD - 14

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        ctx.fill(0, 0, vw(), vh(), SCRIM)
        ScreenTheme.panel(ctx, px, py, px + W, py + H, 10, BG, BORDER)
        ScreenTheme.roundedRect(ctx, px + PAD, py + 12, 24, 24, 6, GOLD_SOFT)
        try {
            ctx.item(RecipeViewer.stackFor(id), px + PAD + 4, py + 16)
            for (s in slots()) {
                ctx.fill(s.x, s.y, s.x + 18, s.y + 18, SLOT)
                ctx.item(RecipeViewer.stackFor(s.id), s.x + 1, s.y + 1)
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

        val tx = px + PAD + 32
        val all = recipes()
        val r = current()
        UiRecorder.textBold(fit(RecipeViewer.clean(RecipeViewer.displayName(id)), backX() - tx - 4, 9f), tx.toFloat(), (py + 13).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        val sub = when {
            r == null -> "No known recipe"
            r.detail.isEmpty() -> r.kind
            else -> r.kind + " · " + r.detail
        }
        UiRecorder.text(fit(sub, W - PAD - (tx - px), 6.5f), tx.toFloat(), (py + 26).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nRect(px + PAD, py + 44, W - PAD * 2, 1, BORDER)

        if (history.isNotEmpty()) button("← Back", backX(), py + 12, 44, mx, my)

        if (r == null) {
            val lines = if (!fishmod.utils.data.NeuRepo.isLoaded()) listOf("Loading NEU repo…") else RecipeViewer.lore(id).map(RecipeViewer::clean).filter { it.isNotBlank() }.take(10)
            lines.forEachIndexed { i, l -> UiRecorder.text(fit(l, W - PAD * 2, 6.5f), (px + PAD).toFloat(), (py + 52 + i * 10).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR) }
        } else {
            UiRecorder.textBold("→", (px + 112).toFloat(), (py + 56 + S + 4).toFloat(), 12f, GOLD)
            for (s in slots()) {
                if (inside(mx, my, s.x, s.y, 18, 18)) UiRecorder.fillRect((s.x + 1).toFloat(), (s.y + 1).toFloat(), 16f, 16f, 0x50FFFFFF)
                if (s.count > 1) {
                    val c = abbr(s.count)
                    UiRecorder.textBold(c, s.x + 18f - UiRecorder.textWidth(c, 6.5f), s.y + 11f, 6.5f, ScreenTheme.TEXT_COLOR)
                }
            }
            if (all.size > 1) {
                button("<", prevX(), pagerY(), 16, mx, my)
                button(">", nextX(), pagerY(), 16, mx, my)
                val label = "${page + 1}/${all.size}"
                UiRecorder.text(label, px + W / 2f - UiRecorder.textWidth(label, 7f) / 2f, pagerY() + 4f, 7f, ScreenTheme.SUBTEXT_COLOR)
            }
        }

        super.extractRenderState(ctx, rawX, rawY, delta)

        slots().firstOrNull { inside(mx, my, it.x, it.y, 18, 18) }?.let { s ->
            val lines = ArrayList<Component>()
            lines.add(Component.literal(RecipeViewer.displayName(s.id)))
            RecipeViewer.lore(s.id).forEach { lines.add(Component.literal(it)) }
            if (s.count > 1) lines.add(Component.literal("§8Amount: ${"%,d".format(s.count)}"))
            if (s.id != id && hasPage(s.id)) lines.add(Component.literal("§eClick to view"))
            ctx.setComponentTooltipForNextFrame(font, lines, rawX, rawY)
        }
    }

    private fun hasPage(target: String) = RecipeViewer.recipesFor(target).isNotEmpty() || fishmod.utils.data.NeuRepo.get(target) != null

    private fun button(label: String, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val hov = inside(mx, my, x, y, w, 14)
        ScreenTheme.nRoundedRectRing(x, y, w, 14, 7, 1, if (hov) CARD_HOVER else CARD, BORDER)
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, 7f) / 2f, y + 3.5f, 7f, if (hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)
    }

    private fun abbr(n: Int): String = when {
        n >= 1_000_000 -> "${n / 1_000_000}m"
        n >= 10_000 -> "${n / 1000}k"
        else -> n.toString()
    }

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
        if (history.isNotEmpty() && inside(mx, my, backX(), py + 12, 44, 14)) { goBack(); return true }
        val n = recipes().size
        if (n > 1 && inside(mx, my, prevX(), pagerY(), 16, 14)) { page = (page - 1 + n) % n; return true }
        if (n > 1 && inside(mx, my, nextX(), pagerY(), 16, 14)) { page = (page + 1) % n; return true }
        slots().firstOrNull { inside(mx, my, it.x, it.y, 18, 18) }?.let { s ->
            if (s.id != id && hasPage(s.id)) {
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
        val inv = Minecraft.getInstance().options.keyInventory
        if (inv.matches(input)) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(width, height, UiScale.factor())
    }
}

package fishmod.features.general

import fishmod.compat.GLFW
import fishmod.compat.showScreen
import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

// Themed (NanoVG) editor matching the config screen; help text is hover-only.
class InventoryButtonEditScreen(private val b: InvButton, private val parent: Screen?) : Screen(Component.literal("Edit Button")), HasUiOverlay {

    private companion object {
        const val SCRIM = 0x99000000.toInt()
        const val BG = 0xFF151515.toInt()
        const val BORDER = 0xFF2D2D2D.toInt()
        const val CARD = 0xFF1C1C1C.toInt()
        const val CARD_HOVER = 0xFF232323.toInt()
        val ON_ACCENT get() = fishmod.features.ScreenTheme.onAccent()
        const val HINT = 0xFF626262.toInt()
        const val OFF = 0xFF333333.toInt()
    }

    private class Field(val box: EditBox, val hint: String, val tip: String)

    private val fields = ArrayList<Field>()
    private var focused = 0
    private val rects = HashMap<String, IntArray>()
    private var deleted = false

    private val pw = 220
    private val ph = 196
    private val pad = 12
    private val fh = 18
    private val side = 30
    private var px = 0
    private var py = 0

    override fun isPauseScreen() = false

    override fun init() {
        val vw = (width / UiScale.factor()).toInt()
        val vh = (height / UiScale.factor()).toInt()
        px = (vw - pw) / 2
        py = Math.max(8, (vh - ph) / 2)
        val old = fields.map { it.box.value }
        fields.clear()
        val iw = pw - pad * 2
        fun add(i: Int, w: Int, max: Int, value: String, hint: String, tip: String) {
            val f = EditBox(font, px + pad, py + 46 + i * (fh + 6), w, fh, Component.literal(hint))
            f.setMaxLength(max)
            f.value = old.getOrNull(i) ?: value
            fields.add(Field(f, hint, tip))
        }
        add(0, iw, 120, b.command, "Command", "Command to run, without the /\ne.g. warp dhub")
        add(1, iw, 40, b.name, "Name", "Label shown next to the button\n(toggle it with Show name)")
        add(2, iw - side - 4, 80, b.icon, "Icon", "Item id, e.g. minecraft:compass\nor click Held to use your held item")
        add(3, iw - side - 4, 8, "%08X".format(b.color), "Colour", "Button background colour\nRRGGBB or AARRGGBB hex")
        focused = focused.coerceIn(0, fields.size - 1)
        fields[focused].box.isFocused = true
    }

    private fun parseColor(s: String): Int? {
        val hex = s.trim().removePrefix("#")
        val v = hex.toLongOrNull(16) ?: return null
        return if (hex.length <= 6) (0xFF000000 or v).toInt() else v.toInt()
    }

    private fun apply() {
        b.command = fields[0].box.value.trim()
        b.name = fields[1].box.value
        b.icon = fields[2].box.value.trim()
        parseColor(fields[3].box.value)?.let { b.color = it }
    }

    private fun inside(id: String, mx: Int, my: Int): Boolean {
        val r = rects[id] ?: return false
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
    }

    private fun btn(id: String, label: String, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int, primary: Boolean = false, danger: Boolean = false) {
        rects[id] = intArrayOf(x, y, w, h)
        val hov = mx >= x && mx < x + w && my >= y && my < y + h
        when {
            primary -> ScreenTheme.nRoundedRect(x, y, w, h, h / 2, if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
            danger -> ScreenTheme.nRoundedRectRing(x, y, w, h, h / 2, 1, if (hov) CARD_HOVER else CARD, if (hov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER)
            else -> ScreenTheme.nRoundedRectRing(x, y, w, h, h / 2, 1, if (hov) CARD_HOVER else CARD, BORDER)
        }
        val col = when {
            primary -> ON_ACCENT
            danger -> if (hov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER
            hov -> ScreenTheme.TEXT_COLOR
            else -> ScreenTheme.SUBTEXT_COLOR
        }
        val size = 7.5f
        UiRecorder.textBold(label, x + (w - UiRecorder.textWidth(label, size)) / 2f, y + (h - size) / 2f, size, col)
    }

    private fun toggle(id: String, label: String, x: Int, y: Int, w: Int, on: Boolean, mx: Int, my: Int) {
        rects[id] = intArrayOf(x, y, w, fh)
        val hov = mx >= x && mx < x + w && my >= y && my < y + fh
        ScreenTheme.nRoundedRectRing(x, y, w, fh, 4, 1, if (hov) CARD_HOVER else CARD, BORDER)
        UiRecorder.text(label, (x + 6).toFloat(), y + (fh - 7f) / 2f, 7f, ScreenTheme.TEXT_COLOR)
        val sx = x + w - 26
        val sy = y + (fh - 10) / 2
        ScreenTheme.nRoundedRect(sx, sy, 20, 10, 5, if (on) ScreenTheme.ACCENT else OFF)
        UiRecorder.disc((if (on) sx + 15 else sx + 5).toFloat(), sy + 5f, 3.5f, ScreenTheme.TEXT_COLOR)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        try {
            renderPanel(UiScale.vx(mouseX), UiScale.vx(mouseY))
        } catch (e: Exception) {
            FishDiag.fail("InventoryButtonEditScreen.1", "inventory button editor render failed", e)
        }
    }

    private fun renderPanel(mx: Int, my: Int) {
        UiRecorder.clear()
        val vw = width / UiScale.factor()
        val vh = height / UiScale.factor()
        val iw = pw - pad * 2
        UiRecorder.fillRect(0f, 0f, vw, vh, SCRIM)
        UiRecorder.dropShadow(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 10f, 16f, 0x70000000)
        ScreenTheme.nPanel(px, py, px + pw, py + ph, 10, BG, BORDER)
        UiRecorder.fillRect((px + 10).toFloat(), py.toFloat(), (pw - 20).toFloat(), 2f, ScreenTheme.ACCENT)

        // Header: live colour swatch + command preview
        val col = parseColor(fields[3].box.value) ?: b.color
        ScreenTheme.nRoundedRectRing(px + pad, py + 10, 24, 24, 6, 1, col, BORDER)
        UiRecorder.textBold("Edit Button", (px + pad + 32).toFloat(), (py + 13).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        val cmd = fields[0].box.value.trim()
        UiRecorder.text(if (cmd.isEmpty()) "no command" else "/" + cmd.take(34), (px + pad + 32).toFloat(), (py + 25).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nRect(px + pad, py + 39, iw, 1, BORDER)

        var tip: String? = null
        for ((i, f) in fields.withIndex()) {
            val e = f.box
            ScreenTheme.nTextField(e, i == focused, e.x, e.y, e.width, e.height)
            if (e.value.isEmpty()) UiRecorder.text(f.hint, (e.x + 3).toFloat(), e.y + 5.5f, 7f, HINT)
            if (mx >= e.x && mx < e.x + e.width && my >= e.y && my < e.y + e.height) tip = f.tip
        }
        val iconF = fields[2].box
        btn("held", "Held", iconF.x + iconF.width + 4, iconF.y, side, fh, mx, my)
        if (inside("held", mx, my)) tip = "Use the item in your main hand as the icon"
        val colF = fields[3].box
        ScreenTheme.nRoundedRectRing(colF.x + colF.width + 4, colF.y, side, fh, 4, 1, col, BORDER)

        val ry = py + 46 + 4 * (fh + 6)
        btn("smaller", "-", px + pad, ry, 18, fh, mx, my)
        val sz = "${b.size}"
        UiRecorder.text(sz, px + pad + 18 + (24 - UiRecorder.textWidth(sz, 7f)) / 2f, ry + (fh - 7f) / 2f, 7f, ScreenTheme.TEXT_COLOR)
        btn("bigger", "+", px + pad + 42, ry, 18, fh, mx, my)
        if (inside("smaller", mx, my) || inside("bigger", mx, my)) tip = "Button size (10-40)"
        toggle("showName", "Show name", px + pad + 66, ry, iw - 66, b.showName, mx, my)
        if (inside("showName", mx, my)) tip = "Draw the name next to the button"

        val by = py + ph - pad - 20
        btn("delete", "Delete", px + pad, by, 60, 20, mx, my, danger = true)
        btn("done", "Done", px + pw - pad - 70, by, 70, 20, mx, my, primary = true)

        tip?.let { t -> ScreenTheme.nItemTooltip(t.split('\n').map { Component.literal(it) }, mx, my, vw.toInt(), vh.toInt()) }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        for ((i, f) in fields.withIndex()) {
            val e = f.box
            if (mx >= e.x && mx < e.x + e.width && my >= e.y && my < e.y + e.height) { focus(i); return true }
        }
        when {
            inside("smaller", mx, my) -> b.size = (b.size - 2).coerceAtLeast(10)
            inside("bigger", mx, my) -> b.size = (b.size + 2).coerceAtMost(40)
            inside("showName", mx, my) -> b.showName = !b.showName
            inside("held", mx, my) -> {
                val held = Minecraft.getInstance().player?.mainHandItem
                if (held != null && !held.isEmpty) fields[2].box.value = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.item).toString()
            }
            inside("delete", mx, my) -> { deleted = true; onClose() }
            inside("done", mx, my) -> onClose()
            else -> return super.mouseClicked(click, doubled)
        }
        return true
    }

    private fun focus(i: Int) {
        fields[focused].box.isFocused = false
        focused = i
        fields[i].box.isFocused = true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> return super.keyPressed(input)
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { onClose(); return true }
            GLFW.GLFW_KEY_TAB -> { focus((focused + 1) % fields.size); return true }
        }
        if (fields[focused].box.keyPressed(input)) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        fields[focused].box.charTyped(input)
        return true
    }

    override fun paintUiOverlay() {
        UiRenderer.paint(width, height, UiScale.factor())
    }

    override fun onClose() {
        if (deleted) InvShortcutButtons.buttons.remove(b) else apply()
        InvShortcutButtons.save()
        Minecraft.getInstance().showScreen(parent)
    }
}

package fishmod.features.general

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class CommandWheelScreen : Screen(Component.literal("Command Wheel")), HasUiOverlay {

    private val slots = CommandWheel.slots().toList()
    private var hovered = -1
    private val grow = FloatArray(slots.size)
    private var opened = System.currentTimeMillis()
    private var lastFrame = opened

    private var cx = 0f
    private var cy = 0f
    private var ring = 100f

    private fun layout() {
        val f = UiScale.factor()
        val vw = width / f
        val vh = height / f
        cx = vw / 2f
        cy = vh / 2f
        ring = min(118f, min(vw, vh) * 0.34f)
    }

    private fun slotX(i: Int, scale: Float = 1f): Float = cx + cos(angle(i)) * ring * scale
    private fun slotY(i: Int, scale: Float = 1f): Float = cy + sin(angle(i)) * ring * scale
    private fun angle(i: Int): Float = (-PI / 2.0 + i * 2.0 * PI / slots.size).toFloat()

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        layout()
        val f = UiScale.factor()
        val mx = mouseX / f
        val my = mouseY / f
        val dx = mx - cx
        val dy = my - cy
        hovered = -1
        if (slots.isNotEmpty() && hypot(dx, dy) > CENTER_R * 0.8f) {
            var a = atan2(dy, dx) + (PI / 2.0).toFloat()
            val step = (2.0 * PI / slots.size).toFloat()
            while (a < 0) a += (2.0 * PI).toFloat()
            hovered = ((a + step / 2f) / step).toInt() % slots.size
        }
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now
        for (i in grow.indices) {
            val target = if (i == hovered) 1f else 0f
            grow[i] += (target - grow[i]) * min(1f, dt * 16f)
        }
        val open = ((now - opened) / 140f).coerceIn(0f, 1f)
        val ease = 1f - (1f - open) * (1f - open)

        UiRecorder.clear()
        UiRecorder.disc(cx, cy, ring * ease + R_BASE + 14f, 0x66000000)
        UiRecorder.roundedRectRing(cx - ring * ease, cy - ring * ease, ring * 2f * ease, ring * 2f * ease, ring * ease, 1.5f, 0, ScreenTheme.surface(0x30))
        for (i in slots.indices) {
            val s = slots[i]
            val g = grow[i]
            val r = (R_BASE + g * 7f) * ease
            val x = slotX(i, ease)
            val y = slotY(i, ease)
            if (g > 0.01f) UiRecorder.disc(x, y, r + 5f, al(s.color, 0.22f * g))
            UiRecorder.disc(x, y, r + 1.5f, if (i == hovered) s.color else al(s.color, 0.55f))
            UiRecorder.disc(x, y, r, if (i == hovered) 0xF0242A36.toInt() else 0xEE181C26.toInt())
            if (CommandWheel.stackFor(s.item) == null) {
                val g1 = s.label.trim().take(1).uppercase().ifEmpty { "?" }
                val w = UiRecorder.textWidthBold(g1, 16f)
                UiRecorder.textBold(g1, x - w / 2f, y - 8f, 16f, s.color)
            }
        }
        UiRecorder.disc(cx, cy, CENTER_R * ease, 0xEE12151D.toInt())
        UiRecorder.roundedRectRing(cx - CENTER_R * ease, cy - CENTER_R * ease, CENTER_R * 2f * ease, CENTER_R * 2f * ease, CENTER_R * ease, 1f, 0,
            if (hovered >= 0) slots[hovered].color else ScreenTheme.surface(0x30))
        if (slots.isEmpty()) {
            centered("No slots", cx, cy - 4f, 8f, ScreenTheme.SUBTEXT_COLOR)
        } else if (hovered >= 0) {
            val s = slots[hovered]
            val label = fit(s.label.ifBlank { "Slot ${hovered + 1}" }, CENTER_R * 1.7f, 9f, true)
            val cmd = fit("/" + s.command.trim().removePrefix("/"), CENTER_R * 1.7f, 6.5f, false)
            UiRecorder.textBold(label, cx - UiRecorder.textWidthBold(label, 9f) / 2f, cy - 9f, 9f, ScreenTheme.TEXT_COLOR)
            centered(cmd, cx, cy + 3f, 6.5f, ScreenTheme.SUBTEXT_COLOR)
        } else {
            centered("Move to pick", cx, cy - 7f, 7.5f, ScreenTheme.SUBTEXT_COLOR)
            centered("Release to run", cx, cy + 3f, 6.5f, ScreenTheme.surface(0x70))
        }
    }

    private fun centered(s: String, x: Float, y: Float, size: Float, color: Int) =
        UiRecorder.text(s, x - UiRecorder.textWidth(s, size) / 2f, y, size, color)

    private fun fit(s: String, maxW: Float, size: Float, bold: Boolean): String {
        fun w(t: String) = if (bold) UiRecorder.textWidthBold(t, size) else UiRecorder.textWidth(t, size)
        if (w(s) <= maxW) return s
        var t = s
        while (t.length > 1 && w("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun al(c: Int, a: Float): Int = ((c ushr 24) * a).toInt().coerceIn(0, 255) shl 24 or (c and 0xFFFFFF)

    override fun paintUiOverlay() {
        UiRenderer.paint(this.width, this.height, UiScale.factor())
    }

    fun extractLateLayer(state: net.minecraft.client.renderer.state.gui.GuiRenderState): Boolean {
        if (slots.isEmpty()) return false
        val mc = Minecraft.getInstance()
        val ctx = GuiGraphicsExtractor(mc, state, 0, 0)
        val f = UiScale.factor()
        val open = ((System.currentTimeMillis() - opened) / 140f).coerceIn(0f, 1f)
        if (open < 0.6f) return false
        for (i in slots.indices) {
            val st = CommandWheel.stackFor(slots[i].item) ?: continue
            val gx = Math.round(slotX(i) * f)
            val gy = Math.round(slotY(i) * f)
            val sc = 1f + grow[i] * 0.45f
            ctx.pose().pushMatrix()
            ctx.pose().translate(gx.toFloat(), gy.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.item(st, -8, -8)
            ctx.pose().popMatrix()
        }
        return true
    }

    override fun tick() {
        super.tick()
        val mc = Minecraft.getInstance()
        if (!CommandWheel.isDown(mc)) {
            val h = hovered
            if (h in slots.indices) CommandWheel.run(slots[h])
            onClose()
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == 0 && hovered in slots.indices) {
            CommandWheel.run(slots[hovered])
            onClose()
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val R_BASE = 25f
        const val CENTER_R = 42f
    }
}

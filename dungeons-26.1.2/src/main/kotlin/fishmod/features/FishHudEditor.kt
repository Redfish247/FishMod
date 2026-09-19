package fishmod.features

import config.practical.hud.HUDComponent
import fishmod.utils.config.FishConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.BooleanSupplier
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier

/**
 * HUD position/scale editor. Instead of dropping every HUD box on screen at once (they pile up and
 * overlap), the left panel lists the HUDs split into a Left / Right column by which half of the
 * screen they currently sit in; you pick one and only that box is interactive on the canvas, with
 * the rest shown as dim ghosts for context.
 */
class FishHudEditor(
    private val parent: Screen,
    /** When non-null, only HUDs whose name is in this set are shown/editable (per-column editing). */
    private val only: Set<String>? = null,
) : Screen(Component.literal("Edit HUD")) {

    /** Plain class, not a data class, so Java callers keep record-style accessors like `.name()`. */
    class HudEntry @JvmOverloads constructor(
        private val nameVal: String,
        private val getXVal: IntSupplier,
        private val setXVal: IntConsumer,
        private val getYVal: IntSupplier,
        private val setYVal: IntConsumer,
        private val wVal: Int,
        private val hVal: Int,
        private val lockedVal: Boolean = false,
        private val getScaleVal: DoubleSupplier? = null,
        private val setScaleVal: DoubleConsumer? = null,
        private val visibleVal: BooleanSupplier? = null
    ) {
        fun name(): String = nameVal
        fun getX(): IntSupplier = getXVal
        fun setX(): IntConsumer = setXVal
        fun getY(): IntSupplier = getYVal
        fun setY(): IntConsumer = setYVal
        fun w(): Int = wVal
        fun h(): Int = hVal
        fun locked(): Boolean = lockedVal
        fun getScale(): DoubleSupplier? = getScaleVal
        fun setScale(): DoubleConsumer? = setScaleVal
        fun visible(): BooleanSupplier? = visibleVal

        fun scale(): Double = if (getScaleVal != null) getScaleVal.asDouble else 1.0
        fun isVisible(): Boolean = visibleVal == null || visibleVal.asBoolean
    }

    companion object {
        private val ENTRIES: MutableList<HudEntry> = ArrayList()

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, null))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer,
            visible: BooleanSupplier
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, visible))
        }

        @JvmStatic
        fun registerLocked(name: String, getX: IntSupplier, getY: IntSupplier, w: Int, h: Int) {
            ENTRIES.add(HudEntry(name, getX, IntConsumer { }, getY, IntConsumer { }, w, h, true))
        }

        /** Uses true pixel space, not `getScaledX()`, so the box stays anchored while scaling. */
        @JvmStatic
        fun register(name: String, component: HUDComponent) {
            ENTRIES.add(
                HudEntry(
                    name,
                    IntSupplier { Math.round(component.scaledX * component.scale) },
                    IntConsumer { v ->
                        val ww = Minecraft.getInstance().window.guiScaledWidth
                        val cur = Math.round(component.scaledX * component.scale)
                        component.move((v - cur).toDouble() / ww, 0.0)
                    },
                    IntSupplier { Math.round(component.scaledY * component.scale) },
                    IntConsumer { v ->
                        val wh = Minecraft.getInstance().window.guiScaledHeight
                        val cur = Math.round(component.scaledY * component.scale)
                        component.move(0.0, (v - cur).toDouble() / wh)
                    },
                    component.width, component.height,
                    false,
                    DoubleSupplier { component.scale.toDouble() },
                    DoubleConsumer { v -> component.scale = v.toFloat() }
                )
            )
        }

        /** Default position/scale per HUD for "Reset positions"; keep in sync with defaults elsewhere. */
        private val DEFAULTS: Map<String, DoubleArray> = java.util.Map.ofEntries(
            java.util.Map.entry("Pet", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Session Stats", doubleArrayOf(10.0, 120.0, 1.0)),
            java.util.Map.entry("Dungeon Score", doubleArrayOf(10.0, 200.0, 1.0)),
            java.util.Map.entry("Simon Says", doubleArrayOf(10.0, 360.0, 1.0)),
            java.util.Map.entry("Soulflow", doubleArrayOf(10.0, 60.0, 1.0)),
            java.util.Map.entry("Spirit Bear", doubleArrayOf(10.0, 165.0, 1.5)),
            java.util.Map.entry("Blessings", doubleArrayOf(10.0, 100.0, 1.0)),
            java.util.Map.entry("Desk-Buddy", doubleArrayOf(10.0, 440.0, 1.5)),
            java.util.Map.entry("PB Pace", doubleArrayOf(10.0, 300.0, 1.0)),
            java.util.Map.entry("Tick Timer", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Wither Dragon Timer", doubleArrayOf(-1.0, 100.0, 2.0)),
            java.util.Map.entry("Crystal Spawn Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("Crystal Reminder", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Storm Death Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("LB Release Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Storm Crushed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Term Start Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Section Progress", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("Current Section", doubleArrayOf(10.0, 190.0, 1.0)),
            java.util.Map.entry("Device Completed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Melody Warning", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Section Completion", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("S4 Alert", doubleArrayOf(0.0, 0.0, 1.5)),
            java.util.Map.entry("S4 Debug", doubleArrayOf(10.0, 220.0, 1.0)),
            java.util.Map.entry("Relic Spawn Timer", doubleArrayOf(10.0, 180.0, 1.0)),
            java.util.Map.entry("Goldor Splits", doubleArrayOf(10.0, 128.0, 1.0)),
            java.util.Map.entry("Splits", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Puzzles", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Party Finder List", doubleArrayOf(6.0, 45.0, 1.0)),
            java.util.Map.entry("Dungeon Map", doubleArrayOf(100.0, 100.0, 1.0)),
            java.util.Map.entry("Slayer Spawn", doubleArrayOf(10.0, 140.0, 1.0)),
            java.util.Map.entry("Slayer Stats", doubleArrayOf(10.0, 170.0, 1.0)),
            java.util.Map.entry("Slayer Boss Timer", doubleArrayOf(10.0, 255.0, 1.0)),
            java.util.Map.entry("Slayer Profit", doubleArrayOf(240.0, 90.0, 1.0))
        )

        /** Which movable HUDs belong to each FishModScreen column, for its header "Edit HUD" button. */
        private val COLUMN_HUDS: Map<String, Set<String>> = mapOf(
            "Dungeon Trackers" to setOf("Session Stats", "PB Pace"),
            "Dungeons" to setOf("Dungeon Score", "Spirit Bear", "Blessings", "Simon Says", "Puzzles"),
            "Dungeon Solvers" to setOf("Puzzles", "Simon Says"),
            "Dungeon Map" to setOf("Dungeon Map", "Dungeon Score"),
            "Floor 7" to setOf(
                "Tick Timer", "Wither Dragon Timer", "Crystal Spawn Time", "Crystal Reminder",
                "Storm Death Time", "LB Release Timer", "Storm Crushed", "Term Start Timer",
                "Section Progress", "Goldor Splits", "Splits", "Current Section", "Device Completed",
                "Melody Warning", "Section Completion", "S4 Alert", "S4 Debug", "Relic Spawn Timer",
            ),
            "HUD & Overlays" to setOf(
                "Pet", "Soulflow", "Desk-Buddy",
            ),
            "Party & Social" to setOf("Party Finder List"),
            "Slayer" to setOf("Slayer Spawn", "Slayer Stats", "Slayer Boss Timer", "Slayer Profit"),
        )

        /** Non-empty HUD-name set for [columnName], or null if that column has no movable HUDs. */
        @JvmStatic
        fun columnHuds(columnName: String): Set<String>? = COLUMN_HUDS[columnName]?.takeIf { it.isNotEmpty() }

        private const val RESET_X = 10
        private const val RESET_W = 120

        private const val PANEL_W = 150
        private const val ROW_H = 12
        private const val LIST_TOP = 34

        private val ACCENT = 0xFF00AACC.toInt()
        private val ACCENT_HOVER = 0xFF00CCEE.toInt()
        private val BOX_FILL = 0x5500AACC
        private val BOX_HOVER = 0x7700AACC
        private val BOX_DRAG = 0x9900CCEE.toInt()
        private val PANEL_BG = 0xE0101418.toInt()
        private val ROW_SEL = 0xFF16333d.toInt()
        private val ROW_HOV = 0x33FFFFFF
        private val GHOST_LINE = 0x55AAAAAA
    }

    private var dragging: HudEntry? = null
    private var dragOffX = 0
    private var dragOffY = 0
    /** Last box the mouse touched — target for arrow-key nudge. */
    private var lastTouched: HudEntry? = null
    private var resetArmed = false

    override fun isPauseScreen(): Boolean = false

    private fun visibleEntries(): List<HudEntry> =
        ENTRIES.filter { it.isVisible() && (only == null || it.name() in only) }

    /** "Reset positions" — scoped to what this editor shows (all, or one column's HUDs). */
    private fun resetVisible() {
        for (e in visibleEntries()) {
            if (e.locked()) continue
            val d = DEFAULTS[e.name()] ?: continue
            e.setX().accept(d[0].toInt())
            e.setY().accept(d[1].toInt())
            e.setScale()?.accept(d[2])
        }
        FishConfig.manager.save()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000.toInt())

        ctx.centeredText(
            this.font,
            (if (only != null) "Editing this section's HUDs · " else "") + "Drag to move · scroll to resize · arrows nudge",
            this.width / 2, 10, 0xFFAAAAAA.toInt()
        )

        for (e in visibleEntries()) {
            val x = e.getX().asInt
            val y = e.getY().asInt
            val sw = Math.max(8, (e.w() * e.scale()).toInt())
            val sh = Math.max(8, (e.h() * e.scale()).toInt())
            val hov = !e.locked() && mouseX in x..(x + sw) && mouseY in y..(y + sh)
            val drag = e === dragging
            val fill = if (e.locked()) 0x33888888 else if (drag) BOX_DRAG else if (hov) BOX_HOVER else BOX_FILL
            val outline = if (e.locked()) 0xFF555555.toInt() else if (drag) ACCENT_HOVER else ACCENT
            ctx.fill(x, y, x + sw, y + sh, fill)
            ctx.fill(x, y, x + sw, y + 1, outline)
            ctx.fill(x, y + sh - 1, x + sw, y + sh, outline)
            ctx.fill(x, y, x + 1, y + sh, outline)
            ctx.fill(x + sw - 1, y, x + sw, y + sh, outline)

            val label = e.name() + (if (e.scale() != 1.0) String.format(" §7(%.2fx)", e.scale()) else "")
            val lw = this.font.width(label)
            val lx = if (lw + 6 <= sw) x + 3 else x
            val ly = if (lw + 6 <= sw) y + (sh - 8) / 2
                else if (y + sh + 10 <= this.height) y + sh + 1 else y - 10
            if (lw + 6 > sw) ctx.fill(lx - 1, ly - 1, lx + lw + 1, ly + 9, 0xC0000000.toInt())
            val labelColor = if (e.locked()) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
            ctx.text(this.font, label, lx, ly, labelColor, true)
        }

        val btnW = 60
        val btnH = 18
        val btnX = this.width / 2 - btnW / 2
        val btnY = this.height - 28
        val btnHov = mouseX in btnX..(btnX + btnW) && mouseY in btnY..(btnY + btnH)
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, if (btnHov) ACCENT_HOVER else ACCENT)
        ctx.centeredText(this.font, "Done", btnX + btnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF.toInt())

        val rHov = mouseX in RESET_X..(RESET_X + RESET_W) && mouseY in btnY..(btnY + btnH)
        val rFill = if (resetArmed) 0xFFAA3333.toInt() else if (rHov) 0xFF553333.toInt() else 0xFF442222.toInt()
        ctx.fill(RESET_X, btnY, RESET_X + RESET_W, btnY + btnH, rFill)
        ctx.centeredText(
            this.font,
            if (resetArmed) "§fClick to confirm" else "Reset positions",
            RESET_X + RESET_W / 2, btnY + (btnH - 8) / 2, 0xFFFFCCCC.toInt()
        )

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun boxUnder(mx: Int, my: Int): HudEntry? = visibleEntries().lastOrNull { e ->
        val x = e.getX().asInt
        val y = e.getY().asInt
        val sw = Math.max(8, (e.w() * e.scale()).toInt())
        val sh = Math.max(8, (e.h() * e.scale()).toInt())
        mx in x..(x + sw) && my in y..(y + sh)
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()

        val btnW = 60
        val btnH = 18
        val btnX = this.width / 2 - btnW / 2
        val btnY = this.height - 28
        if (mx in btnX..(btnX + btnW) && my in btnY..(btnY + btnH)) { this.onClose(); return true }

        if (mx in RESET_X..(RESET_X + RESET_W) && my in btnY..(btnY + btnH)) {
            if (resetArmed) { resetVisible(); resetArmed = false } else resetArmed = true
            return true
        }
        resetArmed = false

        val hit = boxUnder(mx, my) ?: return super.mouseClicked(click, bl)
        lastTouched = hit
        if (!hit.locked()) {
            dragging = hit
            dragOffX = mx - hit.getX().asInt
            dragOffY = my - hit.getY().asInt
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val d = dragging
        if (d != null) {
            val sw = Math.max(8, (d.w() * d.scale()).toInt())
            val sh = Math.max(8, (d.h() * d.scale()).toInt())
            var nx = click.x().toInt() - dragOffX
            var ny = click.y().toInt() - dragOffY
            nx = Math.max(0, Math.min(this.width - sw, nx))
            ny = Math.max(0, Math.min(this.height - sh, ny))
            d.setX().accept(nx)
            d.setY().accept(ny)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val e = boxUnder(mouseX.toInt(), mouseY.toInt())
        val setScale = e?.setScale()
        if (e == null || e.locked() || setScale == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }
        lastTouched = e
        val step = if (vertical > 0) 0.1 else -0.1
        setScale.accept(Math.max(0.5, Math.min(3.0, Math.round((e.scale() + step) * 100.0) / 100.0)))
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val e = lastTouched
        if (e != null && !e.locked()) {
            val step = if (input.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) 10 else 1
            val dx = when (input.key()) { GLFW.GLFW_KEY_LEFT -> -step; GLFW.GLFW_KEY_RIGHT -> step; else -> 0 }
            val dy = when (input.key()) { GLFW.GLFW_KEY_UP -> -step; GLFW.GLFW_KEY_DOWN -> step; else -> 0 }
            if (dx != 0 || dy != 0) {
                val sw = Math.max(8, (e.w() * e.scale()).toInt())
                val sh = Math.max(8, (e.h() * e.scale()).toInt())
                e.setX().accept(Math.max(0, Math.min(this.width - sw, e.getX().asInt + dx)))
                e.setY().accept(Math.max(0, Math.min(this.height - sh, e.getY().asInt + dy)))
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun onClose() {
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }
}

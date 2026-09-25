package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.shaded.practicalconfig.hud.HUDComponent
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

class FishHudEditor(
    private val parent: Screen,
    private val only: Set<String>? = null,
) : Screen(Component.literal("Edit HUD")) {

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

        @JvmStatic
        fun columnHuds(columnName: String): Set<String>? = COLUMN_HUDS[columnName]?.takeIf { it.isNotEmpty() }

        private const val SNAP = 4
        private const val MARGIN = 4
        private const val GRID = 10
        private const val GRIP = 3
        private const val MIN_SCALE = 0.5
        private const val MAX_SCALE = 3.0

        private const val BTN_W = 60
        private const val BTN_H = 18
        private const val BTN_GAP = 6

        private val ACCENT = 0xFF00AACC.toInt()
        private val ACCENT_HOVER = 0xFF00CCEE.toInt()
        private val BOX_FILL = 0x3300AACC
        private val BOX_HOVER = 0x5500AACC
        private val BOX_SEL = 0x6600CCEE
        private val GUIDE = 0xFFFF4FB0.toInt()
        private val GRID_LINE = 0x14FFFFFF

        private val CONTROLS = listOf(
            "Drag to Move",
            "Scroll or Drag Corner to Resize",
            "Tab to Swap Selected HUD",
            "Arrows for Precision Move (Shift = 10px)",
            "Ctrl+H to Center Horizontally",
            "Ctrl+V to Center Vertically",
            "Hold Alt to Disable Snapping",
            "R to Reset Selected HUD",
            "G to Toggle Grid",
            "Ctrl+Z / Ctrl+Y to Undo / Redo",
            "Enter to Save, Esc to Cancel",
            "F1 to Hide Controls",
        )

        // Session-sticky so reopening the editor keeps the user's choice.
        private var showControls = true
        private var showGrid = false
    }

    private data class Pos(val x: Int, val y: Int, val scale: Double)

    private var selected: HudEntry? = null
    private var dragging: HudEntry? = null
    private var resizing: HudEntry? = null
    private var dragOffX = 0
    private var dragOffY = 0
    private var dragBefore: List<Pos>? = null
    private var guideX: Int? = null
    private var guideY: Int? = null
    private var resetArmed = false

    private val undoStack = ArrayDeque<List<Pos>>()
    private val redoStack = ArrayDeque<List<Pos>>()
    private val opened: List<Pos> = snapshot()

    override fun isPauseScreen(): Boolean = false

    private fun visibleEntries(): List<HudEntry> =
        ENTRIES.filter { it.isVisible() && (only == null || it.name() in only) }

    private fun movable(): List<HudEntry> = visibleEntries().filter { !it.locked() }

    private fun sw(e: HudEntry) = Math.max(8, (e.w() * e.scale()).toInt())
    private fun sh(e: HudEntry) = Math.max(8, (e.h() * e.scale()).toInt())

    private fun snapshot(): List<Pos> = ENTRIES.map { Pos(it.getX().asInt, it.getY().asInt, it.scale()) }

    private fun restore(s: List<Pos>) {
        ENTRIES.forEachIndexed { i, e ->
            val p = s.getOrNull(i) ?: return@forEachIndexed
            if (e.locked()) return@forEachIndexed
            e.setScale()?.accept(p.scale)
            e.setX().accept(p.x)
            e.setY().accept(p.y)
        }
    }

    private fun pushUndo(before: List<Pos>) {
        if (before == snapshot()) return
        undoStack.addLast(before)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun edit(action: () -> Unit) {
        val before = snapshot()
        action()
        pushUndo(before)
    }

    private fun moveTo(e: HudEntry, x: Int, y: Int) {
        e.setX().accept(Math.max(0, Math.min(this.width - sw(e), x)))
        e.setY().accept(Math.max(0, Math.min(this.height - sh(e), y)))
    }

    private fun resetEntry(e: HudEntry) {
        if (e.locked()) return
        val d = DEFAULTS[e.name()] ?: return
        e.setScale()?.accept(d[2])
        e.setX().accept(d[0].toInt())
        e.setY().accept(d[1].toInt())
    }

    private fun altDown(): Boolean {
        val w = Minecraft.getInstance().window
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)
    }

    // Snaps the box's near edge, centre or far edge to screen edges/margins/centre and other HUDs' edges.
    private fun snap(e: HudEntry, x: Int, y: Int): Pair<Int, Int> {
        val xs = mutableListOf(0, MARGIN, this.width / 2, this.width - MARGIN, this.width)
        val ys = mutableListOf(0, MARGIN, this.height / 2, this.height - MARGIN, this.height)
        for (o in visibleEntries()) {
            if (o === e) continue
            val ox = o.getX().asInt
            val oy = o.getY().asInt
            xs += listOf(ox, ox + sw(o) / 2, ox + sw(o))
            ys += listOf(oy, oy + sh(o) / 2, oy + sh(o))
        }
        fun best(pos: Int, size: Int, lines: List<Int>): Pair<Int, Int?> {
            var bd = SNAP + 1
            var bl: Int? = null
            for (off in intArrayOf(0, size / 2, size)) for (l in lines) {
                val d = l - (pos + off)
                if (Math.abs(d) < Math.abs(bd)) { bd = d; bl = l }
            }
            return if (bl != null && Math.abs(bd) <= SNAP) Pair(pos + bd, bl) else Pair(pos, null)
        }
        val (nx, gx) = best(x, sw(e), xs)
        val (ny, gy) = best(y, sh(e), ys)
        guideX = gx
        guideY = gy
        return Pair(nx, ny)
    }

    private fun doneX() = this.width / 2 - BTN_W - BTN_GAP / 2
    private fun resetX() = this.width / 2 + BTN_GAP / 2
    private fun btnY() = this.height - 28

    private fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx in x..(x + w) && my in y..(y + h)

    private fun onGrip(e: HudEntry, mx: Int, my: Int): Boolean {
        if (e.locked() || e.setScale() == null) return false
        val gx = e.getX().asInt + sw(e)
        val gy = e.getY().asInt + sh(e)
        return mx in (gx - GRIP - 1)..(gx + GRIP) && my in (gy - GRIP - 1)..(gy + GRIP)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000.toInt())

        if (showGrid) {
            var gx = GRID
            while (gx < this.width) { ctx.fill(gx, 0, gx + 1, this.height, GRID_LINE); gx += GRID }
            var gy = GRID
            while (gy < this.height) { ctx.fill(0, gy, this.width, gy + 1, GRID_LINE); gy += GRID }
        }

        val sel = selected
        val header = if (sel != null) {
            "§b${sel.name()} §7· §f${Math.round(sel.scale() * 100)}% §7· §fx ${sel.getX().asInt}, y ${sel.getY().asInt}"
        } else {
            (if (only != null) "§7Editing this section's HUDs" else "§7Editing HUD layout") + " · click a HUD or press Tab"
        }
        ctx.centeredText(this.font, header, this.width / 2, 10, 0xFFFFFFFF.toInt())

        val hover = if (dragging == null && resizing == null) boxUnder(mouseX, mouseY) else null
        for (e in visibleEntries()) {
            val x = e.getX().asInt
            val y = e.getY().asInt
            val w = sw(e)
            val h = sh(e)
            val isSel = e === sel
            val fill = when {
                e.locked() -> 0x22888888
                isSel -> BOX_SEL
                e === hover -> BOX_HOVER
                else -> BOX_FILL
            }
            val outline = when {
                e.locked() -> 0xFF555555.toInt()
                isSel -> 0xFFFFFFFF.toInt()
                e === hover -> ACCENT_HOVER
                else -> ACCENT
            }
            ctx.fill(x, y, x + w, y + h, fill)
            ctx.fill(x, y, x + w, y + 1, outline)
            ctx.fill(x, y + h - 1, x + w, y + h, outline)
            ctx.fill(x, y, x + 1, y + h, outline)
            ctx.fill(x + w - 1, y, x + w, y + h, outline)

            if (isSel) {
                val tag = e.name() + " " + Math.round(e.scale() * 100) + "%"
                val tw = this.font.width(tag)
                val ty = if (y >= 12) y - 11 else y + h + 1
                ctx.fill(x, ty, x + tw + 4, ty + 11, ACCENT)
                ctx.text(this.font, tag, x + 2, ty + 2, 0xFF0B1417.toInt(), false)
                if (e.setScale() != null && !e.locked()) {
                    ctx.fill(x + w - GRIP - 1, y + h - GRIP - 1, x + w + GRIP, y + h + GRIP, 0xFF0B1417.toInt())
                    ctx.fill(x + w - GRIP, y + h - GRIP, x + w + GRIP - 1, y + h + GRIP - 1, ACCENT_HOVER)
                }
            } else {
                val label = e.name()
                if (this.font.width(label) + 6 <= w && h >= 10) {
                    val c = if (e.locked()) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
                    ctx.text(this.font, label, x + 3, y + (h - 8) / 2, c, true)
                }
            }
        }

        guideX?.let { ctx.fill(it, 0, it + 1, this.height, GUIDE) }
        guideY?.let { ctx.fill(0, it, this.width, it + 1, GUIDE) }

        if (showControls) {
            var ly = this.height - 6 - CONTROLS.size * 10
            for (line in CONTROLS) {
                ctx.text(this.font, line, 6, ly, 0xFFFFFFFF.toInt(), true)
                ly += 10
            }
        } else {
            ctx.text(this.font, "F1 to Show Controls", 6, this.height - 16, 0xFFAAAAAA.toInt(), true)
        }

        val by = btnY()
        val dHov = inRect(mouseX, mouseY, doneX(), by, BTN_W, BTN_H)
        ctx.fill(doneX(), by, doneX() + BTN_W, by + BTN_H, if (dHov) ACCENT_HOVER else ACCENT)
        ctx.centeredText(this.font, "Done", doneX() + BTN_W / 2, by + (BTN_H - 8) / 2, 0xFFFFFFFF.toInt())

        val rHov = inRect(mouseX, mouseY, resetX(), by, BTN_W, BTN_H)
        val rFill = if (resetArmed) 0xFFAA3333.toInt() else if (rHov) 0xFF553333.toInt() else 0xFF442222.toInt()
        ctx.fill(resetX(), by, resetX() + BTN_W, by + BTN_H, rFill)
        ctx.centeredText(
            this.font, if (resetArmed) "§fSure?" else "Reset all",
            resetX() + BTN_W / 2, by + (BTN_H - 8) / 2, 0xFFFFCCCC.toInt()
        )

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun boxUnder(mx: Int, my: Int): HudEntry? = visibleEntries().lastOrNull { e ->
        val x = e.getX().asInt
        val y = e.getY().asInt
        mx in x..(x + sw(e)) && my in y..(y + sh(e))
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val by = btnY()

        if (inRect(mx, my, doneX(), by, BTN_W, BTN_H)) { this.onClose(); return true }
        if (inRect(mx, my, resetX(), by, BTN_W, BTN_H)) {
            if (resetArmed) { edit { visibleEntries().forEach { resetEntry(it) } }; resetArmed = false } else resetArmed = true
            return true
        }
        resetArmed = false

        val sel = selected
        if (sel != null && onGrip(sel, mx, my)) {
            resizing = sel
            dragBefore = snapshot()
            return true
        }

        val hit = boxUnder(mx, my)
        if (hit == null) { selected = null; return super.mouseClicked(click, bl) }
        selected = hit
        if (!hit.locked()) {
            dragging = hit
            dragBefore = snapshot()
            dragOffX = mx - hit.getX().asInt
            dragOffY = my - hit.getY().asInt
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        resizing?.let { e ->
            val x = e.getX().asInt
            val y = e.getY().asInt
            val fit = Math.min((this.width - x).toDouble() / e.w(), (this.height - y).toDouble() / e.h())
            val s = Math.round((mx - x).toDouble() / e.w() * 20.0) / 20.0
            e.setScale()?.accept(Math.max(MIN_SCALE, Math.min(Math.min(MAX_SCALE, fit), s)))
            // HUDComponent positions are stored scaled, so pin the top-left after a scale change.
            e.setX().accept(x)
            e.setY().accept(y)
            return true
        }
        dragging?.let { e ->
            var nx = Math.max(0, Math.min(this.width - sw(e), mx - dragOffX))
            var ny = Math.max(0, Math.min(this.height - sh(e), my - dragOffY))
            if (altDown()) {
                guideX = null
                guideY = null
            } else {
                val p = snap(e, nx, ny)
                nx = p.first
                ny = p.second
            }
            moveTo(e, nx, ny)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        dragBefore?.let { pushUndo(it) }
        dragBefore = null
        dragging = null
        resizing = null
        guideX = null
        guideY = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val e = boxUnder(mouseX.toInt(), mouseY.toInt())
        val setScale = e?.setScale()
        if (e == null || e.locked() || setScale == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }
        selected = e
        val x = e.getX().asInt
        val y = e.getY().asInt
        val step = if (vertical > 0) 0.05 else -0.05
        edit {
            setScale.accept(Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round((e.scale() + step) * 100.0) / 100.0)))
            moveTo(e, x, y)
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val mods = input.modifiers()
        val ctrl = mods and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SUPER) != 0
        val shift = mods and GLFW.GLFW_MOD_SHIFT != 0
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { cancel(); return true }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { this.onClose(); return true }
            GLFW.GLFW_KEY_F1 -> { showControls = !showControls; return true }
            GLFW.GLFW_KEY_G -> if (!ctrl) { showGrid = !showGrid; return true }
            GLFW.GLFW_KEY_Z -> if (ctrl) { if (shift) redo() else undo(); return true }
            GLFW.GLFW_KEY_Y -> if (ctrl) { redo(); return true }
            GLFW.GLFW_KEY_TAB -> {
                val list = movable()
                if (list.isNotEmpty()) {
                    val i = list.indexOf(selected)
                    selected = list[if (i < 0) 0 else (i + (if (shift) list.size - 1 else 1)) % list.size]
                }
                return true
            }
        }

        val e = selected
        if (e != null && !e.locked()) {
            when (input.key()) {
                GLFW.GLFW_KEY_H -> if (ctrl) { edit { moveTo(e, (this.width - sw(e)) / 2, e.getY().asInt) }; return true }
                GLFW.GLFW_KEY_V -> if (ctrl) { edit { moveTo(e, e.getX().asInt, (this.height - sh(e)) / 2) }; return true }
                GLFW.GLFW_KEY_R -> if (!ctrl) { edit { resetEntry(e) }; return true }
            }
            val step = if (shift) 10 else 1
            val dx = when (input.key()) { GLFW.GLFW_KEY_LEFT -> -step; GLFW.GLFW_KEY_RIGHT -> step; else -> 0 }
            val dy = when (input.key()) { GLFW.GLFW_KEY_UP -> -step; GLFW.GLFW_KEY_DOWN -> step; else -> 0 }
            if (dx != 0 || dy != 0) {
                edit { moveTo(e, e.getX().asInt + dx, e.getY().asInt + dy) }
                return true
            }
        }
        return super.keyPressed(input)
    }

    private fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(s)
    }

    private fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(s)
    }

    private fun cancel() {
        restore(opened)
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun onClose() {
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }
}

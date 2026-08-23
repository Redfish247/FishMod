package fishmod.features

import config.practical.hud.HUDComponent
import fishmod.utils.config.FishConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.function.BooleanSupplier
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier

class FishHudEditor(private val parent: Screen) : Screen(Component.literal("Edit HUD")) {

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
            java.util.Map.entry("Farming Coins", doubleArrayOf(10.0, 240.0, 1.0)),
            java.util.Map.entry("Pet", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Harvest Feast", doubleArrayOf(10.0, 280.0, 1.0)),
            java.util.Map.entry("Session Stats", doubleArrayOf(10.0, 120.0, 1.0)),
            java.util.Map.entry("Dungeon Score", doubleArrayOf(10.0, 200.0, 1.0)),
            java.util.Map.entry("Simon Says", doubleArrayOf(10.0, 360.0, 1.0)),
            java.util.Map.entry("Soulflow", doubleArrayOf(10.0, 60.0, 1.0)),
            java.util.Map.entry("Mining Coins", doubleArrayOf(10.0, 320.0, 1.0)),
            java.util.Map.entry("Trophy Frogs", doubleArrayOf(10.0, 60.0, 1.0)),
            java.util.Map.entry("Bobber Reminder", doubleArrayOf(10.0, 140.0, 1.5)),
            java.util.Map.entry("Sea Creatures", doubleArrayOf(10.0, 160.0, 1.0)),
            java.util.Map.entry("Trophy Fish", doubleArrayOf(10.0, 200.0, 1.0)),
            java.util.Map.entry("Slayer Drops", doubleArrayOf(10.0, 240.0, 1.0)),
            java.util.Map.entry("Desk-Buddy", doubleArrayOf(10.0, 440.0, 1.5)),
            java.util.Map.entry("PB Pace", doubleArrayOf(10.0, 300.0, 1.0)),
            java.util.Map.entry("Slayer XP", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Skill XP", doubleArrayOf(10.0, 360.0, 1.0)),
            java.util.Map.entry("Powder", doubleArrayOf(10.0, 100.0, 1.0)),
            java.util.Map.entry("Challenges", doubleArrayOf(10.0, 400.0, 1.0)),
            java.util.Map.entry("Tick Timer", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Crystal Spawn Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("Crystal Reminder", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Storm Death Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("LB Release Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Storm Crushed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Term Start Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Section Progress", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("Goldor Splits", doubleArrayOf(10.0, 128.0, 1.0)),
            java.util.Map.entry("Splits", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Puzzles", doubleArrayOf(0.0, 0.0, 1.0))
        )

        @JvmStatic
        fun resetAll() {
            for (e in ENTRIES) {
                val d = DEFAULTS[e.name()] ?: continue
                if (e.locked()) continue
                e.setX().accept(d[0].toInt())
                e.setY().accept(d[1].toInt())
                e.setScale()?.accept(d[2])
            }
            FishConfig.manager.save()
        }

        private const val RESET_X = 10
        private const val RESET_W = 120

        private val ACCENT = 0xFF00AACC.toInt()
        private val ACCENT_HOVER = 0xFF00CCEE.toInt()
        private val BOX_FILL = 0x5500AACC
        private val BOX_HOVER = 0x7700AACC
        private val BOX_DRAG = 0x9900CCEE.toInt()
    }

    private var dragging: HudEntry? = null
    private var dragOffX = 0
    private var dragOffY = 0

    private var resetArmed = false

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000.toInt())

        ctx.centeredText(
            this.font,
            "Drag to move · scroll to resize", this.width / 2, 10, 0xFFAAAAAA.toInt()
        )

        val btnW = 60
        val btnH = 18
        val btnX = this.width / 2 - btnW / 2
        val btnY = this.height - 28
        val btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, if (btnHov) ACCENT_HOVER else ACCENT)
        ctx.centeredText(
            this.font, "Done",
            btnX + btnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF.toInt()
        )

        // Reset-positions button (bottom-left). First click arms, second click confirms.
        val rHov = mouseX >= RESET_X && mouseX <= RESET_X + RESET_W && mouseY >= btnY && mouseY <= btnY + btnH
        val rFill = if (resetArmed) 0xFFAA3333.toInt() else if (rHov) 0xFF553333.toInt() else 0xFF442222.toInt()
        ctx.fill(RESET_X, btnY, RESET_X + RESET_W, btnY + btnH, rFill)
        ctx.centeredText(
            this.font,
            if (resetArmed) "§fClick to confirm" else "Reset positions",
            RESET_X + RESET_W / 2, btnY + (btnH - 8) / 2, 0xFFFFCCCC.toInt()
        )

        // Only show entries active right now, so inactive ones can't overlap and get moved by accident.
        for (e in ENTRIES) {
            if (!e.isVisible()) continue
            val active = true
            val x = e.getX().asInt
            val y = e.getY().asInt
            val scaledW = Math.max(8, (e.w() * e.scale()).toInt())
            val scaledH = Math.max(8, (e.h() * e.scale()).toInt())
            val hov = !e.locked() && mouseX >= x && mouseX <= x + scaledW && mouseY >= y && mouseY <= y + scaledH
            val drag = e === dragging

            val fill = if (e.locked()) 0x33888888 else if (drag) BOX_DRAG else if (hov) BOX_HOVER else if (active) BOX_FILL else 0x33444444
            val outline = if (e.locked()) 0xFF555555.toInt() else if (drag) ACCENT_HOVER else if (active) ACCENT else 0xFF666666.toInt()

            ctx.fill(x, y, x + scaledW, y + scaledH, fill)
            ctx.fill(x, y, x + scaledW, y + 1, outline)
            ctx.fill(x, y + scaledH - 1, x + scaledW, y + scaledH, outline)
            ctx.fill(x, y, x + 1, y + scaledH, outline)
            ctx.fill(x + scaledW - 1, y, x + scaledW, y + scaledH, outline)

            val labelColor = if (e.locked()) 0xFF888888.toInt() else if (active) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
            val label = e.name() + (if (e.scale() != 1.0) String.format(" §7(%.2fx)", e.scale()) else "")
            val labelW = this.font.width(label)
            var labelX: Int
            var labelY: Int
            if (labelW + 6 <= scaledW) {
                labelX = x + 3
                labelY = y + (scaledH - 8) / 2
            } else {
                // too wide — drop label below (or above near screen bottom) with a dark backing
                labelX = x
                labelY = if (y + scaledH + 10 <= this.height) y + scaledH + 1 else y - 10
                ctx.fill(labelX - 1, labelY - 1, labelX + labelW + 1, labelY + 9, 0xC0000000.toInt())
            }
            ctx.text(this.font, label, labelX, labelY, labelColor, true)
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()

        val btnW = 60
        val btnH = 18
        val btnX = this.width / 2 - btnW / 2
        val btnY = this.height - 28
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            this.onClose()
            return true
        }

        // Reset-positions button: first click arms, second click performs the reset.
        if (mx >= RESET_X && mx <= RESET_X + RESET_W && my >= btnY && my <= btnY + btnH) {
            if (resetArmed) { resetAll(); resetArmed = false } else resetArmed = true
            return true
        }
        resetArmed = false // any other click disarms the confirm

        for (e in ENTRIES) {
            if (e.locked() || !e.isVisible()) continue
            val x = e.getX().asInt
            val y = e.getY().asInt
            val sw = Math.max(8, (e.w() * e.scale()).toInt())
            val sh = Math.max(8, (e.h() * e.scale()).toInt())
            if (mx >= x && mx <= x + sw && my >= y && my <= y + sh) {
                dragging = e
                dragOffX = mx - x
                dragOffY = my - y
                return true
            }
        }

        return super.mouseClicked(click, bl)
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val d = dragging
        if (d != null) {
            var nx = click.x().toInt() - dragOffX
            var ny = click.y().toInt() - dragOffY
            val sw = Math.max(8, (d.w() * d.scale()).toInt())
            val sh = Math.max(8, (d.h() * d.scale()).toInt())
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
        for (e in ENTRIES) {
            val setScale = e.setScale()
            if (e.locked() || setScale == null || !e.isVisible()) continue
            val x = e.getX().asInt
            val y = e.getY().asInt
            val scaledW = Math.max(8, (e.w() * e.scale()).toInt())
            val scaledH = Math.max(8, (e.h() * e.scale()).toInt())
            if (mouseX >= x && mouseX <= x + scaledW && mouseY >= y && mouseY <= y + scaledH) {
                val cur = e.scale()
                val step = if (vertical > 0) 0.1 else -0.1
                val next = Math.max(0.5, Math.min(3.0, Math.round((cur + step) * 100.0) / 100.0))
                setScale.accept(next)
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun onClose() {
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }
}

package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

object TerminalTracker {
    private class Slot(val label: String, val pos: Vec3?) {
        val shown = when (label[0]) { 'T' -> label.substring(1); 'D' -> "Dev"; else -> if (label == "L1") "LL" else if (label == "L2") "RL" else label }
        var player = ""
        var doneAt = -1L
        val done get() = doneAt >= 0
    }

    private fun t(n: Int, x: Double, y: Double, z: Double) = Slot("T$n", Vec3(x, y, z))
    private fun d(n: Int, x: Double, y: Double, z: Double) = Slot("D$n", Vec3(x, y, z))
    private fun l(n: Int, x: Double, y: Double, z: Double) = Slot("L$n", Vec3(x, y, z))

    // Layout per section, positions = player spot at completion
    private fun layout(section: Int): MutableList<Slot> = when (section) {
        1 -> mutableListOf(t(1, 109.9, 113.0, 73.4), t(2, 109.5, 119.0, 79.5), t(3, 91.9, 112.0, 92.4), t(4, 91.8, 122.0, 101.3),
            d(1, 108.8, 120.0, 93.9), l(1, 95.5, 123.0, 114.5), l(2, 105.3, 124.6, 113.3))
        2 -> mutableListOf(t(1, 68.6, 109.0, 124.9), t(2, 59.3, 120.0, 124.6), t(3, 46.3, 109.0, 122.3), t(4, 40.2, 109.0, 124.2),
            t(5, 39.5, 109.0, 140.6), d(1, 61.1, 132.0, 140.1), l(1, 28.3, 123.1, 128.1), l(2, 23.5, 132.0, 138.5))
        3 -> mutableListOf(t(1, 0.6, 109.0, 77.9), t(2, 0.4, 119.0, 93.1), t(3, 1.0, 109.0, 112.3), t(4, 15.5, 123.0, 94.5),
            d(1, -0.4, 120.0, 77.8), l(1, 5.0, 124.0, 55.9), l(2, 11.9, 121.6, 55.6))
        else -> mutableListOf(t(1, 41.6, 109.0, 32.2), t(2, 44.9, 121.0, 31.7), t(3, 67.2, 109.0, 31.8), t(4, 71.8, 115.0, 45.9),
            d(1, 63.5, 127.0, 35.5), l(1, 86.5, 128.3, 45.5), l(2, 84.8, 120.1, 35.3))
    }

    private const val SNAP_DIST = 12.0
    private const val MAX_SECTION = 4
    private const val DETECT_DIST = 15.0
    private var viewSection = 0

    private val slots = HashMap<Int, MutableList<Slot>>()
    private val starts = HashMap<Int, Long>()
    private val gates = HashMap<Int, Long>()

    @ConfigValue @JvmField var enabled: Boolean = false
    @ConfigValue @JvmField var showAllSections: Boolean = false
    @ConfigValue @JvmField var logPositions: Boolean = false
    @ConfigValue @JvmField var doneColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var pendingColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var nameColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var headerColor: Int = 0xFFFFFF55.toInt()

    @ConfigValue @JvmField
    var hud: HUDComponent = HUDComponent(
        10.0, 230.0, 150, 80, 1f, "Terminal Tracker", { false }, TerminalTracker::render, { enabled }
    )

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> clear(); false }
        Events.ON_PHASE_CHANGE.register {
            if (Phase.inTerminals() && viewSection == 0) {
                viewSection = 1
                starts.putIfAbsent(1, System.currentTimeMillis())
            }
            false
        }
        Events.ON_SECTION_CHANGE.register {
            if (viewSection == 0) viewSection = Section.getSection().coerceIn(1, MAX_SECTION)
            else viewSection = minOf(viewSection + 1, MAX_SECTION)
            starts.putIfAbsent(viewSection, System.currentTimeMillis())
            false
        }
        Events.ON_TERMINAL.register { name, _, objective, _, _ ->
            FishDiag.guard("TerminalTracker.1", "record $objective by $name") { record(name, objective) }
            false
        }
        Events.ON_GAME_MESSAGE.register { msg ->
            if (Phase.inTerminals() && msg.string == "The gate has been destroyed!") {
                val s = (1..3).firstOrNull { it !in gates && it <= maxOf(viewSection, 1) }
                if (s != null) gates[s] = System.currentTimeMillis()
            }
            false
        }
    }

    private fun clear() { slots.clear(); starts.clear(); gates.clear(); viewSection = 0 }

    // Section from where the player stood (survives reconnects), else the Section counter
    private fun detectSection(pos: Vec3?): Int {
        if (pos != null) {
            var best = 0
            var bestD = DETECT_DIST
            for (s in 1..MAX_SECTION) for (sl in slots.getOrPut(s) { layout(s) }) {
                val d = sl.pos?.distanceTo(pos) ?: continue
                if (d < bestD) { bestD = d; best = s }
            }
            if (best != 0) return best
        }
        return if (viewSection != 0) viewSection else Section.getSection()
    }

    private fun record(name: String, objective: String) {
        val pos = Minecraft.getInstance().level?.players()?.firstOrNull { it.gameProfile.name == name }?.position()
        val section = detectSection(pos)
        if (section !in 1..MAX_SECTION) return
        viewSection = section
        val prefix = when (objective) { "terminal" -> "T"; "device" -> "D"; else -> "L" }
        val now = System.currentTimeMillis()
        starts.putIfAbsent(section, now)
        val list = slots.getOrPut(section) { layout(section) }
        val free = list.filter { !it.done && it.label.startsWith(prefix) }
        val slot = pos?.let { p -> free.filter { it.pos != null }.minByOrNull { it.pos!!.distanceTo(p) }?.takeIf { it.pos!!.distanceTo(p) <= SNAP_DIST } }
            ?: free.firstOrNull()
            ?: Slot("$prefix${list.count { it.label.startsWith(prefix) } + 1}", null).also { list.add(it) }
        slot.player = name
        slot.doneAt = now
        if (logPositions) {
            val p = pos?.let { "%.1f, %.1f, %.1f".format(it.x, it.y, it.z) } ?: "unknown"
            val line = "[TermTracker] S$section $objective ${slot.label} by $name at $p"
            fishmod.utils.debug.Debug.LOGGER.info(line)
            Misc.addChatMessage(Component.literal("§7$line"))
        }
    }

    @JvmStatic
    fun display(): Boolean = enabled && Location.inDungeon() && Phase.isInFloor7() && Phase.inTerminals()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        try { renderInner(component, context) } catch (e: Exception) { FishDiag.fail("TerminalTracker.2", "terminal tracker render threw", e) }
    }

    private class Line(val text: String, val color: Int, val indent: Int = 0, val player: String = "", val time: String = "")

    private fun secs(from: Long, to: Long) = "%.1fs".format((to - from) / 1000.0)

    private fun renderInner(component: HUDComponent, context: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font ?: return
        val cur = (if (viewSection != 0) viewSection else Section.getSection()).coerceIn(1, MAX_SECTION)
        val lines = ArrayList<Line>()
        for (s in (if (showAllSections) (1..cur).toList() else listOf(cur))) {
            val list = slots[s] ?: layout(s)
            val start = starts[s] ?: System.currentTimeMillis()
            lines.add(Line("S$s (${list.count { it.done }}/${list.size})", headerColor))
            for (sl in list) {
                lines.add(if (sl.done) Line(sl.shown, doneColor, 4, sl.player, secs(start, sl.doneAt)) else Line(sl.shown, pendingColor, 4))
            }
            if (s <= 3) {
                val g = gates[s]
                lines.add(if (g != null) Line("Gate", doneColor, 4, "", secs(start, g)) else Line("Gate", pendingColor, 4))
            }
        }
        val rowH = font.lineHeight + 1
        val x = component.scaledX
        val y0 = component.scaledY
        lines.forEachIndexed { i, ln ->
            val y = y0 + i * rowH
            var cx = x + ln.indent
            context.text(font, ln.text, cx, y, ln.color, true)
            cx += font.width(ln.text) + 4
            if (ln.player.isNotEmpty()) {
                context.text(font, ln.player, cx, y, nameColor, true)
                cx += font.width(ln.player) + 4
            }
            if (ln.time.isNotEmpty()) context.text(font, ln.time, cx, y, ln.color, true)
        }
    }
}

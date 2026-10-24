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
    private class Entry(val kind: String, val player: String, val pos: Vec3?, val label: String)

    // section -> (label, position); empty until Eli captures coordinates with "Log Positions"
    private val KNOWN: Map<Int, List<Pair<String, Vec3>>> = emptyMap()
    private const val SNAP_DIST = 12.0

    private val entries = HashMap<Int, MutableList<Entry>>()
    private val totals = HashMap<Int, Int>()
    private val gates = HashSet<Int>()

    @ConfigValue @JvmField var enabled: Boolean = false
    @ConfigValue @JvmField var showAllSections: Boolean = false
    @ConfigValue @JvmField var logPositions: Boolean = false
    @ConfigValue @JvmField var title: String = "Terminals"
    @ConfigValue @JvmField var bgOpacity: Int = 40
    @ConfigValue @JvmField var bgColor: Int = 0xFF000000.toInt()
    @ConfigValue @JvmField var termColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var deviceColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var leverColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gateColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var nameColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var missingColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField
    var hud: HUDComponent = HUDComponent(
        10.0, 230.0, 150, 80, 1f, "Terminal Tracker", { false }, TerminalTracker::render, { enabled }
    )

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> clear(); false }
        Events.ON_TERMINAL.register { name, _, objective, current, total ->
            FishDiag.guard("TerminalTracker.1", "record $objective by $name") { record(name, objective, total) }
            false
        }
        Events.ON_GAME_MESSAGE.register { msg ->
            if (Phase.inTerminals() && msg.string == "The gate has been destroyed!") {
                val s = (1..4).firstOrNull { it !in gates && it <= maxOf(Section.getSection(), 1) } ?: return@register false
                gates.add(s)
            }
            false
        }
    }

    private fun clear() { entries.clear(); totals.clear(); gates.clear() }

    private fun record(name: String, objective: String, total: Int) {
        val section = Section.getSection()
        if (section !in 1..4) return
        val mc = Minecraft.getInstance()
        val pos = mc.level?.players()?.firstOrNull { it.gameProfile.name == name }?.position()
        val list = entries.getOrPut(section) { mutableListOf() }
        val nth = list.count { it.kind == objective } + 1
        val snapped = pos?.let { p -> KNOWN[section]?.minByOrNull { it.second.distanceTo(p) }?.takeIf { it.second.distanceTo(p) <= SNAP_DIST }?.first }
        list.add(Entry(objective, name, pos, snapped ?: "#$nth"))
        totals[section] = total
        if (logPositions) {
            val p = pos?.let { "%.1f, %.1f, %.1f".format(it.x, it.y, it.z) } ?: "unknown"
            val line = "[TermTracker] S$section $objective #$nth by $name at $p"
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

    private fun kindColor(kind: String): Int = when (kind) {
        "terminal" -> termColor
        "device" -> deviceColor
        else -> leverColor
    }

    private fun kindName(kind: String): String = when (kind) {
        "terminal" -> "Term"
        "device" -> "Dev"
        else -> "Lever"
    }

    private class Line(val text: String, val color: Int, val indent: Int = 0, val player: String = "")

    private fun renderInner(component: HUDComponent, context: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font ?: return
        val cur = Section.getSection().coerceIn(1, 4)
        val lines = ArrayList<Line>()
        lines.add(Line(title, nameColor))
        for (s in (if (showAllSections) (1..cur).toList() else listOf(cur))) {
            val list = entries[s].orEmpty()
            val total = totals[s] ?: Section.totalFor(s)
            lines.add(Line("S$s (${list.size}/$total)", nameColor))
            for (e in list) lines.add(Line("${kindName(e.kind)} ${e.label}", kindColor(e.kind), 4, e.player))
            if (list.size < total) lines.add(Line("Missing ${total - list.size}", missingColor, 4))
            val gate = s in gates
            lines.add(Line(if (gate) "Gate done" else "Gate pending", if (gate) gateColor else missingColor, 4))
        }
        val rowH = font.lineHeight + 1
        val x = component.scaledX
        val y0 = component.scaledY
        val alpha = (bgOpacity.coerceIn(0, 100) * 255 / 100) shl 24
        context.fill(x - 2, y0 - 2, x + component.width, y0 + lines.size * rowH + 1, alpha or (bgColor and 0xFFFFFF))
        lines.forEachIndexed { i, l ->
            val y = y0 + i * rowH
            context.text(font, l.text, x + l.indent, y, l.color, true)
            if (l.player.isNotEmpty()) context.text(font, l.player, x + l.indent + font.width(l.text + " "), y, nameColor, true)
        }
    }
}

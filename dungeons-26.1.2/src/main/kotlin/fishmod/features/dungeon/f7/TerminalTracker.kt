package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object TerminalTracker {
    private val st = TerminalTrackerState()
    private const val MAX_SECTION = TerminalTrackerState.MAX_SECTION

    @ConfigValue @JvmField var terminalTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var showAllSections: Boolean = false
    @ConfigValue @JvmField var logPositions: Boolean = false
    @ConfigValue @JvmField var doneColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var pendingColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var nameColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var headerColor: Int = 0xFFFFFF55.toInt()

    @ConfigValue @JvmField
    var terminalTrackerHud: HUDComponent = HUDComponent(
        10.0, 230.0, 150, 80, 1f, "Terminal Tracker", { false }, TerminalTracker::render, { terminalTrackerEnabled }
    )

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> st.clear(); false }
        Events.ON_PHASE_CHANGE.register {
            // Fresh state each P3; leftovers from a run without a location change ate S1
            if (Phase.inTerminals()) st.terminalsStarted(System.currentTimeMillis())
            false
        }
        Events.ON_SECTION_CHANGE.register { st.sectionChanged(Section.getSection(), System.currentTimeMillis()); false }
        Events.ON_TERMINAL.register { name, _, objective, _, _ ->
            FishDiag.guard("TerminalTracker.1", "record $objective by $name") { record(name, objective) }
            false
        }
        Events.ON_GAME_MESSAGE.register { msg ->
            if (Phase.inTerminals() && msg.string == "The gate has been destroyed!") st.gateDestroyed(System.currentTimeMillis())
            false
        }
    }

    private fun record(name: String, objective: String) {
        val pos = Minecraft.getInstance().level?.players()?.firstOrNull { it.gameProfile.name == name }?.position()
        val cls = DungeonClass.getClass(name)
        val s1Dev = cls == null || cls == DungeonClass.HEALER || cls == DungeonClass.TANK
        val (section, slot) = st.record(name, objective, pos?.x, pos?.z, Section.getSection(), cls == DungeonClass.BERSERK, s1Dev, System.currentTimeMillis()) ?: return
        if (logPositions) {
            val p = pos?.let { "%.1f, %.1f, %.1f".format(it.x, it.y, it.z) } ?: "unknown"
            val line = "[TermTracker] S$section $objective ${slot.label} by $name at $p"
            fishmod.utils.debug.Debug.LOGGER.info(line)
            Misc.addChatMessage(Component.literal("§7$line"))
        }
    }

    @JvmStatic
    fun display(): Boolean = terminalTrackerEnabled && Location.inDungeon() && Phase.isInFloor7() && (Phase.inTerminals() || (Phase.inP2() && Phase.stormDead()))

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        try { renderInner(component, context) } catch (e: Exception) { FishDiag.fail("TerminalTracker.2", "terminal tracker render threw", e) }
    }

    private class Line(val text: String, val color: Int, val indent: Int = 0, val player: String = "", val time: String = "")

    private fun secs(from: Long, to: Long) = "%.1fs".format((to - from) / 1000.0)

    private fun renderInner(component: HUDComponent, context: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font ?: return
        val cur = st.current(Section.getSection()).coerceIn(1, MAX_SECTION)
        val lines = ArrayList<Line>()
        for (s in (if (showAllSections) (1..cur).toList() else listOf(cur))) {
            val list = st.list(s)
            val start = st.starts[s] ?: System.currentTimeMillis()
            lines.add(Line("S$s (${list.count { it.done }}/${list.size})", headerColor))
            for (sl in list) {
                lines.add(if (sl.done) Line(sl.shown, doneColor, 4, sl.player, if (sl.pre) "pre" else secs(start, sl.doneAt)) else Line(sl.shown, pendingColor, 4))
            }
            if (s <= 3) {
                val g = st.gates[s]
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

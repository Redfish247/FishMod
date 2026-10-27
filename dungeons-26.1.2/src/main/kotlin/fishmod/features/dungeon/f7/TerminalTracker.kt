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

    // /termreplay: client-only replay of the 2026-10-26 22:35 run at real pace (chat + HUD, nothing sent)
    private var replayUntil = 0L
    private val replayRun = listOf(
        "0|0|§c[BOSS] Goldor§f: Who dares trespass into my domain?",
        "1880|1|Olkebex|terminal", "2250|1|Hayacyaa|lever", "3260|1|Hayacyaa|lever", "4030|1|gate",
        "5110|1|Olkebex|terminal", "5660|1|xkxr|terminal", "5780|1|RedFish2471|device", "8010|1|jtszn|device", "8640|1|xkxr|terminal",
        "8650|2|section", "10560|2|Hayacyaa|device", "12710|2|RedFish2471|terminal", "13050|2|Olkebex|terminal", "13590|2|jtszn|lever",
        "13670|2|xkxr|terminal", "14640|2|gate", "15690|2|Olkebex|lever", "16560|2|RedFish2471|terminal", "17600|2|Hayacyaa|terminal",
        "17610|3|section", "20400|3|xkxr|terminal", "22480|3|Olkebex|terminal", "22530|3|jtszn|terminal", "23530|3|xkxr|lever",
        "27000|4|section",
    )

    @JvmStatic
    fun replay() {
        val mc = Minecraft.getInstance()
        val t0 = System.currentTimeMillis() + 1500
        replayUntil = t0 + 40000
        mc.execute {
            // Leftovers from a "previous run" so the reset is visible
            st.sectionChanged(4, t0 - 5000)
            listOf("T1", "T2", "L1").forEach { l -> st.list(4).first { it.label == l }.doneAt = t0 - 4000 }
            Misc.addChatMessage(Component.literal("§7[TermReplay] previous run leftovers in S4, P3 starts in 1.5s"))
        }
        Thread({
            val counts = HashMap<Int, Int>()
            for (line in replayRun) {
                val p = line.split("|")
                val at = t0 + p[0].toLong()
                Thread.sleep(maxOf(0L, at - System.currentTimeMillis()))
                val sec = p[1].toInt()
                mc.execute {
                    when {
                        p.size == 3 && p[0] == "0" -> { Misc.addChatMessage(Component.literal(p[2])); st.terminalsStarted(at) }
                        p[2] == "section" -> st.sectionChanged(sec, at)
                        p[2] == "gate" -> { Misc.addChatMessage(Component.literal("The gate has been destroyed!")); st.gateDestroyed(at) }
                        else -> {
                            val n = (counts[sec] ?: 0) + 1
                            counts[sec] = n
                            val verb = if (p[3] == "device") "completed" else "activated"
                            Misc.addChatMessage(Component.literal("§b${p[2]} §a$verb a ${p[3]}! (§c$n§a/${if (sec == 2) 8 else 7})"))
                            st.record(p[2], p[3], null, null, sec, p[2] == "RedFish2471", true, at)
                        }
                    }
                }
            }
            Thread.sleep(maxOf(0L, replayUntil - System.currentTimeMillis()))
            mc.execute { st.clear(); Misc.addChatMessage(Component.literal("§7[TermReplay] done")) }
        }, "TermReplay").apply { isDaemon = true }.start()
    }

    @JvmStatic
    fun display(): Boolean = terminalTrackerEnabled && System.currentTimeMillis() < replayUntil ||
        terminalTrackerEnabled &&Location.inDungeon() && Phase.isInFloor7() && (Phase.inTerminals() || (Phase.inP2() && Phase.stormDead()))

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

package fishmod.utils.dungeon

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.features.dungeon.PbMessages
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.Constants
import fishmod.utils.JsonUtility
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import fishmod.utils.events.interfaces.PhaseEvent
import fishmod.utils.events.interfaces.RunEndEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.util.regex.Pattern

object Phase {

    private val END_PATTERN: Pattern = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")

    private val DUMMY_SPLIT = Split("test split", "if this is called idk", "if this is called idk", 43690, 0.0)

    private val FLOOR_SPLITS: HashMap<String, ArrayList<Split>> = JsonUtility.readSplits("/data/fishmod_splits.json")

    private const val DUMMY_SIZE = 10
    const val SPLIT_LENGTH: Int = 165

    private var currentSplits: ArrayList<Split>? = null
    private var currentPhase = -1
    private var floor: String? = ""

    private var inFloor7 = false
    private var stormDead = false
    private var stormKillAnnounced = false
    private var runOver = false

    @ConfigValue @JvmField var enableSplits: Boolean = false

    @ConfigValue @JvmField var includeTotalTime: Boolean = false

    @ConfigValue @JvmField var sendSplitInChat: Boolean = false

    @ConfigValue @JvmField var onlyShowActivatedSplits: Boolean = false

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (floor == null) detectFloor()
            val splits = currentSplits
            if (splits == null || runOver) return@register false
            for (split in splits) {
                split.tick()
            }
            checkP5Fallback()
            false
        }

        Events.ON_LOCATION_CHANGE.register { _ ->
            reset()
            false
        }

        Events.ON_GAME_MESSAGE.register(Phase::parseGameMessage)
    }

    private fun detectFloor() {
        if (floor != null) return
        val key = fishmod.features.dungeon.map.DungeonState.currentFloorKey() ?: return
        floor = key

        currentSplits = FLOOR_SPLITS[floor]
        FishDiag.check(currentSplits != null, "Phase.1") { "no split definitions for floor key '$key' (have ${FLOOR_SPLITS.keys})" }
        currentSplits?.forEach { it.reset() }
        if (floor!!.contains("7")) inFloor7 = true
    }

    // Alpha doesn't send Necron's death line; his boss bar emptying or vanishing stands in for it
    private fun checkP5Fallback() {
        if (!awaitingNecronDeath()) return
        val bars = (Minecraft.getInstance().gui.bossOverlay as BossBarHudAccessor).bossBars.values
        val necron = bars.firstOrNull { it.name.string.contains("Necron") }
        if (necron != null && necron.progress > 0f) { necronBarSeen = true; return }
        if (necronBarSeen) fireP5Start(if (necron == null) "necron bar gone" else "necron bar empty")
    }

    private fun awaitingNecronDeath() = inFloor7 && currentPhase == 8 && currentSplits?.getOrNull(9)?.name == "Dragons"

    private fun fireP5Start(reason: String) {
        fishmod.utils.debug.Debug.LOGGER.info("[Phase] P5 fallback: $reason")
        Events.ON_GAME_MESSAGE.invoke { it.onGameMessage(Component.literal(P5_START)) }
    }

    private const val P5_START = "[BOSS] Necron: All this, for nothing..."
    private const val WK_FIRST_LINE = "[BOSS] Wither King: You... again?"
    private var necronBarSeen = false

    private fun reset() {
        necronBarSeen = false
        currentSplits = null
        floor = null
        currentPhase = -1
        inFloor7 = false
        stormDead = false
        stormKillAnnounced = false
        runOver = false
    }

    @JvmStatic
    fun parseGameMessage(message: Component): Boolean {
        val string = message.string
        val splits = currentSplits ?: return false
        if (runOver) return false
        if (string == WK_FIRST_LINE && awaitingNecronDeath()) fireP5Start("wither king spoke")

        for (i in splits.indices) {
            val currentSplit = splits[i]
            if (currentSplit.ended()) continue

            try {
                currentSplit.parseMessage(string)
            } catch (t: Throwable) {
                FishDiag.fail("Phase.2", "split '${currentSplit.name}' parseMessage", t)
                continue
            }

            if (currentSplit.ended()) {
                val pb = FishDiag.guard("Phase.3", "split PB for ${currentSplit.name}") { splitPb(currentSplit) }
                val tick = FishDiag.guard("Phase.4", "tick PB for ${currentSplit.name}") { tickPb(currentSplit) }
                if (sendSplitInChat) {
                    val line = currentSplit.createNameText().append(currentSplit.createTimeText())
                    if (showSplitPb()) appendPbTags(line, pb, tick, false)
                    Misc.addChatMessage(line)
                } else if (showSplitPb() && anyPbToShow(pb, tick)) {
                    Misc.addChatMessage(appendPbTags(currentSplit.createNameText().append(currentSplit.createTimeText()), pb, tick, true))
                }

                FishDiag.check(i + 1 >= currentPhase, "Phase.5") { "phase went backwards $currentPhase -> ${i + 1} floor=$floor split=${currentSplit.name}" }
                currentPhase = i + 1
                FishDiag.guard("Phase.6", "ON_PHASE_CHANGE listeners") { Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange) }
            }

            if (currentSplit.started() && currentPhase == -1) {
                currentPhase = i
                FishDiag.guard("Phase.7", "ON_PHASE_CHANGE listeners (run start)") { Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange) }
            }
        }

        val matcher = END_PATTERN.matcher(string)
        if (matcher.find()) {
            endRun()
        }

        if (inP2()) {
            if (string == "[BOSS] Storm: I should have known that I stood no chance.") {
                stormDead = true
            }
        }

        return false
    }

    @JvmStatic
    fun onStormKill(secs: Double) {
        if (stormKillAnnounced) return
        stormKillAnnounced = true
        if (PracticeMode.active) return
        val f = floor ?: return
        FishDiag.check(!secs.isNaN() && secs > 0, "Phase.8") { "storm kill with bad time $secs" }
        FishDiag.guard("Phase.9", "announce storm kill PB") {
            PbMessages.announce(FishSettings.pbMessagesStormKill, "stormkill:$f",
                Component.literal("§3Storm Kill§a in"), secs)
        }
    }

    private fun endRun() {
        runOver = true
        currentPhase = currentSplits?.size ?: 0
        FishDiag.check(currentSplits != null, "Phase.10") { "run end message with no splits loaded floor=$floor" }
        Scheduler.scheduleTask(Runnable {
            try { printSplits() } catch (t: Throwable) { FishDiag.fail("Phase.11", "print end-of-run splits floor=$floor", t) }
        }, 2)
        FishDiag.guard("Phase.12", "ON_RUN_END listeners") { Events.ON_RUN_END.invoke(RunEndEvent::onRunEnd) }
    }

    @JvmStatic
    fun getFloor(): String? = floor

    @JvmStatic
    fun splitFloors(): List<String> = FLOOR_ORDER.filter { FLOOR_SPLITS.containsKey(it) }

    private val FLOOR_ORDER = listOf("E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7")

    @JvmStatic
    fun distinctSplits(): List<Split> {
        val seen = LinkedHashMap<String, Split>()
        for (f in FLOOR_ORDER + FLOOR_SPLITS.keys) FLOOR_SPLITS[f]?.forEach { seen.putIfAbsent(it.name, it) }
        val run = seen.remove("Run Time")
        return seen.values.toList() + listOfNotNull(run)
    }

    @JvmStatic
    fun pbSplitsCommand(arg: String?) {
        val f = arg?.uppercase() ?: floor?.takeIf { FLOOR_SPLITS.containsKey(it) } ?: "M7"
        val splits = FLOOR_SPLITS[f]
        if (splits == null) {
            Misc.addChatMessage(Component.literal("§cNo splits for §f$f§c. Try one of: ${FLOOR_ORDER.filter { FLOOR_SPLITS.containsKey(it) }.joinToString(", ")}"))
            return
        }
        Misc.addChatMessage(Component.literal("§d§l$f PB Splits §8(§ereal §8/ §3tick§8)"))
        var total = 0.0
        var tickTotal = 0.0
        var missing = 0
        var tickMissing = 0
        for (s in splits) {
            if (s.avg < 0) continue
            val pb = seedPb(f, s.name)
            val tick = PbMessages.get("splittick:$f:${s.name}")
            val line = s.createNameText()
            if (pb == null) { missing++; line.append(Component.literal("§7—")) }
            else { total += pb; line.append(Component.literal("§e${PbMessages.fmt(pb)}")) }
            if (tick == null) { tickMissing++; line.append(Component.literal(" §8(§7—§8)")) }
            else { tickTotal += tick; line.append(Component.literal(" §8(§3${PbMessages.fmt(tick)}§8)")) }
            Misc.addChatMessage(line)
        }
        val sum = Component.literal("§aSum of best: §e§l${PbMessages.fmt(total)}")
        if (missing > 0) sum.append(Component.literal(" §7($missing split${if (missing == 1) "" else "s"} with no PB yet)"))
        PbMessages.get("split:$f:Run Time")?.let { sum.append(Component.literal(" §8| §7Run PB §f${PbMessages.fmt(it)}")) }
        Misc.addChatMessage(sum)
        val tickSum = Component.literal("§aSum of tick best: §3§l${PbMessages.fmt(tickTotal)}")
        if (tickMissing > 0) tickSum.append(Component.literal(" §7($tickMissing split${if (tickMissing == 1) "" else "s"} with no tick PB yet)"))
        PbMessages.get("splittick:$f:Run Time")?.let { tickSum.append(Component.literal(" §8| §7Run tick PB §f${PbMessages.fmt(it)}")) }
        Misc.addChatMessage(tickSum)
    }

    private fun splitPb(split: Split): PbMessages.Result? {
        if (PracticeMode.active) return null
        val f = floor ?: return null
        val t = split.getRealTime()
        val avg = RunHistory.getPersonalAvg(f, split.name)
        seedPb(f, split.name)
        FishDiag.check(!t.isNaN() && t >= 0, "Phase.13") { "split ${split.name} ended with bad time $t floor=$f" }
        val r = PbMessages.submit("split:$f:${split.name}", t) ?: return null
        split.paceColor = paceColor(r, avg)
        return r
    }

    private fun tickPb(split: Split): PbMessages.Result? {
        if (PracticeMode.active) return null
        val f = floor ?: return null
        val t = split.getTickTime()
        FishDiag.check(!t.isNaN() && t >= 0, "Phase.14") { "split ${split.name} bad tick time $t floor=$f" }
        return PbMessages.submit("splittick:$f:${split.name}", t)
    }

    private fun anyPbToShow(pb: PbMessages.Result?, tick: PbMessages.Result?): Boolean =
        pb?.isPb == true || tick?.isPb == true || (!FishSettings.pbMessagesOnlyPb && (pb != null || tick != null))

    private fun appendPbTags(line: MutableComponent, pb: PbMessages.Result?, tick: PbMessages.Result?, respectOnlyPb: Boolean): MutableComponent {
        val onlyPb = respectOnlyPb && FishSettings.pbMessagesOnlyPb
        if (pb != null && (pb.isPb || !onlyPb)) line.append(PbMessages.tag(pb))
        if (tick != null && (tick.isPb || !onlyPb)) line.append(PbMessages.tickTag(tick))
        return line
    }

    private fun seedPb(f: String, name: String): Double? {
        val key = "split:$f:$name"
        PbMessages.get(key)?.let { return it }
        if (PbMessages.get(PbMessages.noSeedKey(f)) != null) return null
        val hist = RunHistory.getPersonalBest(f, name)
        if (hist <= 0) return null
        PbMessages.submit(key, hist)
        return hist
    }

    @JvmStatic
    fun paceColor(r: PbMessages.Result, avg: Double): Int = when {
        r.isPb && r.previous != null -> Split.PB_COLOR
        avg > 0 && r.seconds < avg -> Split.AVG_COLOR
        else -> 0
    }

    private fun showSplitPb(): Boolean = FishSettings.pbMessagesEnabled && FishSettings.pbMessagesSplits

    private fun printSplits() {
        Misc.addChatMessage(Component.literal("§aSplits: "))
        val splits = currentSplits ?: return
        for (split in splits) {
            val wasRunning = split.started()
            split.end()
            val pb = if (wasRunning) splitPb(split) else null
            val tick = if (wasRunning) tickPb(split) else null
            val line = split.createNameText().append(split.createTimeText())
            if (showSplitPb()) appendPbTags(line, pb, tick, true)
            Misc.addChatMessage(line)
        }
        FishDiag.guard("Phase.15", "save run history floor=$floor") { RunHistory.saveSplits(floor, splits) }
        if (splits.isNotEmpty()) {
            val time = splits.last().getTimeDifference()
            val formattedTime = Constants.DECIMAL_FORMAT.format(time)
            val timeLost = Component.literal("§aApproximately §e" + formattedTime + "s §alost to lag.")
            Misc.addChatMessage(timeLost)
        }
    }

    private fun phase(): Int =
        if (PracticeMode.active && PracticeMode.phaseOverride >= 0) PracticeMode.phaseOverride else currentPhase

    @JvmStatic
    fun getPhase(): Int = phase()

    @JvmStatic
    fun isInFloor7(): Boolean = inFloor7 || PracticeMode.active

    @JvmStatic
    fun runStarted(): Boolean = phase() >= 0

    @JvmStatic
    fun getCurrentSplits(): List<Split> = currentSplits ?: java.util.List.of()

    @JvmStatic
    fun runJustStarted(): Boolean = phase() == 0

    @JvmStatic
    fun inBoss(): Boolean = phase() > 3

    @JvmStatic
    fun inP1(): Boolean = phase() == 4 && isInFloor7()

    @JvmStatic
    fun inP2(): Boolean = phase() == 5 && isInFloor7()

    @JvmStatic
    fun stormDead(): Boolean = stormDead

    @JvmStatic
    fun inTerminals(): Boolean = phase() == 6 && isInFloor7()

    @JvmStatic
    fun inGoldorTunnel(): Boolean = phase() == 7 && isInFloor7()

    @JvmStatic
    fun inP3(): Boolean = (phase() == 6 || phase() == 7) && isInFloor7()

    @JvmStatic
    fun inP5(): Boolean = phase() == 9 && isInFloor7()

    @JvmStatic
    fun runOver(): Boolean = runOver

    @JvmStatic
    fun getPhaseTime(index: Int): Double {
        val splits = currentSplits ?: return 0.0
        if (index < 0 || index >= splits.size) return 0.0
        return splits[index].getRealTime()
    }

    @ConfigValue @JvmField
    var splitTimer: HUDComponent = HUDComponent(0.0, 0.0, SPLIT_LENGTH, 100, 1f, "Splits",
        { false },
        { hudComponent, drawContext -> renderSplitRows(drawContext, hudComponent.scaledX, hudComponent.scaledY) },
        { enableSplits }
    )

    @JvmStatic
    fun renderHud(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (enableSplits && runStarted()) {
            try {
                renderScaled(ctx, splitTimer) { renderSplitRows(ctx, splitTimer.scaledX, splitTimer.scaledY) }
            } catch (t: Throwable) {
                FishDiag.fail("Phase.16", "render splits HUD", t)
            }
        }
    }

    private fun renderScaled(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, c: HUDComponent, draw: Runnable) {
        val stack = ctx.pose()
        stack.pushMatrix()
        stack.scale(c.scale, c.scale)
        draw.run()
        stack.popMatrix()
    }

    @JvmStatic
    fun renderSplitRows(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, x: Int, y: Int) {
        val textRenderer: Font = Minecraft.getInstance().font
        val splits = currentSplits
        if (splits != null) {
            var splitCount = splits.size
            if (!includeTotalTime) splitCount--
            var height = 0
            for (i in 0 until splitCount) {
                val split = splits[i]
                if (onlyShowActivatedSplits && !(split.started() || split.ended())) continue
                split.drawSplit(ctx, textRenderer, x, y + Constants.TEXT_HEIGHT * height, SPLIT_LENGTH)
                height++
            }
            if (height > 0) {
                ctx.fill(x, y + Constants.TEXT_HEIGHT * height + 3,
                    x + SPLIT_LENGTH, y + Constants.TEXT_HEIGHT * height + 4, 0x44FFFFFF.toInt())
            }
        } else {
            for (i in 0 until DUMMY_SIZE) {
                DUMMY_SPLIT.drawSplit(ctx, textRenderer, x, y + Constants.TEXT_HEIGHT * i, SPLIT_LENGTH)
            }
        }
    }

    @JvmStatic
    fun getVisibleRowCount(): Int {
        val splits = currentSplits ?: return 0
        var splitCount = splits.size
        if (!includeTotalTime) splitCount--
        if (!onlyShowActivatedSplits) return splitCount
        var visible = 0
        for (i in 0 until splitCount) {
            val s = splits[i]
            if (s.started() || s.ended()) visible++
        }
        return visible
    }

}

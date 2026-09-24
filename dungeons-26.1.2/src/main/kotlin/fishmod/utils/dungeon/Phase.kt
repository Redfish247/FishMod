package fishmod.utils.dungeon

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.features.dungeon.PbMessages
import fishmod.utils.Constants
import fishmod.utils.JsonUtility
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.events.interfaces.PhaseEvent
import fishmod.utils.events.interfaces.RunEndEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
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
    private var runOver = false

    @ConfigValue @JvmField var enableSplits: Boolean = false

    @ConfigValue @JvmField var includeTotalTime: Boolean = false

    @ConfigValue @JvmField var sendSplitInChat: Boolean = false

    @ConfigValue @JvmField var onlyShowActivatedSplits: Boolean = false

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (floor == null) detectFloor()
            if (currentSplits == null || runOver) return@register false
            for (split in currentSplits!!) {
                split.tick()
            }
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
        currentSplits?.forEach { it.reset() }
        if (floor!!.contains("7")) inFloor7 = true
    }

    private fun reset() {
        currentSplits = null
        floor = null
        currentPhase = -1
        inFloor7 = false
        stormDead = false
        runOver = false
    }

    @JvmStatic
    fun parseGameMessage(message: Component): Boolean {
        val string = message.string
        val splits = currentSplits ?: return false
        if (runOver) return false

        for (i in splits.indices) {
            val currentSplit = splits[i]
            if (currentSplit.ended()) continue

            currentSplit.parseMessage(string)

            if (currentSplit.ended()) {
                val pb = splitPb(currentSplit)
                if (sendSplitInChat) {
                    val line = currentSplit.createNameText().append(currentSplit.createTimeText())
                    if (pb != null && showSplitPb()) line.append(PbMessages.tag(pb))
                    Misc.addChatMessage(line)
                } else if (pb != null && showSplitPb() && (pb.isPb || !FishSettings.pbMessagesOnlyPb)) {
                    Misc.addChatMessage(currentSplit.createNameText().append(currentSplit.createTimeText()).append(PbMessages.tag(pb)))
                }

                currentPhase = i + 1
                Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange)
            }

            if (currentSplit.started() && currentPhase == -1) {
                currentPhase = i
                Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange)
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

    private fun endRun() {
        runOver = true
        currentPhase = currentSplits?.size ?: 0
        Scheduler.scheduleTask(Runnable { printSplits() }, 2)
        Events.ON_RUN_END.invoke(RunEndEvent::onRunEnd)
    }

    @JvmStatic
    fun getFloor(): String? = floor

    private val FLOOR_ORDER = listOf("E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7")

    // One entry per distinct split name (first floor's split as the default colour), Run Time last.
    @JvmStatic
    fun distinctSplits(): List<Split> {
        val seen = LinkedHashMap<String, Split>()
        for (f in FLOOR_ORDER + FLOOR_SPLITS.keys) FLOOR_SPLITS[f]?.forEach { seen.putIfAbsent(it.name, it) }
        val run = seen.remove("Run Time")
        return seen.values.toList() + listOfNotNull(run)
    }

    // /pbsplits: sum of your best time for each non-overlapping split (no Boss Entry / Run Time).
    @JvmStatic
    fun pbSplitsCommand(arg: String?) {
        val f = arg?.uppercase() ?: floor?.takeIf { FLOOR_SPLITS.containsKey(it) } ?: "M7"
        val splits = FLOOR_SPLITS[f]
        if (splits == null) {
            Misc.addChatMessage(Component.literal("§cNo splits for §f$f§c. Try one of: ${FLOOR_ORDER.filter { FLOOR_SPLITS.containsKey(it) }.joinToString(", ")}"))
            return
        }
        Misc.addChatMessage(Component.literal("§d§l$f PB Splits"))
        var total = 0.0
        var missing = 0
        for (s in splits) {
            if (s.avg < 0) continue
            val pb = seedPb(f, s.name)
            val line = s.createNameText()
            if (pb == null) { missing++; line.append(Component.literal("§7—")) }
            else { total += pb; line.append(Component.literal("§e${PbMessages.fmt(pb)}")) }
            Misc.addChatMessage(line)
        }
        val sum = Component.literal("§aSum of best: §e§l${PbMessages.fmt(total)}")
        if (missing > 0) sum.append(Component.literal(" §7($missing split${if (missing == 1) "" else "s"} with no PB yet)"))
        PbMessages.get("split:$f:Run Time")?.let { sum.append(Component.literal(" §8| §7Run PB §f${PbMessages.fmt(it)}")) }
        Misc.addChatMessage(sum)
    }

    private fun splitPb(split: Split): PbMessages.Result? {
        if (PracticeMode.active) return null
        val f = floor ?: return null
        val t = split.getRealTime()
        val avg = RunHistory.getPersonalAvg(f, split.name)
        seedPb(f, split.name)
        val r = PbMessages.submit("split:$f:${split.name}", t) ?: return null
        split.paceColor = paceColor(r, avg)
        return r
    }

    // Falls back to the best of the last-30 run history so PBs work before the first new record.
    private fun seedPb(f: String, name: String): Double? {
        val key = "split:$f:$name"
        PbMessages.get(key)?.let { return it }
        val hist = RunHistory.getPersonalBest(f, name)
        if (hist <= 0) return null
        PbMessages.submit(key, hist)
        return hist
    }

    // Pink = beat an existing PB, orange = faster than your average.
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
            val line = split.createNameText().append(split.createTimeText())
            if (pb != null && showSplitPb() && (pb.isPb || !FishSettings.pbMessagesOnlyPb)) line.append(PbMessages.tag(pb))
            Misc.addChatMessage(line)
        }
        RunHistory.saveSplits(floor, splits)
        if (splits.isNotEmpty()) {
            val time = splits.last().getTimeDiffrence()
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
            renderScaled(ctx, splitTimer) { renderSplitRows(ctx, splitTimer.scaledX, splitTimer.scaledY) }
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

    @JvmStatic
    fun renderSplitsHud(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, x: Int, y: Int) {
        if (!enableSplits || !runStarted()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        renderSplitRows(ctx, x, y)
    }
}

package fishmod.utils.dungeon

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.Constants
import fishmod.utils.JsonUtility
import fishmod.utils.Misc
import fishmod.utils.Scheduler
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

    private val FLOOR_SPLITS: HashMap<String, ArrayList<Split>> = JsonUtility.readSplits("/data/splits.json")

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

    // Floor key ("F7"/"M7") now comes from the shared fishmod.features.dungeon.map.DungeonState
    // (chat + sidebar based) instead of re-parsing the "The Catacombs (" sidebar/team line here.
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
                if (sendSplitInChat) {
                    Misc.addChatMessage(currentSplit.createNameText().append(currentSplit.createTimeText()))
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

    private fun printSplits() {
        Misc.addChatMessage(Component.literal("§aSplits: "))
        val splits = currentSplits ?: return
        for (split in splits) {
            split.end()
            Misc.addChatMessage(split.createNameText().append(split.createTimeText()))
        }
        RunHistory.saveSplits(floor, splits)
        if (splits.isNotEmpty()) {
            val time = splits.last().getTimeDiffrence()
            val formattedTime = Constants.DECIMAL_FORMAT.format(time)
            val timeLost = Component.literal("§aApproximately §e" + formattedTime + "s §alost to lag.")
            Misc.addChatMessage(timeLost)
        }
    }

    /** The effective phase — a [PracticeMode] override on a practice server, else the real tracker. */
    private fun phase(): Int =
        if (PracticeMode.active && PracticeMode.phaseOverride >= 0) PracticeMode.phaseOverride else currentPhase

    @JvmStatic
    fun getPhase(): Int = phase()

    @JvmStatic
    fun isInFloor7(): Boolean = inFloor7 || PracticeMode.active

    @JvmStatic
    fun runStarted(): Boolean = phase() >= 0

    /** Live splits for the current run (empty when no run is active). Read-only use only. */
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

    // condition forced false: rendered explicitly via renderHud to avoid double-drawing via auto-render
    @ConfigValue @JvmField
    var splitTimer: HUDComponent = HUDComponent(0.0, 0.0, SPLIT_LENGTH, 100, 1f, "Splits",
        { false },
        { hudComponent, drawContext -> renderSplitRows(drawContext, hudComponent.scaledX, hudComponent.scaledY) },
        { enableSplits }
    )

    /** Explicit HUD render for the splits panel (auto-render is disabled above). */
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

    /** Renders split rows + separator. Called by splitTimer HUD (with blade) and renderSplitsHud (standalone). */
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

    /** Returns how many split rows are currently visible (for FishEstTotal snap position). */
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

    /** Direct HUD render for standalone mode (no blade / HUDComponent system). */
    @JvmStatic
    fun renderSplitsHud(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, x: Int, y: Int) {
        if (!enableSplits || !runStarted()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        renderSplitRows(ctx, x, y)
    }
}

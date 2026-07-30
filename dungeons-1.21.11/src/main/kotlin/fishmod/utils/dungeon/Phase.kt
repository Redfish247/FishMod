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
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.text.Text
import java.util.regex.Pattern

object Phase {

    private val END_PATTERN: Pattern = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")
    private val SEARCH_PATTERN: Pattern = Pattern.compile("The Catacombs \\(")

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
        Events.ON_TEAM.register(Phase::detectFloor)
    }

    private fun detectFloor(line: String): Boolean {
        if (floor != null) return false

        val matcher = SEARCH_PATTERN.matcher(line)
        if (!matcher.find()) return false
        val start = line.indexOf("(")
        val end = line.indexOf(")")
        floor = line.substring(start + 1, end)

        currentSplits = FLOOR_SPLITS[floor]
        currentSplits?.forEach { it.reset() }
        if (floor != null) {
            if (floor!!.contains("7")) inFloor7 = true
        }

        return false
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
    fun parseGameMessage(message: Text): Boolean {
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
        Misc.addChatMessage(Text.literal("§aSplits: "))
        val splits = currentSplits ?: return
        for (split in splits) {
            split.end()
            Misc.addChatMessage(split.createNameText().append(split.createTimeText()))
        }
        // Save this run to personal history
        RunHistory.saveSplits(floor, splits)
        if (splits.isNotEmpty()) {
            val time = splits.last().getTimeDiffrence()
            val formattedTime = Constants.DECIMAL_FORMAT.format(time)
            val timeLost = Text.literal("§aApproximately §e" + formattedTime + "s §alost to lag.")
            Misc.addChatMessage(timeLost)
        }
    }

    @JvmStatic
    fun getPhase(): Int = currentPhase

    @JvmStatic
    fun isInFloor7(): Boolean = inFloor7

    @JvmStatic
    fun runStarted(): Boolean = currentPhase >= 0

    /** Live splits for the current run (empty when no run is active). Read-only use only. */
    @JvmStatic
    fun getCurrentSplits(): List<Split> = currentSplits ?: java.util.List.of()

    @JvmStatic
    fun runJustStarted(): Boolean = currentPhase == 0

    @JvmStatic
    fun inBoss(): Boolean = currentPhase > 3

    @JvmStatic
    fun inP1(): Boolean = currentPhase == 4 && inFloor7

    @JvmStatic
    fun inP2(): Boolean = currentPhase == 5 && inFloor7

    @JvmStatic
    fun stormDead(): Boolean = stormDead

    @JvmStatic
    fun inTerminals(): Boolean = currentPhase == 6 && inFloor7

    @JvmStatic
    fun inGoldorTunnel(): Boolean = currentPhase == 7 && inFloor7

    @JvmStatic
    fun inP3(): Boolean = (currentPhase == 6 || currentPhase == 7) && inFloor7

    @JvmStatic
    fun inP5(): Boolean = currentPhase == 9 && inFloor7

    @JvmStatic
    fun runOver(): Boolean = runOver

    @JvmStatic
    fun getPhaseTime(index: Int): Double {
        val splits = currentSplits ?: return 0.0
        if (index < 0 || index >= splits.size) return 0.0
        return splits[index].getRealTime()
    }

    // Splits panel rendered explicitly via Phase.renderHud, so auto-render is forced false to avoid double-drawing.
    @ConfigValue @JvmField
    var splitTimer: HUDComponent = HUDComponent(0.0, 0.0, SPLIT_LENGTH, 100, 1f, "Splits",
        { false },
        { hudComponent, drawContext -> renderSplitRows(drawContext, hudComponent.scaledX, hudComponent.scaledY) },
        { enableSplits }
    )

    /** Explicit HUD render for the splits panel (auto-render is disabled above). */
    @JvmStatic
    fun renderHud(ctx: net.minecraft.client.gui.DrawContext) {
        if (enableSplits && runStarted()) {
            renderScaled(ctx, splitTimer) { renderSplitRows(ctx, splitTimer.scaledX, splitTimer.scaledY) }
        }
    }

    private fun renderScaled(ctx: net.minecraft.client.gui.DrawContext, c: HUDComponent, draw: Runnable) {
        val stack = ctx.matrices
        stack.pushMatrix()
        stack.scale(c.scale, c.scale)
        draw.run()
        stack.popMatrix()
    }

    /** Renders split rows + separator. Called by splitTimer HUD (with blade) and renderSplitsHud (standalone). */
    @JvmStatic
    fun renderSplitRows(ctx: net.minecraft.client.gui.DrawContext, x: Int, y: Int) {
        val textRenderer: TextRenderer = MinecraftClient.getInstance().textRenderer
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
    fun renderSplitsHud(ctx: net.minecraft.client.gui.DrawContext, x: Int, y: Int) {
        if (!enableSplits || !runStarted()) return
        val mc = MinecraftClient.getInstance()
        if (mc.player == null) return
        renderSplitRows(ctx, x, y)
    }
}

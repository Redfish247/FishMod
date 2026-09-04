package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.RunHistory
import fishmod.utils.events.Events
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * FishMod-exclusive Est. Total row for the split timer. Uses its own inner LocalSplit class
 * (never fishmod.utils.dungeon.Split) since blade-addons' Phase/Split load instead of FishMod's
 * when both mods are present, and touching Split would throw NoSuchMethodError on blade's ctor.
 */
object FishEstTotal {

    // LocalSplit — never references fishmod.utils.dungeon.Split

    private class LocalSplit(
        val name: String,
        val startMsg: String,
        val endMsg: String,
        val avg: Double // -1 = cumulative/skip
    ) {
        private var startedFlag = false
        private var endedFlag = false
        var startTime: Long = 0
            private set
        private var endTime: Long = 0

        fun reset() {
            startedFlag = false; endedFlag = false; startTime = 0; endTime = 0
        }

        fun parseMessage(msg: String) {
            if (!startedFlag && msg == startMsg) {
                startTime = System.currentTimeMillis()
                startedFlag = true
            } else if (startedFlag && !endedFlag && msg == endMsg) {
                end()
            }
        }

        fun end() {
            if (endedFlag) return
            endTime = System.currentTimeMillis()
            startedFlag = false
            endedFlag = true
        }

        fun started(): Boolean = startedFlag
        fun ended(): Boolean = endedFlag

        fun getRealTime(): Double {
            // Guard against force-ending a never-started split: (now - 0)/1000 would read as ~55 years.
            if (startTime == 0L) return 0.0
            if (endedFlag) return (endTime - startTime) / 1000.0
            if (startedFlag) return (System.currentTimeMillis() - startTime) / 1000.0
            return 0.0
        }
    }

    private val END_PATTERN: Pattern =
        Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")

    // floor → ordered list of LocalSplits
    private val FLOOR_SPLITS: HashMap<String, ArrayList<LocalSplit>> = loadSplits()

    private var currentSplits: ArrayList<LocalSplit>? = null
    private var floor: String? = null
    private var runOver = false

    // Est. Total / Lag Lost text changes at DECIMAL_FORMAT resolution (0.01s), far less often than every
    // render frame — cache the built Components and measured width, keyed on the text that actually varies.
    private var cachedEstKey: String? = null
    private var cachedEstLabel: Component? = null
    private var cachedEstTime: Component? = null
    private var cachedEstTimeWidth: Int = 0

    private val LAG_LABEL: Component = Component.literal("Lag Lost ").withColor(0xFF888888.toInt())
    private var cachedLagStr: String? = null
    private var cachedLagTime: Component? = null
    private var cachedLagTimeWidth: Int = 0

    private fun estComponents(client: Minecraft, estColor: Int, estTimeStr: String): Triple<Component, Component, Int> {
        val key = "$estColor|$estTimeStr"
        if (key != cachedEstKey) {
            cachedEstKey = key
            val label = Component.literal("Est. Total ").withColor(estColor)
            val time = Component.literal(estTimeStr).withColor(0xFF55FF55.toInt())
            cachedEstLabel = label
            cachedEstTime = time
            cachedEstTimeWidth = client.font.width(time)
        }
        return Triple(cachedEstLabel!!, cachedEstTime!!, cachedEstTimeWidth)
    }

    @JvmField
    @ConfigValue
    var estTotalHud: HUDComponent = HUDComponent(
        0.0, 0.0, Phase.SPLIT_LENGTH, Constants.TEXT_HEIGHT * 2 + 4, 1f, "Est. Total",
        { display() },
        { component, context -> render(component, context) },
        { try { Phase.enableSplits } catch (t: Throwable) { false } }
    )

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register { if (floor == null) detectFloor(); false }
        Events.ON_GAME_MESSAGE.register { message -> parseGameMessage(message) }
        Events.ON_LOCATION_CHANGE.register { _ -> reset(); false }
    }

    // Floor key ("F7"/"M7") now comes from the shared fishmod.features.dungeon.map.DungeonState
    // (chat + sidebar based) instead of re-parsing the "The Catacombs (" sidebar/team line here.
    private fun detectFloor() {
        if (floor != null) return
        val key = fishmod.features.dungeon.map.DungeonState.currentFloorKey() ?: return
        floor = key
        currentSplits = FLOOR_SPLITS[floor]
        currentSplits?.forEach { it.reset() }
    }

    private fun parseGameMessage(message: Component): Boolean {
        val string = message.string
        val splits = currentSplits
        if (splits == null || runOver) return false
        for (s in splits) {
            if (!s.ended()) s.parseMessage(string)
        }
        if (END_PATTERN.matcher(string).find()) endRun()
        return false
    }

    private fun endRun() {
        runOver = true
        val splits = currentSplits ?: return
        // Only finalize splits that actually started.
        for (s in splits) {
            if (s.startTime > 0) s.end()
        }
        val times = LinkedHashMap<String, Double>()
        for (s in splits) {
            if (s.avg < 0) continue
            if (s.ended()) times[s.name] = s.getRealTime()
        }
        RunHistory.saveSplitTimes(floor, times)
    }

    private fun reset() {
        currentSplits = null
        floor = null
        runOver = false
    }

    /** Mirrors Phase.getVisibleRowCount() against our own LocalSplits so it works even under blade-addons' Phase. */
    private fun computeVisibleRowCount(): Int {
        val splits = currentSplits ?: return 0
        var onlyActivated = true
        var includeTotal = false
        try { onlyActivated = Phase.onlyShowActivatedSplits } catch (ignored: Throwable) {}
        try { includeTotal = Phase.includeTotalTime } catch (ignored: Throwable) {}
        var count = splits.size
        if (!includeTotal) count-- // last row is the cumulative "total" split
        if (!onlyActivated) return Math.max(0, count)
        var visible = 0
        for (i in 0 until count) {
            val s = splits[i]
            if (s.started() || s.ended()) visible++
        }
        return visible
    }

    @JvmStatic
    fun display(): Boolean {
        return try {
            Phase.enableSplits && Phase.runStarted() && currentSplits != null
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val splits = currentSplits ?: return
        val client = Minecraft.getInstance()

        // Auto-snap below Phase.splitTimer using our own row count (blade-addons' Phase lacks getVisibleRowCount()).
        val x: Int
        val y: Int
        try {
            x = Phase.splitTimer.scaledX
            val baseY = Phase.splitTimer.scaledY
            y = baseY + Constants.TEXT_HEIGHT * computeVisibleRowCount() + 8
        } catch (t: Throwable) {
            return renderAt(context, client, splits, component.scaledX, component.scaledY)
        }
        renderAt(context, client, splits, x, y)
    }

    private fun renderAt(context: GuiGraphicsExtractor, client: Minecraft, splits: ArrayList<LocalSplit>, x: Int, y: Int) {
        // Base = sum of averages; delta = actual-vs-avg overage so the estimate updates live.
        val splitCount = splits.size - 1
        var base = 0.0
        var delta = 0.0
        var personalCount = 0
        var fallbackCount = 0

        for (i in 0 until splitCount) {
            val s = splits[i]
            if (s.avg < 0) continue
            val personal = RunHistory.getPersonalAvg(floor, s.name)
            val avg = if (personal > 0) personal else s.avg
            if (personal > 0) { base += personal; personalCount++ } else { base += s.avg; fallbackCount++ }
            if (s.ended()) delta += s.getRealTime() - avg
            else if (s.started()) delta += Math.max(0.0, s.getRealTime() - avg)
        }

        // Lag is already reflected in `delta` via wall-clock time — do not subtract it separately.
        val totalSeconds = Math.max(0.0, base + delta)

        val estColor = if (personalCount > 0 && fallbackCount == 0) 0xFF00AACC.toInt()
        else if (personalCount > 0) 0xFFFFAA00.toInt() else 0xFF888888.toInt()

        val mins = if (totalSeconds >= 60) (totalSeconds / 60).toInt().toString() + "m " else ""
        val estTimeStr = mins + Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s"

        val (estLabel, estTime, timeWidth) = estComponents(client, estColor, estTimeStr)
        context.text(client.font, estLabel, x, y, 0xFFFFFFFF.toInt(), true)
        context.text(client.font, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)

        drawLagLine(context, client, x, y + Constants.TEXT_HEIGHT)
    }

    /** Running total of seconds lost to lag this run, drawn on the row beneath Est. Total. */
    private fun drawLagLine(context: GuiGraphicsExtractor, client: Minecraft, x: Int, y: Int) {
        val lag = LagTracker.getCurrentLag()
        val lagStr = Constants.DECIMAL_FORMAT.format(lag) + "s"
        if (lagStr != cachedLagStr) {
            cachedLagStr = lagStr
            val time = Component.literal(lagStr).withColor(0xFFFF5555.toInt())
            cachedLagTime = time
            cachedLagTimeWidth = client.font.width(time)
        }
        context.text(client.font, LAG_LABEL, x, y, 0xFFFFFFFF.toInt(), true)
        context.text(client.font, cachedLagTime!!, x + Phase.SPLIT_LENGTH - cachedLagTimeWidth, y, 0xFFFFFFFF.toInt(), true)
    }

    @JvmStatic
    fun renderStandalone(ctx: GuiGraphicsExtractor, baseX: Int, baseY: Int) {
        if (!display()) return
        val client = Minecraft.getInstance()
        if (client.player == null) return

        val splits = currentSplits ?: return
        val x = baseX
        val y = baseY + Constants.TEXT_HEIGHT * computeVisibleRowCount() + 4

        val splitCount = splits.size - 1
        var base = 0.0
        var delta = 0.0
        var personalCount = 0
        var fallbackCount = 0
        for (i in 0 until splitCount) {
            val s = splits[i]
            if (s.avg < 0) continue
            val personal = RunHistory.getPersonalAvg(floor, s.name)
            val avg = if (personal > 0) personal else s.avg
            if (personal > 0) { base += personal; personalCount++ } else { base += s.avg; fallbackCount++ }
            if (s.ended()) delta += s.getRealTime() - avg
            else if (s.started()) delta += Math.max(0.0, s.getRealTime() - avg)
        }

        // Lag is already reflected in `delta` via wall-clock time — do not subtract it separately.
        val totalSeconds = Math.max(0.0, base + delta)
        val estColor = if (personalCount > 0 && fallbackCount == 0) 0xFF00AACC.toInt()
        else if (personalCount > 0) 0xFFFFAA00.toInt() else 0xFF888888.toInt()
        val estTimeStr = (if (totalSeconds >= 60) (totalSeconds / 60).toInt().toString() + "m " else "") +
            Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s"
        val (estLabel, estTime, timeWidth) = estComponents(client, estColor, estTimeStr)
        ctx.text(client.font, estLabel, x, y, 0xFFFFFFFF.toInt(), true)
        ctx.text(client.font, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)

        drawLagLine(ctx, client, x, y + Constants.TEXT_HEIGHT)
    }

    // splits.json loader — FishMod's own jar via FishEstTotal.class

    private fun loadSplits(): HashMap<String, ArrayList<LocalSplit>> {
        try {
            javaClass.getResourceAsStream("/data/splits.json").use { stream ->
                if (stream == null) return HashMap()
                InputStreamReader(stream).use { reader ->
                    val root: JsonElement = JsonParser.parseReader(reader)
                    return parseSplits(root.asJsonObject)
                }
            }
        } catch (e: Exception) {
            return HashMap()
        }
    }

    private fun parseSplits(obj: JsonObject): HashMap<String, ArrayList<LocalSplit>> {
        val floors = HashMap<String, ArrayList<LocalSplit>>()
        for (entry in obj.entrySet()) {
            val splits = ArrayList<LocalSplit>()
            for (el in entry.value.asJsonArray) {
                val s = el.asJsonObject
                splits.add(
                    LocalSplit(
                        s.get("name").asString,
                        s.get("start").asString,
                        s.get("end").asString,
                        if (s.has("avg")) s.get("avg").asDouble else -1.0
                    )
                )
            }
            floors[entry.key] = splits
        }
        return floors
    }
}

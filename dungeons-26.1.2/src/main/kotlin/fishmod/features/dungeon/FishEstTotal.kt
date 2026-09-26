package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.Misc
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.RunHistory
import fishmod.utils.events.Events
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.io.InputStreamReader
import java.util.regex.Pattern

object FishEstTotal {

    private class LocalSplit(
        val name: String,
        val startMsg: String,
        val endMsg: String,
        val avg: Double
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
            if (startTime == 0L) return 0.0
            if (endedFlag) return (endTime - startTime) / 1000.0
            if (startedFlag) return (System.currentTimeMillis() - startTime) / 1000.0
            return 0.0
        }
    }

    private val END_PATTERN: Pattern =
        Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")

    private val FLOOR_SPLITS: HashMap<String, ArrayList<LocalSplit>> = loadSplits()

    private var currentSplits: ArrayList<LocalSplit>? = null
    private var floor: String? = null
    private var runOver = false

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

    private fun detectFloor() {
        if (floor != null) return
        val key = fishmod.features.dungeon.map.DungeonState.currentFloorKey() ?: return
        floor = key
        currentSplits = FLOOR_SPLITS[floor]
        currentSplits?.forEach { it.reset() }
    }

    private const val RUN_START_FRAGMENT = "I found this map when I first entered the dungeon"

    private fun parseGameMessage(message: Component): Boolean {
        val string = message.string
        if (runOver && string.contains(RUN_START_FRAGMENT)) {
            reset()
            detectFloor()
        }
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
        cachedEstKey = null
        cachedEstLabel = null
        cachedEstTime = null
        cachedEstTimeWidth = 0
        cachedLagStr = null
        cachedLagTime = null
        cachedLagTimeWidth = 0
    }

    private fun computeVisibleRowCount(): Int {
        val splits = currentSplits ?: return 0
        var onlyActivated = true
        var includeTotal = false
        try { onlyActivated = Phase.onlyShowActivatedSplits } catch (ignored: Throwable) {}
        try { includeTotal = Phase.includeTotalTime } catch (ignored: Throwable) {}
        var count = splits.size
        if (!includeTotal) count--
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
    fun printDebugInfo() {
        val splits = currentSplits
        Misc.addChatMessage(Component.literal("§e[EstTotal] floor=$floor runOver=$runOver splits=${splits?.size ?: -1}"))
        if (splits == null) return
        var base = 0.0
        var delta = 0.0
        val splitCount = splits.size - 1
        for (i in 0 until splitCount) {
            val s = splits[i]
            val personal = RunHistory.getPersonalAvg(floor, s.name)
            val avg = if (personal > 0) personal else s.avg
            val skip = s.avg < 0
            var contrib = 0.0
            if (!skip) {
                base += avg
                if (s.ended()) { contrib = s.getRealTime() - avg; delta += contrib }
                else if (s.started()) { contrib = Math.max(0.0, s.getRealTime() - avg); delta += contrib }
            }
            Misc.addChatMessage(Component.literal(
                "§7  ${s.name}: skip=$skip started=${s.started()} ended=${s.ended()} real=${"%.2f".format(s.getRealTime())} " +
                    "jsonAvg=${s.avg} personalAvg=$personal usedAvg=${"%.2f".format(avg)} contrib=${"%.2f".format(contrib)}"
            ))
        }
        Misc.addChatMessage(Component.literal("§e[EstTotal] base=${"%.2f".format(base)} delta=${"%.2f".format(delta)} total=${"%.2f".format(Math.max(0.0, base + delta))}"))
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val splits = currentSplits ?: return
        val client = Minecraft.getInstance()

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

    private fun loadSplits(): HashMap<String, ArrayList<LocalSplit>> {
        try {
            javaClass.getResourceAsStream("/data/fishmod_splits.json").use { stream ->
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

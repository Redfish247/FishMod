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
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import java.io.InputStreamReader
import java.util.regex.Pattern

/** FishMod-exclusive Est. Total row; uses its own LocalSplit (not fishmod.utils.dungeon.Split) to avoid NoSuchMethodError when blade-addons' Phase classloads instead. */
object FishEstTotal {

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

        fun tick() {}

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
            // startTime == 0 means never started; without this guard force-ending it makes getRealTime() ~55 years.
            if (startTime == 0L) return 0.0
            if (endedFlag) return (endTime - startTime) / 1000.0
            if (startedFlag) return (System.currentTimeMillis() - startTime) / 1000.0
            return 0.0
        }
    }

    private val END_PATTERN: Pattern =
        Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")
    private val FLOOR_PATTERN: Pattern = Pattern.compile("The Catacombs \\(")

    private val FLOOR_SPLITS: HashMap<String, ArrayList<LocalSplit>> = loadSplits()

    private var currentSplits: ArrayList<LocalSplit>? = null
    private var floor: String? = null
    private var runOver = false

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
        Events.ON_TEAM.register { line -> detectFloor(line) }
        Events.ON_GAME_MESSAGE.register { message -> parseGameMessage(message) }
        Events.ON_LOCATION_CHANGE.register { _ -> reset(); false }
        Events.ON_SERVER_TICK.register {
            if (currentSplits == null || runOver) return@register false
            for (s in currentSplits!!) s.tick()
            false
        }
    }

    private fun detectFloor(line: String): Boolean {
        if (floor != null) return false
        if (!FLOOR_PATTERN.matcher(line).find()) return false
        val start = line.indexOf("(")
        val end = line.indexOf(")")
        if (start < 0 || end <= start) return false
        floor = line.substring(start + 1, end)
        currentSplits = FLOOR_SPLITS[floor]
        currentSplits?.forEach { it.reset() }
        return false
    }

    private fun parseGameMessage(message: Text): Boolean {
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
        // Only finalise splits that actually started, or getRealTime() blows up to ~55 years.
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

    /** Mirrors Phase.getVisibleRowCount() against our own LocalSplits, so Est. Total still tracks correctly when blade-addons' Phase is rendering. */
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
    fun render(component: HUDComponent, context: DrawContext) {
        val splits = currentSplits ?: return
        val client = MinecraftClient.getInstance()

        // Uses our own LocalSplits' row count since blade-addons' Phase has no getVisibleRowCount().
        val x: Int
        val y: Int
        try {
            x = Phase.splitTimer.scaledX
            val baseY = Phase.splitTimer.scaledY
            y = baseY + Constants.TEXT_HEIGHT * computeVisibleRowCount() + 8
        } catch (t: Throwable) {
            val (rx, ry) = component.scaledX to component.scaledY
            return renderAt(context, client, splits, rx, ry)
        }
        renderAt(context, client, splits, x, y)
    }

    private fun renderAt(context: DrawContext, client: MinecraftClient, splits: ArrayList<LocalSplit>, x: Int, y: Int) {
        // Base = sum of averages; delta = (actual - avg) for ended splits, plus overage of the running one.
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

        // Don't subtract lag from delta — it's wall-clock already, and subtracting would cancel the penalty.
        val totalSeconds = Math.max(0.0, base + delta)

        val estColor = if (personalCount > 0 && fallbackCount == 0) 0xFF00AACC.toInt()
        else if (personalCount > 0) 0xFFFFAA00.toInt() else 0xFF888888.toInt()

        val mins = if (totalSeconds >= 60) (totalSeconds / 60).toInt().toString() + "m " else ""
        val estTimeStr = mins + Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s"

        val estLabel = Text.literal("Est. Total ").withColor(estColor)
        val estTime = Text.literal(estTimeStr).withColor(0xFF55FF55.toInt())

        val timeWidth = client.textRenderer.getWidth(estTime)
        context.drawText(client.textRenderer, estLabel, x, y, 0xFFFFFFFF.toInt(), true)
        context.drawText(client.textRenderer, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)

        drawLagLine(context, client, x, y + Constants.TEXT_HEIGHT)
    }

    /** Running total of seconds lost to lag this run, drawn on the row beneath Est. Total. */
    private fun drawLagLine(context: DrawContext, client: MinecraftClient, x: Int, y: Int) {
        val lag = LagTracker.getCurrentLag()
        val lagLabel = Text.literal("Lag Lost ").withColor(0xFF888888.toInt())
        val lagTime = Text.literal(Constants.DECIMAL_FORMAT.format(lag) + "s").withColor(0xFFFF5555.toInt())
        val timeWidth = client.textRenderer.getWidth(lagTime)
        context.drawText(client.textRenderer, lagLabel, x, y, 0xFFFFFFFF.toInt(), true)
        context.drawText(client.textRenderer, lagTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)
    }

    /** Standalone render — called from HudRenderCallback when blade-addons is absent. */
    @JvmStatic
    fun renderStandalone(ctx: DrawContext, baseX: Int, baseY: Int) {
        if (!display()) return
        val client = MinecraftClient.getInstance()
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

        // Don't subtract lag from delta — it's wall-clock already, and subtracting would cancel the penalty.
        val totalSeconds = Math.max(0.0, base + delta)
        val estColor = if (personalCount > 0 && fallbackCount == 0) 0xFF00AACC.toInt()
        else if (personalCount > 0) 0xFFFFAA00.toInt() else 0xFF888888.toInt()
        val estTimeStr = (if (totalSeconds >= 60) (totalSeconds / 60).toInt().toString() + "m " else "") +
            Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s"
        val estLabel = Text.literal("Est. Total ").withColor(estColor)
        val estTime = Text.literal(estTimeStr).withColor(0xFF55FF55.toInt())
        val timeWidth = client.textRenderer.getWidth(estTime)
        ctx.drawText(client.textRenderer, estLabel, x, y, 0xFFFFFFFF.toInt(), true)
        ctx.drawText(client.textRenderer, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)

        drawLagLine(ctx, client, x, y + Constants.TEXT_HEIGHT)
    }

    private fun loadSplits(): HashMap<String, ArrayList<LocalSplit>> {
        try {
            javaClass.getResourceAsStream("/data/fishmod-splits.json").use { stream ->
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

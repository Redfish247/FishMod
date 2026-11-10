package fishmod.features.dungeon

import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.utils.FishConstants
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.RunHistory
import fishmod.utils.dungeon.RunSplit
import fishmod.utils.events.FishEvents
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

    // Reads the Splits HUD's own splits so both always agree (a private copy missed early splits).
    private val currentSplits: List<RunSplit>? get() = BossPhase.currentSplitList()
    private val floor: String? get() = BossPhase.getFloor()

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
        0.0, 0.0, BossPhase.SPLIT_LENGTH, FishConstants.TEXT_HEIGHT * 2 + 4, 1f, "Est. Total",
        { display() },
        { component, context -> render(component, context) },
        { try { BossPhase.runSplitsEnabled } catch (t: Throwable) { FishDiag.fail("FishEstTotal.1", "BossPhase.enableSplits read failed", t); false } }
    )

    @JvmStatic
    fun init() {
        FishEvents.locationChange.register { _ -> reset(); false }
    }

    private fun reset() {
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
        try { onlyActivated = BossPhase.runSplitsReachedOnly } catch (t: Throwable) { FishDiag.fail("FishEstTotal.6", "BossPhase.onlyShowActivatedSplits read failed", t) }
        try { includeTotal = BossPhase.runSplitsTotal } catch (t: Throwable) { FishDiag.fail("FishEstTotal.7", "BossPhase.includeTotalTime read failed", t) }
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
            BossPhase.runSplitsEnabled && BossPhase.runStarted() && currentSplits != null
        } catch (t: Throwable) {
            FishDiag.fail("FishEstTotal.8", "est total display check failed", t)
            false
        }
    }

    @JvmStatic
    fun printDebugInfo() {
        val splits = currentSplits
        FishUtil.addChatMessage(Component.literal("§e[EstTotal] floor=$floor splits=${splits?.size ?: -1}"))
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
            FishUtil.addChatMessage(Component.literal(
                "§7  ${s.name}: skip=$skip started=${s.started()} ended=${s.ended()} real=${"%.2f".format(s.getRealTime())} " +
                    "jsonAvg=${s.avg} personalAvg=$personal usedAvg=${"%.2f".format(avg)} contrib=${"%.2f".format(contrib)}"
            ))
        }
        FishUtil.addChatMessage(Component.literal("§e[EstTotal] base=${"%.2f".format(base)} delta=${"%.2f".format(delta)} total=${"%.2f".format(Math.max(0.0, base + delta))}"))
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val splits = currentSplits ?: return
        val client = Minecraft.getInstance()

        val x: Int
        val y: Int
        try {
            x = BossPhase.runSplitsBox.scaledX
            val baseY = BossPhase.runSplitsBox.scaledY
            y = baseY + FishConstants.TEXT_HEIGHT * computeVisibleRowCount() + 8
        } catch (t: Throwable) {
            FishDiag.fail("FishEstTotal.9", "split timer position read failed, using own HUD position", t)
            try { renderAt(context, client, splits, component.scaledX, component.scaledY) } catch (e: Exception) { FishDiag.fail("FishEstTotal.10", "est total render failed (fallback pos)", e) }
            return
        }
        // Splits draw inside their own scale, so follow it or the rows overlap at scale != 1.
        val rel = try { BossPhase.runSplitsBox.scale / component.scale } catch (t: Throwable) { 1f }
        val pose = context.pose()
        pose.pushMatrix()
        pose.scale(rel, rel)
        try { renderAt(context, client, splits, x, y) } catch (e: Exception) { FishDiag.fail("FishEstTotal.11", "est total render failed (floor=$floor, ${splits.size} splits)", e) }
        finally { pose.popMatrix() }
    }

    // HUD editor example: the real rows over the Splits example (M7 history/averages, example lag)
    @JvmStatic
    fun example(): HudExample? {
        val splits = BossPhase.exampleSplits()
        if (splits.isEmpty()) return null
        val client = Minecraft.getInstance()
        return HudExample(BossPhase.SPLIT_LENGTH, FishConstants.TEXT_HEIGHT + 9) { ctx -> renderAt(ctx, client, splits, 0, 0) }
    }

    private fun renderAt(context: GuiGraphicsExtractor, client: Minecraft, splits: List<RunSplit>, x: Int, y: Int) {
        val floor = if (HudPreview.active) "M7" else floor
        val splitCount = splits.size - 1
        FishDiag.check(splitCount >= 0, "FishEstTotal.12") { "empty split list for floor $floor" }
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
        val estTimeStr = mins + FishConstants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s"

        val (estLabel, estTime, timeWidth) = estComponents(client, estColor, estTimeStr)
        context.text(client.font, estLabel, x, y, 0xFFFFFFFF.toInt(), true)
        context.text(client.font, estTime, x + BossPhase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF.toInt(), true)

        drawLagLine(context, client, x, y + FishConstants.TEXT_HEIGHT)
    }

    private fun drawLagLine(context: GuiGraphicsExtractor, client: Minecraft, x: Int, y: Int) {
        val lag = if (HudPreview.active) 1.35 else LagTracker.getCurrentLag()
        val lagStr = FishConstants.DECIMAL_FORMAT.format(lag) + "s"
        if (lagStr != cachedLagStr) {
            cachedLagStr = lagStr
            val time = Component.literal(lagStr).withColor(0xFFFF5555.toInt())
            cachedLagTime = time
            cachedLagTimeWidth = client.font.width(time)
        }
        context.text(client.font, LAG_LABEL, x, y, 0xFFFFFFFF.toInt(), true)
        context.text(client.font, cachedLagTime!!, x + BossPhase.SPLIT_LENGTH - cachedLagTimeWidth, y, 0xFFFFFFFF.toInt(), true)
    }
}

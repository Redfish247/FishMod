package fishmod.features

import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.RunHistory
import fishmod.utils.dungeon.Split
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen

/**
 * PB Pace — a racing-style "ghost" for dungeon runs. As each split completes it compares your live
 * time to your personal best for that split and shows a running delta: green when you're ahead of
 * your PB pace, red when you're behind. Pure read-over of the existing split + run-history systems,
 * so it costs nothing until you actually start a run with recorded history.
 */
object PbPaceHud {

    @JvmStatic
    fun isVisible(): Boolean =
        FishSettings.pbPaceEnabled && Phase.runStarted() && Phase.getCurrentSplits().isNotEmpty()

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.pbPaceEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        if (mc.screen != null && mc.screen !is ChatScreen) return
        if (!Phase.runStarted()) return

        val splits: List<Split> = Phase.getCurrentSplits()
        if (splits.isEmpty()) return
        val floor = Phase.getFloor()

        var cumDelta = 0.0
        var anyPb = false
        var last: Split? = null
        var lastDelta = 0.0
        var lastHasPb = false

        for (s in splits) {
            if (!s.ended() || s.avg < 0) continue // skip unfinished + cumulative/total rows
            last = s
            val pb = RunHistory.getPersonalBest(floor, s.name)
            if (pb > 0) {
                val d = s.getRealTime() - pb
                cumDelta += d
                anyPb = true
                lastDelta = d
                lastHasPb = true
            } else {
                lastHasPb = false
            }
        }
        if (last == null) return // no split finished yet — nothing to pace against

        val x = FishSettings.pbPaceHudX
        val y = FishSettings.pbPaceHudY
        val sc = FishSettings.pbPaceScale.toFloat()
        val lh = Constants.TEXT_HEIGHT + 1

        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        var row = 0
        ctx.text(mc.font, "§6§lPB Pace §7(" + (floor?.uppercase() ?: "") + ")", 0, row++ * lh, 0xFFFFFFFF.toInt(), true)
        if (lastHasPb)
            ctx.text(mc.font, "§f" + last.name + " " + signed(lastDelta), 0, row++ * lh, 0xFFFFFFFF.toInt(), true)
        if (anyPb)
            ctx.text(mc.font, "§7vs PB: " + (if (cumDelta <= 0) "§aahead " else "§cbehind ") + signed(cumDelta), 0, row++ * lh, 0xFFFFFFFF.toInt(), true)
        else
            ctx.text(mc.font, "§8building PB history…", 0, row++ * lh, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    /** Format a delta vs PB: green & "-" when faster, red & "+" when slower. */
    private fun signed(d: Double): String {
        val num = Constants.DECIMAL_FORMAT.format(Math.abs(d))
        return if (d <= 0) "§a-" + num + "s" else "§c+" + num + "s"
    }
}

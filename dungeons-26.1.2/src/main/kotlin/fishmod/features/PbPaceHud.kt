package fishmod.features
import fishmod.compat.curScreen

import fishmod.utils.debug.FishDiag
import fishmod.utils.FishConstants
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.RunHistory
import fishmod.utils.dungeon.RunSplit
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen

object PbPaceHud {

    @JvmStatic
    fun isVisible(): Boolean =
        FishSettings.pbPaceEnabled && BossPhase.runStarted() && BossPhase.getCurrentSplits().isNotEmpty()

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.pbPaceEnabled) return
        try {
            renderInner(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("PbPaceHud.1", "PB pace HUD render failed floor=${BossPhase.getFloor()}", t)
        }
    }

    private fun renderInner(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        if (mc.curScreen != null && mc.curScreen !is ChatScreen) return
        if (!BossPhase.runStarted()) return

        val splits: List<RunSplit> = BossPhase.getCurrentSplits()
        if (splits.isEmpty()) return
        val floor = BossPhase.getFloor()

        var cumDelta = 0.0
        var anyPb = false
        var last: RunSplit? = null
        var lastDelta = 0.0
        var lastHasPb = false

        for (s in splits) {
            if (!s.ended() || s.avg < 0) continue
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
        if (last == null) return

        val x = FishSettings.pbPaceHudX
        val y = FishSettings.pbPaceHudY
        val sc = FishSettings.pbPaceScale.toFloat()
        val lh = FishConstants.TEXT_HEIGHT + 1

        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        HudPreview.drawText(ctx, lines(floor, if (lastHasPb) last.name else null, lastDelta, anyPb, cumDelta), lh)
        ctx.pose().popMatrix()
    }

    private fun lines(floor: String?, lastName: String?, lastDelta: Double, anyPb: Boolean, cumDelta: Double): List<String> {
        val out = ArrayList<String>(3)
        out.add("§6§lPB Pace §7(" + (floor?.uppercase() ?: "") + ")")
        if (lastName != null) out.add("§f" + lastName + " " + signed(lastDelta))
        if (anyPb) out.add("§7vs PB: " + (if (cumDelta <= 0) "§aahead " else "§cbehind ") + signed(cumDelta))
        else out.add("§8building PB history…")
        return out
    }

    // Example data: obviously fake floor/split/times
    fun registerPreview() {
        HudPreview.register("PB Pace") { HudPreview.text(lines("m7", "Storm", 1.24, true, 3.87), FishConstants.TEXT_HEIGHT + 1) }
    }

    private fun signed(d: Double): String {
        val num = FishConstants.DECIMAL_FORMAT.format(Math.abs(d))
        return if (d <= 0) "§a-" + num + "s" else "§c+" + num + "s"
    }
}

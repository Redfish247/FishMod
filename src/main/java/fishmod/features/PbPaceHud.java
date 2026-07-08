package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.RunHistory;
import fishmod.utils.dungeon.Split;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * PB Pace — a racing-style "ghost" for dungeon runs. As each split completes it compares your live
 * time to your personal best for that split and shows a running delta: green when you're ahead of
 * your PB pace, red when you're behind. Pure read-over of the existing split + run-history systems,
 * so it costs nothing until you actually start a run with recorded history.
 */
public final class PbPaceHud {

    private PbPaceHud() {}

    public static boolean isVisible() {
        return FishSettings.pbPaceEnabled && Phase.runStarted() && !Phase.getCurrentSplits().isEmpty();
    }

    public static void renderHud(GuiGraphics ctx, DeltaTracker tick) {
        if (!FishSettings.pbPaceEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        if (!Phase.runStarted()) return;

        java.util.List<Split> splits = Phase.getCurrentSplits();
        if (splits.isEmpty()) return;
        String floor = Phase.getFloor();

        double cumDelta = 0;
        boolean anyPb = false;
        Split last = null;
        double lastDelta = 0;
        boolean lastHasPb = false;

        for (Split s : splits) {
            if (!s.ended() || s.getAvg() < 0) continue; // skip unfinished + cumulative/total rows
            last = s;
            double pb = RunHistory.getPersonalBest(floor, s.getName());
            if (pb > 0) {
                double d = s.getRealTime() - pb;
                cumDelta += d;
                anyPb = true;
                lastDelta = d;
                lastHasPb = true;
            } else {
                lastHasPb = false;
            }
        }
        if (last == null) return; // no split finished yet — nothing to pace against

        int x = FishSettings.pbPaceHudX, y = FishSettings.pbPaceHudY;
        float sc = (float) FishSettings.pbPaceScale;
        int lh = Constants.TEXT_HEIGHT + 1;

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        int row = 0;
        ctx.drawString(mc.font, "§6§lPB Pace §7(" + floor.toUpperCase() + ")", 0, row++ * lh, 0xFFFFFFFF, true);
        if (lastHasPb)
            ctx.drawString(mc.font, "§f" + last.getName() + " " + signed(lastDelta), 0, row++ * lh, 0xFFFFFFFF, true);
        if (anyPb)
            ctx.drawString(mc.font, "§7vs PB: " + (cumDelta <= 0 ? "§aahead " : "§cbehind ") + signed(cumDelta), 0, row++ * lh, 0xFFFFFFFF, true);
        else
            ctx.drawString(mc.font, "§8building PB history…", 0, row++ * lh, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    /** Format a delta vs PB: green & "-" when faster, red & "+" when slower. */
    private static String signed(double d) {
        String num = Constants.DECIMAL_FORMAT.format(Math.abs(d));
        return d <= 0 ? "§a-" + num + "s" : "§c+" + num + "s";
    }
}

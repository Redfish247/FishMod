package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.RunHistory;
import fishmod.utils.dungeon.Split;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;

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

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        if (!FishSettings.pbPaceEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;
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

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        int row = 0;
        ctx.drawText(mc.textRenderer, "§6§lPB Pace §7(" + floor.toUpperCase() + ")", 0, row++ * lh, 0xFFFFFFFF, true);
        if (lastHasPb)
            ctx.drawText(mc.textRenderer, "§f" + last.getName() + " " + signed(lastDelta), 0, row++ * lh, 0xFFFFFFFF, true);
        if (anyPb)
            ctx.drawText(mc.textRenderer, "§7vs PB: " + (cumDelta <= 0 ? "§aahead " : "§cbehind ") + signed(cumDelta), 0, row++ * lh, 0xFFFFFFFF, true);
        else
            ctx.drawText(mc.textRenderer, "§8building PB history…", 0, row++ * lh, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }

    /** Format a delta vs PB: green & "-" when faster, red & "+" when slower. */
    private static String signed(double d) {
        String num = Constants.DECIMAL_FORMAT.format(Math.abs(d));
        return d <= 0 ? "§a-" + num + "s" : "§c+" + num + "s";
    }
}

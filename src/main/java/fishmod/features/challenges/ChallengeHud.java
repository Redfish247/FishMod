package fishmod.features.challenges;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD overlay listing your active daily/weekly/monthly challenges and progress. */
public class ChallengeHud {

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        if (!FishSettings.challengesEnabled || !FishSettings.challengeHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;

        ChallengeProgress p = ChallengeProgress.get();
        if (p.active.isEmpty()) return;

        int x = FishSettings.challengeHudX;
        int y = FishSettings.challengeHudY;
        float sc = (float) FishSettings.challengeHudScale;

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);

        int lh = Constants.TEXT_HEIGHT + 2;
        int row = 0;
        ctx.text(mc.font, "§6§lChallenges §7(§a" + p.totalPoints + "§7 pts)" +
                (ChallengeManager.isAfkPaused() ? " §e[AFK]" : ""), 0, row * lh, 0xFFFFFFFF, true);
        row++;
        for (Tier t : Tier.values()) {
            Challenge c = p.active.get(t);
            if (c == null) continue;
            int pct = (int) Math.round(c.progressPct() * 100);
            ctx.text(mc.font, t.color + t.label + "§7: §f" + truncate(c.describe(), 50), 0, row * lh, 0xFFFFFFFF, true);
            row++;
            ctx.text(mc.font, "  §a" + pct + "%§7 — " + remaining(c), 6, row * lh, 0xFFCCCCCC, true);
            row++;
        }

        ctx.pose().popMatrix();
    }

    private static String truncate(String s, int n) { return s.length() > n ? s.substring(0, n - 1) + "…" : s; }

    private static String remaining(Challenge c) {
        long ms = c.expiresAtMs - System.currentTimeMillis();
        if (ms <= 0) return "§cexpired";
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        if (h >= 24) return (h / 24) + "d " + (h % 24) + "h left";
        if (h > 0) return h + "h " + m + "m left";
        return m + "m left";
    }
}

package fishmod.features.challenges;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** HUD overlay listing your active daily/weekly/monthly challenges and progress. */
public class ChallengeHud {

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        if (!FishSettings.challengesEnabled || !FishSettings.challengeHudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;

        ChallengeProgress p = ChallengeProgress.get();
        if (p.active.isEmpty()) return;

        int x = FishSettings.challengeHudX;
        int y = FishSettings.challengeHudY;
        float sc = (float) FishSettings.challengeHudScale;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);

        int lh = Constants.TEXT_HEIGHT + 2;
        int row = 0;
        ctx.drawText(mc.textRenderer, "§6§lChallenges §7(§a" + p.totalPoints + "§7 pts)" +
                (ChallengeManager.isAfkPaused() ? " §e[AFK]" : ""), 0, row * lh, 0xFFFFFFFF, true);
        row++;
        for (Tier t : Tier.values()) {
            Challenge c = p.active.get(t);
            if (c == null) continue;
            int pct = (int) Math.round(c.progressPct() * 100);
            ctx.drawText(mc.textRenderer, t.color + t.label + "§7: §f" + truncate(c.describe(), 50), 0, row * lh, 0xFFFFFFFF, true);
            row++;
            ctx.drawText(mc.textRenderer, "  §a" + pct + "%§7 — " + remaining(c), 6, row * lh, 0xFFCCCCCC, true);
            row++;
        }

        ctx.getMatrices().popMatrix();
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

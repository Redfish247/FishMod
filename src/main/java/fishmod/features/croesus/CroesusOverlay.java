package fishmod.features.croesus;

import fishmod.features.FishHudEditor;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Always-on Croesus stats HUD. Visible in the dungeon hub at all times, and
 * for {@link #POST_CLAIM_MS} after each new claim regardless of location.
 *
 * Three lines:
 *   Most Recent RNG: <highest-priced item from latest claim>
 *   Most Recent Profit: <latest claim value − claim cost>
 *   Total Profit Per Run: <sum of (value − cost) / run count>
 */
public class CroesusOverlay {

    private static final DecimalFormat NUM = new DecimalFormat("#,###");
    private static final long POST_CLAIM_MS = 15_000;

    private static volatile CroesusStore.Entry latest = null;
    private static volatile long lastClaimAt = 0;

    public static void init() {
        FishHudEditor.register("Croesus Drop",
                () -> FishSettings.croesusOverlayX, v -> FishSettings.croesusOverlayX = v,
                () -> FishSettings.croesusOverlayY, v -> FishSettings.croesusOverlayY = v,
                200, 36,
                () -> FishSettings.croesusOverlayScale, v -> FishSettings.croesusOverlayScale = v);
    }

    public static void show(CroesusStore.Entry e) {
        latest = e;
        lastClaimAt = System.currentTimeMillis();
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!FishSettings.croesusOverlayEnabled) return;
        Location loc = Location.getCurrentLocation();
        boolean inDungeonHub = loc == Location.DUNGEON_HUB;
        boolean inDungeon    = Location.inDungeon();
        if (!inDungeonHub && !(inDungeon && !FishSettings.croesusOverlayHideInDungeon)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Pull latest entry from disk lazily if needed (covers restarts where
        // latest is null but the player has past claims to display).
        CroesusStore.Entry e = latest;
        if (e == null) {
            List<CroesusStore.Entry> all = CroesusStore.all();
            if (!all.isEmpty()) e = all.get(all.size() - 1);
        }

        String rng    = "Most Recent RNG: §f"    + (e == null ? "—" : bestDrop(e));
        String recent$ = "Most Recent Profit: §a" + (e == null ? "—" : fmtCoins(profit(e)));
        String avg     = "Total Profit Per Run: §a" + fmtCoins(avgProfit());

        int line = 10;
        float sc = (float) FishSettings.croesusOverlayScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) FishSettings.croesusOverlayX, (float) FishSettings.croesusOverlayY);
        ctx.getMatrices().scale(sc, sc);
        ctx.drawText(mc.textRenderer, "§e" + rng,    0, 0,            0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§e" + recent$, 0, line,         0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§e" + avg,    0, line * 2,     0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }

    /** Keywords that identify a true RNG drop (curated for catacombs chests). */
    private static final String[] RNG_KEYWORDS = {
            "scroll", "master star", "handle", "claymore", "giant's sword",
            "bonzo's staff", "spirit sword", "spirit sceptre", "spirit bow",
            "spirit boots", "spirit mask", "spirit pet", "livid dagger",
            "shadow assassin", "necron", "scarf", "thorn's bow", "reaper mask",
            "last breath", "implosion", "wither shield", "shadow warp",
            "auto recombobulator", "recombobulator", "wither cloak",
            "sadan's brooch", "warden helmet", "axe of the shredded",
            "warped stone", "precursor eye"
    };

    private static boolean isRng(CroesusStore.Item it) {
        if (it.name == null) return false;
        String n = it.name.toLowerCase();
        for (String kw : RNG_KEYWORDS) if (n.contains(kw)) return true;
        return false;
    }

    public static String bestDrop(CroesusStore.Entry e) {
        if (e.items.isEmpty()) return "—";
        // Scan latest entry first; if no RNG, walk back through history.
        String fromLatest = pickRng(e);
        if (fromLatest != null) return fromLatest;
        java.util.List<CroesusStore.Entry> all = CroesusStore.all();
        for (int i = all.size() - 1; i >= 0; i--) {
            String r = pickRng(all.get(i));
            if (r != null) return r;
        }
        return "—";
    }

    private static String pickRng(CroesusStore.Entry e) {
        CroesusStore.Item best = null;
        double bestVal = -1;
        for (CroesusStore.Item it : e.items) {
            if (!isRng(it)) continue;
            double v = Math.max(it.priceAtClaim * it.count, 1);
            if (v > bestVal) { bestVal = v; best = it; }
        }
        if (best == null) return null;
        return (best.count > 1 ? best.count + "x " : "") + best.name;
    }

    private static double profit(CroesusStore.Entry e) {
        double v = 0;
        for (CroesusStore.Item it : e.items) v += it.priceAtClaim * it.count;
        return v - e.claimCost;
    }

    public static double avgProfit() {
        List<CroesusStore.Entry> all = CroesusStore.all();
        if (all.isEmpty()) return 0;
        double sum = 0;
        for (CroesusStore.Entry e : all) sum += profit(e);
        return sum / all.size();
    }

    public static String fmtCoins(double v) {
        if (v == 0) return "0";
        String sign = v < 0 ? "-" : "";
        double a = Math.abs(v);
        if (a >= 1_000_000_000d) return sign + String.format("%.2fB", a / 1_000_000_000d);
        if (a >= 1_000_000d)     return sign + String.format("%.2fM", a / 1_000_000d);
        if (a >= 1_000d)         return sign + String.format("%.1fk", a / 1_000d);
        return sign + NUM.format((long) a);
    }
}

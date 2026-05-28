package fishmod.features.challenges;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Hologram-style stacked leaderboard at the Hub coords [-13.5, 71, -3].
 * Only renders when player is in HUB. Pulls from {@link ChallengeApi#fetchLeaderboard}
 * every 60 seconds and caches the result.
 */
public class LeaderboardRenderer {

    private static final double CENTER_X = -13.5;
    private static final double CENTER_Y = 74;
    private static final double CENTER_Z = -3;
    private static final double RENDER_DIST_SQ = 60 * 60; // only render within 60 blocks
    private static final long   REFRESH_MS = 60_000L;

    private static List<ChallengeApi.LbEntry> cache = new ArrayList<>();
    private static long lastFetchMs = 0;
    private static boolean fetching = false;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(LeaderboardRenderer::render);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx) {
        if (!FishSettings.challengesEnabled) return;
        if (!FishSettings.challengeLeaderboardEnabled) return;
        if (!Location.in(Location.HUB)) return;
        if (ctx.worldState() == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double dx = mc.player.getX() - CENTER_X, dy = mc.player.getY() - CENTER_Y, dz = mc.player.getZ() - CENTER_Z;
        if (dx*dx + dy*dy + dz*dz > RENDER_DIST_SQ) return;

        // Refresh cache asynchronously when stale
        long now = System.currentTimeMillis();
        if (!fetching && now - lastFetchMs > REFRESH_MS) {
            fetching = true;
            lastFetchMs = now;
            ChallengeApi.fetchLeaderboard(10, entries -> {
                cache = entries;
                fetching = false;
            });
        }

        MatrixStack matrices = ctx.matrices();
        if (matrices == null) return;
        Vec3d cam = ctx.worldState().cameraRenderState.pos;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        // Lines (top -> bottom): title, separator, top N entries
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("§6§l✦ FishMod Challenges ✦"));
        lines.add(Text.literal("§8§m                              "));
        if (cache.isEmpty()) {
            lines.add(Text.literal("§7(no scores yet)"));
        } else {
            int rank = 1;
            for (ChallengeApi.LbEntry e : cache) {
                String rk = rank == 1 ? "§e§l#1" : rank == 2 ? "§7§l#2" : rank == 3 ? "§c§l#3" : "§8#" + rank;
                net.minecraft.text.MutableText line = net.minecraft.text.Text.literal(rk + " ").copy();
                line.append(ChallengeApi.renderName(e.name));
                line.append(Text.literal(" §8— §a" + e.totalPoints + " §7pts"));
                lines.add(line);
                rank++;
                if (rank > 10) break;
            }
        }

        double lineHeight = 0.30;
        double baseY = CENTER_Y + (lines.size() - 1) * lineHeight / 2.0;
        for (int i = 0; i < lines.size(); i++) {
            double y = baseY - i * lineHeight;
            RenderUtils.renderText(ctx, matrices, lines.get(i), CENTER_X, y, CENTER_Z, 1.5f);
        }

        matrices.pop();
    }

    /** Manual refresh trigger (used by /fmchallenge refresh). */
    public static void forceRefresh() { lastFetchMs = 0; }
}

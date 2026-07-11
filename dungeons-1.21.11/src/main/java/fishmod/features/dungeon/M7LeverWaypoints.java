package fishmod.features.dungeon;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights the F7/M7 boss levers with a through-walls filled box (Odin-style waypoint) so you can
 * spot them before reaching the lever room. Each box DISAPPEARS the instant its lever is flipped
 * (the {@link LeverBlock#POWERED} state flips), since the renderer reads live block state per frame.
 *
 * Render-only — it only reads block state and draws; it never edits the world or sends packets.
 *
 * The scan is player-centred and retried on a short cadence until the levers are located (their
 * chunks can load after you enter the boss), then it stops. Ported from a community addition and
 * rewritten against the current render stack ({@link RenderingEvents#NO_DEPTH_FILLED}).
 */
public final class M7LeverWaypoints {

    private M7LeverWaypoints() {}

    private static final int H_RADIUS    = 40;     // horizontal scan radius (blocks) around the player
    private static final int V_RADIUS    = 40;     // vertical scan radius (blocks)
    private static final int RETRY_TICKS = 20;     // re-scan cadence while no levers found yet
    private static final double EXPAND   = 0.005;  // tiny inflate so the box doesn't z-fight the lever

    private static final List<BlockPos> levers = new ArrayList<>();
    private static int retry = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(M7LeverWaypoints::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(matrices, vc));
    }

    /** Active only while the feature is on and we're in the F7/M7 boss. */
    private static boolean active() {
        return FishSettings.enableM7LeverWaypoints && Phase.isInFloor7() && Phase.inBoss();
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || !active()) {
            if (!levers.isEmpty()) levers.clear();
            retry = 0;
            return;
        }
        // Re-scan until we locate the levers (their chunks may load after entering the boss); stop once found.
        if (levers.isEmpty()) {
            if (retry <= 0) { scan(mc.world, mc.player.getBlockPos()); retry = RETRY_TICKS; }
            else retry--;
        }
    }

    private static void scan(ClientWorld world, BlockPos center) {
        levers.clear();
        BlockPos.Mutable m = new BlockPos.Mutable();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int x = cx - H_RADIUS; x <= cx + H_RADIUS; x++) {
            for (int z = cz - H_RADIUS; z <= cz + H_RADIUS; z++) {
                for (int y = cy - V_RADIUS; y <= cy + V_RADIUS; y++) {
                    m.set(x, y, z);
                    if (world.getBlockState(m).getBlock() instanceof LeverBlock) {
                        levers.add(m.toImmutable());
                    }
                }
            }
        }
    }

    private static void render(MatrixStack matrices, VertexConsumer vc) {
        if (!active() || levers.isEmpty()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        int c = FishSettings.m7LeverWaypointColor;
        float a = (c >>> 24 & 0xFF) / 255f;
        float r = (c >> 16 & 0xFF) / 255f;
        float g = (c >> 8 & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        float[] rgba = { r, g, b, a };

        for (BlockPos p : levers) {
            BlockState st = world.getBlockState(p);
            if (!(st.getBlock() instanceof LeverBlock)) continue;
            if (st.get(LeverBlock.POWERED)) continue;   // lever flipped → box disappears
            Box box = new Box(
                p.getX() - EXPAND,     p.getY() - EXPAND,     p.getZ() - EXPAND,
                p.getX() + 1 + EXPAND, p.getY() + 1 + EXPAND, p.getZ() + 1 + EXPAND);
            RenderUtils.renderFilled(matrices, vc, box, rgba);
        }
    }
}

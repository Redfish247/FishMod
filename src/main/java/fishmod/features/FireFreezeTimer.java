package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.rendering.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fire Freeze Staff timer — when you use the staff, nearby mobs are frozen for 5s.
 * Renders a countdown floating at each frozen mob's hitbox center.
 */
public class FireFreezeTimer {

    private static final long   WAIT_MS   = 5000L;  // cooldown/wait countdown before freeze
    private static final long   FREEZE_MS = 10000L; // freeze duration (10s)
    private static final long   TOTAL_MS  = WAIT_MS + FREEZE_MS;
    private static final double RADIUS    = 3.0;    // Fire Freeze AOE is small (~2.5-3 blocks)

    // entityId -> wall-clock ms when the staff was used (cast start)
    private static final Map<Integer, Long> frozen = new ConcurrentHashMap<>();

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!FishSettings.fireFreezeTimerEnabled || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack == null || stack.isEmpty()) return ActionResult.PASS;
            if (!"FIRE_FREEZE_STAFF".equals(ItemUtil.getId(stack))) return ActionResult.PASS;

            long start = System.currentTimeMillis();
            Box area = mc.player.getBoundingBox().expand(RADIUS);
            for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
                if (e instanceof LivingEntity && !(e instanceof PlayerEntity) && e.isAlive()
                        && e.squaredDistanceTo(mc.player) <= RADIUS * RADIUS) {
                    frozen.put(e.getId(), start);
                }
            }
            return ActionResult.PASS;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!FishSettings.fireFreezeTimerEnabled || frozen.isEmpty() || ctx.worldState() == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            MatrixStack matrices = ctx.matrices();
            if (matrices == null) return;

            long now = System.currentTimeMillis();
            Vec3d cam = ctx.worldState().cameraRenderState.pos;
            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            Iterator<Map.Entry<Integer, Long>> it = frozen.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> en = it.next();
                long elapsed = now - en.getValue();
                Entity e = mc.world.getEntityById(en.getKey());
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive()) { it.remove(); continue; }

                Text t;
                if (elapsed < WAIT_MS) {
                    // 5s cooldown/wait countdown with an hourglass.
                    double secs = (WAIT_MS - elapsed) / 1000.0;
                    t = Text.literal("§e⌛ " + String.format("%.1fs", secs));
                } else {
                    // 10s freeze countdown with a snowflake.
                    double secs = (TOTAL_MS - elapsed) / 1000.0;
                    String color = secs <= 2.0 ? "§c" : secs <= 5.0 ? "§b" : "§3";
                    t = Text.literal(color + "❄ " + String.format("%.1fs", secs));
                }
                double y = e.getY() + e.getHeight() / 2.0;
                RenderUtils.renderText(ctx, matrices, t, e.getX(), y, e.getZ(), 1.0f);
            }
            matrices.pop();
        });
    }
}

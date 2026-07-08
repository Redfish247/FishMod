package fishmod.features;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.rendering.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
            if (!FishSettings.fireFreezeTimerEnabled || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (stack == null || stack.isEmpty()) return InteractionResult.PASS;
            if (!"FIRE_FREEZE_STAFF".equals(ItemUtil.getId(stack))) return InteractionResult.PASS;

            long start = System.currentTimeMillis();
            AABB area = mc.player.getBoundingBox().inflate(RADIUS);
            for (Entity e : mc.level.getEntities(mc.player, area)) {
                if (e instanceof LivingEntity && !(e instanceof Player) && e.isAlive()
                        && e.distanceToSqr(mc.player) <= RADIUS * RADIUS) {
                    frozen.put(e.getId(), start);
                }
            }
            return InteractionResult.PASS;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!FishSettings.fireFreezeTimerEnabled || frozen.isEmpty() || ctx.worldState() == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            PoseStack matrices = ctx.matrices();
            if (matrices == null) return;

            long now = System.currentTimeMillis();
            Vec3 cam = ctx.worldState().cameraRenderState.pos;
            matrices.pushPose();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            Iterator<Map.Entry<Integer, Long>> it = frozen.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> en = it.next();
                long elapsed = now - en.getValue();
                Entity e = mc.level.getEntity(en.getKey());
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive()) { it.remove(); continue; }

                Component t;
                if (elapsed < WAIT_MS) {
                    // 5s cooldown/wait countdown with an hourglass.
                    double secs = (WAIT_MS - elapsed) / 1000.0;
                    t = Component.literal("§e⌛ " + String.format("%.1fs", secs));
                } else {
                    // 10s freeze countdown with a snowflake.
                    double secs = (TOTAL_MS - elapsed) / 1000.0;
                    String color = secs <= 2.0 ? "§c" : secs <= 5.0 ? "§b" : "§3";
                    t = Component.literal(color + "❄ " + String.format("%.1fs", secs));
                }
                double y = e.getY() + e.getBbHeight() / 2.0;
                RenderUtils.renderText(ctx, matrices, t, e.getX(), y, e.getZ(), 1.0f);
            }
            matrices.popPose();
        });
    }
}

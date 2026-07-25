package fishmod.features.dungeon;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Outlines dungeon mobs whose nametag carries the gold ✯ (Hypixel's marker for a "starred" elite
 * mob that must be killed to clear the floor), so they stand out from regular fodder mobs.
 */
public final class StarredMobHighlight {

    private static final char STAR = '✯'; // ✯

    private StarredMobHighlight() {}

    public static void init() {
        RenderingEvents.OUTLINE_ENTITY.register(StarredMobHighlight::render);
    }

    private static boolean active() {
        return FishSettings.enableStarredMobHighlight && Location.inDungeon();
    }

    private static void render(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer) {
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float[] rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof PlayerEntity) continue;
            if (!living.isAlive() || !isStarred(living)) continue;
            RenderUtils.renderOutline(matrices, consumer, living.getBoundingBox(), rgba);
        }
    }

    private static boolean isStarred(LivingEntity entity) {
        if (!entity.hasCustomName()) return false;
        String name = entity.getCustomName().getString();
        return name.indexOf(STAR) >= 0;
    }
}

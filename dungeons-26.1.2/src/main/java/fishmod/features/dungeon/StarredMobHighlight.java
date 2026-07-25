package fishmod.features.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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

    private static void render(LevelRenderContext context, PoseStack matrices, VertexConsumer consumer) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float[] rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player) continue;
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

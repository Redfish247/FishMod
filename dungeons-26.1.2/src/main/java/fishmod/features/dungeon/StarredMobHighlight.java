package fishmod.features.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * Outlines dungeon mobs marked as "starred" elites. Hypixel doesn't put the ✯ on the mob's own
 * nametag — it's on a separate invisible armor stand riding/hovering above the mob that also
 * shows its health (e.g. "Zombie Knight ✯300,000/300,000❤"). So detection works by scanning
 * armor-stand nametags for the star + heart markers, then picking the nearest non-armor-stand
 * living entity underneath as the actual mob to outline (same approach NoammAddons uses).
 */
public final class StarredMobHighlight {

    private static final String STAR = "✯";
    private static final String HEART = "❤";

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
        ClientLevel level = mc.level;
        if (level == null) return;

        float[] rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor);

        for (Entity mob : findStarredMobs(level)) {
            if (mob instanceof LivingEntity living && living.isAlive()) {
                RenderUtils.renderOutline(matrices, consumer, living.getBoundingBox(), rgba);
            }
        }
    }

    private static Set<Entity> findStarredMobs(ClientLevel level) {
        Set<Entity> mobs = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            String name = stand.getCustomName().getString();
            if (!name.contains(STAR) || !name.contains(HEART)) continue;

            Entity mob = findNearestMob(level, stand);
            if (mob != null) mobs.add(mob);
        }
        return mobs;
    }

    private static Entity findNearestMob(ClientLevel level, ArmorStand stand) {
        AABB searchBox = stand.getBoundingBox().inflate(1.0, 3.0, 1.0);
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity candidate : level.getEntities(stand, searchBox)) {
            if (!isEligibleMob(candidate)) continue;
            double dist = candidate.distanceToSqr(stand);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isEligibleMob(Entity entity) {
        if (entity instanceof ArmorStand) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;
        if (entity instanceof Player player) return !player.isInvisible();
        return true;
    }
}

package fishmod.features.dungeon;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

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

    private static void render(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer) {
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        float[] rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor);

        for (Entity mob : findStarredMobs(world)) {
            if (mob instanceof LivingEntity living && living.isAlive()) {
                RenderUtils.renderOutline(matrices, consumer, living.getBoundingBox(), rgba);
            }
        }
    }

    private static Set<Entity> findStarredMobs(ClientWorld world) {
        Set<Entity> mobs = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity stand) || !stand.hasCustomName()) continue;
            String name = stand.getCustomName().getString();
            if (!name.contains(STAR) || !name.contains(HEART)) continue;

            Entity mob = findNearestMob(world, stand);
            if (mob != null) mobs.add(mob);
        }
        return mobs;
    }

    private static Entity findNearestMob(ClientWorld world, ArmorStandEntity stand) {
        Box searchBox = stand.getBoundingBox().expand(1.0, 3.0, 1.0);
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity candidate : world.getOtherEntities(stand, searchBox)) {
            if (!isEligibleMob(candidate)) continue;
            double dist = candidate.squaredDistanceTo(stand);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isEligibleMob(Entity entity) {
        if (entity instanceof ArmorStandEntity) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;
        if (entity instanceof PlayerEntity player) return !player.isInvisible();
        return true;
    }
}

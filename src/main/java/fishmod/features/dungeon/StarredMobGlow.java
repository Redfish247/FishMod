package fishmod.features.dungeon;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Highlights starred Catacombs mobs with a glowing outline, line-of-sight only. A starred mob is
 * detected by its floating "✯" nametag (a nearby armor stand); the actual mob just below it is the
 * one we glow. Client {@code setGlowing} doesn't stick (isGlowing reads synced data), so the outline
 * is forced via a {@code MinecraftClient.hasOutline} mixin reading {@link #shouldGlow}. Only mobs the
 * player {@code canSee} are flagged, so the outline never shows through walls.
 */
public final class StarredMobGlow {
    private StarredMobGlow() {}

    private static final Set<Integer> glowing = new HashSet<>();

    /** True if this entity is a starred mob currently flagged to glow (read by the hasOutline mixin). */
    public static boolean shouldGlow(Entity e) {
        return e != null && glowing.contains(e.getId());
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            glowing.clear();
            if (!FishSettings.starGlowEnabled || mc.world == null || mc.player == null || !Location.inDungeon()) return;

            // Collect the positions of "✯" nametag armor stands (the star floats above its mob).
            List<Vec3d> stars = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof ArmorStandEntity && e.hasCustomName()) {
                    var n = e.getCustomName();
                    if (n != null && n.getString().contains("✯")) stars.add(e.getPos());
                }
            }
            if (stars.isEmpty()) return;

            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof MobEntity) || e instanceof ArmorStandEntity) continue;
                for (Vec3d s : stars) {
                    double dx = e.getX() - s.x, dz = e.getZ() - s.z;
                    // Nametag sits within ~1.5 blocks horizontally and up to 3 blocks above the mob.
                    if (dx * dx + dz * dz < 2.25 && s.y >= e.getY() && s.y - e.getY() < 3.0) {
                        if (mc.player.canSee(e)) glowing.add(e.getId());
                        break;
                    }
                }
            }
        });
    }
}

package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

/**
 * Cosmetic particle effects rendered around the local player — a trail, an orbiting aura, particle
 * wings, or footsteps — in a chosen particle type. Spawned client-side each tick. This is the local
 * version; syncing the effect to other FishMod users (via {@link fishmod.cosmetic.RemoteSync}) is a
 * planned follow-up, mirroring how nicks/sizes are shared.
 */
public final class ParticleCosmetics {
    private ParticleCosmetics() {}

    public enum Style {
        TRAIL("Trail"), AURA("Aura"), WINGS("Wings"), FOOTSTEPS("Footsteps");
        private final String label;
        Style(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum PType {
        FLAME("Flame", ParticleTypes.FLAME), SOUL("Soul", ParticleTypes.SOUL),
        HEART("Heart", ParticleTypes.HEART), END_ROD("End Rod", ParticleTypes.END_ROD),
        HAPPY("Happy", ParticleTypes.HAPPY_VILLAGER),
        SNOW("Snowflake", ParticleTypes.SNOWFLAKE), GLOW("Glow", ParticleTypes.GLOW),
        CRIT("Crit", ParticleTypes.CRIT), PORTAL("Portal", ParticleTypes.PORTAL);
        private final String label;
        public final ParticleEffect effect;
        PType(String l, ParticleEffect e) { label = l; effect = e; }
        @Override public String toString() { return label; }
    }

    private static double tickAngle = 0;
    private static double lastX, lastZ;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.particlesEnabled || mc.player == null || mc.world == null) return;
            if (mc.isPaused()) return;
            tickAngle += 0.25;
            spawn(mc, mc.player);
        });
    }

    private static void spawn(MinecraftClient mc, ClientPlayerEntity p) {
        ParticleEffect fx = FishSettings.particleType.effect;
        double x = p.getX(), y = p.getY(), z = p.getZ();

        switch (FishSettings.particleStyle) {
            case TRAIL -> {
                // Particles left at the feet, drifting up slightly — fade where you've been.
                mc.world.addParticle(fx, false, false,x + rand(0.25), y + 0.05, z + rand(0.25), 0, 0.01, 0);
            }
            case AURA -> {
                // Two points orbiting at waist height.
                for (int i = 0; i < 2; i++) {
                    double a = tickAngle + i * Math.PI;
                    mc.world.addParticle(fx, false, false,x + Math.cos(a) * 0.7, y + 1.0, z + Math.sin(a) * 0.7, 0, 0, 0);
                }
            }
            case WINGS -> {
                // Two arcs sweeping back from the shoulders, oriented by the player's facing.
                float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                double bx = Math.sin(yaw), bz = -Math.cos(yaw);      // backward
                double sx = Math.cos(yaw), sz = Math.sin(yaw);       // sideways
                for (int i = 1; i <= 3; i++) {
                    double back = 0.15 * i, span = 0.18 * i, up = 1.3 - 0.12 * i;
                    mc.world.addParticle(fx, false, false,x + bx * back + sx * span, y + up, z + bz * back + sz * span, 0, 0, 0);
                    mc.world.addParticle(fx, false, false,x + bx * back - sx * span, y + up, z + bz * back - sz * span, 0, 0, 0);
                }
            }
            case FOOTSTEPS -> {
                double dx = x - lastX, dz = z - lastZ;
                if (dx * dx + dz * dz > 0.0006 && p.isOnGround())
                    mc.world.addParticle(fx, false, false,x + rand(0.15), y + 0.02, z + rand(0.15), 0, 0, 0);
            }
        }
        lastX = x; lastZ = z;
    }

    private static double rand(double spread) {
        return (Math.random() * 2 - 1) * spread;
    }
}

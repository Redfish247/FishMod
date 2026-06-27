package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

/**
 * Cosmetic particle effects rendered around a player — a trail, an orbiting aura, particle wings, or
 * footsteps — in a chosen particle type. {@link #spawnAt} is reused for both the local player (this
 * class) and other FishMod users (see {@link fishmod.cosmetic.RemoteParticles}). Sharing rides the
 * item-cosmetics channel, so it needs Remote item cosmetics enabled.
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

    // Re-upload our particle config when it changes (so others see edits without a relog).
    private static String lastUploaded = null;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.isPaused()) return;
            tickAngle += 0.25;

            // Push config changes to the share channel.
            String sig = (FishSettings.particlesSynced && FishSettings.particlesEnabled)
                    ? FishSettings.particleStyle.name() + ":" + FishSettings.particleType.name() : "";
            if (!sig.equals(lastUploaded)) { lastUploaded = sig; ItemCustomizer.uploadOwn(); }

            if (!FishSettings.particlesEnabled || mc.player == null || mc.world == null) return;
            double x = mc.player.getX(), z = mc.player.getZ();
            boolean moving = (x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ) > 0.0006 && mc.player.isOnGround();
            lastX = x; lastZ = z;
            spawnAt(mc, FishSettings.particleStyle, FishSettings.particleType.effect,
                    x, mc.player.getY(), z, mc.player.getYaw(), moving);
        });
    }

    /** Spawns one tick of the given style's particles around a position. Reused for remote players. */
    public static void spawnAt(MinecraftClient mc, Style style, ParticleEffect fx,
                               double x, double y, double z, float yaw, boolean moving) {
        ParticleManager pm = mc.particleManager;
        switch (style) {
            case TRAIL -> pm.addParticle(fx, x + rand(0.25), y + 0.05, z + rand(0.25), 0, 0.01, 0);
            case AURA -> {
                for (int i = 0; i < 2; i++) {
                    double a = tickAngle + i * Math.PI;
                    pm.addParticle(fx, x + Math.cos(a) * 0.7, y + 1.0, z + Math.sin(a) * 0.7, 0, 0, 0);
                }
            }
            case WINGS -> {
                float r = yaw * MathHelper.RADIANS_PER_DEGREE;
                double bx = Math.sin(r), bz = -Math.cos(r), sx = Math.cos(r), sz = Math.sin(r);
                for (int i = 1; i <= 3; i++) {
                    double back = 0.15 * i, span = 0.18 * i, up = 1.3 - 0.12 * i;
                    pm.addParticle(fx, x + bx * back + sx * span, y + up, z + bz * back + sz * span, 0, 0, 0);
                    pm.addParticle(fx, x + bx * back - sx * span, y + up, z + bz * back - sz * span, 0, 0, 0);
                }
            }
            case FOOTSTEPS -> {
                if (moving) pm.addParticle(fx, x + rand(0.15), y + 0.02, z + rand(0.15), 0, 0, 0);
            }
        }
    }

    private static double rand(double spread) { return (Math.random() * 2 - 1) * spread; }
}

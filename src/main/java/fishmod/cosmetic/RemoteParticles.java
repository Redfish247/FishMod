package fishmod.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.features.ItemCustomizer;
import fishmod.features.ParticleCosmetics;
import fishmod.features.ParticleCosmetics.PType;
import fishmod.features.ParticleCosmetics.Style;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Other FishMod users' particle cosmetics. The config rides the item-customs sync channel as a
 * synthetic {@link ItemCustomizer#PARTICLE_KEY} entry ("STYLE:TYPE"); {@link RemoteSync} feeds us the
 * same per-player item payloads it gives {@link RemoteItems}, and we extract that entry and render the
 * effect at each player's position every tick (reusing {@link ParticleCosmetics#spawnAt}).
 */
public final class RemoteParticles {
    private RemoteParticles() {}

    /** player uuid (no dashes) → {styleName, typeName}. */
    private static final Map<String, String[]> byUuid = new ConcurrentHashMap<>();

    public static void clearAll() { byUuid.clear(); }

    /** Pull each queried player's particle config out of their item-sync payload. */
    public static void accept(Set<String> queried, Map<String, String> itemPayloads) {
        if (!FishSettings.particlesSynced) { byUuid.clear(); return; }
        for (String u : queried) {
            String cfg = extract(itemPayloads.get(u));
            String[] st = cfg == null ? null : cfg.split(":");
            if (st != null && st.length == 2) byUuid.put(u, st);
            else byUuid.remove(u);
        }
    }

    private static String extract(String payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            JsonArray arr = JsonParser.parseString(payload).getAsJsonArray();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("key") && ItemCustomizer.PARTICLE_KEY.equals(o.get("key").getAsString()) && o.has("particle"))
                    return o.get("particle").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.particlesSynced || byUuid.isEmpty()) return;
            if (mc.world == null || mc.player == null || mc.isPaused()) return;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                String[] cfg = byUuid.get(p.getUuid().toString().replace("-", ""));
                if (cfg == null) continue;
                Style style;
                PType type;
                try { style = Style.valueOf(cfg[0]); type = PType.valueOf(cfg[1]); }
                catch (IllegalArgumentException e) { continue; }
                ParticleCosmetics.spawnAt(mc, style, type.effect, p.getX(), p.getY(), p.getZ(), p.getYaw(), true);
            }
        });
    }
}

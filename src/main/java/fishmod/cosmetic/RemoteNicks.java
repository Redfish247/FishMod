package fishmod.cosmetic;

import fishmod.utils.HypixelApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds other players' cosmetic nicks (fetched from the mod proxy) and rewrites their IGN to the
 * styled nick in chat/tab/nametags — the multiplayer counterpart to the local-only {@link NickState}.
 */
public final class RemoteNicks {
    private RemoteNicks() {}

    // IGN → styled nick Text, for players other than the local one.
    private static final Map<String, Text> styledByName = new ConcurrentHashMap<>();
    private static int tick = 0;

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            tick = 0;
            styledByName.clear();
            uploadOwn();      // (re)publish our own nick on join
            refresh();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tick < 20 * 30) return; // every ~30s (keep KV reads well under the daily quota)
            tick = 0;
            refresh();
        });
    }

    /** Publish the local player's current nick (or clear it) to the shared store. */
    public static void uploadOwn() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        java.util.UUID id = mc.getSession().getUuidOrNull();
        if (id == null) return;
        HypixelApi.uploadNick(id.toString().replace("-", ""), NickState.isActive() ? NickState.getRaw() : "");
    }

    private static void refresh() {
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) { styledByName.clear(); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        String self = mc.player.getGameProfile().name();
        Map<String, String> uuidToName = new HashMap<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            var gp = entry.getProfile();
            if (gp == null || gp.id() == null) continue;
            String name = gp.name();
            if (name == null || name.isEmpty() || name.equals(self)) continue; // self handled by NickState
            uuidToName.put(gp.id().toString().replace("-", ""), name);
        }
        if (uuidToName.isEmpty()) return;

        HypixelApi.fetchNicks(uuidToName.keySet(), nicks -> mc.execute(() -> {
            Map<String, Text> next = new ConcurrentHashMap<>();
            for (var e : nicks.entrySet()) {
                String name = uuidToName.get(e.getKey());
                String raw = e.getValue();
                if (name != null && raw != null && !raw.isEmpty()) next.put(name, NickState.parse(raw));
            }
            styledByName.clear();
            styledByName.putAll(next);
        }));
    }

    /** Replace any known remote player's IGN in the text with their styled nick. */
    public static Text apply(Text text) {
        if (text == null || styledByName.isEmpty()
                || !fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return text;
        String s = text.getString();
        Text out = text;
        for (Map.Entry<String, Text> e : styledByName.entrySet()) {
            if (s.contains(e.getKey())) {
                out = NameRewriter.replaceName(out, e.getKey(), e.getValue());
                s = out.getString();
            }
        }
        return out;
    }
}

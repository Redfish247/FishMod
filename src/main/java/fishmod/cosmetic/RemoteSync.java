package fishmod.cosmetic;

import fishmod.utils.HypixelApi;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single combined poller for shared cosmetics (nicks + item customs). Replaces the two separate
 * ~30s refreshes that {@link RemoteNicks} and {@link RemoteItems} used to run with one version-gated
 * {@code /sync} request every ~5s.
 *
 * The worker keeps a global change-version that bumps on any write. We send our last-seen version;
 * when nothing has changed the worker returns just the version (a single cached row read — no table
 * reads, no second request), and we do nothing. Only when the version moved (someone changed a nick
 * or an item) does it return the full nick/item maps for the players we asked about. The upshot:
 * updates land in ~5s instead of ~30s, while Cloudflare/D1 usage drops (one request instead of two,
 * and almost all of them are tiny "nothing changed" replies).
 *
 * We force a full fetch (since = -1) whenever new players appear in the tab list, since a roster
 * change does not bump the version but we still need those newcomers' cosmetics.
 */
public final class RemoteSync {
    private RemoteSync() {}

    private static final int BASE_TICKS = 20 * 5;   // 5s — poll spacing while things are changing
    private static final int MAX_TICKS  = 20 * 10;  // 10s — backed-off spacing when nothing changes
    private static final int STEP_TICKS = 20 * 5;   // grow 5s per idle (unchanged) poll

    private static int tick = 0;
    private static int interval = BASE_TICKS;       // current poll spacing (adaptive)
    private static volatile long version = -1;      // last server version we've applied
    private static Set<String> lastUuids = Set.of();// uuids covered by the last successful sync
    private static int lastTabSize = 0;             // tab-list size at last (re)sync, for cheap growth detection

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> { reset(); refresh(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Cheap O(1) check: if players just appeared in the tab list, snap to fast and poll now —
            // a roster change doesn't move the server version, so we must fetch the newcomers eagerly.
            int size = tabSize();
            if (size != lastTabSize) {
                boolean grew = size > lastTabSize;
                lastTabSize = size;
                if (grew) { interval = BASE_TICKS; tick = interval; } // fire on the next tick
            }
            if (++tick < interval) return;
            tick = 0;
            refresh();
        });
    }

    private static void reset() { tick = 0; interval = BASE_TICKS; version = -1; lastUuids = Set.of(); lastTabSize = 0; }

    private static int tabSize() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
    }

    /** Force an immediate poll that ignores the cached version (used by debug commands / toggles). */
    public static void forceSync() { version = -1; interval = BASE_TICKS; tick = 0; refresh(); }

    private static void refresh() {
        boolean nicksOn = FishSettings.remoteNicksEnabled;
        boolean itemsOn = FishSettings.remoteItemsEnabled;
        boolean sizeOn  = FishSettings.playerSizeShared;
        if (!nicksOn) RemoteNicks.clearAll();
        if (!itemsOn) RemoteItems.clearAll();
        if (!sizeOn)  RemoteScales.clearAll();
        if (!nicksOn && !itemsOn && !sizeOn) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;
        String selfUuid = mc.player.getUUID().toString().replace("-", "");
        Map<String, String> uuidToName = new HashMap<>();
        for (var entry : mc.getConnection().getOnlinePlayers()) {
            var gp = entry.getProfile();
            if (gp == null || gp.id() == null) continue;
            String name = gp.name();
            if (name == null || name.isEmpty()) continue;
            String u = gp.id().toString().replace("-", "");
            if (u.equals(selfUuid)) continue;
            uuidToName.put(u, name);
        }
        if (uuidToName.isEmpty()) return;

        // New players in the tab list won't have moved the version, so force a full fetch for them.
        boolean newPlayers = !lastUuids.containsAll(uuidToName.keySet());
        long since = newPlayers ? -1 : version;
        final Set<String> keys = new HashSet<>(uuidToName.keySet());

        HypixelApi.fetchSync(uuidToName.keySet(), since, (ver, nicks, items, scales) -> mc.execute(() -> {
            version = ver;
            lastUuids = keys;
            lastTabSize = tabSize();
            boolean changed = nicks != null || items != null || scales != null; // server only sends maps when something moved
            // Back off when idle (most of the time), snap back to fast on any change. Keeps updates
            // near-instant during activity while collapsing steady-state requests ~6x.
            interval = changed ? BASE_TICKS : Math.min(interval + STEP_TICKS, MAX_TICKS);
            if (nicks  != null && FishSettings.remoteNicksEnabled) RemoteNicks.acceptNicks(uuidToName, nicks);
            if (items  != null && FishSettings.remoteItemsEnabled) RemoteItems.acceptItems(keys, items);
            if (scales != null && FishSettings.playerSizeShared)   RemoteScales.acceptScales(keys, scales);
        }));
    }
}

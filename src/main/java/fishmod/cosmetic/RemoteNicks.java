package fishmod.cosmetic;

import fishmod.utils.HypixelApi;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds other players' cosmetic nicks (fetched from the mod proxy) and rewrites their IGN to the
 * styled nick in chat/tab/nametags — the multiplayer counterpart to the local-only {@link NickState}.
 *
 * Sources of names:
 *   1. Periodic tab-list scan via {@link RemoteSync} (covers everyone on your current server).
 *   2. Chat-driven discovery — any IGN that appears in a chat line is resolved + fetched, so
 *      DMs and party/guild messages from off-server players also get nick-rewritten.
 */
public final class RemoteNicks {
    private RemoteNicks() {}

    // IGN → styled nick Text, for players other than the local one.
    private static final Map<String, Component> styledByName = new ConcurrentHashMap<>();
    /** name → ms when negative cache (no nick set) expires. Stops re-lookups of plain IGNs. */
    private static final Map<String, Long> negativeCache = new ConcurrentHashMap<>();
    /** names currently being resolved → don't re-fire. */
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private static final long NEGATIVE_TTL_MS = 15 * 60_000L; // 15 min
    private static final Pattern IGN_PAT = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b");

    public static void init() {
        // Polling is driven by RemoteSync (combined version-gated /sync). We only (re)publish our
        // own nick on join here; incoming nicks arrive via acceptNicks().
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> uploadOwn());
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reportSeen());
    }

    /** Publish the local player's current nick (or clear it) to the shared store. */
    public static void uploadOwn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;
        java.util.UUID id = mc.getUser().getProfileId();
        if (id == null) return;
        HypixelApi.uploadNick(id.toString().replace("-", ""), NickState.isActive() ? NickState.getRaw() : "");
    }

    /** Reports this install to the local usage dashboard (uuid + real IGN + mod version). */
    private static void reportSeen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;
        java.util.UUID id = mc.getUser().getProfileId();
        if (id == null) return;
        String name = mc.getUser().getName();
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("fishmod")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        HypixelApi.uploadSeen(id.toString().replace("-", ""), name, version);
    }

    /** Snapshot of the IGN→styled-Text cache. Used by debug commands. */
    public static Map<String, Component> snapshot() { return new HashMap<>(styledByName); }

    /** Force an immediate refresh from the tab list. */
    public static void forceRefresh() { RemoteSync.forceSync(); }

    /** Clear all remotely-sourced nicks (called when the feature is toggled off). */
    public static void clearAll() { styledByName.clear(); }

    /**
     * Apply the result of a {@link RemoteSync} poll. {@code uuidToName} is the full set of on-server
     * players we queried; {@code nicks} holds only those with a nick currently set. Players in
     * {@code uuidToName} but absent from {@code nicks} have no (or a just-cleared) nick, so we drop
     * any stale styled entry for them. Off-server entries discovered via chat are NOT touched.
     */
    public static void acceptNicks(Map<String, String> uuidToName, Map<String, String> nicks) {
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) { styledByName.clear(); return; }
        long now = System.currentTimeMillis();
        boolean newlyResolved = false;
        for (var e : uuidToName.entrySet()) {
            String name = e.getValue();
            String raw = nicks.get(e.getKey());
            if (raw != null && !raw.isEmpty()) {
                Component prev = styledByName.put(name, NickState.parse(ProfanityFilter.censor(raw)));
                negativeCache.remove(name);
                if (prev == null) newlyResolved = true; // a name we showed plainly now has a nick
            } else {
                styledByName.remove(name);
                negativeCache.put(name, now + NEGATIVE_TTL_MS);
            }
        }
        // A newly-known nick should retroactively re-style any messages already in the chat history.
        if (newlyResolved) ChatNickRefresher.requestRefresh();
    }

    /**
     * Scan a chat-rendered string for unknown IGN-like tokens and resolve+fetch nicks for them.
     * Bounded to a few new lookups per call to avoid runaway requests on noisy chat.
     */
    public static void ensureKnownFromChat(String text) {
        if (text == null || text.isEmpty()) return;
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return;
        Matcher m = IGN_PAT.matcher(text);
        long now = System.currentTimeMillis();
        int triggered = 0;
        Set<String> seenThisLine = new java.util.HashSet<>();
        while (m.find() && triggered < 4) {
            String name = m.group(1);
            if (!seenThisLine.add(name)) continue;
            if (!hasLetter(name)) continue; // skip pure-number tokens (coords, stats) — never an IGN we care about
            if (styledByName.containsKey(name)) continue;
            Long neg = negativeCache.get(name);
            if (neg != null && neg > now) continue;
            if (!inFlight.add(name)) continue;
            triggered++;
            lookupAndCache(name);
        }
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    private static void lookupAndCache(String name) {
        String cachedUuid = HypixelApi.getCachedUuid(name);
        if (cachedUuid != null) { fetchOne(name, cachedUuid); return; }
        // Mojang-authoritative resolve (shared with stats lookups) so a recycled/changed name maps to
        // the player who currently owns it — otherwise we'd style the wrong person's nick onto it.
        HypixelApi.resolveUuidAsync(name, uuid -> {
            if (uuid != null) fetchOne(name, uuid);
            else markNotFound(name);
        });
    }

    private static void fetchOne(String name, String uuid) {
        HypixelApi.fetchNicks(java.util.Set.of(uuid), nicks -> {
            inFlight.remove(name);
            String raw = nicks.get(uuid);
            if (raw != null && !raw.isEmpty()) {
                Component prev = styledByName.put(name, NickState.parse(ProfanityFilter.censor(raw)));
                negativeCache.remove(name);
                // The message that triggered this chat lookup is already in history showing the IGN;
                // retroactively re-style it (and any earlier ones) now that the nick is known.
                if (prev == null) ChatNickRefresher.requestRefresh();
            } else {
                negativeCache.put(name, System.currentTimeMillis() + NEGATIVE_TTL_MS);
            }
        });
    }

    private static void markNotFound(String name) {
        inFlight.remove(name);
        negativeCache.put(name, System.currentTimeMillis() + NEGATIVE_TTL_MS);
    }

    /** Replace any known remote player's IGN in the text with their styled nick. */
    public static Component apply(Component text) {
        if (text == null) return text;
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return text;
        // Trigger background lookups for any new IGNs in this line. The current rewrite uses
        // whatever's in styledByName right now; the first message from an unknown player won't
        // be rewritten but subsequent ones will be (typical 200-500ms after the lookup completes).
        ensureKnownFromChat(text.getString());
        if (styledByName.isEmpty()) return text;
        String s = text.getString();
        Component out = text;
        for (Map.Entry<String, Component> e : styledByName.entrySet()) {
            if (s.contains(e.getKey())) {
                out = NameRewriter.replaceName(out, e.getKey(), e.getValue());
                s = out.getString();
            }
        }
        return out;
    }

    /**
     * Like {@link #apply}, but only rewrites already-known nicks — it does NOT trigger new chat-driven
     * lookups. Used by {@link ChatNickRefresher} when re-styling existing chat history, so re-scanning
     * every line doesn't spam name→uuid lookups. Returns the same instance when nothing changed.
     */
    public static Component applyResolvedOnly(Component text) {
        if (text == null) return text;
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled || styledByName.isEmpty()) return text;
        String s = text.getString();
        Component out = text;
        for (Map.Entry<String, Component> e : styledByName.entrySet()) {
            if (s.contains(e.getKey())) {
                out = NameRewriter.replaceName(out, e.getKey(), e.getValue());
                s = out.getString();
            }
        }
        return out;
    }
}

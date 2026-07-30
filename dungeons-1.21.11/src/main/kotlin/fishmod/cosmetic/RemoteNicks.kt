package fishmod.cosmetic

import fishmod.utils.HypixelApi
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Holds other players' cosmetic nicks (fetched from the mod proxy) and rewrites their IGN to the
 * styled nick in chat/tab/nametags — the multiplayer counterpart to the local-only [NickState].
 *
 * Sources of names:
 *   1. Periodic tab-list scan via [RemoteSync] (covers everyone on your current server).
 *   2. Chat-driven discovery — any IGN that appears in a chat line is resolved + fetched, so
 *      DMs and party/guild messages from off-server players also get nick-rewritten.
 */
object RemoteNicks {

    // IGN -> styled nick Text, for players other than the local one.
    private val styledByName: MutableMap<String, Text> = ConcurrentHashMap()

    /** name -> ms when negative cache (no nick set) expires. Stops re-lookups of plain IGNs. */
    private val negativeCache: MutableMap<String, Long> = ConcurrentHashMap()

    /** names currently being resolved -> don't re-fire. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private const val NEGATIVE_TTL_MS = 15 * 60_000L // 15 min
    private val IGN_PAT: Pattern = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b")

    @JvmStatic
    fun init() {
        // Polling is driven by RemoteSync (combined version-gated /sync). We only (re)publish our
        // own nick on join here; incoming nicks arrive via acceptNicks().
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> uploadOwn() }
    }

    /** Publish the local player's current nick (or clear it) to the shared store. */
    @JvmStatic
    fun uploadOwn() {
        val mc = MinecraftClient.getInstance()
        if (mc.session == null) return
        val id: UUID = mc.session.uuidOrNull ?: return
        HypixelApi.uploadNick(id.toString().replace("-", ""), if (NickState.isActive()) NickState.getRaw() else "")
    }

    /** Snapshot of the IGN->styled-Text cache. Used by debug commands. */
    @JvmStatic
    fun snapshot(): Map<String, Text> = HashMap(styledByName)

    /** Force an immediate refresh from the tab list. */
    @JvmStatic
    fun forceRefresh() {
        RemoteSync.forceSync()
    }

    /** Clear all remotely-sourced nicks (called when the feature is toggled off). */
    @JvmStatic
    fun clearAll() {
        styledByName.clear()
    }

    /**
     * Apply the result of a [RemoteSync] poll. `uuidToName` is the full set of on-server
     * players we queried; `nicks` holds only those with a nick currently set. Players in
     * `uuidToName` but absent from `nicks` have no (or a just-cleared) nick, so we drop
     * any stale styled entry for them. Off-server entries discovered via chat are NOT touched.
     */
    @JvmStatic
    fun acceptNicks(uuidToName: Map<String, String>, nicks: Map<String, String>) {
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) {
            styledByName.clear()
            return
        }
        val now = System.currentTimeMillis()
        var newlyResolved = false
        for ((uuid, name) in uuidToName) {
            val raw = nicks[uuid]
            if (raw != null && raw.isNotEmpty()) {
                val prev = styledByName.put(name, NickState.parse(ProfanityFilter.censor(raw)))
                negativeCache.remove(name)
                if (prev == null) newlyResolved = true // a name we showed plainly now has a nick
            } else {
                styledByName.remove(name)
                negativeCache[name] = now + NEGATIVE_TTL_MS
            }
        }
        // A newly-known nick should retroactively re-style any messages already in the chat history.
        if (newlyResolved) ChatNickRefresher.requestRefresh()
    }

    /**
     * Scan a chat-rendered string for unknown IGN-like tokens and resolve+fetch nicks for them.
     * Bounded to a few new lookups per call to avoid runaway requests on noisy chat.
     */
    @JvmStatic
    fun ensureKnownFromChat(text: String?) {
        if (text == null || text.isEmpty()) return
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return
        val m = IGN_PAT.matcher(text)
        val now = System.currentTimeMillis()
        var triggered = 0
        val seenThisLine = HashSet<String>()
        while (m.find() && triggered < 4) {
            val name = m.group(1)
            if (!seenThisLine.add(name)) continue
            if (!hasLetter(name)) continue // skip pure-number tokens (coords, stats) — never an IGN we care about
            if (styledByName.containsKey(name)) continue
            val neg = negativeCache[name]
            if (neg != null && neg > now) continue
            if (!inFlight.add(name)) continue
            triggered++
            lookupAndCache(name)
        }
    }

    private fun hasLetter(s: String): Boolean {
        for (i in s.indices) if (Character.isLetter(s[i])) return true
        return false
    }

    private fun lookupAndCache(name: String) {
        val cachedUuid = HypixelApi.getCachedUuid(name)
        if (cachedUuid != null) {
            fetchOne(name, cachedUuid)
            return
        }
        // Mojang-authoritative resolve (shared with stats lookups) so a recycled/changed name maps to
        // the player who currently owns it — otherwise we'd style the wrong person's nick onto it.
        HypixelApi.resolveUuidAsync(name) { uuid ->
            if (uuid != null) fetchOne(name, uuid) else markNotFound(name)
        }
    }

    private fun fetchOne(name: String, uuid: String) {
        HypixelApi.fetchNicks(setOf(uuid)) { nicks ->
            inFlight.remove(name)
            val raw = nicks[uuid]
            if (raw != null && raw.isNotEmpty()) {
                val prev = styledByName.put(name, NickState.parse(ProfanityFilter.censor(raw)))
                negativeCache.remove(name)
                // The message that triggered this chat lookup is already in history showing the IGN;
                // retroactively re-style it (and any earlier ones) now that the nick is known.
                if (prev == null) ChatNickRefresher.requestRefresh()
            } else {
                negativeCache[name] = System.currentTimeMillis() + NEGATIVE_TTL_MS
            }
        }
    }

    private fun markNotFound(name: String) {
        inFlight.remove(name)
        negativeCache[name] = System.currentTimeMillis() + NEGATIVE_TTL_MS
    }

    /** Replace any known remote player's IGN in the text with their styled nick. */
    @JvmStatic
    fun apply(text: Text?): Text? {
        if (text == null) return text
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return text
        // Trigger background lookups for any new IGNs in this line. The current rewrite uses
        // whatever's in styledByName right now; the first message from an unknown player won't
        // be rewritten but subsequent ones will be (typical 200-500ms after the lookup completes).
        ensureKnownFromChat(text.string)
        if (styledByName.isEmpty()) return text
        var s = text.string
        var out = text
        for ((k, v) in styledByName) {
            if (s.contains(k)) {
                out = NameRewriter.replaceName(out, k, v)
                s = out!!.string
            }
        }
        return out
    }

    /**
     * Like [apply], but only rewrites already-known nicks — it does NOT trigger new chat-driven
     * lookups. Used by [ChatNickRefresher] when re-styling existing chat history, so re-scanning
     * every line doesn't spam name->uuid lookups. Returns the same instance when nothing changed.
     */
    @JvmStatic
    fun applyResolvedOnly(text: Text?): Text? {
        if (text == null) return text
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled || styledByName.isEmpty()) return text
        var s = text.string
        var out = text
        for ((k, v) in styledByName) {
            if (s.contains(k)) {
                out = NameRewriter.replaceName(out, k, v)
                s = out!!.string
            }
        }
        return out
    }
}

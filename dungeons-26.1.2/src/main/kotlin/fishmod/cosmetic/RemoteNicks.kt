package fishmod.cosmetic

import fishmod.utils.HypixelApi
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object RemoteNicks {

    private val styledByName: MutableMap<String, Component> = ConcurrentHashMap()

    private val negativeCache: MutableMap<String, Long> = ConcurrentHashMap()

    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private const val NEGATIVE_TTL_MS = 15 * 60_000L
    private val IGN_PAT: Pattern = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b")

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> uploadOwn() }
    }

    @JvmStatic
    fun uploadOwn() {
        val mc = Minecraft.getInstance()
        if (mc.user == null) return
        val id: UUID = mc.user.profileId ?: return
        HypixelApi.uploadNick(id.toString().replace("-", ""), if (NickState.isActive()) NickState.getRaw() else "")
    }

    @JvmStatic
    fun snapshot(): Map<String, Component> = HashMap(styledByName)

    @JvmStatic
    fun isEmpty(): Boolean = styledByName.isEmpty()

    @JvmStatic
    fun forceRefresh() {
        uploadOwn()
        ChatNickRefresher.requestRefresh()
        RemoteSync.forceSync()
    }

    @JvmStatic
    fun clearAll() {
        styledByName.clear()
    }

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
                if (prev == null) newlyResolved = true
            } else {
                styledByName.remove(name)
                negativeCache[name] = now + NEGATIVE_TTL_MS
            }
        }
        if (newlyResolved) ChatNickRefresher.requestRefresh()
    }

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
            if (!hasLetter(name)) continue
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

    @JvmStatic
    fun apply(text: Component?): Component? {
        if (text == null) return text
        if (!fishmod.utils.config.values.FishSettings.remoteNicksEnabled) return text
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

    @JvmStatic
    fun applyResolvedOnly(text: Component?): Component? {
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

package fishmod.features.rift

import com.google.gson.JsonObject
import fishmod.utils.HypixelApi
import fishmod.utils.events.Events
import fishmod.utils.Location
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap
import fishmod.features.rift.RiftSettings as S

// Rift nametag lines: Vampire slayer level/XP, Enigma Souls, Punchcard mark
object RiftNametag {

    private const val TTL_MS = 30 * 60 * 1000L
    private val VAMP_XP = longArrayOf(20, 75, 240, 840, 2400)
    private val PUNCHED = Regex("^PUNCHCARD! You punched (?:\\[[^]]+] )?(\\w+) and both regained")

    private class Entry { @Volatile var vampXp = -1L; @Volatile var souls = -1; @Volatile var at = 0L; @Volatile var pending = false }
    private val cache = ConcurrentHashMap<String, Entry>()
    private val punched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var lastKick = 0L

    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (Rift.inRift()) PUNCHED.find(Rift.strip(text.string).trim())?.let { punched.add(it.groupValues[1].lowercase()) }
            false
        }
        Events.ON_LOCATION_CHANGE.register { loc -> if (loc != Location.THE_RIFT) punched.clear(); false }
    }

    // Replaces the normal stat lines while in the Rift; null = not handled
    @JvmStatic
    fun override(name: String): List<Component>? {
        if (!Rift.inRift() || (!S.riftNametag && !S.riftPunchcard)) return null
        val out = ArrayList<Component>(2)
        if (S.riftPunchcard && name.lowercase() in punched)
            out.add(Component.literal("✔ Punched").withColor(S.riftPunchcardColor and 0xFFFFFF))
        if (S.riftNametag) stats(name)?.let { out.add(Component.literal(it)) }
        return out
    }

    private fun stats(name: String): String? {
        if (cache.size > 500) cache.clear()
        val e = cache.computeIfAbsent(name.lowercase()) { Entry() }
        val now = System.currentTimeMillis()
        if (!e.pending && (e.at == 0L || now - e.at > TTL_MS) && now - lastKick > 1000L) {
            e.pending = true; lastKick = now
            HypixelApi.getMemberByName(name) { m -> apply(e, m); e.at = if (m == null) System.currentTimeMillis() - TTL_MS + 60_000L else System.currentTimeMillis(); e.pending = false }
        }
        val sb = StringBuilder()
        if (S.riftNametagVampire && e.vampXp >= 0) {
            val lvl = VAMP_XP.count { e.vampXp >= it }
            sb.append("§4Vampire §f").append(lvl).append(" §7(").append(e.vampXp).append(" XP)")
        }
        if (S.riftNametagSouls && e.souls >= 0) {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append("§5Souls §f").append(e.souls)
        }
        return sb.toString().ifEmpty { null }
    }

    private fun apply(e: Entry, m: JsonObject?) {
        if (m == null) return
        try {
            e.vampXp = m.getAsJsonObject("slayer")?.getAsJsonObject("slayer_bosses")?.getAsJsonObject("vampire")?.get("xp")?.asLong ?: 0L
            e.souls = m.getAsJsonObject("rift")?.getAsJsonObject("enigma")?.getAsJsonArray("found_souls")?.size() ?: 0
        } catch (_: Exception) {}
    }
}

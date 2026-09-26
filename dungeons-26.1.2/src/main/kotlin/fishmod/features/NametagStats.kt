package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object NametagStats {

    private const val TTL_MS = 30 * 60 * 1000L
    private const val RETRY_MS = 10 * 60 * 1000L
    private const val MAX_INFLIGHT = 3
    private const val KICK_SPACING_MS = 1000L
    private const val MAX_ENTRIES = 300
    private val VALID_IGN = Regex("^\\w{1,16}$")

    private class Entry {
        @Volatile var networth: Double = Double.NaN
        @Volatile var cataLevel: String? = null
        @Volatile var secretAvg: String? = null
        @Volatile var skillAvg: String? = null
        @Volatile var nwAt: Long = 0
        @Volatile var dungAt: Long = 0
        @Volatile var nwPending = false
        @Volatile var dungPending = false
        @Volatile var version = 0
        var builtVersion = -1
        var builtKey = -1
        var lines: List<Component>? = null
    }

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = AtomicInteger(0)
    @Volatile private var lastKick = 0L

    @JvmStatic
    fun init() {
        // Keep entries across lobby swaps (TTL expires them); just cap size.
        Events.ON_WORLD_CHANGE.register { if (cache.size > MAX_ENTRIES) cache.clear(); false }
    }

    private fun canKick(now: Long): Boolean =
        inFlight.get() < MAX_INFLIGHT && now - lastKick >= KICK_SPACING_MS

    @JvmStatic
    fun linesFor(name: String): List<Component>? {
        if (!FishSettings.nametagStatsEnabled) return null
        if (!Location.inSkyblock()) return null
        if (!VALID_IGN.matches(name)) return null

        val hub = Location.inDungeonHub()
        val e = cache.computeIfAbsent(name.lowercase()) { Entry() }
        val now = System.currentTimeMillis()
        val showNw = FishSettings.nametagStatsShowNetworth
        val showSkill = FishSettings.nametagStatsShowSkillAvg
        val showCata = hub && FishSettings.nametagStatsShowCataLevel
        val showSecrets = hub && FishSettings.nametagStatsShowSecretAvg

        val nwStale = showNw && (e.nwAt == 0L ||
            now - e.nwAt > (if (e.networth.isNaN() || e.networth < 0) RETRY_MS else TTL_MS))
        if (nwStale && !e.nwPending && canKick(now)) {
            e.nwPending = true
            lastKick = now
            inFlight.incrementAndGet()
            HypixelApi.getNetworth(Minecraft.getInstance(), name) { nw, _ ->
                e.networth = nw
                e.nwAt = System.currentTimeMillis()
                e.nwPending = false
                e.version++
                inFlight.decrementAndGet()
            }
        }

        val needDung = showSkill || showCata || showSecrets
        val dungStale = needDung && (e.dungAt == 0L || now - e.dungAt > TTL_MS)
        if (dungStale && !e.dungPending && canKick(now)) {
            e.dungPending = true
            lastKick = now
            inFlight.incrementAndGet()
            HypixelApi.getByNameSilent(name) { d ->
                if (d.failed) {
                    e.dungPending = false
                    inFlight.decrementAndGet()
                    return@getByNameSilent
                }
                e.cataLevel = if (d.cataXp > 0) HypixelApi.formatLevel(d.cataXp) else null
                e.secretAvg = d.secretAverage
                e.skillAvg = d.skillAverage
                e.dungAt = System.currentTimeMillis()
                e.dungPending = false
                e.version++
                inFlight.decrementAndGet()
            }
        }

        val key = (if (showNw) 1 else 0) or (if (showSkill) 2 else 0) or (if (showCata) 4 else 0) or (if (showSecrets) 8 else 0)
        val version = e.version
        if (e.builtVersion == version && e.builtKey == key) return e.lines
        val out = ArrayList<Component>(2)

        val nw = if (showNw && !e.networth.isNaN() && e.networth >= 0) e.networth else null
        val skill = if (showSkill) e.skillAvg else null
        if (nw != null || skill != null) {
            val sb = StringBuilder()
            nw?.let { sb.append("§6NW §e").append(abbrev(it)) }
            skill?.let {
                if (sb.isNotEmpty()) sb.append("  ")
                sb.append("§aSkill Avg §f").append(it)
            }
            out.add(Component.literal(sb.toString()))
        }

        run {
            val cata = if (showCata) e.cataLevel else null
            val secrets = if (showSecrets) e.secretAvg else null
            if (cata != null || secrets != null) {
                val sb = StringBuilder()
                cata?.let { sb.append("§bCata §f").append(it) }
                secrets?.let {
                    if (sb.isNotEmpty()) sb.append("  ")
                    sb.append("§7Secrets §f").append(it)
                }
                out.add(Component.literal(sb.toString()))
            }
        }
        val lines = out.ifEmpty { null }
        e.lines = lines
        e.builtVersion = version
        e.builtKey = key
        return lines
    }

    private fun abbrev(v: Double): String = when {
        v >= 1_000_000_000_000.0 -> String.format("%.2fT", v / 1_000_000_000_000.0)
        v >= 1_000_000_000.0 -> String.format("%.2fB", v / 1_000_000_000.0)
        v >= 1_000_000.0 -> String.format("%.1fM", v / 1_000_000.0)
        v >= 1_000.0 -> String.format("%.0fk", v / 1_000.0)
        else -> String.format("%.0f", v)
    }
}

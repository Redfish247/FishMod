package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Draws each player's networth under their nametag, and — while in the Dungeon Hub — their catacombs
 * level and secret average too. Values come from [HypixelApi] (proxy, no key) and are cached per IGN;
 * lookups are kicked lazily from the nametag render path and rate-limited so a crowded hub doesn't
 * hammer the proxy. The actual drawing is done in EntityRendererMixin off the lines returned here.
 */
object NametagStats {

    private const val TTL_MS = 10 * 60 * 1000L
    private const val RETRY_MS = 60 * 1000L
    private const val MAX_INFLIGHT = 3
    private const val KICK_SPACING_MS = 200L
    private val VALID_IGN = Regex("^\\w{1,16}$")

    private class Entry {
        @Volatile var networth: Double = Double.NaN   // NaN = not fetched, <0 = failed
        @Volatile var cataLevel: String? = null
        @Volatile var secretAvg: String? = null
        @Volatile var nwAt: Long = 0
        @Volatile var dungAt: Long = 0
        @Volatile var nwPending = false
        @Volatile var dungPending = false
    }

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = AtomicInteger(0)
    @Volatile private var lastKick = 0L

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { cache.clear(); false }
    }

    private fun canKick(now: Long): Boolean =
        inFlight.get() < MAX_INFLIGHT && now - lastKick >= KICK_SPACING_MS

    /** Lines to draw under [name]'s nametag, or null when nothing is ready. Also kicks async fetches. */
    @JvmStatic
    fun linesFor(name: String): List<Component>? {
        if (!FishSettings.nametagStatsEnabled) return null
        if (!Location.inSkyblock()) return null
        if (!VALID_IGN.matches(name)) return null

        val hub = Location.inDungeonHub()
        val e = cache.computeIfAbsent(name.lowercase()) { Entry() }
        val now = System.currentTimeMillis()

        val nwStale = e.nwAt == 0L ||
            now - e.nwAt > (if (e.networth.isNaN() || e.networth < 0) RETRY_MS else TTL_MS)
        if (nwStale && !e.nwPending && canKick(now)) {
            e.nwPending = true
            lastKick = now
            inFlight.incrementAndGet()
            HypixelApi.getNetworth(Minecraft.getInstance(), name) { nw, _ ->
                e.networth = nw
                e.nwAt = System.currentTimeMillis()
                e.nwPending = false
                inFlight.decrementAndGet()
            }
        }

        if (hub) {
            val dungStale = e.dungAt == 0L || now - e.dungAt > TTL_MS
            if (dungStale && !e.dungPending && canKick(now)) {
                e.dungPending = true
                lastKick = now
                inFlight.incrementAndGet()
                HypixelApi.getByNameSilent(name) { d ->
                    e.cataLevel = if (d.cataXp > 0) HypixelApi.formatLevel(d.cataXp) else null
                    e.secretAvg = d.secretAverage
                    e.dungAt = System.currentTimeMillis()
                    e.dungPending = false
                    inFlight.decrementAndGet()
                }
            }
        }

        val out = ArrayList<Component>(2)
        if (!e.networth.isNaN() && e.networth >= 0)
            out.add(Component.literal("§6NW §e" + abbrev(e.networth)))
        if (hub && (e.cataLevel != null || e.secretAvg != null)) {
            val sb = StringBuilder()
            e.cataLevel?.let { sb.append("§bCata §f").append(it) }
            e.secretAvg?.let {
                if (sb.isNotEmpty()) sb.append("  ")
                sb.append("§7Secrets §f").append(it)
            }
            out.add(Component.literal(sb.toString()))
        }
        return out.ifEmpty { null }
    }

    private fun abbrev(v: Double): String = when {
        v >= 1_000_000_000_000.0 -> String.format("%.2fT", v / 1_000_000_000_000.0)
        v >= 1_000_000_000.0 -> String.format("%.2fB", v / 1_000_000_000.0)
        v >= 1_000_000.0 -> String.format("%.1fM", v / 1_000_000.0)
        v >= 1_000.0 -> String.format("%.0fk", v / 1_000.0)
        else -> String.format("%.0f", v)
    }
}

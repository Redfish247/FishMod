package fishmod.features.dungeon

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// All-time bests (seconds, lower is better) for splits / Goldor sections / terminals / relics.
object PbMessages {

    private val FILE: Path = Paths.get("config/fishmod/personal_bests.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "FishMod-PB-IO").apply { isDaemon = true }
    }

    private val pbs = HashMap<String, Double>()
    private var loaded = false

    class Result(val seconds: Double, val previous: Double?, val isPb: Boolean)

    @JvmStatic
    fun submit(key: String, seconds: Double): Result? {
        if (seconds <= 0.0 || seconds > 3600.0) return null
        synchronized(pbs) {
            ensureLoaded()
            val prev = pbs[key]
            val isPb = prev == null || seconds < prev
            if (isPb) { pbs[key] = seconds; save() }
            return Result(seconds, prev, isPb)
        }
    }

    @JvmStatic
    fun get(key: String): Double? = synchronized(pbs) { ensureLoaded(); pbs[key] }

    // Marker that stops Phase from re-seeding split PBs out of run history after a reset.
    @JvmStatic
    fun noSeedKey(floor: String): String = "meta:noseed:$floor"

    /** Clears split PBs for one floor (or all when null); returns how many were removed. */
    @JvmStatic
    fun resetSplits(floor: String?, floors: Collection<String>): Int = synchronized(pbs) {
        ensureLoaded()
        val prefix = if (floor == null) "split:" else "split:$floor:"
        val n = pbs.keys.count { it.startsWith(prefix) }
        pbs.keys.removeIf { it.startsWith(prefix) }
        for (f in if (floor == null) floors else listOf(floor)) pbs[noSeedKey(f)] = 1.0
        save()
        n
    }

    // " (PB!)" / " (+1.23s)" suffix with the old best on hover.
    @JvmStatic
    fun tag(r: Result): MutableComponent {
        val prev = r.previous
        val hover = if (prev == null) "§7No previous best" else "§7Previous best: §a${fmt(prev)}"
        val text = when {
            r.isPb && prev == null -> " §d§l(PB!)"
            r.isPb -> " §d§l(PB!) §8(§a-${fmt(prev!! - r.seconds)}§8)"
            else -> " §8(§c+${fmt(r.seconds - prev!!)} §8| PB §7${fmt(prev)}§8)"
        }
        return Component.literal(text).withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal(hover))) }
    }

    // Records the time and prints "<label> <time> (PB!)" unless it's a non-PB and only-PB is on.
    @JvmStatic
    fun announce(enabled: Boolean, key: String, label: Component, seconds: Double): Result? {
        val r = submit(key, seconds) ?: return null
        if (!FishSettings.pbMessagesEnabled || !enabled) return r
        if (!r.isPb && FishSettings.pbMessagesOnlyPb) return r
        Misc.addChatMessage(Component.empty().append(label).append(Component.literal(" §e${fmt(seconds)}")).append(tag(r)))
        return r
    }

    @JvmStatic
    fun fmt(s: Double): String =
        if (s >= 60.0) "%dm %.2fs".format((s / 60).toInt(), s % 60) else "%.2fs".format(s)

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (Files.exists(FILE)) {
                val t = object : TypeToken<HashMap<String, Double>>() {}.type
                GSON.fromJson<HashMap<String, Double>>(Files.readString(FILE), t)?.let { pbs.putAll(it) }
            }
        } catch (e: Exception) {
            Debug.LOGGER.warn("[PbMessages] load failed: {}", e.toString())
        }
    }

    private fun save() {
        val json = GSON.toJson(pbs)
        ioExecutor.execute {
            try {
                Files.createDirectories(FILE.parent)
                Files.writeString(FILE, json)
            } catch (e: Exception) {
                Debug.LOGGER.warn("[PbMessages] save failed: {}", e.toString())
            }
        }
    }
}

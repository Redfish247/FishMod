package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type
import java.util.concurrent.Executors

/**
 * Persistent per-(boss, tier) personal-best kill times, in seconds.
 *
 * Stored in `config/fishmod/slayer_pbs.json` as `"<TYPE>|<tier>" -> seconds` — the same on-disk +
 * off-thread-write pattern as [fishmod.utils.dungeon.RunHistory], so a PB set on a boss kill (which
 * happens on the tick/network thread) never stutters the game. Survives MC/mod restarts and world
 * changes because it's a plain file keyed only by boss identity — never combined across bosses.
 */
object SlayerPersonalBests {

    private const val FILE_PATH = "config/fishmod/slayer_pbs.json"
    private const val MAX_SECONDS = 1800.0 // reject absurd times from a mis-fired timer
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fishmod-slayer-pb-io").apply { isDaemon = true }
    }
    private val lock = Any()

    // "REVENANT|4" -> best seconds
    private var data: MutableMap<String, Double> = HashMap()

    init { load() }

    private fun key(type: SlayerType, tier: Int) = "${type.name}|$tier"

    /** Best time for this exact boss+tier, or -1 if none recorded. */
    @JvmStatic
    fun get(type: SlayerType, tier: Int): Double = synchronized(lock) {
        data[key(type, tier)] ?: -1.0
    }

    /** Best time across every tier of this boss, or -1 if none recorded. */
    @JvmStatic
    fun bestForType(type: SlayerType): Double = synchronized(lock) {
        data.entries.asSequence()
            .filter { it.key.substringBefore('|') == type.name }
            .map { it.value }
            .minOrNull() ?: -1.0
    }

    /**
     * Records [seconds] for this boss+tier. Returns true when it beat (or first-set) the PB.
     * A slower time is ignored.
     */
    @JvmStatic
    fun record(type: SlayerType, tier: Int, seconds: Double): Boolean {
        if (seconds <= 0.0 || seconds > MAX_SECONDS) return false
        synchronized(lock) {
            val k = key(type, tier)
            val prev = data[k]
            if (prev != null && prev <= seconds) return false
            data[k] = seconds
            save()
            return true
        }
    }

    private fun load() {
        synchronized(lock) {
            val file = File(FILE_PATH)
            if (!file.exists()) return
            try {
                FileReader(file).use { reader ->
                    val type: Type = object : TypeToken<MutableMap<String, Double>>() {}.type
                    GSON.fromJson<MutableMap<String, Double>?>(reader, type)?.let { data = it }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        val json = synchronized(lock) { GSON.toJson(data) }
        writeExecutor.execute {
            try {
                val file = File(FILE_PATH)
                file.parentFile?.mkdirs()
                FileWriter(file).use { it.write(json) }
            } catch (_: Exception) {
            }
        }
    }
}

package fishmod.utils.dungeon

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type

/** Tracks personal split times across runs for the EST display; stored in config/fishmod-runs.json. */
object RunHistory {

    private const val MAX_RUNS = 30

    // Any single split taking more than this is an artifact of a never-started split
    // being force-ended (startTime==0 → huge wall time). Reject on save; filter on read.
    private const val MAX_SPLIT_SECONDS = 3600.0
    private const val FILE_PATH = "config/fishmod-runs.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // floor → split name → list of real times (seconds), newest last
    private var data: MutableMap<String, MutableMap<String, MutableList<Double>>> = HashMap()

    init {
        load()
    }

    /** Saves raw split times without depending on Split (avoids a classloader conflict with blade's Split). */
    @JvmStatic
    fun saveSplitTimes(floor: String?, times: Map<String, Double>?) {
        if (floor == null || times == null || times.isEmpty()) return
        val floorData = data.getOrPut(floor) { HashMap() }
        var anyRecorded = false
        for ((key, t) in times) {
            if (t <= 0 || t > MAX_SPLIT_SECONDS) continue  // reject corrupt/impossible values
            val list = floorData.getOrPut(key) { ArrayList() }
            list.add(t)
            if (list.size > MAX_RUNS) list.removeAt(0)
            anyRecorded = true
        }
        if (anyRecorded) save()
    }

    /** Call when a run completes; saves every ended split's real time. */
    @JvmStatic
    fun saveSplits(floor: String?, splits: List<Split>?) {
        if (floor == null || splits == null || splits.isEmpty()) return

        val floorData = data.getOrPut(floor) { HashMap() }
        var anyRecorded = false

        for (split in splits) {
            if (!split.ended()) continue
            if (split.avg < 0) continue  // skip cumulative/total splits
            val t = split.getRealTime()
            if (t <= 0 || t > MAX_SPLIT_SECONDS) continue  // reject corrupt/impossible values

            val times = floorData.getOrPut(split.name) { ArrayList() }
            times.add(t)
            if (times.size > MAX_RUNS) times.removeAt(0)
            anyRecorded = true
        }

        if (anyRecorded) save()
    }

    /** Returns the personal average for a split, or -1 if no data yet. */
    @JvmStatic
    fun getPersonalAvg(floor: String?, splitName: String?): Double {
        if (floor == null || splitName == null) return -1.0
        val floorData = data[floor] ?: return -1.0
        val times = floorData[splitName]
        if (times == null || times.isEmpty()) return -1.0
        return times.asSequence()
            .filter { it > 0 && it <= MAX_SPLIT_SECONDS }
            .average()
            .let { if (it.isNaN()) -1.0 else it }
    }

    /** Returns the personal best (fastest) recorded time for a split, or -1 if no data yet. */
    @JvmStatic
    fun getPersonalBest(floor: String?, splitName: String?): Double {
        if (floor == null || splitName == null) return -1.0
        val floorData = data[floor] ?: return -1.0
        val times = floorData[splitName]
        if (times == null || times.isEmpty()) return -1.0
        return times.asSequence()
            .filter { it > 0 && it <= MAX_SPLIT_SECONDS }
            .minOrNull() ?: -1.0
    }

    /** How many recorded runs exist for a given split. */
    @JvmStatic
    fun runCount(floor: String?, splitName: String?): Int {
        val floorData = data[floor] ?: return 0
        val times = floorData[splitName]
        return times?.size ?: 0
    }

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            FileReader(file).use { reader ->
                val type: Type = object : TypeToken<MutableMap<String, MutableMap<String, MutableList<Double>>>>() {}.type
                val loaded: MutableMap<String, MutableMap<String, MutableList<Double>>>? = GSON.fromJson(reader, type)
                if (loaded != null) data = loaded
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile.mkdirs()
            FileWriter(file).use { writer ->
                GSON.toJson(data, writer)
            }
        } catch (_: Exception) {
        }
    }
}

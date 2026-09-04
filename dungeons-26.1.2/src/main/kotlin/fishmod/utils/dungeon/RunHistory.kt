package fishmod.utils.dungeon

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type
import java.util.concurrent.Executors

/** Stored in config/fishmod-runs.json as floor -> split name -> list of times. */
object RunHistory {

    private const val MAX_RUNS = 30

    // splits longer than this come from a never-started split being force-ended; reject/filter them
    private const val MAX_SPLIT_SECONDS = 3600.0
    private const val FILE_PATH = "config/fishmod-runs.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // Disk writes run here so run-end (☠ Defeated) doesn't stutter on the tick/network thread.
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fishmod-runhistory-io").apply { isDaemon = true }
    }

    // reads on the render thread, writes on the dungeon-tick/network thread; guards [data] against CME
    private val lock = Any()

    // floor → split name → list of real times (seconds), newest last
    private var data: MutableMap<String, MutableMap<String, MutableList<Double>>> = HashMap()

    init {
        load()
    }

    /** Saves raw times without depending on Split, to avoid classloader conflicts with FishEstTotal. */
    @JvmStatic
    fun saveSplitTimes(floor: String?, times: Map<String, Double>?) {
        if (floor == null || times == null || times.isEmpty()) return
        synchronized(lock) {
            val floorData = data.getOrPut(floor) { HashMap() }
            var anyRecorded = false
            for ((key, t) in times) {
                if (t <= 0 || t > MAX_SPLIT_SECONDS) continue
                val list = floorData.getOrPut(key) { ArrayList() }
                list.add(t)
                if (list.size > MAX_RUNS) list.removeAt(0)
                anyRecorded = true
            }
            if (anyRecorded) save()
        }
    }

    @JvmStatic
    fun saveSplits(floor: String?, splits: List<Split>?) {
        if (floor == null || splits == null || splits.isEmpty()) return

        synchronized(lock) {
            val floorData = data.getOrPut(floor) { HashMap() }
            var anyRecorded = false

            for (split in splits) {
                if (!split.ended()) continue
                if (split.avg < 0) continue  // skip cumulative/total splits
                val t = split.getRealTime()
                if (t <= 0 || t > MAX_SPLIT_SECONDS) continue

                val times = floorData.getOrPut(split.name) { ArrayList() }
                times.add(t)
                if (times.size > MAX_RUNS) times.removeAt(0)
                anyRecorded = true
            }

            if (anyRecorded) save()
        }
    }

    @JvmStatic
    fun getPersonalAvg(floor: String?, splitName: String?): Double {
        if (floor == null || splitName == null) return -1.0
        synchronized(lock) {
            val floorData = data[floor] ?: return -1.0
            val times = floorData[splitName]
            if (times == null || times.isEmpty()) return -1.0
            return times.asSequence()
                .filter { it > 0 && it <= MAX_SPLIT_SECONDS }
                .average()
                .let { if (it.isNaN()) -1.0 else it }
        }
    }

    @JvmStatic
    fun getPersonalBest(floor: String?, splitName: String?): Double {
        if (floor == null || splitName == null) return -1.0
        synchronized(lock) {
            val floorData = data[floor] ?: return -1.0
            val times = floorData[splitName]
            if (times == null || times.isEmpty()) return -1.0
            return times.asSequence()
                .filter { it > 0 && it <= MAX_SPLIT_SECONDS }
                .minOrNull() ?: -1.0
        }
    }

    @JvmStatic
    fun runCount(floor: String?, splitName: String?): Int = synchronized(lock) {
        val floorData = data[floor] ?: return@synchronized 0
        floorData[splitName]?.size ?: 0
    }

    private fun load() {
        synchronized(lock) {
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
    }

    private fun save() {
        // snapshot under the lock, write off-thread; single-thread executor keeps writes FIFO
        val json = synchronized(lock) { GSON.toJson(data) }
        writeExecutor.execute {
            try {
                val file = File(FILE_PATH)
                file.parentFile?.mkdirs()
                FileWriter(file).use { writer -> writer.write(json) }
            } catch (_: Exception) {
            }
        }
    }
}

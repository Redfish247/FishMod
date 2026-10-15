package fishmod.features.achievements

import com.google.gson.GsonBuilder
import fishmod.utils.IoExecutor
import fishmod.utils.debug.FishDiag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// Achievement counters, unlocks and run/RNG logs; main-thread mutations, debounced off-thread writes
object AchStore {

    class RunRec { var t = 0L; var floor = ""; var secs = 0.0; var score = 0; var deaths = 0; var secrets = 0; var solo = false }
    class RngRec { var t = 0L; var item = ""; var value = 0.0; var source = "" }
    class Feed { var base = 0.0; var last = 0.0 }

    class Data {
        var since = 0L
        var counters: MutableMap<String, Long> = HashMap()
        var unlocked: MutableMap<String, Long> = LinkedHashMap()
        var playDays: MutableMap<String, Long> = HashMap()
        var playDaysDungeon: MutableMap<String, Long> = HashMap()
        var fmDays: MutableSet<String> = HashSet()
        var mayors: MutableSet<String> = HashSet()
        var featuresOn: MutableSet<String> = HashSet()
        var feeds: MutableMap<String, Feed> = HashMap()
        var runs: MutableList<RunRec> = ArrayList()
        var rng: MutableList<RngRec> = ArrayList()
        var lastApiSync = 0L
    }

    private const val MAX_RUNS = 5000
    private const val MAX_RNG = 2000
    private val FILE: Path = Paths.get("config/fishmod/achievements.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    var data = Data(); private set
    @Volatile private var dirty = false
    private var lastSave = 0L
    var version = 0; private set

    fun load() {
        try {
            if (Files.exists(FILE)) GSON.fromJson(Files.readString(FILE), Data::class.java)?.let { data = it }
        } catch (e: Exception) { FishDiag.fail("AchStore.1", "achievements.json load failed", e) }
        if (data.since == 0L) { data.since = System.currentTimeMillis(); changed() }
    }

    fun get(k: String): Long = data.counters[k] ?: 0L

    fun inc(k: String, n: Long = 1) { if (n == 0L) return; data.counters[k] = get(k) + n; changed() }

    // Never lowers a counter (API values and bests)
    fun max(k: String, v: Long) { if (v > get(k)) { data.counters[k] = v; changed() } }

    fun set(k: String, v: Long) { if (get(k) != v) { data.counters[k] = v; changed() } }

    fun addRun(r: RunRec) { data.runs.add(r); while (data.runs.size > MAX_RUNS) data.runs.removeAt(0); changed() }
    fun addRng(r: RngRec) { data.rng.add(r); while (data.rng.size > MAX_RNG) data.rng.removeAt(0); changed() }

    fun changed() { dirty = true; version++ }

    fun tick() {
        val now = System.currentTimeMillis()
        if (dirty && now - lastSave > 10_000) saveNow()
    }

    fun saveNow() {
        if (!dirty) return
        dirty = false
        lastSave = System.currentTimeMillis()
        val json = try { GSON.toJson(data) } catch (t: Throwable) { FishDiag.fail("AchStore.2", "serialize failed", t); return }
        IoExecutor.execute {
            Files.createDirectories(FILE.parent)
            val tmp = FILE.resolveSibling("achievements.json.tmp")
            Files.writeString(tmp, json)
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

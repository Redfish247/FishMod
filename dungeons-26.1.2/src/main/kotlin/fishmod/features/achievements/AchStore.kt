package fishmod.features.achievements

import com.google.gson.GsonBuilder
import fishmod.utils.IoExecutor
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// Achievement counters, unlocks and run/RNG logs, one file per Minecraft UUID; main-thread mutations, debounced off-thread writes
object AchStore {

    class RunRec { var t = 0L; var floor = ""; var secs = 0.0; var score = 0; var deaths = 0; var secrets = 0; var solo = false }
    class RngRec { var t = 0L; var item = ""; var value = 0.0; var source = "" }
    class Feed { var base = 0.0; var last = 0.0 }

    class Data {
        var owner = ""
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
        var verSeen = ""
        var updateDay = ""
        var mastersOn: MutableSet<String> = HashSet()
        var hudAreas: MutableSet<String> = HashSet()
        var riftAreas: MutableSet<String> = HashSet()
        var basis: MutableMap<String, DoubleArray> = HashMap()
    }

    private const val MAX_RUNS = 5000
    private const val MAX_RNG = 2000
    private val DIR: Path = Paths.get("config/fishmod/achievements")
    private val LEGACY: Path = Paths.get("config/fishmod/achievements.json")
    // Un-keyed saves shipped in Eli's modpack (matched by their creation stamp); only his UUID may claim them
    private val KNOWN_LEGACY = mapOf(1792038447645L to "2abb218fada349bea6d181a2872941e2", 1792037278734L to "2abb218fada349bea6d181a2872941e2")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    var data = Data(); private set
    @Volatile private var dirty = false
    private var lastSave = 0L
    var version = 0; private set

    fun load() { bind() }

    // True when the loaded progress belongs to this dashless UUID
    fun ownedBy(uuid: String): Boolean = uuid.isNotEmpty() && data.owner == uuid

    // Swaps to the logged-in player's file; called every tick, cheap when nothing changed
    fun bind() {
        val uuid = Minecraft.getInstance().player?.uuid?.toString()?.replace("-", "")?.lowercase() ?: return
        if (data.owner == uuid) return
        saveNow()
        data = read(uuid)
        dirty = false
        if (data.since == 0L) data.since = System.currentTimeMillis()
        changed()
        AchUsage.onBind()
        saveNow()
    }

    private fun read(uuid: String): Data {
        val file = DIR.resolve("$uuid.json")
        try {
            if (Files.exists(file)) {
                val d = GSON.fromJson(Files.readString(file), Data::class.java) ?: return fresh(uuid)
                if (d.owner.isNotEmpty() && d.owner != uuid) { FishDiag.fail("AchStore.3", "achievements file owner ${d.owner} != $uuid, starting fresh", null); return fresh(uuid) }
                d.owner = uuid
                return d
            }
            if (Files.exists(LEGACY)) return claimLegacy(uuid)
        } catch (e: Exception) { FishDiag.fail("AchStore.1", "achievements load failed", e) }
        return fresh(uuid)
    }

    // Old un-keyed achievements.json: first player on this install claims it, except a known copied save
    private fun claimLegacy(uuid: String): Data {
        val d = GSON.fromJson(Files.readString(LEGACY), Data::class.java)
        Files.move(LEGACY, LEGACY.resolveSibling("achievements.json.legacy"), StandardCopyOption.REPLACE_EXISTING)
        val knownOwner = d?.let { KNOWN_LEGACY[it.since] }
        if (d == null || (knownOwner != null && knownOwner != uuid)) return fresh(uuid)
        d.owner = uuid
        return d
    }

    private fun fresh(uuid: String) = Data().apply { owner = uuid }

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
        val owner = data.owner
        if (owner.isEmpty()) return
        lastSave = System.currentTimeMillis()
        val json = try { GSON.toJson(data) } catch (t: Throwable) { FishDiag.fail("AchStore.2", "serialize failed", t); return }
        IoExecutor.execute {
            Files.createDirectories(DIR)
            val file = DIR.resolve("$owner.json")
            val tmp = DIR.resolve("$owner.json.tmp")
            Files.writeString(tmp, json)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

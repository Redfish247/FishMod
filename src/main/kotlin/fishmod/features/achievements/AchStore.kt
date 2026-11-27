package fishmod.features.achievements

import com.google.gson.GsonBuilder
import fishmod.utils.IoExecutor
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

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
    private val KNOWN_LEGACY = mapOf(1792038447645L to "2abb218fada349bea6d181a2872941e2", 1792037278734L to "2abb218fada349bea6d181a2872941e2")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    var data = Data(); private set
    @Volatile private var dirty = false
    private var lastSave = 0L
    var version = 0; private set

    fun load() { bind() }

    fun ownedBy(uuid: String): Boolean = uuid.isNotEmpty() && data.owner == uuid

    private var lastBoundUuid: java.util.UUID? = null

    fun bind() {
        val playerUuid = Minecraft.getInstance().player?.uuid ?: return
        if (playerUuid == lastBoundUuid) return
        val uuid = playerUuid.toString().replace("-", "").lowercase()
        if (data.owner == uuid) { lastBoundUuid = playerUuid; return }
        if (System.currentTimeMillis() < retryAt) return
        saveNow()
        val loaded = try { read(uuid) } catch (e: java.io.IOException) {
            // Transient read error (file locked etc.): stay unbound so nothing overwrites the file, retry soon
            FishDiag.fail("AchStore.4", "achievements read failed for $uuid, retrying", e)
            data = Data(); dirty = false; retryAt = System.currentTimeMillis() + 5_000
            return
        }
        data = loaded
        dirty = false
        if (data.since == 0L) data.since = System.currentTimeMillis()
        changed()
        AchUsage.onBind()
        saveNow()
        lastBoundUuid = playerUuid
    }

    private var retryAt = 0L

    // Throws IOException on a transient read error; garbage files fall back to the .bak copy
    private fun read(uuid: String): Data {
        val file = DIR.resolve("$uuid.json")
        val bak = DIR.resolve("$uuid.json.bak")
        if (Files.exists(file)) {
            parse(file, uuid)?.let { return it }
            quarantine(file)
            parse(bak, uuid)?.let { FishDiag.fail("AchStore.3", "achievements file for $uuid unreadable, restored from backup", null); return it }
            FishDiag.fail("AchStore.1", "achievements file for $uuid unreadable and no backup, starting fresh", null)
            return fresh(uuid)
        }
        parse(bak, uuid)?.let { return it }
        if (Files.exists(LEGACY)) {
            try { return claimLegacy(uuid) } catch (e: java.io.IOException) { if (e !is java.nio.charset.CharacterCodingException) throw e } catch (_: Exception) {}
            quarantine(LEGACY)
        }
        return fresh(uuid)
    }

    // Null when missing or not a valid save for this uuid
    private fun parse(f: Path, uuid: String): Data? {
        if (!Files.exists(f)) return null
        val text = try { Files.readString(f) } catch (e: java.nio.charset.CharacterCodingException) { return null }
        val d = try { GSON.fromJson(text, Data::class.java) } catch (_: Exception) { return null } ?: return null
        if (d.owner.isNotEmpty() && d.owner != uuid) return null
        d.owner = uuid
        return d
    }

    private fun quarantine(f: Path) {
        runCatching { Files.move(f, f.resolveSibling("${f.fileName}.corrupt-${System.currentTimeMillis()}"), StandardCopyOption.REPLACE_EXISTING) }
    }

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
            if (Files.exists(file)) runCatching { Files.copy(file, DIR.resolve("$owner.json.bak"), StandardCopyOption.REPLACE_EXISTING) }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

package fishmod.features.croesus

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Persistent store for the manual loot/profit tracker shown in the Dungeon-Hub inventory.
 * Holds a run counter and a list of manually-added drop rows. Persists to
 * `config/fishmod/loot_tracker.json`.
 */
object LootTrackerStore {

    private val FILE: Path = Paths.get("config/fishmod/loot_tracker.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    class Row {
        @JvmField var name: String = ""
        @JvmField var id: String = ""    // "" if unresolved
        @JvmField var count: Int = 0
    }

    class Data {
        @JvmField var runs: Int = 0
        @JvmField var rows: MutableList<Row> = ArrayList()
    }

    private var data: Data? = null
    private var loaded = false

    @JvmStatic
    @Synchronized
    fun get(): Data {
        ensureLoaded()
        return data!!
    }

    @JvmStatic
    @Synchronized
    fun runs(): Int {
        ensureLoaded()
        return data!!.runs
    }

    @JvmStatic
    @Synchronized
    fun setRuns(r: Int) {
        ensureLoaded()
        data!!.runs = Math.max(0, r)
        save()
    }

    @JvmStatic
    @Synchronized
    fun rows(): MutableList<Row> {
        ensureLoaded()
        return data!!.rows
    }

    /** Matches by id when non-empty, else by name; removes the row if count drops to 0. */
    @JvmStatic
    @Synchronized
    fun addOrIncrement(name: String?, id: String?, delta: Int) {
        ensureLoaded()
        var found: Row? = null
        for (r in data!!.rows) {
            val match = if (!id.isNullOrEmpty()) id == r.id else name.equals(r.name, ignoreCase = true)
            if (match) { found = r; break }
        }
        if (found == null) {
            if (delta <= 0) return
            found = Row()
            found.name = name ?: ""
            found.id = id ?: ""
            data!!.rows.add(found)
        }
        found.count += delta
        if (found.count <= 0) data!!.rows.remove(found)
        save()
    }

    @JvmStatic
    @Synchronized
    fun setCount(name: String?, id: String?, count: Int) {
        ensureLoaded()
        var found: Row? = null
        for (r in data!!.rows) {
            val match = if (!id.isNullOrEmpty()) id == r.id else name.equals(r.name, ignoreCase = true)
            if (match) { found = r; break }
        }
        if (count <= 0) {
            if (found != null) data!!.rows.remove(found)
        } else if (found != null) {
            found.count = count
        } else {
            found = Row()
            found.name = name ?: ""
            found.id = id ?: ""
            found.count = count
            data!!.rows.add(found)
        }
        save()
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        ensureLoaded()
        data!!.runs = 0
        data!!.rows.clear()
        save()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        data = Data()
        try {
            if (!Files.exists(FILE)) return
            val read = GSON.fromJson(Files.readString(FILE), Data::class.java)
            if (read != null) {
                data = read
                if (read.rows == null) read.rows = ArrayList()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            Files.writeString(FILE, GSON.toJson(data))
        } catch (ignored: java.io.IOException) {
        }
    }
}

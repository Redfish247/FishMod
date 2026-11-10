package fishmod.features.croesus

import com.google.gson.GsonBuilder
import fishmod.utils.debug.FishDiag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object LootTrackerStore {

    private val FILE: Path = Paths.get("config/fishmod/loot_tracker.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    class Row {
        @JvmField var name: String = ""
        @JvmField var id: String = ""
        @JvmField var count: Int = 0
    }

    class FloorData {
        @JvmField var runs: Int = 0
        @JvmField var rows: MutableList<Row> = ArrayList()
    }

    class Data {
        @JvmField var runs: Int = 0
        @JvmField var rows: MutableList<Row> = ArrayList()
        // per-floor split (E, F1-F7, M1-M7); runs from before this existed only count in the totals above
        @JvmField var floors: MutableMap<String, FloorData> = LinkedHashMap()
    }

    private val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7)
    private val FLOOR_RE = Regex("^(Master )?Catacombs - (?:Floor ([IVX]+|[0-9])|(Entrance))", RegexOption.IGNORE_CASE)

    /** Floor key (E, F1-F7, M1-M7) from a Croesus run screen title, or null when unparseable. */
    @JvmStatic
    fun floorFromTitle(title: String): String? {
        val m = FLOOR_RE.find(title.trim()) ?: return null
        if (m.groupValues[3].isNotEmpty()) return "E"
        val g = m.groupValues[2].uppercase()
        val n = g.toIntOrNull() ?: ROMAN[g] ?: return null
        if (n !in 1..7) return null
        return (if (m.groupValues[1].isNotEmpty()) "M" else "F") + n
    }

    @JvmStatic
    fun floorOrder(key: String): Int = if (key == "E") 0 else (if (key[0] == 'F') 0 else 7) + key.substring(1).toInt()

    /** Floors with at least one recorded run, in E, F1-F7, M1-M7 order. */
    @JvmStatic
    @Synchronized
    fun floorsWithRuns(): List<String> {
        ensureLoaded()
        return data!!.floors.filter { it.value.runs > 0 }.keys.sortedBy { floorOrder(it) }
    }

    @JvmStatic
    @Synchronized
    fun runs(floor: String?): Int {
        if (floor == null) return runs()
        ensureLoaded()
        return data!!.floors[floor]?.runs ?: 0
    }

    @JvmStatic
    @Synchronized
    fun rows(floor: String?): MutableList<Row> {
        if (floor == null) return rows()
        ensureLoaded()
        return data!!.floors[floor]?.rows ?: ArrayList()
    }

    @JvmStatic
    @Synchronized
    fun addRun(floor: String?) {
        ensureLoaded()
        data!!.runs++
        if (floor != null) floorData(floor).runs++
        save()
    }

    @JvmStatic
    @Synchronized
    fun setRuns(floor: String?, r: Int) {
        if (floor == null) { setRuns(r); return }
        ensureLoaded()
        val fd = floorData(floor)
        val n = Math.max(0, r)
        data!!.runs = Math.max(0, data!!.runs + n - fd.runs)
        fd.runs = n
        save()
    }

    @JvmStatic
    @Synchronized
    fun addAll(items: List<Triple<String?, String?, Int>>, floor: String?) {
        if (items.isEmpty()) return
        for ((name, id, delta) in items) {
            increment(rows(), name, id, delta)
            if (floor != null) increment(floorData(floor).rows, name, id, delta)
        }
        save()
    }

    @JvmStatic
    @Synchronized
    fun setCount(floor: String?, name: String?, id: String?, count: Int) {
        if (floor == null) { setCount(name, id, count); return }
        ensureLoaded()
        val rows = floorData(floor).rows
        val old = findRow(rows, name, id)?.count ?: 0
        setCountIn(rows, name, id, count)
        // keep the All totals in step with the floor edit
        val all = data!!.rows
        val cur = findRow(all, name, id)?.count ?: 0
        setCountIn(all, name, id, Math.max(0, cur + Math.max(0, count) - old))
        save()
    }

    /** Clears one floor and takes its runs and drops back out of the All totals. */
    @JvmStatic
    @Synchronized
    fun clear(floor: String?) {
        if (floor == null) { clear(); return }
        ensureLoaded()
        val fd = data!!.floors.remove(floor) ?: return
        data!!.runs = Math.max(0, data!!.runs - fd.runs)
        for (r in fd.rows) {
            val cur = findRow(data!!.rows, r.name, r.id)?.count ?: 0
            setCountIn(data!!.rows, r.name, r.id, Math.max(0, cur - r.count))
        }
        save()
    }

    private fun floorData(floor: String): FloorData = data!!.floors.getOrPut(floor) { FloorData() }

    private fun findRow(rows: List<Row>, name: String?, id: String?): Row? {
        for (r in rows) {
            val match = if (!id.isNullOrEmpty()) id == r.id else name.equals(r.name, ignoreCase = true)
            if (match) return r
        }
        return null
    }

    private fun setCountIn(rows: MutableList<Row>, name: String?, id: String?, count: Int) {
        val found = findRow(rows, name, id)
        if (count <= 0) {
            if (found != null) rows.remove(found)
        } else if (found != null) {
            found.count = count
        } else {
            val r = Row()
            r.name = name ?: ""
            r.id = id ?: ""
            r.count = count
            rows.add(r)
        }
    }

    private var data: Data? = null
    private var loaded = false

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

    @JvmStatic
    @Synchronized
    fun addOrIncrement(name: String?, id: String?, delta: Int) {
        increment(rows(), name, id, delta)
        save()
    }

    @JvmStatic
    @Synchronized
    fun addAll(items: List<Triple<String?, String?, Int>>) {
        if (items.isEmpty()) return
        for ((name, id, delta) in items) increment(rows(), name, id, delta)
        save()
    }

    private fun increment(rows: MutableList<Row>, name: String?, id: String?, delta: Int) {
        ensureLoaded()
        var found = findRow(rows, name, id)
        if (found == null) {
            if (delta <= 0) { FishDiag.fail("LootTrackerStore.4", "decrement of untracked loot '$name' ($id) by $delta"); return }
            found = Row()
            found.name = name ?: ""
            found.id = id ?: ""
            rows.add(found)
        }
        found.count += delta
        if (found.count <= 0) rows.remove(found)
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
        data!!.floors.clear()
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
                if (read.floors == null) read.floors = LinkedHashMap()
            }
        } catch (e: Exception) {
            FishDiag.fail("LootTrackerStore.1", "loot tracker load failed, starting empty", e)
        }
    }

    private fun save() {
        val json = FishDiag.guard("LootTrackerStore.2", "loot tracker serialize failed") { GSON.toJson(data) } ?: return
        fishmod.utils.IoExecutor.execute { FishDiag.guard("LootTrackerStore.3", "loot tracker write failed") { fishmod.utils.SafeFiles.writeAtomic(FILE, json) } }
    }
}

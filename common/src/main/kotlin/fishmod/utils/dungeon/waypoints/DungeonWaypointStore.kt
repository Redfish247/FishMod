package fishmod.utils.dungeon.waypoints

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * GSON-backed store of every user-placed dungeon waypoint, keyed by
 * [fishmod.utils.dungeon.map.RoomSignature.key] (rotation-normalized). Same load-on-static-init,
 * save-on-mutation pattern as `RoomSignatureDB`.
 *
 * Stored in config/fishmod-dungeon-waypoints.json as `{ signatureKey: [ StoredWaypoint, ... ] }`.
 *
 * Ported from a Java class with only static members — a Kotlin `object` with `@JvmStatic` on every
 * public member so Java call sites (e.g. `DungeonWaypointStore.get(...)`) keep working unchanged.
 */
object DungeonWaypointStore {

    private const val FILE_PATH = "config/fishmod-dungeon-waypoints.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private var data: MutableMap<String, MutableList<StoredWaypoint>> = HashMap()

    init {
        load()
    }

    @JvmStatic
    fun get(roomKey: String): List<StoredWaypoint> = data.getOrDefault(roomKey, emptyList())

    @JvmStatic
    fun add(roomKey: String, waypoint: StoredWaypoint) {
        data.computeIfAbsent(roomKey) { ArrayList() }.add(waypoint)
        save()
    }

    /** Removes the waypoint whose stored position is within `epsilon` of (x,y,z). Returns true if one was removed. */
    @JvmStatic
    fun removeNear(roomKey: String, x: Double, y: Double, z: Double, epsilon: Double): Boolean {
        val list = data[roomKey] ?: return false
        val removed = list.removeIf { w ->
            Math.abs(w.x - x) < epsilon && Math.abs(w.y - y) < epsilon && Math.abs(w.z - z) < epsilon
        }
        if (removed) save()
        return removed
    }

    @JvmStatic
    fun clearRoom(roomKey: String) {
        if (data.remove(roomKey) != null) save()
    }

    /** Removes every waypoint tagged with [routeId] across all rooms. Returns how many were removed. */
    @JvmStatic
    fun removeRoute(routeId: String): Int {
        var removed = 0
        for (list in data.values) {
            removed += list.count { it.routeId == routeId }
            list.removeIf { it.routeId == routeId }
        }
        if (removed > 0) save()
        return removed
    }

    @JvmStatic
    fun allData(): MutableMap<String, MutableList<StoredWaypoint>> = data

    @JvmStatic
    fun replaceAll(newData: MutableMap<String, MutableList<StoredWaypoint>>?) {
        data = newData ?: HashMap()
        save()
    }

    /** 90-degree rotation of a room-tile-relative point around its tile center. steps in [0,3], applied CCW to match `RoomSignature`'s (x,z) -> (z,-x). */
    @JvmStatic
    fun rotate90(x: Double, z: Double, steps: Int): DoubleArray {
        var rx = x
        var rz = z
        val n = ((steps % 4) + 4) % 4
        for (i in 0 until n) {
            val nx = rz
            val nz = -rx
            rx = nx
            rz = nz
        }
        return doubleArrayOf(rx, rz)
    }

    @JvmStatic
    fun exportBase64(): String? {
        return try {
            val json = GSON.toJson(data)
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gz ->
                gz.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            Base64.getEncoder().encodeToString(baos.toByteArray())
        } catch (e: Exception) {
            null
        }
    }

    /** Decodes base64(gzip(json)) and replaces the whole DB. Returns true on success. */
    @JvmStatic
    fun importBase64(base64: String?): Boolean {
        if (base64 == null || base64.isBlank()) return false
        return try {
            val compressed = Base64.getDecoder().decode(base64.trim())
            val bais = ByteArrayInputStream(compressed)
            val sb = StringBuilder()
            GZIPInputStream(bais).use { gz ->
                val buf = ByteArray(4096)
                var n: Int
                while (gz.read(buf).also { n = it } > 0) {
                    sb.append(String(buf, 0, n, StandardCharsets.UTF_8))
                }
            }
            val type = object : TypeToken<MutableMap<String, MutableList<StoredWaypoint>>>() {}.type
            val loaded: MutableMap<String, MutableList<StoredWaypoint>>? = GSON.fromJson(sb.toString(), type)
            if (loaded == null) return false
            replaceAll(loaded)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<MutableMap<String, MutableList<StoredWaypoint>>>() {}.type
                val loaded: MutableMap<String, MutableList<StoredWaypoint>>? = GSON.fromJson(reader, type)
                if (loaded != null) data = loaded
            }
        } catch (ignored: Exception) {
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer ->
                GSON.toJson(data, writer)
            }
        } catch (ignored: Exception) {
        }
    }
}

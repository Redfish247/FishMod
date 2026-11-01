package fishmod.features.dungeon

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.Misc
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.waypoints.DungeonWaypointStore
import fishmod.utils.dungeon.waypoints.StoredWaypoint
import fishmod.utils.dungeon.waypoints.WaypointType
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream

// Imports Odin dungeon waypoints (room name -> list, clay-relative, north-rotated) into the room store.
object OdinWaypointImport {

    private const val PACKS_DIR = "config/odin/dungeon-waypoints"
    private const val LEGACY_FILE = "config/odin/dungeon-waypoint-config.json"
    private const val ROOM_KEY_PREFIX = "dungeon:room:"

    // Odin room names that differ from ours.
    private val ROOM_ALIASES = mapOf("Criss-Cross" to "Criss Cross", "Multicolored" to "Multi Colored")

    @JvmStatic
    fun importFromOdinConfig() {
        val files = File(PACKS_DIR).listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
            .ifEmpty { listOfNotNull(File(LEGACY_FILE).takeIf { it.exists() }) }
        if (files.isEmpty()) {
            Misc.addChatMessage(Component.literal("§cNo Odin waypoint config found in config/odin/."))
            return
        }
        val merged = LinkedHashMap<String, MutableList<StoredWaypoint>>()
        var read = 0
        for (f in files) {
            val parsed = FishDiag.guard("OdinWaypointImport.1", "failed to read Odin pack ${f.name}") {
                convert(JsonParser.parseString(f.readText()))
            } ?: continue
            parsed.forEach { (k, v) -> merged.getOrPut(k) { ArrayList() }.addAll(v) }
            read++
        }
        finish(merged, "$read Odin pack${if (read == 1) "" else "s"}")
    }

    @JvmStatic
    fun importFromClipboard() {
        val clip = Minecraft.getInstance().keyboardHandler.clipboard?.trim().orEmpty()
        val parsed = if (clip.isEmpty()) null else try {
            val json = if (clip.startsWith("{")) clip
                else GZIPInputStream(Base64.getDecoder().decode(clip).inputStream()).bufferedReader().use { it.readText() }
            convert(JsonParser.parseString(json))
        } catch (e: Exception) { null }
        if (parsed == null) {
            Misc.addChatMessage(Component.literal("§cClipboard doesn't look like an Odin waypoint export (/dwp export)."))
            return
        }
        finish(parsed, "clipboard")
    }

    private fun finish(entries: Map<String, List<StoredWaypoint>>, source: String) {
        val total = entries.values.sumOf { it.size }
        val added = DungeonWaypointStore.addAll(entries, 0.05)
        DungeonWaypoints.refreshLive()
        Misc.addChatMessage(Component.literal(
            "§aImported $added Odin waypoint${if (added == 1) "" else "s"} from $source across ${entries.size} rooms" +
                if (total > added) " §7(${total - added} duplicates skipped)" else ""
        ))
    }

    private fun convert(root: JsonElement): Map<String, List<StoredWaypoint>> {
        val out = LinkedHashMap<String, List<StoredWaypoint>>()
        for ((room, arr) in root.asJsonObject.entrySet()) {
            if (!arr.isJsonArray) continue
            val list = arr.asJsonArray.mapNotNull { e ->
                FishDiag.guard("OdinWaypointImport.2", "bad Odin waypoint in $room") { toStored(e.asJsonObject) }
            }
            if (list.isNotEmpty()) out[ROOM_KEY_PREFIX + (ROOM_ALIASES[room] ?: room)] = list
        }
        return out
    }

    private fun toStored(o: JsonObject): StoredWaypoint {
        val pos = o.getAsJsonObject("blockPos")
        val bx = coord(pos ?: o, "x", "field_11175")
        val by = coord(pos ?: o, "y", "field_11174")
        val bz = coord(pos ?: o, "z", "field_11173")

        // Odin box is block-local (0..1 = full block); keep its size and vertical centre.
        val box = o.getAsJsonObject("aabb")
        val b = if (box == null) doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else
            if (box.has("minX")) listOf("minX", "minY", "minZ", "maxX", "maxY", "maxZ").map { box[it].asDouble }.toDoubleArray()
            else if (box.has("field_1323")) listOf("field_1323", "field_1322", "field_1321", "field_1320", "field_1325", "field_1324").map { box[it].asDouble }.toDoubleArray()
            else listOf("field_72340_a", "field_72338_b", "field_72339_c", "field_72336_d", "field_72337_e", "field_72334_f").map { box[it].asDouble }.toDoubleArray()
        fun half(min: Double, max: Double) = Math.abs(max - min).let { if (it < 1e-3) 0.5 else it / 2.0 }

        val typeName = o.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { n -> WaypointType.entries.any { it.name == n } }
            ?: if (o.get("secret")?.asBoolean == true) WaypointType.SECRET.name else null

        return StoredWaypoint(
            bx + 0.5, by + (b[1] + b[4]) / 2.0, bz + 0.5,
            half(b[0], b[3]), half(b[1], b[4]), half(b[2], b[5]),
            color(o.get("color")),
            o.get("filled")?.asBoolean ?: false,
            !(o.get("depth")?.asBoolean ?: true),
            o.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null },
            typeName, null
        )
    }

    private fun coord(o: JsonObject, name: String, srg: String): Int = (o.get(name) ?: o.get(srg)).asInt

    // Odin Color serializes as HSB + alpha; fall back to its cached rgba int.
    private fun color(e: JsonElement?): Int {
        if (e == null || e.isJsonNull) return 0xFFFFFFFF.toInt()
        if (e.isJsonPrimitive) return e.asInt
        val c = e.asJsonObject
        if (c.has("hue")) {
            val alpha = (c.get("alphaFloat") ?: c.get("alpha"))?.asFloat ?: 1f
            val rgb = java.awt.Color.HSBtoRGB(c["hue"].asFloat, c["saturation"].asFloat, c["brightness"].asFloat) and 0xFFFFFF
            return rgb or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
        }
        return c.get("rgba")?.asInt ?: 0xFFFFFFFF.toInt()
    }
}

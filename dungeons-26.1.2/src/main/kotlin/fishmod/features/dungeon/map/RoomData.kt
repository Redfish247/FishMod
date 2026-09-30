package fishmod.features.dungeon.map

import fishmod.utils.debug.FishDiag
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import fishmod.utils.debug.Debug
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

class RoomData {
    var name: String? = null
        private set
    var type: Room.Type? = null
    var cores: List<Int>? = null
        private set
    var secrets: Int = 0
        private set
    var shape: Room.Shape? = null
        private set
    var prince: Boolean = false
        private set
    var secretDetails: Map<String, List<BlockPos>>? = null
        private set

    companion object {
        private const val ROOMS_JSON_PATH = "/assets/fishmod/map/rooms.json"

        private val GSON: Gson = GsonBuilder()
            .registerTypeAdapter(
                Room.Shape::class.java,
                JsonDeserializer { json, _, _ ->
                    if (json != null && !json.isJsonNull && json.isJsonPrimitive) {
                        Room.Shape.fromStr(json.asString) ?: run {
                            FishDiag.fail("RoomData.1", "unknown room shape '${json.asString}' in rooms.json")
                            Room.Shape.UNKNOWN
                        }
                    } else {
                        Room.Shape.UNKNOWN
                    }
                }
            )
            .registerTypeAdapter(
                BlockPos::class.java,
                JsonDeserializer { json, _, _ ->
                    try {
                        if (json.isJsonObject) {
                            val o = json.asJsonObject
                            BlockPos(o.get("x").asInt, o.get("y").asInt, o.get("z").asInt)
                        } else {
                            val p = json.asString.split(Regex(",\\s*"))
                            val px = p.getOrNull(0)?.trim()?.toIntOrNull()
                            val py = p.getOrNull(1)?.trim()?.toIntOrNull()
                            val pz = p.getOrNull(2)?.trim()?.toIntOrNull()
                            if (px == null || py == null || pz == null) FishDiag.fail("RoomData.2", "bad secret pos '${json.asString}' in rooms.json")
                            BlockPos(px ?: 0, py ?: 0, pz ?: 0)
                        }
                    } catch (e: Exception) {
                        FishDiag.fail("RoomData.3", "secret pos parse failed: $json", e)
                        BlockPos(0, 0, 0)
                    }
                }
            )
            .create()

        private var roomMap: Map<Int, RoomData> = emptyMap()
        var loaded: Boolean = false
            private set

        @JvmStatic
        fun getRoomData(core: Int): RoomData? = roomMap[core]

        @JvmStatic
        fun isLoaded(): Boolean = loaded

        @JvmStatic
        fun loadRoomData() {
            try {
                val stream = RoomData::class.java.getResourceAsStream(ROOMS_JSON_PATH) ?: run {
                    FishDiag.fail("RoomData.4", "rooms.json resource missing at $ROOMS_JSON_PATH")
                    return
                }

                val listType: Type = object : TypeToken<List<RoomData>>() {}.type
                val built = HashMap<Int, RoomData>()

                stream.use { s ->
                    InputStreamReader(s, StandardCharsets.UTF_8).use { reader ->
                        val all: List<RoomData>? = GSON.fromJson(reader, listType)
                        if (all == null) FishDiag.fail("RoomData.5", "rooms.json parsed to null")
                        if (all != null) {
                            for (rd in all) {
                                if (rd.name == null) FishDiag.fail("RoomData.6", "room entry with no name cores=${rd.cores}")
                                if (rd.type == null) FishDiag.fail("RoomData.7", "room '${rd.name}' has no/unknown type")
                                val cores = rd.cores
                                if (cores == null) FishDiag.fail("RoomData.8", "room '${rd.name}' has no cores")
                                if (cores != null) {
                                    for (core in cores) {
                                        built[core] = rd
                                    }
                                }
                            }
                        }
                    }
                }

                FishDiag.check(built.isNotEmpty(), "RoomData.9") { "rooms.json loaded but no cores registered" }
                roomMap = built
                loaded = true
            } catch (e: Exception) {
                Debug.LOGGER.error("Failed to load dungeon map rooms.json", e)
                FishDiag.fail("RoomData.10", "failed to load dungeon map rooms.json", e)
            }
        }
    }
}

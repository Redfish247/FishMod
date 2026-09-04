package fishmod.features.dungeon.map

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import fishmod.Bladeaddons
import fishmod.utils.debug.Debug
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

/** Bundled per-core room metadata loaded from assets/fishmod/map/rooms.json. */
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
                        Room.Shape.fromStr(json.asString) ?: Room.Shape.UNKNOWN
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
                            BlockPos(
                                p.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
                                p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                                p.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                            )
                        }
                    } catch (e: Exception) {
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
                val stream = Bladeaddons::class.java.getResourceAsStream(ROOMS_JSON_PATH) ?: return

                val listType: Type = object : TypeToken<List<RoomData>>() {}.type
                val built = HashMap<Int, RoomData>()

                stream.use { s ->
                    InputStreamReader(s, StandardCharsets.UTF_8).use { reader ->
                        val all: List<RoomData>? = GSON.fromJson(reader, listType)
                        if (all != null) {
                            for (rd in all) {
                                val cores = rd.cores
                                if (cores != null) {
                                    for (core in cores) {
                                        built[core] = rd
                                    }
                                }
                            }
                        }
                    }
                }

                roomMap = built
                loaded = true
            } catch (e: Exception) {
                Debug.LOGGER.error("Failed to load dungeon map rooms.json", e)
            }
        }
    }
}

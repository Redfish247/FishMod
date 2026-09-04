package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.Room as MapRoom
import fishmod.utils.Location
import fishmod.utils.debug.Debug
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Room model for the puzzle solvers. An earlier stand-alone scanner never matched 26.1 reliably
 * (~half the time), which made every box-placing solver misfire.
 *
 * It now rides on FishMod's own dungeon-map scanner ([DungeonMap] / [MapRoom]), which already
 * resolves room identity, rotation and the clay-corner every scan tick and retries until it lands.
 * The [ORoom] we hand the solvers carries that rotation + clayPos verbatim; [ORoom.getRealCoords]
 * is identical to [MapRoom.offset], so solutions land exactly where the map says the room is.
 */
object OdinScan {

    /** Room table — kept only for `type` / `cores` metadata by room name. */
    private val nameToData: Map<String, ORoomData> = run {
        try {
            OdinScan::class.java.getResourceAsStream("/odin_rooms.json")!!.use { s ->
                val list: Set<ORoomData> = Gson().fromJson(
                    InputStreamReader(s, StandardCharsets.UTF_8),
                    object : TypeToken<Set<ORoomData>>() {}.type,
                )
                list.associateBy { it.name }
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("Odin rooms.json failed to load", e); emptyMap()
        }
    }

    var currentRoom: ORoom? = null
        private set

    val currentRoomName: String get() = currentRoom?.data?.name ?: "Unknown"

    private val enterListeners = mutableListOf<(ORoom?) -> Unit>()
    fun onRoomEnter(cb: (ORoom?) -> Unit) { enterListeners.add(cb) }

    private var lastKey: String? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
        Events.ON_WORLD_CHANGE.register { setRoom(null); false }
    }

    @Volatile private var lastDiag = 0L
    private fun diag(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastDiag > 3000) { lastDiag = now; Debug.LOGGER.info("[OdinScan] {}", msg) }
    }

    private fun tick(mc: Minecraft) {
        if (mc.player == null || mc.level == null) return
        if (!Location.inDungeon() || Phase.inBoss()) {
            if (currentRoom != null) setRoom(null)
            return
        }

        val map = DungeonMap.roomPlayerIn()?.owner
        // roomPlayerIn() drops to null on doorways / room edges / unscanned tiles as you move, so
        // treat null as a transient miss and keep the last room; only switch on a *different* room.
        val mapName = map?.data?.name
        if (map == null || mapName == null || map.rotation == MapRoom.Rotation.NONE) {
            diag("transient miss (map=${map != null} name=$mapName rot=${map?.rotation}) — keeping '${currentRoom?.data?.name}'")
            return
        }

        val clay = map.clayPos
        val key = "$mapName|${map.rotation.name}|${clay?.x},${clay?.z}"
        if (key == lastKey) return
        lastKey = key
        val built = build(map)
        diag("room='${built.data.name}' type=${built.data.type} rot=${built.rotation} clay=${clay?.x},${clay?.z}")
        setRoom(built)
    }

    private fun setRoom(room: ORoom?) {
        currentRoom = room
        if (room == null) lastKey = null
        for (l in enterListeners) runCatching { l(room) }
    }

    private fun build(m: MapRoom): ORoom {
        val name = m.data?.name ?: "Unknown"
        val data = nameToData[name] ?: ORoomData(
            name = name,
            type = runCatching { ORoomType.valueOf(m.type?.name ?: "") }.getOrDefault(ORoomType.NORMAL),
            cores = emptyList(),
            shape = m.shape?.let { runCatching { ORoomShape.valueOf(mapShapeName(it)) }.getOrNull() }
                ?: ORoomShape.UNKNOWN,
        )
        val comps = (if (m.tiles.isEmpty()) listOf(BlockPos(0, 0, 0))
        else m.tiles.map { BlockPos(it.pos.x, 0, it.pos.z) })
            .mapTo(mutableSetOf()) { ORoomComponent(it.x, it.z) }

        return ORoom(
            rotation = runCatching { ORotations.valueOf(m.rotation.name) }.getOrDefault(ORotations.NONE),
            data = data,
            clayPos = m.clayPos ?: BlockPos(0, 0, 0),
            roomComponents = comps,
        )
    }

    private fun mapShapeName(s: MapRoom.Shape): String = when (s) {
        MapRoom.Shape.SL -> "L"
        MapRoom.Shape.S1x1 -> "S1x1"
        MapRoom.Shape.S2x1 -> "S2x1"
        MapRoom.Shape.S3x1 -> "S3x1"
        MapRoom.Shape.S4x1 -> "S4x1"
        MapRoom.Shape.S2x2 -> "S2x2"
        MapRoom.Shape.UNKNOWN -> "UNKNOWN"
    }
}

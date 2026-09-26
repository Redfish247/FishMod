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

object OdinScan {

    private val nameToData: Map<String, ORoomData> = run {
        try {
            OdinScan::class.java.getResourceAsStream("/assets/fishmod/map/rooms.json")!!.use { s ->
                val list: Set<ORoomData> = Gson().fromJson(
                    InputStreamReader(s, StandardCharsets.UTF_8),
                    object : TypeToken<Set<ORoomData>>() {}.type,
                )
                list.associateBy { it.name }
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("rooms.json failed to load for OdinScan", e); emptyMap()
        }
    }

    var currentRoom: ORoom? = null
        private set

    val currentRoomName: String get() = currentRoom?.data?.name ?: "Unknown"

    private val enterListeners = mutableListOf<(ORoom?) -> Unit>()
    fun onRoomEnter(cb: (ORoom?) -> Unit) { enterListeners.add(cb) }

    private var lastName: String? = null
    private var lastRotation: MapRoom.Rotation? = null
    private var lastClay: net.minecraft.core.BlockPos? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
        Events.ON_WORLD_CHANGE.register { setRoom(null); false }
    }

    @Volatile private var lastDiag = 0L
    private fun diag(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastDiag > 3000) { lastDiag = now; Debug.LOGGER.debug("[OdinScan] {}", msg) }
    }

    private fun tick(mc: Minecraft) {
        if (mc.player == null || mc.level == null) return
        if (!Location.inDungeon() || Phase.inBoss()) {
            if (currentRoom != null) setRoom(null)
            return
        }

        val map = DungeonMap.roomPlayerIn()?.owner
        val mapName = map?.data?.name
        if (map == null || mapName == null || map.rotation == MapRoom.Rotation.NONE) {
            if (Debug.LOGGER.isDebugEnabled) diag("transient miss (map=${map != null} name=$mapName rot=${map?.rotation}) — keeping '${currentRoom?.data?.name}'")
            return
        }

        val clay = map.clayPos
        if (mapName == lastName && map.rotation == lastRotation && clay == lastClay) return
        lastName = mapName
        lastRotation = map.rotation
        lastClay = clay
        val built = build(map)
        diag("room='${built.data.name}' type=${built.data.type} rot=${built.rotation} clay=${clay?.x},${clay?.z}")
        setRoom(built)
    }

    fun findRoom(name: String): ORoom? {
        val m = synchronized(fishmod.features.dungeon.map.Scan.rooms) {
            fishmod.features.dungeon.map.Scan.rooms.firstOrNull { it.data?.name == name }
        } ?: return null
        if (m.rotation == MapRoom.Rotation.NONE || m.clayPos == null) return null
        return build(m)
    }

    private fun setRoom(room: ORoom?) {
        currentRoom = room
        if (room == null) { lastName = null; lastRotation = null; lastClay = null }
        for (l in enterListeners) runCatching { l(room) }
    }

    private fun build(m: MapRoom): ORoom {
        val name = m.data?.name ?: "Unknown"
        val data = nameToData[name] ?: ORoomData(
            name = name,
            type = runCatching { ORoomType.valueOf(m.type?.name ?: "") }.getOrDefault(ORoomType.NORMAL),
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

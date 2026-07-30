package fishmod.utils.dungeon.map

import fishmod.utils.Location
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import net.minecraft.component.type.MapIdComponent
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket

/** Calibrates a world-position -> 32-block grid-tile bridge for DungeonWaypoints by locating the entrance's green streak in the map's pixel data. */
object MapReader {
    private const val ENTRANCE_MAP_COLOR: Byte = 30

    private var currentMapId: MapIdComponent? = null
    private var calibrated = false
    private var roomGap = 20
    private var entranceTileX = 0
    private var entranceTileZ = 0

    // World origin captured once from the player's position the first tick the dungeon map is seen (near the entrance); dungeons are instanced 8 blocks off a fixed 32-block grid.
    private var worldAnchored = false
    private var worldOriginX = 0
    private var worldOriginZ = 0

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            if (Location.inDungeon() && packet is MapUpdateS2CPacket) {
                val newId = packet.mapId()
                // A changed map ID signals a new run; Location.DUNGEON can persist across runs via the hub, so ON_LOCATION_CHANGE alone can't detect it.
                if (currentMapId != null && currentMapId != newId) reset()
                currentMapId = newId
            }
            false
        }
        Events.ON_SERVER_TICK.register {
            tick()
            false
        }
        Events.ON_LOCATION_CHANGE.register { newLocation ->
            if (newLocation != Location.DUNGEON) reset()
            false
        }
    }

    private fun reset() {
        currentMapId = null
        calibrated = false
        roomGap = 20
        worldAnchored = false
    }

    private fun tick() {
        if (!Location.inDungeon() || currentMapId == null || calibrated) return
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return
        val world = mc.world ?: return

        if (!worldAnchored) {
            val px = Math.floor((player.x + 8.5)).toInt()
            val pz = Math.floor((player.z + 8.5)).toInt()
            worldOriginX = px - Math.floorMod(px, 32)
            worldOriginZ = pz - Math.floorMod(pz, 32)
            worldAnchored = true
        }

        val map = world.getMapState(currentMapId) ?: return
        if (map.colors == null || map.colors.size < 128 * 128) return

        tryCalibrate(map.colors)
    }

    /** Finds the entrance's green streak and derives the grid's pixel origin from its position modulo the room-grid period. */
    private fun tryCalibrate(colors: ByteArray): Boolean {
        var index = 0
        while (index < colors.size) {
            if (colors[index] != ENTRANCE_MAP_COLOR) {
                index++
                continue
            }

            var end = index
            while (end < colors.size && colors[end] == colors[index]) end++

            val length = end - index
            if (length != 16 && length != 18) {
                index++
                continue
            }

            val roomSize = length
            roomGap = roomSize + 4
            var pixelStartX = (index % 128) % roomGap
            var pixelStartY = (index / 128) % roomGap
            if (pixelStartX == 0) pixelStartX = 22
            if (pixelStartY == 0) pixelStartY = 22

            entranceTileX = ((index % 128) - pixelStartX) / roomGap
            entranceTileZ = ((index / 128) - pixelStartY) / roomGap

            calibrated = true
            return true
        }
        return false
    }

    @JvmStatic
    fun isCalibrated(): Boolean = calibrated

    // World-position bridge for DungeonWaypoints; only valid once isCalibrated() is true.

    /** World X of grid tile column tileX's northwest corner. */
    @JvmStatic
    fun tileWorldOriginX(tileX: Int): Int {
        return worldOriginX + (tileX - entranceTileX) * 32
    }

    /** World Z of grid tile row tileZ's northwest corner. */
    @JvmStatic
    fun tileWorldOriginZ(tileZ: Int): Int {
        return worldOriginZ + (tileZ - entranceTileZ) * 32
    }

    /** Which GridPos tile (0..5 range in normal play) a world X/Z position falls in. */
    @JvmStatic
    fun worldToGridPos(worldX: Double, worldZ: Double): GridPos {
        val tileX = entranceTileX + Math.floorDiv(Math.floor(worldX).toInt() - worldOriginX, 32)
        val tileZ = entranceTileZ + Math.floorDiv(Math.floor(worldZ).toInt() - worldOriginZ, 32)
        return GridPos(tileX, tileZ)
    }
}

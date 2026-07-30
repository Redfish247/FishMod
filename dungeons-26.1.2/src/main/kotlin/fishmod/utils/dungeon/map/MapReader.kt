package fishmod.utils.dungeon.map

import fishmod.utils.Location
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.saveddata.maps.MapId

/**
 * Reads the dungeon's vanilla map item each tick purely to calibrate a world-position bridge for
 * [fishmod.features.dungeon.DungeonWaypoints]: which fixed 32-block grid tile a world X/Z
 * position falls in. No room/door type tracking or HUD rendering — this only locates the entrance's
 * green streak in the map's pixel data once, to anchor [worldToGridPos].
 */
object MapReader {
    private const val ENTRANCE_MAP_COLOR: Byte = 30

    private var currentMapId: MapId? = null
    private var calibrated = false
    private var roomGap = 20
    private var entranceTileX = 0
    private var entranceTileZ = 0

    // World-position bridge — captured once, from the player's position the first tick the dungeon
    // map is seen (reliably at/near the entrance, since that's where a run starts). Hypixel dungeons
    // are always instanced 8 blocks off a fixed 32-block grid.
    private var worldAnchored = false
    private var worldOriginX = 0
    private var worldOriginZ = 0

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            if (Location.inDungeon() && packet is ClientboundMapItemDataPacket) {
                val newId = packet.mapId()
                // Each dungeon run gets its own fresh map item ID. Location alone doesn't reliably
                // signal "new run" — going from one run straight into another via the dungeon hub
                // can stay at Location.DUNGEON the whole time, so ON_LOCATION_CHANGE never fires
                // between runs. A changed map ID is a direct, run-specific signal instead.
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
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (!worldAnchored) {
            val px = Math.floor(player.x + 8.5).toInt()
            val pz = Math.floor(player.z + 8.5).toInt()
            worldOriginX = px - Math.floorMod(px, 32)
            worldOriginZ = pz - Math.floorMod(pz, 32)
            worldAnchored = true
        }

        val map = level.getMapData(currentMapId!!) ?: return
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

    // --- World-position bridge, exposed for DungeonWaypoints (see that class for usage). Only valid
    // once isCalibrated() is true (worldOriginX/Z are captured before calibration, but entranceTileX/Z
    // — needed to convert an arbitrary tile index — only come from tryCalibrate). ---

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

package fishmod.utils.dungeon.map

/**
 * STUB — see [RoomTile]. Rotation is always 0 and the key is not room-shape-derived until the
 * room-grid tracking system is rebuilt; [fishmod.features.dungeon.DungeonWaypoints] never actually
 * reaches this since [DungeonGrid.allRooms] is always empty, so no real waypoint uses this key.
 */
class RoomSignature private constructor(private val gridPos: GridPos) {
    fun key(): String = "stub:${gridPos}"
    fun rotation(): Int = 0

    companion object {
        @JvmStatic
        fun withRotation(tile: RoomTile): RoomSignature = RoomSignature(tile.pos())
    }
}

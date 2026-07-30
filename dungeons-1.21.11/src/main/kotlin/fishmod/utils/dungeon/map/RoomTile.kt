package fishmod.utils.dungeon.map

/** STUB: room-grid tracking (room/door detection) was removed; this exists only so DungeonWaypoints still compiles. DungeonGrid never produces instances, so room detection silently no-ops. */
class RoomTile(private val gridPos: GridPos) {
    fun pos(): GridPos = gridPos
}

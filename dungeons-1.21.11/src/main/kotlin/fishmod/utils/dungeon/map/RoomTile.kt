package fishmod.utils.dungeon.map

/**
 * STUB — the room-grid tracking system (Catlas-style room/door detection) was removed and is
 * pending a rebuild. This minimal shape exists only so [fishmod.features.dungeon.DungeonWaypoints]
 * (which snaps waypoints to a room's canonical orientation) still compiles; [DungeonGrid] never
 * actually produces any instances, so room detection silently no-ops until the real system returns.
 */
class RoomTile(private val gridPos: GridPos) {
    fun pos(): GridPos = gridPos
}

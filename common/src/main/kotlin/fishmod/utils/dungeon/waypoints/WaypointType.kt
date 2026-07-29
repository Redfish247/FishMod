package fishmod.utils.dungeon.waypoints

/** Kind of thing a waypoint marks, purely as metadata — no special placement/trajectory logic per type (see feature javadoc). */
enum class WaypointType {
    NONE, NORMAL, SECRET, ETHERWARP, MOVE, BLOCKETHERWARP
}

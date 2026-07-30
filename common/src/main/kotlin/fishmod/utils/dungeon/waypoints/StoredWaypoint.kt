package fishmod.utils.dungeon.waypoints

/** A waypoint persisted in [DungeonWaypointStore], stored room-tile-relative in the room's canonical (rotation 0) orientation; `@JvmField` plain fields keep Java call sites and Gson reflection working unchanged. */
class StoredWaypoint() {
    @JvmField var x: Double = 0.0
    @JvmField var y: Double = 0.0
    @JvmField var z: Double = 0.0

    /** Half-extents of the placed box, in blocks. */
    @JvmField var halfX: Double = 0.0
    @JvmField var halfY: Double = 0.0
    @JvmField var halfZ: Double = 0.0

    /** ARGB color, e.g. from an 8-hex-char RRGGBBAA input. */
    @JvmField var color: Int = 0
    @JvmField var filled: Boolean = false
    @JvmField var throughWalls: Boolean = false
    @JvmField var title: String? = null

    /** Enum name string, nullable. */
    @JvmField var type: String? = null

    /** Enum name string, nullable. */
    @JvmField var timer: String? = null

    /** Route grouping (e.g. dungeons-26.1.2's /fmwp route recording) — null if not part of a route. */
    @JvmField var routeId: String? = null

    /** Order of this waypoint within its route, when [routeId] is non-null. */
    @JvmField var routeOrder: Int = 0

    constructor(
        x: Double, y: Double, z: Double,
        halfX: Double, halfY: Double, halfZ: Double,
        color: Int, filled: Boolean, throughWalls: Boolean, title: String?,
        type: String?, timer: String?
    ) : this() {
        this.x = x; this.y = y; this.z = z
        this.halfX = halfX; this.halfY = halfY; this.halfZ = halfZ
        this.color = color
        this.filled = filled
        this.throughWalls = throughWalls
        this.title = title
        this.type = type
        this.timer = timer
    }
}

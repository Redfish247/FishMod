package fishmod.utils.dungeon.waypoints

/**
 * A single waypoint as persisted in [DungeonWaypointStore]. Position is stored
 * room-tile-relative (offset from the owning grid tile's 32x32 footprint center) and in the room's
 * CANONICAL orientation (rotation index 0 of [fishmod.utils.dungeon.map.RoomSignature]) — see
 * [DungeonWaypointStore] for the rotate-in/rotate-out convention used to make a waypoint set on
 * one instance of a room replay correctly on a later, differently-rotated instance of the same room.
 *
 * Fields are `@JvmField` plain mutable fields (not Kotlin properties with getters) so that:
 *  - Java call sites keep using direct field access (e.g. `w.halfX`, `canonical.x`) unchanged.
 *  - Gson's default reflection-based (de)serialization keeps working exactly as it did against the
 *    original Java class's public fields.
 */
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

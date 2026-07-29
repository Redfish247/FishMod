package fishmod.utils.dungeon.waypoints;

/**
 * A single waypoint as persisted in {@link DungeonWaypointStore}. Position is stored
 * room-tile-relative (offset from the owning grid tile's 32x32 footprint center) and in the room's
 * CANONICAL orientation (rotation index 0 of {@link fishmod.utils.dungeon.map.RoomSignature}) — see
 * {@link DungeonWaypointStore} for the rotate-in/rotate-out convention used to make a waypoint set on
 * one instance of a room replay correctly on a later, differently-rotated instance of the same room.
 */
public class StoredWaypoint {
    public double x, y, z;
    /** Half-extents of the placed box, in blocks. */
    public double halfX, halfY, halfZ;
    /** ARGB color, e.g. from an 8-hex-char RRGGBBAA input. */
    public int color;
    public boolean filled;
    public boolean throughWalls;
    public String title;
    /** Enum name string, nullable. */
    public String type;
    /** Enum name string, nullable. */
    public String timer;
    /** Non-null when this waypoint is part of an ordered route (see DungeonWaypoints route recording); null for a standalone waypoint. */
    public String routeId;
    /** This waypoint's position within its route, ascending. Meaningless when routeId is null. */
    public int routeOrder;

    public StoredWaypoint() {}

    public StoredWaypoint(double x, double y, double z, double halfX, double halfY, double halfZ,
                           int color, boolean filled, boolean throughWalls, String title,
                           String type, String timer) {
        this.x = x; this.y = y; this.z = z;
        this.halfX = halfX; this.halfY = halfY; this.halfZ = halfZ;
        this.color = color;
        this.filled = filled;
        this.throughWalls = throughWalls;
        this.title = title;
        this.type = type;
        this.timer = timer;
    }
}

package fishmod.utils.dungeon.waypoints

class StoredWaypoint() {
    @JvmField var x: Double = 0.0
    @JvmField var y: Double = 0.0
    @JvmField var z: Double = 0.0

    @JvmField var halfX: Double = 0.0
    @JvmField var halfY: Double = 0.0
    @JvmField var halfZ: Double = 0.0

    @JvmField var color: Int = 0
    @JvmField var filled: Boolean = false
    @JvmField var throughWalls: Boolean = false
    @JvmField var title: String? = null

    @JvmField var type: String? = null

    @JvmField var timer: String? = null

    @JvmField var routeId: String? = null

    @JvmField var routeOrder: Int = 0

    @JvmField var message: String? = null

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

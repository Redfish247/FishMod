package fishmod.utils.dungeon.map

/** A cell in the dungeon's 6x6 room grid (half-unit coords 0..10, room cells at even x/z), indexed purely from map pixel data (see MapReader), never world position. Plain class (not data class) with explicit x()/z() so Java call sites keep using record-style accessors. */
class GridPos(private val xCoord: Int, private val zCoord: Int) {

    fun x(): Int = xCoord
    fun z(): Int = zCoord

    fun isRoomCell(): Boolean = (xCoord and 1) == 0 && (zCoord and 1) == 0

    fun offset(dx: Int, dz: Int): GridPos = GridPos(xCoord + dx, zCoord + dz)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridPos) return false
        return xCoord == other.xCoord && zCoord == other.zCoord
    }

    override fun hashCode(): Int = 31 * xCoord + zCoord

    override fun toString(): String = "GridPos[x=$xCoord, z=$zCoord]"
}

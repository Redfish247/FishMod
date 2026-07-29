package fishmod.utils.dungeon.map

/**
 * A cell in the dungeon's fixed 6x6 room grid, in half-unit coordinates (0..10): room-quadrant
 * cells sit at even (x, z); door/connector cells sit at odd/even or even/odd. Unlike earlier
 * versions of this class, grid coordinates are no longer derived from the player's world position
 * at all — the whole grid is indexed purely off the map's own pixel data (see MapReader), matching
 * how Odin (github.com/odtheking/Odin) does it. That sidesteps needing any correspondence between
 * world coordinates and map pixel space, which turned out to be the source of miscalibration.
 *
 * Ported from the original Java `record GridPos(int x, int z)`. Java callers invoke the record-style
 * accessors `.x()` / `.z()` (e.g. DungeonGrid, RoomSignature, MapReader), so this is a plain class
 * with explicit `x()`/`z()` methods rather than a Kotlin data class (whose `val x`/`val z` properties
 * would instead compile to `getX()`/`getZ()`, breaking those call sites) — equals/hashCode/toString
 * are implemented by hand to match record semantics since GridPos is used as a HashMap key.
 */
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

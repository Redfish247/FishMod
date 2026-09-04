package fishmod.features.dungeon.map

/** Immutable 2D integer vector used for dungeon map room/tile coordinates. */
data class MapVec2i(val x: Int, val z: Int) {

    fun add(o: MapVec2i): MapVec2i = MapVec2i(x + o.x, z + o.z)

    fun add(dx: Int, dz: Int): MapVec2i = MapVec2i(x + dx, z + dz)

    fun multiply(f: Int): MapVec2i = MapVec2i(x * f, z * f)

    fun multiply(f: Double): MapVec2i = MapVec2i((x * f).toInt(), (z * f).toInt())

    fun divide(f: Int): MapVec2i = MapVec2i(x / f, z / f)

    fun divide(f: Double): MapVec2i = MapVec2i((x / f).toInt(), (z / f).toInt())

    fun mapIndex(): Int = z * 128 + x

    fun roomListIndex(): Int = x * 6 + z

    /**
     * World (blockX, blockZ) -> roomsList storage index, or -1 when the position is outside the 6x6
     * grid. Room-tile k spans world [-200+32k, -169+32k] (interior + east wall); tile centres are at
     * -185+32k. Shift by half a cell (+16) and floor-divide so every block inside a room maps to that
     * room, not the previous one on the west/north side. Without the per-axis range check an
     * off-grid position aliases onto a wrong in-range index instead of "no room".
     */
    fun index(): Int {
        val tx = Math.floorDiv(x + 201, 32)
        val tz = Math.floorDiv(z + 201, 32)
        return if (tx in 0..5 && tz in 0..5) tx * 6 + tz else -1
    }

    override fun toString(): String = "MapVec2i($x, $z)"
}

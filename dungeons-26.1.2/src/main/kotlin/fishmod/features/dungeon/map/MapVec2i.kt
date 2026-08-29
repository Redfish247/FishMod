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

    fun index(): Int = (x + 185) / 32 * 6 + (z + 185) / 32

    fun roomTilePos(): MapVec2i = this.add(MapVec2i(185, 185)).divide(32)

    override fun toString(): String = "MapVec2i($x, $z)"
}

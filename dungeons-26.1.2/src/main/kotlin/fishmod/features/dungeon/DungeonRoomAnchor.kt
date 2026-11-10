package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.Room
import fishmod.utils.debug.FishDiag
import net.minecraft.core.BlockPos

object DungeonRoomAnchor {

    data class Anchor(val name: String, val rotation: Room.Rotation, val clay: BlockPos)

    @JvmStatic
    fun current(): Anchor? {
        val room = DungeonMap.roomPlayerIn()?.owner ?: return null
        val name = room.data?.name ?: return null
        val clay = room.clayPos ?: run {
            if (room.rotation != Room.Rotation.NONE) FishDiag.fail("DungeonRoomAnchor.1", "room $name has rotation ${room.rotation} but no clay pos")
            return null
        }
        if (room.rotation == Room.Rotation.NONE) return null
        return Anchor(name, room.rotation, clay)
    }

    @JvmStatic
    fun toLocal(a: Anchor, world: BlockPos): BlockPos =
        rotateToNorth(world.x - a.clay.x, world.y, world.z - a.clay.z, a.rotation)

    @JvmStatic
    fun toWorld(a: Anchor, local: BlockPos): BlockPos {
        val r = rotateAroundNorth(local.x, local.y, local.z, a.rotation)
        return BlockPos(r.x + a.clay.x, r.y, r.z + a.clay.z)
    }

    // Sub-block point: block via the lattice formula, offset from the block centre rotated the same way
    @JvmStatic
    fun pointToLocal(a: Anchor, x: Double, y: Double, z: Double): DoubleArray {
        val bx = Math.floor(x); val bz = Math.floor(z)
        val b = toLocal(a, BlockPos(bx.toInt(), Math.floor(y).toInt(), bz.toInt()))
        val (ox, oz) = rotateOffset(x - bx - 0.5, z - bz - 0.5, a.rotation, toWorld = false)
        return doubleArrayOf(b.x + 0.5 + ox, y, b.z + 0.5 + oz)
    }

    @JvmStatic
    fun pointToWorld(a: Anchor, x: Double, y: Double, z: Double): DoubleArray {
        val bx = Math.floor(x); val bz = Math.floor(z)
        val b = toWorld(a, BlockPos(bx.toInt(), Math.floor(y).toInt(), bz.toInt()))
        val (ox, oz) = rotateOffset(x - bx - 0.5, z - bz - 0.5, a.rotation, toWorld = true)
        return doubleArrayOf(b.x + 0.5 + ox, y, b.z + 0.5 + oz)
    }

    /** True when the room is turned 90/270 degrees, so box X/Z extents swap between frames. */
    @JvmStatic
    fun swapsAxes(a: Anchor): Boolean = a.rotation == Room.Rotation.WEST || a.rotation == Room.Rotation.EAST

    private fun rotateOffset(x: Double, z: Double, rot: Room.Rotation, toWorld: Boolean): Pair<Double, Double> = when (rot) {
        Room.Rotation.NORTH -> Pair(-x, -z)
        Room.Rotation.WEST -> if (toWorld) Pair(-z, x) else Pair(z, -x)
        Room.Rotation.EAST -> if (toWorld) Pair(z, -x) else Pair(-z, x)
        else -> Pair(x, z)
    }

    private fun rotateAroundNorth(x: Int, y: Int, z: Int, rot: Room.Rotation): BlockPos = when (rot) {
        Room.Rotation.NORTH -> BlockPos(-x, y, -z)
        Room.Rotation.SOUTH -> BlockPos(x, y, z)
        Room.Rotation.WEST -> BlockPos(-z, y, x)
        Room.Rotation.EAST -> BlockPos(z, y, -x)
        Room.Rotation.NONE -> { FishDiag.fail("DungeonRoomAnchor.2", "toWorld called with unrotated anchor"); BlockPos(x, y, z) }
    }

    private fun rotateToNorth(x: Int, y: Int, z: Int, rot: Room.Rotation): BlockPos = when (rot) {
        Room.Rotation.NORTH -> BlockPos(-x, y, -z)
        Room.Rotation.SOUTH -> BlockPos(x, y, z)
        Room.Rotation.WEST -> BlockPos(z, y, -x)
        Room.Rotation.EAST -> BlockPos(-z, y, x)
        Room.Rotation.NONE -> { FishDiag.fail("DungeonRoomAnchor.3", "toLocal called with unrotated anchor"); BlockPos(x, y, z) }
    }
}

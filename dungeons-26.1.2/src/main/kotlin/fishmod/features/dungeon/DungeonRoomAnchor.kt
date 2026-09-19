package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.Room
import net.minecraft.core.BlockPos

/** Converts world positions to/from a room's canonical frame. Integer math only — rotating a fractional block-centre lands a block off on 90° turns, so callers must floor to [BlockPos] first. */
object DungeonRoomAnchor {

    /** The room the player is standing in, resolved enough to transform coordinates. Null on doorways, boss, unscanned tiles. */
    data class Anchor(val name: String, val rotation: Room.Rotation, val clay: BlockPos)

    @JvmStatic
    fun current(): Anchor? {
        val room = DungeonMap.roomPlayerIn()?.owner ?: return null
        val name = room.data?.name ?: return null
        val clay = room.clayPos ?: return null
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

    private fun rotateAroundNorth(x: Int, y: Int, z: Int, rot: Room.Rotation): BlockPos = when (rot) {
        Room.Rotation.NORTH -> BlockPos(-x, y, -z)
        Room.Rotation.SOUTH -> BlockPos(x, y, z)
        Room.Rotation.WEST -> BlockPos(-z, y, x)
        Room.Rotation.EAST -> BlockPos(z, y, -x)
        Room.Rotation.NONE -> BlockPos(x, y, z)
    }

    private fun rotateToNorth(x: Int, y: Int, z: Int, rot: Room.Rotation): BlockPos = when (rot) {
        Room.Rotation.NORTH -> BlockPos(-x, y, -z)
        Room.Rotation.SOUTH -> BlockPos(x, y, z)
        Room.Rotation.WEST -> BlockPos(z, y, -x)
        Room.Rotation.EAST -> BlockPos(-z, y, x)
        Room.Rotation.NONE -> BlockPos(x, y, z)
    }
}

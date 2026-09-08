package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.Room
import net.minecraft.core.BlockPos

/**
 * Converts world block positions to/from a dungeon room's canonical (north-up, clay-corner origin)
 * frame, so a waypoint placed in one run lands in the right spot on later runs regardless of where
 * the room instance spawned or how it's rotated.
 *
 * Integer block math only — same rotation formulas the Odin puzzle solvers use ([map.Room] /
 * `OdinDungeon`). Rotating a fractional block-*centre* with these formulas lands a block off on 90°
 * rotations, so callers must floor to a [BlockPos] first and re-add the 0.5 centre afterwards.
 */
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

    /** World block -> room-local (north-up, clay origin). */
    @JvmStatic
    fun toLocal(a: Anchor, world: BlockPos): BlockPos =
        rotateToNorth(world.x - a.clay.x, world.y, world.z - a.clay.z, a.rotation)

    /** Room-local block -> world. */
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

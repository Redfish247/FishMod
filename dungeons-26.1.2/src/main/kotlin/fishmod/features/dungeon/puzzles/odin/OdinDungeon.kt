package fishmod.features.dungeon.puzzles.odin

import com.google.gson.annotations.SerializedName
import net.minecraft.core.BlockPos

data class OVec2(val x: Int, val z: Int)

enum class ORotations(val x: Int, val z: Int) {
    NORTH(15, 15),
    SOUTH(-15, -15),
    WEST(15, -15),
    EAST(-15, 15),
    NONE(0, 0);
}

enum class ORoomType {
    BLOOD, CHAMPION, ENTRANCE, FAIRY, NORMAL, PUZZLE, RARE, TRAP
}

enum class ORoomShape(val displayName: String) {
    @SerializedName("Unknown") UNKNOWN("Unknown"),
    @SerializedName("L") L("L"),
    @SerializedName("1x1") S1x1("1x1"),
    @SerializedName("1x2") S2x1("1x2"),
    @SerializedName("1x3") S3x1("1x3"),
    @SerializedName("1x4") S4x1("1x4"),
    @SerializedName("2x2") S2x2("2x2");
}

data class ORoomData(
    val name: String,
    val type: ORoomType,
    val shape: ORoomShape = ORoomShape.UNKNOWN,
)

data class ORoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = OVec2(x, z)
    val blockPos: BlockPos = BlockPos(x, 70, z)
}

data class ORoom(
    var rotation: ORotations = ORotations.NONE,
    var data: ORoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<ORoomComponent>,
) {
    fun getRealCoords(pos: BlockPos): BlockPos =
        rotateAroundNorth(pos, rotation).offset(clayPos.x, 0, clayPos.z)

    val centerPos: BlockPos
        get() = roomComponents.firstOrNull()?.let { BlockPos(it.x, 0, it.z) } ?: BlockPos.ZERO

    val rotationDeg: Int
        get() = when (rotation) {
            ORotations.SOUTH -> 0
            ORotations.WEST -> 90
            ORotations.NORTH -> 180
            ORotations.EAST -> 270
            else -> 0
        }

    fun getRelativeCoords(pos: BlockPos): BlockPos =
        rotateToNorth(pos.subtract(BlockPos(clayPos.x, 0, clayPos.z)), rotation)

    companion object {
        fun rotateAroundNorth(p: BlockPos, rotation: ORotations): BlockPos = when (rotation) {
            ORotations.NORTH -> BlockPos(-p.x, p.y, -p.z)
            ORotations.WEST -> BlockPos(-p.z, p.y, p.x)
            ORotations.SOUTH -> BlockPos(p.x, p.y, p.z)
            ORotations.EAST -> BlockPos(p.z, p.y, -p.x)
            else -> p
        }

        fun rotateToNorth(p: BlockPos, rotation: ORotations): BlockPos = when (rotation) {
            ORotations.NORTH -> BlockPos(-p.x, p.y, -p.z)
            ORotations.WEST -> BlockPos(p.z, p.y, -p.x)
            ORotations.SOUTH -> BlockPos(p.x, p.y, p.z)
            ORotations.EAST -> BlockPos(-p.z, p.y, p.x)
            else -> p
        }
    }
}

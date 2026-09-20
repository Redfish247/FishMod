package fishmod.features.dungeon.map

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object DoorHighlight {

    private const val Y_MIN = 69.0
    private const val Y_MAX = 74.0

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ -> renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, matrices, vc -> render(matrices, vc, depthTested = false, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, matrices, vc -> render(matrices, vc, depthTested = false, fill = false) }
    }

    private fun throughWall(type: Door.Type): Boolean =
        type == Door.Type.WITHER || DungeonMapSettings.mapDoorHighlightThroughWall

    private fun active(): Boolean {
        return DungeonMapSettings.mapDoorHighlightEnabled && DungeonState.isInDungeon()
    }

    private fun facingRoomTile(door: Door): Room.Tile? {
        val player = Minecraft.getInstance().player ?: return null
        val idx = MapVec2i(player.blockX, player.blockZ).index()
        val here = Scan.roomsList.getOrNull(idx)?.owner ?: return null
        return door.rooms.firstOrNull { it.owner === here }
    }

    private fun openable(door: Door): Boolean {
        return when (door.type) {
            Door.Type.BLOOD -> DungeonState.hasBloodKey()
            Door.Type.WITHER -> DungeonState.hasWitherKey()
            else -> false
        }
    }

    private fun lineColor(door: Door): Int {
        val s = DungeonMapSettings
        return if (openable(door)) s.mapDoorOpenableColor
        else when (door.type) {
            Door.Type.BLOOD -> s.mapBloodDoorColor
            Door.Type.WITHER -> s.mapWitherDoorColor
            else -> s.mapNormalDoorColor
        }
    }

    private const val LOCKED_FILL_ALPHA = 0x80

    private const val WITHER_FILL_ALPHA = 0x50

    private fun witherLine(door: Door): Int =
        if (openable(door)) DungeonMapSettings.mapDoorOpenableColor
        else DungeonMapSettings.mapWitherHighlightMissingColor

    private fun witherFill(door: Door): Int =
        (WITHER_FILL_ALPHA shl 24) or (witherLine(door) and 0x00FFFFFF)

    private fun fillColor(door: Door): Int {
        val s = DungeonMapSettings
        return if (openable(door)) s.mapDoorOpenableColorFilled
        else {
            (LOCKED_FILL_ALPHA shl 24) or (lineColor(door) and 0x00FFFFFF)
        }
    }

    private fun box(door: Door): AABB {
        val x = door.pos.x.toDouble()
        val z = door.pos.z.toDouble()
        return AABB(x - 1.0, Y_MIN, z - 1.0, x + 2.0, Y_MAX, z + 2.0).inflate(0.02)
    }

    private fun faceQuad(door: Door, hereTile: Room.Tile): Array<Vec3>? {
        val full = box(door)
        val other = door.rooms.firstOrNull { it !== hereTile } ?: return null
        val dx = hereTile.pos.x - other.pos.x
        val dz = hereTile.pos.z - other.pos.z
        return if (abs(dx) >= abs(dz)) {
            val x = if (dx > 0) full.maxX else full.minX
            arrayOf(
                Vec3(x, full.minY, full.minZ),
                Vec3(x, full.minY, full.maxZ),
                Vec3(x, full.maxY, full.maxZ),
                Vec3(x, full.maxY, full.minZ)
            )
        } else {
            val z = if (dz > 0) full.maxZ else full.minZ
            arrayOf(
                Vec3(full.minX, full.minY, z),
                Vec3(full.maxX, full.minY, z),
                Vec3(full.maxX, full.maxY, z),
                Vec3(full.minX, full.maxY, z)
            )
        }
    }

    private fun renderGizmo() {
        if (!active()) return
        val fullBox = DungeonMapSettings.mapDoorHighlightFullBox
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !door.locked || !door.seen) continue
            if (throughWall(door.type)) continue
            val hereTile = facingRoomTile(door) ?: continue
            if (fullBox) {
                RenderUtils.gizmoBox(box(door), fillColor(door), lineColor(door))
            } else {
                val quad = faceQuad(door, hereTile) ?: continue
                RenderUtils.gizmoQuad(quad, fillColor(door), lineColor(door))
            }
        }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, depthTested: Boolean, fill: Boolean) {
        if (!active()) return
        val fullBox = DungeonMapSettings.mapDoorHighlightFullBox
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !door.locked || !door.seen) continue
            if (throughWall(door.type) == depthTested) continue
            val hereTile = facingRoomTile(door) ?: continue

            val isWither = door.type == Door.Type.WITHER
            val fillC = RenderUtils.toFloats(if (isWither) witherFill(door) else fillColor(door))
            val lineC = RenderUtils.toFloats(if (isWither) witherLine(door) else lineColor(door))
            if (fullBox || isWither) {
                val box = box(door)
                if (fill) RenderUtils.renderFilled(matrices, vc, box, fillC)
                else RenderUtils.renderOutline(matrices, vc, box, lineC)
            } else {
                val quad = faceQuad(door, hereTile) ?: continue
                if (fill) RenderUtils.renderFilledQuad(matrices, vc, quad, fillC)
                else RenderUtils.renderQuadOutline(matrices, vc, quad, lineC)
            }
        }
    }
}

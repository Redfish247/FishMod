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

    // The map marks fairy-coloured doors as unlocked and misses opened ones, so trust the world blocks when loaded.
    private fun closed(door: Door): Boolean {
        val level = Minecraft.getInstance().level ?: return door.locked
        val bp = net.minecraft.core.BlockPos(door.pos.x, 69, door.pos.z)
        if (!level.isLoaded(bp)) return door.locked
        val b = level.getBlockState(bp).block
        return b === net.minecraft.world.level.block.Blocks.COAL_BLOCK || b === net.minecraft.world.level.block.Blocks.RED_TERRACOTTA
    }

    private fun isFairyRoom(r: Room?): Boolean = r != null && (r.type == Room.Type.FAIRY || r.data?.name == "Fairy")

    private fun fairyRoom(door: Door): Room? = door.rooms.firstNotNullOfOrNull { it.owner?.takeIf(::isFairyRoom) }

    private fun isFairyDoor(door: Door): Boolean = fairyRoom(door) != null

    // Fairy door: only while you're standing in a room it connects to (e.g. Waterfall), not just nearby
    private fun visible(door: Door): Boolean {
        fairyRoom(door) ?: return door.seen
        return facingRoomTile(door) != null
    }

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

    private fun outlineOnly(): Boolean = DungeonMapSettings.mapDoorOutlineOnly

    // Outline-only: doors of the room you're in (plus open fairy doors), one colour, no fill.
    private fun outlineDoors(): List<Door> =
        ArrayList(Scan.doors).filter { visible(it) && (facingRoomTile(it) != null || isFairyDoor(it)) }

    // Closed wither/blood doors keep their key colours in outline mode.
    private fun keyDoor(door: Door): Boolean = door.type != Door.Type.NORMAL && closed(door)

    private fun outlineThroughWall(door: Door): Boolean =
        if (keyDoor(door)) throughWall(door.type) else DungeonMapSettings.mapDoorHighlightThroughWall

    private fun keyFill(door: Door): Int = if (door.type == Door.Type.WITHER) witherFill(door) else fillColor(door)

    private fun keyLine(door: Door): Int = if (door.type == Door.Type.WITHER) witherLine(door) else lineColor(door)

    private fun renderGizmo() {
        if (!active()) return
        if (outlineOnly()) {
            for (door in outlineDoors()) {
                if (outlineThroughWall(door)) continue
                if (keyDoor(door)) RenderUtils.gizmoBox(box(door), keyFill(door), keyLine(door))
                else RenderUtils.gizmoBox(box(door), 0, DungeonMapSettings.mapDoorOutlineColor)
            }
            return
        }
        val fullBox = DungeonMapSettings.mapDoorHighlightFullBox
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !visible(door) || !closed(door)) continue
            if (throughWall(door.type)) continue
            val fairy = isFairyDoor(door)
            val hereTile = facingRoomTile(door)
            if (hereTile == null && !fairy) continue
            if (fullBox || fairy || hereTile == null) {
                RenderUtils.gizmoBox(box(door), fillColor(door), lineColor(door))
            } else {
                val quad = faceQuad(door, hereTile) ?: continue
                RenderUtils.gizmoQuad(quad, fillColor(door), lineColor(door))
            }
        }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, depthTested: Boolean, fill: Boolean) {
        if (!active()) return
        if (outlineOnly()) {
            for (door in outlineDoors()) {
                if (outlineThroughWall(door) == depthTested) continue
                val key = keyDoor(door)
                if (fill) {
                    if (key) RenderUtils.renderFilled(matrices, vc, box(door), RenderUtils.toFloats(keyFill(door)))
                } else {
                    val c = if (key) keyLine(door) else DungeonMapSettings.mapDoorOutlineColor
                    RenderUtils.renderOutline(matrices, vc, box(door), RenderUtils.toFloats(c))
                }
            }
            return
        }
        val fullBox = DungeonMapSettings.mapDoorHighlightFullBox
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !visible(door) || !closed(door)) continue
            if (throughWall(door.type) == depthTested) continue
            val fairy = isFairyDoor(door)
            val hereTile = facingRoomTile(door)
            if (hereTile == null && !fairy) continue

            val isWither = door.type == Door.Type.WITHER
            val fillC = RenderUtils.toFloats(if (isWither) witherFill(door) else fillColor(door))
            val lineC = RenderUtils.toFloats(if (isWither) witherLine(door) else lineColor(door))
            if (fullBox || isWither || fairy || hereTile == null) {
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

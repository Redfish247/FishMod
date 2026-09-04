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

/**
 * World-space highlight for already-seen locked doors.
 *
 * BLOOD (and other non-normal) doors draw as occluded vanilla gizmos ([RenderingEvents.GIZMO]) so a
 * wall or terrain in front hides them. WITHER doors instead draw on [RenderingEvents.NO_DEPTH_FILLED]
 * — through walls — since knowing a Wither door is right there (e.g. behind the wall you're facing)
 * is the whole point of tracking Wither keys. Both only draw while the player stands in one of
 * [Door.rooms] — see [facingRoomTile].
 *
 * By default only the door-frame face toward the player's current room is drawn — a genuine flat
 * quad ([faceQuad]), not the whole 3x3x5 box and not a box collapsed to near-zero thickness on one
 * axis: that was tried first and its own front/back faces, a hair apart, z-fought each other and
 * flickered the highlight frame to frame. [DungeonMapSettings.mapDoorHighlightFullBox] opts every
 * type into the full-box silhouette; WITHER doors always use the full box (outline + translucent
 * fill), coloured [mapDoorOpenableColor] once the Wither Key is held ([openable]) /
 * [DungeonMapSettings.mapWitherHighlightMissingColor] until then.
 *
 * A door is "openable" once the player holds the matching key (a Wither Key for a locked WITHER
 * door, the Blood Key for a locked BLOOD door), via [DungeonState.hasWitherKey]/[DungeonState.hasBloodKey].
 * Openable doors use [DungeonMapSettings.mapDoorOpenableColor]/`Filled`; doors still locked without
 * the key fall back to the door's own per-type 2D-map colour ([MapColors] via [Door]).
 */
object DoorHighlight {

    // door frame is 3x3x5; Y_MAX-Y_MIN must be 5 to cover the full frame height, not 4
    private const val Y_MIN = 69.0
    private const val Y_MAX = 74.0

    @JvmStatic
    fun init() {
        // never mix fill and line topologies on one VertexConsumer - line verts as quads render as bowtie garbage
        RenderingEvents.GIZMO.register { _ -> renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, matrices, vc -> render(matrices, vc, depthTested = false, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, matrices, vc -> render(matrices, vc, depthTested = false, fill = false) }
    }

    /** WITHER doors always pierce walls (knowing one is behind you is the point); the config toggle
     *  opts every other highlighted door into the same through-wall treatment. */
    private fun throughWall(type: Door.Type): Boolean =
        type == Door.Type.WITHER || DungeonMapSettings.mapDoorHighlightThroughWall

    private fun active(): Boolean {
        return DungeonMapSettings.mapDoorHighlightEnabled && DungeonState.isInDungeon()
    }

    /** The door's own room-tile on the player's side, or null if the player isn't in either of [Door.rooms]. */
    private fun facingRoomTile(door: Door): Room.Tile? {
        val player = Minecraft.getInstance().player ?: return null
        val idx = MapVec2i(player.blockX, player.blockZ).index()
        val here = Scan.roomsList.getOrNull(idx)?.owner ?: return null
        return door.rooms.firstOrNull { it.owner === here }
    }

    /** "openable" test: locked + the matching key already claimed. */
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

    /** ~50% opacity (0x80/255) for a locked door's flat fill — mapDoorOpenableColorFilled's own alpha (~20%) read as basically invisible. */
    private const val LOCKED_FILL_ALPHA = 0x80

    /** ~31% opacity for the WITHER full-box fill — visible without hiding the room behind the frame. */
    private const val WITHER_FILL_ALPHA = 0x50

    /** WITHER box outline colour: shared openable green once the Wither Key is held, red until then. */
    private fun witherLine(door: Door): Int =
        if (openable(door)) DungeonMapSettings.mapDoorOpenableColor
        else DungeonMapSettings.mapWitherHighlightMissingColor

    private fun witherFill(door: Door): Int =
        (WITHER_FILL_ALPHA shl 24) or (witherLine(door) and 0x00FFFFFF)

    private fun fillColor(door: Door): Int {
        val s = DungeonMapSettings
        return if (openable(door)) s.mapDoorOpenableColorFilled
        else {
            // no per-type "Filled" colour, so reuse the line colour's RGB at a fixed visible alpha
            (LOCKED_FILL_ALPHA shl 24) or (lineColor(door) and 0x00FFFFFF)
        }
    }

    // inflate(0.02) pushes faces just in front of the door blocks so they win the z-fight from the room side
    private fun box(door: Door): AABB {
        val x = door.pos.x.toDouble()
        val z = door.pos.z.toDouble()
        return AABB(x - 1.0, Y_MIN, z - 1.0, x + 2.0, Y_MAX, z + 2.0).inflate(0.02)
    }

    /**
     * The single flat face of [box] nearest [hereTile] — the one the player is actually looking at
     * from their current room — as its 4 corners, wound consistently around the quad. Orientation
     * comes from comparing [hereTile]'s world position against the door's other room-tile: whichever
     * axis (x or z) they differ on is the axis the door frame faces, and the sign of that difference
     * says which side of the box is the near one.
     */
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

    /** Occluded pass: every highlighted door that should NOT pierce walls, via vanilla Gizmos. */
    private fun renderGizmo() {
        if (!active()) return
        val fullBox = DungeonMapSettings.mapDoorHighlightFullBox
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !door.locked || !door.seen) continue
            if (throughWall(door.type)) continue   // through-wall doors draw on the NO_DEPTH pass
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
            // each door renders on exactly one layer: no-depth pass if it pierces walls, depth-tested otherwise
            if (throughWall(door.type) == depthTested) continue
            val hereTile = facingRoomTile(door) ?: continue

            val isWither = door.type == Door.Type.WITHER
            // Wither doors are always the full 3x3x5 frame box, coloured by Wither Key pickup state.
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

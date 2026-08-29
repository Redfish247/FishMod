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
 * World-space highlight for already-seen locked doors, ported from System22's
 * `DoorEsp.drawDoorAuto`/`drawDoor` color-choice logic — but NOT `DoorEsp.java`'s "always visible
 * through every wall" rendering, with one deliberate exception: WITHER doors.
 *
 * `DoorEsp.java` was always visible from anywhere via `NO_DEPTH_TEST`. This port draws BLOOD (and
 * any other non-normal) doors on the depth-tested [RenderingEvents.FILLED_BLOCK] layer instead, so
 * they're properly occluded by any wall or terrain in front of them. WITHER doors specifically draw
 * on [RenderingEvents.NO_DEPTH_FILLED] — through walls — since knowing a Wither door is right there
 * (e.g. behind the wall you're facing) is the whole point of tracking Wither Essence/keys; both are
 * still gated to only draw while the player is standing in one of [Door.rooms] — see
 * [facingRoomTile] — so nothing shows from a room the door isn't even part of.
 *
 * Only the single face of the door frame that faces the player's current room is drawn — a flat
 * quad, not the whole 3x3x5 box — see [faceQuad], rather than the full box silhouette, since the
 * far/side faces of the box were never meant to be visible and just cluttered the view. This is a
 * genuine flat quad, not a box collapsed to near-zero thickness on one axis: the latter was tried
 * first and its own front/back faces — only a hair apart — z-fought each other, flickering the
 * highlight in and out frame to frame instead of rendering consistently.
 *
 * Color logic mirrors `DoorEsp.drawDoorAuto`: a door is "openable" once the player holds the
 * matching key (a Wither Key for a locked WITHER door, the Blood Key for a locked BLOOD door), using
 * [DungeonState.hasWitherKey]/[DungeonState.hasBloodKey] (the chat-tracking half of `DoorEsp.java`
 * that was already ported into [DungeonState]). Openable doors use
 * [DungeonMapSettings.mapDoorOpenableColor]/`Filled`; doors still locked without the key fall back to
 * the door's own already-ported per-type 2D-map color ([MapColors] via [Door]'s BLOOD/WITHER color
 * fields) so this doesn't need the `mapDoorEsp*` fields that were deliberately excluded from
 * [DungeonMapSettings] (that pair belongs to a separate through-wall addon, not this feature).
 */
object DoorHighlight {

    // Door frame is a 3(x) x 3(z) x 5(y) opening; box() already spans x-1..x+2 and z-1..z+2 (3
    // wide each), so Y_MAX-Y_MIN needs to be 5 to cover the whole frame's height, not 4.
    private const val Y_MIN = 69.0
    private const val Y_MAX = 74.0

    @JvmStatic
    fun init() {
        RenderingEvents.FILLED_BLOCK.register { _, matrices, vc -> render(matrices, vc) { it != Door.Type.WITHER } }
        RenderingEvents.NO_DEPTH_FILLED.register { _, matrices, vc -> render(matrices, vc) { it == Door.Type.WITHER } }
    }

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

    /** Same "openable" test as `DoorEsp.drawDoorAuto`: locked + the matching key already claimed. */
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

    private fun fillColor(door: Door): Int {
        val s = DungeonMapSettings
        return if (openable(door)) s.mapDoorOpenableColorFilled
        else {
            // No per-type "Filled" (translucent) color is ported (mapDoorEsp*Filled was intentionally
            // excluded), so reuse the door's own line color's RGB at a fixed, clearly-visible alpha.
            (LOCKED_FILL_ALPHA shl 24) or (lineColor(door) and 0x00FFFFFF)
        }
    }

    // Coincides exactly with the door's own solid blocks, which would z-fight against that same
    // geometry. Inflating slightly pushes every face just in front of the block it highlights, so
    // it wins the fight from the room side while still being properly occluded by any actual
    // wall/terrain further away.
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

    private fun render(matrices: PoseStack, vc: VertexConsumer, typeFilter: (Door.Type) -> Boolean) {
        if (!active()) return
        for (door in ArrayList(Scan.doors)) {
            if (door.type == Door.Type.NORMAL || !typeFilter(door.type) || !door.locked || !door.seen) continue
            val hereTile = facingRoomTile(door) ?: continue
            val quad = faceQuad(door, hereTile) ?: continue
            RenderUtils.renderFilledQuad(matrices, vc, quad, RenderUtils.toFloats(fillColor(door)))
            RenderUtils.renderQuadOutline(matrices, vc, quad, RenderUtils.toFloats(lineColor(door)))
        }
    }
}

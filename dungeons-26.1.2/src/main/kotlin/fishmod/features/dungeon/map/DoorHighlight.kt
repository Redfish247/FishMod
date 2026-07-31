package fishmod.features.dungeon.map

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.world.phys.AABB

/**
 * Legit (depth-tested) world-space highlight for already-seen locked doors, ported from System22's
 * `DoorEsp.drawDoorAuto`/`drawDoor` color-choice logic — but NOT `DoorEsp.java`'s rendering pipeline.
 *
 * The excluded `DoorEsp.java` always rendered with `DepthTestFunction.NO_DEPTH_TEST`, i.e. through
 * walls, which is why it was left out of the earlier dungeon-map port. This feature is intentionally
 * different: it only ever highlights doors where [Door.seen] is already true (the player has
 * legitimately walked into a room touching that door), and it submits to [RenderingEvents.FILLED_BLOCK]
 * (backed by [fishmod.utils.rendering.RenderLayers.FILLED_LAYER]/[fishmod.utils.rendering.RenderLayers.getOutline]
 * with `depthCheck = true`), so normal terrain occludes it exactly like any other block. There is no
 * "legit mode" toggle here because it has no illegit behavior to gate — unlike the 2D map's
 * [DungeonMapSettings.mapLegitMode], which hides *unseen* info.
 *
 * Color logic mirrors `DoorEsp.drawDoorAuto`: a door is "openable" once the player holds the
 * matching key (a Wither Key for a locked WITHER door, the Blood Key for a locked BLOOD door), using
 * [DungeonState.hasWitherKey]/[DungeonState.hasBloodKey] (the chat-tracking half of `DoorEsp.java`
 * that was already ported into [DungeonState]). Openable doors use
 * [DungeonMapSettings.mapDoorOpenableColor]/`Filled`; doors still locked without the key fall back to
 * the door's own already-ported per-type 2D-map color ([MapColors] via [Door]'s BLOOD/WITHER color
 * fields) so this doesn't need the `mapDoorEsp*` fields that were deliberately excluded from
 * [DungeonMapSettings] (that pair belongs to a separate through-wall addon, not this legit feature).
 */
object DoorHighlight {

    private const val Y_MIN = 69.0
    private const val Y_MAX = 73.0

    @JvmStatic
    fun init() {
        // Both the fill and the outline emit QUADS (renderThickOutline draws thin filled boxes for
        // its edges, same as renderFilled's box), so both must go on the same depth-tested layer —
        // mirrors DungeonWaypoints.kt's convention of pairing renderFilled + renderThickOutline on
        // one RenderHandler, just using the depth-tested FILLED_BLOCK layer instead of NO_DEPTH_FILLED.
        RenderingEvents.FILLED_BLOCK.register { _, matrices, vc -> render(matrices, vc) }
    }

    private fun active(): Boolean {
        return DungeonMapSettings.mapDoorHighlightEnabled && DungeonState.isInDungeon()
    }

    private fun eligibleDoors(): List<Door> {
        return ArrayList(Scan.doors).filter { d ->
            d.type != Door.Type.NORMAL && d.locked && d.seen
        }
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

    private fun fillColor(door: Door): Int {
        val s = DungeonMapSettings
        return if (openable(door)) s.mapDoorOpenableColorFilled
        else {
            // No per-type "Filled" (translucent) color is ported (mapDoorEsp*Filled was intentionally
            // excluded), so reuse mapDoorOpenableColorFilled's alpha with the door's own line color's RGB.
            val alpha = (s.mapDoorOpenableColorFilled ushr 24) and 0xFF
            (alpha shl 24) or (lineColor(door) and 0x00FFFFFF)
        }
    }

    private fun box(door: Door): AABB {
        val x = door.pos.x.toDouble()
        val z = door.pos.z.toDouble()
        return AABB(x - 1.0, Y_MIN, z - 1.0, x + 2.0, Y_MAX, z + 2.0)
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!active()) return
        val doors = eligibleDoors()
        if (doors.isEmpty()) return
        val width = DungeonMapSettings.mapDoorHighlightWidth.toDouble()
        for (door in doors) {
            val b = box(door)
            RenderUtils.renderFilled(matrices, vc, b, RenderUtils.toFloats(fillColor(door)))
            RenderUtils.renderThickOutline(matrices, vc, b, RenderUtils.toFloats(lineColor(door)), width * 0.03)
        }
    }
}

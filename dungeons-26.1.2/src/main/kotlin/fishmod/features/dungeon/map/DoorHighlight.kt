package fishmod.features.dungeon.map

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB

/**
 * World-space highlight for already-seen locked doors, ported from System22's
 * `DoorEsp.drawDoorAuto`/`drawDoor` color-choice logic — but NOT `DoorEsp.java`'s "always visible
 * through every wall" rendering.
 *
 * `DoorEsp.java` was always visible from anywhere via `NO_DEPTH_TEST`. This port draws on the
 * depth-tested [RenderingEvents.FILLED_BLOCK] layer instead, so it's properly occluded by any wall
 * or terrain in front of it, and is additionally gated to only draw while the player is standing in
 * one of [Door.rooms] — [visibleFromCurrentRoom].
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

    private const val Y_MIN = 69.0
    private const val Y_MAX = 73.0

    @JvmStatic
    fun init() {
        RenderingEvents.FILLED_BLOCK.register { _, matrices, vc -> render(matrices, vc) }
    }

    private fun active(): Boolean {
        return DungeonMapSettings.mapDoorHighlightEnabled && DungeonState.isInDungeon()
    }

    /** True only while the player is standing in one of this door's own two adjacent rooms. */
    private fun visibleFromCurrentRoom(door: Door): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val idx = MapVec2i(player.blockX, player.blockZ).index()
        val here = Scan.roomsList.getOrNull(idx)?.owner ?: return false
        return door.rooms.any { it.owner === here }
    }

    private fun eligibleDoors(): List<Door> {
        return ArrayList(Scan.doors).filter { d ->
            d.type != Door.Type.NORMAL && d.locked && d.seen && visibleFromCurrentRoom(d)
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

    // Coincides exactly with the door's own solid blocks, which would z-fight against that same
    // geometry. Inflating slightly pushes every face just in front of the block it highlights, so
    // it wins the fight from the room side while still being properly occluded by any actual
    // wall/terrain further away.
    private fun box(door: Door): AABB {
        val x = door.pos.x.toDouble()
        val z = door.pos.z.toDouble()
        return AABB(x - 1.0, Y_MIN, z - 1.0, x + 2.0, Y_MAX, z + 2.0).inflate(0.02)
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

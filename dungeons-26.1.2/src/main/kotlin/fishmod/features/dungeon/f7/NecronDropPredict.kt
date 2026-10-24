package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

// Marks the predicted landing spot: your position pushed along your velocity by a tunable lead, dropped to the floor.
object NecronDropPredict {

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ ->
            if (!Floor7.enableNecronDropPredict || !Location.inDungeon() || !Phase.isInFloor7() || Phase.getPhase() != 8) return@register
            try { render() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.1", "necron drop predict render threw", e) }
        }
    }

    private fun render() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val pos = player.getPosition(mc.deltaTracker.getGameTimeDeltaPartialTick(true))
        val v = player.deltaMovement
        val lead = Floor7.necronDropLeadTicks
        val x = pos.x + v.x * lead + Floor7.necronDropOffsetX
        val z = pos.z + v.z * lead + Floor7.necronDropOffsetZ
        val hit = level.clip(ClipContext(Vec3(x, pos.y, z), Vec3(x, pos.y - 80.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) return
        val y = hit.location.y
        val color = Floor7.necronDropColor
        RenderUtils.gizmoBox(AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 0.05, z + 0.5), color and 0x40FFFFFF, color, true)
    }
}

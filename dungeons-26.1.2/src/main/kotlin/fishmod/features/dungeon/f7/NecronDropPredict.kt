package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

// Necron's drag moves you in a straight line ~DRAG_DZ blocks in z (21 logged runs); the line is read off the first ticks of the drag.
object NecronDropPredict {

    private const val DRAG_TICKS = 50
    private const val DRAG_DZ = 60.0
    private const val START_SPEED = 0.7
    private const val LOCK_DIST = 4.0
    private const val MAX_WAIT_TICKS = 200

    private var phaseTicks = 0
    private var prev: Vec3? = null
    private var start: Vec3? = null
    private var startTick = 0
    private var landing: Vec3? = null
    private var dragTicks = 0

    private fun active() = Floor7.enableNecronDropPredict && Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() in 8..9

    private fun reset() { phaseTicks = 0; prev = null; start = null; landing = null; dragTicks = 0 }

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            try { tick() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.3", "necron drop tick threw", e) }
            false
        }
        RenderingEvents.GIZMO.register { _ ->
            val s = landing ?: return@register
            if (!active() || dragTicks > DRAG_TICKS + 40) return@register
            try {
                val color = Floor7.necronDropColor
                RenderUtils.gizmoBox(AABB(s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 1.8, s.z + 0.3), color and 0x40FFFFFF, color, true)
            } catch (e: Exception) { FishDiag.fail("NecronDropPredict.1", "necron drop predict render threw", e) }
        }
    }

    private fun tick() {
        if (!active()) { reset(); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        val pos = Vec3(p.x, p.y, p.z)
        phaseTicks++
        if (landing != null) { dragTicks++; return }
        val last = prev
        prev = pos
        if (start == null) {
            if (last != null && phaseTicks < MAX_WAIT_TICKS && hypot(pos.x - last.x, pos.z - last.z) > START_SPEED) { start = last; startTick = phaseTicks }
            return
        }
        val s = start ?: return
        val dx = pos.x - s.x
        val dz = pos.z - s.z
        if (hypot(dx, dz) < LOCK_DIST) return
        if (dz < 0.5) { start = null; return }
        val k = DRAG_DZ / dz
        val ex = s.x + dx * k
        val ez = s.z + dz * k
        val hit = level.clip(ClipContext(Vec3(ex, 90.0, ez), Vec3(ex, 40.0, ez), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
        val ey = if (hit.type == HitResult.Type.BLOCK) hit.location.y else 64.0
        landing = Vec3(ex, ey, ez)
        dragTicks = phaseTicks - startTick
        Debug.LOGGER.info("[NecronDrop] start=%.2f,%.2f,%.2f dir=%.2f,%.2f predicted=%.2f,%.2f,%.2f".format(s.x, s.y, s.z, dx, dz, ex, ey, ez))
    }

    @JvmStatic
    fun display(): Boolean = active() && Floor7.enableTickTimers && landing != null && dragTicks <= DRAG_TICKS

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("NecronDropPredict.2", "necron drag timer render threw") {
            RenderUtils.drawPrefixedText(component, context, "Dragged", Constants.DECIMAL_FORMAT.format(dragTicks * Constants.TICK_DURATION))
        }
    }
}

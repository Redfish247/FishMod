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
import kotlin.math.sqrt

// Necron's drag pulls you ~DRAG_LEN blocks (3D) along the direction of its first ticks, wherever you stand (5 logged runs from different spots, 1.6-3.3 blocks off).
object NecronDropPredict {

    private const val DRAG_TICKS = 50
    private const val DRAG_LEN = 64.0
    private const val START_DZ = 0.9
    private const val TARGET_X_MIN = 47.5
    private const val TARGET_X_MAX = 63.5
    private const val TARGET_Z = 112.0
    private const val LOCK_DIST = 6.0
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
            if (last != null && phaseTicks < MAX_WAIT_TICKS && pos.z - last.z > START_DZ) { start = last; startTick = phaseTicks }
            return
        }
        val s = start ?: return
        val dx = pos.x - s.x
        val dz = pos.z - s.z
        if (hypot(dx, dz) < LOCK_DIST) return
        if (dz < 0.5) { start = null; return }
        val dy = pos.y - s.y
        val k = DRAG_LEN / sqrt(dx * dx + dy * dy + dz * dz)
        // The pull aims at a random point in the target box; snap the measured line into the box, then stop short or at the target.
        val aimX = (s.x + dx / dz * (TARGET_Z - s.z)).coerceIn(TARGET_X_MIN, TARGET_X_MAX)
        val aimDx = aimX - s.x
        val aimDz = TARGET_Z - s.z
        val reach = minOf(hypot(dx, dz) * k, hypot(aimDx, aimDz))
        val ex = s.x + aimDx / hypot(aimDx, aimDz) * reach
        val ez = s.z + aimDz / hypot(aimDx, aimDz) * reach
        // The platform floor is uneven (64.06 to 65.0); take the highest top within the box footprint.
        val ey = listOf(-0.3, 0.0, 0.3).flatMap { ox -> listOf(-0.3, 0.0, 0.3).mapNotNull { oz ->
            val h = level.clip(ClipContext(Vec3(ex + ox, 85.0, ez + oz), Vec3(ex + ox, 50.0, ez + oz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
            if (h.type == HitResult.Type.BLOCK) h.location.y else null
        } }.maxOrNull() ?: 64.5
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

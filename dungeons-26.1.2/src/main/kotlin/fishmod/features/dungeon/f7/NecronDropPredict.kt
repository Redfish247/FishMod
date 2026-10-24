package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

// Red player-sized box at the landing predicted when the drag starts: position plus velocity over the drag, dropped to the floor.
object NecronDropPredict {

    private const val WARN_TICKS = 100
    private const val DRAG_START = 3175
    private const val DRAG_END = 3225
    private val timer = TickTimer()
    private var landing: Vec3? = null

    private fun active() = Floor7.enableNecronDropPredict && Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() == 8

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() == 8 },
            resetOn = { Location.inDungeon() }
        )
        RenderingEvents.GIZMO.register { _ ->
            if (!active() || timer.tick < DRAG_START) { landing = null; return@register }
            try { render() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.1", "necron drop predict render threw", e) }
        }
    }

    // Locked in once when the drag starts, never moves afterwards.
    private fun render() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val spot = landing ?: run {
            val v = player.deltaMovement
            val lead = (DRAG_END - timer.tick).coerceAtLeast(0)
            val x = player.x + v.x * lead
            val z = player.z + v.z * lead
            val hit = level.clip(ClipContext(Vec3(x, player.y, z), Vec3(x, player.y - 80.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            if (hit.type != HitResult.Type.BLOCK) return
            Vec3(x, hit.location.y, z).also { landing = it }
        }
        val color = Floor7.necronDropColor
        RenderUtils.gizmoBox(AABB(spot.x - 0.3, spot.y, spot.z - 0.3, spot.x + 0.3, spot.y + 1.8, spot.z + 0.3), color and 0x40FFFFFF, color, true)
    }

    @JvmStatic
    fun display(): Boolean = active() && Floor7.enableTickTimers && timer.tick in (DRAG_START - WARN_TICKS)..DRAG_END

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("NecronDropPredict.2", "necron drag timer render threw") {
            val t = timer.tick
            val label = if (t < DRAG_START) "Drag in" else "Dragged"
            val left = (if (t < DRAG_START) DRAG_START - t else DRAG_END - t) * Constants.TICK_DURATION
            RenderUtils.drawPrefixedText(component, context, label, "%s (t=%s)".format(Constants.DECIMAL_FORMAT.format(left), Constants.DECIMAL_FORMAT.format(t * Constants.TICK_DURATION)))
        }
    }
}

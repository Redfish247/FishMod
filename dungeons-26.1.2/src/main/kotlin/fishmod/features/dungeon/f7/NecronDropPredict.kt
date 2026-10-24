package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Red box at the fixed spot Necron's drag ends (73.5, 6, 47.5 in two logged runs), shown from the platform-to-arena teleport.
object NecronDropPredict {

    private const val DRAG_TICKS = 50
    private const val DROP_Y = 30.0
    private val LANDING = Vec3(73.5, 6.0, 47.5)

    private var dragging = false
    private var dragTicks = 0
    private var lastY = Double.NaN

    private fun active() = Floor7.enableNecronDropPredict && Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() == 8

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (dragging) dragTicks++
            false
        }
        RenderingEvents.GIZMO.register { _ ->
            if (!active()) { dragging = false; dragTicks = 0; lastY = Double.NaN; return@register }
            try { render() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.1", "necron drop predict render threw", e) }
        }
    }

    private fun render() {
        val y = Minecraft.getInstance().player?.y ?: return
        if (!dragging && !lastY.isNaN() && lastY - y > DROP_Y) { dragging = true; dragTicks = 0 }
        lastY = y
        if (!dragging) return
        val color = Floor7.necronDropColor
        val s = LANDING
        RenderUtils.gizmoBox(AABB(s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 1.8, s.z + 0.3), color and 0x40FFFFFF, color, true)
    }

    @JvmStatic
    fun display(): Boolean = active() && Floor7.enableTickTimers && dragging && dragTicks <= DRAG_TICKS

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("NecronDropPredict.2", "necron drag timer render threw") {
            RenderUtils.drawPrefixedText(component, context, "Dragged", Constants.DECIMAL_FORMAT.format(dragTicks * Constants.TICK_DURATION))
        }
    }
}

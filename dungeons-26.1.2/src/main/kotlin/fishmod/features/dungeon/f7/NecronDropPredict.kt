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
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

// Necron's drag moves you in a straight line ~DRAG_DZ blocks in z (21 logged runs); the line is read off the first ticks of the drag.
object NecronDropPredict {

    private const val DRAG_TICKS = 50
    private const val DRAG_DZ = 61.0
    private const val START_SPEED = 0.7
    private const val LOCK_DIST = 6.0
    private const val MAX_WAIT_TICKS = 200

    private var phaseTicks = 0
    private var prev: Vec3? = null
    private var start: Vec3? = null
    private var startTick = 0
    private var landing: Vec3? = null
    private var dragTicks = 0

    // Singleplayer test (/fm necrondrag): replays a logged drag by moving the player along it.
    private var testTick = -1
    private var testLinger = 0
    private var testStart = Vec3.ZERO
    private val TEST_END = Vec3(-9.06, 0.0, 61.65)
    private val TEST_PROFILE = doubleArrayOf(0.0, 0.11, 0.26, 0.40, 0.53, 0.66, 0.78, 0.89, 0.98, 1.0)

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal("necrondrag").executes {
        val p = Minecraft.getInstance().player
        if (p != null) { reset(); testStart = Vec3(p.x, p.y, p.z); testTick = 0 }
        1
    }

    private fun testStep() {
        val p = Minecraft.getInstance().player ?: return
        val i = (testTick / 5).coerceAtMost(TEST_PROFILE.size - 2)
        val f = TEST_PROFILE[i] + (TEST_PROFILE[i + 1] - TEST_PROFILE[i]) * ((testTick % 5) / 5.0)
        p.setPos(testStart.x + TEST_END.x * f, testStart.y, testStart.z + TEST_END.z * f)
        p.deltaMovement = Vec3.ZERO
        testTick++
        if (testTick > 45) {
            testTick = -2; testLinger = 200
            Debug.LOGGER.info("[NecronDrop] test end at=%.2f,%.2f,%.2f".format(p.x, p.y, p.z))
        }
    }

    private fun active() = testTick != -1 || Floor7.enableNecronDropPredict && Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() in 8..9

    private fun reset() { phaseTicks = 0; prev = null; start = null; landing = null; dragTicks = 0 }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (testTick == -2 && --testLinger <= 0) { testTick = -1; reset() }
            if (testTick >= 0) { try { testStep(); tick() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.4", "necron drop test threw", e) } }
        }
        Events.ON_SERVER_TICK.register {
            if (testTick >= 0) return@register false
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
        // The platform floor is uneven (64.06 to 65.0); take the highest top within the box footprint.
        val ey = listOf(-0.3, 0.0, 0.3).flatMap { ox -> listOf(-0.3, 0.0, 0.3).mapNotNull { oz ->
            val h = level.clip(ClipContext(Vec3(ex + ox, p.y + 30.0, ez + oz), Vec3(ex + ox, p.y - 50.0, ez + oz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
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

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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot
import kotlin.math.sqrt
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component

// Necron's drag pulls you ~DRAG_LEN blocks (3D) along the direction of its first ticks (2026-10-26 runs: 56-59 blocks).
object NecronDropPredict {

    private const val DRAG_TICKS = 50
    private const val DRAG_LEN = 58.0
    private const val START_DZ = 0.9
    private const val TARGET_X_MIN = 47.5
    private const val TARGET_X_MAX = 63.5
    private const val TARGET_Z = 112.0
    private const val LOCK_DIST = 6.0
    private const val HIDE_AFTER_STOP_TICKS = 10
    private const val MAX_WAIT_TICKS = 200

    private var phaseTicks = 0
    private var prev: Vec3? = null
    private var start: Vec3? = null
    private var startTick = 0
    private var landing: Vec3? = null
    private var dragTicks = 0
    private var lastMoveTick = 0
    private var dragOver = false
    private var locked = false

    // Singleplayer test (/fm necrondrag [random]): pulls the player to a random point in the target box with the logged speed profile.
    private const val TEST_TICKS = 40
    private const val TARGET_Y = 70.0
    private const val TARGET_Z_MIN = 105.5
    private const val TARGET_Z_MAX = 118.5
    private var testTick = -1
    private var testLinger = 0
    private var testStart = Vec3.ZERO
    private var testTarget = Vec3.ZERO

    private fun testDist(ticks: Int): Double {
        var d = 0.0
        for (n in 0 until ticks) d += if (n < 3) 1.73 else 2.01 - 0.0217 * (n - 3)
        return d
    }

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("necrondrag").executes { startTest(false); 1 }
            .then(ClientCommands.literal("random").executes { startTest(true); 1 })

    private fun startTest(randomStart: Boolean) {
        val p = Minecraft.getInstance().player ?: return
        val r = kotlin.random.Random
        reset()
        testStart = if (randomStart) Vec3(r.nextDouble(28.0, 90.0), r.nextDouble(64.0, 126.0), r.nextDouble(14.0, 48.0)) else Vec3(p.x, p.y, p.z)
        testTarget = Vec3(r.nextDouble(TARGET_X_MIN, TARGET_X_MAX), TARGET_Y, r.nextDouble(TARGET_Z_MIN, TARGET_Z_MAX))
        p.setPos(testStart.x, testStart.y, testStart.z)
        p.setNoGravity(true)
        p.deltaMovement = Vec3.ZERO
        testTick = 0
        Misc.addChatMessage(Component.literal("§b[necrondrag] §7start §f%.1f %.1f %.1f §7target §f%.1f %.1f %.1f".format(testStart.x, testStart.y, testStart.z, testTarget.x, testTarget.y, testTarget.z)))
    }

    private fun testStep() {
        val p = Minecraft.getInstance().player ?: return
        val full = testTarget.subtract(testStart)
        val f = (testDist(testTick + 1) / full.length()).coerceAtMost(1.0)
        p.setPos(testStart.x + full.x * f, testStart.y + full.y * f, testStart.z + full.z * f)
        p.deltaMovement = Vec3.ZERO
        testTick++
        if (testTick >= TEST_TICKS) {
            testTick = -2; testLinger = 200
            p.setNoGravity(false)
            val pred = landing
            val reached = testDist(TEST_TICKS) >= full.length()
            Misc.addChatMessage(Component.literal("§b[necrondrag] §7ended at §f%.1f %.1f %.1f §7(%s)%s".format(p.x, p.y, p.z, if (reached) "reached target" else "fell short",
                if (pred != null) " §7predicted off by §f%.1f §7blocks".format(hypot(pred.x - p.x, pred.z - p.z)) else " §cno prediction")))
        }
    }

    private fun active() = testTick != -1 || Floor7.enableNecronDropPredict && Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() in 8..9

    private fun reset() { phaseTicks = 0; prev = null; start = null; landing = null; dragTicks = 0; lastMoveTick = 0; dragOver = false; locked = false }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (testTick == -2 && --testLinger <= 0) { testTick = -1; reset() }
            if (testTick == -2) { try { tick() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.4", "necron drop test threw", e) } }
            if (testTick >= 0) { try { tick(); testStep() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.4", "necron drop test threw", e) } }
        }
        Events.ON_SERVER_TICK.register {
            if (testTick != -1) return@register false
            try { tick() } catch (e: Exception) { FishDiag.fail("NecronDropPredict.3", "necron drop tick threw", e) }
            false
        }
        RenderingEvents.GIZMO.register { _ ->
            val s = landing ?: return@register
            if (!active() || dragOver) return@register
            try {
                val color = Floor7.necronDropColor
                if (Floor7.necronDropStyle == "Beacon") RenderUtils.gizmoBox(AABB(s.x - 0.15, s.y - 40.0, s.z - 0.15, s.x + 0.15, s.y + 300.0, s.z + 0.15), color and 0x60FFFFFF, 0)
                else RenderUtils.gizmoBox(AABB(s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 1.8, s.z + 0.3), color and 0x40FFFFFF, color, true)
            } catch (e: Exception) { FishDiag.fail("NecronDropPredict.1", "necron drop predict render threw", e) }
        }
    }

    private fun tick() {
        if (!active()) { reset(); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val pos = Vec3(p.x, p.y, p.z)
        phaseTicks++
        if (phaseTicks <= 200) Debug.LOGGER.info("[NecronDropTrace] t=%d pos=%.2f,%.2f,%.2f ground=%s".format(phaseTicks, pos.x, pos.y, pos.z, p.onGround()))
        if (locked) {
            dragTicks++
            val before = prev
            prev = pos
            if (before != null && hypot(pos.x - before.x, pos.z - before.z) > 0.3) lastMoveTick = phaseTicks
            if (phaseTicks - lastMoveTick > HIDE_AFTER_STOP_TICKS) dragOver = true
            return
        }
        val last = prev
        prev = pos
        if (start == null) {
            if (last != null && phaseTicks < MAX_WAIT_TICKS && pos.z - last.z > START_DZ) { start = last; startTick = phaseTicks }
            return
        }
        val s = start ?: return
        val dx = pos.x - s.x
        val dz = pos.z - s.z
        // Show a guess from the first pulled tick, refine it until LOCK_DIST blocks.
        val early = hypot(dx, dz) < LOCK_DIST
        if (dz < 0.5) { start = null; landing = null; return }
        val dy = pos.y - s.y
        val k = DRAG_LEN / sqrt(dx * dx + dy * dy + dz * dz)
        // The pull aims at a random point in the target box; snap the measured line into the box, then stop short or at the target.
        val aimX = (s.x + dx / dz * (TARGET_Z - s.z)).coerceIn(TARGET_X_MIN, TARGET_X_MAX)
        val aimDx = aimX - s.x
        val aimDz = TARGET_Z - s.z
        val reach = minOf(hypot(dx, dz) * k, hypot(aimDx, aimDz))
        val ex = s.x + aimDx / hypot(aimDx, aimDz) * reach
        val ez = s.z + aimDz / hypot(aimDx, aimDz) * reach
        // Height follows how far along the line to the target the pull gets (y 70 only if it reaches).
        val ey = s.y + (TARGET_Y - s.y) * (reach / hypot(aimDx, aimDz))
        landing = Vec3(ex, ey, ez)
        lastMoveTick = phaseTicks
        dragTicks = phaseTicks - startTick
        if (early) return
        locked = true
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

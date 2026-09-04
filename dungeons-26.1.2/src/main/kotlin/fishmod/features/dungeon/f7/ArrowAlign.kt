package fishmod.features.dungeon.f7

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items

/**
 * Arrow Align device solver (F7 P3). The 5x5 grid of arrow item frames sits at fixed world coords
 * (P3 room orientation is constant), so no room transform is needed. Reads each frame's rotation
 * (0-7), matches against the 9 hardcoded target patterns (-1 = slot not in the maze), and renders
 * the remaining click count above each frame.
 */
object ArrowAlign {

    private val CORNER = BlockPos(-2, 120, 75)
    private var clicksRemaining: Map<Int, Int> = emptyMap()
    private var tickAcc = 0

    // After a click, trust our own +1 rotation for ~1s so the count reacts before the next poll.
    private var lastRotations: IntArray? = null
    private val recentClick = HashMap<Int, Long>()

    /**
     * [Phase.inP3] fast path, or a Y-band fallback (100..156) so an unconfigured P3 sim still
     * activates. [solve]'s device-proximity check keeps it scoped.
     */
    private fun inP3(): Boolean =
        Phase.inP3() || (Minecraft.getInstance().player?.let { it.y in 100.0..156.0 } == true)

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.arrowAlignEnabled || !inP3()) { clicksRemaining = emptyMap(); return@register }
            if (++tickAcc < 3) return@register
            tickAcc = 0
            solve(mc)
        }

        // "Stop Wrong Clicks" — cancel rotating an arrow frame that isn't part of the current
        // solution (hold sneak to override).
        UseEntityCallback.EVENT.register(UseEntityCallback { player, _, hand, entity, _ ->
            if (hand != InteractionHand.MAIN_HAND) return@UseEntityCallback InteractionResult.PASS
            if (!FishSettings.arrowAlignEnabled || !inP3()) return@UseEntityCallback InteractionResult.PASS
            if (entity !is ItemFrame || !entity.item.`is`(Items.ARROW)) return@UseEntityCallback InteractionResult.PASS

            val bp = entity.blockPosition()
            if (bp.x != CORNER.x) return@UseEntityCallback InteractionResult.PASS
            val index = (bp.y - CORNER.y) + (bp.z - CORNER.z) * 5
            if (index !in 0..24) return@UseEntityCallback InteractionResult.PASS

            if (FishSettings.arrowAlignBlockWrong && player.isShiftKeyDown.not() &&
                index !in clicksRemaining
            ) return@UseEntityCallback InteractionResult.FAIL

            // Optimistic local rotation so the count updates immediately, but guarded by
            // clicksRemaining>0 so a double-click on an aligned frame can't wrap the count to 7.
            recentClick[index] = System.currentTimeMillis()
            if ((clicksRemaining[index] ?: 0) > 0) {
                lastRotations?.let { it[index] = (it[index] + 1) % 8 }
                Minecraft.getInstance().let { if (it.level != null && it.player != null) solve(it) }
            }
            InteractionResult.PASS
        })

        RenderingEvents.GIZMO.register { _ ->
            if (!FishSettings.arrowAlignEnabled || clicksRemaining.isEmpty() || !inP3()) return@register
            if (Minecraft.getInstance().level == null) return@register
            for ((index, need) in clicksRemaining) {
                if (need <= 0) continue
                val color = if (need < 3) "§2" else if (need < 5) "§6" else "§c"
                val p = framePos(index)
                RenderUtils.gizmoText(
                    Component.literal("$color$need"),
                    net.minecraft.world.phys.Vec3(p.x + 0.5, p.y + 0.6, p.z + 0.5), 1f, -0x1,
                )
            }
        }
    }

    private fun framePos(index: Int): BlockPos = CORNER.offset(0, index % 5, index / 5)

    private fun readRotations(mc: Minecraft): IntArray {
        val byPos = HashMap<Long, Int>()
        for (e in mc.level!!.entitiesForRendering()) {
            if (e !is ItemFrame || !e.item.`is`(Items.ARROW)) continue
            byPos[e.blockPosition().asLong()] = e.rotation
        }
        val now = System.currentTimeMillis()
        val prev = lastRotations
        val out = IntArray(25) { i ->
            val server = byPos[framePos(i).asLong()] ?: -1
            // Trust our optimistic rotation for ~1s after clicking frame i.
            if (prev != null && recentClick[i]?.let { now - it < 1000 } == true && prev[i] != -1) prev[i] else server
        }
        lastRotations = out
        return out
    }

    private fun clicksNeeded(cur: Int, target: Int): Int = (8 - cur + target) % 8

    private fun solve(mc: Minecraft) {
        if (mc.level == null || mc.player == null) return
        // distSqr to the centre block (0,120,77) > 200 — i.e. within ~14 blocks of the device.
        if (mc.player!!.blockPosition().distSqr(BlockPos(0, 120, 77)) > 200.0) {
            clicksRemaining = emptyMap(); return
        }

        val cur = readRotations(mc)
        for (arr in SOLUTIONS) {
            var ok = true
            for (i in arr.indices) {
                if ((arr[i] == -1 || cur[i] == -1) && arr[i] != cur[i]) { ok = false; break }
            }
            if (!ok) continue
            val out = HashMap<Int, Int>()
            for (i in arr.indices) {
                if (cur[i] == -1 || arr[i] == -1) continue
                val n = clicksNeeded(cur[i], arr[i])
                if (n != 0) out[i] = n
            }
            clicksRemaining = out
            return
        }
        clicksRemaining = emptyMap()
    }

    private val SOLUTIONS: List<IntArray> = listOf(
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        intArrayOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        intArrayOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        intArrayOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
    )
}

package fishmod.features.dungeon.f7

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items

/**
 * Arrow Align device solver (F7 P3) — ported from Odin's ArrowAlign. The 5x5 grid of arrow item
 * frames sits at fixed world coords (P3 room orientation is constant), so no room transform is
 * needed. Reads each frame's rotation (0-7), matches against Odin's 9 hardcoded target patterns
 * (-1 = slot not in the maze), and renders the remaining click count above each frame.
 *
 * Poll-based (no optimistic local rotation / packet hook / wrong-click block — those are follow-ups).
 */
object ArrowAlign {

    private val CORNER = BlockPos(-2, 120, 75)
    private var clicksRemaining: Map<Int, Int> = emptyMap()
    private var tickAcc = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.arrowAlignEnabled || !Phase.inP3()) { clicksRemaining = emptyMap(); return@register }
            if (++tickAcc < 3) return@register
            tickAcc = 0
            solve(mc)
        }

        // Text renders in the single END_MAIN pass; pose is already -camera translated there.
        RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!FishSettings.arrowAlignEnabled || clicksRemaining.isEmpty() || !Phase.inP3()) return@register
            if (Minecraft.getInstance().level == null) return@register
            for ((index, need) in clicksRemaining) {
                if (need <= 0) continue
                val color = if (need < 3) "§2" else if (need < 5) "§6" else "§c"
                val p = framePos(index)
                RenderUtils.renderText(ctx, matrices, Component.literal("$color$need"),
                    p.x + 0.5, p.y + 0.6, p.z + 0.5, 0.03f)
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
        return IntArray(25) { byPos[framePos(it).asLong()] ?: -1 }
    }

    private fun clicksNeeded(cur: Int, target: Int): Int = (8 - cur + target) % 8

    private fun solve(mc: Minecraft) {
        if (mc.level == null || mc.player == null) return
        // Only bother when standing near the device.
        if (mc.player!!.position().distanceToSqr(0.0, 120.0, 77.0) > 200.0 * 200.0) { clicksRemaining = emptyMap(); return }

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

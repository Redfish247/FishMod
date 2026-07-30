package fishmod.features.dungeon

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.block.LeverBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Highlights the F7/M7 boss levers with a through-walls filled box (Odin-style waypoint) so you can
 * spot them before reaching the lever room. Each box DISAPPEARS the instant its lever is flipped
 * (the [LeverBlock.POWERED] state flips), since the renderer reads live block state per frame.
 *
 * Render-only — it only reads block state and draws; it never edits the world or sends packets.
 *
 * The scan is player-centred and retried on a short cadence until the levers are located (their
 * chunks can load after you enter the boss), then it stops. Ported from a community addition and
 * rewritten against the current render stack ([RenderingEvents.NO_DEPTH_FILLED]).
 */
object M7LeverWaypoints {

    private const val H_RADIUS = 40     // horizontal scan radius (blocks) around the player
    private const val V_RADIUS = 40     // vertical scan radius (blocks)
    private const val RETRY_TICKS = 20  // re-scan cadence while no levers found yet
    private const val EXPAND = 0.005    // tiny inflate so the box doesn't z-fight the lever

    private val levers: MutableList<BlockPos> = ArrayList()
    private var retry = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(matrices, vc) }
    }

    /** Active only while the feature is on and we're in the F7/M7 boss. */
    private fun active(): Boolean {
        return FishSettings.enableM7LeverWaypoints && Phase.isInFloor7() && Phase.inBoss()
    }

    private fun onTick(mc: MinecraftClient) {
        if (mc.world == null || mc.player == null || !active()) {
            if (levers.isNotEmpty()) levers.clear()
            retry = 0
            return
        }
        // Re-scan until we locate the levers (their chunks may load after entering the boss); stop once found.
        if (levers.isEmpty()) {
            if (retry <= 0) {
                scan(mc.world!!, mc.player!!.blockPos); retry = RETRY_TICKS
            } else retry--
        }
    }

    private fun scan(world: ClientWorld, center: BlockPos) {
        levers.clear()
        val m = BlockPos.Mutable()
        val cx = center.x
        val cy = center.y
        val cz = center.z
        for (x in cx - H_RADIUS..cx + H_RADIUS) {
            for (z in cz - H_RADIUS..cz + H_RADIUS) {
                for (y in cy - V_RADIUS..cy + V_RADIUS) {
                    m.set(x, y, z)
                    if (world.getBlockState(m).block is LeverBlock) {
                        levers.add(m.toImmutable())
                    }
                }
            }
        }
    }

    private fun render(matrices: MatrixStack, vc: VertexConsumer) {
        if (!active() || levers.isEmpty()) return
        val world = MinecraftClient.getInstance().world ?: return

        val c = FishSettings.m7LeverWaypointColor
        val a = (c ushr 24 and 0xFF) / 255f
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val rgba = floatArrayOf(r, g, b, a)

        for (p in levers) {
            val st = world.getBlockState(p)
            if (st.block !is LeverBlock) continue
            if (st.get(LeverBlock.POWERED)) continue   // lever flipped -> box disappears
            val box = Box(
                p.x - EXPAND, p.y - EXPAND, p.z - EXPAND,
                p.x + 1 + EXPAND, p.y + 1 + EXPAND, p.z + 1 + EXPAND
            )
            RenderUtils.renderFilled(matrices, vc, box, rgba)
        }
    }
}

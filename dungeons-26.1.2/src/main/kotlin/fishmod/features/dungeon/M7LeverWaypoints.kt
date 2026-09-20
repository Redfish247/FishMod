package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB

object M7LeverWaypoints {

    private const val EXPAND = 0.005

    private val LEVER_POSITIONS: List<BlockPos> = listOf(
        BlockPos(106, 124, 113), BlockPos(94, 124, 113),
        BlockPos(27, 124, 127), BlockPos(23, 132, 138),
        BlockPos(14, 122, 55), BlockPos(2, 122, 55),
        BlockPos(86, 128, 64), BlockPos(84, 121, 34),
    )

    private val flicked: MutableSet<BlockPos> = HashSet()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
        RenderingEvents.NO_DEPTH_FILLED.register { _, matrices, vc -> render(matrices, vc) }
        AttackBlockCallback.EVENT.register(AttackBlockCallback { _, world, _, pos, _ ->
            if (pos in LEVER_POSITIONS && world.getBlockState(pos).block is LeverBlock) flicked.add(pos.immutable())
            InteractionResult.PASS
        })
    }

    private fun active(): Boolean =
        FishSettings.enableM7LeverWaypoints && Phase.isInFloor7() && Phase.inBoss()

    private fun onTick(mc: Minecraft) {
        if (mc.level == null || mc.player == null || !active()) {
            if (flicked.isNotEmpty()) flicked.clear()
            return
        }
        val world = mc.level!!
        for (p in LEVER_POSITIONS) {
            if (p in flicked) continue
            val st = world.getBlockState(p)
            if (st.block is LeverBlock && st.getValue(LeverBlock.POWERED)) flicked.add(p)
        }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!active()) return
        val world = Minecraft.getInstance().level ?: return

        val c = FishSettings.m7LeverWaypointColor
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val fillA = FishSettings.m7LeverWaypointOpacity.coerceIn(0, 100) / 100f
        val fill = floatArrayOf(r, g, b, fillA)
        val outline = floatArrayOf(r, g, b, 1f)
        val mode = FishSettings.m7LeverWaypointMode

        for (p in LEVER_POSITIONS) {
            if (p in flicked) continue
            val st = world.getBlockState(p)
            if (st.block !is LeverBlock) continue
            if (st.getValue(LeverBlock.POWERED)) continue

            val shape = st.getShape(world, p)
            val box = (if (shape.isEmpty) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else shape.bounds())
                .move(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
                .inflate(EXPAND)

            if (mode != 0) RenderUtils.renderFilled(matrices, vc, box, fill)
            if (mode != 1) RenderUtils.renderThickOutline(matrices, vc, box, outline, 0.02)
        }
    }
}

package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * Configurable highlight on the block you're looking at (ported from NoammAddons' BlockOverlay).
 * Uses FishMod's proven deferred render layers — depth-tested by default, through-walls when
 * "Phase" is on.
 */
object BlockOverlay {

    @JvmStatic
    fun init() {
        RenderingEvents.FILLED_BLOCK.register { _, m, vc -> if (!FishSettings.blockOverlayPhase) render(m, vc) }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.blockOverlayPhase) render(m, vc) }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.blockOverlayEnabled) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (mc.player == null || mc.options.hideGui) return
        val hit = mc.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        if (state.isAir) return

        val shape = state.getShape(level, pos)
        val box = (if (shape.isEmpty) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else shape.bounds())
            .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).inflate(0.002)

        val mode = FishSettings.blockOverlayMode // 0 outline, 1 fill, 2 filled outline
        if (mode != 0) {
            val fill = RenderUtils.toFloats(FishSettings.blockOverlayFillColor)
            fill[3] *= FishSettings.blockOverlayOpacity.coerceIn(0, 100) / 100f
            RenderUtils.renderFilled(matrices, vc, box, fill)
        }
        if (mode != 1) RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(FishSettings.blockOverlayOutlineColor))
    }
}

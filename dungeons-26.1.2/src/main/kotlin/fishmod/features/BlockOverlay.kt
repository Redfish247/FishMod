package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * Configurable highlight on the block you're looking at.
 * Occluded by default (vanilla Gizmos); through-walls when "Phase" is on (NO_DEPTH pass).
 */
object BlockOverlay {

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.blockOverlayPhase) renderGizmo() }
        // Through-walls: fills on the QUADS layer, outlines on the DEBUG_LINES layer — never mix.
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.blockOverlayPhase) renderNoDepth(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.blockOverlayPhase) renderNoDepth(m, vc, fill = false) }
    }

    /** The block the crosshair is on, boxed to its real shape, or null if nothing to draw. */
    private fun targetBox(): AABB? {
        if (!FishSettings.blockOverlayEnabled) return null
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        if (mc.player == null || mc.options.hideGui) return null
        val hit = mc.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        if (state.isAir) return null
        val shape = state.getShape(level, pos)
        return (if (shape.isEmpty) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else shape.bounds())
            .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).inflate(0.002)
    }

    private fun fillArgb(): Int =
        ARGB.multiplyAlpha(FishSettings.blockOverlayFillColor, FishSettings.blockOverlayOpacity.coerceIn(0, 100) / 100f)

    private fun renderGizmo() {
        val box = targetBox() ?: return
        val mode = FishSettings.blockOverlayMode // 0 outline, 1 fill, 2 filled outline
        RenderUtils.gizmoBox(
            box,
            if (mode != 0) fillArgb() else 0,
            if (mode != 1) FishSettings.blockOverlayOutlineColor else 0,
        )
    }

    private fun renderNoDepth(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        val box = targetBox() ?: return
        val mode = FishSettings.blockOverlayMode
        if (fill) {
            if (mode != 0) RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(fillArgb()))
        } else {
            if (mode != 1) RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(FishSettings.blockOverlayOutlineColor))
        }
    }
}

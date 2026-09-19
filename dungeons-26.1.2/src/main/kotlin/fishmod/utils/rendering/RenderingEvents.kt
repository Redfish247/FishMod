package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * World-overlay dispatch: [GIZMO] emits vanilla gizmos (occluded by terrain) from
 * [LevelRenderEvents.BEFORE_GIZMOS]; [NO_DEPTH_FILLED]/[NO_DEPTH_LINE] draw through walls in one
 * [LevelRenderEvents.END_MAIN] pass.
 */
object RenderingEvents {

    /** Emit vanilla gizmos here (via [RenderUtils.gizmoBox] / [RenderUtils.gizmoQuad] / etc.). */
    @JvmField var GIZMO = GizmoHandler()

    @JvmField var NO_DEPTH_FILLED = RenderHandler()
    @JvmField var NO_DEPTH_LINE = RenderHandler()

    @Volatile private var registered = false

    @JvmStatic
    fun init() {
        if (registered) return
        registered = true
        LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { ctx -> gizmos(ctx) })
        LevelRenderEvents.END_MAIN.register(LevelRenderEvents.EndMain { ctx -> render(ctx) })
    }

    private fun gizmos(ctx: LevelRenderContext) {
        if (Minecraft.getInstance().level == null) return
        GIZMO.invoke { it.emit(ctx) }
    }

    private fun render(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val buffers: MultiBufferSource.BufferSource = ctx.bufferSource() ?: return
        val ps: PoseStack = ctx.poseStack() ?: return

        val cam = mc.gameRenderer.mainCamera.position()
        ps.pushPose()
        ps.translate(-cam.x, -cam.y, -cam.z)

        drawLayer(ctx, ps, buffers, RenderLayers.FILL_ND, NO_DEPTH_FILLED)
        drawLayer(ctx, ps, buffers, RenderLayers.LINE_ND, NO_DEPTH_LINE)

        ps.popPose()
    }

    // Only ever called with a single handler — a plain parameter avoids the vararg's per-call array allocation.
    private fun drawLayer(
        ctx: LevelRenderContext, ps: PoseStack, buffers: MultiBufferSource.BufferSource,
        layer: RenderType, handler: RenderHandler,
    ) {
        if (handler.size() == 0) return
        val vc = buffers.getBuffer(layer)
        handler.invoke { it.render(ctx, ps, vc) }
        buffers.endBatch(layer)
    }
}

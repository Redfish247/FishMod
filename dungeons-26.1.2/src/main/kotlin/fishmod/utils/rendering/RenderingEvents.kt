package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

object RenderingEvents {

    @JvmField var GIZMO = SimpleHandler<GizmoEvent>()

    @JvmField var NO_DEPTH_FILLED = SimpleHandler<RenderingEvent>()
    @JvmField var NO_DEPTH_LINE = SimpleHandler<RenderingEvent>()

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
        RenderUtils.clearDeferredFills()
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

        if (RenderUtils.hasDeferredFills()) {
            RenderUtils.flushDeferredFills(ps, buffers.getBuffer(RenderLayers.FILL))
            buffers.endBatch(RenderLayers.FILL)
        }
        drawLayer(ctx, ps, buffers, RenderLayers.FILL_ND, NO_DEPTH_FILLED)
        drawLayer(ctx, ps, buffers, RenderLayers.LINE_ND, NO_DEPTH_LINE)

        ps.popPose()
    }

    private fun drawLayer(
        ctx: LevelRenderContext, ps: PoseStack, buffers: MultiBufferSource.BufferSource,
        layer: RenderType, handler: SimpleHandler<RenderingEvent>,
    ) {
        if (handler.size() == 0) return
        val vc = buffers.getBuffer(layer)
        handler.invoke { it.render(ctx, ps, vc) }
        buffers.endBatch(layer)
        RenderUtils.flushText()
    }
}

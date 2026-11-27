package fishmod.utils.rendering
import fishmod.compat.cam

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

object RenderHooks {

    @JvmField var GIZMO = SimpleHandler<GizmoEvent>()

    @JvmField var NO_DEPTH_FILLED = SimpleHandler<RenderHook>()
    @JvmField var NO_DEPTH_LINE = SimpleHandler<RenderHook>()

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
        try {
            FishDraw.clearDeferredFills()
            GIZMO.invoke { it.emit(ctx) }
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.1", "gizmo pass", t)
        }
    }

    private fun render(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val buffers: MultiBufferSource.BufferSource = FishDiag.notNull(ctx.bufferSource(), "RenderHooks.2") { "level render context has no buffer source" } ?: return
        val ps: PoseStack = FishDiag.notNull(ctx.poseStack(), "RenderHooks.3") { "level render context has no pose stack" } ?: return

        val cam = mc.cam.position()
        ps.pushPose()
        try {
        ps.translate(-cam.x, -cam.y, -cam.z)

        if (FishDraw.hasDeferredFills()) {
            FishDraw.flushDeferredFills(ps, buffers.getBuffer(FishRenderLayers.FILL))
            buffers.endBatch(FishRenderLayers.FILL)
        }
        drawLayer(ctx, ps, buffers, FishRenderLayers.FILL_ND, NO_DEPTH_FILLED)
        FishDraw.quadsToFillLayer = true
        try {
            drawLayer(ctx, ps, buffers, FishRenderLayers.LINE_ND, NO_DEPTH_LINE)
        } finally {
            FishDraw.quadsToFillLayer = false
        }
        if (FishDraw.hasRedirectedQuads()) {
            FishDraw.flushRedirectedQuads(buffers.getBuffer(FishRenderLayers.FILL_ND))
            buffers.endBatch(FishRenderLayers.FILL_ND)
        }
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.4", "world overlay render pass", t)
        } finally {
            ps.popPose()
        }
    }

    private fun drawLayer(
        ctx: LevelRenderContext, ps: PoseStack, buffers: MultiBufferSource.BufferSource,
        layer: RenderType, handler: SimpleHandler<RenderHook>,
    ) {
        if (handler.size() == 0) return
        val vc = buffers.getBuffer(layer)
        handler.invoke { it.render(ctx, ps, vc) }
        buffers.endBatch(layer)
        FishDraw.flushText()
    }
}

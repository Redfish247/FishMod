package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * World-overlay dispatch, rebuilt on the System22 `WaypointTest` pattern (see [RenderLayers]).
 *
 * Everything draws in one [LevelRenderEvents.END_MAIN] pass: translate the pose by `-camera`, grab
 * a [MultiBufferSource.BufferSource] buffer per [RenderType], let every registered handler write to
 * it, then `endBatch` that type immediately. No deferred `submitCustomGeometry`, no per-submission
 * pose snapshots — the handler's `matrixStack` is the live, correctly-translated stack.
 *
 * Handler signature (`RenderingEvent`) is unchanged, so every feature keeps working as-is; the
 * `LevelRenderContext` arg is still passed through for handlers that want camera/level state.
 */
object RenderingEvents {

    @JvmField var FILLED_BLOCK = RenderHandler()
    @JvmField var NO_DEPTH_FILLED = RenderHandler()
    @JvmField var FILLED_ENTITY = RenderHandler()
    @JvmField var OUTLINE_ENTITY = RenderHandler()
    @JvmField var NO_DEPTH_OUTLINE_ENTITY = RenderHandler()
    @JvmField var LINE = RenderHandler()
    @JvmField var NO_DEPTH_LINE = RenderHandler()

    @Volatile private var registered = false

    @JvmStatic
    fun init() {
        if (registered) return
        registered = true
        LevelRenderEvents.END_MAIN.register(LevelRenderEvents.EndMain { ctx -> render(ctx) })
    }

    private fun render(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val buffers: MultiBufferSource.BufferSource = ctx.bufferSource() ?: return
        val ps: PoseStack = ctx.poseStack() ?: return

        val cam = mc.gameRenderer.mainCamera.position()
        ps.pushPose()
        ps.translate(-cam.x, -cam.y, -cam.z)

        // Depth-tested first (occluded by terrain), then through-walls on top.
        drawLayer(ctx, ps, buffers, RenderLayers.FILL, FILLED_BLOCK, FILLED_ENTITY)
        drawLayer(ctx, ps, buffers, RenderLayers.LINE, OUTLINE_ENTITY, LINE)
        drawLayer(ctx, ps, buffers, RenderLayers.FILL_ND, NO_DEPTH_FILLED)
        drawLayer(ctx, ps, buffers, RenderLayers.LINE_ND, NO_DEPTH_OUTLINE_ENTITY, NO_DEPTH_LINE)

        ps.popPose()
    }

    private fun drawLayer(
        ctx: LevelRenderContext, ps: PoseStack, buffers: MultiBufferSource.BufferSource,
        layer: RenderType, vararg handlers: RenderHandler,
    ) {
        if (handlers.all { it.size() == 0 }) return
        val vc = buffers.getBuffer(layer)
        for (h in handlers) h.invoke { it.render(ctx, ps, vc) }
        buffers.endBatch(layer)
    }
}

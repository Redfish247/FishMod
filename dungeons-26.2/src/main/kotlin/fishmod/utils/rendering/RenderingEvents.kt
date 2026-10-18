package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.compat.WorldText
import fishmod.compat.cam
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType

// 26.2 side: layers are recorded while the level collects submits and drawn as custom geometry.
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
        LevelRenderEvents.COLLECT_SUBMITS.register(LevelRenderEvents.CollectSubmits { ctx -> collect(ctx) })
    }

    private fun gizmos(ctx: LevelRenderContext) {
        if (Minecraft.getInstance().level == null) return
        try {
            RenderUtils.clearDeferredFills()
            GIZMO.invoke { it.emit(ctx) }
        } catch (t: Throwable) {
            FishDiag.fail("RenderingEvents.1", "gizmo pass", t)
        }
    }

    private fun cameraStack(): PoseStack {
        val cam = Minecraft.getInstance().cam.position()
        val ps = PoseStack()
        ps.translate(-cam.x, -cam.y, -cam.z)
        return ps
    }

    private fun collect(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val collector = ctx.submitNodeCollector()
        WorldText.collector = collector
        try {
            if (RenderUtils.hasDeferredFills()) {
                val rec = RecordingConsumer()
                RenderUtils.flushDeferredFills(cameraStack(), rec)
                submit(collector, RenderLayers.FILL, rec)
            }
            drawLayer(ctx, collector, RenderLayers.FILL_ND, NO_DEPTH_FILLED)
            RenderUtils.quadsToFillLayer = true
            try {
                drawLayer(ctx, collector, RenderLayers.LINE_ND, NO_DEPTH_LINE)
            } finally {
                RenderUtils.quadsToFillLayer = false
            }
            if (RenderUtils.hasRedirectedQuads()) {
                val rec = RecordingConsumer()
                RenderUtils.flushRedirectedQuads(rec)
                submit(collector, RenderLayers.FILL_ND, rec)
            }
        } catch (t: Throwable) {
            FishDiag.fail("RenderingEvents.4", "world overlay render pass", t)
        } finally {
            WorldText.collector = null
        }
    }

    private fun submit(collector: SubmitNodeCollector, layer: RenderType, rec: RecordingConsumer) {
        if (rec.count == 0) return
        collector.submitCustomGeometry(PoseStack(), layer) { _, vc -> rec.replay(vc) }
    }

    private fun drawLayer(ctx: LevelRenderContext, collector: SubmitNodeCollector, layer: RenderType, handler: SimpleHandler<RenderingEvent>) {
        if (handler.size() == 0) return
        val rec = RecordingConsumer()
        val ps = cameraStack()
        handler.invoke { it.render(ctx, ps, rec) }
        submit(collector, layer, rec)
    }
}

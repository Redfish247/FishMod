package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.compat.WorldText
import fishmod.compat.cam
import fishmod.utils.config.values.Visual
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.Vec3

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
        WorldText.init()
    }

    private fun gizmos(ctx: LevelRenderContext) {
        if (Minecraft.getInstance().level == null) return
        try {
            RenderUtils.clearDeferredFills()
            GIZMO.invoke { it.emit(ctx) }
            if (Visual.gizmoOverlays) gizmoOverlays(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("RenderingEvents.1", "gizmo pass", t)
        }
    }

    // Vanilla gizmos are drawn after terrain and translucent targets, so highlights can't be covered by water, glass or leaves.
    private fun gizmoOverlays(ctx: LevelRenderContext) {
        val ps = PoseStack()
        val fills = RecordingConsumer()
        NO_DEPTH_FILLED.invoke { it.render(ctx, ps, fills) }
        quads(fills, true)
        val lines = RecordingConsumer()
        RenderUtils.quadsToFillLayer = true
        try {
            NO_DEPTH_LINE.invoke { it.render(ctx, ps, lines) }
        } finally {
            RenderUtils.quadsToFillLayer = false
        }
        var i = 0
        while (i + 1 < lines.count) {
            Gizmos.line(Vec3(lines.x(i), lines.y(i), lines.z(i)), Vec3(lines.x(i + 1), lines.y(i + 1), lines.z(i + 1)), lines.color(i), lines.width(i)).setAlwaysOnTop()
            i += 2
        }
        if (RenderUtils.hasRedirectedQuads()) {
            val rq = RecordingConsumer()
            RenderUtils.flushRedirectedQuads(rq)
            quads(rq, true)
        }
        if (RenderUtils.hasDeferredFills()) {
            val rec = RecordingConsumer()
            RenderUtils.flushDeferredFills(ps, rec)
            quads(rec, false)
        }
    }

    private fun quads(rec: RecordingConsumer, onTop: Boolean) {
        var i = 0
        while (i + 3 < rec.count) {
            val p = Gizmos.rect(
                Vec3(rec.x(i), rec.y(i), rec.z(i)), Vec3(rec.x(i + 1), rec.y(i + 1), rec.z(i + 1)),
                Vec3(rec.x(i + 2), rec.y(i + 2), rec.z(i + 2)), Vec3(rec.x(i + 3), rec.y(i + 3), rec.z(i + 3)),
                GizmoStyle.fill(rec.color(i))
            )
            if (onTop) p.setAlwaysOnTop()
            i += 4
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
            if (Visual.gizmoOverlays) return
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
            WorldText.commit(ctx.levelState().cameraRenderState)
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

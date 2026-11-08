package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.compat.WorldText
import fishmod.compat.cam
import fishmod.utils.config.values.VisualSettings
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
object RenderHooks {

    @JvmField var GIZMO = SimpleHandler<GizmoEvent>()

    @JvmField var NO_DEPTH_FILLED = SimpleHandler<RenderHook>()
    @JvmField var NO_DEPTH_LINE = SimpleHandler<RenderHook>()

    @Volatile private var registered = false

    // Recorders are reused across frames; each pass restarts the pool and replay finishes before the next frame's collect
    private val recPool = ArrayList<RecordingConsumer>()
    private var recUsed = 0
    private fun newRec(): RecordingConsumer {
        val r = if (recUsed < recPool.size) recPool[recUsed] else RecordingConsumer().also { recPool.add(it) }
        recUsed++
        r.clear()
        return r
    }

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
        recUsed = 0
        try {
            FishDraw.clearDeferredFills()
            GIZMO.invoke { it.emit(ctx) }
            if (VisualSettings.gizmoOverlays) gizmoOverlays(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.1", "gizmo pass", t)
        }
    }

    // Vanilla gizmos are drawn after terrain and translucent targets, so highlights can't be covered by water, glass or leaves.
    private fun gizmoOverlays(ctx: LevelRenderContext) {
        val ps = PoseStack()
        val fills = newRec()
        NO_DEPTH_FILLED.invoke { it.render(ctx, ps, fills) }
        quads(fills, true)
        val lines = newRec()
        FishDraw.quadsToFillLayer = true
        try {
            NO_DEPTH_LINE.invoke { it.render(ctx, ps, lines) }
        } finally {
            FishDraw.quadsToFillLayer = false
        }
        var i = 0
        while (i + 1 < lines.count) {
            Gizmos.line(Vec3(lines.x(i), lines.y(i), lines.z(i)), Vec3(lines.x(i + 1), lines.y(i + 1), lines.z(i + 1)), lines.color(i), lines.width(i)).setAlwaysOnTop()
            i += 2
        }
        if (FishDraw.hasRedirectedQuads()) {
            val rq = newRec()
            FishDraw.flushRedirectedQuads(rq)
            quads(rq, true)
        }
        if (FishDraw.hasDeferredFills()) {
            val rec = newRec()
            FishDraw.flushDeferredFills(ps, rec)
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
        recUsed = 0
        WorldText.collector = collector
        try {
            if (VisualSettings.gizmoOverlays) return
            if (FishDraw.hasDeferredFills()) {
                val rec = newRec()
                FishDraw.flushDeferredFills(cameraStack(), rec)
                submit(collector, FishRenderLayers.FILL, rec)
            }
            drawLayer(ctx, collector, FishRenderLayers.FILL_ND, NO_DEPTH_FILLED)
            FishDraw.quadsToFillLayer = true
            try {
                drawLayer(ctx, collector, FishRenderLayers.LINE_ND, NO_DEPTH_LINE)
            } finally {
                FishDraw.quadsToFillLayer = false
            }
            if (FishDraw.hasRedirectedQuads()) {
                val rec = newRec()
                FishDraw.flushRedirectedQuads(rec)
                submit(collector, FishRenderLayers.FILL_ND, rec)
            }
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.4", "world overlay render pass", t)
        } finally {
            WorldText.collector = null
            WorldText.commit(ctx.levelState().cameraRenderState)
        }
    }

    private fun submit(collector: SubmitNodeCollector, layer: RenderType, rec: RecordingConsumer) {
        if (rec.count == 0) return
        collector.submitCustomGeometry(PoseStack(), layer) { _, vc -> rec.replay(vc) }
    }

    private fun drawLayer(ctx: LevelRenderContext, collector: SubmitNodeCollector, layer: RenderType, handler: SimpleHandler<RenderHook>) {
        if (handler.size() == 0) return
        val rec = newRec()
        val ps = cameraStack()
        handler.invoke { it.render(ctx, ps, rec) }
        submit(collector, layer, rec)
    }
}

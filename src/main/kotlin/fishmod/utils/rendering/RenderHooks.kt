package fishmod.utils.rendering
//? if <26.2 {
import fishmod.compat.cam
//?}

import com.mojang.blaze3d.vertex.PoseStack
//? if >=26.2 {
/*import fishmod.compat.WorldText
import fishmod.compat.cam
import fishmod.utils.config.values.VisualSettings
*///?}
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource
//?} else {
/*import net.minecraft.client.renderer.SubmitNodeCollector
*///?}
import net.minecraft.client.renderer.rendertype.RenderType
//? if >=26.2 {
/*import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.Vec3
*///?}

object RenderHooks {

    @JvmField var GIZMO = SimpleHandler<GizmoEvent>()

    @JvmField var NO_DEPTH_FILLED = SimpleHandler<RenderHook>()
    @JvmField var NO_DEPTH_LINE = SimpleHandler<RenderHook>()

    @Volatile private var registered = false

    //? if >=26.2 {
    /*private val recPool = ArrayList<RecordingConsumer>()
    private var recUsed = 0
    private fun newRec(): RecordingConsumer {
        val r = if (recUsed < recPool.size) recPool[recUsed] else RecordingConsumer().also { recPool.add(it) }
        recUsed++
        r.clear()
        return r
    }

    *///?}
    @JvmStatic
    fun init() {
        if (registered) return
        registered = true
        LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { ctx -> gizmos(ctx) })
        //? if <26.2 {
        LevelRenderEvents.END_MAIN.register(LevelRenderEvents.EndMain { ctx -> render(ctx) })
        //?} else {
        /*LevelRenderEvents.COLLECT_SUBMITS.register(LevelRenderEvents.CollectSubmits { ctx -> collect(ctx) })
        WorldText.init()
        *///?}
    }

    private fun gizmos(ctx: LevelRenderContext) {
        if (Minecraft.getInstance().level == null) return
        //? if >=26.2 {
        /*recUsed = 0
        *///?}
        try {
            FishDraw.clearDeferredFills()
            GIZMO.invoke { it.emit(ctx) }
            //? if >=26.2 {
            /*if (VisualSettings.gizmoOverlays) gizmoOverlays(ctx)
            *///?}
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.1", "gizmo pass", t)
        }
    }

    //? if <26.2 {
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
    //?} else {
    /*private fun gizmoOverlays(ctx: LevelRenderContext) {
        val ps = PoseStack()
        val fills = newRec()
        NO_DEPTH_FILLED.invoke { it.render(ctx, ps, fills) }
        quads(fills, true)
        val lines = newRec()
    *///?}
        FishDraw.quadsToFillLayer = true
        try {
            //? if <26.2 {
            drawLayer(ctx, ps, buffers, FishRenderLayers.LINE_ND, NO_DEPTH_LINE)
            //?} else {
            /*NO_DEPTH_LINE.invoke { it.render(ctx, ps, lines) }
            *///?}
        } finally {
            FishDraw.quadsToFillLayer = false
        }
        //? if >=26.2 {
        /*var i = 0
        while (i + 1 < lines.count) {
            Gizmos.line(Vec3(lines.x(i), lines.y(i), lines.z(i)), Vec3(lines.x(i + 1), lines.y(i + 1), lines.z(i + 1)), lines.color(i), lines.width(i)).setAlwaysOnTop()
            i += 2
        }
        *///?}
        if (FishDraw.hasRedirectedQuads()) {
            //? if <26.2 {
            FishDraw.flushRedirectedQuads(buffers.getBuffer(FishRenderLayers.FILL_ND))
            buffers.endBatch(FishRenderLayers.FILL_ND)
            //?} else {
            /*val rq = newRec()
            FishDraw.flushRedirectedQuads(rq)
            quads(rq, true)
            *///?}
        }
        //? if >=26.2 {
        /*if (FishDraw.hasDeferredFills()) {
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
        *///?}
        } catch (t: Throwable) {
            FishDiag.fail("RenderHooks.4", "world overlay render pass", t)
        } finally {
            //? if <26.2 {
            ps.popPose()
            //?} else {
            /*WorldText.collector = null
            WorldText.commit(ctx.levelState().cameraRenderState)
            *///?}
        }
    }

    //? if <26.2 {
    private fun drawLayer(
        ctx: LevelRenderContext, ps: PoseStack, buffers: MultiBufferSource.BufferSource,
        layer: RenderType, handler: SimpleHandler<RenderHook>,
    ) {
    //?} else {
    /*private fun submit(collector: SubmitNodeCollector, layer: RenderType, rec: RecordingConsumer) {
        if (rec.count == 0) return
        collector.submitCustomGeometry(PoseStack(), layer) { _, vc -> rec.replay(vc) }
    }

    private fun drawLayer(ctx: LevelRenderContext, collector: SubmitNodeCollector, layer: RenderType, handler: SimpleHandler<RenderHook>) {
    *///?}
        if (handler.size() == 0) return
        //? if <26.2 {
        val vc = buffers.getBuffer(layer)
        handler.invoke { it.render(ctx, ps, vc) }
        buffers.endBatch(layer)
        FishDraw.flushText()
        //?} else {
        /*val rec = newRec()
        val ps = cameraStack()
        handler.invoke { it.render(ctx, ps, rec) }
        submit(collector, layer, rec)
        *///?}
    }
}

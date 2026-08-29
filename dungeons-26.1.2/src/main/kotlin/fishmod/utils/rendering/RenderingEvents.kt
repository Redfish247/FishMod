package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.phys.Vec3

object RenderingEvents {

    @JvmField
    var FILLED_BLOCK = RenderHandler()
    @JvmField
    var NO_DEPTH_FILLED = RenderHandler()
    @JvmField
    var FILLED_ENTITY = RenderHandler()
    @JvmField
    var OUTLINE_ENTITY = RenderHandler()
    @JvmField
    var NO_DEPTH_OUTLINE_ENTITY = RenderHandler()
    @JvmField
    var LINE = RenderHandler()

    /** Like LINE, but through walls (no depth test) — a true GL_LINES layer, unlike NO_DEPTH_FILLED's triangle-strip box layer. */
    @JvmField
    var NO_DEPTH_LINE = RenderHandler()

    /**
     * All 7 handlers submit their geometry on [LevelRenderEvents.COLLECT_SUBMITS] — not
     * BEFORE_GIZMOS/AFTER_TRANSLUCENT_FEATURES, despite those sounding more apt by name. The
     * engine flushes the solid/translucent custom-geometry submission buckets (via
     * FeatureRenderDispatcher.renderSolidFeatures()/renderTranslucentFeatures(), inside
     * CustomFeatureRenderer.renderSolid()/renderTranslucent()) *before* either of those two
     * events fire, so anything submitted from them arrives one flush too late and is silently
     * dropped every frame — no exception, no gate failure, nothing ever drawn. COLLECT_SUBMITS
     * fires earlier in the frame, before that flush, which is the only point these submissions
     * actually get picked up.
     */
    @JvmStatic
    fun init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::filled)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::filledNoDepth)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::entityFilled)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::entityOutline)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::entityOutlineNoDepth)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::debugLine)
        LevelRenderEvents.COLLECT_SUBMITS.register(this::debugLineNoDepth)
    }

    /**
     * `submitCustomGeometry`'s renderer callback runs *later*, when the engine flushes the
     * solid/translucent custom-geometry buckets — not synchronously here. Its `pose` parameter is
     * a correctly-snapshotted copy taken at submission time for exactly this reason. The 7
     * handlers below used to ignore that parameter and close over the shared, mutable
     * `context.poseStack()` instead — which had already had `popPose()` called on it (right after
     * submission, well before the deferred callback ever runs) and could be in any arbitrary state
     * by the time rendering actually happened. That produced no exception (the pose is always a
     * valid transform, just the wrong one) and matched every symptom seen: geometry submitted with
     * correct world data, camera/gate checks all passing, nothing ever appearing on screen. Fix is
     * to build a fresh PoseStack seeded from the deferred `pose` and hand that down instead.
     */
    private fun poseStackFrom(pose: PoseStack.Pose): PoseStack {
        val stack = PoseStack()
        stack.last().set(pose)
        return stack
    }

    private fun filled(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER) { pose, consumer ->
            val local = poseStackFrom(pose)
            FILLED_BLOCK.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun filledNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER_NO_DEPTH) { pose, consumer ->
            val local = poseStackFrom(pose)
            NO_DEPTH_FILLED.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun entityFilled(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_ENTITY_LAYER) { pose, consumer ->
            val local = poseStackFrom(pose)
            FILLED_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun entityOutline(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true)) { pose, consumer ->
            val local = poseStackFrom(pose)
            OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun entityOutlineNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, false)) { pose, consumer ->
            val local = poseStackFrom(pose)
            NO_DEPTH_OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun debugLine(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true)) { pose, consumer ->
            val local = poseStackFrom(pose)
            LINE.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }

    private fun debugLineNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, false)) { pose, consumer ->
            val local = poseStackFrom(pose)
            NO_DEPTH_LINE.invoke { renderingEvent -> renderingEvent.render(context, local, consumer) }
        }
        matrices.popPose()
    }
}

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

    @JvmStatic
    fun init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(this::filled)
        LevelRenderEvents.BEFORE_GIZMOS.register(this::filledNoDepth)
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(this::entityFilled)
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(this::entityOutline)
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(this::entityOutlineNoDepth)
        LevelRenderEvents.BEFORE_GIZMOS.register(this::debugLine)
        LevelRenderEvents.BEFORE_GIZMOS.register(this::debugLineNoDepth)
    }

    private fun filled(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER) { _, consumer ->
            FILLED_BLOCK.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun filledNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER_NO_DEPTH) { _, consumer ->
            NO_DEPTH_FILLED.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun entityFilled(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_ENTITY_LAYER) { _, consumer ->
            FILLED_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun entityOutline(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true)) { _, consumer ->
            OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun entityOutlineNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, false)) { _, consumer ->
            NO_DEPTH_OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun debugLine(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true)) { _, consumer ->
            LINE.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }

    private fun debugLineNoDepth(context: LevelRenderContext) {
        if (context.levelState() == null) return
        val camera: Vec3 = context.levelState().cameraRenderState.pos
        val matrices: PoseStack = context.poseStack() ?: return
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, false)) { _, consumer ->
            NO_DEPTH_LINE.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        }
        matrices.popPose()
    }
}

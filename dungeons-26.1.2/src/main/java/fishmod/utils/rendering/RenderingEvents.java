package fishmod.utils.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.Vec3;

public class RenderingEvents {

    public static RenderHandler FILLED_BLOCK = new RenderHandler();
    public static RenderHandler NO_DEPTH_FILLED = new RenderHandler();
    public static RenderHandler FILLED_ENTITY = new RenderHandler();
    public static RenderHandler OUTLINE_ENTITY = new RenderHandler();
    public static RenderHandler NO_DEPTH_OUTLINE_ENTITY = new RenderHandler();
    public static RenderHandler LINE = new RenderHandler();


    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(RenderingEvents::filled);
        LevelRenderEvents.BEFORE_GIZMOS.register(RenderingEvents::filledNoDepth);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(RenderingEvents::entityFilled);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(RenderingEvents::entityOutline);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(RenderingEvents::entityOutlineNoDepth);
        LevelRenderEvents.BEFORE_GIZMOS.register(RenderingEvents::debugLine);

    }

    private static void filled(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER, (pose, consumer) ->
                FILLED_BLOCK.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }

    private static void filledNoDepth(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_LAYER_NO_DEPTH, (pose, consumer) ->
                NO_DEPTH_FILLED.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }

    private static void entityFilled(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.FILLED_ENTITY_LAYER, (pose, consumer) ->
                FILLED_ENTITY.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }


    private static void entityOutline(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true), (pose, consumer) ->
                OUTLINE_ENTITY.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }

    private static void entityOutlineNoDepth(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, false), (pose, consumer) ->
                NO_DEPTH_OUTLINE_ENTITY.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }

    private static void debugLine(LevelRenderContext context) {
        if (context.levelState() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        context.submitNodeCollector().submitCustomGeometry(matrices, RenderLayers.getOutline(4, true), (pose, consumer) ->
                LINE.invoke(renderingEvent -> renderingEvent.render(context, matrices, consumer)));
        matrices.popPose();
    }

}

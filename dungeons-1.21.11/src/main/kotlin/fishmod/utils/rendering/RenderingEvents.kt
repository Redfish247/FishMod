package fishmod.utils.rendering

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.render.VertexConsumer
import net.minecraft.util.math.Vec3d

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
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::filled)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::filledNoDepth)
        WorldRenderEvents.AFTER_ENTITIES.register(this::entityFilled)
        WorldRenderEvents.AFTER_ENTITIES.register(this::entityOutline)
        WorldRenderEvents.AFTER_ENTITIES.register(this::entityOutlineNoDepth)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::debugLine)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::debugLineNoDepth)
    }

    private fun filled(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.FILLED_LAYER)

        FILLED_BLOCK.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun filledNoDepth(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.FILLED_LAYER_NO_DEPTH)

        NO_DEPTH_FILLED.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun entityFilled(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.FILLED_ENTITY_LAYER)

        FILLED_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun entityOutline(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.getOutline(4, true))

        OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun entityOutlineNoDepth(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.getOutline(4, false))

        NO_DEPTH_OUTLINE_ENTITY.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun debugLine(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.getOutline(4, true))

        LINE.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }

    private fun debugLineNoDepth(context: WorldRenderContext) {
        if (context.worldState() == null) return
        val camera: Vec3d = context.worldState().cameraRenderState.pos
        val matrices = context.matrices() ?: return
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)

        val consumers = context.consumers() ?: return
        val consumer: VertexConsumer = consumers.getBuffer(RenderLayers.getOutline(4, false))

        NO_DEPTH_LINE.invoke { renderingEvent -> renderingEvent.render(context, matrices, consumer) }
        matrices.pop()
    }
}

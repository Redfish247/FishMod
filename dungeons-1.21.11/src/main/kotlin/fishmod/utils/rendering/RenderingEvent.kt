package fishmod.utils.rendering

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack

fun interface RenderingEvent {
    fun render(context: WorldRenderContext, matrixStack: MatrixStack, consumer: VertexConsumer)
}

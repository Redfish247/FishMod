package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

fun interface RenderingEvent {
    fun render(context: LevelRenderContext, matrixStack: PoseStack, consumer: VertexConsumer)
}

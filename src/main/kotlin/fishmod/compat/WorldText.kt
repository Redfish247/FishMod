package fishmod.compat

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component

object WorldText {
    private val buffers: MultiBufferSource.BufferSource = MultiBufferSource.immediate(ByteBufferBuilder(1536))

    fun draw(ps: PoseStack, text: Component, argb: Int, shadow: Boolean) {
        val font = Minecraft.getInstance().font
        font.drawInBatch(text, -font.width(text) / 2f, 0f, argb, shadow, ps.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 15728880)
    }

    fun flush() = buffers.endBatch()
}

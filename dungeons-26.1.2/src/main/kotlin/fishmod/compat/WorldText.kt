package fishmod.compat

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component

// 26.1 side: immediate-mode see-through billboard text; the 26.2 module overlays this with the submit-node path.
object WorldText {
    private val buffers: MultiBufferSource.BufferSource = MultiBufferSource.immediate(ByteBufferBuilder(1536))

    // `ps` is already translated, rotated and scaled; text is centred on x = 0.
    fun draw(ps: PoseStack, text: Component, argb: Int, shadow: Boolean) {
        val font = Minecraft.getInstance().font
        font.drawInBatch(text, -font.width(text) / 2f, 0f, argb, shadow, ps.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 15728880)
    }

    fun flush() = buffers.endBatch()
}

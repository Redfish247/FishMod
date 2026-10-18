package fishmod.compat

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.network.chat.Component

// 26.2 side: text is submitted to the level's node collector while the render events collect.
object WorldText {
    @JvmField var collector: SubmitNodeCollector? = null

    fun draw(ps: PoseStack, text: Component, argb: Int, shadow: Boolean) {
        val c = collector ?: return
        val font = Minecraft.getInstance().font
        c.submitText(ps, -font.width(text) / 2f, 0f, text.visualOrderText, shadow, Font.DisplayMode.SEE_THROUGH, 15728880, argb, 0, 0)
    }

    fun flush() {}
}

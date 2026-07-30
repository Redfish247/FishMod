package fishmod.utils.rendering.drawevents

import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack

fun interface SlotEvent {

    fun draw(drawContext: DrawContext, item: ItemStack, x: Int, y: Int)

}

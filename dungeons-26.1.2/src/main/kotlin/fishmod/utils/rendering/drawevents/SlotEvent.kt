package fishmod.utils.rendering.drawevents

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

fun interface SlotEvent {

    fun draw(drawContext: GuiGraphicsExtractor, item: ItemStack, x: Int, y: Int)

}

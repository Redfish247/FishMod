package fishmod.features

import fishmod.features.item.ItemRarity
import fishmod.features.item.ItemRarityHolder
import fishmod.utils.config.values.Visual
import fishmod.utils.rendering.DrawEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Draws a rarity-tinted sprite behind every item; rarity is parsed once and cached per ItemStack. */
object ItemRarityHotbar {

    private val SQUARE: Identifier = Identifier.fromNamespaceAndPath("fishmod", "rarity-background")
    private val CIRCLE: Identifier = Identifier.fromNamespaceAndPath("fishmod", "rarity-background-circle")

    private const val TINT_ALPHA = 0xFF
    private const val DESATURATE = 0.55f // blend raw rarity color halfway toward grey so it's a subtle hint

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_BEFORE.register(::drawRarity)
    }

    @JvmStatic
    fun drawRarity(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (!Visual.itemRarityBackground || stack == null || stack.isEmpty) return

        val holder = stack as ItemRarityHolder
        if (!holder.`fishmod$hasScanned`()) holder.`fishmod$setItemRarity`(getRarity(stack))
        if (!holder.`fishmod$hasItemRarity`()) return

        val sprite = if (Visual.circularRarityBackground) CIRCLE else SQUARE
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, getTintColor(holder.`fishmod$getItemRarity`()))
    }

    private fun getTintColor(rarity: ItemRarity): Int {
        val color = rarity.color
        var red = (color shr 16) and 0xff
        var green = (color shr 8) and 0xff
        var blue = color and 0xff
        val grey = (red + green + blue) / 3

        red = desaturate(red, grey)
        green = desaturate(green, grey)
        blue = desaturate(blue, grey)

        return (TINT_ALPHA shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun desaturate(channel: Int, grey: Int): Int =
        Math.round(channel + (grey - channel) * DESATURATE)

    @JvmStatic
    fun getRarity(stack: ItemStack): ItemRarity {
        val lore = stack.get(DataComponents.LORE) ?: return ItemRarity.NONE
        val lines = lore.lines()
        if (lines.isEmpty()) return ItemRarity.NONE
        for (i in lines.indices.reversed()) {
            for (word in lines[i].string.split(" ")) {
                try { return ItemRarity.valueOf(word) } catch (ignored: IllegalArgumentException) {}
            }
        }
        return ItemRarity.NONE
    }
}

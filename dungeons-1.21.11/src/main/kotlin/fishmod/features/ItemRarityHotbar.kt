package fishmod.features

import fishmod.features.item.ItemRarity
import fishmod.features.item.ItemRarityHolder
import fishmod.utils.config.values.Visual
import fishmod.utils.rendering.DrawEvents
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

/** Rarity background — draws a tinted sprite (square or circle) BEHIND every item, coloured by its SkyBlock rarity. */
object ItemRarityHotbar {

    private val SQUARE: Identifier = Identifier.of("fishmod", "rarity-background")
    private val CIRCLE: Identifier = Identifier.of("fishmod", "rarity-background-circle")

    // The raw rarity colors are very bright/saturated. Tone them WAY down so the backing is a subtle
    // hint rather than a glaring block: drop to a low alpha and blend halfway toward grey.
    private const val TINT_ALPHA = 0xFF     // ~100% opacity
    private const val DESATURATE = 0.55f    // 0 = full color, 1 = grey

    /** Inventory coverage — the slot-before event fires for every rendered inventory slot. */
    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_BEFORE.register(::drawRarity)
    }

    /** Shared draw: parse (cached) rarity and blit the tinted sprite behind the 16x16 item icon. */
    @JvmStatic
    fun drawRarity(ctx: DrawContext, stack: ItemStack?, x: Int, y: Int) {
        if (!Visual.itemRarityBackground || stack == null || stack.isEmpty) return

        val holder = stack as ItemRarityHolder
        if (!holder.`fishmod$hasScanned`()) holder.`fishmod$setItemRarity`(getRarity(stack))
        if (!holder.`fishmod$hasItemRarity`()) return

        val sprite = if (Visual.circularRarityBackground) CIRCLE else SQUARE
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, getTintColor(holder.`fishmod$getItemRarity`()))
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

    /** Reads the rarity keyword from the last lore lines (e.g. "LEGENDARY DUNGEON SWORD"). */
    @JvmStatic
    fun getRarity(stack: ItemStack): ItemRarity {
        val lore = stack.get(DataComponentTypes.LORE) ?: return ItemRarity.NONE
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

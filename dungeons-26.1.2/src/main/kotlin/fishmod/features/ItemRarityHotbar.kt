package fishmod.features

import fishmod.features.item.ItemRarity
import fishmod.features.item.ItemRarityHolder
import fishmod.utils.config.values.Visual
import fishmod.utils.data.TextUtil
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

    /** Hypixel's per-rarity RGB. */
    private val HYPIXEL: Map<ItemRarity, Int> = mapOf(
        ItemRarity.COMMON to 0xFFFFFF,
        ItemRarity.UNCOMMON to 0x21FF2A,
        ItemRarity.RARE to 0x459BFF,
        ItemRarity.EPIC to 0xA335EE,
        ItemRarity.LEGENDARY to 0xFFA216,
        ItemRarity.MYTHIC to 0xFF55FF,
        ItemRarity.DIVINE to 0x55FFFF,
        ItemRarity.SPECIAL to 0xFF5555,
        ItemRarity.VERY_SPECIAL to 0xD13228,
        ItemRarity.ULTIMATE to 0xD13228,
        ItemRarity.ADMIN to 0xD13228,
    )

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
        val rgb = if (Visual.itemRarityHypixelColors) HYPIXEL[rarity] ?: (rarity.color and 0xFFFFFF)
                  else rarity.color and 0xFFFFFF
        val alpha = Visual.itemRarityOpacity.coerceIn(0, 100) * 255 / 100
        return (alpha shl 24) or rgb
    }

    // pets carry no lore rarity line — it's the colour code after the "[Lvl N]" prefix, e.g. "§7[Lvl 100] §6Golden Dragon"
    private val PET_NAME = Regex("\\[Lvl \\d+](?: §8\\[[^\\]]*])? §([0-9a-f])")
    private val PET_COLOR: Map<Char, ItemRarity> = mapOf(
        'f' to ItemRarity.COMMON, 'a' to ItemRarity.UNCOMMON, '9' to ItemRarity.RARE,
        '5' to ItemRarity.EPIC, '6' to ItemRarity.LEGENDARY, 'd' to ItemRarity.MYTHIC, 'b' to ItemRarity.DIVINE,
    )

    @JvmStatic
    fun getRarity(stack: ItemStack): ItemRarity {
        stack.get(DataComponents.LORE)?.lines()?.let { lines ->
            for (i in lines.indices.reversed()) {
                for (word in lines[i].string.split(" ")) {
                    try { return ItemRarity.valueOf(word) } catch (ignored: IllegalArgumentException) {}
                }
            }
        }
        PET_NAME.find(TextUtil.orderedTextToString(stack.hoverName.visualOrderText))
            ?.let { PET_COLOR[it.groupValues[1][0]] }?.let { return it }
        return ItemRarity.NONE
    }
}

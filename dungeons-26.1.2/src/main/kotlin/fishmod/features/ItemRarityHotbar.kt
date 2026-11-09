package fishmod.features

import fishmod.utils.debug.FishDiag
import fishmod.features.item.RarityTier
import fishmod.features.item.RarityTierCache
import fishmod.utils.config.values.VisualSettings
import fishmod.utils.data.TextTools
import fishmod.utils.rendering.DrawHooks
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

object ItemRarityHotbar {

    private val SQUARE: Identifier = Identifier.fromNamespaceAndPath("fishmod", "rarity-background")
    private val CIRCLE: Identifier = Identifier.fromNamespaceAndPath("fishmod", "rarity-background-circle")

    private val HYPIXEL: Map<RarityTier, Int> = mapOf(
        RarityTier.COMMON to 0xFFFFFF,
        RarityTier.UNCOMMON to 0x21FF2A,
        RarityTier.RARE to 0x459BFF,
        RarityTier.EPIC to 0xA335EE,
        RarityTier.LEGENDARY to 0xFFA216,
        RarityTier.MYTHIC to 0xFF55FF,
        RarityTier.DIVINE to 0x55FFFF,
        RarityTier.SPECIAL to 0xFF5555,
        RarityTier.VERY_SPECIAL to 0xD13228,
        RarityTier.ULTIMATE to 0xD13228,
        RarityTier.ADMIN to 0xD13228,
    )

    @JvmStatic
    fun init() {
        DrawHooks.INVENTORY_SLOT_BEFORE.register(::drawRarity)
    }

    // Item List panel draws its own tint, so the global one is skipped for its icons
    @JvmField var suppress = false

    @JvmStatic
    fun drawRarity(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (suppress || !VisualSettings.rarityBackground || fishmod.compat.BladeCompat.active || stack == null || stack.isEmpty) return

        val holder = stack as? RarityTierCache
        if (holder == null) {
            FishDiag.fail("ItemRarityHotbar.1", "ItemStack missing RarityTierCache mixin: ${stack.javaClass.name}")
            return
        }
        try {
            if (!holder.`fishmod$hasScanned`()) holder.`fishmod$setItemRarity`(getRarity(stack))
            if (!holder.`fishmod$hasItemRarity`()) return

            val sprite = if (VisualSettings.rarityBgCircle) CIRCLE else SQUARE
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, getTintColor(holder.`fishmod$getItemRarity`()))
        } catch (t: Throwable) {
            FishDiag.fail("ItemRarityHotbar.2", "rarity background draw failed at $x,$y", t)
        }
    }

    private fun getTintColor(rarity: RarityTier): Int {
        val rgb = if (VisualSettings.itemRarityHypixelColors) HYPIXEL[rarity] ?: (rarity.color and 0xFFFFFF)
                  else rarity.color and 0xFFFFFF
        val alpha = VisualSettings.itemRarityOpacity.coerceIn(0, 100) * 255 / 100
        return (alpha shl 24) or rgb
    }

    private val PET_NAME = Regex("\\[Lvl \\d+](?: §8\\[[^\\]]*])? §([0-9a-f])")
    private val PET_COLOR: Map<Char, RarityTier> = mapOf(
        'f' to RarityTier.COMMON, 'a' to RarityTier.UNCOMMON, '9' to RarityTier.RARE,
        '5' to RarityTier.EPIC, '6' to RarityTier.LEGENDARY, 'd' to RarityTier.MYTHIC, 'b' to RarityTier.DIVINE, 'c' to RarityTier.SPECIAL,
    )

    private val RARITY_BY_NAME: Map<String, RarityTier> = RarityTier.entries.associateBy { it.name }

    @JvmStatic
    fun getRarity(stack: ItemStack): RarityTier {
        stack.get(DataComponents.LORE)?.lines()?.let { lines ->
            for (i in lines.indices.reversed()) {
                val line = lines[i].string
                if (line.contains("VERY SPECIAL")) return RarityTier.VERY_SPECIAL
                for (word in line.split(" ")) {
                    RARITY_BY_NAME[word]?.let { return it }
                }
            }
        }
        PET_NAME.find(TextTools.orderedTextToString(stack.hoverName.visualOrderText))
            ?.let { PET_COLOR[it.groupValues[1][0]] }?.let { return it }
        return RarityTier.NONE
    }
}

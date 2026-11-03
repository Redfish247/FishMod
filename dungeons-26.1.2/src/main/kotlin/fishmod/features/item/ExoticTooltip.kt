package fishmod.features.item

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component

object ExoticTooltip {

    private val CRYSTAL = hexes("1F0030 46085E 54146E 5D1C78 63237D 6A2C82 7E4196 8E51A6 9C64B3 A875BD B88BC9 C6A3D4 D9C1E3 E5D1ED EFE1F5 FCF3FF")
    private val FAIRY = hexes("330066 4C0099 660033 660066 6600CC 7F00FF 99004C 990099 9933FF B266FF CC0066 CC00CC CC99FF E5CCFF FF007F FF00FF FF3399 FF33FF FF66B2 FF66FF FF99CC FF99FF FFCCE5 FFCCFF")

    private fun hexes(s: String) = s.split(' ').map { it.toInt(16) }.toSet()

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.exoticTooltip || !SkyblockLocation.inSkyblock()) return@ItemTooltipCallback
            try {
                val rgb = stack.get(DataComponents.DYED_COLOR)?.rgb()?.and(0xFFFFFF) ?: return@ItemTooltipCallback
                val tag = stack.fishmodCustomDataTag() ?: return@ItemTooltipCallback
                if (tag.contains("dye_item")) return@ItemTooltipCallback
                val id = tag.getString("id").orElse("")
                NeuItems.ensureLoaded()
                val base = NeuItems.armorColor(id) ?: return@ItemTooltipCallback
                if (base == rgb) return@ItemTooltipCallback
                val type = when {
                    rgb in CRYSTAL -> "§bCRYSTAL"
                    rgb in FAIRY -> "§dFAIRY"
                    else -> "§6EXOTIC"
                }
                val name = lines.firstOrNull() ?: return@ItemTooltipCallback
                lines[0] = name.copy().append(Component.literal(" §8[$type§8]"))
                lines.add(Component.literal("§7Hex: §f#%06X §8(default #%06X)".format(rgb, base)))
            } catch (e: Exception) {
                FishDiag.fail("ExoticTooltip.1", "exotic tooltip failed for ${stack.hoverName.string}", e)
            }
        })
    }
}

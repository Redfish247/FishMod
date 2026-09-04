package fishmod.features.item

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component

/**
 * Appends a "Quality Bonus: +X% (floor)" line to dungeon-item tooltips. Reads
 * `baseStatBoostPercentage` / `dungeon_skill_req` / `item_tier` from the item's `custom_data` —
 * no network dependency.
 */
object ItemQualityTooltip {

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.itemQualityTooltip || !Location.inSkyblock()) return@ItemTooltipCallback
            val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return@ItemTooltipCallback
            val boost = tag.getInt("baseStatBoostPercentage").orElse(0)
            if (boost <= 0) return@ItemTooltipCallback
            val req = tag.getString("dungeon_skill_req").orElse("")
            val tier = tag.getInt("item_tier").orElse(0)

            val floor = when {
                req.isEmpty() && tier > 0 -> "§aE"
                req.isEmpty() -> "§bF$tier"
                else -> {
                    val parts = req.split(':', limit = 2)
                    val dungeon = parts[0]
                    val levelReq = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    if (dungeon == "CATACOMBS") {
                        if (levelReq - tier > 19) "§4M${tier - 3}" else "§aF$tier"
                    } else "§b$dungeon $tier"
                }
            }
            val color = when {
                boost <= 17 -> "§c"
                boost <= 33 -> "§e"
                boost <= 49 -> "§a"
                else -> "§b"
            }
            lines.add(Component.literal("§6Quality Bonus: $color+$boost% §7($floor§7)"))
        })
    }
}

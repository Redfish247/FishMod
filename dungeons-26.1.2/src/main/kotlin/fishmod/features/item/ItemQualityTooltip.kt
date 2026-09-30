package fishmod.features.item

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component

object ItemQualityTooltip {

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.itemQualityTooltip || !Location.inSkyblock()) return@ItemTooltipCallback
            try {
                val tag = stack.fishmodCustomDataTag() ?: return@ItemTooltipCallback
                val boost = tag.getInt("baseStatBoostPercentage").orElse(0)
                if (boost <= 0) return@ItemTooltipCallback
                FishDiag.check(boost <= 100, "ItemQualityTooltip.4") { "baseStatBoostPercentage out of range: $boost" }
                val req = tag.getString("dungeon_skill_req").orElse("")
                val tier = tag.getInt("item_tier").orElse(0)

                val floor = when {
                    req.isEmpty() && tier > 0 -> "§aE"
                    req.isEmpty() -> "§bF$tier"
                    else -> {
                        val parts = req.split(':', limit = 2)
                        val dungeon = parts[0]
                        val levelReq = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        if (parts.size > 1) FishDiag.check(parts[1].toIntOrNull() != null, "ItemQualityTooltip.2") { "dungeon_skill_req level not numeric: '$req'" }
                        FishDiag.check(dungeon.isNotEmpty(), "ItemQualityTooltip.3") { "dungeon_skill_req has empty dungeon: '$req'" }
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
            } catch (e: Exception) {
                FishDiag.fail("ItemQualityTooltip.1", "quality tooltip failed for ${stack.hoverName.string}", e)
            }
        })
    }
}

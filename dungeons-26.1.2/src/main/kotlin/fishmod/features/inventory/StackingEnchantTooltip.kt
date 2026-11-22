package fishmod.features.inventory

import fishmod.features.extras.*

import fishmod.features.hud.fishmodCustomDataTag
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Tooltip line with progress to the next level of a stacking enchant
object StackingEnchantTooltip {
    private class Info(val name: String, val field: String, val unit: String, val ladder: IntArray)
    // from Skyblocker (StackingEnchantProgressTooltip): nbt counters and level thresholds
    private val INFO = mapOf(
        "expertise" to Info("Expertise", "expertise_kills", "kills", intArrayOf(0, 50, 100, 250, 500, 1000, 2500, 5500, 10_000, 15_000)),
        "compact" to Info("Compact", "compact_blocks", "blocks", intArrayOf(0, 100, 500, 1500, 5000, 15_000, 50_000, 150_000, 500_000, 1_000_000)),
        "cultivating" to Info("Cultivating", "farmed_cultivating", "crops", intArrayOf(0, 1000, 5000, 25_000, 100_000, 250_000, 1_000_000, 2_500_000, 10_000_000, 25_000_000)),
        "champion" to Info("Champion", "champion_combat_xp", "Combat XP", intArrayOf(0, 50_000, 100_000, 250_000, 500_000, 1_000_000, 1_500_000, 2_000_000, 2_500_000, 3_000_000)),
        "hecatomb" to Info("Hecatomb", "hecatomb_s_runs", "S runs", intArrayOf(0, 2, 5, 10, 20, 30, 40, 60, 80, 100)),
        "absorb" to Info("Absorb", "absorb_logs_chopped", "logs", intArrayOf(0, 1000, 5000, 25_000, 100_000, 250_000, 1_000_000, 2_500_000, 10_000_000, 25_000_000)),
        "toxophilite" to Info("Toxophilite", "toxophilite_combat_xp", "Combat XP", intArrayOf(0, 50_000, 100_000, 250_000, 500_000, 1_000_000, 1_500_000, 2_000_000, 2_500_000, 3_000_000)),
    )

    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!S.stackingEnchantTooltip) return@ItemTooltipCallback
            try {
                val tag = stack.fishmodCustomDataTag() ?: return@ItemTooltipCallback
                val ench = tag.getCompoundOrEmpty("enchantments")
                val key = ench.keySet().firstOrNull { it in INFO } ?: return@ItemTooltipCallback
                val info = INFO.getValue(key)
                val level = ench.getIntOr(key, 0)
                if (level < 1) return@ItemTooltipCallback
                val have = tag.getLongOr(info.field, 0L)
                // maxed: no next level, so show the running total only
                val line = if (level >= info.ladder.size) Component.literal("§7" + info.name + " " + level + ": §6" + "%,d".format(have) + " §7" + info.unit)
                else Component.literal("§7" + info.name + " " + (level + 1) + ": §c" + "%,d".format(have) + "§7/§c" + "%,d".format(info.ladder[level].toLong()) + " §7" + info.unit)
                // Bottom of the tooltip, just above the Obtained line when it is already there
                val ob = lines.indexOfLast { it.string.startsWith("Obtained:") }
                if (ob >= 1) lines.add(ob, line) else lines.add(line)
            } catch (e: Exception) {
                FishDiag.fail("StackingEnchantTooltip.1", "stacking enchant tooltip failed", e)
            }
        })
    }
}

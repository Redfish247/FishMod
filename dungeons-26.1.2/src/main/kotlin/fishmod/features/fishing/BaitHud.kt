package fishmod.features.fishing

import fishmod.features.mining.MiningHuds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import fishmod.features.fishing.FishingWorldSettings as W

// HUD with the bait in the last hotbar slot and how much is left
object BaitHud {
    private var name: String? = null
    private var left: Int? = null
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!W.baitHud || tick++ % 20 != 0) return@register
            val p = mc.player ?: run { name = null; return@register }
            val stack = p.inventory.getItem(8)
            val n = if (stack.isEmpty) null else stack.hoverName.string
            if (n == null || (!n.contains("Bait") && !n.contains("Obfuscated"))) { name = null; left = null; return@register }
            name = n
            left = stack.get(DataComponents.LORE)?.lines()?.firstNotNullOfOrNull { FishingAlertText.baitRemaining(it.string) }
        }
        MiningHuds.reg("Current Bait", "fishing_bait", 120, 10,
            { W.baitHudX }, { W.baitHudX = it }, { W.baitHudY }, { W.baitHudY = it },
            { W.baitHudScale }, { W.baitHudScale = it },
            { W.baitHud && Fishing.active() && Fishing.holdingRod() }, { W.baitHud }, example = { listOf("§bBait: §fWhale Bait §7(42)") }, lines = ::lines)
    }

    private fun lines(): List<String> {
        val n = name ?: return listOf("§bBait: §cNone")
        return listOf("§bBait: §f$n" + (left?.let { " §7($it)" } ?: ""))
    }
}

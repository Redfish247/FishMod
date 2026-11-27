package fishmod.features.extras

import fishmod.compat.curScreen
import fishmod.features.diana.DianaTracker
import fishmod.pv.price.ItemPricer
import fishmod.utils.TextStrip
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

object AhPriceCheck {
    // from NoFrills (PriceTooltips): menu title + "Cost: " lore line on the confirm button
    private const val TITLE = "Confirm Purchase"
    private const val COST = "Cost: "

    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!S.ahPriceCheck) return@ItemTooltipCallback
            try {
                val screen = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*> ?: return@ItemTooltipCallback
                if (screen.title.string.trim() != TITLE) return@ItemTooltipCallback
                val lore = stack.get(DataComponents.LORE)?.lines()?.map { TextStrip.plain(it.string).trim() } ?: return@ItemTooltipCallback
                val raw = lore.firstOrNull { it.startsWith(COST) } ?: return@ItemTooltipCallback
                val cost = raw.removePrefix(COST).replace(",", "").replace("coins", "").trim().toDoubleOrNull() ?: return@ItemTooltipCallback
                val item = screen.menu.slots.take(screen.menu.slots.size - 36).map { it.item }
                    .firstOrNull { it !== stack && ItemTools.getId(it) != null } ?: return@ItemTooltipCallback
                if (!ItemPricer.isReady()) { ItemPricer.start(); lines.add(Component.literal("§7Lowest BIN: loading...")); return@ItemTooltipCallback }
                val lbin = ItemPricer.price(ItemTools.getId(item)) * item.count
                if (lbin <= 0) { lines.add(Component.literal("§7Lowest BIN: unknown")); return@ItemTooltipCallback }
                val pct = ((cost - lbin) / lbin * 100).toInt()
                val col = if (pct > S.ahPriceCheckWarnPct) "§c" else if (pct > 0) "§e" else "§a"
                lines.add(Component.literal("§7Lowest BIN: §6" + DianaTracker.short(lbin) + " §7(" + col + (if (pct >= 0) "+" else "") + pct + "%§7)"))
                if (pct > S.ahPriceCheckWarnPct) lines.add(Component.literal("§c§lOverpriced vs lowest BIN!"))
            } catch (t: Throwable) {
                FishDiag.fail("AhPriceCheck.1", "ah price check tooltip failed", t)
            }
        })
    }
}

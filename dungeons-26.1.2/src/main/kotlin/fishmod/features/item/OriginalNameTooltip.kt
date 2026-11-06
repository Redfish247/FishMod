package fishmod.features.item

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component

object OriginalNameTooltip {

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.originalNameTooltip || !ItemCustomizationStore.hasAny()) return@ItemTooltipCallback
            try {
                val uuid = ItemTools.getUuid(stack) ?: return@ItemTooltipCallback
                if (ItemCustomizationStore.getItemName(uuid) == null) return@ItemTooltipCallback
                val original = stack.get(DataComponents.CUSTOM_NAME) ?: stack.get(DataComponents.ITEM_NAME) ?: return@ItemTooltipCallback
                val label = Component.literal(FishSettings.originalNameLabel).withColor(FishSettings.originalNameColor and 0xFFFFFF)
                lines.add(if (lines.isEmpty()) 0 else 1, label.append(original))
            } catch (e: Exception) {
                FishDiag.fail("OriginalNameTooltip.1", "original name tooltip failed", e)
            }
        })
    }
}

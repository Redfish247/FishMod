package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Arrow Fix. Shortbows shoot instantly, but the client still plays the bow pull-back animation and
 * eats the right-click; clearing the active use item every tick while a shortbow is drawn removes
 * that. Per-id lore cache.
 */
object ArrowFix {

    private val shortbows = HashSet<String>()
    private val others = HashSet<String>()

    @JvmStatic
    fun isShortbow(item: ItemStack?): Boolean {
        if (!FishSettings.arrowFixEnabled) return false
        if (item == null || item.isEmpty || !item.`is`(Items.BOW)) return false
        val id = ItemUtil.getId(item) ?: return false
        if (id in shortbows) return true
        if (id in others) return false
        val lore = item.get(DataComponents.LORE)?.lines() ?: return false
        for (i in lore.indices.reversed()) {
            if ("Shortbow: Instantly shoots!" in lore[i].string) { shortbows.add(id); return true }
        }
        others.add(id)
        return false
    }
}

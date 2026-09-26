package fishmod.utils.data

import fishmod.features.item.fishmodCustomDataTag
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object ItemUtil {

    @JvmStatic
    fun getId(item: ItemStack): String? {
        val compound = item.fishmodCustomDataTag() ?: return null
        return if (compound.contains("id")) compound.getStringOr("id", "") else null
    }

    @JvmStatic
    fun getUuid(item: ItemStack): String? {
        val compound = item.fishmodCustomDataTag() ?: return null
        return if (compound.contains("uuid")) compound.getStringOr("uuid", "") else null
    }

    @JvmStatic
    fun containsLore(item: ItemStack?, contain: String): Boolean {
        if (item == null) return false
        val lore = item.get(DataComponents.LORE) ?: return false

        val lines = lore.lines()
        if (lines.isEmpty()) return false

        for (line in lines.asReversed()) {
            val string = line.string
            if (string.contains(contain)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun containsIgnoreCaseLore(item: ItemStack?, contain: String): Boolean {
        if (item == null) return false
        val lore = item.get(DataComponents.LORE) ?: return false

        for (line in lore.lines().asReversed()) {
            if (line.string.contains(contain, ignoreCase = true)) return true
        }
        return false
    }
}

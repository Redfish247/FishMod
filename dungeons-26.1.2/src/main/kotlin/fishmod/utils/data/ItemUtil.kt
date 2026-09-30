package fishmod.utils.data

import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object ItemUtil {

    @JvmStatic
    fun getId(item: ItemStack): String? {
        val compound = item.fishmodCustomDataTag() ?: return null
        if (!compound.contains("id")) return null
        val id = compound.getStringOr("id", "")
        if (id.isEmpty()) FishDiag.fail("ItemUtil.1", "item custom data has 'id' but it isn't a non-empty string: ${compound.get("id")}")
        return id
    }

    @JvmStatic
    fun getUuid(item: ItemStack): String? {
        val compound = item.fishmodCustomDataTag() ?: return null
        if (!compound.contains("uuid")) return null
        val uuid = compound.getStringOr("uuid", "")
        if (uuid.isEmpty()) FishDiag.fail("ItemUtil.2", "item custom data has 'uuid' but it isn't a non-empty string: ${compound.get("uuid")}")
        return uuid
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

package fishmod.utils.data

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object ItemUtil {

    @JvmStatic
    fun getId(item: ItemStack): String? {
        val nbt = item.get(DataComponents.CUSTOM_DATA) ?: return null
        val compound = nbt.copyTag()
        return if (compound.contains("id")) compound.getStringOr("id", "") else null
    }

    /** Reads a raw string value out of an item's CUSTOM_DATA NBT (e.g. "petInfo"), or null. */
    @JvmStatic
    fun getNbtString(item: ItemStack, key: String): String? {
        val nbt = item.get(DataComponents.CUSTOM_DATA) ?: return null
        val compound = nbt.copyTag()
        return if (compound.contains(key)) compound.getStringOr(key, "") else null
    }

    @JvmStatic
    fun getUuid(item: ItemStack): String? {
        val nbt = item.get(DataComponents.CUSTOM_DATA) ?: return null
        val compound = nbt.copyTag()
        return if (compound.contains("uuid")) compound.getStringOr("uuid", "") else null
    }

    @JvmStatic
    fun isHolding(name: String): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.mainHandItem.hoverName.string.contains(name)
    }

    @JvmStatic
    fun itemHasName(itemStack: ItemStack?, name: String): Boolean {
        val player = Minecraft.getInstance().player
        if (player == null || itemStack == null) return false
        return itemStack.hoverName.string.contains(name)
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

        val lines = lore.lines()
        if (lines.isEmpty()) return false

        for (line in lines.asReversed()) {
            val string = line.string
            if (string.lowercase().contains(contain.lowercase())) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun containsNBT(item: ItemStack, contain: String): Boolean {
        val nbt = item.get(DataComponents.CUSTOM_DATA) ?: return false
        return nbt.toString().contains(contain)
    }
}

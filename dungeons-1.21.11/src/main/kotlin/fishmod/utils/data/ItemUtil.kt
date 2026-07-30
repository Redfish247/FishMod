package fishmod.utils.data

import net.minecraft.client.MinecraftClient
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack

object ItemUtil {

    @JvmStatic
    fun getId(item: ItemStack): String? {
        val nbt = item.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        val compound = nbt.copyNbt()
        return compound.getString("id", null)
    }

    /** Reads a raw string value out of an item's CUSTOM_DATA NBT (e.g. "petInfo"), or null. */
    @JvmStatic
    fun getNbtString(item: ItemStack, key: String): String? {
        val nbt = item.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        return nbt.copyNbt().getString(key, null)
    }

    @JvmStatic
    fun getUuid(item: ItemStack): String? {
        val nbt = item.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        val compound = nbt.copyNbt()
        return compound.getString("uuid", null)
    }

    @JvmStatic
    fun isHolding(name: String): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        return player.mainHandStack.name.string.contains(name)
    }

    @JvmStatic
    fun itemHasName(itemStack: ItemStack?, name: String): Boolean {
        val player = MinecraftClient.getInstance().player
        if (player == null || itemStack == null) return false
        return itemStack.name.string.contains(name)
    }

    @JvmStatic
    fun containsLore(item: ItemStack?, contain: String): Boolean {
        if (item == null) return false
        val lore = item.get(DataComponentTypes.LORE) ?: return false

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
        val lore = item.get(DataComponentTypes.LORE) ?: return false

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
        val nbt = item.get(DataComponentTypes.CUSTOM_DATA) ?: return false
        return nbt.toString().contains(contain)
    }
}

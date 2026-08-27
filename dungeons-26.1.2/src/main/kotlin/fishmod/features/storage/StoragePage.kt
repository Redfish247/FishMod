package fishmod.features.storage

import net.minecraft.client.Minecraft

/** One storage page: ender-chest pages are index 0..8, backpack pages 9..26 (Noamm's layout). */
data class StoragePage(val index: Int) : Comparable<StoragePage> {
    val isEnderChest get() = index < 9
    val name get() = if (isEnderChest) "Ender Chest #${index + 1}" else "Backpack #${index - 9 + 1}"

    fun open() {
        val cmd = if (isEnderChest) "enderchest ${index + 1}" else "backpack ${index - 9 + 1}"
        Minecraft.getInstance().connection?.sendCommand(cmd)
    }

    override fun compareTo(other: StoragePage) = index - other.index

    companion object {
        private val ENDER = Regex("^Ender Chest (?:✦ )?\\(([1-9])/[1-9]\\)$")
        private val BACKPACK = Regex("^.+Backpack (?:✦ )?\\(Slot #([0-9]+)\\)$")

        fun fromTitle(title: String): StoragePage? {
            ENDER.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return StoragePage(it - 1) }
            BACKPACK.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return StoragePage(it - 1 + 9) }
            return null
        }
    }
}

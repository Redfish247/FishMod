package fishmod.features.storage

import net.minecraft.client.Minecraft
import fishmod.utils.debug.FishDiag

data class StoragePage(val index: Int) : Comparable<StoragePage> {
    val isEnderChest get() = index < 9
    val isRift get() = index >= RIFT_BASE
    val name get() = when {
        isRift -> "Rift Ender Chest #${index - RIFT_BASE + 1}"
        isEnderChest -> "Ender Chest #${index + 1}"
        else -> "Backpack #${index - 9 + 1}"
    }

    fun open() {
        val cmd = if (isRift) "enderchest ${index - RIFT_BASE + 1}" else if (isEnderChest) "enderchest ${index + 1}" else "backpack ${index - 9 + 1}"
        FishDiag.notNull(Minecraft.getInstance().connection, "StoragePage.1") { "no connection to open $name" }?.sendCommand(cmd)
    }

    override fun compareTo(other: StoragePage) = index - other.index

    companion object {
        // Rift has its own 2 ender chest pages, stored after the 27 normal ones
        const val RIFT_BASE = 27
        const val MAX_INDEX = RIFT_BASE + 2

        fun inRift() = fishmod.utils.Location.`in`(fishmod.utils.Location.THE_RIFT)

        fun isOverviewTitle(t: String) = t == "Storage" || t == "Rift Storage"
        private val ENDER = Regex("^Ender Chest (?:✦ )?\\(([1-9])/[1-9]\\)$")
        private val BACKPACK = Regex("^.+Backpack (?:✦ )?\\(Slot #([0-9]+)\\)$")

        fun fromTitle(title: String): StoragePage? {
            ENDER.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (inRift()) return if (it <= 2) StoragePage(RIFT_BASE + it - 1) else null
                return StoragePage(it - 1)
            }
            if (inRift()) return null
            BACKPACK.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return StoragePage(it - 1 + 9) }
            return null
        }

        fun overviewIndex(slot: Int): Int? = when (slot) {
            in 9..17 -> slot - 9
            in 27..44 -> slot - 27 + 9
            else -> null
        }
    }
}

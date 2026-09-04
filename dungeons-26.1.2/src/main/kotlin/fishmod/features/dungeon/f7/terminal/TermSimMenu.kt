package fishmod.features.dungeon.f7.terminal

import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

/** A local chest menu backing [TermSimScreen] — no server, clicks are handled by the screen. */
class TermSimMenu(val rows: Int, playerInv: Inventory, @JvmField val box: SimpleContainer) :
    ChestMenu(menuType(rows), 0, playerInv, box, rows) {

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
    override fun stillValid(player: Player): Boolean = true

    companion object {
        private fun menuType(rows: Int): MenuType<ChestMenu> = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }
    }
}

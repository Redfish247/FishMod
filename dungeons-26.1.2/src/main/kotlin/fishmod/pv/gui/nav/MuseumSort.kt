/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class MuseumSort(private val iconItem: Item, private val langKey: String) {
	ALL(Items.PAINTING, "fishmod.pv.museum.all"),
	COMBAT(Items.IRON_SWORD, "fishmod.pv.museum.combat"),
	MINING(Items.IRON_PICKAXE, "fishmod.pv.museum.mining"),
	FORAGING(Items.IRON_AXE, "fishmod.pv.museum.foraging"),
	FARMING(Items.IRON_HOE, "fishmod.pv.museum.farming"),
	FISHING(Items.FISHING_ROD, "fishmod.pv.museum.fishing"),
	DUNGEONEERING(Items.GOLDEN_SWORD, "fishmod.pv.museum.dungeoneering"),
	HUNTING(Items.BOW, "fishmod.pv.museum.hunting"),
	SPECIAL(Items.BEACON, "fishmod.pv.museum.special");

	fun icon(): ItemStack = ItemStack(iconItem)

	fun label(): Component = Component.translatable(langKey)

	fun isAll(): Boolean = this == ALL

	companion object {
		@JvmStatic
		fun categories(): Array<MuseumSort> = entries.drop(1).toTypedArray()
	}
}

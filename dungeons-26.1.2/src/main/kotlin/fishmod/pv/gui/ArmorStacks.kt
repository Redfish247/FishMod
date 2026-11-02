/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import net.minecraft.world.item.ItemStack

/** Armor stacks for the home mannequin from SkyBlock `inv_armor`. */
object ArmorStacks {
	/** @return boots, leggings, chestplate, helmet (Hypixel slot order) */
	@JvmStatic
	fun fromMember(member: JsonObject?): Array<ItemStack> {
		val armor = Array<ItemStack>(4) { ItemStack.EMPTY }
		if (member == null) {
			return armor
		}
		val slots = InventoryDecoder.readArmorSlots(member)
		for (i in 0 until minOf(4, slots.size)) {
			val stack = slots[i]
			if (stack == null || stack.id.isBlank()) {
				continue
			}
			val slot = InventorySnapshot.Slot(
				stack.id,
				maxOf(1, stack.count),
				stack.lore,
				stack.displayName,
				stack.dyeColor,
				stack.skullValue,
				stack.skullSignature
			)
			val rendered = SkyBlockItemFactory.toStack(slot)
			if (rendered != null && !rendered.isEmpty) {
				armor[i] = rendered
			}
		}
		return armor
	}
}

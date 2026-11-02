/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections

import java.util.Locale
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Vanilla item icons for collection category column headers. */
object CollectionCategoryIcons {
	// Drawn every frame; shared read-only stacks avoid per-frame allocation.
	private val FARMING = ItemStack(Items.GOLDEN_HOE)
	private val MINING = ItemStack(Items.STONE_PICKAXE)
	private val COMBAT = ItemStack(Items.IRON_SWORD)
	private val FORAGING = ItemStack(Items.JUNGLE_SAPLING)
	private val FISHING = ItemStack(Items.FISHING_ROD)
	private val BOSS = ItemStack(Items.WITHER_SKELETON_SKULL)
	private val RIFT = ItemStack(Items.MYCELIUM)
	private val OTHER = ItemStack(Items.CHEST)

	@JvmStatic
	fun icon(categoryId: String?): ItemStack =
		when (categoryId?.trim()?.uppercase(Locale.ROOT) ?: "") {
			"FARMING" -> FARMING
			"MINING" -> MINING
			"COMBAT" -> COMBAT
			"FORAGING" -> FORAGING
			"FISHING" -> FISHING
			"BOSS" -> BOSS
			"RIFT" -> RIFT
			else -> OTHER
		}
}

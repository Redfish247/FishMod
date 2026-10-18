/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections;

import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla item icons for collection category column headers. */
public final class CollectionCategoryIcons {
	private CollectionCategoryIcons() {
	}

	public static ItemStack icon(String categoryId) {
		String id = categoryId == null ? "" : categoryId.trim().toUpperCase(Locale.ROOT);
		return switch (id) {
			case "FARMING" -> new ItemStack(Items.GOLDEN_HOE);
			case "MINING" -> new ItemStack(Items.STONE_PICKAXE);
			case "COMBAT" -> new ItemStack(Items.IRON_SWORD);
			case "FORAGING" -> new ItemStack(Items.JUNGLE_SAPLING);
			case "FISHING" -> new ItemStack(Items.FISHING_ROD);
			case "BOSS" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
			case "RIFT" -> new ItemStack(Items.MYCELIUM);
			default -> new ItemStack(Items.CHEST);
		};
	}
}

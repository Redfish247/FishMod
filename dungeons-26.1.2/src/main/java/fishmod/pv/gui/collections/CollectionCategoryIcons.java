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
			case "FARMING" -> FARMING;
			case "MINING" -> MINING;
			case "COMBAT" -> COMBAT;
			case "FORAGING" -> FORAGING;
			case "FISHING" -> FISHING;
			case "BOSS" -> BOSS;
			case "RIFT" -> RIFT;
			default -> OTHER;
		};
	}

	// Drawn every frame; shared read-only stacks avoid per-frame allocation.
	private static final ItemStack FARMING = new ItemStack(Items.GOLDEN_HOE);
	private static final ItemStack MINING = new ItemStack(Items.STONE_PICKAXE);
	private static final ItemStack COMBAT = new ItemStack(Items.IRON_SWORD);
	private static final ItemStack FORAGING = new ItemStack(Items.JUNGLE_SAPLING);
	private static final ItemStack FISHING = new ItemStack(Items.FISHING_ROD);
	private static final ItemStack BOSS = new ItemStack(Items.WITHER_SKELETON_SKULL);
	private static final ItemStack RIFT = new ItemStack(Items.MYCELIUM);
	private static final ItemStack OTHER = new ItemStack(Items.CHEST);
}

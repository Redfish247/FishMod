/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum MuseumSort {
	/** Aggregate of every museum category (side-tab first). */
	ALL(Items.PAINTING, "fishmod.pv.museum.all"),
	COMBAT(Items.IRON_SWORD, "fishmod.pv.museum.combat"),
	MINING(Items.IRON_PICKAXE, "fishmod.pv.museum.mining"),
	FORAGING(Items.IRON_AXE, "fishmod.pv.museum.foraging"),
	FARMING(Items.IRON_HOE, "fishmod.pv.museum.farming"),
	FISHING(Items.FISHING_ROD, "fishmod.pv.museum.fishing"),
	DUNGEONEERING(Items.GOLDEN_SWORD, "fishmod.pv.museum.dungeoneering"),
	HUNTING(Items.BOW, "fishmod.pv.museum.hunting"),
	SPECIAL(Items.BEACON, "fishmod.pv.museum.special");

	private final Item iconItem;
	private final String langKey;

	MuseumSort(Item item, String langKey) {
		this.iconItem = item;
		this.langKey = langKey;
	}

	public ItemStack icon() {
		return new ItemStack(this.iconItem);
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}

	public boolean isAll() {
		return this == ALL;
	}

	/** Per-category sorts in display order (excludes {@link #ALL}). */
	public static MuseumSort[] categories() {
		MuseumSort[] all = values();
		MuseumSort[] out = new MuseumSort[all.length - 1];
		System.arraycopy(all, 1, out, 0, out.length);
		return out;
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav;

import fishmod.pv.ProfileViewer;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum InventoryPane {
	INVENTORY(null, null, Items.CHEST, "fishmod.pv.inv.inventory"),
	ENDER_CHEST(null, null, Items.ENDER_CHEST, "fishmod.pv.inv.ender_chest"),
	BACKPACKS("JUMBO_BACKPACK", null, Items.SHULKER_BOX, "fishmod.pv.inv.backpacks"),
	WARDROBE(null, null, Items.ARMOR_STAND, "fishmod.pv.inv.wardrobe"),
	EQUIPMENT_WARDROBE(null, null, Items.IRON_CHESTPLATE, "fishmod.pv.inv.equipment_wardrobe"),
	LOADOUTS(null, null, Items.NAME_TAG, "fishmod.pv.inv.loadouts"),
	SACKS("POCKET_SACK_IN_A_SACK", null, Items.BUNDLE, "fishmod.pv.inv.sacks"),
	/** No dedicated Fishing Bag skull in the Hypixel items API. */
	FISHING_BAG(null, null, Items.FISHING_ROD, "fishmod.pv.inv.fishing_bag"),
	/**
	 * Hypixel items API ships a broken shared placeholder skin for potion bags -
	 * use the bundled SkyCrypt render instead.
	 */
	POTION_BAG(null, "textures/gui/inventories/potion_bag.png", Items.POTION, "fishmod.pv.inv.potion_bag"),
	QUIVER(null, null, Items.ARROW, "fishmod.pv.inv.quiver"),
	/**
	 * Same API placeholder issue as potion bags (talisman bag = accessory bag).
	 */
	ACCESSORY_BAG(null, "textures/gui/inventories/accessory_bag.png", Items.GOLDEN_APPLE, "fishmod.pv.inv.accessory_bag"),
	TIME_POCKET(null, null, Items.CLOCK, "fishmod.pv.inv.time_pocket"),
	PERSONAL_VAULT(null, null, Items.ENDER_EYE, "fishmod.pv.inv.personal_vault"),
	CARNIVAL_MASKS("CARNIVAL_MASK_BAG", null, Items.LEATHER_HELMET, "fishmod.pv.inv.carnival_masks"),
	CANDY_BAG(null, null, Items.COOKIE, "fishmod.pv.inv.candy_bag");

	private final String skyblockIconId;
	private final Identifier textureIcon;
	private final Item fallback;
	private final String langKey;

	InventoryPane(
		String skyblockIconId,
		String texturePath,
		Item fallback,
		String langKey
	) {
		this.skyblockIconId = skyblockIconId;
		this.textureIcon = texturePath == null
			? null
			: ProfileViewer.id(texturePath);
		this.fallback = fallback;
		this.langKey = langKey;
	}

	/** Bundled 16×16 GUI texture when Hypixel/NEU skulls are missing or wrong. */
	public Identifier textureIcon() {
		return this.textureIcon;
	}

	public ItemStack icon() {
		if (this.skyblockIconId != null) {
			ItemStack sky = SkyBlockItemFactory.iconStack(this.skyblockIconId);
			if (sky != null && !sky.isEmpty()) {
				if (this == CARNIVAL_MASKS || sky.is(Items.PLAYER_HEAD)) {
					return sky;
				}
			}
		}
		return new ItemStack(this.fallback);
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}

	public boolean visibleOn(InventorySnapshot snapshot) {
		// Fishing Bag was removed from the game; still decoded for networth but never shown.
		if (this == FISHING_BAG) {
			return false;
		}
		if (snapshot == null) {
			return this != CARNIVAL_MASKS && this != CANDY_BAG;
		}
		return switch (this) {
			case CARNIVAL_MASKS -> snapshot.carnivalPresent();
			case CANDY_BAG -> snapshot.candyPresent();
			default -> true;
		};
	}
}

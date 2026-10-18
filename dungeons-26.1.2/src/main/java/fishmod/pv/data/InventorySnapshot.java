/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Decoded SkyBlock inventories for the Inventories tab. */
public final class InventorySnapshot {
	public record Slot(
		String id,
		int count,
		List<String> lore,
		String displayName,
		Integer dyeColor,
		String skullValue,
		String skullSignature,
		CompoundTag extraAttributes,
		boolean soulbound
	) {
		/** UI-only slots (icons, placeholders) without NBT. */
		public Slot(
			String id,
			int count,
			List<String> lore,
			String displayName,
			Integer dyeColor,
			String skullValue,
			String skullSignature
		) {
			this(id, count, lore, displayName, dyeColor, skullValue, skullSignature, null, false);
		}

		public static Slot empty() {
			return null;
		}

		public boolean isEmpty() {
			return id == null || id.isBlank();
		}
	}

	public record Page(String title, List<Slot> slots, int columns, int equippedColumn) {
		public Page(String title, List<Slot> slots, int columns) {
			this(title, slots, columns, -1);
		}

		public Page {
			// Empty inventory slots are intentionally null; List.copyOf forbids nulls.
			if (slots == null) {
				slots = List.of();
			} else {
				slots = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(slots));
			}
			columns = Math.max(1, columns);
			title = title == null ? "" : title;
		}
	}

	/** One accessory tuning template (non-zero stats only). */
	public record TuningTemplate(int slot, List<StatPoint> stats) {
		public TuningTemplate {
			stats = stats == null ? List.of() : List.copyOf(stats);
		}
	}

	public record StatPoint(String id, String label, int value) {
	}

	/** Magical power / power stone / tuning for the Accessory Bag pane. */
	public record AccessoryInfo(
		int magicalPower,
		int highestMagicalPower,
		String selectedPower,
		List<TuningTemplate> tunings,
		int bagUpgrades,
		List<String> unlockedPowers
	) {
		public AccessoryInfo {
			magicalPower = Math.max(0, magicalPower);
			highestMagicalPower = Math.max(0, highestMagicalPower);
			selectedPower = selectedPower == null ? "" : selectedPower;
			tunings = tunings == null ? List.of() : List.copyOf(tunings);
			bagUpgrades = Math.max(0, bagUpgrades);
			unlockedPowers = unlockedPowers == null ? List.of() : List.copyOf(unlockedPowers);
		}

		public static AccessoryInfo empty() {
			return new AccessoryInfo(0, 0, "", List.of(), 0, List.of());
		}
	}

	/**
	 * Named loadout preset: equipment column + armor column + metadata.
	 * hotmPreset/hotfPreset are the skill tree preset slots (1-5) the loadout switches to, null when unset.
	 */
	public record Loadout(
		String name,
		List<Slot> equipment,
		List<Slot> armor,
		String powerStone,
		Integer tuningSlot,
		List<StatPoint> tuning,
		Slot pet,
		String petLabel,
		Integer hotmPreset,
		Integer hotfPreset
	) {
		public Loadout {
			name = name == null || name.isBlank() ? "Loadout" : name;
			equipment = padSlots(equipment, 4);
			armor = padSlots(armor, 4);
			powerStone = powerStone == null ? "" : powerStone;
			tuning = tuning == null ? List.of() : List.copyOf(tuning);
			petLabel = petLabel == null ? "" : petLabel;
		}

		private static List<Slot> padSlots(List<Slot> slots, int size) {
			List<Slot> out = new java.util.ArrayList<>(size);
			if (slots != null) {
				out.addAll(slots);
			}
			while (out.size() < size) {
				out.add(null);
			}
			if (out.size() > size) {
				out = new java.util.ArrayList<>(out.subList(0, size));
			}
			return java.util.Collections.unmodifiableList(out);
		}
	}

	private final Page inventory;
	private final List<Page> enderChest;
	private final List<Page> backpacks;
	private final List<Page> wardrobe;
	private final List<Page> equipmentWardrobe;
	private final List<Loadout> loadouts;
	private final List<Page> sacks;
	private final Page fishingBag;
	private final Page potionBag;
	private final Page quiver;
	private final List<Page> accessoryBag;
	private final AccessoryInfo accessoryInfo;
	private final Page timePocket;
	private final Page personalVault;
	private final Page carnivalMasks;
	private final boolean carnivalPresent;
	private final Page candyBag;
	private final boolean candyPresent;
	private final boolean inventoryApi;
	private final boolean personalVaultPresent;
	private final boolean timePocketPresent;

	public InventorySnapshot(
		Page inventory,
		List<Page> enderChest,
		List<Page> backpacks,
		List<Page> wardrobe,
		List<Page> equipmentWardrobe,
		List<Loadout> loadouts,
		List<Page> sacks,
		Page fishingBag,
		Page potionBag,
		Page quiver,
		List<Page> accessoryBag,
		AccessoryInfo accessoryInfo,
		Page timePocket,
		Page personalVault,
		Page carnivalMasks,
		boolean carnivalPresent,
		Page candyBag,
		boolean candyPresent
	) {
		this(
			inventory, enderChest, backpacks, wardrobe, equipmentWardrobe, loadouts, sacks, fishingBag, potionBag,
			quiver, accessoryBag, accessoryInfo, timePocket, personalVault, carnivalMasks, carnivalPresent, candyBag,
			candyPresent, true, true, true
		);
	}

	/**
	 * @param inventoryApi false when the member's inventory API is off ({@code inv_contents} missing)
	 * @param personalVaultPresent false when {@code personal_vault_contents} is absent (vault not unlocked)
	 * @param timePocketPresent false when no Time Pocket field was found in the API data
	 */
	public InventorySnapshot(
		Page inventory,
		List<Page> enderChest,
		List<Page> backpacks,
		List<Page> wardrobe,
		List<Page> equipmentWardrobe,
		List<Loadout> loadouts,
		List<Page> sacks,
		Page fishingBag,
		Page potionBag,
		Page quiver,
		List<Page> accessoryBag,
		AccessoryInfo accessoryInfo,
		Page timePocket,
		Page personalVault,
		Page carnivalMasks,
		boolean carnivalPresent,
		Page candyBag,
		boolean candyPresent,
		boolean inventoryApi,
		boolean personalVaultPresent,
		boolean timePocketPresent
	) {
		this.inventoryApi = inventoryApi;
		this.personalVaultPresent = personalVaultPresent;
		this.timePocketPresent = timePocketPresent;
		this.inventory = inventory == null ? emptyPage("Inventory", 9) : inventory;
		this.enderChest = enderChest == null ? List.of() : List.copyOf(enderChest);
		this.backpacks = backpacks == null ? List.of() : List.copyOf(backpacks);
		this.wardrobe = wardrobe == null ? List.of() : List.copyOf(wardrobe);
		this.equipmentWardrobe = equipmentWardrobe == null ? List.of() : List.copyOf(equipmentWardrobe);
		this.loadouts = loadouts == null ? List.of() : List.copyOf(loadouts);
		this.sacks = sacks == null ? List.of() : List.copyOf(sacks);
		this.fishingBag = fishingBag == null ? emptyPage("Fishing Bag", 9) : fishingBag;
		this.potionBag = potionBag == null ? emptyPage("Potion Bag", 9) : potionBag;
		this.quiver = quiver == null ? emptyPage("Quiver", 9) : quiver;
		this.accessoryBag = accessoryBag == null || accessoryBag.isEmpty()
			? List.of(emptyPage("Accessory Bag", 9))
			: List.copyOf(accessoryBag);
		this.accessoryInfo = accessoryInfo == null ? AccessoryInfo.empty() : accessoryInfo;
		this.timePocket = timePocket == null ? emptyPage("Time Pocket", 9) : timePocket;
		this.personalVault = personalVault == null ? emptyPage("Personal Vault", 9) : personalVault;
		this.carnivalMasks = carnivalMasks == null ? emptyPage("Carnival Masks", 9) : carnivalMasks;
		this.carnivalPresent = carnivalPresent;
		this.candyBag = candyBag == null ? emptyPage("Candy Bag", 9) : candyBag;
		this.candyPresent = candyPresent;
	}

	public static InventorySnapshot empty() {
		return new InventorySnapshot(
			emptyPage("Inventory", 9),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(emptyPage("Sacks", 9)),
			emptyPage("Fishing Bag", 9),
			emptyPage("Potion Bag", 9),
			emptyPage("Quiver", 9),
			List.of(emptyPage("Accessory Bag", 9)),
			AccessoryInfo.empty(),
			emptyPage("Time Pocket", 9),
			emptyPage("Personal Vault", 9),
			emptyPage("Carnival Masks", 9),
			false,
			emptyPage("Candy Bag", 9),
			false
		);
	}

	public static Page emptyPage(String title, int columns) {
		return new Page(title, List.of(), columns);
	}

	public Page inventory() {
		return this.inventory;
	}

	public List<Page> enderChest() {
		return this.enderChest;
	}

	public List<Page> backpacks() {
		return this.backpacks;
	}

	public List<Page> wardrobe() {
		return this.wardrobe;
	}

	public List<Page> equipmentWardrobe() {
		return this.equipmentWardrobe;
	}

	public List<Loadout> loadouts() {
		return this.loadouts;
	}

	public List<Page> sacks() {
		return this.sacks;
	}

	public Page fishingBag() {
		return this.fishingBag;
	}

	public Page potionBag() {
		return this.potionBag;
	}

	public Page quiver() {
		return this.quiver;
	}

	public List<Page> accessoryBag() {
		return this.accessoryBag;
	}

	public AccessoryInfo accessoryInfo() {
		return this.accessoryInfo;
	}

	public Page timePocket() {
		return this.timePocket;
	}

	public Page personalVault() {
		return this.personalVault;
	}

	public boolean inventoryApi() {
		return this.inventoryApi;
	}

	public boolean personalVaultPresent() {
		return this.personalVaultPresent;
	}

	public boolean timePocketPresent() {
		return this.timePocketPresent;
	}

	public Page carnivalMasks() {
		return this.carnivalMasks;
	}

	public boolean carnivalPresent() {
		return this.carnivalPresent;
	}

	public Page candyBag() {
		return this.candyBag;
	}

	public boolean candyPresent() {
		return this.candyPresent;
	}
}

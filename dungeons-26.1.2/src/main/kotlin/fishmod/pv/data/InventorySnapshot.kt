/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.util.Collections
import net.minecraft.nbt.CompoundTag

class InventorySnapshot @JvmOverloads constructor(
	inventory: Page?,
	enderChest: List<Page>?,
	backpacks: List<Page>?,
	wardrobe: List<Page>?,
	equipmentWardrobe: List<Page>?,
	loadouts: List<Loadout>?,
	sacks: List<Page>?,
	fishingBag: Page?,
	potionBag: Page?,
	quiver: Page?,
	accessoryBag: List<Page>?,
	accessoryInfo: AccessoryInfo?,
	timePocket: Page?,
	personalVault: Page?,
	carnivalMasks: Page?,
	@get:JvmName("carnivalPresent") val carnivalPresent: Boolean,
	candyBag: Page?,
	@get:JvmName("candyPresent") val candyPresent: Boolean,
	@get:JvmName("inventoryApi") val inventoryApi: Boolean = true,
	@get:JvmName("personalVaultPresent") val personalVaultPresent: Boolean = true,
	@get:JvmName("timePocketPresent") val timePocketPresent: Boolean = true
) {
	@JvmRecord
	data class Slot(
		val id: String?,
		val count: Int,
		val lore: List<String>?,
		val displayName: String?,
		val dyeColor: Int?,
		val skullValue: String?,
		val skullSignature: String?,
		val extraAttributes: CompoundTag?,
		val soulbound: Boolean
	) {
		constructor(
			id: String?,
			count: Int,
			lore: List<String>?,
			displayName: String?,
			dyeColor: Int?,
			skullValue: String?,
			skullSignature: String?
		) : this(id, count, lore, displayName, dyeColor, skullValue, skullSignature, null, false)

		fun isEmpty(): Boolean = id.isNullOrBlank()

		companion object {
			@JvmStatic
			fun empty(): Slot? = null
		}
	}

	class Page @JvmOverloads constructor(title: String?, slots: List<Slot?>?, columns: Int, equippedColumn: Int = -1) {
		@get:JvmName("title") val title: String = title ?: ""
		@get:JvmName("slots") val slots: List<Slot?> = if (slots == null) listOf() else Collections.unmodifiableList(ArrayList(slots))
		@get:JvmName("columns") val columns: Int = maxOf(1, columns)
		@get:JvmName("equippedColumn") val equippedColumn: Int = equippedColumn
	}

	/** One accessory tuning template (non-zero stats only). */
	class TuningTemplate(@get:JvmName("slot") val slot: Int, stats: List<StatPoint>?) {
		@get:JvmName("stats") val stats: List<StatPoint> = if (stats == null) listOf() else java.util.List.copyOf(stats)
	}

	@JvmRecord
	data class StatPoint(val id: String, val label: String, val value: Int)

	class AccessoryInfo(
		magicalPower: Int,
		highestMagicalPower: Int,
		selectedPower: String?,
		tunings: List<TuningTemplate>?,
		bagUpgrades: Int,
		unlockedPowers: List<String>?
	) {
		@get:JvmName("magicalPower") val magicalPower: Int = maxOf(0, magicalPower)
		@get:JvmName("highestMagicalPower") val highestMagicalPower: Int = maxOf(0, highestMagicalPower)
		@get:JvmName("selectedPower") val selectedPower: String = selectedPower ?: ""
		@get:JvmName("tunings") val tunings: List<TuningTemplate> = if (tunings == null) listOf() else java.util.List.copyOf(tunings)
		@get:JvmName("bagUpgrades") val bagUpgrades: Int = maxOf(0, bagUpgrades)
		@get:JvmName("unlockedPowers") val unlockedPowers: List<String> =
			if (unlockedPowers == null) listOf() else java.util.List.copyOf(unlockedPowers)

		companion object {
			@JvmStatic
			fun empty(): AccessoryInfo = AccessoryInfo(0, 0, "", listOf(), 0, listOf())
		}
	}

	class Loadout(
		name: String?,
		equipment: List<Slot?>?,
		armor: List<Slot?>?,
		powerStone: String?,
		@get:JvmName("tuningSlot") val tuningSlot: Int?,
		tuning: List<StatPoint>?,
		@get:JvmName("pet") val pet: Slot?,
		petLabel: String?,
		@get:JvmName("hotmPreset") val hotmPreset: Int?,
		@get:JvmName("hotfPreset") val hotfPreset: Int?
	) {
		@get:JvmName("name") val name: String = if (name.isNullOrBlank()) "Loadout" else name
		@get:JvmName("equipment") val equipment: List<Slot?> = padSlots(equipment, 4)
		@get:JvmName("armor") val armor: List<Slot?> = padSlots(armor, 4)
		@get:JvmName("powerStone") val powerStone: String = powerStone ?: ""
		@get:JvmName("tuning") val tuning: List<StatPoint> = if (tuning == null) listOf() else java.util.List.copyOf(tuning)
		@get:JvmName("petLabel") val petLabel: String = petLabel ?: ""

		private fun padSlots(slots: List<Slot?>?, size: Int): List<Slot?> {
			val out = ArrayList<Slot?>(size)
			if (slots != null) {
				out.addAll(slots.take(size))
			}
			while (out.size < size) {
				out.add(null)
			}
			return Collections.unmodifiableList(out)
		}
	}

	@get:JvmName("inventory") val inventory: Page = inventory ?: emptyPage("Inventory", 9)
	@get:JvmName("enderChest") val enderChest: List<Page> = copy(enderChest)
	@get:JvmName("backpacks") val backpacks: List<Page> = copy(backpacks)
	@get:JvmName("wardrobe") val wardrobe: List<Page> = copy(wardrobe)
	@get:JvmName("equipmentWardrobe") val equipmentWardrobe: List<Page> = copy(equipmentWardrobe)
	@get:JvmName("loadouts") val loadouts: List<Loadout> = copy(loadouts)
	@get:JvmName("sacks") val sacks: List<Page> = copy(sacks)
	@get:JvmName("fishingBag") val fishingBag: Page = fishingBag ?: emptyPage("Fishing Bag", 9)
	@get:JvmName("potionBag") val potionBag: Page = potionBag ?: emptyPage("Potion Bag", 9)
	@get:JvmName("quiver") val quiver: Page = quiver ?: emptyPage("Quiver", 9)
	@get:JvmName("accessoryBag") val accessoryBag: List<Page> =
		if (accessoryBag.isNullOrEmpty()) listOf(emptyPage("Accessory Bag", 9)) else java.util.List.copyOf(accessoryBag)
	@get:JvmName("accessoryInfo") val accessoryInfo: AccessoryInfo = accessoryInfo ?: AccessoryInfo.empty()
	@get:JvmName("timePocket") val timePocket: Page = timePocket ?: emptyPage("Time Pocket", 9)
	@get:JvmName("personalVault") val personalVault: Page = personalVault ?: emptyPage("Personal Vault", 9)
	@get:JvmName("carnivalMasks") val carnivalMasks: Page = carnivalMasks ?: emptyPage("Carnival Masks", 9)
	@get:JvmName("candyBag") val candyBag: Page = candyBag ?: emptyPage("Candy Bag", 9)

	companion object {
		@JvmStatic
		fun empty(): InventorySnapshot = InventorySnapshot(
			emptyPage("Inventory", 9),
			listOf(),
			listOf(),
			listOf(),
			listOf(),
			listOf(),
			listOf(emptyPage("Sacks", 9)),
			emptyPage("Fishing Bag", 9),
			emptyPage("Potion Bag", 9),
			emptyPage("Quiver", 9),
			listOf(emptyPage("Accessory Bag", 9)),
			AccessoryInfo.empty(),
			emptyPage("Time Pocket", 9),
			emptyPage("Personal Vault", 9),
			emptyPage("Carnival Masks", 9),
			false,
			emptyPage("Candy Bag", 9),
			false
		)

		@JvmStatic
		fun emptyPage(title: String?, columns: Int): Page = Page(title, listOf(), columns)

		private fun <T : Any> copy(list: List<T>?): List<T> = if (list == null) listOf() else java.util.List.copyOf(list)
	}
}

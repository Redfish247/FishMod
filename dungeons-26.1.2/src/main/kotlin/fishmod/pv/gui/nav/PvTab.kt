/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav

import java.util.Locale
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class PvTab(private val iconItem: Item, private val langKey: String) {
	HOME(Items.PAPER, "fishmod.pv.tab.home"),
	DUNGEONS(Items.SKELETON_SKULL, "fishmod.pv.tab.dungeons"),
	INVENTORIES(Items.CHEST, "fishmod.pv.tab.inventories"),
	PETS(Items.BONE, "fishmod.pv.tab.pets"),
	COLLECTIONS(Items.ITEM_FRAME, "fishmod.pv.tab.collections"),
	GARDEN(Items.IRON_HOE, "fishmod.pv.tab.garden"),
	MINING(Items.IRON_PICKAXE, "fishmod.pv.tab.mining"),
	FORAGING(Items.IRON_AXE, "fishmod.pv.tab.foraging"),
	FISHING(Items.FISHING_ROD, "fishmod.pv.tab.fishing"),
	CRIMSON(Items.NETHERRACK, "fishmod.pv.tab.crimson"),
	RIFT(Items.ENDER_EYE, "fishmod.pv.tab.rift"),
	MUSEUM(Items.EMERALD, "fishmod.pv.tab.museum"),
	BESTIARY(Items.IRON_SWORD, "fishmod.pv.tab.bestiary"),
	EVENTS(Items.FIREWORK_ROCKET, "fishmod.pv.tab.events");

	fun icon(): ItemStack = ItemStack(iconItem)

	fun label(): Component = Component.translatable(langKey)

	/** A `/pv <page>` target. `sub` is null when the tab's default subtab should be used. */
	@JvmRecord
	data class Route(val tab: PvTab?, val sub: PvSubTab?) {
		fun valid(): Boolean {
			if (tab == null) {
				return false
			}
			if (sub == null) {
				return true
			}
			return tab.subTabs().any { it == sub }
		}
	}

	fun subTabs(): Array<PvSubTab> = when (this) {
		HOME -> arrayOf(PvSubTab.HOME_OVERVIEW, PvSubTab.HOME_MISC, PvSubTab.HOME_RECORDS, PvSubTab.HOME_ESSENCE)
		DUNGEONS -> arrayOf()
		COLLECTIONS -> arrayOf()
		GARDEN -> arrayOf(
			PvSubTab.GARDEN_OVERVIEW,
			PvSubTab.GARDEN_VISITORS,
			PvSubTab.GARDEN_CROPS,
			PvSubTab.GARDEN_GREENHOUSE,
			PvSubTab.GARDEN_JACOB
		)
		MINING -> arrayOf(
			PvSubTab.MINING_OVERVIEW,
			PvSubTab.MINING_HOTM,
			PvSubTab.MINING_GLACITE
		)
		FORAGING -> arrayOf(
			PvSubTab.FORAGING_OVERVIEW,
			PvSubTab.FORAGING_HOTF,
			PvSubTab.FORAGING_HUNTING,
			PvSubTab.FORAGING_ATTRIBUTE_SHARDS
		)
		EVENTS -> arrayOf(
			PvSubTab.EVENTS_BINGO,
			PvSubTab.EVENTS_CHOCOLATE
		)
		FISHING -> arrayOf(PvSubTab.FISHING_MAIN, PvSubTab.FISHING_TROPHIES)
		CRIMSON -> arrayOf(
			PvSubTab.CRIMSON_OVERVIEW,
			PvSubTab.CRIMSON_KUUDRA,
			PvSubTab.CRIMSON_ABIPHONE
		)
		RIFT -> arrayOf(PvSubTab.RIFT_OVERVIEW, PvSubTab.RIFT_INVENTORY)
		else -> PvSubTab.NONE
	}

	fun hasSubTabs(): Boolean = subTabs().isNotEmpty()

	fun isBestiarySplit(): Boolean = this == BESTIARY

	fun hasMuseumSort(): Boolean = this == MUSEUM

	fun isInventorySplit(): Boolean = this == INVENTORIES

	fun leftTabs(): Array<Any> {
		if (this == INVENTORIES) {
			return arrayOf<Any>(*InventoryPane.entries.toTypedArray())
		}
		return arrayOf<Any>(*subTabs())
	}

	companion object {
		/**
		 * Resolves `/pv <page>` aliases. Returns `null` when the token is not a page
		 * (caller should treat it as a player name).
		 */
		@JvmStatic
		fun fromCommandArg(raw: String?): PvTab? = routeFromCommandArg(raw)?.tab

		@JvmStatic
		fun routeFromCommandArg(raw: String?): Route? {
			if (raw == null || raw.isBlank()) {
				return null
			}
			val token = raw.trim().lowercase(Locale.ROOT)
			if (token == "essence" || token == "essences") {
				return Route(HOME, PvSubTab.HOME_ESSENCE)
			}
			val tab = when (token) {
				"home" -> HOME
				"dungeons", "dungeon" -> DUNGEONS
				"storage", "inventories", "inventory" -> INVENTORIES
				"pets", "pet" -> PETS
				"collections", "collection" -> COLLECTIONS
				"garden" -> GARDEN
				"mining" -> MINING
				"foraging" -> FORAGING
				"fishing" -> FISHING
				"crimson" -> CRIMSON
				"rift" -> RIFT
				"museum" -> MUSEUM
				"bestiary" -> BESTIARY
				"events", "event" -> EVENTS
				else -> null
			}
			return if (tab == null) null else Route(tab, null)
		}

		/** Canonical `/pv <page>` tokens shown in command suggestions. */
		@JvmStatic
		fun commandAliases(): Array<String> = arrayOf(
			"home",
			"dungeons",
			"storage",
			"pets",
			"collections",
			"garden",
			"mining",
			"foraging",
			"fishing",
			"crimson",
			"rift",
			"essence"
		)
	}
}

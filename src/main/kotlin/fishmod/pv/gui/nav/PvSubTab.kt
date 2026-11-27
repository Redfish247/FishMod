/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav

import fishmod.pv.ProfileViewer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class PvSubTab(
	private val iconItem: Item,
	private val langKey: String,
	texturePath: String? = null,
	textureSize: Int = 16
) {
	DUNGEON_NORMAL(Items.STONE_BRICKS, "fishmod.pv.sub.dungeon_normal"),
	DUNGEON_MASTER(Items.NETHER_BRICKS, "fishmod.pv.sub.dungeon_master"),
	DUNGEON_OVERVIEW(Items.SKELETON_SKULL, "fishmod.pv.sub.dungeon_overview"),
	DUNGEON_CHESTS(Items.CHEST, "fishmod.pv.sub.dungeon_chests"),
	AUCTION_STATS(Items.GOLD_BLOCK, "fishmod.pv.sub.auction_stats"),
	AUCTION_SOLD(Items.GOLD_INGOT, "fishmod.pv.sub.auction_sold"),
	AUCTION_BOUGHT(Items.EMERALD, "fishmod.pv.sub.auction_bought"),
	COLLECTIONS_LIST(Items.ITEM_FRAME, "fishmod.pv.sub.collections"),
	COLLECTIONS_MINIONS(Items.HOPPER, "fishmod.pv.sub.minions"),
	GARDEN_OVERVIEW(Items.GRASS_BLOCK, "fishmod.pv.sub.garden_overview"),
	GARDEN_VISITORS(Items.VILLAGER_SPAWN_EGG, "fishmod.pv.sub.visitors"),
	GARDEN_CROPS(Items.WHEAT, "fishmod.pv.sub.crops"),
	GARDEN_COMPOSTER(Items.COMPOSTER, "fishmod.pv.sub.composter"),
	GARDEN_GREENHOUSE(Items.GLASS, "fishmod.pv.sub.greenhouse"),
	GARDEN_JACOB(Items.GOLDEN_CARROT, "fishmod.pv.sub.jacob"),
	MINING_OVERVIEW(Items.IRON_PICKAXE, "fishmod.pv.sub.mining_overview"),
	MINING_HOTM(Items.PLAYER_HEAD, "fishmod.pv.sub.mining_hotm", "textures/gui/nav/hotm.png", 64),
	MINING_GLACITE(Items.BLUE_ICE, "fishmod.pv.sub.mining_glacite"),
	FORAGING_OVERVIEW(Items.IRON_AXE, "fishmod.pv.sub.foraging_overview"),
	FORAGING_HOTF(Items.OAK_SAPLING, "fishmod.pv.sub.hotf"),
	FORAGING_HUNTING(Items.LEAD, "fishmod.pv.sub.hunting"),
	FORAGING_ATTRIBUTE_SHARDS(Items.PRISMARINE_SHARD, "fishmod.pv.sub.attribute_shards"),
	FISHING_MAIN(Items.FISHING_ROD, "fishmod.pv.sub.fishing_main"),
	FISHING_TROPHIES(Items.TROPICAL_FISH, "fishmod.pv.sub.fishing_trophies"),
	CRIMSON_OVERVIEW(Items.NETHERRACK, "fishmod.pv.sub.crimson_overview"),
	CRIMSON_KUUDRA(Items.NETHERITE_SCRAP, "fishmod.pv.sub.kuudra"),
	CRIMSON_ABIPHONE(Items.NOTE_BLOCK, "fishmod.pv.sub.abiphone"),
	RIFT_OVERVIEW(Items.ENDER_EYE, "fishmod.pv.sub.rift_overview"),
	RIFT_INVENTORY(Items.CHEST, "fishmod.pv.sub.rift_inventory"),
	EVENTS_BINGO(Items.FILLED_MAP, "fishmod.pv.sub.bingo"),
	EVENTS_CHOCOLATE(Items.COOKIE, "fishmod.pv.sub.chocolate"),
	HOME_OVERVIEW(Items.PAPER, "fishmod.pv.sub.home_overview"),
	HOME_MISC(Items.BOOK, "fishmod.pv.sub.home_misc"),
	HOME_RECORDS(Items.DRAGON_HEAD, "fishmod.pv.sub.home_records"),
	HOME_ESSENCE(Items.NETHER_STAR, "fishmod.pv.sub.home_essence");

	private val textureIcon: Identifier? = texturePath?.let { ProfileViewer.id(it) }
	private val textureSize: Int = maxOf(1, textureSize)

	fun icon(): ItemStack = ItemStack(iconItem)

	fun textureIcon(): Identifier? = textureIcon

	fun textureSize(): Int = textureSize

	fun label(): Component = Component.translatable(langKey)

	companion object {
		@JvmField
		val NONE: Array<PvSubTab> = arrayOf()
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav;

import fishmod.pv.BetterPV;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PvSubTab {
	DUNGEON_NORMAL(Items.STONE_BRICKS, "betterpv.sub.dungeon_normal"),
	DUNGEON_MASTER(Items.NETHER_BRICKS, "betterpv.sub.dungeon_master"),
	DUNGEON_OVERVIEW(Items.SKELETON_SKULL, "betterpv.sub.dungeon_overview"),
	DUNGEON_CHESTS(Items.CHEST, "betterpv.sub.dungeon_chests"),
	AUCTION_STATS(Items.GOLD_BLOCK, "betterpv.sub.auction_stats"),
	AUCTION_SOLD(Items.GOLD_INGOT, "betterpv.sub.auction_sold"),
	AUCTION_BOUGHT(Items.EMERALD, "betterpv.sub.auction_bought"),
	COLLECTIONS_LIST(Items.ITEM_FRAME, "betterpv.sub.collections"),
	COLLECTIONS_MINIONS(Items.HOPPER, "betterpv.sub.minions"),
	GARDEN_OVERVIEW(Items.GRASS_BLOCK, "betterpv.sub.garden_overview"),
	GARDEN_VISITORS(Items.VILLAGER_SPAWN_EGG, "betterpv.sub.visitors"),
	GARDEN_CROPS(Items.WHEAT, "betterpv.sub.crops"),
	GARDEN_COMPOSTER(Items.COMPOSTER, "betterpv.sub.composter"),
	GARDEN_GREENHOUSE(Items.GLASS, "betterpv.sub.greenhouse"),
	GARDEN_JACOB(Items.GOLDEN_CARROT, "betterpv.sub.jacob"),
	MINING_OVERVIEW(Items.IRON_PICKAXE, "betterpv.sub.mining_overview"),
	MINING_HOTM(Items.PLAYER_HEAD, "betterpv.sub.mining_hotm", "textures/gui/nav/hotm.png", 64),
	MINING_GLACITE(Items.BLUE_ICE, "betterpv.sub.mining_glacite"),
	FORAGING_OVERVIEW(Items.IRON_AXE, "betterpv.sub.foraging_overview"),
	FORAGING_HOTF(Items.OAK_SAPLING, "betterpv.sub.hotf"),
	FORAGING_HUNTING(Items.LEAD, "betterpv.sub.hunting"),
	FORAGING_ATTRIBUTE_SHARDS(Items.PRISMARINE_SHARD, "betterpv.sub.attribute_shards"),
	FISHING_MAIN(Items.FISHING_ROD, "betterpv.sub.fishing_main"),
	FISHING_TROPHIES(Items.TROPICAL_FISH, "betterpv.sub.fishing_trophies"),
	CRIMSON_OVERVIEW(Items.NETHERRACK, "betterpv.sub.crimson_overview"),
	CRIMSON_KUUDRA(Items.NETHERITE_SCRAP, "betterpv.sub.kuudra"),
	CRIMSON_ABIPHONE(Items.NOTE_BLOCK, "betterpv.sub.abiphone"),
	RIFT_OVERVIEW(Items.ENDER_EYE, "betterpv.sub.rift_overview"),
	RIFT_INVENTORY(Items.CHEST, "betterpv.sub.rift_inventory"),
	EVENTS_BINGO(Items.FILLED_MAP, "betterpv.sub.bingo"),
	EVENTS_CHOCOLATE(Items.COOKIE, "betterpv.sub.chocolate"),
	HOME_OVERVIEW(Items.PAPER, "betterpv.sub.home_overview"),
	HOME_MISC(Items.BOOK, "betterpv.sub.home_misc"),
	HOME_RECORDS(Items.DRAGON_HEAD, "betterpv.sub.home_records"),
	HOME_ESSENCE(Items.NETHER_STAR, "betterpv.sub.home_essence");

	public static final PvSubTab[] NONE = new PvSubTab[0];

	private final Item iconItem;
	private final String langKey;
	private final Identifier textureIcon;
	private final int textureSize;

	PvSubTab(Item item, String langKey) {
		this(item, langKey, null, 16);
	}

	PvSubTab(Item item, String langKey, String texturePath, int textureSize) {
		this.iconItem = item;
		this.langKey = langKey;
		this.textureIcon = texturePath == null
			? null
			: Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, texturePath);
		this.textureSize = Math.max(1, textureSize);
	}

	public ItemStack icon() {
		return new ItemStack(this.iconItem);
	}

	public Identifier textureIcon() {
		return this.textureIcon;
	}

	public int textureSize() {
		return this.textureSize;
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}
}

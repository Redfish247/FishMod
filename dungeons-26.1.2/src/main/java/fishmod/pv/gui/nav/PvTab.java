/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PvTab {
	HOME(Items.PAPER, "betterpv.tab.home"),
	DUNGEONS(Items.SKELETON_SKULL, "betterpv.tab.dungeons"),
	INVENTORIES(Items.CHEST, "betterpv.tab.inventories"),
	PETS(Items.BONE, "betterpv.tab.pets"),
	COLLECTIONS(Items.ITEM_FRAME, "betterpv.tab.collections"),
	GARDEN(Items.IRON_HOE, "betterpv.tab.garden"),
	MINING(Items.IRON_PICKAXE, "betterpv.tab.mining"),
	FORAGING(Items.IRON_AXE, "betterpv.tab.foraging"),
	FISHING(Items.FISHING_ROD, "betterpv.tab.fishing"),
	CRIMSON(Items.NETHERRACK, "betterpv.tab.crimson"),
	RIFT(Items.ENDER_EYE, "betterpv.tab.rift"),
	MUSEUM(Items.EMERALD, "betterpv.tab.museum"),
	BESTIARY(Items.IRON_SWORD, "betterpv.tab.bestiary"),
	EVENTS(Items.FIREWORK_ROCKET, "betterpv.tab.events");

	private final Item iconItem;
	private final String langKey;

	PvTab(Item item, String langKey) {
		this.iconItem = item;
		this.langKey = langKey;
	}

	public ItemStack icon() {
		return new ItemStack(this.iconItem);
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}

	/** A {@code /pv <page>} target. {@code sub} is null when the tab's default subtab should be used. */
	public record Route(PvTab tab, PvSubTab sub) {
		public boolean valid() {
			if (this.tab == null) {
				return false;
			}
			if (this.sub == null) {
				return true;
			}
			for (PvSubTab candidate : this.tab.subTabs()) {
				if (candidate == this.sub) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Resolves {@code /pv <page>} aliases. Returns {@code null} when the token is not a page
	 * (caller should treat it as a player name).
	 */
	public static PvTab fromCommandArg(String raw) {
		Route route = routeFromCommandArg(raw);
		return route == null ? null : route.tab();
	}

	public static Route routeFromCommandArg(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String token = raw.trim().toLowerCase(Locale.ROOT);
		if (token.equals("essence") || token.equals("essences")) {
			return new Route(HOME, PvSubTab.HOME_ESSENCE);
		}
		PvTab tab = switch (token) {
			case "home" -> HOME;
			case "dungeons", "dungeon" -> DUNGEONS;
			case "storage", "inventories", "inventory" -> INVENTORIES;
			case "pets", "pet" -> PETS;
			case "collections", "collection" -> COLLECTIONS;
			case "garden" -> GARDEN;
			case "mining" -> MINING;
			case "foraging" -> FORAGING;
			case "fishing" -> FISHING;
			case "crimson" -> CRIMSON;
			case "rift" -> RIFT;
			case "museum" -> MUSEUM;
			case "bestiary" -> BESTIARY;
			case "events", "event" -> EVENTS;
			default -> null;
		};
		return tab == null ? null : new Route(tab, null);
	}

	/** Canonical {@code /pv <page>} tokens shown in command suggestions. */
	public static String[] commandAliases() {
		return new String[] {
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
		};
	}

	public PvSubTab[] subTabs() {
		return switch (this) {
			case HOME -> new PvSubTab[] { PvSubTab.HOME_OVERVIEW, PvSubTab.HOME_MISC, PvSubTab.HOME_RECORDS, PvSubTab.HOME_ESSENCE };
			case DUNGEONS -> new PvSubTab[0];
			case COLLECTIONS -> new PvSubTab[0];
			case GARDEN -> new PvSubTab[] {
				PvSubTab.GARDEN_OVERVIEW,
				PvSubTab.GARDEN_VISITORS,
				PvSubTab.GARDEN_CROPS,
				PvSubTab.GARDEN_GREENHOUSE,
				PvSubTab.GARDEN_JACOB
			};
			case MINING -> new PvSubTab[] {
				PvSubTab.MINING_OVERVIEW,
				PvSubTab.MINING_HOTM,
				PvSubTab.MINING_GLACITE
			};
			case FORAGING -> new PvSubTab[] {
				PvSubTab.FORAGING_OVERVIEW,
				PvSubTab.FORAGING_HOTF,
				PvSubTab.FORAGING_HUNTING,
				PvSubTab.FORAGING_ATTRIBUTE_SHARDS
			};
			case EVENTS -> new PvSubTab[] {
				PvSubTab.EVENTS_BINGO,
				PvSubTab.EVENTS_CHOCOLATE
			};
			case FISHING -> new PvSubTab[] { PvSubTab.FISHING_MAIN, PvSubTab.FISHING_TROPHIES };
			case CRIMSON -> new PvSubTab[] {
				PvSubTab.CRIMSON_OVERVIEW,
				PvSubTab.CRIMSON_KUUDRA,
				PvSubTab.CRIMSON_ABIPHONE
			};
			case RIFT -> new PvSubTab[] { PvSubTab.RIFT_OVERVIEW, PvSubTab.RIFT_INVENTORY };
			default -> PvSubTab.NONE;
		};
	}

	public boolean hasSubTabs() {
		return subTabs().length > 0;
	}

	public boolean isBestiarySplit() {
		return this == BESTIARY;
	}

	public boolean hasMuseumSort() {
		return this == MUSEUM;
	}

	public boolean isInventorySplit() {
		return this == INVENTORIES;
	}

	public Object[] leftTabs() {
		if (this == INVENTORIES) {
			return InventoryPane.values();
		}
		return subTabs();
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence;

import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla stand-in icons for essence shop perks, shared by every shop view. */
public final class EssenceIcons {
	private EssenceIcons() {
	}

	public static ItemStack shop(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.PLAYER_HEAD) : stack;
	}

	public static ItemStack perk(String perkId) {
		return perk(perkId, Items.PAPER);
	}

	// Drawn every frame; shared read-only stacks per (fallback, perk).
	private static final java.util.Map<Item, java.util.Map<String, ItemStack>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ItemStack perk(String perkId, Item fallback) {
		if (perkId == null || "eagles_advantage".equals(perkId)) {
			return build(perkId, fallback);
		}
		return CACHE.computeIfAbsent(fallback, f -> new java.util.concurrent.ConcurrentHashMap<>())
			.computeIfAbsent(perkId, id -> build(id, fallback));
	}

	private static ItemStack build(String perkId, Item fallback) {
		if (perkId == null) {
			return new ItemStack(fallback);
		}
		return switch (perkId) {
			// Dungeons (Malik)
			case "permanent_health", "catacombs_health" -> new ItemStack(Items.GOLDEN_APPLE);
			case "permanent_defense", "catacombs_defense" -> new ItemStack(Items.IRON_CHESTPLATE);
			case "permanent_speed" -> new ItemStack(Items.SUGAR);
			case "permanent_intelligence", "catacombs_intelligence" -> new ItemStack(Items.ENCHANTED_BOOK);
			case "permanent_strength", "catacombs_strength" -> new ItemStack(Items.BLAZE_POWDER);
			case "forbidden_blessing" -> new ItemStack(Items.GOLD_INGOT);
			case "dungeonbreaker_secrets" -> new ItemStack(Items.DIAMOND_PICKAXE);
			case "catacombs_boss_luck" -> new ItemStack(Items.RABBIT_FOOT);
			case "catacombs_looting" -> new ItemStack(Items.GOLDEN_SWORD);
			case "revive_stone", "help_of_the_fairies" -> new ItemStack(Items.TOTEM_OF_UNDYING);
			case "catacombs_crit_damage" -> new ItemStack(Items.DIAMOND_SWORD);
			case "cold_efficiency" -> new ItemStack(Items.IRON_PICKAXE);
			case "cooled_forges" -> new ItemStack(Items.BLAST_FURNACE);
			case "frozen_skin" -> new ItemStack(Items.LEATHER_CHESTPLATE);
			case "season_of_joy" -> new ItemStack(Items.SNOWBALL);
			case "drake_piper" -> new ItemStack(Items.EGG);
			case "empowered_agility" -> new ItemStack(Items.FEATHER);
			case "vermin_control" -> new ItemStack(Items.SPIDER_EYE);
			case "bane" -> new ItemStack(Items.IRON_SWORD);
			case "spider_training" -> new ItemStack(Items.BOOK);
			case "toxophilite" -> new ItemStack(Items.BOW);
			case "flat_damage_vs_ender" -> new ItemStack(Items.DIAMOND_SWORD);
			case "mana_after_ender_kill" -> new ItemStack(Items.EXPERIENCE_BOTTLE);
			case "fero_vs_dragons" -> new ItemStack(Items.DRAGON_BREATH);
			case "inc_zealots_odds" -> new ItemStack(Items.ENDER_PEARL);
			case "combat_wisdom_in_end" -> new ItemStack(Items.BOOK);
			case "edrag_cd" -> new ItemStack(Items.DRAGON_HEAD);
			case "dragon_reforges_buff" -> new ItemStack(Items.ANVIL);
			case "increased_sup_chances" -> new ItemStack(Items.EGG);
			case "unbridled_rage" -> new ItemStack(Items.BLAZE_POWDER);
			// Gold / Diamond
			case "heart_of_gold" -> new ItemStack(Items.GOLDEN_APPLE);
			case "treasures_of_the_earth" -> new ItemStack(Items.CHEST);
			case "dwarven_training" -> new ItemStack(Items.IRON_PICKAXE);
			case "unbreaking" -> new ItemStack(Items.ANVIL);
			case "eager_miner" -> new ItemStack(Items.GOLDEN_PICKAXE);
			case "midas_lure" -> new ItemStack(Items.FISHING_ROD);
			case "radiant_fisher" -> new ItemStack(Items.COD);
			case "diamond_in_the_rough" -> new ItemStack(Items.DIAMOND);
			case "rhinestone_infusion" -> new ItemStack(Items.AMETHYST_SHARD);
			case "under_pressure" -> new ItemStack(Items.OBSIDIAN);
			case "high_roller" -> new ItemStack(Items.GOLD_BLOCK);
			case "return_to_sender" -> new ItemStack(Items.ARROW);
			// Fossil
			case "prehistorian" -> new ItemStack(Items.BONE);
			case "resourceful" -> new ItemStack(Items.CHEST);
			case "chilled_to_the_bone" -> new ItemStack(Items.BLUE_ICE);
			case "dwarven_expertise" -> new ItemStack(Items.IRON_PICKAXE);
			case "sleight_of_hand" -> new ItemStack(Items.GOLDEN_PICKAXE);
			case "cut_loose" -> new ItemStack(Items.SHEARS);
			// Forest
			case "trapped" -> new ItemStack(Items.IRON_TRAPDOOR);
			case "axed" -> new ItemStack(Items.IRON_AXE);
			case "extreme_pressure" -> new ItemStack(Items.OBSIDIAN);
			case "lumberjack" -> new ItemStack(Items.OAK_LOG);
			case "tasty" -> new ItemStack(Items.APPLE);
			case "forest_training" -> new ItemStack(Items.BOOK);
			// Safari
			case "critter_catcher" -> new ItemStack(Items.EGG);
			case "critter_master" -> new ItemStack(Items.NETHER_STAR);
			case "floortunate" -> new ItemStack(Items.STRING);
			case "fresh_footprints" -> new ItemStack(Items.LEATHER_BOOTS);
			case "head_start" -> new ItemStack(Items.MAP);
			case "hunting_hotspot" -> new ItemStack(Items.COMPASS);
			case "thawing" -> new ItemStack(Items.MAGMA_CREAM);
			case "deep_diver" -> new ItemStack(Items.TURTLE_HELMET);
			case "quickdraw" -> new ItemStack(Items.BOW);
			case "amateur_hour" -> new ItemStack(Items.BOOK);
			case "sparkling_specialist" -> new ItemStack(Items.AMETHYST_SHARD);
			case "eagles_advantage" -> orFallback(SkyBlockItemFactory.iconStack("EAGLE;4"), Items.FEATHER);
			// Crimson
			case "strongarm_kuudra" -> new ItemStack(Items.FISHING_ROD);
			case "fresh_tools_kuudra" -> new ItemStack(Items.IRON_PICKAXE);
			case "headstart_kuudra" -> new ItemStack(Items.GOLD_NUGGET);
			case "master_kuudra" -> new ItemStack(Items.NETHERITE_SWORD);
			case "fungus_fortuna" -> new ItemStack(Items.RED_MUSHROOM);
			case "harena_fortuna" -> new ItemStack(Items.RED_SAND);
			case "crimson_training" -> new ItemStack(Items.BOOK);
			case "wither_piper" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
			default -> new ItemStack(fallback);
		};
	}

	// iconStack returns paper until the NEU repo has loaded the item.
	private static ItemStack orFallback(ItemStack resolved, Item fallback) {
		return isResolved(resolved == null || resolved.isEmpty() ? null : resolved.getItem()) ? resolved : new ItemStack(fallback);
	}

	static boolean isResolved(Item item) {
		return item != null && item != Items.PAPER;
	}
}

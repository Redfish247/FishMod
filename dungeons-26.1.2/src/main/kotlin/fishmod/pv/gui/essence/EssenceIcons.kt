/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence

import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Vanilla stand-in icons for essence shop perks, shared by every shop view. */
object EssenceIcons {
	@JvmStatic
	fun shop(skyblockId: String?): ItemStack {
		val stack: ItemStack? = SkyBlockItemFactory.iconStack(skyblockId ?: "")
		return if (stack == null || stack.isEmpty) ItemStack(Items.PLAYER_HEAD) else stack
	}

	@JvmStatic
	fun perk(perkId: String?): ItemStack = perk(perkId, Items.PAPER)

	// Drawn every frame; shared read-only stacks per (fallback, perk).
	private val CACHE = ConcurrentHashMap<Item, ConcurrentHashMap<String, ItemStack>>()

	@JvmStatic
	fun perk(perkId: String?, fallback: Item): ItemStack {
		if (perkId == null || perkId == "eagles_advantage") {
			return build(perkId, fallback)
		}
		return CACHE.computeIfAbsent(fallback) { ConcurrentHashMap() }
			.computeIfAbsent(perkId) { build(it, fallback) }
	}

	private fun build(perkId: String?, fallback: Item): ItemStack {
		if (perkId == null) {
			return ItemStack(fallback)
		}
		return when (perkId) {
			// Dungeons (Malik)
			"permanent_health", "catacombs_health" -> ItemStack(Items.GOLDEN_APPLE)
			"permanent_defense", "catacombs_defense" -> ItemStack(Items.IRON_CHESTPLATE)
			"permanent_speed" -> ItemStack(Items.SUGAR)
			"permanent_intelligence", "catacombs_intelligence" -> ItemStack(Items.ENCHANTED_BOOK)
			"permanent_strength", "catacombs_strength" -> ItemStack(Items.BLAZE_POWDER)
			"forbidden_blessing" -> ItemStack(Items.GOLD_INGOT)
			"dungeonbreaker_secrets" -> ItemStack(Items.DIAMOND_PICKAXE)
			"catacombs_boss_luck" -> ItemStack(Items.RABBIT_FOOT)
			"catacombs_looting" -> ItemStack(Items.GOLDEN_SWORD)
			"revive_stone", "help_of_the_fairies" -> ItemStack(Items.TOTEM_OF_UNDYING)
			"catacombs_crit_damage" -> ItemStack(Items.DIAMOND_SWORD)
			"cold_efficiency" -> ItemStack(Items.IRON_PICKAXE)
			"cooled_forges" -> ItemStack(Items.BLAST_FURNACE)
			"frozen_skin" -> ItemStack(Items.LEATHER_CHESTPLATE)
			"season_of_joy" -> ItemStack(Items.SNOWBALL)
			"drake_piper" -> ItemStack(Items.EGG)
			"empowered_agility" -> ItemStack(Items.FEATHER)
			"vermin_control" -> ItemStack(Items.SPIDER_EYE)
			"bane" -> ItemStack(Items.IRON_SWORD)
			"spider_training" -> ItemStack(Items.BOOK)
			"toxophilite" -> ItemStack(Items.BOW)
			"flat_damage_vs_ender" -> ItemStack(Items.DIAMOND_SWORD)
			"mana_after_ender_kill" -> ItemStack(Items.EXPERIENCE_BOTTLE)
			"fero_vs_dragons" -> ItemStack(Items.DRAGON_BREATH)
			"inc_zealots_odds" -> ItemStack(Items.ENDER_PEARL)
			"combat_wisdom_in_end" -> ItemStack(Items.BOOK)
			"edrag_cd" -> ItemStack(Items.DRAGON_HEAD)
			"dragon_reforges_buff" -> ItemStack(Items.ANVIL)
			"increased_sup_chances" -> ItemStack(Items.EGG)
			"unbridled_rage" -> ItemStack(Items.BLAZE_POWDER)
			// Gold / Diamond
			"heart_of_gold" -> ItemStack(Items.GOLDEN_APPLE)
			"treasures_of_the_earth" -> ItemStack(Items.CHEST)
			"dwarven_training" -> ItemStack(Items.IRON_PICKAXE)
			"unbreaking" -> ItemStack(Items.ANVIL)
			"eager_miner" -> ItemStack(Items.GOLDEN_PICKAXE)
			"midas_lure" -> ItemStack(Items.FISHING_ROD)
			"radiant_fisher" -> ItemStack(Items.COD)
			"diamond_in_the_rough" -> ItemStack(Items.DIAMOND)
			"rhinestone_infusion" -> ItemStack(Items.AMETHYST_SHARD)
			"under_pressure" -> ItemStack(Items.OBSIDIAN)
			"high_roller" -> ItemStack(Items.GOLD_BLOCK)
			"return_to_sender" -> ItemStack(Items.ARROW)
			// Fossil
			"prehistorian" -> ItemStack(Items.BONE)
			"resourceful" -> ItemStack(Items.CHEST)
			"chilled_to_the_bone" -> ItemStack(Items.BLUE_ICE)
			"dwarven_expertise" -> ItemStack(Items.IRON_PICKAXE)
			"sleight_of_hand" -> ItemStack(Items.GOLDEN_PICKAXE)
			"cut_loose" -> ItemStack(Items.SHEARS)
			// Forest
			"trapped" -> ItemStack(Items.IRON_TRAPDOOR)
			"axed" -> ItemStack(Items.IRON_AXE)
			"extreme_pressure" -> ItemStack(Items.OBSIDIAN)
			"lumberjack" -> ItemStack(Items.OAK_LOG)
			"tasty" -> ItemStack(Items.APPLE)
			"forest_training" -> ItemStack(Items.BOOK)
			// Safari
			"critter_catcher" -> ItemStack(Items.EGG)
			"critter_master" -> ItemStack(Items.NETHER_STAR)
			"floortunate" -> ItemStack(Items.STRING)
			"fresh_footprints" -> ItemStack(Items.LEATHER_BOOTS)
			"head_start" -> ItemStack(Items.MAP)
			"hunting_hotspot" -> ItemStack(Items.COMPASS)
			"thawing" -> ItemStack(Items.MAGMA_CREAM)
			"deep_diver" -> ItemStack(Items.TURTLE_HELMET)
			"quickdraw" -> ItemStack(Items.BOW)
			"amateur_hour" -> ItemStack(Items.BOOK)
			"sparkling_specialist" -> ItemStack(Items.AMETHYST_SHARD)
			"eagles_advantage" -> orFallback(SkyBlockItemFactory.iconStack("EAGLE;4"), Items.FEATHER)
			// Crimson
			"strongarm_kuudra" -> ItemStack(Items.FISHING_ROD)
			"fresh_tools_kuudra" -> ItemStack(Items.IRON_PICKAXE)
			"headstart_kuudra" -> ItemStack(Items.GOLD_NUGGET)
			"master_kuudra" -> ItemStack(Items.NETHERITE_SWORD)
			"fungus_fortuna" -> ItemStack(Items.RED_MUSHROOM)
			"harena_fortuna" -> ItemStack(Items.RED_SAND)
			"crimson_training" -> ItemStack(Items.BOOK)
			"wither_piper" -> ItemStack(Items.WITHER_SKELETON_SKULL)
			else -> ItemStack(fallback)
		}
	}

	// iconStack returns paper until the NEU repo has loaded the item.
	private fun orFallback(resolved: ItemStack?, fallback: Item): ItemStack =
		if (resolved != null && !resolved.isEmpty && isResolved(resolved.item)) resolved else ItemStack(fallback)

	@JvmStatic
	fun isResolved(item: Item?): Boolean = item != null && item !== Items.PAPER
}

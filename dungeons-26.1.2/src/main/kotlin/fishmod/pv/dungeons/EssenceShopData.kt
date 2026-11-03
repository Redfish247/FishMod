/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.Leveling
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object EssenceShopData {
	private class Def(val id: String, val name: String, val maxLevel: Int, vararg aliasIds: String) {
		val aliasIds: List<String> = aliasIds.toList()
	}

	private val WITHER = listOf(
		Def("permanent_health", "Health", 5),
		Def("permanent_defense", "Defense", 5),
		Def("permanent_speed", "Speed", 2),
		Def("permanent_intelligence", "Intelligence", 5),
		Def("permanent_strength", "Strength", 5),
		Def("forbidden_blessing", "Blessing", 10),
		Def("dungeonbreaker_secrets", "Echoes of the Lost", 5)
	)

	private val UNDEAD = listOf(
		Def("catacombs_boss_luck", "Boss Luck", 4),
		Def("catacombs_looting", "Looting", 5),
		Def("revive_stone", "Fairies", 1, "help_of_the_fairies"),
		Def("catacombs_health", "Health", 5),
		Def("catacombs_defense", "Defense", 5),
		Def("catacombs_strength", "Strength", 5),
		Def("catacombs_intelligence", "Intelligence", 5),
		Def("catacombs_crit_damage", "Crit", 5)
	)

	private val GOLD = listOf(
		Def("heart_of_gold", "Heart of Gold", 5),
		Def("treasures_of_the_earth", "Treasure of the Earth", 5),
		Def("dwarven_training", "Dwarven Training", 3),
		Def("unbreaking", "Unbreaking", 5),
		Def("eager_miner", "Eager Miner", 10),
		Def("midas_lure", "Midas Lure", 10)
	)

	private val CRIMSON = listOf(
		Def("strongarm_kuudra", "Strongarm", 2),
		Def("fresh_tools_kuudra", "Fresh Tools", 5),
		Def("headstart_kuudra", "Headstart", 5),
		Def("master_kuudra", "Kuudra Master", 1),
		Def("fungus_fortuna", "Fungus Fortuna", 10),
		Def("harena_fortuna", "Harena Fortuna", 10),
		Def("crimson_training", "Crimson Training", 3),
		Def("wither_piper", "Wither Piper", 5)
	)

	private val FOREST = listOf(
		Def("trapped", "Trapped", 5),
		Def("axed", "Axed", 1),
		Def("extreme_pressure", "Extreme Pressure", 10),
		Def("lumberjack", "Lumberjack", 10),
		Def("tasty", "Tasty", 5),
		Def("forest_training", "Forest Training", 3)
	)

	private val FOSSIL = listOf(
		Def("prehistorian", "Prehistorian", 10),
		Def("resourceful", "Resourceful", 5),
		Def("chilled_to_the_bone", "Chilled To The Bone", 10),
		Def("dwarven_expertise", "Dwarven Expertise", 10),
		Def("sleight_of_hand", "Sleight Of Hand", 1),
		Def("cut_loose", "Cut Loose", 5)
	)

	private val DIAMOND = listOf(
		Def("radiant_fisher", "Radiant Fisher", 10),
		Def("diamond_in_the_rough", "Diamond in the Rough", 5),
		Def("rhinestone_infusion", "Rhinestone Infusion", 10),
		Def("under_pressure", "Under Pressure", 5),
		Def("high_roller", "High Roller", 1),
		Def("return_to_sender", "Return to Sender", 10)
	)

	private val SPIDER = listOf(
		Def("empowered_agility", "Empowered Agility", 10),
		Def("vermin_control", "Vermin Control", 5),
		Def("bane", "Bane", 5),
		Def("spider_training", "Spider Training", 3),
		Def("toxophilite", "Toxophilite", 5)
	)

	private val DRAGON = listOf(
		Def("flat_damage_vs_ender", "One Punch", 5),
		Def("mana_after_ender_kill", "Recharge", 10),
		Def("fero_vs_dragons", "Rageborn", 5),
		Def("inc_zealots_odds", "Zealuck", 5),
		Def("combat_wisdom_in_end", "Ender Training", 3),
		Def("edrag_cd", "Infused Dragon", 5),
		Def("dragon_reforges_buff", "Two-Headed Strike", 5),
		Def("increased_sup_chances", "Dragon Piper", 1),
		Def("unbridled_rage", "Unbridled Rage", 5)
	)

	private val ICE = listOf(
		Def("cold_efficiency", "Cold Efficiency", 5),
		Def("cooled_forges", "Cooled Forges", 5),
		Def("frozen_skin", "Frozen Skin", 5),
		Def("season_of_joy", "Season of Joy", 10),
		Def("drake_piper", "Drake Piper", 1)
	)

	private val SAFARI = listOf(
		Def("critter_catcher", "Critter Catcher", 8),
		Def("critter_master", "Critter Master", 1),
		Def("floortunate", "Floortunate", 5),
		Def("fresh_footprints", "Fresh Footprints", 10),
		Def("head_start", "Head Start", 1),
		Def("hunting_hotspot", "Hunting Hotspot", 5),
		Def("thawing", "Thawing", 10),
		Def("deep_diver", "Deep Diver", 10),
		Def("quickdraw", "Quickdraw", 10),
		Def("amateur_hour", "Amateur Hour", 10),
		Def("sparkling_specialist", "Sparkling Specialist", 1),
		Def("eagles_advantage", "Eagle's Advantage", 5)
	)

	private val MAPPED_KEYS: Set<String> = listOf(WITHER, UNDEAD, GOLD, CRIMSON, FOREST, FOSSIL, DIAMOND, SPIDER, DRAGON, ICE, SAFARI)
		.flatten()
		.flatMap { listOf(it.id) + it.aliasIds }
		.toSet()
	private val LOGGED_UNMAPPED: MutableSet<String> = ConcurrentHashMap.newKeySet()

	@JvmStatic
	fun wither(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("wither", "Wither", "WITHER", "ESSENCE_WITHER", WITHER, member)

	@JvmStatic
	fun undead(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("undead", "Undead", "UNDEAD", "ESSENCE_UNDEAD", UNDEAD, member)

	@JvmStatic
	fun gold(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("gold", "Gold", "GOLD", "ESSENCE_GOLD", GOLD, member)

	@JvmStatic
	fun crimson(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("crimson", "Crimson", "CRIMSON", "ESSENCE_CRIMSON", CRIMSON, member)

	@JvmStatic
	fun forest(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("forest", "Forest", "FOREST", "ESSENCE_FOREST", FOREST, member)

	@JvmStatic
	fun diamond(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("diamond", "Diamond", "DIAMOND", "ESSENCE_DIAMOND", DIAMOND, member)

	@JvmStatic
	fun spider(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("spider", "Spider", "SPIDER", "ESSENCE_SPIDER", SPIDER, member)

	@JvmStatic
	fun dragon(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("dragon", "Dragon", "DRAGON", "ESSENCE_DRAGON", DRAGON, member)

	@JvmStatic
	fun ice(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("ice", "Ice", "ICE", "ESSENCE_ICE", ICE, member)

	@JvmStatic
	fun fossil(member: JsonObject?): DungeonSnapshot.EssenceShop = shop("fossil", "Fossil", "FOSSIL", "ESSENCE_FOSSIL", FOSSIL, member)

	@JvmStatic
	fun safari(member: JsonObject?): DungeonSnapshot.EssenceShop {
		logUnmappedPerks(member)
		return shop("safari", "Safari", "SAFARI", "ESSENCE_SAFARI", SAFARI, member)
	}

	/**
	 * Perk keys present on the profile that no shop reads, with their raw values. NEU's perk ids are
	 * hand-authored, so a new perk can be stored by Hypixel under a different key (or format).
	 */
	internal fun unmappedPerks(member: JsonObject?): MutableMap<String, String> {
		val out = TreeMap<String, String>()
		val perks = perks(member) ?: return out
		for ((key, value) in perks.entrySet()) {
			if (key !in MAPPED_KEYS) out[key] = value.toString()
		}
		return out
	}

	// Called once per profile load; logs each unmapped key once per session so the real ids show up in latest.log.
	private fun logUnmappedPerks(member: JsonObject?) {
		val unmapped = unmappedPerks(member)
		unmapped.keys.removeIf { !LOGGED_UNMAPPED.add(it) }
		if (unmapped.isNotEmpty()) ProfileViewer.LOGGER.info("[FishPV] Unmapped essence perk keys: {}", unmapped)
	}

	/** Raw essence balance from `currencies.essence.<TYPE>.current`. */
	@JvmStatic
	fun balance(member: JsonObject?, currencyKey: String): Long = essenceBalance(member, currencyKey)

	private fun shop(
		id: String,
		name: String,
		currencyKey: String,
		iconId: String,
		defs: List<Def>,
		member: JsonObject?
	): DungeonSnapshot.EssenceShop {
		val perks = perks(member)
		val list = defs.map { def ->
			val level = max(0, min(def.maxLevel, perkLevel(perks, def)))
			DungeonSnapshot.EssencePerk(def.id, def.name, level, def.maxLevel)
		}
		return DungeonSnapshot.EssenceShop(id, name, essenceBalance(member, currencyKey), iconId, java.util.List.copyOf(list))
	}

	private fun perks(member: JsonObject?): JsonObject? {
		val playerData = Leveling.obj(member?.get("player_data"))
		playerData?.let { Leveling.obj(it.get("perks")) }?.let { return it }
		val dungeons = Leveling.obj(member?.get("dungeons"))
		return dungeons?.let { Leveling.obj(it.get("perks")) }
	}

	private fun perkLevel(perks: JsonObject?, def: Def): Int {
		val level = perkLevel(perks, def.id)
		if (level > 0) return level
		for (alias in def.aliasIds) {
			val alt = perkLevel(perks, alias)
			if (alt > 0) return alt
		}
		return level
	}

	private fun perkLevel(perks: JsonObject?, id: String?): Int {
		if (perks == null || id == null || !perks.has(id)) return 0
		val el = perks.get(id)
		if (el != null && el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) return if (el.asBoolean) 1 else 0
		val n = Leveling.num(el) ?: return 0
		return Math.round(n)
	}

	private fun essenceBalance(member: JsonObject?, type: String): Long {
		val currencies = Leveling.obj(member?.get("currencies"))
		val essence = currencies?.let { Leveling.obj(it.get("essence")) }
		val typed = essence?.let { Leveling.obj(it.get(type)) } ?: return 0L
		val current = Leveling.num(typed.get("current")) ?: return 0L
		return Math.round(current).toLong()
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.BetterPV;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.Leveling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class EssenceShopData {
	private record Def(String id, String name, int maxLevel, String... aliasIds) {
	}

	private static final List<Def> WITHER = List.of(
		new Def("permanent_health", "Health", 5),
		new Def("permanent_defense", "Defense", 5),
		new Def("permanent_speed", "Speed", 2),
		new Def("permanent_intelligence", "Intelligence", 5),
		new Def("permanent_strength", "Strength", 5),
		new Def("forbidden_blessing", "Blessing", 10),
		new Def("dungeonbreaker_secrets", "Echoes of the Lost", 5)
	);

	private static final List<Def> UNDEAD = List.of(
		new Def("catacombs_boss_luck", "Boss Luck", 4),
		new Def("catacombs_looting", "Looting", 5),
		new Def("revive_stone", "Fairies", 1, "help_of_the_fairies"),
		new Def("catacombs_health", "Health", 5),
		new Def("catacombs_defense", "Defense", 5),
		new Def("catacombs_strength", "Strength", 5),
		new Def("catacombs_intelligence", "Intelligence", 5),
		new Def("catacombs_crit_damage", "Crit", 5)
	);

	private static final List<Def> GOLD = List.of(
		new Def("heart_of_gold", "Heart of Gold", 5),
		new Def("treasures_of_the_earth", "Treasure of the Earth", 5),
		new Def("dwarven_training", "Dwarven Training", 3),
		new Def("unbreaking", "Unbreaking", 5),
		new Def("eager_miner", "Eager Miner", 10),
		new Def("midas_lure", "Midas Lure", 10)
	);
	
	private static final List<Def> CRIMSON = List.of(
		new Def("strongarm_kuudra", "Strongarm", 2),
		new Def("fresh_tools_kuudra", "Fresh Tools", 5),
		new Def("headstart_kuudra", "Headstart", 5),
		new Def("master_kuudra", "Kuudra Master", 1),
		new Def("fungus_fortuna", "Fungus Fortuna", 10),
		new Def("harena_fortuna", "Harena Fortuna", 10),
		new Def("crimson_training", "Crimson Training", 3),
		new Def("wither_piper", "Wither Piper", 5)
	);

	private static final List<Def> FOREST = List.of(
		new Def("trapped", "Trapped", 5),
		new Def("axed", "Axed", 1),
		new Def("extreme_pressure", "Extreme Pressure", 10),
		new Def("lumberjack", "Lumberjack", 10),
		new Def("tasty", "Tasty", 5),
		new Def("forest_training", "Forest Training", 3)
	);

	private static final List<Def> FOSSIL = List.of(
		new Def("prehistorian", "Prehistorian", 10),
		new Def("resourceful", "Resourceful", 5),
		new Def("chilled_to_the_bone", "Chilled To The Bone", 10),
		new Def("dwarven_expertise", "Dwarven Expertise", 10),
		new Def("sleight_of_hand", "Sleight Of Hand", 1),
		new Def("cut_loose", "Cut Loose", 5)
	);

	private static final List<Def> DIAMOND = List.of(
		new Def("radiant_fisher", "Radiant Fisher", 10),
		new Def("diamond_in_the_rough", "Diamond in the Rough", 5),
		new Def("rhinestone_infusion", "Rhinestone Infusion", 10),
		new Def("under_pressure", "Under Pressure", 5),
		new Def("high_roller", "High Roller", 1),
		new Def("return_to_sender", "Return to Sender", 10)
	);

	private static final List<Def> SPIDER = List.of(
		new Def("empowered_agility", "Empowered Agility", 10),
		new Def("vermin_control", "Vermin Control", 5),
		new Def("bane", "Bane", 5),
		new Def("spider_training", "Spider Training", 3),
		new Def("toxophilite", "Toxophilite", 5)
	);

	private static final List<Def> DRAGON = List.of(
		new Def("flat_damage_vs_ender", "One Punch", 5),
		new Def("mana_after_ender_kill", "Recharge", 10),
		new Def("fero_vs_dragons", "Rageborn", 5),
		new Def("inc_zealots_odds", "Zealuck", 5),
		new Def("combat_wisdom_in_end", "Ender Training", 3),
		new Def("edrag_cd", "Infused Dragon", 5),
		new Def("dragon_reforges_buff", "Two-Headed Strike", 5),
		new Def("increased_sup_chances", "Dragon Piper", 1),
		new Def("unbridled_rage", "Unbridled Rage", 5)
	);

	private static final List<Def> ICE = List.of(
		new Def("cold_efficiency", "Cold Efficiency", 5),
		new Def("cooled_forges", "Cooled Forges", 5),
		new Def("frozen_skin", "Frozen Skin", 5),
		new Def("season_of_joy", "Season of Joy", 10),
		new Def("drake_piper", "Drake Piper", 1)
	);

	private static final List<Def> SAFARI = List.of(
		new Def("critter_catcher", "Critter Catcher", 8),
		new Def("critter_master", "Critter Master", 1),
		new Def("floortunate", "Floortunate", 5),
		new Def("fresh_footprints", "Fresh Footprints", 10),
		new Def("head_start", "Head Start", 1),
		new Def("hunting_hotspot", "Hunting Hotspot", 5),
		new Def("thawing", "Thawing", 10),
		new Def("deep_diver", "Deep Diver", 10),
		new Def("quickdraw", "Quickdraw", 10),
		new Def("amateur_hour", "Amateur Hour", 10),
		new Def("sparkling_specialist", "Sparkling Specialist", 1),
		new Def("eagles_advantage", "Eagle's Advantage", 5)
	);

	private static final Set<String> MAPPED_KEYS = mappedKeys(
		WITHER, UNDEAD, GOLD, CRIMSON, FOREST, FOSSIL, DIAMOND, SPIDER, DRAGON, ICE, SAFARI);
	private static final Set<String> LOGGED_UNMAPPED = ConcurrentHashMap.newKeySet();

	private EssenceShopData() {
	}

	public static DungeonSnapshot.EssenceShop wither(JsonObject member) {
		return shop("wither", "Wither", "WITHER", "ESSENCE_WITHER", WITHER, member);
	}

	public static DungeonSnapshot.EssenceShop undead(JsonObject member) {
		return shop("undead", "Undead", "UNDEAD", "ESSENCE_UNDEAD", UNDEAD, member);
	}

	public static DungeonSnapshot.EssenceShop gold(JsonObject member) {
		return shop("gold", "Gold", "GOLD", "ESSENCE_GOLD", GOLD, member);
	}

	public static DungeonSnapshot.EssenceShop crimson(JsonObject member) {
		return shop("crimson", "Crimson", "CRIMSON", "ESSENCE_CRIMSON", CRIMSON, member);
	}

	public static DungeonSnapshot.EssenceShop forest(JsonObject member) {
		return shop("forest", "Forest", "FOREST", "ESSENCE_FOREST", FOREST, member);
	}

	public static DungeonSnapshot.EssenceShop diamond(JsonObject member) {
		return shop("diamond", "Diamond", "DIAMOND", "ESSENCE_DIAMOND", DIAMOND, member);
	}

	public static DungeonSnapshot.EssenceShop spider(JsonObject member) {
		return shop("spider", "Spider", "SPIDER", "ESSENCE_SPIDER", SPIDER, member);
	}

	public static DungeonSnapshot.EssenceShop dragon(JsonObject member) {
		return shop("dragon", "Dragon", "DRAGON", "ESSENCE_DRAGON", DRAGON, member);
	}

	public static DungeonSnapshot.EssenceShop ice(JsonObject member) {
		return shop("ice", "Ice", "ICE", "ESSENCE_ICE", ICE, member);
	}

	public static DungeonSnapshot.EssenceShop fossil(JsonObject member) {
		return shop("fossil", "Fossil", "FOSSIL", "ESSENCE_FOSSIL", FOSSIL, member);
	}

	public static DungeonSnapshot.EssenceShop safari(JsonObject member) {
		logUnmappedPerks(member);
		return shop("safari", "Safari", "SAFARI", "ESSENCE_SAFARI", SAFARI, member);
	}

	/**
	 * Perk keys present on the profile that no shop reads, with their raw values. NEU's perk ids are
	 * hand-authored, so a new perk can be stored by Hypixel under a different key (or format).
	 */
	static Map<String, String> unmappedPerks(JsonObject member) {
		Map<String, String> out = new TreeMap<>();
		JsonObject perks = perks(member);
		if (perks == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> entry : perks.entrySet()) {
			if (!MAPPED_KEYS.contains(entry.getKey())) {
				out.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}
		return out;
	}

	// Called once per profile load; logs each unmapped key once per session so the real ids show up in latest.log.
	private static void logUnmappedPerks(JsonObject member) {
		Map<String, String> unmapped = unmappedPerks(member);
		unmapped.keySet().removeIf(key -> !LOGGED_UNMAPPED.add(key));
		if (!unmapped.isEmpty()) {
			BetterPV.LOGGER.info("[BetterPV] Unmapped essence perk keys: {}", unmapped);
		}
	}

	@SafeVarargs
	private static Set<String> mappedKeys(List<Def>... shops) {
		Set<String> keys = new HashSet<>();
		for (List<Def> shop : shops) {
			for (Def def : shop) {
				keys.add(def.id());
				if (def.aliasIds() != null) {
					keys.addAll(List.of(def.aliasIds()));
				}
			}
		}
		return Set.copyOf(keys);
	}

	/** Raw essence balance from {@code currencies.essence.<TYPE>.current}. */
	public static long balance(JsonObject member, String currencyKey) {
		return essenceBalance(member, currencyKey);
	}

	private static DungeonSnapshot.EssenceShop shop(
		String id,
		String name,
		String currencyKey,
		String iconId,
		List<Def> defs,
		JsonObject member
	) {
		JsonObject perks = perks(member);
		List<DungeonSnapshot.EssencePerk> list = new ArrayList<>(defs.size());
		for (Def def : defs) {
			int level = Math.max(0, Math.min(def.maxLevel(), perkLevel(perks, def)));
			list.add(new DungeonSnapshot.EssencePerk(def.id(), def.name(), level, def.maxLevel()));
		}
		return new DungeonSnapshot.EssenceShop(
			id, name, essenceBalance(member, currencyKey), iconId, List.copyOf(list)
		);
	}

	private static JsonObject perks(JsonObject member) {
		JsonObject playerData = Leveling.obj(member == null ? null : member.get("player_data"));
		JsonObject fromPlayer = playerData == null ? null : Leveling.obj(playerData.get("perks"));
		if (fromPlayer != null) {
			return fromPlayer;
		}
		JsonObject dungeons = Leveling.obj(member == null ? null : member.get("dungeons"));
		return dungeons == null ? null : Leveling.obj(dungeons.get("perks"));
	}

	private static int perkLevel(JsonObject perks, Def def) {
		int level = perkLevel(perks, def.id());
		if (level > 0 || def.aliasIds() == null) {
			return level;
		}
		for (String alias : def.aliasIds()) {
			int alt = perkLevel(perks, alias);
			if (alt > 0) {
				return alt;
			}
		}
		return level;
	}

	private static int perkLevel(JsonObject perks, String id) {
		if (perks == null || id == null || !perks.has(id)) {
			return 0;
		}
		JsonElement el = perks.get(id);
		if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
			return el.getAsBoolean() ? 1 : 0;
		}
		Float n = Leveling.num(el);
		return n == null ? 0 : Math.round(n);
	}

	private static long essenceBalance(JsonObject member, String type) {
		JsonObject currencies = Leveling.obj(member == null ? null : member.get("currencies"));
		JsonObject essence = currencies == null ? null : Leveling.obj(currencies.get("essence"));
		JsonObject typed = essence == null ? null : Leveling.obj(essence.get(type));
		if (typed == null) {
			return 0L;
		}
		Float current = Leveling.num(typed.get("current"));
		return current == null ? 0L : Math.round(current);
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import fishmod.pv.networth.InventoryDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

public final class DungeonModifierScanner {
	public record Mods(boolean expertRing, int hecatombLevel, double scarfBonus) {
		public static Mods none() {
			return new Mods(false, 0, 0);
		}
	}

	private static final Map<String, Double> SCARF_BONUS = Map.of(
		"SCARF_STUDIES", 0.02,
		"SCARF_THESIS", 0.04,
		"SCARF_GRIMOIRE", 0.06
	);

	private DungeonModifierScanner() {
	}

	public static Mods scan(Map<String, List<InventoryDecoder.Stack>> categories) {
		if (categories == null || categories.isEmpty()) {
			return Mods.none();
		}
		boolean ring = false;
		int hecatomb = 0;
		double scarf = 0;
		for (List<InventoryDecoder.Stack> stacks : categories.values()) {
			if (stacks == null) {
				continue;
			}
			for (InventoryDecoder.Stack stack : stacks) {
				if (stack == null || stack.id() == null) {
					continue;
				}
				String id = stack.id().toUpperCase(Locale.ROOT);
				if ("CATACOMBS_EXPERT_RING".equals(id)) {
					ring = true;
				}
				Double scarfBonus = SCARF_BONUS.get(id);
				if (scarfBonus != null) {
					scarf = Math.max(scarf, scarfBonus);
				}
				hecatomb = Math.max(hecatomb, hecatombOn(stack));
			}
		}
		return new Mods(ring, hecatomb, scarf);
	}

	/**
	 * Catacombs Graduate attribute shard: +2% class XP per level, max 10 → +20%.
	 * Stored on the profile as {@code attributes.catacombs_graduate}.
	 * When the attribute is absent from the API payload, assume max (Adjectils does the same).
	 */
	public static double readCatacombsGraduateBonus(com.google.gson.JsonObject member) {
		if (member == null) {
			return 0.20;
		}
		Float level = attributeLevel(member, "catacombs_graduate");
		if (level == null) {
			return 0.20;
		}
		if (level <= 0) {
			return 0;
		}
		return Math.min(0.20, level * 0.02);
	}

	private static Float attributeLevel(com.google.gson.JsonObject member, String key) {
		Float direct = num(obj(member.get("attributes")) == null ? null : obj(member.get("attributes")).get(key));
		if (direct != null) {
			return direct;
		}
		com.google.gson.JsonObject playerData = obj(member.get("player_data"));
		if (playerData == null) {
			return null;
		}
		com.google.gson.JsonObject attrs = obj(playerData.get("attributes"));
		return attrs == null ? null : num(attrs.get(key));
	}

	public static Map<String, Double> readEssenceClassBonuses(com.google.gson.JsonObject member) {
		Map<String, Double> out = new HashMap<>();
		com.google.gson.JsonObject dungeons = LevelingObj(member);
		if (dungeons == null) {
			return out;
		}
		// Common shapes: dungeons.perks / player_data.perks / essence upgrades on classes.
		readPerkMap(out, obj(dungeons.get("perks")));
		com.google.gson.JsonObject playerData = obj(member.get("player_data"));
		if (playerData != null) {
			readPerkMap(out, obj(playerData.get("perks")));
		}
		com.google.gson.JsonObject classes = obj(dungeons.get("player_classes"));
		if (classes != null) {
			for (String classId : List.of("mage", "berserk", "archer", "healer", "tank")) {
				com.google.gson.JsonObject clazz = obj(classes.get(classId));
				if (clazz == null) {
					continue;
				}
				Double fromClass = essenceFromClassObject(clazz);
				if (fromClass != null && fromClass > 0) {
					out.merge(classId, fromClass, Math::max);
				}
			}
		}
		return out;
	}

	private static void readPerkMap(Map<String, Double> out, com.google.gson.JsonObject perks) {
		if (perks == null) {
			return;
		}
		putPerk(out, perks, "mage", "cold_efficiency", "mage_xp", "class_xp_mage");
		putPerk(out, perks, "berserk", "unbridled_rage", "berserk_xp", "class_xp_berserk");
		putPerk(out, perks, "archer", "toxophilite", "archer_xp", "class_xp_archer");
		putPerk(out, perks, "healer", "heart_of_gold", "healer_xp", "class_xp_healer");
		putPerk(out, perks, "tank", "diamond_in_the_rough", "tank_xp", "class_xp_tank");
	}

	private static void putPerk(Map<String, Double> out, com.google.gson.JsonObject perks, String classId, String... keys) {
		for (String key : keys) {
			if (!perks.has(key)) {
				continue;
			}
			Float level = num(perks.get(key));
			if (level == null || level <= 0) {
				continue;
			}
			double bonus = Math.min(0.10, level * 0.02);
			out.merge(classId, bonus, Math::max);
		}
	}

	private static Double essenceFromClassObject(com.google.gson.JsonObject clazz) {
		for (String key : List.of("experience_boost", "xp_boost", "class_xp_boost")) {
			if (!clazz.has(key)) {
				continue;
			}
			Float level = num(clazz.get(key));
			if (level != null && level > 0) {
				return Math.min(0.10, level * 0.02);
			}
		}
		return null;
	}

	private static com.google.gson.JsonObject LevelingObj(com.google.gson.JsonObject member) {
		return obj(member == null ? null : member.get("dungeons"));
	}

	private static com.google.gson.JsonObject obj(com.google.gson.JsonElement el) {
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static Float num(com.google.gson.JsonElement el) {
		if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
			return null;
		}
		try {
			return el.getAsFloat();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static int hecatombOn(InventoryDecoder.Stack stack) {
		CompoundTag ea = stack.extraAttributes();
		if (ea == null || !ea.contains("enchantments")) {
			return 0;
		}
		Tag value = ea.get("enchantments");
		if (!(value instanceof CompoundTag map)) {
			return 0;
		}
		for (String key : map.keySet()) {
			if (key != null && key.equalsIgnoreCase("hecatomb")) {
				return clampHec(intValue(map, key));
			}
		}
		return 0;
	}

	private static int clampHec(int level) {
		return Math.max(0, Math.min(10, level));
	}

	private static int intValue(CompoundTag tag, String key) {
		try {
			return tag.getIntOr(key, 0);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			if (value instanceof NumericTag numeric) {
				return numeric.intValue();
			}
			return 0;
		}
	}
}

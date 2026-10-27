/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** {@code dungeons.treasures}: Hypixel only keeps the most recent runs and their reward chests. */
public record DungeonChestHistory(List<Run> runs) {
	public static final long EXPIRY_MS = 72L * 60L * 60L * 1000L;

	public record Chest(String type, boolean paid, int rerolls, List<String> rewards) {
		public Chest {
			type = type == null ? "" : type;
			rewards = List.copyOf(rewards == null ? List.of() : rewards);
		}
	}

	public record Run(String floor, long completedMs, List<String> party, List<Chest> chests) {
		public Run {
			floor = floor == null ? "" : floor;
			party = List.copyOf(party == null ? List.of() : party);
			chests = List.copyOf(chests == null ? List.of() : chests);
		}

		/** Croesus drops runs 72h after completion; the API can keep them longer until the list is rewritten. */
		public long expiresMs() {
			return completedMs <= 0L ? 0L : completedMs + EXPIRY_MS;
		}

		public boolean expired(long nowMs) {
			return completedMs > 0L && nowMs >= expiresMs();
		}

		public Chest opened() {
			Chest best = null;
			for (Chest chest : chests) {
				if (chest.paid() && (best == null || typeRank(chest.type()) > typeRank(best.type()))) {
					best = chest;
				}
			}
			return best;
		}
	}

	public DungeonChestHistory {
		runs = List.copyOf(runs == null ? List.of() : runs);
	}

	public static DungeonChestHistory empty() {
		return new DungeonChestHistory(List.of());
	}

	public boolean present() {
		return !runs.isEmpty();
	}

	public static DungeonChestHistory from(JsonObject member) {
		JsonObject dungeons = Leveling.obj(member == null ? null : member.get("dungeons"));
		JsonObject treasures = Leveling.obj(dungeons == null ? null : dungeons.get("treasures"));
		if (treasures == null) {
			return empty();
		}
		Map<String, List<Chest>> chestsByRun = new LinkedHashMap<>();
		if (treasures.get("chests") != null && treasures.get("chests").isJsonArray()) {
			for (JsonElement el : treasures.getAsJsonArray("chests")) {
				JsonObject c = Leveling.obj(el);
				if (c == null) {
					continue;
				}
				List<String> rewards = new ArrayList<>();
				JsonObject rewardObj = Leveling.obj(c.get("rewards"));
				if (rewardObj != null && rewardObj.get("rewards") != null && rewardObj.get("rewards").isJsonArray()) {
					for (JsonElement r : rewardObj.getAsJsonArray("rewards")) {
						String raw = str(r);
						if (!raw.isBlank()) {
							rewards.add(raw);
						}
					}
				}
				// Kuudra chests use chest_type / is_opened / is_rerolled and carry no reward list.
				String type = str(c.get("treasure_type"));
				if (type.isBlank()) {
					type = str(c.get("chest_type"));
				}
				int rerolls = (int) num(c.get("rerolls"));
				if (rerolls == 0 && bool(c.get("is_rerolled"))) {
					rerolls = 1;
				}
				chestsByRun.computeIfAbsent(str(c.get("run_id")), k -> new ArrayList<>()).add(new Chest(
					type,
					bool(c.get("paid")) || bool(c.get("is_opened")),
					rerolls,
					rewards
				));
			}
		}
		List<Run> runs = new ArrayList<>();
		if (treasures.get("runs") != null && treasures.get("runs").isJsonArray()) {
			for (JsonElement el : treasures.getAsJsonArray("runs")) {
				JsonObject r = Leveling.obj(el);
				if (r == null) {
					continue;
				}
				List<String> party = new ArrayList<>();
				if (r.get("participants") != null && r.get("participants").isJsonArray()) {
					for (JsonElement p : r.getAsJsonArray("participants")) {
						JsonObject po = Leveling.obj(p);
						// Keeps Hypixel's § codes so the UI can show rank colours.
						String name = po == null ? "" : str(po.get("display_name")).trim();
						if (!fishmod.utils.TextStrip.plain(name).isBlank()) {
							party.add(name);
						}
					}
				}
				List<Chest> chests = new ArrayList<>(chestsByRun.getOrDefault(str(r.get("run_id")), List.of()));
				chests.sort(Comparator.comparingInt((Chest c) -> typeRank(c.type())).reversed());
				runs.add(new Run(
					floorLabel(str(r.get("type")), str(r.get("dungeon_type")),
						r.has("dungeon_tier") ? r.get("dungeon_tier") : r.get("tier_id")),
					num(r.get("completion_ts")),
					party,
					chests
				));
			}
		}
		runs.sort(Comparator.comparingLong(Run::completedMs).reversed());
		return new DungeonChestHistory(runs);
	}

	public static int typeRank(String type) {
		return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
			case "free" -> 1;
			case "paid" -> 2;
			case "wood" -> 1;
			case "gold" -> 2;
			case "diamond" -> 3;
			case "emerald" -> 4;
			case "obsidian" -> 5;
			case "bedrock" -> 6;
			default -> 0;
		};
	}

	/** {@code ESSENCE:UNDEAD:22} → "22x Undead Essence", {@code ultimate_wise_1} → "Ultimate Wise I". */
	public static String prettyReward(String raw) {
		return prettyReward(raw, HypixelItemsCache::get, NeuRepoCache::get);
	}

	static String prettyReward(String raw, Function<String, JsonObject> hypixel, Function<String, JsonObject> neu) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		if (raw.startsWith("ESSENCE:")) {
			String[] parts = raw.split(":");
			String type = parts.length > 1 ? InventoryDecoder.prettyWords(parts[1]) : "";
			return parts.length > 2 ? parts[2] + "x " + type + " Essence" : type + " Essence";
		}
		if (raw.startsWith("SHARD:")) {
			String[] parts = raw.split(":");
			String type = parts.length > 1 ? InventoryDecoder.prettyWords(parts[1]) : "";
			int count = parts.length > 2 ? (int) Math.max(1L, parseLongOr(parts[2], 1L)) : 1;
			return (count > 1 ? count + "x " : "") + type + " Shard";
		}
		String id = raw.toLowerCase(Locale.ROOT);
		String itemName = rewardItemName(id, hypixel, neu);
		if (itemName != null) {
			return itemName;
		}
		if (isBook(raw)) {
			int us = id.lastIndexOf('_');
			int level = Integer.parseInt(id.substring(us + 1));
			String fromNeu = bookLoreName(bookDef(id, neu), level);
			if (fromNeu != null) {
				return fromNeu;
			}
			String core = enchantCore(id.substring(0, us));
			String name = InventoryDecoder.prettyWords(core) + " " + roman(level);
			return NAMED_ULTIMATES.contains(core) ? "Ultimate " + name : name;
		}
		return InventoryDecoder.prettyWords(id);
	}

	// Ultimate enchants whose in-game name keeps the "Ultimate" word (NEU ULTIMATE_WISE / ULTIMATE_JERRY lore).
	private static final Set<String> NAMED_ULTIMATES = Set.of("wise", "jerry");

	private static String enchantCore(String base) {
		if ("master_jerry".equals(base)) {
			return "jerry";
		}
		return base.startsWith("ultimate_") ? base.substring("ultimate_".length()) : base;
	}

	/** NEU enchanted book ({@code ULTIMATE_WISE;1} / {@code FEATHER_FALLING;6}) for a Croesus book id. */
	private static JsonObject bookDef(String id, Function<String, JsonObject> neu) {
		int us = id.lastIndexOf('_');
		String level = id.substring(us + 1);
		String core = enchantCore(id.substring(0, us)).toUpperCase(Locale.ROOT);
		JsonObject def = neu.apply("ULTIMATE_" + core + ";" + level);
		return def != null ? def : neu.apply(core + ";" + level);
	}

	private static boolean isUltimateBook(JsonObject def) {
		return def != null && def.has("internalname") && def.get("internalname").isJsonPrimitive()
			&& def.get("internalname").getAsString().toUpperCase(Locale.ROOT).startsWith("ULTIMATE_");
	}

	/** The enchant line of a NEU book's lore, e.g. "Ultimate Wise I", with formatting stripped. */
	private static String bookLoreName(JsonObject def, int level) {
		if (def == null || !def.has("lore") || !def.get("lore").isJsonArray()) {
			return null;
		}
		String suffix = " " + roman(level);
		for (JsonElement line : def.getAsJsonArray("lore")) {
			String text = str(line).replaceAll("\u00a7.", "").trim();
			if (text.endsWith(suffix)) {
				return text;
			}
		}
		return null;
	}

	public static boolean isEssence(String raw) {
		return raw != null && raw.startsWith("ESSENCE:");
	}

	/** Enchanted-book style rewards end in a level, e.g. {@code feather_falling_6}. */
	public static boolean isBook(String raw) {
		if (raw == null || isEssence(raw) || raw.contains("tier")) {
			return false;
		}
		int us = raw.lastIndexOf('_');
		if (us <= 0 || us >= raw.length() - 1 || !raw.substring(us + 1).chars().allMatch(Character::isDigit)) {
			return false;
		}
		// Enchant levels stop at 10; keeps items like recombobulator_3000 out.
		return parseLongOr(raw.substring(us + 1), 0L) <= 10L;
	}

	// Croesus reward ids that aren't the item's SkyBlock id. Dyes are handled by itemIdFor.
	private static final Map<String, String> REWARD_ITEM_IDS = Map.of(
		"shadow_helmet", "SHADOW_ASSASSIN_HELMET",
		"shadow_chestplate", "SHADOW_ASSASSIN_CHESTPLATE",
		"shadow_leggings", "SHADOW_ASSASSIN_LEGGINGS",
		"shadow_boots", "SHADOW_ASSASSIN_BOOTS",
		"necro_brooch", "NECROMANCER_BROOCH"
	);

	// NEU constants/enchants.json ultimate pool, without the ultimate_ prefix. Croesus ids drop it too.
	private static final java.util.Set<String> ULTIMATE_ENCHANTS = java.util.Set.of(
		"chimera", "combo", "swarm", "wise", "rend", "bank", "last_stand", "legion", "no_pain_no_gain",
		"wisdom", "one_for_all", "soul_eater", "reiterate", "inferno", "fatal_tempo", "flash",
		"habanero_tactics", "bobbin_time", "refrigerate", "flowstate", "missile", "first_impression",
		"sunset", "master_jerry", "jerry", "duplex", "the_one"
	);

	/**
	 * SkyBlock rarity for a reward ({@code EPIC}, {@code LEGENDARY}, ...), or {@code ESSENCE},
	 * {@code ULTIMATE} / {@code BOOK} for enchanted books. Empty when unknown.
	 */
	public static String rewardTier(String raw) {
		return rewardTier(raw, HypixelItemsCache::get, NeuRepoCache::get);
	}

	static String rewardTier(String raw, Function<String, JsonObject> hypixel, Function<String, JsonObject> neu) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		if (isEssence(raw)) {
			return "ESSENCE";
		}
		String itemId;
		if (raw.startsWith("SHARD:")) {
			String[] parts = raw.split(":");
			itemId = parts.length > 1 ? "SHARD_" + parts[1].toUpperCase(Locale.ROOT) : "";
		} else {
			itemId = itemIdFor(raw.toLowerCase(Locale.ROOT));
		}
		JsonObject def = hypixel.apply(itemId);
		if (def != null && def.has("tier") && def.get("tier").isJsonPrimitive()) {
			return def.get("tier").getAsString().toUpperCase(Locale.ROOT);
		}
		if (isBook(raw)) {
			String id = raw.toLowerCase(Locale.ROOT);
			JsonObject book = bookDef(id, neu);
			if (book != null) {
				return isUltimateBook(book) ? "ULTIMATE" : "BOOK";
			}
			String base = id.substring(0, id.lastIndexOf('_'));
			return ULTIMATE_ENCHANTS.contains(enchantCore(base)) ? "ULTIMATE" : "BOOK";
		}
		return neuRarity(neu.apply(itemId));
	}

	private static final List<String> RARITIES = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "SUPREME"
	);

	/** Rarity from the last NEU lore line, e.g. "EPIC DYE" → EPIC. */
	private static String neuRarity(JsonObject def) {
		if (def == null || !def.has("lore") || !def.get("lore").isJsonArray() || def.getAsJsonArray("lore").isEmpty()) {
			return "";
		}
		var lore = def.getAsJsonArray("lore");
		String last = str(lore.get(lore.size() - 1)).replaceAll("\u00a7.", "").trim().toUpperCase(Locale.ROOT);
		String rarity = "";
		for (String r : RARITIES) {
			String spaced = r.replace('_', ' ');
			if ((last.equals(spaced) || last.startsWith(spaced + " ")) && spaced.length() > rarity.length()) {
				rarity = r;
			}
		}
		return rarity;
	}

	/** SkyBlock item id for a Croesus reward id: {@code necron_dye} → {@code DYE_NECRON}. */
	static String itemIdFor(String id) {
		String alias = REWARD_ITEM_IDS.get(id);
		if (alias != null) {
			return alias;
		}
		if (id.endsWith("_dye") && id.length() > 4) {
			return "DYE_" + id.substring(0, id.length() - 4).toUpperCase(Locale.ROOT);
		}
		return id.toUpperCase(Locale.ROOT);
	}

	private static String rewardItemName(String id, Function<String, JsonObject> hypixel, Function<String, JsonObject> neu) {
		String itemId = itemIdFor(id);
		JsonObject def = hypixel.apply(itemId);
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			String name = def.get("name").getAsString().replaceAll("\u00a7.", "").trim();
			if (!name.isBlank()) {
				return name;
			}
		}
		JsonObject neuDef = neu.apply(itemId);
		if (neuDef != null && neuDef.has("displayname") && neuDef.get("displayname").isJsonPrimitive()) {
			String name = neuDef.get("displayname").getAsString().replaceAll("\u00a7.", "").trim();
			if (!name.isBlank()) {
				return name;
			}
		}
		String alias = REWARD_ITEM_IDS.get(id);
		return alias != null ? InventoryDecoder.prettyWords(alias.toLowerCase(Locale.ROOT)) : null;
	}

	private static long parseLongOr(String s, long fallback) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static final String[] KUUDRA_TIERS = { "Basic", "Hot", "Burning", "Fiery", "Infernal" };

	// Kuudra runs have type KUUDRA and tier_id (e.g. "INFERNAL") instead of dungeon_type / dungeon_tier.
	private static String floorLabel(String runType, String dungeonType, JsonElement tierEl) {
		String type = dungeonType == null ? "" : dungeonType.toLowerCase(Locale.ROOT);
		String tierName = str(tierEl).toLowerCase(Locale.ROOT);
		int tier = (int) num(tierEl);
		if ("kuudra".equalsIgnoreCase(runType) || type.contains("kuudra")) {
			for (String name : KUUDRA_TIERS) {
				if (tierName.equals(name.toLowerCase(Locale.ROOT)) || type.contains(name.toLowerCase(Locale.ROOT))) {
					return "Kuudra " + name;
				}
			}
			if ("none".equals(tierName) || "kuudra".equals(type) && tier == 0) {
				return "Kuudra Basic";
			}
			return tier >= 1 && tier <= KUUDRA_TIERS.length ? "Kuudra " + KUUDRA_TIERS[tier - 1] : "Kuudra";
		}
		boolean master = type.startsWith("master");
		if (tier <= 0) {
			return master ? "M?" : "Entrance";
		}
		return (master ? "M" : "F") + tier;
	}

	public static boolean isKuudra(String floor) {
		return floor != null && floor.startsWith("Kuudra");
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	private static String str(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		try {
			return el.getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static long num(JsonElement el) {
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
			return 0L;
		}
		return Math.max(0L, (long) el.getAsDouble());
	}

	private static boolean bool(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return false;
		}
		try {
			return el.getAsBoolean();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}
}

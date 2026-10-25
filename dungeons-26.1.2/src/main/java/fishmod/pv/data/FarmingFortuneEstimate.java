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
import fishmod.pv.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Farming Fortune for the best farming setup found on the profile. The API only has what was last saved, so this
 * is an estimate built from item lore plus the permanent bonuses below (checked against EliteFarmers'
 * FarmingWeight constants). Sources the API doesn't expose are listed as unavailable instead of guessed.
 */
public final class FarmingFortuneEstimate {
	public static final List<String> CROPS = List.of(
		"Wheat", "Carrot", "Potato", "Pumpkin", "Sugar Cane", "Melon", "Cactus",
		"Cocoa Beans", "Mushroom", "Nether Wart", "Sunflower", "Moonflower", "Wild Rose"
	);
	public static final List<String> ARMOUR_SLOTS = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
	public static final List<String> EQUIPMENT_SLOTS = List.of("NECKLACE", "CLOAK", "BELT", "GLOVES");
	public static final double CROP_UPGRADE_FORTUNE = 5D;
	static final int FARMING_LEVEL_CAP = 60;
	static final int ANITA_CAP = 15;
	static final int PLOTS_MAX = 24;
	static final int GARDEN_TIERS_MAX = 250;
	static final int DNA_MILESTONE_MAX = 6;
	static final List<String> SHARD_IDS = List.of(
		"fortunate_farmer", "ultimate_dna", "solar_power", "lunar_power", "infiltration", "filter_upgrade",
		"pest_fortune"
	);
	private static final String[] SOURCES = {
		"armor", "equipment", "inventory", "enderchest", "storage", "wardrobe", "personal_vault"
	};
	private static final Pattern CROP_LINE = Pattern.compile("^([A-Za-z ]+?) Fortune: \\+([0-9]+(?:\\.[0-9]+)?)");
	private static final Pattern PIECE_BONUS = Pattern.compile("^Piece Bonus: \\+([0-9]+(?:\\.[0-9]+)?)");
	private static final List<String> PEST_IDS = List.of(
		"beetle", "cricket", "worm", "fly", "locust", "mite", "mosquito", "moth", "rat", "slug", "mouse",
		"dragonfly", "firefly", "praying_mantis", "lunar_moth"
	);
	// NEU bestiary bracket 7; every other pest uses bracket 6.
	private static final Set<String> SMALL_BRACKET_PESTS = Set.of("mouse", "lunar_moth");
	private static final int[] PEST_BRACKETS = {1, 2, 3, 5, 7, 9, 14, 17, 21, 25, 50, 80, 125, 175, 250};
	private static final int[] MOUSE_BRACKETS = {1, 2, 3, 5, 7, 9, 11, 14, 17, 20, 30, 40, 55, 75, 100};
	// Non-pest garden bestiary mobs: bracket 7, Timestalk Clone capped at 20 kills (10 tiers).
	private static final int[] TIMESTALK_BRACKETS = {1, 2, 3, 5, 7, 9, 11, 14, 17, 20};
	private static final String DNA_MILESTONE_PREFIX = "dna_analysis_rewardfarming_fortune_";
	private static final Map<String, Integer> PB_CROPS_PER_TIER = Map.ofEntries(
		Map.entry("Wheat", 100), Map.entry("Carrot", 300), Map.entry("Potato", 300), Map.entry("Pumpkin", 100),
		Map.entry("Melon", 500), Map.entry("Mushroom", 100), Map.entry("Cactus", 200), Map.entry("Sugar Cane", 200),
		Map.entry("Nether Wart", 300), Map.entry("Cocoa Beans", 300), Map.entry("Sunflower", 200),
		Map.entry("Moonflower", 200), Map.entry("Wild Rose", 200)
	);
	// Highest owned tier wins; Helianthus Relic applies to every crop.
	private static final List<String> FERMENTO_FAMILY = List.of(
		"HELIANTHUS_RELIC", "FERMENTO_ARTIFACT", "SQUASH_RING", "CROPIE_TALISMAN"
	);
	private static final Map<String, Double> FERMENTO_FORTUNE = Map.of(
		"HELIANTHUS_RELIC", 40D, "FERMENTO_ARTIFACT", 30D, "SQUASH_RING", 20D, "CROPIE_TALISMAN", 10D
	);
	private static final Map<String, String> FERMENTO_NAMES = Map.of(
		"FERMENTO_ARTIFACT", "Fermento Artifact", "SQUASH_RING", "Squash Ring", "CROPIE_TALISMAN", "Cropie Talisman"
	);
	private static final Map<String, List<String>> FERMENTO_CROPS = Map.of(
		"FERMENTO_ARTIFACT", List.of("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cocoa Beans",
			"Nether Wart", "Cactus", "Sugar Cane"),
		"SQUASH_RING", List.of("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cocoa Beans"),
		"CROPIE_TALISMAN", List.of("Wheat", "Carrot", "Potato")
	);
	// Tiered set bonus for 2, 3 and 4 pieces of one armour family.
	private static final Map<String, int[]> SET_BONUS = Map.of(
		"MELON", new int[] {10, 20, 30},
		"CROPIE", new int[] {15, 30, 45},
		"SQUASH", new int[] {20, 40, 60},
		"FERMENTO", new int[] {25, 50, 75},
		"HELIANTHUS", new int[] {25, 50, 75},
		"RABBIT", new int[] {0, 0, 10}
	);
	private static final Map<String, String> SET_NAMES = Map.of(
		"MELON", "Cropier Crops", "CROPIE", "Squashbuckle", "SQUASH", "Mento Fermento",
		"FERMENTO", "Feast", "HELIANTHUS", "Feast", "RABBIT", "Bonus Farming Fortune"
	);
	private static final List<String> RARITIES = FarmingToolFortune.RARITIES;
	private static final String RARITY_CODES = "fa956d";
	// Farming pets other than Rose Dragon, for Symbiosis.
	private static final Set<String> SYMBIOSIS_PETS = Set.of(
		"BEE", "CHICKEN", "ELEPHANT", "HEDGEHOG", "MOOSHROOM_COW", "MOSQUITO", "ORCHID_MANTIS", "PIG", "RABBIT", "SLUG"
	);

	/** Only counted lines add to the total; conditional ones are active some of the time and listed separately. */
	public enum Kind { COUNTED, CONDITIONAL, UNAVAILABLE }

	public record Line(String label, double value, Kind kind, String note) {
		static Line counted(String label, double value, String note) {
			return new Line(label, value, Kind.COUNTED, note);
		}

		static Line conditional(String label, double value, String note) {
			return new Line(label, value, Kind.CONDITIONAL, note);
		}

		static Line unavailable(String label, String note) {
			return new Line(label, 0D, Kind.UNAVAILABLE, note);
		}
	}

	public record Piece(String slot, InventoryDecoder.Stack stack, double fortune, double pieceBonus, Map<String, Double> cropFortune) {
		public double total() {
			return fortune + pieceBonus;
		}
	}

	public record SetBonus(String family, String name, int pieces, double fortune) {
	}

	public record Accessory(InventoryDecoder.Stack stack, double fortune) {
	}

	/** A tool valued for one crop; {@code parts} is the attribute breakdown. */
	public record Tool(InventoryDecoder.Stack stack, boolean fromToolkit, String rarity, List<Line> parts) {
		public double total() {
			return sum(parts);
		}
	}

	record ToolCandidate(InventoryDecoder.Stack stack, boolean fromToolkit, FarmingToolFortune.Info info) {
	}

	/** A pet valued for one crop: ability and stat lines, plus the held item when it gives fortune. */
	public record PetPick(PetSnapshot.Entry pet, List<Line> lines, Line item) {
		public double total() {
			return sum(lines) + (item == null ? 0D : sum(List.of(item)));
		}
	}

	/** Island data loads separately from the profile, so plots, garden level and crop upgrades may be missing. */
	public record Garden(
		boolean loaded, int plots, int level, Map<String, Integer> upgrades, Map<String, Integer> milestones,
		long uniqueVisitors
	) {
		public static final Garden NONE = new Garden(false, 0, 0, Map.of(), Map.of(), 0L);

		public static Garden of(GardenSnapshot snap) {
			if (snap == null || !snap.islandLoaded()) {
				return NONE;
			}
			Map<String, Integer> upgrades = new HashMap<>();
			Map<String, Integer> milestones = new HashMap<>();
			for (GardenSnapshot.CropRow row : snap.crops()) {
				upgrades.put(row.name(), row.upgradeLevel());
				milestones.put(row.name(), row.milestone());
			}
			return new Garden(true, snap.plotsUnlocked(), snap.gardenLevel(), upgrades, milestones, snap.uniqueVisitors());
		}
	}

	/**
	 * {@code toolkit} holds tools still stored in the toolkit; slots marked in use are skipped because the tool is in
	 * the inventory. {@code toolkitUnreadable} lists crops whose slot had data that didn't decode.
	 */
	record Inputs(
		boolean inventoryKnown,
		boolean toolkitKnown,
		boolean toolkitUnlocked,
		List<InventoryDecoder.Stack> toolkit,
		Set<String> toolkitUnreadable,
		boolean axedPerk,
		int farmingLevel,
		int anitaLevel,
		int pestTiers,
		int gardenTiers,
		int dnaMilestone,
		int cropshotLevel,
		Map<String, Integer> shards,
		boolean personalBestsUnlocked,
		Map<String, Long> personalBests,
		List<PetSnapshot.Entry> pets,
		FarmingFortuneTemporary.Buffs buffs
	) {
		Inputs(
			boolean inventoryKnown, boolean toolkitKnown, boolean toolkitUnlocked, List<InventoryDecoder.Stack> toolkit,
			Set<String> toolkitUnreadable, boolean axedPerk, int farmingLevel, int anitaLevel, int pestTiers,
			int gardenTiers, int dnaMilestone, int cropshotLevel, Map<String, Integer> shards,
			boolean personalBestsUnlocked, Map<String, Long> personalBests, List<PetSnapshot.Entry> pets
		) {
			this(inventoryKnown, toolkitKnown, toolkitUnlocked, toolkit, toolkitUnreadable, axedPerk, farmingLevel,
				anitaLevel, pestTiers, gardenTiers, dnaMilestone, cropshotLevel, shards, personalBestsUnlocked,
				personalBests, pets, FarmingFortuneTemporary.Buffs.NONE);
		}

		Inputs {
			buffs = buffs == null ? FarmingFortuneTemporary.Buffs.NONE : buffs;
		}
	}

	private final Inputs inputs;
	private final List<Piece> armour;
	private final List<SetBonus> setBonuses;
	private final List<Piece> equipment;
	private final List<Accessory> accessories;
	private final InventoryDecoder.Stack fermento;
	private final InventoryDecoder.Stack magic8Ball;
	private final boolean atmosphericFilter;
	private final InventoryDecoder.Stack anita;
	private final boolean truffle;
	private final List<ToolCandidate> tools;

	private FarmingFortuneEstimate(
		Inputs inputs, List<Piece> armour, List<SetBonus> setBonuses, List<Piece> equipment,
		List<Accessory> accessories, InventoryDecoder.Stack fermento, InventoryDecoder.Stack magic8Ball,
		boolean atmosphericFilter, InventoryDecoder.Stack anita, boolean truffle, List<ToolCandidate> tools
	) {
		this.inputs = inputs;
		this.armour = List.copyOf(armour);
		this.setBonuses = List.copyOf(setBonuses);
		this.equipment = List.copyOf(equipment);
		this.accessories = List.copyOf(accessories);
		this.fermento = fermento;
		this.magic8Ball = magic8Ball;
		this.atmosphericFilter = atmosphericFilter;
		this.anita = anita;
		this.truffle = truffle;
		this.tools = List.copyOf(tools);
	}

	public static PlayerStatsSnapshot attach(
		PlayerStatsSnapshot stats, JsonObject member, Map<String, List<InventoryDecoder.Stack>> categories
	) {
		if (stats == null || member == null || categories == null) {
			return stats;
		}
		return stats.withFarmingFortune(from(inputs(member), categories));
	}

	static FarmingFortuneEstimate from(Inputs in, Map<String, List<InventoryDecoder.Stack>> all) {
		Map<String, Piece> bestArmour = new LinkedHashMap<>();
		Map<String, Piece> bestEquipment = new LinkedHashMap<>();
		List<ToolCandidate> bestTools = new ArrayList<>();
		Set<String> seenTools = new HashSet<>();
		boolean truffle = false;
		for (String source : SOURCES) {
			for (InventoryDecoder.Stack stack : all.getOrDefault(source, List.of())) {
				consider(stack, false, bestArmour, bestEquipment, bestTools, seenTools);
				truffle |= stack != null && "REFINED_DARK_CACAO_TRUFFLE".equalsIgnoreCase(stack.id());
			}
		}
		for (InventoryDecoder.Stack stack : in.toolkit()) {
			consider(stack, true, bestArmour, bestEquipment, bestTools, seenTools);
		}

		List<Piece> armour = ordered(bestArmour, ARMOUR_SLOTS);
		Map<String, Integer> families = new LinkedHashMap<>();
		for (Piece p : armour) {
			String family = family(p.stack().id());
			if (family != null) {
				families.merge(family, 1, Integer::sum);
			}
		}
		List<SetBonus> sets = new ArrayList<>();
		for (var e : families.entrySet()) {
			int count = e.getValue();
			double ff = count >= 2 ? SET_BONUS.get(e.getKey())[Math.min(count, 4) - 2] : 0D;
			if (ff > 0D) {
				sets.add(new SetBonus(e.getKey(), SET_NAMES.get(e.getKey()), count, ff));
			}
		}

		List<Accessory> accessories = new ArrayList<>();
		Set<String> seenAccessories = new HashSet<>();
		InventoryDecoder.Stack fermento = null;
		InventoryDecoder.Stack magic8Ball = null;
		boolean atmosphericFilter = false;
		InventoryDecoder.Stack anita = null;
		List<String> anitaFamily = FarmingFortuneTemporary.ANITA_FAMILY;
		for (InventoryDecoder.Stack stack : all.getOrDefault("accessories", List.of())) {
			if (stack == null || stack.id() == null) {
				continue;
			}
			String id = stack.id().toUpperCase(Locale.ROOT);
			int tier = FERMENTO_FAMILY.indexOf(id);
			if (tier >= 0) {
				if (fermento == null || tier < FERMENTO_FAMILY.indexOf(fermento.id().toUpperCase(Locale.ROOT))) {
					fermento = stack;
				}
				continue;
			}
			int anitaTier = anitaFamily.indexOf(id);
			if (anitaTier >= 0) {
				if (anita == null || anitaTier < anitaFamily.indexOf(anita.id().toUpperCase(Locale.ROOT))) {
					anita = stack;
				}
				continue;
			}
			if ("MAGIC_8_BALL".equals(id)) {
				magic8Ball = stack;
				continue;
			}
			if ("ATMOSPHERIC_FILTER".equals(id)) {
				atmosphericFilter = true;
				continue;
			}
			if (!seenAccessories.add(id)) {
				continue;
			}
			double ff = fortune(stack);
			if (ff > 0D) {
				accessories.add(new Accessory(stack, ff));
			}
		}
		return new FarmingFortuneEstimate(
			in, armour, sets, ordered(bestEquipment, EQUIPMENT_SLOTS), accessories, fermento, magic8Ball,
			atmosphericFilter, anita, truffle, bestTools
		);
	}

	private static void consider(
		InventoryDecoder.Stack stack, boolean fromToolkit, Map<String, Piece> bestArmour,
		Map<String, Piece> bestEquipment, List<ToolCandidate> tools, Set<String> seenTools
	) {
		if (stack == null || stack.id() == null) {
			return;
		}
		FarmingToolFortune.Info info = FarmingToolFortune.info(stack.id());
		if (info != null) {
			String uuid = NbtAttrs.string(stack.extraAttributes(), "uuid");
			if (uuid != null && !uuid.isBlank() && !seenTools.add(uuid)) {
				return;
			}
			tools.add(new ToolCandidate(stack, fromToolkit, info));
			return;
		}
		if (stack.lore() == null) {
			return;
		}
		String slot = slot(stack.lore());
		if (ARMOUR_SLOTS.contains(slot)) {
			String family = family(stack.id());
			double ff = fortune(stack);
			if (ff <= 0D && family == null) {
				return;
			}
			Piece piece = new Piece(slot, stack, ff, 0D, cropLines(stack));
			Piece held = bestArmour.get(slot);
			if (held == null || armourScore(piece) > armourScore(held)) {
				bestArmour.put(slot, piece);
			}
		} else if (EQUIPMENT_SLOTS.contains(slot)) {
			Piece piece = new Piece(slot, stack, fortune(stack), pieceBonus(stack.lore()), cropLines(stack));
			if (piece.total() <= 0D) {
				return;
			}
			Piece held = bestEquipment.get(slot);
			if (held == null || piece.total() > held.total()) {
				bestEquipment.put(slot, piece);
			}
		}
	}

	// A set piece is worth its stat line plus its share of the family's set bonus.
	private static double armourScore(Piece piece) {
		String family = family(piece.stack().id());
		return piece.fortune() + (family == null ? 0D : SET_BONUS.get(family)[0]);
	}

	static Inputs inputs(JsonObject member) {
		JsonObject inventory = Leveling.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = Leveling.obj(member.get("inventories"));
		}
		JsonObject gpd = Leveling.obj(member.get("garden_player_data"));
		JsonElement toolkitEl = gpd == null ? null : gpd.get("farming_toolkit");
		JsonObject toolkit = Leveling.obj(toolkitEl);
		JsonObject jacobs = Leveling.obj(member.get("jacobs_contest"));
		JsonObject perks = jacobs == null ? null : Leveling.obj(jacobs.get("perks"));
		JsonObject bestiary = Leveling.obj(member.get("bestiary"));
		JsonObject playerData = Leveling.obj(member.get("player_data"));

		int farming;
		try {
			farming = Leveling.skillLevel(member, "farming");
		} catch (RuntimeException e) {
			farming = 0;
		}
		Map<String, Long> pbs = new LinkedHashMap<>();
		JsonObject pbObj = jacobs == null ? null : Leveling.obj(jacobs.get("personal_bests"));
		if (pbObj != null) {
			for (var e : pbObj.entrySet()) {
				String crop = GardenData.prettyCrop(e.getKey());
				if (CROPS.contains(crop)) {
					pbs.merge(crop, Leveling.longAmount(e.getValue()), Math::max);
				}
			}
		}
		List<PetSnapshot.Entry> pets;
		try {
			pets = PetSnapshot.fromMember(member).pets();
		} catch (RuntimeException e) {
			pets = List.of();
		}
		List<InventoryDecoder.Stack> toolkitStacks = new ArrayList<>();
		Set<String> unreadable = new HashSet<>();
		for (InventoryDecoder.ToolkitItem item : InventoryDecoder.parseToolkit(toolkit)) {
			if (item.inUse()) {
				continue;
			}
			if (item.stack() != null) {
				toolkitStacks.add(item.stack());
			} else {
				unreadable.add(GardenData.prettyCrop(item.category()));
			}
		}
		JsonObject playerPerks = playerData == null ? null : Leveling.obj(playerData.get("perks"));
		JsonObject kills = bestiary == null ? null : Leveling.obj(bestiary.get("kills"));
		return new Inputs(
			inventory != null && inventory.has("inv_contents"),
			toolkit != null,
			boolOf(toolkit, "IS_UNLOCKED"),
			toolkitStacks,
			unreadable,
			intOf(playerPerks, "axed") > 0,
			farming,
			intOf(perks, "double_drops"),
			pestTiers(kills),
			gardenTiers(kills),
			dnaMilestone(member),
			chipLevel(playerData == null ? null : Leveling.obj(playerData.get("garden_chips")), "cropshot"),
			PlayerStatsCalculator.attributeLevels(member, SHARD_IDS),
			boolOf(perks, "personal_bests"),
			pbs,
			pets,
			FarmingFortuneTemporary.buffs(playerData)
		);
	}

	static int pestTiers(JsonObject kills) {
		if (kills == null) {
			return 0;
		}
		int tiers = 0;
		for (String pest : PEST_IDS) {
			int[] brackets = SMALL_BRACKET_PESTS.contains(pest) ? MOUSE_BRACKETS : PEST_BRACKETS;
			tiers += tiers(kills, "pest_" + pest + "_1", brackets);
		}
		return tiers;
	}

	/** Every garden bestiary mob, which is what the +0.4 per tier counts. Hunter's Insight only counts pests. */
	static int gardenTiers(JsonObject kills) {
		if (kills == null) {
			return 0;
		}
		return pestTiers(kills) + tiers(kills, "zombuddy_1", MOUSE_BRACKETS)
			+ tiers(kills, "timestalk_clone_100", TIMESTALK_BRACKETS);
	}

	private static int tiers(JsonObject kills, String key, int[] brackets) {
		long count = Leveling.longAmount(kills.get(key));
		for (int i = brackets.length - 1; i >= 0; i--) {
			if (count >= brackets[i]) {
				return i + 1;
			}
		}
		return 0;
	}

	// Each claimed DNA Analysis reward is an objectives.tutorial entry ending in its level.
	static int dnaMilestone(JsonObject member) {
		JsonObject objectives = Leveling.obj(member.get("objectives"));
		JsonElement tutorial = objectives == null ? null : objectives.get("tutorial");
		if (tutorial == null || !tutorial.isJsonArray()) {
			return 0;
		}
		int level = 0;
		for (JsonElement el : tutorial.getAsJsonArray()) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			String entry = el.getAsString();
			if (entry.startsWith(DNA_MILESTONE_PREFIX)) {
				try {
					level = Math.max(level, Integer.parseInt(entry.substring(DNA_MILESTONE_PREFIX.length())));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return Math.min(DNA_MILESTONE_MAX, level);
	}

	static int chipLevel(JsonObject chips, String chip) {
		if (chips == null) {
			return 0;
		}
		for (var e : chips.entrySet()) {
			if (!chip.equals(GardenData.normalizeChipKey(e.getKey()))) {
				continue;
			}
			Float n = Leveling.num(e.getValue());
			if (n == null && e.getValue() != null && e.getValue().isJsonObject()) {
				n = Leveling.num(e.getValue().getAsJsonObject().get("level"));
			}
			return n == null ? 0 : Math.max(0, Math.min(20, Math.round(n)));
		}
		return 0;
	}

	static double cropshotFortune(int level) {
		int per = level > 15 ? 5 : level > 10 ? 4 : 3;
		return per * level;
	}

	static double personalBestFortune(String crop, long amount) {
		Integer per = PB_CROPS_PER_TIER.get(crop);
		if (per == null || amount <= 0L) {
			return 0D;
		}
		return Math.min(100D, Math.floor((double) amount / per) / 100D);
	}

	// NEU petnums values scale linearly from level 1 to max; Rose Dragon's levels 101 to 200 map onto 1 to 100.
	static double petScale(double atOne, double atMax, int level) {
		int n = Math.max(1, Math.min(100, level));
		return atOne + (atMax - atOne) * (n - 1) / 99D;
	}

	/**
	 * Pet abilities and stats for {@code crop} (null for fortune that applies to every crop), without the held item.
	 * Empty when the pet gives no Farming Fortune. Values come from NEU petnums and the current pet lore.
	 */
	List<Line> petLines(PetSnapshot.Entry pet, String crop, Garden garden) {
		String type = pet.type() == null ? "" : pet.type().toUpperCase(Locale.ROOT);
		int rarity = RARITIES.indexOf(pet.tier() == null ? "" : pet.tier().toUpperCase(Locale.ROOT));
		int level = pet.level();
		List<Line> out = new ArrayList<>();
		switch (type) {
			case "ROSE_DRAGON" -> {
				int n = level - 100;
				out.add(Line.counted("Farming Fortune stat", petScale(20.4D, 40D, n), ""));
				int farming = Math.min(FARMING_LEVEL_CAP, inputs.farmingLevel());
				out.add(Line.counted("Garden Power", petScale(1.52D, 3D, n) * farming, "Farming " + farming));
				if (garden.loaded()) {
					int milestones = 0;
					for (int m : garden.milestones().values()) {
						milestones += m;
					}
					out.add(Line.counted("Rosy Scales", petScale(0.0765D, 0.15D, n) * milestones, milestones + " crop milestones"));
				} else {
					out.add(Line.unavailable("Rosy Scales", "Needs crop milestones, garden not loaded"));
				}
				if (level >= 200) {
					int maxed = maxedFarmingPets();
					out.add(Line.counted("Symbiosis", 3D * maxed, maxed + " other maxed farming pets"));
				}
			}
			case "HEDGEHOG" -> {
				out.add(Line.counted("Hunter's Insight", 0.7D * inputs.pestTiers(), inputs.pestTiers() + " pest bestiary tiers"));
				out.add(Line.conditional("Fearsome Farmer", petScale(1D, 100D, level), "Only on pests"));
			}
			case "ELEPHANT" -> {
				if (rarity >= 5) {
					out.add(Line.counted("Farming Fortune stat", petScale(0.5D, 50D, level), ""));
				}
				if (rarity >= 4) {
					out.add(Line.counted("Trunk Efficiency", petScale(1.5D, 150D, level), ""));
				}
			}
			case "MOOSHROOM_COW" -> {
				out.add(Line.counted("Farming Fortune stat", petScale(1D, 100D, level), ""));
				if (rarity >= 2) {
					out.add(Line.unavailable("Farming Strength", "Needs live Strength"));
				}
			}
			case "BEE" -> {
				double per = rarity >= 5 ? 0.4D : rarity >= 3 ? 0.3D : rarity == 2 ? 0.2D : 0D;
				if (per > 0D) {
					out.add(Line.counted("Farming Fortune stat", per * Math.min(100, level), ""));
				}
			}
			case "CHICKEN" -> out.add(Line.counted("Farming Fortune stat", petScale(0.5D, 50D, level), ""));
			case "PIG" -> {
				if ("Potato".equals(crop)) {
					out.add(Line.counted("Potato Fortune stat", petScale(0.2D, 20D, level), "Potato only"));
					if (rarity >= 2) {
						out.add(Line.unavailable("Shining Stampede", "Needs Shiny Pig bestiary"));
					}
				}
			}
			case "MOSQUITO" -> {
				if ("Sugar Cane".equals(crop) && rarity >= 2) {
					double per = rarity == 2 ? petScale(0.01D, 1D, level) : petScale(0.02D, 2D, level);
					out.add(garden.loaded()
						? Line.counted("Buzzin' Barterer", Math.min(175D, per * garden.uniqueVisitors()),
							garden.uniqueVisitors() + " unique visitors, max 175")
						: Line.unavailable("Buzzin' Barterer", "Needs unique visitors, garden not loaded"));
				}
			}
			default -> {
			}
		}
		return out;
	}

	private int maxedFarmingPets() {
		Set<String> maxed = new HashSet<>();
		for (PetSnapshot.Entry pet : inputs.pets()) {
			String type = pet.type() == null ? "" : pet.type().toUpperCase(Locale.ROOT);
			if (SYMBIOSIS_PETS.contains(type) && pet.maxLevel() > 0 && pet.level() >= pet.maxLevel()) {
				maxed.add(type);
			}
		}
		return maxed.size();
	}

	public boolean inventoryKnown() {
		return inputs.inventoryKnown();
	}

	public boolean toolkitKnown() {
		return inputs.toolkitKnown();
	}

	public int toolkitTools() {
		return inputs.toolkit().size();
	}

	public List<Piece> armour() {
		return armour;
	}

	public List<SetBonus> setBonuses() {
		return setBonuses;
	}

	public List<Piece> equipment() {
		return equipment;
	}

	public List<Accessory> accessories() {
		return accessories;
	}

	public InventoryDecoder.Stack fermento() {
		return fermento;
	}

	/** Best tool for {@code crop}; Dedication depends on crop milestones, so the pick can change once the garden loads. */
	public Tool tool(String crop, Garden garden) {
		Tool best = null;
		for (ToolCandidate c : tools) {
			if (!c.info().crops().contains(crop)) {
				continue;
			}
			InventoryDecoder.Stack stack = c.stack();
			String rarity = FarmingToolFortune.rarity(c.info(), stack.extraAttributes(), stack.lore());
			List<Line> parts = FarmingToolFortune.parts(
				c.info(), stack.extraAttributes(), rarity, crop, garden.loaded() ? garden.milestones() : null, inputs.axedPerk()
			);
			Tool tool = new Tool(named(stack, rarity), c.fromToolkit(), rarity, parts);
			if (best == null || tool.total() > best.total()) {
				best = tool;
			}
		}
		return best;
	}

	// Toolkit tools have no display name, so build one with the reforge and rarity colour.
	private static InventoryDecoder.Stack named(InventoryDecoder.Stack stack, String rarity) {
		if (stack.displayName() != null && !stack.displayName().isBlank()) {
			return stack;
		}
		String name = KuudraGearChecks.itemName(stack.id());
		String reforge = NbtAttrs.string(stack.extraAttributes(), "modifier");
		if (reforge != null && !reforge.isBlank()) {
			name = FarmingToolFortune.pretty(reforge) + " " + name;
		}
		return new InventoryDecoder.Stack(
			stack.id(), stack.count(), stack.extraAttributes(), stack.lore(), stack.soulbound(),
			"\u00a7" + RARITY_CODES.charAt(Math.max(0, RARITIES.indexOf(rarity))) + name, null, null, null
		);
	}

	public long personalBest(String crop) {
		return inputs.personalBests().getOrDefault(crop, 0L);
	}

	public boolean personalBestsUnlocked() {
		return inputs.personalBestsUnlocked();
	}

	/** Permanent profile bonuses that apply to every crop. */
	public List<Line> permanentLines(Garden garden) {
		List<Line> out = new ArrayList<>();
		int level = Math.min(FARMING_LEVEL_CAP, inputs.farmingLevel());
		out.add(Line.counted("Farming level", 4D * level, "Level " + level));
		int anita = Math.min(ANITA_CAP, inputs.anitaLevel());
		out.add(Line.counted("Anita bonus", 4D * anita, anita + " / " + ANITA_CAP));
		out.add(garden.loaded()
			? Line.counted("Unlocked plots", 3D * Math.min(PLOTS_MAX, garden.plots()), garden.plots() + " / " + PLOTS_MAX)
			: Line.unavailable("Unlocked plots", "Garden not loaded"));
		out.add(Line.counted("Garden bestiary", 0.4D * inputs.gardenTiers(), inputs.gardenTiers() + " / " + GARDEN_TIERS_MAX + " tiers"));
		if (inputs.dnaMilestone() > 0) {
			out.add(Line.counted("DNA Analysis milestone", 5D * inputs.dnaMilestone(),
				inputs.dnaMilestone() + " / " + DNA_MILESTONE_MAX));
		}
		if (inputs.cropshotLevel() > 0) {
			out.add(Line.counted("Cropshot chip", cropshotFortune(inputs.cropshotLevel()), "Level " + inputs.cropshotLevel()));
		}
		int fly = inputs.shards().getOrDefault("fortunate_farmer", 0);
		if (fly > 0) {
			out.add(Line.counted("Fly shard", 2.5D * fly, "Level " + fly));
		}
		int galaxy = inputs.shards().getOrDefault("ultimate_dna", 0);
		if (galaxy > 0) {
			out.add(Line.counted("Galaxy Fish shard", galaxy, "Level " + galaxy));
		}
		// Firefly works by day and Lunar Moth by night, so the lower level is always active.
		int solar = inputs.shards().getOrDefault("solar_power", 0);
		int lunar = inputs.shards().getOrDefault("lunar_power", 0);
		int both = Math.min(solar, lunar);
		if (both > 0) {
			out.add(Line.counted("Firefly and Lunar Moth shards", 5D * both, "Level " + both + ", day and night"));
		}
		double acc = 0D;
		for (Accessory a : accessories) {
			acc += a.fortune();
		}
		if (acc > 0D) {
			out.add(Line.counted("Accessories", acc, accessories.size() == 1 ? "1 accessory" : accessories.size() + " accessories"));
		}
		if (fermento != null && "HELIANTHUS_RELIC".equalsIgnoreCase(fermento.id())) {
			out.add(Line.counted("Helianthus Relic", FERMENTO_FORTUNE.get("HELIANTHUS_RELIC"), "All crops"));
		}
		return out;
	}

	/** Temporary and situational bonuses for {@code crop} at {@code nowMs}. Never part of a total. */
	public List<FarmingFortuneTemporary.Source> temporary(String crop, Garden garden, long nowMs) {
		return FarmingFortuneTemporary.sources(this, crop, garden, nowMs);
	}

	Inputs inputs() {
		return inputs;
	}

	InventoryDecoder.Stack magic8Ball() {
		return magic8Ball;
	}

	boolean atmosphericFilter() {
		return atmosphericFilter;
	}

	InventoryDecoder.Stack anita() {
		return anita;
	}

	boolean truffle() {
		return truffle;
	}

	/** Owned farming pets valued for {@code crop} (null for every crop), best first, one per pet type. */
	public List<PetPick> pets(String crop, Garden garden) {
		Map<String, PetPick> bestByType = new LinkedHashMap<>();
		for (PetSnapshot.Entry pet : inputs.pets()) {
			List<Line> lines = petLines(pet, crop, garden);
			if (lines.isEmpty()) {
				continue;
			}
			PetPick pick = new PetPick(pet, lines, petItem(pet.heldItem(), garden));
			PetPick held = bestByType.get(pet.type());
			if (held == null || pick.total() > held.total()) {
				bestByType.put(pet.type(), pick);
			}
		}
		List<PetPick> out = new ArrayList<>(bestByType.values());
		out.sort(Comparator.comparingDouble(PetPick::total).reversed());
		return out;
	}

	public PetPick bestPet(String crop, Garden garden) {
		List<PetPick> pets = pets(crop, garden);
		return pets.isEmpty() ? null : pets.get(0);
	}

	private static Line petItem(String heldItem, Garden garden) {
		if (heldItem == null) {
			return null;
		}
		return switch (heldItem.toUpperCase(Locale.ROOT)) {
			case "YELLOW_BANDANA" -> Line.counted("Yellow Bandana", 30D, "");
			case "GREEN_BANDANA" -> garden.loaded()
				? Line.counted("Green Bandana", 4D * garden.level(), "Garden level " + garden.level())
				: Line.unavailable("Green Bandana", "Garden not loaded");
			default -> null;
		};
	}

	public double permanentTotal(Garden garden) {
		return sum(permanentLines(garden));
	}

	public double armourTotal() {
		double total = 0D;
		for (Piece p : armour) {
			total += p.total();
		}
		for (SetBonus s : setBonuses) {
			total += s.fortune();
		}
		return total;
	}

	public double equipmentTotal() {
		double total = 0D;
		for (Piece p : equipment) {
			total += p.total();
		}
		return total;
	}

	public double petTotal(String crop, Garden garden) {
		PetPick pet = bestPet(crop, garden);
		return pet == null ? 0D : pet.total();
	}

	/** Fortune that applies to every crop: permanent bonuses, best armour, equipment and best all-crop pet. */
	public double general(Garden garden) {
		return permanentTotal(garden) + armourTotal() + equipmentTotal() + petTotal(null, garden);
	}

	/** Fortune that only applies while farming {@code crop}. */
	public List<Line> cropLines(String crop, Garden garden) {
		List<Line> out = new ArrayList<>();
		Tool tool = tool(crop, garden);
		if (tool != null) {
			out.add(Line.counted("Best tool", tool.total(), tool.fromToolkit() ? "Farming Toolkit" : "Inventory"));
		} else if (!inputs.inventoryKnown()) {
			out.add(Line.unavailable("Best tool", "Inventory API off"));
		} else if (inputs.toolkitUnreadable().contains(crop)) {
			out.add(Line.unavailable("Best tool", "Toolkit slot unreadable"));
		} else if (!inputs.toolkitKnown()) {
			out.add(Line.unavailable("Best tool", "Toolkit not in API"));
		} else if (!inputs.toolkitUnlocked()) {
			out.add(Line.counted("Best tool", 0D, "None in inventories, toolkit locked"));
		} else {
			out.add(Line.counted("Best tool", 0D, "None in inventories or toolkit"));
		}
		out.add(garden.loaded()
			? Line.counted("Crop upgrade", CROP_UPGRADE_FORTUNE * garden.upgrades().getOrDefault(crop, 0),
				"Level " + garden.upgrades().getOrDefault(crop, 0) + " / 9")
			: Line.unavailable("Crop upgrade", "Garden not loaded"));
		long pb = personalBest(crop);
		out.add(inputs.personalBestsUnlocked()
			? Line.counted("Personal best", personalBestFortune(crop, pb), FormatUtil.commas(pb) + " crops")
			: Line.counted("Personal best", 0D, "Perk not unlocked"));
		if (fermento != null) {
			String id = fermento.id().toUpperCase(Locale.ROOT);
			List<String> crops = FERMENTO_CROPS.get(id);
			if (crops != null && crops.contains(crop)) {
				out.add(Line.counted(FERMENTO_NAMES.get(id), FERMENTO_FORTUNE.get(id), "Accessory"));
			}
		}
		double gear = 0D;
		for (Piece p : armour) {
			gear += p.cropFortune().getOrDefault(crop, 0D);
		}
		for (Piece p : equipment) {
			gear += p.cropFortune().getOrDefault(crop, 0D);
		}
		if (gear > 0D) {
			out.add(Line.counted("Gear crop fortune", gear, "Armour and equipment"));
		}
		return out;
	}

	public double cropBonus(String crop, Garden garden) {
		return sum(cropLines(crop, garden));
	}

	/** The pet is picked for this crop, so it can differ from the one in {@link #general}. */
	public double forCrop(String crop, Garden garden) {
		return permanentTotal(garden) + armourTotal() + equipmentTotal() + petTotal(crop, garden) + cropBonus(crop, garden);
	}

	private static double sum(List<Line> lines) {
		double total = 0D;
		for (Line line : lines) {
			if (line.kind() == Kind.COUNTED) {
				total += line.value();
			}
		}
		return total;
	}

	static String family(String id) {
		if (id == null) {
			return null;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		for (String slot : ARMOUR_SLOTS) {
			if (upper.endsWith("_" + slot)) {
				String family = upper.substring(0, upper.length() - slot.length() - 1);
				return SET_BONUS.containsKey(family) ? family : null;
			}
		}
		return null;
	}

	static String slot(List<String> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = PlayerStatsCalculator.stripFormatting(lore.get(i));
			if (line.isEmpty()) {
				continue;
			}
			String padded = " " + line.toUpperCase(Locale.ROOT) + " ";
			for (String slot : ARMOUR_SLOTS) {
				if (padded.contains(" " + slot + " ")) {
					return slot;
				}
			}
			for (String slot : List.of("NECKLACE", "CLOAK", "BELT")) {
				if (padded.contains(" " + slot + " ")) {
					return slot;
				}
			}
			return padded.contains(" GLOVES ") || padded.contains(" BRACELET ") ? "GLOVES" : "";
		}
		return "";
	}

	static List<String> toolCrops(String id) {
		FarmingToolFortune.Info info = FarmingToolFortune.info(id);
		return info == null ? List.of() : info.crops();
	}

	private static double fortune(InventoryDecoder.Stack stack) {
		return stack == null ? 0D : PlayerStatsCalculator.loreStat(stack.lore(), "farming_fortune");
	}

	static double pieceBonus(List<String> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			Matcher m = PIECE_BONUS.matcher(PlayerStatsCalculator.stripFormatting(lore.get(i)));
			if (m.find()) {
				return Double.parseDouble(m.group(1));
			}
		}
		return 0D;
	}

	static Map<String, Double> cropLines(InventoryDecoder.Stack stack) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (stack == null || stack.lore() == null) {
			return out;
		}
		for (String raw : stack.lore()) {
			Matcher m = CROP_LINE.matcher(PlayerStatsCalculator.stripFormatting(raw));
			if (!m.find()) {
				continue;
			}
			String name = m.group(1).equals("Melon Slice") ? "Melon" : m.group(1);
			if (CROPS.contains(name)) {
				out.merge(name, Double.parseDouble(m.group(2)), Double::sum);
			}
		}
		return out;
	}

	private static List<Piece> ordered(Map<String, Piece> bySlot, List<String> slots) {
		List<Piece> out = new ArrayList<>();
		for (String slot : slots) {
			Piece p = bySlot.get(slot);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	private static int intOf(JsonObject obj, String key) {
		Float n = obj == null ? null : Leveling.num(obj.get(key));
		return n == null ? 0 : Math.max(0, Math.round(n));
	}

	private static boolean boolOf(JsonObject obj, String key) {
		JsonElement el = obj == null ? null : obj.get(key);
		if (el == null || !el.isJsonPrimitive()) {
			return false;
		}
		if (el.getAsJsonPrimitive().isBoolean()) {
			return el.getAsBoolean();
		}
		Float n = Leveling.num(el);
		return n != null && n != 0F;
	}
}

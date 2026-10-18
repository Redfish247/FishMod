/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Static garden XP / per-crop milestone tables (NEU constants) + visitor catalog. */
public final class GardenData {
	private static volatile JsonObject root;
	private static volatile long[] gardenXp = new long[0];
	private static volatile Map<String, long[]> cropMilestones = Map.of();
	private static volatile long[] visitorMilestones = new long[0];
	/** Unique Visitors Served (Dec 2025 Garden Greenhouse update thresholds). */
	private static volatile long[] uniqueVisitorMilestones = new long[0];
	private static volatile List<String> visitorIds = List.of();
	private static volatile Map<String, String> visitorRarities = Map.of();
	private static volatile int maxPlots = 24;

	private GardenData() {
	}

	public static void ensureLoaded() {
		if (root != null) {
			return;
		}
		synchronized (GardenData.class) {
			if (root != null) {
				return;
			}
			try (InputStream in = GardenData.class.getClassLoader().getResourceAsStream("assets/betterpv/data/garden.json")) {
				if (in == null) {
					BetterPV.LOGGER.warn("Missing garden.json");
					root = new JsonObject();
					return;
				}
				root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				gardenXp = readLongArray(root.get("garden_xp_cumulative"));
				visitorMilestones = readLongArray(root.get("visitor_milestone_cumulative"));
				uniqueVisitorMilestones = readLongArray(root.get("unique_visitor_milestone_cumulative"));
				if (root.has("max_plots")) {
					maxPlots = root.get("max_plots").getAsInt();
				}
				Map<String, long[]> crops = new LinkedHashMap<>();
				JsonObject cropObj = root.has("crop_milestones") && root.get("crop_milestones").isJsonObject()
					? root.getAsJsonObject("crop_milestones")
					: null;
				if (cropObj != null) {
					for (Map.Entry<String, JsonElement> e : cropObj.entrySet()) {
						crops.put(e.getKey().toUpperCase(Locale.ROOT), readLongArray(e.getValue()));
					}
				}
				cropMilestones = Collections.unmodifiableMap(crops);

				List<String> visitors = new ArrayList<>();
				Map<String, String> rarities = new LinkedHashMap<>();
				JsonObject vis = root.has("visitors") && root.get("visitors").isJsonObject()
					? root.getAsJsonObject("visitors")
					: null;
				if (vis != null) {
					for (Map.Entry<String, JsonElement> e : vis.entrySet()) {
						String id = e.getKey().toLowerCase(Locale.ROOT);
						visitors.add(id);
						if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
							String rarity = e.getValue().getAsString();
							if (rarity != null && !rarity.isBlank()) {
								rarities.put(id, rarity.toUpperCase(Locale.ROOT));
							}
						}
					}
					visitors.sort(String::compareTo);
				}
				visitorIds = List.copyOf(visitors);
				visitorRarities = Map.copyOf(rarities);
			} catch (IOException | RuntimeException exception) {
				if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
					throw runtime;
				}
				BetterPV.LOGGER.warn("Failed to load garden.json", exception);
				root = new JsonObject();
			}
		}
	}

	public static int maxPlots() {
		ensureLoaded();
		return maxPlots;
	}

	public static List<String> allVisitorIds() {
		ensureLoaded();
		return visitorIds;
	}

	public static String visitorRarity(String id) {
		ensureLoaded();
		if (id == null || id.isBlank()) {
			return "";
		}
		return visitorRarities.getOrDefault(id.toLowerCase(Locale.ROOT), "");
	}

	public static String prettyVisitorRarity(String rarity) {
		if (rarity == null || rarity.isBlank()) {
			return "?";
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> "Uncommon";
			case "RARE" -> "Rare";
			case "EPIC" -> "Epic";
			case "LEGENDARY" -> "Legendary";
			case "MYTHIC" -> "Mythic";
			case "SPECIAL", "VERY_SPECIAL" -> "Special";
			case "COMMON" -> "Common";
			default -> titleCase(rarity.replace('_', ' '));
		};
	}

	/** Rarity display order for visitor tallies. */
	public static List<String> visitorRarityOrder() {
		// SkyBlock visitor tiers: Uncommon → Rare → Legendary → Mythic → Special
		return List.of("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "COMMON");
	}

	public static LevelProgress gardenLevel(double xp) {
		ensureLoaded();
		return progressFromCumulative(gardenXp, Math.max(0L, Math.round(xp)));
	}

	/** Garden caps at 15; past that, every 10,000 XP (the cost of levels 11-15) is one overflow level. */
	static final long GARDEN_OVERFLOW_STEP = 10_000L;

	/** Overflow level progress past the Garden cap, or null below max. */
	public static LevelProgress gardenOverflow(double xp) {
		return overflowFrom(gardenLevel(xp), GARDEN_OVERFLOW_STEP);
	}

	static LevelProgress overflowFrom(LevelProgress base, long step) {
		if (base == null || !base.maxed() || step <= 0L) {
			return null;
		}
		long extra = Math.max(0L, base.intoLevel());
		long levels = extra / step;
		long into = extra % step;
		int level = (int) Math.min(Integer.MAX_VALUE, base.level() + levels);
		return new LevelProgress(level, into, step, into / (float) step, false);
	}

	public static LevelProgress cropMilestone(String cropId, long amount) {
		ensureLoaded();
		long[] table = cropMilestones.get(normalizeCropKey(cropId));
		if (table == null || table.length == 0) {
			table = cropMilestones.get("WHEAT");
		}
		return progressFromCumulative(table == null ? new long[0] : table, Math.max(0L, amount));
	}

	public static LevelProgress visitorMilestone(long completed) {
		ensureLoaded();
		return progressFromCumulative(visitorMilestones, Math.max(0L, completed));
	}

	/** Unique Visitors Served milestone from {@code unique_npcs_served}. */
	public static LevelProgress uniqueVisitorMilestone(long uniqueServed) {
		ensureLoaded();
		return progressFromCumulative(uniqueVisitorMilestones, Math.max(0L, uniqueServed));
	}

	/** Hypixel / NEU crop id → milestone table key. */
	public static String normalizeCropKey(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "CARROT_ITEM", "CARROT" -> "CARROT";
			case "POTATO_ITEM", "POTATO" -> "POTATO";
			case "INK_SACK:3", "COCOA", "COCOA_BEANS" -> "COCOA_BEANS";
			case "MUSHROOM_COLLECTION", "MUSHROOM" -> "MUSHROOM";
			case "NETHER_STALK", "NETHER_WART" -> "NETHER_WART";
			case "DOUBLE_PLANT", "SUNFLOWER" -> "SUNFLOWER";
			case "MELON", "MELON_SLICE" -> "MELON";
			case "SEEDS", "WHEAT_SEEDS" -> "SEEDS";
			default -> id.toUpperCase(Locale.ROOT);
		};
	}

	public static String prettyCrop(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return switch (normalizeCropKey(id)) {
			case "WHEAT" -> "Wheat";
			case "CARROT" -> "Carrot";
			case "POTATO" -> "Potato";
			case "PUMPKIN" -> "Pumpkin";
			case "SUGAR_CANE" -> "Sugar Cane";
			case "MELON" -> "Melon";
			case "CACTUS" -> "Cactus";
			case "COCOA_BEANS" -> "Cocoa Beans";
			case "MUSHROOM" -> "Mushroom";
			case "NETHER_WART" -> "Nether Wart";
			case "SUNFLOWER" -> "Sunflower";
			case "MOONFLOWER" -> "Moonflower";
			case "WILD_ROSE" -> "Wild Rose";
			case "SEEDS", "WHEAT_SEEDS" -> "Seeds";
			default -> titleCase(id);
		};
	}

	/** Item id used for crop icons. */
	public static String cropIconId(String id) {
		return switch (normalizeCropKey(id)) {
			case "CARROT" -> "CARROT_ITEM";
			case "POTATO" -> "POTATO_ITEM";
			case "COCOA_BEANS" -> "INK_SACK-3";
			case "MUSHROOM" -> "RED_MUSHROOM";
			case "NETHER_WART" -> "NETHER_STALK";
			case "SUNFLOWER" -> "DOUBLE_PLANT";
			case "MELON" -> "MELON";
			case "SEEDS", "WHEAT_SEEDS" -> "SEEDS";
			case "MOONFLOWER" -> "MOONFLOWER";
			case "WILD_ROSE" -> "WILD_ROSE";
			default -> normalizeCropKey(id);
		};
	}

	/** Pack model for modern garden crops that NEU maps poorly. */
	public static String cropPackModel(String id) {
		String key = normalizeCropKey(id).toLowerCase(Locale.ROOT);
		return switch (key) {
			case "moonflower", "sunflower", "wild_rose" ->
				"hypixel_skyblock:item/island_relevant/garden/greenhouse/" + key;
			default -> "";
		};
	}

	/** Elite Bot contest crop enum → Hypixel/NEU crop id. */
	public static String eliteCropId(int crop) {
		return switch (crop) {
			case 0 -> "CACTUS";
			case 1 -> "CARROT_ITEM";
			case 2 -> "COCOA_BEANS";
			case 3 -> "MELON";
			case 4 -> "MUSHROOM";
			case 5 -> "NETHER_WART";
			case 6 -> "POTATO_ITEM";
			case 7 -> "PUMPKIN";
			case 8 -> "SUGAR_CANE";
			case 9 -> "WHEAT";
			case 10 -> "SEEDS";
			case 11 -> "SUNFLOWER";
			case 12 -> "MOONFLOWER";
			case 13 -> "WILD_ROSE";
			default -> "";
		};
	}

	/** Elite crop field as int, PascalCase name, or Hypixel id. */
	public static String eliteCropId(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String trimmed = raw.trim();
		try {
			return eliteCropId(Integer.parseInt(trimmed));
		} catch (NumberFormatException ignored) {
		}
		String key = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
		return switch (key) {
			case "CACTUS" -> "CACTUS";
			case "CARROT", "CARROT_ITEM" -> "CARROT_ITEM";
			case "COCOABEANS", "COCOA_BEANS", "INK_SACK:3", "INK_SACK-3" -> "COCOA_BEANS";
			case "MELON" -> "MELON";
			case "MUSHROOM", "MUSHROOM_COLLECTION" -> "MUSHROOM";
			case "NETHERWART", "NETHER_WART", "NETHER_STALK" -> "NETHER_WART";
			case "POTATO", "POTATO_ITEM" -> "POTATO_ITEM";
			case "PUMPKIN" -> "PUMPKIN";
			case "SUGARCANE", "SUGAR_CANE" -> "SUGAR_CANE";
			case "WHEAT" -> "WHEAT";
			case "SEEDS", "WHEAT_SEEDS" -> "SEEDS";
			case "SUNFLOWER", "DOUBLE_PLANT" -> "SUNFLOWER";
			case "MOONFLOWER" -> "MOONFLOWER";
			case "WILDROSE", "WILD_ROSE" -> "WILD_ROSE";
			default -> {
				String camel = trimmed.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
				if (!camel.equals(key)) {
					yield eliteCropId(camel);
				}
				yield key;
			}
		};
	}

	/** Elite Bot contest medal enum → lowercase name. */
	public static String eliteMedalName(int medal) {
		return switch (medal) {
			case -1 -> "unclaimable";
			case 0 -> "none";
			case 1 -> "bronze";
			case 2 -> "silver";
			case 3 -> "gold";
			case 4 -> "platinum";
			case 5 -> "diamond";
			default -> "none";
		};
	}

	/** Known garden chips (API keys) - 10 total. */
	public static final List<String> CHIP_ORDER = List.of(
		"sowledge", "rarefinder", "hypercharge", "quickdraw", "vermin_vaporize",
		"mechamind", "cropshot", "overdrive", "synthesis", "evergreen"
	);

	public static String normalizeChipKey(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String key = id.toLowerCase(Locale.ROOT).replace("garden_chip", "").replace("__", "_");
		if (key.endsWith("_")) {
			key = key.substring(0, key.length() - 1);
		}
		return switch (key) {
			case "vermin_vaporizer", "verminvaporizer" -> "vermin_vaporize";
			default -> key;
		};
	}

	public static String chipIconId(String id) {
		String key = normalizeChipKey(id);
		return switch (key) {
			case "vermin_vaporize" -> "VERMIN_VAPORIZER_GARDEN_CHIP";
			default -> key.isBlank() ? "" : key.toUpperCase(Locale.ROOT) + "_GARDEN_CHIP";
		};
	}

	public static String prettyChip(String id) {
		String key = normalizeChipKey(id);
		return switch (key) {
			case "sowledge" -> "Sowledge";
			case "rarefinder" -> "Rarefinder";
			case "hypercharge" -> "Hypercharge";
			case "quickdraw" -> "Quickdraw";
			case "vermin_vaporize" -> "Vermin Vaporizer";
			case "mechamind" -> "Mechamind";
			case "cropshot" -> "Cropshot";
			case "overdrive" -> "Overdrive";
			case "synthesis" -> "Synthesis";
			case "evergreen" -> "Evergreen";
			default -> titleCase(key.replace('_', ' '));
		};
	}

	public static String prettyComposterUpgrade(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "speed" -> "Composter Speed";
			case "multi_drop" -> "Multi Drop";
			case "fuel_cap" -> "Fuel Cap";
			case "organic_matter_cap" -> "Organic Matter Cap";
			case "cost_reduction" -> "Cost Reduction";
			default -> titleCase(id.replace('_', ' '));
		};
	}

	public static String prettyGreenhouse(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		String pretty = prettyCrop(id);
		if (!"?".equals(pretty) && !pretty.equals(titleCase(id))) {
			return pretty;
		}
		String key = normalizeCropKey(id);
		pretty = prettyCrop(key);
		if (!"?".equals(pretty)) {
			return pretty;
		}
		return titleCase(id.replace('_', ' '));
	}

	/** Prefer SkyBlock item id; keep mutations as-is (do not remap through crop tables). */
	public static String greenhouseIconId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String upper = id.toUpperCase(Locale.ROOT);
		return switch (upper) {
			case "TIMESTALK", "TIMESTALK_CLONE" -> "TIMESTALK";
			case "ROSEWATER", "ROSEWATER_FLASK_EMPTY" -> "ROSEWATER_FLASK";
			case "SUNFLOWER", "DOUBLE_PLANT" -> "DOUBLE_PLANT";
			case "MOONFLOWER", "WILD_ROSE", "WHEAT", "POTATO", "POTATO_ITEM",
				"CARROT", "CARROT_ITEM", "PUMPKIN", "MELON", "CACTUS", "SUGAR_CANE",
				"NETHER_WART", "NETHER_STALK", "COCOA_BEANS", "INK_SACK:3", "INK_SACK-3", "MUSHROOM" -> cropIconId(upper);
			default -> upper;
		};
	}

	/** Forced hypixel_skyblock model path when NEU has no skull (paper / deadbush style). */
	public static String greenhousePackModel(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String key = id.toLowerCase(Locale.ROOT);
		return switch (key) {
			case "dead_plant", "ethereal_vine", "greenhouse_blueprint",
				"overclocker_3000", "plant_diagnostics_tool" ->
				"hypixel_skyblock:item/island_relevant/garden/greenhouse/" + key;
			case "moonflower", "sunflower", "wild_rose" ->
				"hypixel_skyblock:item/island_relevant/garden/greenhouse/" + key;
			default -> "";
		};
	}

	public static String prettyVisitor(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "an" -> "An";
			case "tia" -> "Tia the Fairy";
			case "fire_guy" -> "Fire Guy";
			case "madame_eleanor" -> "Madame Eleanor";
			default -> titleCase(id.replace('_', ' '));
		};
	}

	/** NEU skull item id for a garden visitor. */
	public static String visitorNpcId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String key = id.toLowerCase(Locale.ROOT);
		return switch (key) {
			case "jerry", "st_jerry" -> "ST_JERRY_NPC";
			case "lumberjack" -> "LUMBER_JACK_NPC";
			case "tia" -> "TIA_THE_FAIRY_NPC";
			case "madame_eleanor" -> "MADAME_ELEANOR_Q_GOLDSWORTH_III_NPC";
			case "seraphine" -> "CLERK_SERAPHINE_NPC";
			case "fire_guy", "dirt_guy" -> "DIRT_GUY_NPC";
			case "scardius" -> "SCOUT_SCARDIUS_NPC";
			case "tyashoi" -> "TYASHOI_ALCHEMIST_NPC";
			case "pearl" -> "PEARL_DEALER_NPC";
			case "wilson", "emissary_wilson" -> "EMISSARY_WILSON_NPC";
			case "royal_resident_neighbour", "royal_resident_neighbor" -> "ROYAL_RESIDENT_NEIGHBOR_NPC";
			case "royal_resident_peasant" -> "ROYAL_RESIDENT_NPC";
			case "royal_resident_reward" -> "ROYAL_RESIDENT_SNOOTY_NPC";
			case "shaggy" -> "SHAGGY_NPC";
			case "jacob" -> "JACOB_NPC";
			case "jacobus" -> "JACOBUS_NPC";
			case "anita" -> "ANITA_NPC";
			case "beth" -> "BETH_NPC";
			case "carpenter" -> "CARPENTER_NPC";
			case "farmhand" -> "FARMHAND_NPC";
			case "farm_merchant" -> "FARM_MERCHANT_NPC";
			case "farmer_jon" -> "FARMER_JON_NPC";
			case "jamie" -> "JAMIE_NPC";
			case "liam" -> "LIAM_NPC";
			case "rhys" -> "RHYS_NPC";
			case "rusty" -> "RUSTY_NPC";
			case "tom" -> "TOM_NPC";
			case "terry" -> "TERRY_NPC";
			case "fragilis" -> "FRAGILIS_NPC";
			case "plumber_joe" -> "PLUMBER_JOE_NPC";
			case "dulin_tunnels", "dulin" -> "DULIN_NPC";
			case "vargul_garden", "vargul" -> "VARGUL_NPC";
			case "grandma_wolf" -> "GRANDMA_WOLF_NPC";
			case "lift_operator" -> "LIFT_OPERATOR_NPC";
			case "mayor_aatrox" -> "AATROX_MAYOR_MONSTER";
			case "mayor_cole" -> "COLE_MAYOR_MONSTER";
			case "mayor_diana" -> "DIANA_MAYOR_MONSTER";
			case "mayor_diaz" -> "DIAZ_MAYOR_MONSTER";
			case "mayor_finnegan" -> "FINNEGAN_MAYOR_MONSTER";
			case "mayor_foxy" -> "FOXY_MAYOR_MONSTER";
			case "mayor_marina" -> "MARINA_MAYOR_MONSTER";
			case "mayor_paul" -> "PAUL_MAYOR_MONSTER";
			case "dante_goon" -> "DANTE_SPECIAL_MAYOR_MONSTER";
			default -> key.toUpperCase(Locale.ROOT) + "_NPC";
		};
	}

	/** Hypixel Composter Upgrades menu icon (vanilla stand-in matching the GUI). */
	public static String composterUpgradeIconId(String id) {
		if (id == null) {
			return "COMPOST";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "speed" -> "CLOCK";
			case "multi_drop" -> "GOLDEN_HOE";
			case "fuel_cap" -> "COAL_BLOCK";
			case "organic_matter_cap" -> "COMPOST";
			case "cost_reduction" -> "GOLD_INGOT";
			default -> "COMPOST";
		};
	}

	public static String composterUpgradePackModel(String id) {
		if (id == null) {
			return "";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "organic_matter_cap", "speed" -> "hypixel_skyblock:item/island_relevant/garden/compost";
			default -> "";
		};
	}

	public record LevelProgress(int level, long intoLevel, long needForNext, float fill, boolean maxed) {
	}

	private static LevelProgress progressFromCumulative(long[] cumulative, long total) {
		if (cumulative == null || cumulative.length <= 1) {
			return new LevelProgress(0, total, 0L, 1f, true);
		}
		int level = 0;
		for (int i = 1; i < cumulative.length; i++) {
			if (total >= cumulative[i]) {
				level = i;
			} else {
				break;
			}
		}
		if (level >= cumulative.length - 1) {
			long last = cumulative[cumulative.length - 1];
			return new LevelProgress(level, Math.max(0L, total - last), 0L, 1f, true);
		}
		long floor = cumulative[level];
		long next = cumulative[level + 1];
		long need = Math.max(1L, next - floor);
		long into = Math.max(0L, total - floor);
		return new LevelProgress(level, into, need, Math.min(1f, into / (float) need), false);
	}

	private static long[] readLongArray(JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return new long[0];
		}
		JsonArray array = element.getAsJsonArray();
		long[] out = new long[array.size()];
		for (int i = 0; i < array.size(); i++) {
			try {
				out[i] = array.get(i).getAsLong();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				out[i] = 0L;
			}
		}
		return out;
	}

	private static String titleCase(String raw) {
		String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', ' ').split("[\\s_]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	public static Set<String> knownCropKeys() {
		ensureLoaded();
		return new LinkedHashSet<>(cropMilestones.keySet());
	}
}

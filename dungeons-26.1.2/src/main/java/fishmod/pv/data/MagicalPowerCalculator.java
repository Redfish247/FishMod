/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.io.IOException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Current Magical Power from unique active accessories (SkyCrypt-style),
 * not Hypixel's {@code highest_magical_power} peak field.
 */
public final class MagicalPowerCalculator {
	private static final Map<String, Integer> MP_BY_TIER = Map.ofEntries(
		Map.entry("COMMON", 3),
		Map.entry("UNCOMMON", 5),
		Map.entry("RARE", 8),
		Map.entry("EPIC", 12),
		Map.entry("LEGENDARY", 16),
		Map.entry("MYTHIC", 22),
		Map.entry("DIVINE", 26),
		Map.entry("SPECIAL", 3),
		Map.entry("VERY_SPECIAL", 5)
	);
	private static final List<String> RARITY_ORDER = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"
	);
	private static final List<String> RECOMB_LADDER = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
	);

	private static final Pattern LORE_RARITY = Pattern.compile(
		"\\b(VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL)\\b"
	);

	private static volatile boolean loaded;
	private static Map<String, FamilyPos> familyById = Map.of();
	private static Map<String, String> aliasToCanonical = Map.of();

	private MagicalPowerCalculator() {
	}

	public static int fromMember(JsonObject member) {
		if (member == null) {
			return 0;
		}
		List<InventoryDecoder.Stack> stacks = InventoryDecoder.accessoryCandidates(member);
		return fromStacks(stacks, abiphoneContacts(member), riftPrismConsumed(member));
	}

	public static int fromStacks(
		List<InventoryDecoder.Stack> stacks,
		int abiphoneContacts,
		boolean riftPrismConsumed
	) {
		ensureFamilies();
		if (stacks == null || stacks.isEmpty()) {
			return riftPrismConsumed ? 11 : 0;
		}

		Map<String, Candidate> bestByFamily = new LinkedHashMap<>();
		boolean hasAbicase = false;
		boolean hasRiftPrismItem = false;
		for (InventoryDecoder.Stack stack : stacks) {
			if (stack == null || stack.id() == null || stack.id().isBlank()) {
				continue;
			}
			String id = stack.id().trim().toUpperCase(Locale.ROOT);
			String tier = effectiveTier(id, stack.extraAttributes(), stack.lore());
			if (isAbicase(id)) {
				hasAbicase = true;
				id = "ABICASE";
				if (tier.isBlank()) {
					tier = "RARE";
				}
			}
			if ("RIFT_PRISM".equals(id)) {
				hasRiftPrismItem = true;
				if (tier.isBlank()) {
					tier = "RARE";
				}
			}
			if (tier.isBlank()) {
				tier = "COMMON";
			}

			String family = familyKey(id);
			int rank = familyRank(id);
			int rarityIndex = rarityIndex(tier);
			Candidate next = new Candidate(id, tier, rank, rarityIndex);
			Candidate prev = bestByFamily.get(family);
			if (prev == null || better(next, prev)) {
				bestByFamily.put(family, next);
			}
		}

		int total = 0;
		for (Candidate c : bestByFamily.values()) {
			total += magicalPowerFor(c.id(), c.tier());
		}
		if (riftPrismConsumed && !hasRiftPrismItem) {
			total += 11;
		}
		if (hasAbicase && abiphoneContacts > 0) {
			total += abiphoneContacts / 2;
		}
		return Math.max(0, total);
	}

	public static int abiphoneContacts(JsonObject member) {
		if (member == null) {
			return 0;
		}
		JsonObject nether = obj(member.get("nether_island_player_data"));
		JsonObject abiphone = nether == null ? null : obj(nether.get("abiphone"));
		if (abiphone == null) {
			return 0;
		}
		if (abiphone.has("active_contacts") && abiphone.get("active_contacts").isJsonArray()) {
			return abiphone.getAsJsonArray("active_contacts").size();
		}
		JsonObject contactData = obj(abiphone.get("contact_data"));
		return contactData == null ? 0 : contactData.size();
	}

	public static boolean riftPrismConsumed(JsonObject member) {
		if (member == null) {
			return false;
		}
		JsonObject rift = obj(member.get("rift"));
		JsonObject access = rift == null ? null : obj(rift.get("access"));
		if (access == null || !access.has("consumed_prism")) {
			return false;
		}
		JsonElement el = access.get("consumed_prism");
		return el != null && el.isJsonPrimitive() && el.getAsBoolean();
	}

	/** Family root from {@code accessory_families.json}, or the id itself when it has no family. */
	public static String accessoryFamily(String id) {
		ensureFamilies();
		return familyKey(id == null ? "" : id.trim().toUpperCase(Locale.ROOT));
	}

	/** Position inside the accessory's family (0 = lowest tier). */
	public static int accessoryFamilyRank(String id) {
		ensureFamilies();
		return familyRank(id == null ? "" : id.trim().toUpperCase(Locale.ROOT));
	}

	public static int magicalPower(String id, String tier) {
		String normalized = SkyBlockItemFactory.normalizeTier(tier);
		return magicalPowerFor(id, normalized.isBlank() ? "COMMON" : normalized);
	}

	private static int magicalPowerFor(String id, String tier) {
		if ("RIFT_PRISM".equals(id)) {
			return 11;
		}
		int base = MP_BY_TIER.getOrDefault(tier, 0);
		if ("HEGEMONY_ARTIFACT".equals(id)) {
			return base * 2;
		}
		return base;
	}

	private static String effectiveTier(String id, CompoundTag ea, List<String> lore) {
		// Lore is the rarity Hypixel actually shows, including recomb and special upgrades.
		String loreTier = SkyBlockItemFactory.normalizeTier(tierFromLore(lore));
		if (!loreTier.isBlank()) {
			return loreTier;
		}
		String tier = SkyBlockItemFactory.normalizeTier(SkyBlockItemFactory.resolveTier(id));
		if (tier.isBlank()) {
			return "";
		}
		int upgrades = NbtAttrs.intValue(ea, "rarity_upgrades", 0);
		if (upgrades > 0) {
			tier = bumpTier(tier);
		}
		return tier;
	}

	private static String bumpTier(String tier) {
		int idx = RECOMB_LADDER.indexOf(tier);
		if (idx >= 0 && idx + 1 < RECOMB_LADDER.size()) {
			return RECOMB_LADDER.get(idx + 1);
		}
		if ("SPECIAL".equals(tier)) {
			return "VERY_SPECIAL";
		}
		return tier;
	}

	private static String tierFromLore(List<String> lore) {
		if (lore == null || lore.isEmpty()) {
			return "";
		}
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i);
			if (line == null || line.isBlank()) {
				continue;
			}
			// Rarity line looks like "§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka". Match on the spaced text:
			// normalizeTier underscores it first, and "MYTHIC_ACCESSORY" never matches a word boundary.
			String plain = line.replaceAll("§.", "").toUpperCase(Locale.ROOT);
			if (!plain.contains("ACCESSORY") && !plain.contains("HATCESSORY")) {
				continue;
			}
			Matcher m = LORE_RARITY.matcher(plain);
			return m.find() ? m.group(1).replace(' ', '_') : "";
		}
		return "";
	}

	private static boolean isAbicase(String id) {
		return id != null && id.toUpperCase(Locale.ROOT).contains("ABICASE");
	}

	private static String familyKey(String id) {
		String canonical = aliasToCanonical.getOrDefault(id, id);
		FamilyPos pos = familyById.get(canonical);
		if (pos != null) {
			return pos.root();
		}
		if (isAbicase(canonical)) {
			return "ABICASE";
		}
		return canonical;
	}

	private static int familyRank(String id) {
		String canonical = aliasToCanonical.getOrDefault(id, id);
		FamilyPos pos = familyById.get(canonical);
		return pos == null ? 0 : pos.rank();
	}

	private static int rarityIndex(String tier) {
		int idx = RARITY_ORDER.indexOf(tier);
		return idx < 0 ? -1 : idx;
	}

	private static boolean better(Candidate a, Candidate b) {
		if (a.rank() != b.rank()) {
			return a.rank() > b.rank();
		}
		return a.rarityIndex() > b.rarityIndex();
	}

	private static void ensureFamilies() {
		if (loaded) {
			return;
		}
		synchronized (MagicalPowerCalculator.class) {
			if (loaded) {
				return;
			}
			loadFamilies();
			loaded = true;
		}
	}

	private static void loadFamilies() {
		JsonObject root = loadJson("data/accessory_families.json");
		Map<String, FamilyPos> byId = new HashMap<>();
		Map<String, String> aliases = new HashMap<>();
		if (root != null && root.has("families") && root.get("families").isJsonArray()) {
			for (JsonElement familyEl : root.getAsJsonArray("families")) {
				if (familyEl == null || !familyEl.isJsonArray()) {
					continue;
				}
				JsonArray family = familyEl.getAsJsonArray();
				String rootId = null;
				for (int i = 0; i < family.size(); i++) {
					JsonElement idEl = family.get(i);
					if (idEl == null || !idEl.isJsonPrimitive()) {
						continue;
					}
					String id = idEl.getAsString().trim().toUpperCase(Locale.ROOT);
					if (id.isBlank()) {
						continue;
					}
					if (rootId == null) {
						rootId = id;
					}
					byId.put(id, new FamilyPos(rootId, i));
				}
			}
		}
		if (root != null && root.has("aliases") && root.get("aliases").isJsonObject()) {
			JsonObject aliasObj = root.getAsJsonObject("aliases");
			for (var entry : aliasObj.entrySet()) {
				String canonical = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
				if (canonical.isBlank() || entry.getValue() == null || !entry.getValue().isJsonArray()) {
					continue;
				}
				FamilyPos canonicalPos = byId.get(canonical);
				for (JsonElement aliasEl : entry.getValue().getAsJsonArray()) {
					if (aliasEl == null || !aliasEl.isJsonPrimitive()) {
						continue;
					}
					String alias = aliasEl.getAsString().trim().toUpperCase(Locale.ROOT);
					if (alias.isBlank()) {
						continue;
					}
					aliases.put(alias, canonical);
					if (canonicalPos != null && !byId.containsKey(alias)) {
						byId.put(alias, canonicalPos);
					}
				}
			}
		}
		familyById = Map.copyOf(byId);
		aliasToCanonical = Map.copyOf(aliases);
	}

	private static JsonObject loadJson(String path) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.getResourceManager() != null) {
				Identifier id = Identifier.fromNamespaceAndPath("betterpv", path);
				var optional = client.getResourceManager().getResource(id);
				if (optional.isPresent()) {
					try (InputStream stream = optional.get().open();
						 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
						return JsonParser.parseReader(reader).getAsJsonObject();
					}
				}
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed loading {} from resource manager", path, exception);
		}
		try (InputStream stream = MagicalPowerCalculator.class.getResourceAsStream("/assets/betterpv/" + path)) {
			if (stream == null) {
				BetterPV.LOGGER.warn("Missing classpath resource assets/betterpv/{}", path);
				return null;
			}
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed loading {}", path, exception);
			return null;
		}
	}

	private static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private record FamilyPos(String root, int rank) {
	}

	private record Candidate(String id, String tier, int rank, int rarityIndex) {
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.nav.MuseumSort;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.price.HypixelItemsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Museum donation slots from Hypixel {@code museum_data}.
 * Armor sets are cataloged by set id with piece lists for icons/pricing.
 * Grid order matches Hypixel: {@code game_stage} progression, then donation XP, then id.
 * Higher-tier / mapped donations credit lower variants via {@code parent} + {@code mapped_item_ids}.
 */
public final class MuseumCatalog {
	/** Hypixel museum progression (early → late). */
	private static final Map<String, Integer> GAME_STAGE_RANK = Map.of(
		"STARTER", 0,
		"AMATEUR", 1,
		"INTERMEDIATE", 2,
		"SKILLED", 3,
		"EXPERT", 4,
		"PROFESSIONAL", 5,
		"MASTER", 6
	);

	public record Entry(
		String donationId,
		MuseumSort sort,
		int donationXp,
		boolean armorSet,
		List<String> pieceIds,
		String iconId,
		int gameStageRank
	) {
		public Entry {
			donationId = donationId == null ? "" : donationId.toUpperCase(Locale.ROOT);
			sort = sort == null ? MuseumSort.SPECIAL : sort;
			donationXp = Math.max(0, donationXp);
			pieceIds = pieceIds == null || pieceIds.isEmpty()
				? List.of(donationId)
				: List.copyOf(pieceIds);
			iconId = iconId == null || iconId.isBlank() ? donationId : iconId.toUpperCase(Locale.ROOT);
			gameStageRank = Math.max(0, gameStageRank);
		}
	}

	/**
	 * @param stack display/NBT stack from the museum API (may be the higher-tier item)
	 * @param coveredBy if non-blank, this slot is filled because {@code coveredBy} was donated
	 */
	public record ResolvedDonation(InventoryDecoder.Stack stack, String coveredBy) {
		public ResolvedDonation {
			coveredBy = coveredBy == null || coveredBy.isBlank() ? null : coveredBy.toUpperCase(Locale.ROOT);
		}

		public boolean direct() {
			return coveredBy == null;
		}
	}

	private static volatile Map<MuseumSort, List<String>> BY_SORT = Map.of();
	private static volatile Map<String, Entry> BY_ID = Map.of();
	/** Higher-tier id → lower-tier museum slots it unlocks (direct children). */
	private static volatile Map<String, List<String>> CHILDREN = Map.of();
	/** Starred / alt id → catalog museum slot id. */
	private static volatile Map<String, String> VARIANT_TO_BASE = Map.of();
	/** Armor/equipment piece id → museum set slot id. */
	private static volatile Map<String, String> PIECE_TO_SET = Map.of();

	private MuseumCatalog() {
	}

	public static void ensureBuilt() {
		if (!BY_ID.isEmpty()) {
			return;
		}
		rebuild();
	}

	/** Drop catalog so the next UI access rebuilds against fresh item defs. */
	public static void invalidate() {
		BY_SORT = Map.of();
		BY_ID = Map.of();
		CHILDREN = Map.of();
		VARIANT_TO_BASE = Map.of();
		PIECE_TO_SET = Map.of();
	}

	public static void rebuild() {
		rebuild(HypixelItemsCache.allItems());
	}

	static void rebuild(Collection<JsonObject> items) {
		Map<MuseumSort, LinkedHashSet<String>> buckets = new EnumMap<>(MuseumSort.class);
		for (MuseumSort sort : MuseumSort.categories()) {
			buckets.put(sort, new LinkedHashSet<>());
		}
		Map<String, Mutable> building = new LinkedHashMap<>();
		Map<String, String> upgradeTo = new LinkedHashMap<>();
		Map<String, String> variantToBase = new LinkedHashMap<>();

		for (JsonObject item : items) {
			if (item == null || !item.has("museum_data") || !item.get("museum_data").isJsonObject()) {
				continue;
			}
			JsonObject museum = item.getAsJsonObject("museum_data");
			MuseumSort sort = sortOfCategory(str(museum, "category"));
			if (sort == null) {
				continue;
			}
			String itemId = str(item, "id").toUpperCase(Locale.ROOT);
			if (itemId.isBlank()) {
				continue;
			}
			int stageRank = gameStageRank(str(museum, "game_stage"));
			ingestParents(museum, upgradeTo);
			ingestMapped(museum, itemId, variantToBase);

			if (museum.has("armor_set_donation_xp") && museum.get("armor_set_donation_xp").isJsonObject()) {
				for (var entry : museum.getAsJsonObject("armor_set_donation_xp").entrySet()) {
					String setId = entry.getKey();
					if (setId == null || setId.isBlank()) {
						continue;
					}
					String key = setId.toUpperCase(Locale.ROOT);
					int xp = 0;
					try {
						if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
							xp = entry.getValue().getAsInt();
						}
					} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
						xp = 0;
					}
					Mutable m = building.computeIfAbsent(key, k -> new Mutable(k, sort, true));
					m.sort = sort;
					m.gameStageRank = Math.max(m.gameStageRank, stageRank);
					if (xp > 0) {
						m.donationXp = xp;
					}
					m.pieces.add(itemId);
					buckets.get(sort).add(key);
				}
				continue;
			}

			int xp = 0;
			try {
				if (museum.has("donation_xp") && museum.get("donation_xp").isJsonPrimitive()) {
					xp = museum.get("donation_xp").getAsInt();
				}
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				xp = 0;
			}
			Mutable m = building.computeIfAbsent(itemId, k -> new Mutable(k, sort, false));
			m.sort = sort;
			m.gameStageRank = Math.max(m.gameStageRank, stageRank);
			m.donationXp = Math.max(m.donationXp, xp);
			m.pieces.add(itemId);
			buckets.get(sort).add(itemId);
		}

		Map<String, Entry> byId = new ConcurrentHashMap<>();
		for (Mutable m : building.values()) {
			List<String> pieces = new ArrayList<>(m.pieces);
			Collections.sort(pieces);
			byId.put(
				m.id,
				new Entry(
					m.id,
					m.sort,
					m.donationXp,
					m.armorSet,
					pieces,
					pickIconId(pieces, m.id),
					m.gameStageRank
				)
			);
		}

		Map<MuseumSort, List<String>> out = new EnumMap<>(MuseumSort.class);
		for (MuseumSort sort : MuseumSort.categories()) {
			List<String> list = new ArrayList<>(buckets.getOrDefault(sort, new LinkedHashSet<>()));
			list.sort((a, b) -> compareProgression(byId.get(a), byId.get(b), a, b));
			out.put(sort, List.copyOf(list));
		}

		Map<String, String> pieceToSet = new LinkedHashMap<>();
		for (Mutable m : building.values()) {
			if (m.armorSet) {
				for (String piece : m.pieces) {
					pieceToSet.putIfAbsent(piece, m.id);
				}
			}
		}

		BY_SORT = Map.copyOf(out);
		BY_ID = Map.copyOf(byId);
		CHILDREN = Map.copyOf(childrenOf(upgradeTo, pieceToSet));
		VARIANT_TO_BASE = Map.copyOf(variantToBase);
		PIECE_TO_SET = Map.copyOf(pieceToSet);
	}

	/**
	 * Higher → lower edges with set pieces folded onto their set id. Hypixel sometimes keys
	 * {@code parent} by a piece (MANGROVE_GRIPPERS) even though the museum slot is the set.
	 */
	static Map<String, List<String>> childrenOf(Map<String, String> upgradeTo, Map<String, String> pieceToSet) {
		Map<String, List<String>> children = new LinkedHashMap<>();
		for (var edge : upgradeTo.entrySet()) {
			String lower = edge.getKey() == null ? null : pieceToSet.getOrDefault(edge.getKey(), edge.getKey());
			String higher = edge.getValue() == null ? null : pieceToSet.getOrDefault(edge.getValue(), edge.getValue());
			if (lower == null || higher == null || lower.equals(higher)) {
				continue;
			}
			children.computeIfAbsent(higher, k -> new ArrayList<>()).add(lower);
		}
		for (var e : children.entrySet()) {
			List<String> uniq = new ArrayList<>(new LinkedHashSet<>(e.getValue()));
			e.setValue(List.copyOf(uniq));
		}
		return children;
	}

	/**
	 * Item id whose price counts toward donated value, or null when the slot is only filled because
	 * a higher tier was donated (that donor's price is already counted on its own slot).
	 */
	public static String valueId(String donationId, String coveredBy, InventoryDecoder.Stack stack) {
		if (coveredBy != null && !coveredBy.isBlank()) {
			return null;
		}
		if (donationId != null && donationId.toUpperCase(Locale.ROOT).startsWith("SPECIAL:")) {
			return stack == null || stack.id() == null || stack.id().isBlank() ? null : stack.id();
		}
		return donationId;
	}

	/**
	 * Expand museum API donations so higher tiers / mapped variants fill every covered catalog slot
	 * (e.g. Hyperion → Valkyrie / Scylla / Astraea / Necron Blade).
	 */
	public static Map<String, ResolvedDonation> resolveDonations(Map<String, InventoryDecoder.Stack> raw) {
		ensureBuilt();
		Map<String, ResolvedDonation> out = new LinkedHashMap<>();
		if (raw == null || raw.isEmpty()) {
			return out;
		}
		Map<String, InventoryDecoder.Stack> direct = new LinkedHashMap<>();
		for (var entry : raw.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			String key = entry.getKey().toUpperCase(Locale.ROOT);
			if (key.startsWith("SPECIAL:")) {
				out.put(key, new ResolvedDonation(entry.getValue(), null));
				continue;
			}
			String slot = canonicalSlotId(key);
			if (!BY_ID.containsKey(slot) && PIECE_TO_SET.containsKey(slot)) {
				// Pieces donated before Hypixel grouped them into a set keep their own key (e.g. PESTHUNTERS_BELT
				// next to PESTHUNTER). They aren't a museum slot and don't donate the set, so they're not Special either.
				continue;
			}
			direct.putIfAbsent(slot, entry.getValue());
		}
		for (var entry : direct.entrySet()) {
			out.put(entry.getKey(), new ResolvedDonation(entry.getValue(), null));
		}
		for (var entry : direct.entrySet()) {
			String donor = entry.getKey();
			for (String child : descendants(donor)) {
				out.putIfAbsent(child, new ResolvedDonation(entry.getValue(), donor));
			}
		}
		return out;
	}

	/** Map starred / alt donation keys onto the catalog museum slot id. */
	public static String canonicalSlotId(String rawId) {
		ensureBuilt();
		if (rawId == null || rawId.isBlank()) {
			return "";
		}
		String key = rawId.toUpperCase(Locale.ROOT);
		String base = VARIANT_TO_BASE.get(key);
		return base == null || base.isBlank() ? key : base;
	}

	private static List<String> descendants(String donorId) {
		if (donorId == null || donorId.isBlank()) {
			return List.of();
		}
		LinkedHashSet<String> out = new LinkedHashSet<>();
		ArrayList<String> queue = new ArrayList<>();
		queue.add(donorId.toUpperCase(Locale.ROOT));
		for (int i = 0; i < queue.size(); i++) {
			String cur = queue.get(i);
			List<String> kids = CHILDREN.getOrDefault(cur, List.of());
			for (String kid : kids) {
				if (kid != null && out.add(kid)) {
					queue.add(kid);
				}
			}
		}
		return List.copyOf(out);
	}

	private static void ingestParents(JsonObject museum, Map<String, String> upgradeTo) {
		if (museum == null || !museum.has("parent") || !museum.get("parent").isJsonObject()) {
			return;
		}
		for (var entry : museum.getAsJsonObject("parent").entrySet()) {
			String from = entry.getKey();
			if (from == null || from.isBlank() || entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String to;
			try {
				to = entry.getValue().getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				continue;
			}
			if (to == null || to.isBlank()) {
				continue;
			}
			upgradeTo.put(from.toUpperCase(Locale.ROOT), to.toUpperCase(Locale.ROOT));
		}
	}

	private static void ingestMapped(JsonObject museum, String baseId, Map<String, String> variantToBase) {
		if (museum == null || baseId == null || !museum.has("mapped_item_ids")
			|| !museum.get("mapped_item_ids").isJsonArray()) {
			return;
		}
		for (var el : museum.getAsJsonArray("mapped_item_ids")) {
			if (el == null || !el.isJsonPrimitive()) {
				continue;
			}
			String variant;
			try {
				variant = el.getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				continue;
			}
			if (variant == null || variant.isBlank()) {
				continue;
			}
			variantToBase.put(variant.toUpperCase(Locale.ROOT), baseId);
		}
	}

	public static List<String> donationIds(MuseumSort sort) {
		ensureBuilt();
		if (sort == null) {
			return List.of();
		}
		if (sort.isAll()) {
			List<String> combined = new ArrayList<>();
			for (MuseumSort category : MuseumSort.categories()) {
				combined.addAll(BY_SORT.getOrDefault(category, List.of()));
			}
			return List.copyOf(combined);
		}
		return BY_SORT.getOrDefault(sort, List.of());
	}

	public static Entry entry(String donationId) {
		ensureBuilt();
		if (donationId == null || donationId.isBlank()) {
			return null;
		}
		return BY_ID.get(donationId.toUpperCase(Locale.ROOT));
	}

	public static MuseumSort sortOfDonationId(String id) {
		Entry e = entry(id);
		return e == null ? MuseumSort.SPECIAL : e.sort();
	}

	public static boolean isCataloged(String id) {
		return entry(id) != null;
	}

	/** Item id suitable for {@code SkyBlockItemFactory.iconStack}. */
	public static String iconId(String donationId) {
		Entry e = entry(donationId);
		if (e != null) {
			return e.iconId();
		}
		return donationId == null ? "" : donationId.toUpperCase(Locale.ROOT);
	}

	public static int donationXp(String donationId) {
		Entry e = entry(donationId);
		return e == null ? 0 : e.donationXp();
	}

	/** Market cost to donate this slot (armor sets = sum of pieces). */
	public static double marketPrice(String donationId) {
		Entry e = entry(donationId);
		if (e == null) {
			return ItemPricer.price(donationId);
		}
		if (!e.armorSet()) {
			return ItemPricer.price(e.donationId());
		}
		double sum = 0;
		for (String piece : e.pieceIds()) {
			sum += ItemPricer.price(piece);
		}
		return sum;
	}

	public static String displayName(String donationId) {
		Entry e = entry(donationId);
		String look = e != null ? e.iconId() : (donationId == null ? "" : donationId);
		JsonObject def = HypixelItemsCache.get(look);
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			String stripped = stripFormatting(def.get("name").getAsString());
			if (!stripped.isBlank()) {
				return stripped;
			}
		}
		String plain = SkyBlockItemFactory.plainDisplayName(look);
		if (plain != null && !plain.isBlank()) {
			return plain;
		}
		if (e != null && e.armorSet()) {
			return prettyId(e.donationId()) + " Armor";
		}
		return prettyId(look);
	}

	private static String pickIconId(List<String> pieces, String fallback) {
		if (pieces == null || pieces.isEmpty()) {
			return fallback;
		}
		String best = pieces.get(0);
		int bestScore = scorePiece(best);
		for (int i = 1; i < pieces.size(); i++) {
			String p = pieces.get(i);
			int score = scorePiece(p);
			if (score > bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	private static int scorePiece(String id) {
		if (id == null) {
			return 0;
		}
		String u = id.toUpperCase(Locale.ROOT);
		if (u.endsWith("_HELMET") || u.endsWith("_HAT")) {
			return 5;
		}
		if (u.endsWith("_CHESTPLATE") || u.endsWith("_CLOAK")) {
			return 4;
		}
		if (u.endsWith("_LEGGINGS") || u.endsWith("_BELT")) {
			return 3;
		}
		if (u.endsWith("_BOOTS") || u.endsWith("_NECKLACE") || u.endsWith("_GLOVES")) {
			return 2;
		}
		return 1;
	}

	private static MuseumSort sortOfCategory(String category) {
		if (category == null || category.isBlank()) {
			return null;
		}
		return switch (category.trim().toUpperCase(Locale.ROOT)) {
			case "COMBAT" -> MuseumSort.COMBAT;
			case "MINING" -> MuseumSort.MINING;
			case "FORAGING" -> MuseumSort.FORAGING;
			case "FARMING" -> MuseumSort.FARMING;
			case "FISHING" -> MuseumSort.FISHING;
			case "DUNGEONEERING" -> MuseumSort.DUNGEONEERING;
			case "HUNTING" -> MuseumSort.HUNTING;
			case "SPECIAL" -> MuseumSort.SPECIAL;
			default -> null;
		};
	}

	private static int gameStageRank(String stage) {
		if (stage == null || stage.isBlank()) {
			return 99;
		}
		return GAME_STAGE_RANK.getOrDefault(stage.trim().toUpperCase(Locale.ROOT), 99);
	}

	/** Early→late game (Hypixel museum order), then XP, then id. */
	private static int compareProgression(Entry a, Entry b, String idA, String idB) {
		int rankA = a == null ? 99 : a.gameStageRank();
		int rankB = b == null ? 99 : b.gameStageRank();
		if (rankA != rankB) {
			return Integer.compare(rankA, rankB);
		}
		int xpA = a == null ? 0 : a.donationXp();
		int xpB = b == null ? 0 : b.donationXp();
		if (xpA != xpB) {
			return Integer.compare(xpA, xpB);
		}
		return String.CASE_INSENSITIVE_ORDER.compare(
			idA == null ? "" : idA,
			idB == null ? "" : idB
		);
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.toLowerCase(Locale.ROOT).split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
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

	private static String stripFormatting(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		// Hypixel: %%light_purple%%Name  plus classic § / & codes.
		String cleaned = raw.replaceAll("%%[a-zA-Z0-9_]+%%", "");
		StringBuilder out = new StringBuilder(cleaned.length());
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if ((c == '§' || c == '&') && i + 1 < cleaned.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString().trim();
	}

	private static final class Mutable {
		final String id;
		MuseumSort sort;
		boolean armorSet;
		int donationXp;
		int gameStageRank;
		final LinkedHashSet<String> pieces = new LinkedHashSet<>();

		Mutable(String id, MuseumSort sort, boolean armorSet) {
			this.id = id;
			this.sort = sort;
			this.armorSet = armorSet;
		}
	}
}

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
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CollectionSnapshot {
	public record Member(
		String uuid,
		String name,
		Map<String, Long> amounts,
		Map<String, Set<Integer>> craftedTiers,
		Map<String, Integer> unlockedTiers
	) {
		public Member {
			uuid = uuid == null ? "" : uuid.toLowerCase(Locale.ROOT);
			name = name == null || name.isBlank() ? shortUuid(uuid) : name;
			amounts = amounts == null ? Map.of() : Map.copyOf(amounts);
			Map<String, Set<Integer>> copy = new LinkedHashMap<>();
			if (craftedTiers != null) {
				for (var e : craftedTiers.entrySet()) {
					copy.put(e.getKey(), Set.copyOf(e.getValue()));
				}
			}
			craftedTiers = Map.copyOf(copy);
			unlockedTiers = unlockedTiers == null ? Map.of() : Map.copyOf(unlockedTiers);
		}

		public long amount(String itemId) {
			return lookupAmount(amounts, itemId);
		}

		public int unlockedTier(String itemId) {
			if (itemId == null || itemId.isBlank() || unlockedTiers.isEmpty()) {
				return 0;
			}
			int best = 0;
			for (String key : CollectionIds.lookupKeys(itemId)) {
				Integer tier = unlockedTiers.get(key);
				if (tier != null) {
					best = Math.max(best, tier);
				}
			}
			return best;
		}

		public int maxCraftedMinion(String minionId) {
			if (minionId == null || minionId.isBlank() || craftedTiers.isEmpty()) {
				return 0;
			}
			Set<Integer> tiers = craftedTiers.get(minionId.toUpperCase(Locale.ROOT));
			if (tiers == null || tiers.isEmpty()) {
				return 0;
			}
			int max = 0;
			for (int tier : tiers) {
				max = Math.max(max, tier);
			}
			return max;
		}
	}

	public record MinionEntry(String id, String displayName, int tierCap, Set<Integer> craftedTiers) {
		public MinionEntry {
			id = id == null ? "" : id.toUpperCase(Locale.ROOT);
			displayName = displayName == null || displayName.isBlank() ? prettyMinion(id) : displayName;
			craftedTiers = craftedTiers == null ? Set.of() : Set.copyOf(craftedTiers);
		}

		public int maxCrafted() {
			int max = 0;
			for (int tier : craftedTiers) {
				max = Math.max(max, tier);
			}
			return max;
		}

		public boolean crafted(int tier) {
			return craftedTiers.contains(tier);
		}

		public String iconId() {
			int tier = Math.max(1, maxCrafted());
			return id + "_" + tier;
		}
	}

	private final List<HypixelCollectionsCache.Category> categories;
	private final List<Member> members;
	private final String viewedUuid;
	private final List<MinionEntry> minions;
	private final ConcurrentHashMap<String, String> nameOverrides = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, JsonObject> playerRanks = new ConcurrentHashMap<>();
	private final AtomicBoolean ranksRequested = new AtomicBoolean();

	public CollectionSnapshot(
		List<HypixelCollectionsCache.Category> categories,
		List<Member> members,
		String viewedUuid,
		List<MinionEntry> minions
	) {
		this.categories = categories == null ? List.of() : List.copyOf(categories);
		this.members = members == null ? List.of() : List.copyOf(members);
		this.viewedUuid = viewedUuid == null ? "" : viewedUuid.toLowerCase(Locale.ROOT);
		this.minions = minions == null ? List.of() : List.copyOf(minions);
	}

	public static CollectionSnapshot empty() {
		return new CollectionSnapshot(List.of(), List.of(), "", List.of());
	}

	public List<HypixelCollectionsCache.Category> categories() {
		return this.categories;
	}

	public List<Member> members() {
		return this.members;
	}

	public String viewedUuid() {
		return this.viewedUuid;
	}

	public List<MinionEntry> minions() {
		return this.minions;
	}

	public Member viewed() {
		for (Member member : this.members) {
			if (member.uuid().equals(this.viewedUuid)) {
				return member;
			}
		}
		return this.members.isEmpty() ? null : this.members.get(0);
	}

	public String displayName(Member member) {
		if (member == null) {
			return "?";
		}
		String override = this.nameOverrides.get(member.uuid());
		return override == null || override.isBlank() ? member.name() : override;
	}

	public void putName(String uuid, String name) {
		if (uuid == null || name == null || name.isBlank()) {
			return;
		}
		this.nameOverrides.put(uuid.toLowerCase(Locale.ROOT), name);
	}

	public void putPlayerRank(String uuid, JsonObject player) {
		if (uuid == null || player == null) {
			return;
		}
		this.playerRanks.put(uuid.toLowerCase(Locale.ROOT), player);
		if (player.has("displayname") && player.get("displayname").isJsonPrimitive()) {
			String display = player.get("displayname").getAsString();
			if (display != null && !display.isBlank()) {
				putName(uuid, display);
			}
		}
	}

	public JsonObject playerRank(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return null;
		}
		return this.playerRanks.get(uuid.toLowerCase(Locale.ROOT));
	}

	public long viewedAmount(String itemId) {
		Member viewed = viewed();
		return viewed == null ? 0L : viewed.amount(itemId);
	}

	/**
	 * Amount used for tiers / progress.
	 * Shared Hypixel collections = coop sum; boss collections = viewed player only.
	 */
	public long totalAmount(String itemId) {
		if (BossCollections.isBossId(itemId)) {
			return viewedAmount(itemId);
		}
		long sum = 0L;
		for (Member member : this.members) {
			sum += member.amount(itemId);
		}
		return sum;
	}

	/** Highest unlocked tier recorded on any member ({@code unlocked_coll_tiers}). */
	public int unlockedTier(String itemId) {
		if (BossCollections.isBossId(itemId)) {
			return 0;
		}
		int best = 0;
		for (Member member : this.members) {
			best = Math.max(best, member.unlockedTier(itemId));
		}
		return best;
	}

	/**
	 * Display tier from {@link #totalAmount}, floored by {@code unlocked_coll_tiers}
	 * for shared collections when amounts are missing or split oddly. Unlocks above the
	 * current tier list are reported by {@link #legacyTier} instead.
	 */
	public int displayTier(HypixelCollectionsCache.Item item) {
		if (item == null) {
			return 0;
		}
		int tier = Math.max(item.tierFor(totalAmount(item.id())), unlockedTier(item.id()));
		return item.maxTiers() > 0 ? Math.min(tier, item.maxTiers()) : tier;
	}

	// Hypixel keeps old unlocks after shrinking a collection (Redstone went 16 -> 9 tiers in 0.26.1),
	// so REDSTONE_16 can outlive the tier it names.
	public int legacyTier(HypixelCollectionsCache.Item item) {
		if (item == null || item.maxTiers() <= 0) {
			return 0;
		}
		int unlocked = unlockedTier(item.id());
		return unlocked > item.maxTiers() ? unlocked : 0;
	}

	/** Same basis as {@link #totalAmount} (real amounts only). */
	public long progressAmount(HypixelCollectionsCache.Item item) {
		if (item == null) {
			return 0L;
		}
		return totalAmount(item.id());
	}

	public List<Member> membersByAmount(String itemId) {
		List<Member> sorted = new ArrayList<>(this.members);
		sorted.sort(Comparator
			.comparingLong((Member m) -> m.amount(itemId)).reversed()
			.thenComparing(m -> displayName(m), String.CASE_INSENSITIVE_ORDER));
		return sorted;
	}

	public static CollectionSnapshot fromProfile(JsonObject members, UUID viewedUuid, String viewedName) {
		HypixelCollectionsCache.awaitReady(8_000L);
		List<HypixelCollectionsCache.Category> categories = withBossCategory(HypixelCollectionsCache.categories());
		String viewed = HypixelApiClient.undashed(viewedUuid);

		List<Member> memberList = new ArrayList<>();
		Map<String, Set<Integer>> coopCrafted = new HashMap<>();

		if (members != null) {
			for (var entry : members.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				String uuid = entry.getKey() == null ? "" : entry.getKey().replace("-", "").toLowerCase(Locale.ROOT);
				JsonObject memberObj = entry.getValue().getAsJsonObject();
				Map<String, Long> amounts = readCollectionAmounts(memberObj);
				for (var bossEntry : BossCollections.amountsFromMember(memberObj).entrySet()) {
					putAmount(amounts, bossEntry.getKey(), bossEntry.getValue());
				}
				Map<String, Set<Integer>> crafted = readCraftedTiers(memberObj);
				Map<String, Integer> unlocked = readUnlockedTiers(memberObj);
				mergeCrafted(coopCrafted, crafted);
				String name = uuid.equals(viewed) && viewedName != null && !viewedName.isBlank()
					? viewedName
					: shortUuid(uuid);
				memberList.add(new Member(uuid, name, amounts, crafted, unlocked));
			}
		}

		memberList.sort(Comparator
			.comparing((Member m) -> !m.uuid().equals(viewed))
			.thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER));

		List<MinionEntry> minionEntries = new ArrayList<>();
		for (var entry : NeuRepoCache.minionMaxTiers().entrySet()) {
			String id = entry.getKey();
			int max = entry.getValue() == null ? 0 : entry.getValue();
			Set<Integer> tiers = coopCrafted.getOrDefault(id, Set.of());
			minionEntries.add(new MinionEntry(id, prettyMinion(id), max, tiers));
		}
		minionEntries.sort(Comparator.comparing(MinionEntry::displayName, String.CASE_INSENSITIVE_ORDER));

		CollectionSnapshot snapshot = new CollectionSnapshot(categories, memberList, viewed, minionEntries);
		snapshot.resolveNamesAsync();
		return snapshot;
	}

	/** Coop ranks cost one Hypixel request per member, so they load only once Collections is shown. */
	public void requestMemberRanks() {
		if (this.ranksRequested.compareAndSet(false, true)) {
			resolveRanksAsync();
		}
	}

	private void resolveNamesAsync() {
		for (Member member : this.members) {
			if (member.uuid().isBlank() || member.uuid().equals(this.viewedUuid)) {
				continue;
			}
			if (!member.name().equals(shortUuid(member.uuid()))) {
				continue;
			}
			UUID uuid = HypixelApiClient.parseUndashedUuid(member.uuid());
			if (uuid == null) {
				continue;
			}
			HypixelApiClient.resolveName(uuid).thenAccept(opt -> opt.ifPresent(id -> putName(member.uuid(), id.name())));
		}
	}

	private void resolveRanksAsync() {
		for (Member member : this.members) {
			if (member.uuid().isBlank()) {
				continue;
			}
			UUID uuid = HypixelApiClient.parseUndashedUuid(member.uuid());
			if (uuid == null) {
				continue;
			}
			HypixelApiClient.player(uuid).thenAccept(opt -> opt.ifPresent(player -> putPlayerRank(member.uuid(), player)));
		}
	}

	/** Hypixel categories plus Boss, ordered to match the in-game / SkyCrypt icon bar. */
	private static List<HypixelCollectionsCache.Category> withBossCategory(List<HypixelCollectionsCache.Category> source) {
		List<HypixelCollectionsCache.Category> ordered = new ArrayList<>();
		HypixelCollectionsCache.Category boss = BossCollections.category();
		boolean insertedBoss = false;
		boolean hasBoss = false;
		for (HypixelCollectionsCache.Category category : source == null ? List.<HypixelCollectionsCache.Category>of() : source) {
			if ("BOSS".equalsIgnoreCase(category.id())) {
				hasBoss = true;
				ordered.add(category);
				continue;
			}
			if (!insertedBoss && "RIFT".equalsIgnoreCase(category.id())) {
				ordered.add(boss);
				insertedBoss = true;
			}
			ordered.add(category);
		}
		if (!insertedBoss && !hasBoss) {
			ordered.add(boss);
		}
		return List.copyOf(ordered);
	}

	private static Map<String, Long> readCollectionAmounts(JsonObject member) {
		Map<String, Long> out = new LinkedHashMap<>();
		JsonObject collection = Leveling.obj(member.get("collection"));
		if (collection == null) {
			return out;
		}
		for (var entry : collection.entrySet()) {
			long amount = Leveling.longAmount(entry.getValue());
			if (amount <= 0L) {
				continue;
			}
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				continue;
			}
			putAmount(out, key, amount);
		}
		synthesizeComposites(out);
		return out;
	}

	/** Fill aggregate collections from component items when the aggregate key is absent. */
	private static void synthesizeComposites(Map<String, Long> out) {
		for (String aggregate : List.of("MUSHROOM_COLLECTION", "GEMSTONE_COLLECTION")) {
			if (lookupDirect(out, aggregate) > 0L) {
				continue;
			}
			long sum = 0L;
			for (String part : CollectionIds.compositeParts(aggregate)) {
				sum += lookupDirect(out, part);
			}
			if (sum > 0L) {
				putAmount(out, aggregate, sum);
			}
		}
	}

	private static Map<String, Integer> readUnlockedTiers(JsonObject member) {
		Map<String, Integer> out = new HashMap<>();
		JsonArray array = null;
		JsonObject playerData = Leveling.obj(member.get("player_data"));
		if (playerData != null && playerData.has("unlocked_coll_tiers") && playerData.get("unlocked_coll_tiers").isJsonArray()) {
			array = playerData.getAsJsonArray("unlocked_coll_tiers");
		} else if (member.has("unlocked_coll_tiers") && member.get("unlocked_coll_tiers").isJsonArray()) {
			array = member.getAsJsonArray("unlocked_coll_tiers");
		}
		if (array == null) {
			return out;
		}
		List<String> knownIds = knownCollectionIds();
		for (JsonElement el : array) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			parseUnlockedEntry(el.getAsString(), knownIds, out);
		}
		return out;
	}

	private static List<String> knownCollectionIds() {
		List<String> ids = new ArrayList<>();
		for (HypixelCollectionsCache.Category category : HypixelCollectionsCache.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				ids.add(item.id());
			}
		}
		ids.sort(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
		return ids;
	}

	private static void parseUnlockedEntry(String raw, List<String> knownIds, Map<String, Integer> out) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String key = raw.trim().toUpperCase(Locale.ROOT);
		for (String id : knownIds) {
			String prefix = id.toUpperCase(Locale.ROOT) + "_";
			if (!key.startsWith(prefix)) {
				continue;
			}
			String tierPart = key.substring(prefix.length());
			int tier;
			try {
				tier = Integer.parseInt(tierPart);
			} catch (NumberFormatException ignored) {
				continue;
			}
			if (tier <= 0) {
				return;
			}
			out.merge(id, tier, Math::max);
			for (String alias : CollectionIds.lookupKeys(id)) {
				out.merge(alias, tier, Math::max);
			}
			return;
		}
		// Fallback when collections cache is empty: split on last '_' outside a trailing number.
		int us = key.lastIndexOf('_');
		if (us <= 0 || us >= key.length() - 1) {
			return;
		}
		String id = key.substring(0, us);
		String tierPart = key.substring(us + 1);
		int tier;
		try {
			tier = Integer.parseInt(tierPart);
		} catch (NumberFormatException ignored) {
			return;
		}
		if (tier <= 0) {
			return;
		}
		out.merge(id, tier, Math::max);
		for (String alias : CollectionIds.lookupKeys(id)) {
			out.merge(alias, tier, Math::max);
		}
	}

	private static Map<String, Set<Integer>> readCraftedTiers(JsonObject member) {
		Map<String, Set<Integer>> out = new HashMap<>();
		JsonArray array = null;
		JsonObject playerData = Leveling.obj(member.get("player_data"));
		if (playerData != null && playerData.has("crafted_generators") && playerData.get("crafted_generators").isJsonArray()) {
			array = playerData.getAsJsonArray("crafted_generators");
		} else if (member.has("crafted_generators") && member.get("crafted_generators").isJsonArray()) {
			array = member.getAsJsonArray("crafted_generators");
		}
		if (array == null) {
			return out;
		}
		for (JsonElement el : array) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			parseCraftedEntry(el.getAsString(), out);
		}
		return out;
	}

	private static void parseCraftedEntry(String raw, Map<String, Set<Integer>> out) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String key = raw.trim().toUpperCase(Locale.ROOT);
		int tier = 0;
		String base = key;
		int us = key.lastIndexOf('_');
		if (us > 0 && us < key.length() - 1) {
			String suffix = key.substring(us + 1);
			try {
				tier = Integer.parseInt(suffix);
				base = key.substring(0, us);
			} catch (NumberFormatException ignored) {
				tier = 0;
			}
		}
		if (tier <= 0) {
			return;
		}
		if (!base.endsWith("_GENERATOR")) {
			base = base + "_GENERATOR";
		}
		out.computeIfAbsent(base, ignored -> new TreeSet<>()).add(tier);
	}

	private static void mergeCrafted(Map<String, Set<Integer>> into, Map<String, Set<Integer>> from) {
		for (var entry : from.entrySet()) {
			into.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
		}
	}

	private static long lookupAmount(Map<String, Long> amounts, String itemId) {
		if (amounts == null || itemId == null || itemId.isBlank()) {
			return 0L;
		}
		long direct = lookupDirect(amounts, itemId);
		if (direct > 0L) {
			return direct;
		}
		long composite = 0L;
		for (String part : CollectionIds.compositeParts(itemId)) {
			composite += lookupDirect(amounts, part);
		}
		return composite;
	}

	private static long lookupDirect(Map<String, Long> amounts, String itemId) {
		for (String key : CollectionIds.lookupKeys(itemId)) {
			Long value = amounts.get(key);
			if (value != null && value > 0L) {
				return value;
			}
		}
		return 0L;
	}

	/** Expand each profile collection key under common aliases so UI ids resolve. */
	private static void putAmount(Map<String, Long> out, String key, long amount) {
		if (key == null || key.isBlank() || amount <= 0L) {
			return;
		}
		for (String alias : CollectionIds.lookupKeys(key)) {
			out.merge(alias, amount, Math::max);
		}
	}

	private static String shortUuid(String uuid) {
		if (uuid == null || uuid.length() < 8) {
			return uuid == null ? "?" : uuid;
		}
		return uuid.substring(0, 8);
	}

	private static String prettyMinion(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		String base = id.toUpperCase(Locale.ROOT);
		if (base.endsWith("_GENERATOR")) {
			base = base.substring(0, base.length() - "_GENERATOR".length());
		}
		String[] parts = base.toLowerCase(Locale.ROOT).split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}
}

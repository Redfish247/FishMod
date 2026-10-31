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
import fishmod.pv.api.CoflnetApiClient;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.networth.InventoryDecoder;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuctionSnapshot {
	private static final String[] RARITY_ORDER = {
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE"
	};

	public enum Bucket {
		ACTIVE,
		SOLD,
		BOUGHT
	}

	public record Listing(
		String auctionId,
		String tag,
		String itemName,
		String tier,
		boolean bin,
		long startingBid,
		long highestBid,
		long endMs,
		InventorySnapshot.Slot slot,
		List<String> detailLines,
		// SkyBlock profile the auction belongs to; blank when the source didn't say.
		String profileId
	) {
		public Listing {
			detailLines = List.copyOf(detailLines == null ? List.of() : detailLines);
			profileId = profileId == null ? "" : profileId.replace("-", "").toLowerCase(Locale.ROOT);
		}

		public Listing(
			String auctionId,
			String tag,
			String itemName,
			String tier,
			boolean bin,
			long startingBid,
			long highestBid,
			long endMs,
			InventorySnapshot.Slot slot,
			List<String> detailLines
		) {
			this(auctionId, tag, itemName, tier, bin, startingBid, highestBid, endMs, slot, detailLines, "");
		}

		public Listing(
			String auctionId,
			String tag,
			String itemName,
			String tier,
			boolean bin,
			long startingBid,
			long highestBid,
			long endMs,
			InventorySnapshot.Slot slot
		) {
			this(auctionId, tag, itemName, tier, bin, startingBid, highestBid, endMs, slot, List.of());
		}

		public long price() {
			return this.highestBid > 0L ? this.highestBid : this.startingBid;
		}

		public boolean ended(long nowMs) {
			return this.endMs > 0L && this.endMs <= nowMs;
		}

		public boolean profileKnown() {
			return !this.profileId.isBlank();
		}

		public boolean onProfile(String viewedProfileId) {
			return profileKnown() && viewedProfileId != null
				&& this.profileId.equals(viewedProfileId.replace("-", "").toLowerCase(Locale.ROOT));
		}

		public Listing withTier(String newTier) {
			String t = newTier == null ? "" : newTier;
			if (t.equals(this.tier == null ? "" : this.tier)) {
				return this;
			}
			return new Listing(
				this.auctionId,
				this.tag,
				this.itemName,
				t,
				this.bin,
				this.startingBid,
				this.highestBid,
				this.endMs,
				this.slot,
				this.detailLines,
				this.profileId
			);
		}

		public Listing withEnrichment(String name, String tier, InventorySnapshot.Slot slot, List<String> details) {
			return withEnrichment(name, tier, slot, details, this.profileId);
		}

		public Listing withEnrichment(
			String name,
			String tier,
			InventorySnapshot.Slot slot,
			List<String> details,
			String knownProfileId
		) {
			String n = name == null || name.isBlank() ? this.itemName : name;
			String t = tier == null || tier.isBlank() ? (this.tier == null ? "" : this.tier) : tier;
			InventorySnapshot.Slot s = slot == null ? this.slot : slot;
			List<String> d = details == null ? List.of() : details;
			return new Listing(
				this.auctionId,
				this.tag,
				n,
				t,
				this.bin,
				this.startingBid,
				this.highestBid,
				this.endMs,
				s,
				d,
				knownProfileId == null || knownProfileId.isBlank() ? this.profileId : knownProfileId
			);
		}
	}

	/** Lifetime Auction House stats from {@code player_stats.auctions}. */
	public record Stats(
		long bids,
		long highestBid,
		long won,
		long created,
		long goldSpent,
		long goldEarned,
		long fees,
		Map<String, Long> totalSold,
		Map<String, Long> totalBought
	) {
		public Stats {
			// LinkedHashMap - Map.copyOf does not preserve encounter order.
			totalSold = Collections.unmodifiableMap(new LinkedHashMap<>(totalSold == null ? Map.of() : totalSold));
			totalBought = Collections.unmodifiableMap(new LinkedHashMap<>(totalBought == null ? Map.of() : totalBought));
		}

		public static Stats empty() {
			return new Stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, Map.of(), Map.of());
		}

		public static Stats fromMember(JsonObject member) {
			if (member == null) {
				return empty();
			}
			JsonObject playerStats = member.has("player_stats") && member.get("player_stats").isJsonObject()
				? member.getAsJsonObject("player_stats")
				: null;
			if (playerStats == null || !playerStats.has("auctions") || !playerStats.get("auctions").isJsonObject()) {
				return empty();
			}
			JsonObject a = playerStats.getAsJsonObject("auctions");
			return new Stats(
				longVal(a, "bids"),
				longVal(a, "highest_bid"),
				longVal(a, "won"),
				longVal(a, "created"),
				longVal(a, "gold_spent"),
				longVal(a, "gold_earned"),
				longVal(a, "fees"),
				rarityMap(a, "total_sold"),
				rarityMap(a, "total_bought")
			);
		}

		private static Map<String, Long> rarityMap(JsonObject root, String key) {
			Map<String, Long> out = new LinkedHashMap<>();
			// Highest rarity first (matches in-game AH feel / descending).
			for (int i = RARITY_ORDER.length - 1; i >= 0; i--) {
				out.put(RARITY_ORDER[i], 0L);
			}
			if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
				return out;
			}
			JsonObject obj = root.getAsJsonObject(key);
			for (String rarity : RARITY_ORDER) {
				long n = longVal(obj, rarity);
				if (n <= 0L) {
					n = longVal(obj, rarity.toLowerCase(Locale.ROOT));
				}
				if (n > 0L) {
					out.put(rarity, n);
				}
			}
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				String k = entry.getKey() == null ? "" : entry.getKey().toUpperCase(Locale.ROOT);
				if (k.isBlank() || out.containsKey(k)) {
					continue;
				}
				long n = longVal(obj, entry.getKey());
				if (n > 0L) {
					out.put(k, n);
				}
			}
			return out;
		}
	}

	private final UUID playerUuid;
	private final List<Listing> active;
	private final List<Listing> sold;
	private final List<Listing> bought;
	private final Stats stats;
	private final int soldPage;
	private final int boughtPage;
	private final boolean soldHasMore;
	private final boolean boughtHasMore;
	private final String creditUrl;
	/** Sold/bought come from Coflnet, which can lag seconds behind the active listings. */
	private final boolean historyLoading;

	public AuctionSnapshot(
		UUID playerUuid,
		List<Listing> active,
		List<Listing> sold,
		List<Listing> bought,
		Stats stats,
		int soldPage,
		int boughtPage,
		boolean soldHasMore,
		boolean boughtHasMore
	) {
		this(playerUuid, active, sold, bought, stats, soldPage, boughtPage, soldHasMore, boughtHasMore, false);
	}

	private AuctionSnapshot(
		UUID playerUuid,
		List<Listing> active,
		List<Listing> sold,
		List<Listing> bought,
		Stats stats,
		int soldPage,
		int boughtPage,
		boolean soldHasMore,
		boolean boughtHasMore,
		boolean historyLoading
	) {
		this.historyLoading = historyLoading;
		this.playerUuid = playerUuid;
		this.active = List.copyOf(active == null ? List.of() : active);
		this.sold = List.copyOf(sold == null ? List.of() : sold);
		this.bought = List.copyOf(bought == null ? List.of() : bought);
		this.stats = stats == null ? Stats.empty() : stats;
		this.soldPage = Math.max(0, soldPage);
		this.boughtPage = Math.max(0, boughtPage);
		this.soldHasMore = soldHasMore;
		this.boughtHasMore = boughtHasMore;
		String id = playerUuid == null ? "" : HypixelApiClient.undashed(playerUuid);
		this.creditUrl = id.isBlank() ? "https://sky.coflnet.com/" : "https://sky.coflnet.com/player/" + id;
	}

	public static AuctionSnapshot empty() {
		return new AuctionSnapshot(null, List.of(), List.of(), List.of(), Stats.empty(), 0, 0, false, false);
	}

	public UUID playerUuid() {
		return this.playerUuid;
	}

	public List<Listing> active() {
		return this.active;
	}

	public List<Listing> sold() {
		return this.sold;
	}

	public List<Listing> bought() {
		return this.bought;
	}

	public Stats stats() {
		return this.stats;
	}

	public List<Listing> forBucket(Bucket bucket) {
		return switch (bucket == null ? Bucket.ACTIVE : bucket) {
			case ACTIVE -> this.active;
			case SOLD -> this.sold;
			case BOUGHT -> this.bought;
		};
	}

	public int soldPage() {
		return this.soldPage;
	}

	public int boughtPage() {
		return this.boughtPage;
	}

	public boolean soldHasMore() {
		return this.soldHasMore;
	}

	public boolean boughtHasMore() {
		return this.boughtHasMore;
	}

	public String creditUrl() {
		return this.creditUrl;
	}

	public boolean historyLoading() {
		return this.historyLoading;
	}

	public AuctionSnapshot withHistoryLoading(boolean loading) {
		if (loading == this.historyLoading) {
			return this;
		}
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			this.sold,
			this.bought,
			this.stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore,
			loading
		);
	}

	/** Keeps this snapshot's active listings and stats, takes sold/bought from {@code other}. */
	public AuctionSnapshot withHistoryFrom(AuctionSnapshot other) {
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			other.sold,
			other.bought,
			this.stats,
			other.soldPage,
			other.boughtPage,
			other.soldHasMore,
			other.boughtHasMore,
			other.historyLoading
		);
	}

	public long totalCoins(Bucket bucket) {
		long sum = 0L;
		for (Listing listing : forBucket(bucket)) {
			sum += listing.price();
		}
		return sum;
	}

	public AuctionSnapshot withStats(Stats stats) {
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			this.sold,
			this.bought,
			stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore,
			this.historyLoading
		);
	}

	public AuctionSnapshot withMoreSold(List<Listing> extra, int page, boolean hasMore) {
		List<Listing> merged = mergeById(this.sold, extra);
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			merged,
			this.bought,
			this.stats,
			page,
			this.boughtPage,
			hasMore,
			this.boughtHasMore,
			this.historyLoading
		);
	}

	public AuctionSnapshot withMoreBought(List<Listing> extra, int page, boolean hasMore) {
		List<Listing> merged = mergeById(this.bought, extra);
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			this.sold,
			merged,
			this.stats,
			this.soldPage,
			page,
			this.soldHasMore,
			hasMore,
			this.historyLoading
		);
	}

	/** Patch rarity on listings keyed by auction id (Cofl summaries omit {@code tier}). */
	public AuctionSnapshot withTiers(Map<String, String> tiersByAuctionId) {
		return withTiers(tiersByAuctionId, false);
	}

	/**
	 * Patch rarity on listings keyed by auction id.
	 * @param overwrite when true, replace an existing (e.g. wrong NEU) tier with Cofl's.
	 */
	public AuctionSnapshot withTiers(Map<String, String> tiersByAuctionId, boolean overwrite) {
		if (tiersByAuctionId == null || tiersByAuctionId.isEmpty()) {
			return this;
		}
		Map<String, String> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : tiersByAuctionId.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			String tier = SkyBlockItemFactory.normalizeTier(entry.getValue());
			if (tier.isBlank()) {
				continue;
			}
			normalized.put(entry.getKey().replace("-", "").toLowerCase(Locale.ROOT), tier);
		}
		if (normalized.isEmpty()) {
			return this;
		}
		List<Listing> nextActive = applyTiers(this.active, normalized, overwrite);
		List<Listing> nextSold = applyTiers(this.sold, normalized, overwrite);
		List<Listing> nextBought = applyTiers(this.bought, normalized, overwrite);
		if (nextActive == this.active && nextSold == this.sold && nextBought == this.bought) {
			return this;
		}
		return new AuctionSnapshot(
			this.playerUuid,
			nextActive,
			nextSold,
			nextBought,
			this.stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore,
			this.historyLoading
		);
	}

	public AuctionSnapshot withEnrichments(Map<String, Listing> byAuctionId) {
		if (byAuctionId == null || byAuctionId.isEmpty()) {
			return this;
		}
		Map<String, Listing> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Listing> entry : byAuctionId.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			normalized.put(entry.getKey().replace("-", "").toLowerCase(Locale.ROOT), entry.getValue());
		}
		if (normalized.isEmpty()) {
			return this;
		}
		List<Listing> nextActive = applyEnrichments(this.active, normalized);
		List<Listing> nextSold = applyEnrichments(this.sold, normalized);
		List<Listing> nextBought = applyEnrichments(this.bought, normalized);
		if (nextActive == this.active && nextSold == this.sold && nextBought == this.bought) {
			return this;
		}
		return new AuctionSnapshot(
			this.playerUuid,
			nextActive,
			nextSold,
			nextBought,
			this.stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore,
			this.historyLoading
		);
	}

	private static List<Listing> applyTiers(List<Listing> listings, Map<String, String> tiersByAuctionId, boolean overwrite) {
		if (listings == null || listings.isEmpty()) {
			return listings == null ? List.of() : listings;
		}
		boolean changed = false;
		List<Listing> out = new ArrayList<>(listings.size());
		for (Listing listing : listings) {
			String id = listing.auctionId() == null ? "" : listing.auctionId().replace("-", "").toLowerCase(Locale.ROOT);
			String tier = id.isEmpty() ? null : tiersByAuctionId.get(id);
			boolean missing = listing.tier() == null || listing.tier().isBlank();
			boolean differs = tier != null && !tier.isBlank()
				&& !tier.equalsIgnoreCase(listing.tier() == null ? "" : listing.tier());
			if (tier != null && !tier.isBlank() && (missing || (overwrite && differs))) {
				out.add(listing.withTier(tier));
				changed = true;
			} else {
				out.add(listing);
			}
		}
		return changed ? out : listings;
	}

	private static List<Listing> applyEnrichments(List<Listing> listings, Map<String, Listing> byAuctionId) {
		if (listings == null || listings.isEmpty()) {
			return listings == null ? List.of() : listings;
		}
		boolean changed = false;
		List<Listing> out = new ArrayList<>(listings.size());
		for (Listing listing : listings) {
			String id = listing.auctionId() == null ? "" : listing.auctionId().replace("-", "").toLowerCase(Locale.ROOT);
			Listing enriched = id.isEmpty() ? null : byAuctionId.get(id);
			if (enriched != null) {
				out.add(enriched);
				changed = true;
			} else {
				out.add(listing);
			}
		}
		return changed ? out : listings;
	}

	public static Listing enrichFromCoflDetail(Listing base, JsonObject detail) {
		return enrichFromCoflDetail(base, detail, null);
	}

	public static Listing enrichFromCoflDetail(Listing base, JsonObject detail, UUID player) {
		if (base == null || detail == null) {
			return base;
		}
		String tag = str(detail, "tag");
		if (tag.isBlank()) {
			tag = base.tag();
		}
		String tier = SkyBlockItemFactory.normalizeTier(str(detail, "tier"));
		String name = formatCoflItemName(detail);
		if (name.isBlank()) {
			name = base.itemName();
		}
		List<String> details = coflDetailLines(detail);
		InventorySnapshot.Slot slot = InventoryDecoder.slotFromTag(tag, name, tier);
		return base.withEnrichment(name, tier, slot, details, coflProfileFor(detail, player));
	}

	/** Profile the player used on this auction (as seller, else as bidder); blank if SkyCofl doesn't say. */
	static String coflProfileFor(JsonObject detail, UUID player) {
		if (detail == null || player == null) {
			return "";
		}
		String me = HypixelApiClient.undashed(player).toLowerCase(Locale.ROOT);
		if (me.equals(str(detail, "auctioneerId").replace("-", "").toLowerCase(Locale.ROOT))) {
			return str(detail, "profileId");
		}
		String best = "";
		long bestAmount = -1L;
		JsonElement bids = detail.get("bids");
		if (bids != null && bids.isJsonArray()) {
			for (JsonElement el : bids.getAsJsonArray()) {
				if (el == null || !el.isJsonObject()) {
					continue;
				}
				JsonObject bid = el.getAsJsonObject();
				if (!me.equals(str(bid, "bidder").replace("-", "").toLowerCase(Locale.ROOT))) {
					continue;
				}
				long amount = longVal(bid, "amount");
				if (amount > bestAmount) {
					bestAmount = amount;
					best = str(bid, "profileId");
				}
			}
		}
		return best;
	}

	private static String formatCoflItemName(JsonObject detail) {
		String name = str(detail, "itemName");
		if (name.isBlank()) {
			return "";
		}
		// Cofl often mangles ✪ stars to '?'.
		name = name.replace('\uFFFD', '?');
		int stars = coflUpgradeLevel(detail);
		String starSuffix = stars <= 0 ? "" : " " + "✪".repeat(Math.min(10, stars));
		if (name.indexOf('?') >= 0) {
			name = name.replaceAll("\\s*\\?+", starSuffix).trim();
		} else if (stars > 0 && !name.contains("✪")) {
			name = name.trim() + starSuffix;
		}
		return name;
	}

	private static int coflUpgradeLevel(JsonObject detail) {
		JsonObject flat = obj(detail, "flatNbt");
		if (flat == null) {
			flat = obj(detail, "flattenedNbt");
		}
		if (flat == null) {
			JsonObject nbt = obj(detail, "nbtData");
			flat = nbt == null ? null : obj(nbt, "data");
		}
		int level = intVal(flat, "upgrade_level");
		if (level <= 0) {
			level = intVal(flat, "dungeon_item_level");
		}
		return Math.max(0, level);
	}

	private static List<String> coflDetailLines(JsonObject detail) {
		List<String> lines = new ArrayList<>();
		String reforge = str(detail, "reforge");
		if (!reforge.isBlank() && !"none".equalsIgnoreCase(reforge)) {
			lines.add("§7Reforge: §f" + InventoryDecoder.prettyWords(reforge));
		}
		int stars = coflUpgradeLevel(detail);
		if (stars > 0) {
			lines.add("§6Stars: §e" + stars + " " + "✪".repeat(Math.min(10, stars)));
		}
		JsonObject flat = obj(detail, "flatNbt");
		if (flat == null) {
			flat = obj(detail, "flattenedNbt");
		}
		int hpb = intVal(flat, "hot_potato_count");
		if (hpb > 0) {
			lines.add("§7Hot Potato Books: §e" + hpb);
		}
		int aow = intVal(flat, "art_of_war_count");
		if (aow > 0) {
			lines.add("§7Art of War: §e" + aow);
		}
		int aop = intVal(flat, "art_of_peace_count");
		if (aop > 0) {
			lines.add("§7Art of Peace: §e" + aop);
		}
		if (detail.has("enchantments") && detail.get("enchantments").isJsonArray()) {
			JsonArray enchants = detail.getAsJsonArray("enchantments");
			if (!enchants.isEmpty()) {
				if (!lines.isEmpty()) {
					lines.add("");
				}
				lines.add("§9Enchantments");
				for (JsonElement el : enchants) {
					if (el == null || !el.isJsonObject()) {
						continue;
					}
					JsonObject ench = el.getAsJsonObject();
					String type = str(ench, "type");
					if (type.isBlank()) {
						continue;
					}
					int level = intVal(ench, "level");
					String color = str(ench, "color");
					if (color.isBlank()) {
						color = type.toLowerCase(Locale.ROOT).startsWith("ultimate_") ? "§d" : "§9";
					}
					lines.add(color + InventoryDecoder.prettyWords(type) + (level > 0 ? " " + roman(level) : ""));
				}
			}
		}
		return lines;
	}

	private static String roman(int n) {
		return switch (Math.max(0, Math.min(10, n))) {
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

	private static JsonObject obj(JsonObject root, String key) {
		if (root == null || key == null || !root.has(key) || !root.get(key).isJsonObject()) {
			return null;
		}
		return root.getAsJsonObject(key);
	}

	private static int intVal(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return 0;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			try {
				return (int) obj.get(key).getAsDouble();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored2) {
				return 0;
			}
		}
	}

	public static AuctionSnapshot build(
		UUID playerUuid,
		JsonObject hypixelAuctionRoot,
		JsonArray coflSoldPage0,
		JsonArray coflBidsPage0
	) {
		String player = playerUuid == null ? "" : HypixelApiClient.undashed(playerUuid).toLowerCase(Locale.ROOT);
		long now = System.currentTimeMillis();
		List<Listing> active = parseHypixelActive(hypixelAuctionRoot, player, now);
		List<Listing> sold = parseCoflSold(coflSoldPage0, now);
		List<Listing> bought = parseCoflBought(coflBidsPage0, now);
		boolean soldMore = coflSoldPage0 != null && coflSoldPage0.size() >= 10;
		boolean boughtMore = coflBidsPage0 != null && coflBidsPage0.size() >= 10;
		return new AuctionSnapshot(
			playerUuid, active, sold, bought, Stats.empty(), 0, 0, soldMore, boughtMore
		);
	}

	public static List<Listing> parseCoflSold(JsonArray array) {
		return parseCoflSold(array, System.currentTimeMillis());
	}

	// SkyCofl's player auctions list every auction created, including expired-unsold and live ones.
	static List<Listing> parseCoflSold(JsonArray array, long nowMs) {
		List<Listing> out = new ArrayList<>();
		for (JsonObject obj : CoflnetApiClient.objects(array)) {
			if (longVal(obj, "highestBid") <= 0L || !coflEnded(obj, nowMs)) {
				continue;
			}
			Listing listing = fromCoflSummary(obj, false);
			if (listing != null) {
				out.add(listing);
			}
		}
		return out;
	}

	public static List<Listing> parseCoflBought(JsonArray array) {
		return parseCoflBought(array, System.currentTimeMillis());
	}

	static List<Listing> parseCoflBought(JsonArray array, long nowMs) {
		List<Listing> out = new ArrayList<>();
		for (JsonObject obj : CoflnetApiClient.objects(array)) {
			long own = longVal(obj, "highestOwnBid");
			long high = longVal(obj, "highestBid");
			if (own <= 0L || high <= 0L || own < high || !coflEnded(obj, nowMs)) {
				continue; // not a win (yet)
			}
			Listing listing = fromCoflSummary(obj, true);
			if (listing != null) {
				out.add(listing);
			}
		}
		return out;
	}

	private static List<Listing> parseHypixelActive(JsonObject root, String player, long nowMs) {
		List<Listing> out = new ArrayList<>();
		if (root == null || !root.has("auctions") || !root.get("auctions").isJsonArray()) {
			return out;
		}
		// Hypixel ?player= returns auctions you created OR bid on - Active is only
		// your own listings that are still running (not expired / claimed / bid-only).
		for (JsonElement el : root.getAsJsonArray("auctions")) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject a = el.getAsJsonObject();
			String auctioneer = str(a, "auctioneer").replace("-", "").toLowerCase(Locale.ROOT);
			boolean isSeller = !player.isBlank() && player.equals(auctioneer);
			if (!isSeller) {
				continue;
			}
			long end = longVal(a, "end");
			if (end <= nowMs) {
				continue;
			}
			boolean claimed = bool(a, "claimed");
			if (claimed) {
				continue;
			}

			Listing listing = fromHypixel(a);
			if (listing != null) {
				out.add(listing);
			}
		}
		out.sort((a, b) -> Long.compare(a.endMs(), b.endMs()));
		return out;
	}

	private static Listing fromHypixel(JsonObject a) {
		String id = str(a, "uuid");
		if (id.isBlank()) {
			id = str(a, "auction_id");
		}
		String name = str(a, "item_name");
		String tier = str(a, "tier");
		boolean bin = bool(a, "bin");
		long startBid = longVal(a, "starting_bid");
		long high = longVal(a, "highest_bid_amount");
		long end = longVal(a, "end");
		InventorySnapshot.Slot slot = null;
		if (a.has("item_bytes")) {
			slot = InventoryDecoder.slotFromItemBytes(a.get("item_bytes"));
		}
		String tag = slot == null ? "" : slot.id();
		if (slot == null && !name.isBlank()) {
			slot = InventoryDecoder.slotFromTag("PAPER", name);
		}
		return new Listing(
			id, tag, name.isBlank() ? "Unknown" : name, SkyBlockItemFactory.normalizeTier(tier),
			bin, startBid, high, end, slot, List.of(), str(a, "profile_id")
		);
	}

	private static boolean coflEnded(JsonObject obj, long nowMs) {
		long end = parseCoflTime(str(obj, "end"));
		return end > 0L && end <= nowMs;
	}

	private static Listing fromCoflSummary(JsonObject obj, boolean bought) {
		String id = str(obj, "auctionId");
		if (id.isBlank()) {
			id = str(obj, "uuid");
		}
		String tag = str(obj, "tag");
		String name = str(obj, "itemName");
		boolean bin = bool(obj, "bin");
		long startBid = longVal(obj, "startingBid");
		long high = bought ? longVal(obj, "highestOwnBid") : longVal(obj, "highestBid");
		if (high <= 0L) {
			high = longVal(obj, "highestBid");
		}
		long end = parseCoflTime(str(obj, "end"));
		InventorySnapshot.Slot slot = InventoryDecoder.slotFromTag(tag, name, SkyBlockItemFactory.normalizeTier(str(obj, "tier")));
		// Prefer Cofl's auction tier when present - NEU rarity is often wrong for AH rows.
		String tier = SkyBlockItemFactory.normalizeTier(str(obj, "tier"));
		if (tier.isBlank()) {
			tier = SkyBlockItemFactory.normalizeTier(SkyBlockItemFactory.resolveTier(tag));
		}
		return new Listing(
			id,
			tag,
			name.isBlank() ? (tag.isBlank() ? "Unknown" : tag) : name,
			tier,
			bin,
			startBid,
			high,
			end,
			slot
		);
	}

	private static List<Listing> mergeById(List<Listing> existing, List<Listing> extra) {
		Map<String, Listing> map = new LinkedHashMap<>();
		for (Listing listing : existing) {
			map.put(key(listing), listing);
		}
		if (extra != null) {
			for (Listing listing : extra) {
				map.putIfAbsent(key(listing), listing);
			}
		}
		return new ArrayList<>(map.values());
	}

	private static String key(Listing listing) {
		if (listing.auctionId() != null && !listing.auctionId().isBlank()) {
			return listing.auctionId().toLowerCase(Locale.ROOT);
		}
		return listing.itemName() + "|" + listing.endMs() + "|" + listing.price();
	}

	private static long parseCoflTime(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0L;
		}
		try {
			String normalized = raw.endsWith("Z") ? raw : raw + "Z";
			return Instant.parse(normalized).toEpochMilli();
		} catch (DateTimeParseException ignored) {
			try {
				return Long.parseLong(raw);
			} catch (NumberFormatException ignored2) {
				return 0L;
			}
		}
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static boolean bool(JsonObject obj, String key) {
		JsonElement el = obj == null ? null : obj.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsBoolean();
	}

	private static long longVal(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			return obj.get(key).getAsLong();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			try {
				return (long) obj.get(key).getAsDouble();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored2) {
				return 0L;
			}
		}
	}
}

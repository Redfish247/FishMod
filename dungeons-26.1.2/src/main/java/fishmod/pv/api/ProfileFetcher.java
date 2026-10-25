/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fishmod.pv.data.AuctionSnapshot;
import fishmod.pv.data.BestiarySnapshot;
import fishmod.pv.data.ColeWeight;
import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.data.FarmingFortuneEstimate;
import fishmod.pv.data.FishingSnapshot;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.MiningHotmData;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.data.MiscStatsSnapshot;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.Leveling;
import fishmod.pv.data.PetSnapshot;
import fishmod.pv.data.PlayerStatsCalculator;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.data.RepoData;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.dungeons.DungeonXpData;
import fishmod.pv.gui.ArmorStacks;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NetworthBreakdown;
import fishmod.pv.networth.NetworthCalculator;
import fishmod.pv.networth.NetworthMode;
import fishmod.pv.price.ItemPricer;
import fishmod.pv.weight.WeightBreakdown;
import fishmod.pv.weight.WeightCalculator;
import fishmod.pv.BetterPV;
import fishmod.pv.data.SoftDataFailure;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.ItemStack;

public final class ProfileFetcher {
	/** Match worker profiles cache TTL (5 minutes). */
	private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
	private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
	private static final Pattern MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	/** Post-core enrichment / profile-switch parsing (bounded; avoids parse-pool deadlock). */
	private static final ExecutorService ENRICH_EXECUTOR = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "BetterPV-Enrich");
		t.setDaemon(true);
		return t;
	});
	private static final Object ACTIVE_ENRICH_LOCK = new Object();
	private static volatile ProfileEnrichmentSession activeEnrichment;
	private static volatile fishmod.pv.gui.nav.PvTab requestedTab;

	private ProfileFetcher() {
	}

	// joinedMs is coop_invitation.confirmed_timestamp, 0 for the profile creator.
	public record CoopMemberRef(String uuid, String fallbackName, long joinedMs) {
		public CoopMemberRef {
			uuid = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
			fallbackName = fallbackName == null || fallbackName.isBlank()
				? ProfileCoopIndex.shortCoopUuid(uuid)
				: fallbackName;
			joinedMs = Math.max(0L, joinedMs);
		}
	}

	public record CoopSummary(
		int currentOthers,
		int formerCount,
		List<CoopMemberRef> currentMembers,
		List<CoopMemberRef> formerMembers
	) {
		public CoopSummary {
			currentMembers = currentMembers == null ? List.of() : List.copyOf(currentMembers);
			formerMembers = formerMembers == null ? List.of() : List.copyOf(formerMembers);
			currentOthers = Math.max(0, currentOthers);
			formerCount = Math.max(0, formerCount);
		}

		public static CoopSummary solo() {
			return new CoopSummary(0, 0, List.of(), List.of());
		}

		public boolean soloProfile() {
			return currentOthers == 0 && formerCount == 0;
		}
	}

	public record ProfileChoice(
		String cuteName,
		String profileId,
		boolean selected,
		String gameMode,
		long createdAtMs,
		CoopSummary coop
	) {
		public ProfileChoice {
			cuteName = cuteName == null || cuteName.isBlank() ? "Unknown" : cuteName;
			profileId = profileId == null ? "" : profileId;
			gameMode = gameMode == null ? "" : gameMode.trim();
			createdAtMs = Math.max(0L, createdAtMs);
			coop = coop == null ? CoopSummary.solo() : coop;
		}

		public ProfileChoice(
			String cuteName,
			String profileId,
			boolean selected,
			String gameMode,
			long createdAtMs
		) {
			this(cuteName, profileId, selected, gameMode, createdAtMs, CoopSummary.solo());
		}

		public String gameModeLabel() {
			if (gameMode.isBlank()) {
				return "";
			}
			return switch (gameMode.toLowerCase(java.util.Locale.ROOT)) {
				case "ironman" -> "Ironman";
				case "stranded" -> "Stranded";
				case "bingo" -> "Bingo";
				default -> {
					String lower = gameMode.toLowerCase(java.util.Locale.ROOT);
					yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
				}
			};
		}
	}

	public record LoadedProfile(
		ProfileSnapshot snapshot,
		DungeonSnapshot dungeons,
		InventorySnapshot inventories,
		PetSnapshot pets,
		AuctionSnapshot auctions,
		CollectionSnapshot collections,
		GardenSnapshot garden,
		MiningSnapshot mining,
		ForagingSnapshot foraging,
		FishingSnapshot fishing,
		CrimsonSnapshot crimson,
		RiftSnapshot rift,
		BestiarySnapshot bestiary,
		EventsSnapshot events,
		MiscStatsSnapshot misc,
		JsonObject museumMember,
		String profileId,
		JsonObject profilesRoot,
		List<ProfileChoice> profiles,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networthNormal,
		NetworthBreakdown networthNonCosmetic,
		NetworthBreakdown networthUnsoulbound,
		NetworthBreakdown networthUnsoulboundNonCosmetic,
		ItemStack[] armor,
		PlayerStatsSnapshot playerStats,
		String error
	) {
		public LoadedProfile {
			profiles = profiles == null ? List.of() : List.copyOf(profiles);
		}

		public boolean ok() {
			return error == null || error.isBlank();
		}

		public NetworthBreakdown networth(NetworthMode mode) {
			if (mode == null) {
				return networthNormal;
			}
			return switch (mode) {
				case NORMAL -> networthNormal;
				case NON_COSMETIC -> networthNonCosmetic;
				case UNSOULBOUND -> networthUnsoulbound;
				case UNSOULBOUND_NON_COSMETIC -> networthUnsoulboundNonCosmetic;
			};
		}
	}

	public static CompletableFuture<LoadedProfile> fetch(String playerName) {
		return fetch(playerName, null, UUID.randomUUID().toString(), System.nanoTime());
	}

	/**
	 * Loads the home/core profile first (enough for Home + dismiss loading egg), then publishes
	 * a fully enriched profile through {@code onUpdate}. Fatal failures return {@code !ok()} and
	 * never become a successful core load.
	 */
	public static CompletableFuture<LoadedProfile> fetch(String playerName, Consumer<LoadedProfile> onUpdate) {
		return fetch(playerName, onUpdate, UUID.randomUUID().toString(), System.nanoTime());
	}

	public static CompletableFuture<LoadedProfile> fetch(
		String playerName,
		Consumer<LoadedProfile> onUpdate,
		String correlationId,
		long requestStartedNanos
	) {
		return fetch(playerName, null, onUpdate, correlationId, requestStartedNanos);
	}

	/**
	 * {@code preferredProfileId} keeps a co-op member switch on the same SkyBlock profile instead of
	 * the member's own selected one. Falls back to their selected profile if they aren't in it.
	 */
	public static CompletableFuture<LoadedProfile> fetch(
		String playerName,
		String preferredProfileId,
		Consumer<LoadedProfile> onUpdate,
		String correlationId,
		long requestStartedNanos
	) {
		RepoData.ensureLoaded();
		DungeonXpData.ensureLoaded();
		GardenData.ensureLoaded();
		MiningHotmData.ensureLoaded();
		long startedNanos = requestStartedNanos > 0L ? requestStartedNanos : System.nanoTime();
		String correlation = normalizeCorrelationId(correlationId);
		if (!HypixelApiClient.canFetch()) {
			return CompletableFuture.completedFuture(new LoadedProfile(
				ProfileSnapshot.loading(playerName),
				DungeonSnapshot.empty(),
				InventorySnapshot.empty(),
				PetSnapshot.empty(),
				AuctionSnapshot.empty(),
				CollectionSnapshot.empty(),
				GardenSnapshot.empty(),
				MiningSnapshot.empty(),
				ForagingSnapshot.empty(),
				FishingSnapshot.empty(),
				CrimsonSnapshot.empty(),
				RiftSnapshot.empty(),
				BestiarySnapshot.empty(),
				EventsSnapshot.empty(),
				MiscStatsSnapshot.empty(),
				null,
				null,
				null,
				List.of(),
				WeightBreakdown.empty(fishmod.pv.weight.WeightSystem.SENITHER),
				WeightBreakdown.empty(fishmod.pv.weight.WeightSystem.LILY),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				emptyArmor(),
				PlayerStatsSnapshot.empty(),
				"API unavailable"
			));
		}
		String cleaned = playerName == null ? "" : playerName.trim();
		String preferred = preferredProfileId == null || preferredProfileId.isBlank() ? null : preferredProfileId;
		LoadedProfile cached = getCached(nameKey(cleaned));
		if (cached != null) {
			BetterPV.LOGGER.info("Profile cache hit for {}", cleaned);
			logStage(cleaned, "core_ready", startedNanos, correlation);
			return cachedOnProfile(cached, preferred, onUpdate);
		}
		CompletableFuture<Optional<String>> authFut = FishProxyAuth.bearerTokenAsync();
		HypixelApiClient.UuidName local = localUuid(cleaned);
		CompletableFuture<Optional<HypixelApiClient.UuidName>> uuidFut = local != null
			? CompletableFuture.completedFuture(Optional.of(local))
			: HypixelApiClient.resolveUuid(cleaned);
		return uuidFut.thenCompose(uuidOpt -> {
			logStage(cleaned, local != null ? "uuid_local" : "uuid", startedNanos, correlation);
			if (uuidOpt.isEmpty()) {
				return CompletableFuture.completedFuture(fail(cleaned, "Player not found"));
			}
			HypixelApiClient.UuidName id = uuidOpt.get();
			LoadedProfile byUuid = getCached(uuidKey(id.uuid()));
			if (byUuid != null) {
				BetterPV.LOGGER.info("Profile cache hit for {} ({})", id.name(), id.uuid());
				putCache(nameKey(cleaned), byUuid);
				putCache(nameKey(id.name()), byUuid);
				logStage(cleaned, "core_ready", startedNanos, correlation);
				return cachedOnProfile(byUuid, preferred, onUpdate);
			}

			return authFut
				.thenCompose(ignored -> {
					logStage(cleaned, "auth", startedNanos, correlation);
					return HypixelApiClient.skyblockProfiles(id.uuid(), correlation);
				})
				.thenCompose(profilesOpt -> {
					logStage(cleaned, "profiles", startedNanos, correlation);
					if (profilesOpt.isEmpty()) {
						String authMessage = FishProxyAuth.userFacingFailure()
							.orElse("Profiles request failed");
						FishProxyAuth.notifyPlayerIfNeeded();
						return CompletableFuture.completedFuture(fail(id.name(), authMessage));
					}
					JsonObject root = profilesOpt.get();
					logProfileShape(cleaned, root);
					CompletableFuture<Optional<JsonObject>> electionFut =
						HypixelApiClient.skyblockElection();
					PlayerAuctionFutures auctionFuts = playerAuctionFutures(id.uuid());
					CompletableFuture<Optional<JsonObject>> auctionFut = auctionFuts.auction();
					CompletableFuture<Optional<JsonArray>> soldFut = auctionFuts.sold();
					CompletableFuture<Optional<JsonArray>> bidsFut = auctionFuts.bids();

					CompletableFuture<LoadedProfile> coreFuture = CompletableFuture.supplyAsync(
						() -> ProfileHomeParser.parseHomeCore(id.name(), id.uuid(), root, preferred),
						HypixelApiClient.parseExecutor()
					);

					coreFuture.thenAccept(core -> {
						logStage(cleaned, "core_ready", startedNanos, correlation);
						if (core != null && core.ok()) {
							// Cache core immediately so a quick second /pv is warm for first paint.
							putCache(uuidKey(id.uuid()), core);
							putCache(nameKey(id.name()), core);
							putCache(nameKey(cleaned), core);
							startProgressiveEnrichment(
								core,
								nameKey(cleaned),
								root,
								electionFut,
								auctionFut,
								soldFut,
								bidsFut,
								onUpdate
							);
						}
					});
					return coreFuture;
				});
		});
	}

	private static CompletableFuture<LoadedProfile> cachedOnProfile(
		LoadedProfile cached,
		String preferredProfileId,
		Consumer<LoadedProfile> onUpdate
	) {
		if (preferredProfileId == null
			|| preferredProfileId.equals(cached.profileId())
			|| cached.profilesRoot() == null
			|| cached.snapshot() == null
			|| cached.snapshot().playerUuid() == null) {
			return CompletableFuture.completedFuture(cached);
		}
		return switchToProfile(
			cached.snapshot().playerName(),
			cached.snapshot().playerUuid(),
			cached.profilesRoot(),
			preferredProfileId,
			onUpdate
		);
	}

	// Skips the Mojang lookup when the client already knows the player. Tab entries are
	// only trusted for real accounts (version 4 UUIDs); Hypixel NPCs and fake tab rows
	// use version 2.
	private static HypixelApiClient.UuidName localUuid(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || !client.isSameThread() || !MINECRAFT_NAME.matcher(name).matches()) {
			return null;
		}
		GameProfile profile = null;
		if (client.player != null && name.equalsIgnoreCase(client.player.getGameProfile().name())) {
			profile = client.player.getGameProfile();
		} else if (client.getConnection() != null) {
			PlayerInfo info = client.getConnection().getPlayerInfoIgnoreCase(name);
			if (info != null && info.getProfile().id() != null && info.getProfile().id().version() == 4) {
				profile = info.getProfile();
			}
		}
		if (profile == null || profile.id() == null || !name.equalsIgnoreCase(profile.name())) {
			return null;
		}
		return new HypixelApiClient.UuidName(profile.id(), profile.name());
	}

	private static void logProfileShape(String name, JsonObject root) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: new JsonArray();
		int totalMembers = 0;
		int selectedMembers = 0;
		StringBuilder perProfile = new StringBuilder();
		for (var element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			int members = profile.has("members") && profile.get("members").isJsonObject()
				? profile.getAsJsonObject("members").size()
				: 0;
			totalMembers += members;
			boolean selected = profile.has("selected") && profile.get("selected").isJsonPrimitive()
				&& profile.get("selected").getAsBoolean();
			if (selected) {
				selectedMembers = members;
			}
			if (!perProfile.isEmpty()) {
				perProfile.append(',');
			}
			perProfile.append(members).append(selected ? "*" : "");
		}
		BetterPV.LOGGER.info(
			"[PV timing] {} shape profiles={} members={} selectedMembers={} perProfile=[{}]",
			name, profiles.size(), totalMembers, selectedMembers, perProfile
		);
	}

	private static void logStage(String name, String stage, long startedNanos, String correlationId) {
		BetterPV.LOGGER.info(
			"[PV timing] event={} correlation_id={} player={} elapsed_ms={}",
			stage,
			correlationId,
			name,
			(System.nanoTime() - startedNanos) / 1_000_000L
		);
	}

	private static String normalizeCorrelationId(String correlationId) {
		if (correlationId != null && correlationId.matches("[A-Za-z0-9._:-]{1,80}")) {
			return correlationId;
		}
		return UUID.randomUUID().toString();
	}

	/**
	 * Prefer loading the clicked tab next while background enrichment is still running.
	 */
	public static void prioritizeTab(fishmod.pv.gui.nav.PvTab tab) {
		// The screen asks before its session exists, so remember it for the next session too.
		requestedTab = tab;
		ProfileEnrichmentSession session = activeEnrichment;
		if (session != null) {
			session.prioritize(tab);
		}
	}

	static void clearActiveEnrichment(ProfileEnrichmentSession session) {
		synchronized (ACTIVE_ENRICH_LOCK) {
			if (activeEnrichment == session) {
				activeEnrichment = null;
			}
		}
	}

	static void cacheEnriched(UUID uuid, String name, String cleanedNameKey, LoadedProfile loaded) {
		if (loaded == null || !loaded.ok()) {
			return;
		}
		putCache(uuidKey(uuid), loaded);
		putCache(nameKey(name), loaded);
		if (cleanedNameKey != null && !cleanedNameKey.isBlank()) {
			putCache(cleanedNameKey, loaded);
		}
	}

	static DungeonSnapshot parseDungeonsPublic(
		JsonObject member,
		JsonObject museumMember,
		JsonObject electionRoot,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories
	) {
		return ProfileDungeonParser.parseDungeons(member, museumMember, electionRoot, inventoryCategories);
	}

	static JsonObject findMuseumMemberPublic(JsonObject museumRoot, String profileId, String undashed) {
		return ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed);
	}

	static void scheduleNetworthRefreshPublic(
		LoadedProfile base,
		JsonObject member,
		JsonObject profile,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories,
		Consumer<LoadedProfile> onUpdate
	) {
		scheduleNetworthRefresh(base, member, profile, museumMember, inventoryCategories, onUpdate);
	}

	/**
	 * Called after the Museum tab fetched museum data, so networth can include it
	 * without /pv ever requesting museum itself.
	 */
	public static void museumLoaded(UUID uuid, String profileId, JsonObject museumMember) {
		if (uuid == null || profileId == null || museumMember == null) {
			return;
		}
		ProfileEnrichmentSession session = activeEnrichment;
		if (session != null && session.matches(uuid, profileId) && session.requeueNetworth()) {
			return;
		}
		LoadedProfile base = getCached(uuidKey(uuid));
		if (base == null || !profileId.equals(base.profileId()) || base.profilesRoot() == null) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			JsonObject root = base.profilesRoot();
			JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
				? root.getAsJsonArray("profiles")
				: null;
			JsonObject profile = ProfileCoopIndex.pickProfile(profiles, profileId);
			if (profile == null) {
				return;
			}
			JsonObject members = profile.has("members") && profile.get("members").isJsonObject()
				? profile.getAsJsonObject("members")
				: null;
			JsonObject member = ProfileMuseumLookup.findMember(members, HypixelApiClient.undashed(uuid));
			if (member == null) {
				return;
			}
			Map<String, List<InventoryDecoder.Stack>> categories = InventoryDecoder.withSharedDecode(
				() -> InventoryDecoder.parseCategories(member, museumMember)
			);
			scheduleNetworthRefresh(base, member, profile, museumMember, categories, null);
		}, ENRICH_EXECUTOR);
	}

	private static void startProgressiveEnrichment(
		LoadedProfile core,
		String cleanedNameKey,
		JsonObject root,
		CompletableFuture<Optional<JsonObject>> electionFut,
		CompletableFuture<Optional<JsonObject>> auctionFut,
		CompletableFuture<Optional<JsonArray>> soldFut,
		CompletableFuture<Optional<JsonArray>> bidsFut,
		Consumer<LoadedProfile> onUpdate
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		JsonObject best = ProfileCoopIndex.pickProfile(profiles, core.profileId());
		if (best == null || core.snapshot() == null || core.snapshot().playerUuid() == null) {
			return;
		}
		String undashed = HypixelApiClient.undashed(core.snapshot().playerUuid());
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = ProfileMuseumLookup.findMember(members, undashed);
		if (member == null) {
			return;
		}
		ProfileEnrichmentSession session = new ProfileEnrichmentSession(
			core,
			cleanedNameKey,
			root,
			best,
			members,
			member,
			electionFut,
			auctionFut,
			soldFut,
			bidsFut,
			onUpdate,
			ENRICH_EXECUTOR
		);
		synchronized (ACTIVE_ENRICH_LOCK) {
			if (activeEnrichment != null) {
				activeEnrichment.cancel();
			}
			activeEnrichment = session;
		}
		fishmod.pv.gui.nav.PvTab tab = requestedTab;
		if (tab != null) {
			session.prioritize(tab);
		}
		session.start();
	}

	/**
	 * Async re-parse for profile switching. Publishes core first when {@code onUpdate} is set,
	 * then enrichment. Caller must ignore stale results via its own generation counter.
	 */
	public static CompletableFuture<LoadedProfile> switchToProfile(
		String name,
		UUID uuid,
		JsonObject root,
		String profileId,
		Consumer<LoadedProfile> onUpdate
	) {
		CompletableFuture<LoadedProfile> coreFuture = CompletableFuture.supplyAsync(
			() -> ProfileHomeParser.parseHomeCore(name, uuid, root, profileId),
			HypixelApiClient.parseExecutor()
		);
		coreFuture.thenAccept(core -> {
			if (core == null || !core.ok()) {
				return;
			}
			CompletableFuture<Optional<JsonObject>> electionFut = HypixelApiClient.skyblockElection();
			PlayerAuctionFutures auctionFuts = playerAuctionFutures(uuid);
			CompletableFuture<Optional<JsonObject>> auctionFut = auctionFuts.auction();
			CompletableFuture<Optional<JsonArray>> soldFut = auctionFuts.sold();
			CompletableFuture<Optional<JsonArray>> bidsFut = auctionFuts.bids();
			startProgressiveEnrichment(
				core,
				nameKey(name),
				root,
				electionFut,
				auctionFut,
				soldFut,
				bidsFut,
				onUpdate
			);
		});
		return coreFuture;
	}

	// Hypixel auctions and Cofl history are per player, not per profile, so profile
	// switches reuse the last player's requests instead of fetching them again.
	private record PlayerAuctionFutures(
		UUID uuid,
		long createdAtMillis,
		CompletableFuture<Optional<JsonObject>> auction,
		CompletableFuture<Optional<JsonArray>> sold,
		CompletableFuture<Optional<JsonArray>> bids
	) {
	}

	private static volatile PlayerAuctionFutures lastAuctionFutures;

	private static PlayerAuctionFutures playerAuctionFutures(UUID uuid) {
		long now = System.currentTimeMillis();
		PlayerAuctionFutures last = lastAuctionFutures;
		if (last != null
			&& last.uuid().equals(uuid)
			&& now - last.createdAtMillis() < CACHE_TTL_MS
			&& !HypixelApiClient.failed(last.auction())
			&& !HypixelApiClient.failed(last.sold())
			&& !HypixelApiClient.failed(last.bids())) {
			return last;
		}
		PlayerAuctionFutures next = new PlayerAuctionFutures(
			uuid,
			now,
			// The Auctions tab is gone and the proxy has no auction route: no calls, no failures.
			CompletableFuture.completedFuture(Optional.empty()),
			CompletableFuture.completedFuture(Optional.empty()),
			CompletableFuture.completedFuture(Optional.empty())
		);
		lastAuctionFutures = next;
		return next;
	}

	private static String nameKey(String name) {
		return "n:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
	}

	private static String uuidKey(UUID uuid) {
		return "u:" + HypixelApiClient.undashed(uuid);
	}

	private static LoadedProfile getCached(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		CacheEntry entry = CACHE.get(key);
		if (entry == null) {
			return null;
		}
		if (!entry.fresh()) {
			CACHE.remove(key, entry);
			return null;
		}
		return entry.profile();
	}

	private static void putCache(String key, LoadedProfile profile) {
		if (key == null || key.isBlank() || profile == null || !profile.ok()) {
			return;
		}
		// drop expired profiles so viewed players don't pile up for the whole session
		CACHE.values().removeIf(e -> !e.fresh());
		CACHE.put(key, new CacheEntry(profile, System.currentTimeMillis() + CACHE_TTL_MS));
	}

	private record CacheEntry(LoadedProfile profile, long expiresAtMs) {
		boolean fresh() {
			return System.currentTimeMillis() < this.expiresAtMs;
		}
	}

	public static LoadedProfile failed(String name, String error) {
		return new LoadedProfile(
			ProfileSnapshot.loading(name),
			DungeonSnapshot.empty(),
			InventorySnapshot.empty(),
			PetSnapshot.empty(),
			AuctionSnapshot.empty(),
			CollectionSnapshot.empty(),
			GardenSnapshot.empty(),
			MiningSnapshot.empty(),
			ForagingSnapshot.empty(),
			FishingSnapshot.empty(),
			CrimsonSnapshot.empty(),
			RiftSnapshot.empty(),
			BestiarySnapshot.empty(),
			EventsSnapshot.empty(),
			MiscStatsSnapshot.empty(),
			null,
			null,
			null,
			List.of(),
			WeightBreakdown.empty(fishmod.pv.weight.WeightSystem.SENITHER),
			WeightBreakdown.empty(fishmod.pv.weight.WeightSystem.LILY),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			emptyArmor(),
			PlayerStatsSnapshot.empty(),
			error
		);
	}

	private static LoadedProfile fail(String name, String error) {
		return failed(name, error);
	}

	private static ItemStack[] emptyArmor() {
		return new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	}

	private static LoadedProfile parse(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions
	) {
		return parse(name, uuid, root, museumRoot, electionRoot, auctions, null);
	}

	private static LoadedProfile parse(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions,
		String preferredProfileId
	) {
		try {
			return parseUnsafe(name, uuid, root, museumRoot, electionRoot, auctions, true, preferredProfileId);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Profile parse failed for {}", name, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	public static LoadedProfile parseByProfileId(String name, UUID uuid, JsonObject root, String profileId) {
		if (root == null || profileId == null || profileId.isBlank()) {
			return fail(name, "Missing profile");
		}
		try {
			return InventoryDecoder.withSharedDecode(() -> parseUnsafe(
				name, uuid, root, null, null, AuctionSnapshot.empty(), true, profileId
			));
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Profile re-parse failed for {} ({})", name, profileId, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	private static LoadedProfile parseUnsafe(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions,
		boolean includeNetworth,
		String preferredProfileId
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return fail(name, "No SkyBlock profiles");
		}
		List<ProfileChoice> choices;
		JsonObject best = ProfileCoopIndex.pickProfile(profiles, preferredProfileId);
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		if (best != null) {
			cuteName = best.has("cute_name") ? best.get("cute_name").getAsString() : cuteName;
			profileId = best.has("profile_id") ? best.get("profile_id").getAsString() : profileId;
		}
		choices = ProfileCoopIndex.listProfileChoices(profiles, profileId, undashed);
		if (best == null) {
			return fail(name, "No usable profile");
		}
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = ProfileMuseumLookup.findMember(members, undashed);
		if (member == null) {
			return fail(name, "Member data missing");
		}

		ProfileCoopIndex.warmCoopMemberNames(choices, undashed, name, null);

		Map<String, Leveling.Progress> weightLevels = WeightCalculator.buildLevels(member);
		WeightBreakdown senither = WeightCalculator.senither(member, weightLevels);
		WeightBreakdown lily = WeightCalculator.lily(member, weightLevels);

		// Never block first paint on prices. Brief wait only during enrichment.
		if (includeNetworth && !ItemPricer.isReady()) {
			ItemPricer.awaitReady(1_500L);
		}
		JsonObject museumMember = includeNetworth
			? ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed)
			: null;
		BetterPV.LOGGER.info("Parsing profile {} ({})", name, cuteName);

		Map<String, List<InventoryDecoder.Stack>> inventoryCategories =
			InventoryDecoder.parseCategories(member, museumMember);

		boolean pricesReady = includeNetworth && ItemPricer.isReady();
		NetworthBreakdown networthNormal = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NORMAL)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthNonCosmetic = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NON_COSMETIC)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulbound = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulboundNonCosmetic = pricesReady
			? NetworthCalculator.calculate(
				member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND_NON_COSMETIC
			)
			: NetworthBreakdown.empty("Loading networth");

		List<ProfileSnapshot.SkillEntry> skills = ProfileHomeParser.buildHomeSkills(member);
		ProfileSnapshot.SkillEntry social = ProfileHomeParser.buildSocial(member);
		ProfileSnapshot.SkillEntry runecrafting = ProfileHomeParser.buildRunecrafting(member);
		List<ProfileSnapshot.SlayerEntry> slayers = ProfileHomeParser.buildHomeSlayers(member);
		ProfileSnapshot.ActiveSlayerQuest activeSlayer = ProfileHomeParser.parseActiveSlayerQuest(member);

		int sbLevel = 0;
		int sbXp = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float experience = Leveling.num(leveling.get("experience"));
			if (experience != null) {
				sbLevel = (int) Math.floor(experience / 100F);
				sbXp = Math.round(experience % 100F);
			}
		}
		ProfileSnapshot.EmblemInfo emblems = ProfileHomeParser.parseEmblems(leveling);

		String nwText = !includeNetworth || !pricesReady
			? "..."
			: networthNormal.total() > 0
			? FormatUtil.shortCoins(networthNormal.total())
			: "-";

		double purseCoins = NetworthCalculator.purse(member);
		double bankCoins = NetworthCalculator.bank(best, member);
		List<ProfileSnapshot.BankTransaction> bankTransactions = ProfileHomeParser.parseBankTransactions(best);

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			nwText,
			purseCoins,
			bankCoins,
			bankTransactions,
			skills,
			slayers,
			social,
			runecrafting,
			activeSlayer,
			emblems,
			fishmod.pv.slayer.SlayerMayorMods.from(electionRoot)
		);

		DungeonSnapshot dungeons = ProfileDungeonParser.parseDungeons(
			member, museumMember, electionRoot, inventoryCategories
		);

		InventorySnapshot inventories;
		try {
			inventories = InventoryDecoder.parseUi(member);
			fishmod.pv.gui.inventories.SkyBlockItemFactory.warmAsync(inventories);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Inventory decode failed for {}", name, exception);
			inventories = InventorySnapshot.empty();
		}

		PetSnapshot pets;
		try {
			pets = PetSnapshot.fromMember(member);
			fishmod.pv.gui.inventories.SkyBlockItemFactory.warmPetsAsync(pets);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Pets decode failed for {}", name, exception);
			pets = PetSnapshot.empty();
		}

		CollectionSnapshot collections;
		try {
			collections = CollectionSnapshot.fromProfile(members, uuid, name);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Collections decode failed for {}", name, exception);
			collections = CollectionSnapshot.empty();
		}

		GardenSnapshot garden;
		try {
			garden = GardenSnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Garden member parse failed for {}", name, exception);
			garden = GardenSnapshot.empty();
		}

		MiningSnapshot mining;
		try {
			mining = MiningSnapshot.fromMember(member);
			mining = mining.withColeWeight(ColeWeight.calculate(mining, collections, member));
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Mining member parse failed for {}", name, exception);
			mining = MiningSnapshot.empty();
		}
		ForagingSnapshot foraging;
		try {
			foraging = ForagingSnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Foraging member parse failed for {}", name, exception);
			foraging = ForagingSnapshot.empty();
		}
		FishingSnapshot fishing;
		try {
			fishing = FishingSnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Fishing member parse failed for {}", name, exception);
			fishing = FishingSnapshot.empty();
		}
		CrimsonSnapshot crimson;
		try {
			crimson = CrimsonSnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Crimson member parse failed for {}", name, exception);
			crimson = CrimsonSnapshot.empty();
		}
		RiftSnapshot rift;
		try {
			rift = RiftSnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Rift member parse failed for {}", name, exception);
			rift = RiftSnapshot.empty();
		}
		BestiarySnapshot bestiary;
		try {
			bestiary = BestiarySnapshot.fromMember(member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Bestiary member parse failed for {}", name, exception);
			bestiary = BestiarySnapshot.empty();
		}
		EventsSnapshot events;
		try {
			events = EventsSnapshot.fromMember(member, root, uuid);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Events member parse failed for {}", name, exception);
			events = EventsSnapshot.empty();
		}
		MiscStatsSnapshot misc;
		try {
			misc = MiscStatsSnapshot.from(best, member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Misc stats parse failed for {}", name, exception);
			misc = MiscStatsSnapshot.empty();
		}
		BetterPV.LOGGER.info("Profile ready for {} (nw={})", name, nwText);
		AuctionSnapshot auctionSnapshot = auctions == null ? AuctionSnapshot.empty() : auctions;
		auctionSnapshot = auctionSnapshot.withStats(AuctionSnapshot.Stats.fromMember(member));
		PlayerStatsSnapshot playerStats = FarmingFortuneEstimate.attach(PlayerStatsCalculator.fromMember(member), member, inventoryCategories);
		crimson = crimson.withPlayerStats(playerStats);

		LoadedProfile loaded = new LoadedProfile(
			snapshot,
			dungeons,
			inventories,
			pets,
			auctionSnapshot,
			collections,
			garden,
			mining,
			foraging,
			fishing,
			crimson,
			rift,
			bestiary,
			events,
			misc,
			museumMember,
			profileId,
			root,
			choices,
			senither,
			lily,
			networthNormal,
			networthNonCosmetic,
			networthUnsoulbound,
			networthUnsoulboundNonCosmetic,
			ArmorStacks.fromMember(member),
			playerStats,
			null
		);

		if (includeNetworth && !pricesReady) {
			scheduleNetworthRefresh(loaded, member, best, museumMember, inventoryCategories, null);
		}
		return loaded;
	}

	private static void scheduleNetworthRefresh(
		LoadedProfile base,
		JsonObject member,
		JsonObject profile,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories,
		Consumer<LoadedProfile> onUpdate
	) {
		if (base == null || !base.ok() || base.snapshot() == null || base.snapshot().playerUuid() == null) {
			return;
		}
		UUID uuid = base.snapshot().playerUuid();
		String name = base.snapshot().playerName();
		CompletableFuture.runAsync(() -> {
			ItemPricer.awaitReady(30_000L);
			if (!ItemPricer.isReady()) {
				return;
			}
			try {
				NetworthBreakdown normal = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.NORMAL
				);
				NetworthBreakdown nonCosmetic = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.NON_COSMETIC
				);
				NetworthBreakdown unsoulbound = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND
				);
				NetworthBreakdown unsoulboundNonCosmetic = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND_NON_COSMETIC
				);
				String nwText = normal.total() > 0 ? FormatUtil.shortCoins(normal.total()) : "-";
				LoadedProfile refreshed = new LoadedProfile(
					base.snapshot().withNetworthText(nwText),
					base.dungeons(),
					base.inventories(),
					base.pets(),
					base.auctions(),
					base.collections(),
					base.garden(),
					base.mining(),
					base.foraging(),
					base.fishing(),
					base.crimson(),
					base.rift(),
					base.bestiary(),
					base.events(),
					base.misc(),
					museumMember != null ? museumMember : base.museumMember(),
					base.profileId(),
					base.profilesRoot(),
					base.profiles(),
					base.senither(),
					base.lily(),
					normal,
					nonCosmetic,
					unsoulbound,
					unsoulboundNonCosmetic,
					base.armor(),
					base.playerStats(),
					null
				);
				putCache(uuidKey(uuid), refreshed);
				putCache(nameKey(name), refreshed);
				if (onUpdate != null) {
					onUpdate.accept(refreshed);
				}
				for (Consumer<LoadedProfile> listener : NETWORTH_LISTENERS) {
					try {
						listener.accept(refreshed);
					} catch (RuntimeException listenerError) {
						BetterPV.LOGGER.debug("Networth listener failed for {}", name, listenerError);
					}
				}
			} catch (RuntimeException exception) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception;
				}
				BetterPV.LOGGER.warn("Deferred networth refresh failed for {}", name, exception);
			}
		}, ENRICH_EXECUTOR);
	}

	private static final java.util.concurrent.CopyOnWriteArrayList<Consumer<LoadedProfile>> NETWORTH_LISTENERS =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Registers a listener for deferred networth refreshes (screen generation must filter stale). */
	public static void addNetworthListener(Consumer<LoadedProfile> listener) {
		if (listener != null) {
			NETWORTH_LISTENERS.addIfAbsent(listener);
		}
	}

	public static void removeNetworthListener(Consumer<LoadedProfile> listener) {
		if (listener != null) {
			NETWORTH_LISTENERS.remove(listener);
		}
	}

	public static String coopMemberDisplayName(CoopMemberRef member) {
		return ProfileCoopIndex.coopMemberDisplayName(member);
	}

	public static boolean coopNameResolved(CoopMemberRef member) {
		return ProfileCoopIndex.coopNameResolved(member);
	}

	public static void warmCoopMemberNames(
		List<ProfileChoice> choices,
		String viewedUuidUndashed,
		String viewedName,
		Runnable onResolved
	) {
		ProfileCoopIndex.warmCoopMemberNames(choices, viewedUuidUndashed, viewedName, onResolved);
	}

	public static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
		return ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed);
	}
}

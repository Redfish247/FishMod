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
import fishmod.pv.BetterPV;
import fishmod.pv.data.SoftDataFailure;
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
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.data.MuseumCache;
import fishmod.pv.data.PetSnapshot;
import fishmod.pv.data.PlayerStatsCalculator;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.gui.ArmorStacks;
import fishmod.pv.gui.nav.PvTab;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NetworthBreakdown;
import fishmod.pv.networth.NetworthCalculator;
import fishmod.pv.networth.NetworthMode;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * After Home core is shown, fills remaining tabs one job at a time.
 * Clicking a tab moves that job to the front of the pending queue.
 */
final class ProfileEnrichmentSession {
	enum Job {
		INVENTORY,
		DUNGEONS,
		PETS,
		COLLECTIONS,
		MINING,
		GARDEN,
		FORAGING,
		FISHING,
		CRIMSON,
		RIFT,
		BESTIARY,
		EVENTS,
		AUCTIONS,
		AUCTION_HISTORY,
		MUSEUM,
		NETWORTH
	}

	private static final Job[] DEFAULT_ORDER = {
		Job.INVENTORY,
		Job.DUNGEONS,
		Job.PETS,
		Job.COLLECTIONS,
		Job.MINING,
		Job.GARDEN,
		Job.FORAGING,
		Job.FISHING,
		Job.CRIMSON,
		Job.RIFT,
		Job.BESTIARY,
		Job.EVENTS,
		Job.AUCTIONS,
		Job.MUSEUM,
		Job.NETWORTH
	};

	private final Object lock = new Object();
	private final ArrayDeque<Job> pending = new ArrayDeque<>();
	private final EnumSet<Job> done = EnumSet.noneOf(Job.class);
	private final EnumSet<Job> started = EnumSet.noneOf(Job.class);

	private final String name;
	private final UUID uuid;
	private final String cleanedNameKey;
	private final JsonObject root;
	private final JsonObject best;
	private final JsonObject members;
	private final JsonObject member;
	private final String profileId;
	private final String undashed;
	private final Consumer<ProfileFetcher.LoadedProfile> onUpdate;
	private final Executor executor;

	private final CompletableFuture<Optional<JsonObject>> electionFut;
	private final CompletableFuture<Optional<JsonObject>> auctionFut;
	private final CompletableFuture<Optional<JsonArray>> soldFut;
	private final CompletableFuture<Optional<JsonArray>> bidsFut;

	private ProfileFetcher.LoadedProfile current;
	private Map<String, List<InventoryDecoder.Stack>> inventoryCategories = Map.of();
	private volatile boolean cancelled;
	/** Guarded by {@link #lock}; false once the loop has drained the queue. */
	private boolean running = true;

	ProfileEnrichmentSession(
		ProfileFetcher.LoadedProfile core,
		String cleanedNameKey,
		JsonObject root,
		JsonObject best,
		JsonObject members,
		JsonObject member,
		CompletableFuture<Optional<JsonObject>> electionFut,
		CompletableFuture<Optional<JsonObject>> auctionFut,
		CompletableFuture<Optional<JsonArray>> soldFut,
		CompletableFuture<Optional<JsonArray>> bidsFut,
		Consumer<ProfileFetcher.LoadedProfile> onUpdate,
		Executor executor
	) {
		this.current = core;
		this.name = core.snapshot().playerName();
		this.uuid = core.snapshot().playerUuid();
		this.cleanedNameKey = cleanedNameKey;
		this.root = root;
		this.best = best;
		this.members = members;
		this.member = member;
		this.profileId = core.profileId();
		this.undashed = HypixelApiClient.undashed(this.uuid);
		this.electionFut = electionFut;
		this.auctionFut = auctionFut;
		this.soldFut = soldFut;
		this.bidsFut = bidsFut;
		this.onUpdate = onUpdate;
		this.executor = executor;
		for (Job job : DEFAULT_ORDER) {
			this.pending.addLast(job);
		}
	}

	void cancel() {
		this.cancelled = true;
	}

	void start() {
		this.executor.execute(this::runLoop);
	}

	void prioritize(PvTab tab) {
		Job job = jobForTab(tab);
		if (job == null) {
			return;
		}
		synchronized (this.lock) {
			if (this.done.contains(job) || this.started.contains(job)) {
				return;
			}
			// Dependencies go in front of the requested job, so add the job first.
			this.pending.remove(job);
			this.pending.addFirst(job);
			if (job == Job.MINING && !this.done.contains(Job.COLLECTIONS) && !this.started.contains(Job.COLLECTIONS)) {
				this.pending.remove(Job.COLLECTIONS);
				this.pending.addFirst(Job.COLLECTIONS);
			}
			// Inventory decode is required before several tabs; pull it forward too.
			if (job != Job.INVENTORY && !this.done.contains(Job.INVENTORY) && !this.started.contains(Job.INVENTORY)) {
				this.pending.remove(Job.INVENTORY);
				this.pending.addFirst(Job.INVENTORY);
			}
		}
	}

	boolean matches(UUID uuid, String profileId) {
		return this.uuid != null && this.uuid.equals(uuid)
			&& this.profileId != null && this.profileId.equals(profileId);
	}

	/** Re-runs networth after museum data arrives. False when the loop has already finished. */
	boolean requeueNetworth() {
		synchronized (this.lock) {
			if (!this.running || this.cancelled) {
				return false;
			}
			this.done.remove(Job.NETWORTH);
			this.started.remove(Job.NETWORTH);
			this.pending.remove(Job.NETWORTH);
			this.pending.addFirst(Job.NETWORTH);
			return true;
		}
	}

	/** Queues a job that became runnable later, restarting the loop if it already drained. */
	private void enqueueLate(Job job) {
		boolean restart;
		synchronized (this.lock) {
			if (this.cancelled) {
				return;
			}
			this.pending.remove(job);
			this.pending.addLast(job);
			restart = !this.running;
			this.running = true;
		}
		if (restart) {
			this.executor.execute(this::runLoop);
		}
	}

	static Job jobForTab(PvTab tab) {
		if (tab == null) {
			return null;
		}
		return switch (tab) {
			case HOME -> Job.NETWORTH;
			case DUNGEONS -> Job.DUNGEONS;
			case INVENTORIES -> Job.INVENTORY;
			case PETS -> Job.PETS;
			case COLLECTIONS -> Job.COLLECTIONS;
			case GARDEN -> Job.GARDEN;
			case MINING -> Job.MINING;
			case FORAGING -> Job.FORAGING;
			case FISHING -> Job.FISHING;
			case CRIMSON -> Job.CRIMSON;
			case RIFT -> Job.RIFT;
			case MUSEUM -> Job.MUSEUM;
			case BESTIARY -> Job.BESTIARY;
			case EVENTS -> Job.EVENTS;
		};
	}

	private void runLoop() {
		long sessionStartedNanos = System.nanoTime();
		boolean drained = false;
		try {
			while (!this.cancelled) {
				Job next;
				synchronized (this.lock) {
					next = this.pending.pollFirst();
					if (next != null) {
						this.started.add(next);
					} else {
						this.running = false;
					}
				}
				if (next == null) {
					drained = true;
					break;
				}
				long jobStartedNanos = System.nanoTime();
				try {
					runJob(next);
					BetterPV.LOGGER.info(
						"[PV timing] {} job {} took {}ms (at {}ms)",
						this.name,
						next,
						(System.nanoTime() - jobStartedNanos) / 1_000_000L,
						(System.nanoTime() - sessionStartedNanos) / 1_000_000L
					);
				} catch (RuntimeException exception) {
					// Soft or hard: keep enriching other tabs. Core is already on screen;
					// aborting the session would strand the rest of the viewer empty.
					BetterPV.LOGGER.warn("Enrichment job {} failed for {}", next, this.name, exception);
				}
				synchronized (this.lock) {
					this.done.add(next);
				}
			}
		} finally {
			// A drained loop already cleared running under the lock; clearing it again here
			// could hide a loop that enqueueLate restarted in between.
			if (!drained) {
				synchronized (this.lock) {
					this.running = false;
				}
			}
			ProfileFetcher.clearActiveEnrichment(this);
		}
	}

	private void runJob(Job job) {
		switch (job) {
			case INVENTORY -> runInventory();
			case DUNGEONS -> runDungeons();
			case PETS -> runPets();
			case COLLECTIONS -> runCollections();
			case MINING -> runMining();
			case GARDEN -> runGarden();
			case FORAGING -> runForaging();
			case FISHING -> runFishing();
			case CRIMSON -> runCrimson();
			case RIFT -> runRift();
			case BESTIARY -> runBestiary();
			case EVENTS -> runEvents();
			case AUCTIONS -> runAuctions();
			case AUCTION_HISTORY -> runAuctionHistory();
			case MUSEUM -> runMuseum();
			case NETWORTH -> runNetworth();
		}
	}

	private void publish(ProfileFetcher.LoadedProfile next) {
		if (this.cancelled || next == null || !next.ok()) {
			return;
		}
		this.current = next;
		ProfileFetcher.cacheEnriched(this.uuid, this.name, this.cleanedNameKey, next);
		if (this.onUpdate != null) {
			this.onUpdate.accept(next);
		}
	}

	private void runInventory() {
		InventoryDecoder.withSharedDecode(() -> {
			this.inventoryCategories = InventoryDecoder.parseCategories(this.member, null);
			InventorySnapshot inventories;
			try {
				inventories = InventoryDecoder.parseUi(this.member);
				fishmod.pv.gui.inventories.SkyBlockItemFactory.warmAsync(inventories);
			} catch (RuntimeException exception) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception;
				}
				BetterPV.LOGGER.warn("Inventory decode failed for {}", this.name, exception);
				inventories = InventorySnapshot.empty();
			}
			PlayerStatsSnapshot stats = FarmingFortuneEstimate.attach(
				PlayerStatsCalculator.fromMember(this.member), this.member, this.inventoryCategories
			);
			CrimsonSnapshot crimson = this.current.crimson() == null
				? CrimsonSnapshot.empty().withPlayerStats(stats)
				: this.current.crimson().withPlayerStats(stats);
			publish(merge(
				this.current,
				null, inventories, null, null, null, null, null, null, null, crimson,
				null, null, null, null, null,
				null, null, null, null,
				ArmorStacks.fromMember(this.member),
				stats
			));
		});
	}

	private void runDungeons() {
		ensureInventoryCategories();
		JsonObject election = joinOptional(this.electionFut, "election").orElse(null);
		DungeonSnapshot dungeons = ProfileFetcher.parseDungeonsPublic(
			this.member, null, election, this.inventoryCategories
		);
		publish(merge(
			this.current,
			dungeons, null, null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runPets() {
		PetSnapshot pets;
		try {
			pets = PetSnapshot.fromMember(this.member);
			fishmod.pv.gui.inventories.SkyBlockItemFactory.warmPetsAsync(pets);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Pets decode failed for {}", this.name, exception);
			pets = PetSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, pets, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runCollections() {
		CollectionSnapshot collections;
		try {
			collections = CollectionSnapshot.fromProfile(this.members, this.uuid, this.name);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Collections decode failed for {}", this.name, exception);
			collections = CollectionSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, collections, null, null, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runMining() {
		MiningSnapshot mining;
		try {
			mining = MiningSnapshot.fromMember(this.member);
			mining = mining.withColeWeight(ColeWeight.calculate(mining, this.current.collections(), this.member));
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Mining member parse failed for {}", this.name, exception);
			mining = MiningSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, mining, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runGarden() {
		GardenSnapshot garden;
		try {
			garden = GardenSnapshot.fromMember(this.member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Garden member parse failed for {}", this.name, exception);
			garden = GardenSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, garden, null, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runForaging() {
		ForagingSnapshot foraging;
		try {
			foraging = ForagingSnapshot.fromMember(this.member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Foraging member parse failed for {}", this.name, exception);
			foraging = ForagingSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, foraging, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runFishing() {
		FishingSnapshot fishing;
		try {
			fishing = FishingSnapshot.fromMember(this.member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Fishing member parse failed for {}", this.name, exception);
			fishing = FishingSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, fishing, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runCrimson() {
		CrimsonSnapshot crimson;
		try {
			crimson = CrimsonSnapshot.fromMember(this.member);
			crimson = crimson.withPlayerStats(
				this.current.playerStats() == null ? PlayerStatsSnapshot.empty() : this.current.playerStats()
			);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Crimson member parse failed for {}", this.name, exception);
			crimson = CrimsonSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, null, crimson,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runRift() {
		RiftSnapshot rift;
		try {
			rift = RiftSnapshot.fromMember(this.member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Rift member parse failed for {}", this.name, exception);
			rift = RiftSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, null, null,
			rift, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runBestiary() {
		BestiarySnapshot bestiary;
		try {
			bestiary = BestiarySnapshot.fromMember(this.member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Bestiary member parse failed for {}", this.name, exception);
			bestiary = BestiarySnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, null, null,
			null, bestiary, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runEvents() {
		EventsSnapshot events;
		try {
			events = EventsSnapshot.fromMember(this.member, this.root, this.uuid);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Events member parse failed for {}", this.name, exception);
			events = EventsSnapshot.empty();
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, null, null,
			null, null, events, null, null,
			null, null, null, null, null, null
		));
	}

	// Active listings come from Vyriv in ~200ms; Coflnet history can take seconds, so it
	// joins later as its own job instead of holding the loop.
	private void runAuctions() {
		boolean historyReady = this.soldFut.isDone() && this.bidsFut.isDone();
		publishAuctions(historyReady);
		if (!historyReady) {
			CompletableFuture.allOf(this.soldFut, this.bidsFut)
				.whenComplete((ignored, error) -> enqueueLate(Job.AUCTION_HISTORY));
		}
	}

	private void runAuctionHistory() {
		publishAuctions(true);
	}

	private void publishAuctions(boolean withHistory) {
		AuctionSnapshot auctions = AuctionSnapshot.build(
			this.uuid,
			joinOptional(this.auctionFut, "auctions").orElse(null),
			withHistory ? joinOptional(this.soldFut, "sold auctions").orElse(null) : null,
			withHistory ? joinOptional(this.bidsFut, "auction bids").orElse(null) : null
		);
		auctions = auctions.withStats(AuctionSnapshot.Stats.fromMember(this.member)).withHistoryLoading(!withHistory);
		publish(merge(
			this.current,
			null, null, null, auctions, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null, null, null, null, null
		));
	}

	private void runMuseum() {
		JsonObject museumMember = cachedMuseumMember();
		if (museumMember == null) {
			return;
		}
		publish(merge(
			this.current,
			null, null, null, null, null, null, null, null, null, null,
			null, null, null, museumMember, null,
			null, null, null, null, null, null
		));
	}

	private void runNetworth() {
		long startedNanos = System.nanoTime();
		ensureInventoryCategories();
		long decodedNanos = System.nanoTime();
		JsonObject museumMember = cachedMuseumMember();
		Map<String, List<InventoryDecoder.Stack>> categories = new LinkedHashMap<>(this.inventoryCategories);
		categories.put("museum", InventoryDecoder.parseMuseum(museumMember));
		this.inventoryCategories = categories;
		long museumParsedNanos = System.nanoTime();

		if (!ItemPricer.isReady()) {
			ItemPricer.awaitReady(1_500L);
		}
		long pricesNanos = System.nanoTime();
		boolean pricesReady = ItemPricer.isReady();
		NetworthBreakdown normal = pricesReady
			? NetworthCalculator.calculate(this.member, this.best, museumMember, categories, NetworthMode.NORMAL)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown nonCosmetic = pricesReady
			? NetworthCalculator.calculate(this.member, this.best, museumMember, categories, NetworthMode.NON_COSMETIC)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown unsoulbound = pricesReady
			? NetworthCalculator.calculate(this.member, this.best, museumMember, categories, NetworthMode.UNSOULBOUND)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown unsoulboundNonCosmetic = pricesReady
			? NetworthCalculator.calculate(
				this.member, this.best, museumMember, categories, NetworthMode.UNSOULBOUND_NON_COSMETIC
			)
			: NetworthBreakdown.empty("Loading networth");

		long calcNanos = System.nanoTime();
		BetterPV.LOGGER.info(
			"[PV timing] {} networth split decode={}ms museum={} museumParse={}ms pricesWait={}ms calc={}ms",
			this.name,
			(decodedNanos - startedNanos) / 1_000_000L,
			museumMember == null ? "none" : "cached",
			(museumParsedNanos - decodedNanos) / 1_000_000L,
			(pricesNanos - museumParsedNanos) / 1_000_000L,
			(calcNanos - pricesNanos) / 1_000_000L
		);

		String nwText = !pricesReady
			? "..."
			: normal.total() > 0 ? FormatUtil.shortCoins(normal.total()) : "-";
		ProfileSnapshot snapshot = this.current.snapshot().withNetworthText(nwText);
		ProfileFetcher.LoadedProfile next = merge(
			this.current,
			null, null, null, null, null, null, null, null, null, null,
			null, null, null, museumMember, snapshot,
			normal, nonCosmetic, unsoulbound, unsoulboundNonCosmetic, null, null
		);
		publish(next);
		if (!pricesReady) {
			ProfileFetcher.scheduleNetworthRefreshPublic(
				next, this.member, this.best, museumMember, categories, this.onUpdate
			);
		}
	}

	// Museum is only ever fetched by the Museum tab; /pv and networth just reuse what it cached.
	private JsonObject cachedMuseumMember() {
		MuseumCache.Entry entry = MuseumCache.get(this.uuid, this.profileId);
		return entry != null ? entry.museumMember() : this.current.museumMember();
	}

	private void ensureInventoryCategories() {
		if (this.inventoryCategories == null || this.inventoryCategories.isEmpty()) {
			this.inventoryCategories = InventoryDecoder.parseCategories(this.member, null);
		}
	}

	/** Null fields keep the previous {@code base} value. */
	private static ProfileFetcher.LoadedProfile merge(
		ProfileFetcher.LoadedProfile base,
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
		JsonObject museumMember,
		ProfileSnapshot snapshot,
		NetworthBreakdown nwNormal,
		NetworthBreakdown nwNonCosmetic,
		NetworthBreakdown nwUnsoulbound,
		NetworthBreakdown nwUnsoulboundNonCosmetic,
		net.minecraft.world.item.ItemStack[] armor,
		PlayerStatsSnapshot playerStats
	) {
		return new ProfileFetcher.LoadedProfile(
			snapshot != null ? snapshot : base.snapshot(),
			dungeons != null ? dungeons : base.dungeons(),
			inventories != null ? inventories : base.inventories(),
			pets != null ? pets : base.pets(),
			auctions != null ? auctions : base.auctions(),
			collections != null ? collections : base.collections(),
			garden != null ? garden : base.garden(),
			mining != null ? mining : base.mining(),
			foraging != null ? foraging : base.foraging(),
			fishing != null ? fishing : base.fishing(),
			crimson != null ? crimson : base.crimson(),
			rift != null ? rift : base.rift(),
			bestiary != null ? bestiary : base.bestiary(),
			events != null ? events : base.events(),
			base.misc(),
			museumMember != null ? museumMember : base.museumMember(),
			base.profileId(),
			base.profilesRoot(),
			base.profiles(),
			base.senither(),
			base.lily(),
			nwNormal != null ? nwNormal : base.networthNormal(),
			nwNonCosmetic != null ? nwNonCosmetic : base.networthNonCosmetic(),
			nwUnsoulbound != null ? nwUnsoulbound : base.networthUnsoulbound(),
			nwUnsoulboundNonCosmetic != null ? nwUnsoulboundNonCosmetic : base.networthUnsoulboundNonCosmetic(),
			armor != null ? armor : base.armor(),
			playerStats != null ? playerStats : base.playerStats(),
			null
		);
	}

	/** Join a network future without aborting enrichment on failure. */
	private <T> Optional<T> joinOptional(CompletableFuture<Optional<T>> future, String label) {
		try {
			Optional<T> value = future.join();
			return value == null ? Optional.empty() : value;
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("Enrichment {} future failed for {}", label, this.name, exception);
			return Optional.empty();
		}
	}
}

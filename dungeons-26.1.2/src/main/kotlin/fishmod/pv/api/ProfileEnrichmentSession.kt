/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.data.AuctionSnapshot
import fishmod.pv.data.BestiarySnapshot
import fishmod.pv.data.ColeWeight
import fishmod.pv.data.CollectionSnapshot
import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.EventsSnapshot
import fishmod.pv.data.FarmingFortuneEstimate
import fishmod.pv.data.FishingSnapshot
import fishmod.pv.data.ForagingSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.data.MuseumCache
import fishmod.pv.data.PetSnapshot
import fishmod.pv.data.PlayerStatsCalculator
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.data.SoftDataFailure
import fishmod.pv.gui.ArmorStacks
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.PvTab
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NetworthBreakdown
import fishmod.pv.networth.NetworthCalculator
import fishmod.pv.networth.NetworthMode
import fishmod.pv.price.ItemPricer
import java.util.ArrayDeque
import java.util.EnumSet
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Consumer
import net.minecraft.world.item.ItemStack

/**
 * After Home core is shown, fills remaining tabs one job at a time.
 * Clicking a tab moves that job to the front of the pending queue.
 */
internal class ProfileEnrichmentSession(
    core: ProfileFetcher.LoadedProfile,
    private val cleanedNameKey: String?,
    private val root: JsonObject,
    private val best: JsonObject,
    private val members: JsonObject?,
    private val member: JsonObject,
    private val electionFut: CompletableFuture<Optional<JsonObject>>,
    private val auctionFut: CompletableFuture<Optional<JsonObject>>,
    private val soldFut: CompletableFuture<Optional<JsonArray>>,
    private val bidsFut: CompletableFuture<Optional<JsonArray>>,
    private val onUpdate: Consumer<ProfileFetcher.LoadedProfile>?,
    private val executor: Executor
) {
    enum class Job {
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

    private val lock = Any()
    private val pending = ArrayDeque<Job>()
    private val done: EnumSet<Job> = EnumSet.noneOf(Job::class.java)
    private val started: EnumSet<Job> = EnumSet.noneOf(Job::class.java)

    private val name: String? = core.snapshot?.playerName
    private val uuid: UUID? = core.snapshot?.playerUuid
    private val profileId: String? = core.profileId

    private var current: ProfileFetcher.LoadedProfile = core
    private var inventoryCategories: Map<String, List<InventoryDecoder.Stack>> = mapOf()

    @Volatile
    private var cancelled = false

    /** Guarded by [lock]; false once the loop has drained the queue. */
    private var running = true

    init {
        pending.addAll(DEFAULT_ORDER)
    }

    fun cancel() {
        cancelled = true
    }

    fun start() {
        executor.execute(::runLoop)
    }

    fun prioritize(tab: PvTab?) {
        val job = jobForTab(tab) ?: return
        synchronized(lock) {
            if (done.contains(job) || started.contains(job)) return
            // Dependencies go in front of the requested job, so add the job first.
            pending.remove(job)
            pending.addFirst(job)
            if (job == Job.MINING && !done.contains(Job.COLLECTIONS) && !started.contains(Job.COLLECTIONS)) {
                pending.remove(Job.COLLECTIONS)
                pending.addFirst(Job.COLLECTIONS)
            }
            // Inventory decode is required before several tabs; pull it forward too.
            if (job != Job.INVENTORY && !done.contains(Job.INVENTORY) && !started.contains(Job.INVENTORY)) {
                pending.remove(Job.INVENTORY)
                pending.addFirst(Job.INVENTORY)
            }
        }
    }

    fun matches(uuid: UUID?, profileId: String?): Boolean =
        this.uuid != null && this.uuid == uuid && this.profileId != null && this.profileId == profileId

    /** Re-runs networth after museum data arrives. False when the loop has already finished. */
    fun requeueNetworth(): Boolean {
        synchronized(lock) {
            if (!running || cancelled) return false
            done.remove(Job.NETWORTH)
            started.remove(Job.NETWORTH)
            pending.remove(Job.NETWORTH)
            pending.addFirst(Job.NETWORTH)
            return true
        }
    }

    /** Queues a job that became runnable later, restarting the loop if it already drained. */
    private fun enqueueLate(job: Job) {
        val restart: Boolean
        synchronized(lock) {
            if (cancelled) return
            pending.remove(job)
            pending.addLast(job)
            restart = !running
            running = true
        }
        if (restart) executor.execute(::runLoop)
    }

    private fun runLoop() {
        val sessionStartedNanos = System.nanoTime()
        var drained = false
        try {
            while (!cancelled) {
                val next: Job? = synchronized(lock) {
                    pending.pollFirst().also { if (it != null) started.add(it) else running = false }
                }
                if (next == null) {
                    drained = true
                    break
                }
                val jobStartedNanos = System.nanoTime()
                try {
                    runJob(next)
                    ProfileViewer.LOGGER.info(
                        "[PV timing] {} job {} took {}ms (at {}ms)",
                        name,
                        next,
                        (System.nanoTime() - jobStartedNanos) / 1_000_000L,
                        (System.nanoTime() - sessionStartedNanos) / 1_000_000L
                    )
                } catch (exception: RuntimeException) {
                    // Soft or hard: keep enriching other tabs. Core is already on screen;
                    // aborting the session would strand the rest of the viewer empty.
                    ProfileViewer.LOGGER.warn("Enrichment job {} failed for {}", next, name, exception)
                }
                synchronized(lock) { done.add(next) }
            }
        } finally {
            // A drained loop already cleared running under the lock; clearing it again here
            // could hide a loop that enqueueLate restarted in between.
            if (!drained) {
                synchronized(lock) { running = false }
            }
            ProfileFetcher.clearActiveEnrichment(this)
        }
    }

    private fun runJob(job: Job) {
        when (job) {
            Job.INVENTORY -> runInventory()
            Job.DUNGEONS -> runDungeons()
            Job.PETS -> runPets()
            Job.COLLECTIONS -> runCollections()
            Job.MINING -> runMining()
            Job.GARDEN -> runGarden()
            Job.FORAGING -> runForaging()
            Job.FISHING -> runFishing()
            Job.CRIMSON -> runCrimson()
            Job.RIFT -> runRift()
            Job.BESTIARY -> runBestiary()
            Job.EVENTS -> runEvents()
            Job.AUCTIONS -> runAuctions()
            Job.AUCTION_HISTORY -> runAuctionHistory()
            Job.MUSEUM -> runMuseum()
            Job.NETWORTH -> runNetworth()
        }
    }

    private fun publish(next: ProfileFetcher.LoadedProfile?) {
        if (cancelled || next == null || !next.ok()) return
        current = next
        ProfileFetcher.cacheEnriched(uuid, name, cleanedNameKey, next)
        onUpdate?.accept(next)
    }

    // Logs and falls back to the empty value when a soft data failure hits one tab.
    private inline fun <T> softParse(what: String, empty: () -> T, block: () -> T): T = try {
        block()
    } catch (exception: RuntimeException) {
        if (!SoftDataFailure.isSoft(exception)) throw exception
        ProfileViewer.LOGGER.warn("$what failed for {}", name, exception)
        empty()
    }

    private fun runInventory() {
        InventoryDecoder.withSharedDecode(Runnable {
            inventoryCategories = InventoryDecoder.parseCategories(member, null)
            val inventories = softParse("Inventory decode", { InventorySnapshot.empty() }) {
                InventoryDecoder.parseUi(member).also { SkyBlockItemFactory.warmAsync(it) }
            }
            val stats = FarmingFortuneEstimate.attach(PlayerStatsCalculator.fromMember(member), member, inventoryCategories)
            val crimson = (current.crimson ?: CrimsonSnapshot.empty()).withPlayerStats(stats)
            publish(
                merge(
                    current,
                    inventories = inventories,
                    crimson = crimson,
                    armor = ArmorStacks.fromMember(member),
                    playerStats = stats
                )
            )
        })
    }

    private fun runDungeons() {
        ensureInventoryCategories()
        val election = joinOptional(electionFut, "election").orElse(null)
        val dungeons = ProfileFetcher.parseDungeonsPublic(member, null, election, inventoryCategories)
        publish(merge(current, dungeons = dungeons))
    }

    private fun runPets() {
        val pets = softParse("Pets decode", { PetSnapshot.empty() }) {
            PetSnapshot.fromMember(member).also { SkyBlockItemFactory.warmPetsAsync(it) }
        }
        publish(merge(current, pets = pets))
    }

    private fun runCollections() {
        val collections = softParse("Collections decode", { CollectionSnapshot.empty() }) {
            CollectionSnapshot.fromProfile(members, uuid, name)
        }
        publish(merge(current, collections = collections))
    }

    private fun runMining() {
        val mining = softParse("Mining member parse", { MiningSnapshot.empty() }) {
            val parsed = MiningSnapshot.fromMember(member)
            parsed.withColeWeight(ColeWeight.calculate(parsed, current.collections, member))
        }
        publish(merge(current, mining = mining))
    }

    private fun runGarden() {
        val garden = softParse("Garden member parse", { GardenSnapshot.empty() }) { GardenSnapshot.fromMember(member) }
        publish(merge(current, garden = garden))
    }

    private fun runForaging() {
        val foraging = softParse("Foraging member parse", { ForagingSnapshot.empty() }) { ForagingSnapshot.fromMember(member) }
        publish(merge(current, foraging = foraging))
    }

    private fun runFishing() {
        val fishing = softParse("Fishing member parse", { FishingSnapshot.empty() }) { FishingSnapshot.fromMember(member) }
        publish(merge(current, fishing = fishing))
    }

    private fun runCrimson() {
        val crimson = softParse("Crimson member parse", { CrimsonSnapshot.empty() }) {
            CrimsonSnapshot.fromMember(member).withPlayerStats(current.playerStats ?: PlayerStatsSnapshot.empty())
        }
        publish(merge(current, crimson = crimson))
    }

    private fun runRift() {
        val rift = softParse("Rift member parse", { RiftSnapshot.empty() }) { RiftSnapshot.fromMember(member) }
        publish(merge(current, rift = rift))
    }

    private fun runBestiary() {
        val bestiary = softParse("Bestiary member parse", { BestiarySnapshot.empty() }) { BestiarySnapshot.fromMember(member) }
        publish(merge(current, bestiary = bestiary))
    }

    private fun runEvents() {
        val events = softParse("Events member parse", { EventsSnapshot.empty() }) { EventsSnapshot.fromMember(member, root, uuid) }
        publish(merge(current, events = events))
    }

    // Active listings come from Vyriv in ~200ms; Coflnet history can take seconds, so it
    // joins later as its own job instead of holding the loop.
    private fun runAuctions() {
        val historyReady = soldFut.isDone && bidsFut.isDone
        publishAuctions(historyReady)
        if (!historyReady) {
            CompletableFuture.allOf(soldFut, bidsFut).whenComplete { _, _ -> enqueueLate(Job.AUCTION_HISTORY) }
        }
    }

    private fun runAuctionHistory() {
        publishAuctions(true)
    }

    private fun publishAuctions(withHistory: Boolean) {
        val auctions = AuctionSnapshot.build(
            uuid,
            joinOptional(auctionFut, "auctions").orElse(null),
            if (withHistory) joinOptional(soldFut, "sold auctions").orElse(null) else null,
            if (withHistory) joinOptional(bidsFut, "auction bids").orElse(null) else null
        ).withStats(AuctionSnapshot.Stats.fromMember(member)).withHistoryLoading(!withHistory)
        publish(merge(current, auctions = auctions))
    }

    private fun runMuseum() {
        val museumMember = cachedMuseumMember() ?: return
        publish(merge(current, museumMember = museumMember))
    }

    private fun runNetworth() {
        val startedNanos = System.nanoTime()
        ensureInventoryCategories()
        val decodedNanos = System.nanoTime()
        val museumMember = cachedMuseumMember()
        val categories = LinkedHashMap(inventoryCategories)
        categories["museum"] = InventoryDecoder.parseMuseum(museumMember)
        inventoryCategories = categories
        val museumParsedNanos = System.nanoTime()

        if (!ItemPricer.isReady()) ItemPricer.awaitReady(1_500L)
        val pricesNanos = System.nanoTime()
        val pricesReady = ItemPricer.isReady()
        fun networth(mode: NetworthMode): NetworthBreakdown =
            if (pricesReady) NetworthCalculator.calculate(member, best, museumMember, categories, mode)
            else NetworthBreakdown.empty("Loading networth")
        val normal = networth(NetworthMode.NORMAL)
        val nonCosmetic = networth(NetworthMode.NON_COSMETIC)
        val unsoulbound = networth(NetworthMode.UNSOULBOUND)
        val unsoulboundNonCosmetic = networth(NetworthMode.UNSOULBOUND_NON_COSMETIC)

        val calcNanos = System.nanoTime()
        ProfileViewer.LOGGER.info(
            "[PV timing] {} networth split decode={}ms museum={} museumParse={}ms pricesWait={}ms calc={}ms",
            name,
            (decodedNanos - startedNanos) / 1_000_000L,
            if (museumMember == null) "none" else "cached",
            (museumParsedNanos - decodedNanos) / 1_000_000L,
            (pricesNanos - museumParsedNanos) / 1_000_000L,
            (calcNanos - pricesNanos) / 1_000_000L
        )

        val nwText = when {
            !pricesReady -> "..."
            normal.total > 0 -> FormatUtil.shortCoins(normal.total)
            else -> "-"
        }
        val next = merge(
            current,
            museumMember = museumMember,
            snapshot = current.snapshot?.withNetworthText(nwText),
            nwNormal = normal,
            nwNonCosmetic = nonCosmetic,
            nwUnsoulbound = unsoulbound,
            nwUnsoulboundNonCosmetic = unsoulboundNonCosmetic
        )
        publish(next)
        if (!pricesReady) {
            ProfileFetcher.scheduleNetworthRefreshPublic(next, member, best, museumMember, categories, onUpdate)
        }
    }

    // Museum is only ever fetched by the Museum tab; /pv and networth just reuse what it cached.
    private fun cachedMuseumMember(): JsonObject? {
        val entry = MuseumCache.get(uuid, profileId)
        return if (entry != null) entry.museumMember else current.museumMember
    }

    private fun ensureInventoryCategories() {
        if (inventoryCategories.isEmpty()) {
            inventoryCategories = InventoryDecoder.parseCategories(member, null)
        }
    }

    /** Join a network future without aborting enrichment on failure. */
    private fun <T : Any> joinOptional(future: CompletableFuture<Optional<T>>, label: String): Optional<T> = try {
        future.join() ?: Optional.empty<T>()
    } catch (exception: RuntimeException) {
        ProfileViewer.LOGGER.warn("Enrichment {} future failed for {}", label, name, exception)
        Optional.empty()
    }

    companion object {
        private val DEFAULT_ORDER = listOf(
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
        )

        @JvmStatic
        fun jobForTab(tab: PvTab?): Job? = when (tab) {
            null -> null
            PvTab.HOME -> Job.NETWORTH
            PvTab.DUNGEONS -> Job.DUNGEONS
            PvTab.INVENTORIES -> Job.INVENTORY
            PvTab.PETS -> Job.PETS
            PvTab.COLLECTIONS -> Job.COLLECTIONS
            PvTab.GARDEN -> Job.GARDEN
            PvTab.MINING -> Job.MINING
            PvTab.FORAGING -> Job.FORAGING
            PvTab.FISHING -> Job.FISHING
            PvTab.CRIMSON -> Job.CRIMSON
            PvTab.RIFT -> Job.RIFT
            PvTab.MUSEUM -> Job.MUSEUM
            PvTab.BESTIARY -> Job.BESTIARY
            PvTab.EVENTS -> Job.EVENTS
        }

        /** Null fields keep the previous `base` value. */
        private fun merge(
            base: ProfileFetcher.LoadedProfile,
            dungeons: DungeonSnapshot? = null,
            inventories: InventorySnapshot? = null,
            pets: PetSnapshot? = null,
            auctions: AuctionSnapshot? = null,
            collections: CollectionSnapshot? = null,
            garden: GardenSnapshot? = null,
            mining: MiningSnapshot? = null,
            foraging: ForagingSnapshot? = null,
            fishing: FishingSnapshot? = null,
            crimson: CrimsonSnapshot? = null,
            rift: RiftSnapshot? = null,
            bestiary: BestiarySnapshot? = null,
            events: EventsSnapshot? = null,
            museumMember: JsonObject? = null,
            snapshot: ProfileSnapshot? = null,
            nwNormal: NetworthBreakdown? = null,
            nwNonCosmetic: NetworthBreakdown? = null,
            nwUnsoulbound: NetworthBreakdown? = null,
            nwUnsoulboundNonCosmetic: NetworthBreakdown? = null,
            armor: Array<ItemStack>? = null,
            playerStats: PlayerStatsSnapshot? = null
        ): ProfileFetcher.LoadedProfile = base.copy(
            snapshot = snapshot ?: base.snapshot,
            dungeons = dungeons ?: base.dungeons,
            inventories = inventories ?: base.inventories,
            pets = pets ?: base.pets,
            auctions = auctions ?: base.auctions,
            collections = collections ?: base.collections,
            garden = garden ?: base.garden,
            mining = mining ?: base.mining,
            foraging = foraging ?: base.foraging,
            fishing = fishing ?: base.fishing,
            crimson = crimson ?: base.crimson,
            rift = rift ?: base.rift,
            bestiary = bestiary ?: base.bestiary,
            events = events ?: base.events,
            museumMember = museumMember ?: base.museumMember,
            networthNormal = nwNormal ?: base.networthNormal,
            networthNonCosmetic = nwNonCosmetic ?: base.networthNonCosmetic,
            networthUnsoulbound = nwUnsoulbound ?: base.networthUnsoulbound,
            networthUnsoulboundNonCosmetic = nwUnsoulboundNonCosmetic ?: base.networthUnsoulboundNonCosmetic,
            armor = armor ?: base.armor,
            playerStats = playerStats ?: base.playerStats,
            error = null
        )
    }
}

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
import com.mojang.authlib.GameProfile
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
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.Leveling
import fishmod.pv.data.MiningHotmData
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.data.MiscStatsSnapshot
import fishmod.pv.data.PetSnapshot
import fishmod.pv.data.PlayerStatsCalculator
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.data.RepoData
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.data.SoftDataFailure
import fishmod.pv.dungeons.DungeonXpData
import fishmod.pv.gui.ArmorStacks
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.PvTab
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NetworthBreakdown
import fishmod.pv.networth.NetworthCalculator
import fishmod.pv.networth.NetworthMode
import fishmod.pv.price.ItemPricer
import fishmod.pv.slayer.SlayerMayorMods
import fishmod.pv.weight.WeightBreakdown
import fishmod.pv.weight.WeightCalculator
import fishmod.pv.weight.WeightSystem
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object ProfileFetcher {
    /** Match worker profiles cache TTL (5 minutes). */
    private const val CACHE_TTL_MS = 5L * 60L * 1000L
    private val CACHE = ConcurrentHashMap<String, CacheEntry>()
    private val MINECRAFT_NAME = Regex("[A-Za-z0-9_]{1,16}")
    private val CORRELATION_ID = Regex("[A-Za-z0-9._:-]{1,80}")

    /** Post-core enrichment / profile-switch parsing (bounded; avoids parse-pool deadlock). */
    private val ENRICH_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(3, PvExecutors.daemon("FishPV-Enrich"))
    private val ACTIVE_ENRICH_LOCK = Any()

    @Volatile
    private var activeEnrichment: ProfileEnrichmentSession? = null

    @Volatile
    private var requestedTab: PvTab? = null

    // joinedMs is coop_invitation.confirmed_timestamp, 0 for the profile creator.
    @ConsistentCopyVisibility
    data class CoopMemberRef private constructor(
        @get:JvmName("uuid") val uuid: String,
        @get:JvmName("fallbackName") val fallbackName: String,
        @get:JvmName("joinedMs") val joinedMs: Long
    ) {
        companion object {
            @JvmStatic
            operator fun invoke(uuid: String?, fallbackName: String?, joinedMs: Long): CoopMemberRef {
                val id = uuid?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
                val fallback = if (fallbackName.isNullOrBlank()) ProfileCoopIndex.shortCoopUuid(id) else fallbackName
                return CoopMemberRef(id, fallback, maxOf(0L, joinedMs))
            }
        }
    }

    @ConsistentCopyVisibility
    data class CoopSummary private constructor(
        @get:JvmName("currentOthers") val currentOthers: Int,
        @get:JvmName("formerCount") val formerCount: Int,
        @get:JvmName("currentMembers") val currentMembers: List<CoopMemberRef>,
        @get:JvmName("formerMembers") val formerMembers: List<CoopMemberRef>
    ) {
        fun soloProfile(): Boolean = currentOthers == 0 && formerCount == 0

        companion object {
            private val SOLO = CoopSummary(0, 0, listOf(), listOf())

            @JvmStatic
            operator fun invoke(
                currentOthers: Int,
                formerCount: Int,
                currentMembers: List<CoopMemberRef>?,
                formerMembers: List<CoopMemberRef>?
            ): CoopSummary = CoopSummary(
                maxOf(0, currentOthers),
                maxOf(0, formerCount),
                currentMembers?.let { java.util.List.copyOf(it) } ?: listOf(),
                formerMembers?.let { java.util.List.copyOf(it) } ?: listOf()
            )

            @JvmStatic
            fun solo(): CoopSummary = SOLO
        }
    }

    @ConsistentCopyVisibility
    data class ProfileChoice private constructor(
        @get:JvmName("cuteName") val cuteName: String,
        @get:JvmName("profileId") val profileId: String,
        @get:JvmName("selected") val selected: Boolean,
        @get:JvmName("gameMode") val gameMode: String,
        @get:JvmName("createdAtMs") val createdAtMs: Long,
        @get:JvmName("coop") val coop: CoopSummary
    ) {
        fun gameModeLabel(): String {
            if (gameMode.isBlank()) return ""
            return when (val lower = gameMode.lowercase(Locale.ROOT)) {
                "ironman" -> "Ironman"
                "stranded" -> "Stranded"
                "bingo" -> "Bingo"
                else -> lower.replaceFirstChar { it.uppercaseChar() }
            }
        }

        companion object {
            @JvmStatic
            @JvmOverloads
            operator fun invoke(
                cuteName: String?,
                profileId: String?,
                selected: Boolean,
                gameMode: String?,
                createdAtMs: Long,
                coop: CoopSummary? = CoopSummary.solo()
            ): ProfileChoice = ProfileChoice(
                if (cuteName.isNullOrBlank()) "Unknown" else cuteName,
                profileId ?: "",
                selected,
                gameMode?.trim() ?: "",
                maxOf(0L, createdAtMs),
                coop ?: CoopSummary.solo()
            )
        }
    }

    // Fields nullable like the Java record; profiles must be immutable (listProfileChoices returns one).
    @JvmRecord
    data class LoadedProfile(
        val snapshot: ProfileSnapshot?,
        val dungeons: DungeonSnapshot?,
        val inventories: InventorySnapshot?,
        val pets: PetSnapshot?,
        val auctions: AuctionSnapshot?,
        val collections: CollectionSnapshot?,
        val garden: GardenSnapshot?,
        val mining: MiningSnapshot?,
        val foraging: ForagingSnapshot?,
        val fishing: FishingSnapshot?,
        val crimson: CrimsonSnapshot?,
        val rift: RiftSnapshot?,
        val bestiary: BestiarySnapshot?,
        val events: EventsSnapshot?,
        val misc: MiscStatsSnapshot?,
        val museumMember: JsonObject?,
        val profileId: String?,
        val profilesRoot: JsonObject?,
        val profiles: List<ProfileChoice>,
        val senither: WeightBreakdown?,
        val lily: WeightBreakdown?,
        val networthNormal: NetworthBreakdown?,
        val networthNonCosmetic: NetworthBreakdown?,
        val networthUnsoulbound: NetworthBreakdown?,
        val networthUnsoulboundNonCosmetic: NetworthBreakdown?,
        val armor: Array<ItemStack>?,
        val playerStats: PlayerStatsSnapshot?,
        val error: String?
    ) {
        fun ok(): Boolean = error.isNullOrBlank()

        fun networth(mode: NetworthMode?): NetworthBreakdown? = when (mode) {
            null, NetworthMode.NORMAL -> networthNormal
            NetworthMode.NON_COSMETIC -> networthNonCosmetic
            NetworthMode.UNSOULBOUND -> networthUnsoulbound
            NetworthMode.UNSOULBOUND_NON_COSMETIC -> networthUnsoulboundNonCosmetic
        }
    }

    @JvmStatic
    fun fetch(playerName: String?): CompletableFuture<LoadedProfile> =
        fetch(playerName, null, null, UUID.randomUUID().toString(), System.nanoTime())

    /**
     * Loads the home/core profile first (enough for Home + dismiss loading egg), then publishes
     * a fully enriched profile through `onUpdate`. Fatal failures return `!ok()` and
     * never become a successful core load.
     */
    @JvmStatic
    fun fetch(playerName: String?, onUpdate: Consumer<LoadedProfile>?): CompletableFuture<LoadedProfile> =
        fetch(playerName, null, onUpdate, UUID.randomUUID().toString(), System.nanoTime())

    @JvmStatic
    fun fetch(
        playerName: String?,
        onUpdate: Consumer<LoadedProfile>?,
        correlationId: String?,
        requestStartedNanos: Long
    ): CompletableFuture<LoadedProfile> = fetch(playerName, null, onUpdate, correlationId, requestStartedNanos)

    /**
     * `preferredProfileId` keeps a co-op member switch on the same SkyBlock profile instead of
     * the member's own selected one. Falls back to their selected profile if they aren't in it.
     */
    @JvmStatic
    fun fetch(
        playerName: String?,
        preferredProfileId: String?,
        onUpdate: Consumer<LoadedProfile>?,
        correlationId: String?,
        requestStartedNanos: Long
    ): CompletableFuture<LoadedProfile> {
        RepoData.ensureLoaded()
        DungeonXpData.ensureLoaded()
        GardenData.ensureLoaded()
        MiningHotmData.ensureLoaded()
        val startedNanos = if (requestStartedNanos > 0L) requestStartedNanos else System.nanoTime()
        val correlation = normalizeCorrelationId(correlationId)
        if (!HypixelApiClient.canFetch()) {
            return CompletableFuture.completedFuture(failed(playerName, "API unavailable"))
        }
        val cleaned = playerName?.trim() ?: ""
        val preferred = if (preferredProfileId.isNullOrBlank()) null else preferredProfileId
        getCached(nameKey(cleaned))?.let { cached ->
            ProfileViewer.LOGGER.info("Profile cache hit for {}", cleaned)
            logStage(cleaned, "core_ready", startedNanos, correlation)
            return cachedOnProfile(cached, preferred, onUpdate)
        }
        val authFut = FishProxyAuth.bearerTokenAsync()
        val local = localUuid(cleaned)
        val uuidFut: CompletableFuture<Optional<HypixelApiClient.UuidName>> =
            if (local != null) CompletableFuture.completedFuture(Optional.of(local)) else HypixelApiClient.resolveUuid(cleaned)
        return uuidFut.thenCompose { uuidOpt ->
            logStage(cleaned, if (local != null) "uuid_local" else "uuid", startedNanos, correlation)
            if (uuidOpt.isEmpty) return@thenCompose CompletableFuture.completedFuture(failed(cleaned, "Player not found"))
            val id = uuidOpt.get()
            val byUuid = getCached(uuidKey(id.uuid))
            if (byUuid != null) {
                ProfileViewer.LOGGER.info("Profile cache hit for {} ({})", id.name, id.uuid)
                putCache(nameKey(cleaned), byUuid)
                putCache(nameKey(id.name), byUuid)
                logStage(cleaned, "core_ready", startedNanos, correlation)
                return@thenCompose cachedOnProfile(byUuid, preferred, onUpdate)
            }

            authFut
                .thenCompose {
                    logStage(cleaned, "auth", startedNanos, correlation)
                    HypixelApiClient.skyblockProfiles(id.uuid, correlation)
                }
                .thenCompose { profilesOpt ->
                    logStage(cleaned, "profiles", startedNanos, correlation)
                    if (profilesOpt.isEmpty) {
                        val authMessage = FishProxyAuth.userFacingFailure().orElse("Profiles request failed")
                        FishProxyAuth.notifyPlayerIfNeeded()
                        return@thenCompose CompletableFuture.completedFuture(failed(id.name, authMessage))
                    }
                    val root = profilesOpt.get()
                    logProfileShape(cleaned, root)
                    val electionFut = HypixelApiClient.skyblockElection()
                    val auctionFuts = playerAuctionFutures(id.uuid)

                    val coreFuture = CompletableFuture.supplyAsync(
                        { ProfileHomeParser.parseHomeCore(id.name, id.uuid, root, preferred) },
                        HypixelApiClient.parseExecutor()
                    )

                    coreFuture.thenAccept { core ->
                        logStage(cleaned, "core_ready", startedNanos, correlation)
                        if (core != null && core.ok()) {
                            // Cache core immediately so a quick second /pv is warm for first paint.
                            putCache(uuidKey(id.uuid), core)
                            putCache(nameKey(id.name), core)
                            putCache(nameKey(cleaned), core)
                            startProgressiveEnrichment(
                                core, nameKey(cleaned), root, electionFut,
                                auctionFuts.auction, auctionFuts.sold, auctionFuts.bids, onUpdate
                            )
                        }
                    }
                    coreFuture
                }
        }
    }

    private fun cachedOnProfile(
        cached: LoadedProfile,
        preferredProfileId: String?,
        onUpdate: Consumer<LoadedProfile>?
    ): CompletableFuture<LoadedProfile> {
        val root = cached.profilesRoot
        val snapshot = cached.snapshot
        val uuid = snapshot?.playerUuid()
        if (preferredProfileId == null || preferredProfileId == cached.profileId || root == null || snapshot == null || uuid == null) {
            return CompletableFuture.completedFuture(cached)
        }
        return switchToProfile(snapshot.playerName(), uuid, root, preferredProfileId, onUpdate)
    }

    // Skips the Mojang lookup when the client already knows the player. Tab entries are
    // only trusted for real accounts (version 4 UUIDs); Hypixel NPCs and fake tab rows
    // use version 2.
    private fun localUuid(name: String): HypixelApiClient.UuidName? {
        val client: Minecraft? = Minecraft.getInstance()
        if (client == null || !client.isSameThread() || !MINECRAFT_NAME.matches(name)) return null
        var profile: GameProfile? = null
        val player = client.player
        val connection = client.connection
        if (player != null && name.equals(player.gameProfile.name(), ignoreCase = true)) {
            profile = player.gameProfile
        } else if (connection != null) {
            val info = connection.getPlayerInfoIgnoreCase(name)
            if (info != null && info.profile.id() != null && info.profile.id().version() == 4) {
                profile = info.profile
            }
        }
        if (profile == null || profile.id() == null || !name.equals(profile.name(), ignoreCase = true)) return null
        return HypixelApiClient.UuidName(profile.id(), profile.name())
    }

    private fun logProfileShape(name: String, root: JsonObject) {
        val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else JsonArray()
        var totalMembers = 0
        var selectedMembers = 0
        val perProfile = StringBuilder()
        for (element in profiles) {
            if (!element.isJsonObject) continue
            val profile = element.asJsonObject
            val members = if (profile.has("members") && profile.get("members").isJsonObject) profile.getAsJsonObject("members").size() else 0
            totalMembers += members
            val selected = profile.has("selected") && profile.get("selected").isJsonPrimitive && profile.get("selected").asBoolean
            if (selected) selectedMembers = members
            if (perProfile.isNotEmpty()) perProfile.append(',')
            perProfile.append(members).append(if (selected) "*" else "")
        }
        ProfileViewer.LOGGER.info(
            "[PV timing] {} shape profiles={} members={} selectedMembers={} perProfile=[{}]",
            name, profiles.size(), totalMembers, selectedMembers, perProfile
        )
    }

    private fun logStage(name: String, stage: String, startedNanos: Long, correlationId: String) {
        ProfileViewer.LOGGER.info(
            "[PV timing] event={} correlation_id={} player={} elapsed_ms={}",
            stage, correlationId, name, (System.nanoTime() - startedNanos) / 1_000_000L
        )
    }

    private fun normalizeCorrelationId(correlationId: String?): String =
        if (correlationId != null && CORRELATION_ID.matches(correlationId)) correlationId else UUID.randomUUID().toString()

    /**
     * Prefer loading the clicked tab next while background enrichment is still running.
     */
    @JvmStatic
    fun prioritizeTab(tab: PvTab?) {
        // The screen asks before its session exists, so remember it for the next session too.
        requestedTab = tab
        activeEnrichment?.prioritize(tab)
    }

    internal fun clearActiveEnrichment(session: ProfileEnrichmentSession) {
        synchronized(ACTIVE_ENRICH_LOCK) {
            if (activeEnrichment === session) activeEnrichment = null
        }
    }

    internal fun cacheEnriched(uuid: UUID?, name: String?, cleanedNameKey: String?, loaded: LoadedProfile?) {
        if (loaded == null || !loaded.ok()) return
        putCache(uuidKey(uuid), loaded)
        putCache(nameKey(name), loaded)
        if (!cleanedNameKey.isNullOrBlank()) putCache(cleanedNameKey, loaded)
    }

    internal fun parseDungeonsPublic(
        member: JsonObject,
        museumMember: JsonObject?,
        electionRoot: JsonObject?,
        inventoryCategories: Map<String, List<InventoryDecoder.Stack>>?
    ): DungeonSnapshot = ProfileDungeonParser.parseDungeons(member, museumMember, electionRoot, inventoryCategories)

    internal fun findMuseumMemberPublic(museumRoot: JsonObject?, profileId: String?, undashed: String): JsonObject? =
        ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed)

    internal fun scheduleNetworthRefreshPublic(
        base: LoadedProfile,
        member: JsonObject,
        profile: JsonObject?,
        museumMember: JsonObject?,
        inventoryCategories: Map<String, List<InventoryDecoder.Stack>>,
        onUpdate: Consumer<LoadedProfile>?
    ) = scheduleNetworthRefresh(base, member, profile, museumMember, inventoryCategories, onUpdate)

    /**
     * Called after the Museum tab fetched museum data, so networth can include it
     * without /pv ever requesting museum itself.
     */
    @JvmStatic
    fun museumLoaded(uuid: UUID?, profileId: String?, museumMember: JsonObject?) {
        if (uuid == null || profileId == null || museumMember == null) return
        val session = activeEnrichment
        if (session != null && session.matches(uuid, profileId) && session.requeueNetworth()) return
        val base = getCached(uuidKey(uuid))
        if (base == null || profileId != base.profileId) return
        val root = base.profilesRoot ?: return
        CompletableFuture.runAsync({
            val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else null
            val profile = ProfileCoopIndex.pickProfile(profiles, profileId) ?: return@runAsync
            val members = if (profile.has("members") && profile.get("members").isJsonObject) profile.getAsJsonObject("members") else null
            val member = ProfileMuseumLookup.findMember(members, HypixelApiClient.undashed(uuid)) ?: return@runAsync
            val categories = InventoryDecoder.withSharedDecode(Supplier { InventoryDecoder.parseCategories(member, museumMember) })
            scheduleNetworthRefresh(base, member, profile, museumMember, categories, null)
        }, ENRICH_EXECUTOR)
    }

    private fun startProgressiveEnrichment(
        core: LoadedProfile,
        cleanedNameKey: String,
        root: JsonObject,
        electionFut: CompletableFuture<Optional<JsonObject>>,
        auctionFut: CompletableFuture<Optional<JsonObject>>,
        soldFut: CompletableFuture<Optional<JsonArray>>,
        bidsFut: CompletableFuture<Optional<JsonArray>>,
        onUpdate: Consumer<LoadedProfile>?
    ) {
        val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else null
        val best = ProfileCoopIndex.pickProfile(profiles, core.profileId) ?: return
        val playerUuid = core.snapshot?.playerUuid() ?: return
        val undashed = HypixelApiClient.undashed(playerUuid)
        val members = if (best.has("members") && best.get("members").isJsonObject) best.getAsJsonObject("members") else null
        val member = ProfileMuseumLookup.findMember(members, undashed) ?: return
        val session = ProfileEnrichmentSession(
            core, cleanedNameKey, root, best, members, member,
            electionFut, auctionFut, soldFut, bidsFut, onUpdate, ENRICH_EXECUTOR
        )
        synchronized(ACTIVE_ENRICH_LOCK) {
            activeEnrichment?.cancel()
            activeEnrichment = session
        }
        requestedTab?.let { session.prioritize(it) }
        session.start()
    }

    /**
     * Async re-parse for profile switching. Publishes core first when `onUpdate` is set,
     * then enrichment. Caller must ignore stale results via its own generation counter.
     */
    @JvmStatic
    fun switchToProfile(
        name: String,
        uuid: UUID,
        root: JsonObject,
        profileId: String?,
        onUpdate: Consumer<LoadedProfile>?
    ): CompletableFuture<LoadedProfile> {
        val coreFuture = CompletableFuture.supplyAsync(
            { ProfileHomeParser.parseHomeCore(name, uuid, root, profileId) },
            HypixelApiClient.parseExecutor()
        )
        coreFuture.thenAccept { core ->
            if (core == null || !core.ok()) return@thenAccept
            val electionFut = HypixelApiClient.skyblockElection()
            val auctionFuts = playerAuctionFutures(uuid)
            startProgressiveEnrichment(
                core, nameKey(name), root, electionFut,
                auctionFuts.auction, auctionFuts.sold, auctionFuts.bids, onUpdate
            )
        }
        return coreFuture
    }

    // Hypixel auctions and Cofl history are per player, not per profile, so profile
    // switches reuse the last player's requests instead of fetching them again.
    private class PlayerAuctionFutures(
        val uuid: UUID,
        val createdAtMillis: Long,
        val auction: CompletableFuture<Optional<JsonObject>>,
        val sold: CompletableFuture<Optional<JsonArray>>,
        val bids: CompletableFuture<Optional<JsonArray>>
    )

    @Volatile
    private var lastAuctionFutures: PlayerAuctionFutures? = null

    private fun playerAuctionFutures(uuid: UUID): PlayerAuctionFutures {
        val now = System.currentTimeMillis()
        val last = lastAuctionFutures
        if (last != null &&
            last.uuid == uuid &&
            now - last.createdAtMillis < CACHE_TTL_MS &&
            !HypixelApiClient.failed(last.auction) &&
            !HypixelApiClient.failed(last.sold) &&
            !HypixelApiClient.failed(last.bids)
        ) {
            return last
        }
        val next = PlayerAuctionFutures(
            uuid,
            now,
            // The Auctions tab is gone and the proxy has no auction route: no calls, no failures.
            CompletableFuture.completedFuture(Optional.empty()),
            CompletableFuture.completedFuture(Optional.empty()),
            CompletableFuture.completedFuture(Optional.empty())
        )
        lastAuctionFutures = next
        return next
    }

    private fun nameKey(name: String?): String = "n:" + (name?.trim()?.lowercase(Locale.ROOT) ?: "")

    private fun uuidKey(uuid: UUID?): String = "u:" + HypixelApiClient.undashed(uuid)

    private fun getCached(key: String?): LoadedProfile? {
        if (key.isNullOrBlank()) return null
        val entry = CACHE[key] ?: return null
        if (!entry.fresh()) {
            CACHE.remove(key, entry)
            return null
        }
        return entry.profile
    }

    private fun putCache(key: String?, profile: LoadedProfile?) {
        if (key.isNullOrBlank() || profile == null || !profile.ok()) return
        // drop expired profiles so viewed players don't pile up for the whole session
        CACHE.values.removeIf { !it.fresh() }
        CACHE[key] = CacheEntry(profile, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private class CacheEntry(val profile: LoadedProfile, val expiresAtMs: Long) {
        fun fresh(): Boolean = System.currentTimeMillis() < expiresAtMs
    }

    @JvmStatic
    fun failed(name: String?, error: String?): LoadedProfile = LoadedProfile(
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
        listOf(),
        WeightBreakdown.empty(WeightSystem.SENITHER),
        WeightBreakdown.empty(WeightSystem.LILY),
        NetworthBreakdown.empty(error),
        NetworthBreakdown.empty(error),
        NetworthBreakdown.empty(error),
        NetworthBreakdown.empty(error),
        emptyArmor(),
        PlayerStatsSnapshot.empty(),
        error
    )

    private fun emptyArmor(): Array<ItemStack> = arrayOf(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)

    @JvmStatic
    fun parseByProfileId(name: String, uuid: UUID, root: JsonObject?, profileId: String?): LoadedProfile {
        if (root == null || profileId.isNullOrBlank()) return failed(name, "Missing profile")
        return try {
            InventoryDecoder.withSharedDecode(Supplier {
                parseUnsafe(name, uuid, root, null, null, AuctionSnapshot.empty(), true, profileId)
            })
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Profile re-parse failed for {} ({})", name, profileId, exception)
            failed(name, exception.message ?: "Parse failed")
        }
    }

    // Logs and falls back to the empty value when a soft data failure hits one tab.
    private inline fun <T> softParse(what: String, name: String, empty: () -> T, block: () -> T): T = try {
        block()
    } catch (exception: RuntimeException) {
        if (!SoftDataFailure.isSoft(exception)) throw exception
        ProfileViewer.LOGGER.warn("$what failed for {}", name, exception)
        empty()
    }

    private fun parseUnsafe(
        name: String,
        uuid: UUID,
        root: JsonObject,
        museumRoot: JsonObject?,
        electionRoot: JsonObject?,
        auctions: AuctionSnapshot?,
        includeNetworth: Boolean,
        preferredProfileId: String?
    ): LoadedProfile {
        val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else null
        if (profiles == null || profiles.isEmpty) return failed(name, "No SkyBlock profiles")
        val best = ProfileCoopIndex.pickProfile(profiles, preferredProfileId)
        var cuteName = "Unknown"
        val undashed = HypixelApiClient.undashed(uuid)
        var profileId: String? = null
        if (best != null) {
            if (best.has("cute_name")) cuteName = best.get("cute_name").asString
            if (best.has("profile_id")) profileId = best.get("profile_id").asString
        }
        val choices = ProfileCoopIndex.listProfileChoices(profiles, profileId, undashed)
        if (best == null) return failed(name, "No usable profile")
        val members = if (best.has("members") && best.get("members").isJsonObject) best.getAsJsonObject("members") else null
        val member = ProfileMuseumLookup.findMember(members, undashed) ?: return failed(name, "Member data missing")

        ProfileCoopIndex.warmCoopMemberNames(choices, undashed, name, null)

        val weightLevels = WeightCalculator.buildLevels(member)
        val senither = WeightCalculator.senither(member, weightLevels)
        val lily = WeightCalculator.lily(member, weightLevels)

        // Never block first paint on prices. Brief wait only during enrichment.
        if (includeNetworth && !ItemPricer.isReady()) ItemPricer.awaitReady(1_500L)
        val museumMember = if (includeNetworth) ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed) else null
        ProfileViewer.LOGGER.info("Parsing profile {} ({})", name, cuteName)

        val inventoryCategories = InventoryDecoder.parseCategories(member, museumMember)

        val pricesReady = includeNetworth && ItemPricer.isReady()
        fun networth(mode: NetworthMode): NetworthBreakdown =
            if (pricesReady) NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, mode)
            else NetworthBreakdown.empty("Loading networth")
        val networthNormal = networth(NetworthMode.NORMAL)
        val networthNonCosmetic = networth(NetworthMode.NON_COSMETIC)
        val networthUnsoulbound = networth(NetworthMode.UNSOULBOUND)
        val networthUnsoulboundNonCosmetic = networth(NetworthMode.UNSOULBOUND_NON_COSMETIC)

        val skills = ProfileHomeParser.buildHomeSkills(member)
        val social = ProfileHomeParser.buildSocial(member)
        val runecrafting = ProfileHomeParser.buildRunecrafting(member)
        val slayers = ProfileHomeParser.buildHomeSlayers(member)
        val activeSlayer = ProfileHomeParser.parseActiveSlayerQuest(member)

        var sbLevel = 0
        var sbXp = 0
        val leveling = Leveling.obj(member.get("leveling"))
        if (leveling != null) {
            Leveling.num(leveling.get("experience"))?.let { experience ->
                sbLevel = floor(experience / 100f).toInt()
                sbXp = Math.round(experience % 100f)
            }
        }
        val emblems = ProfileHomeParser.parseEmblems(leveling)

        val nwText = when {
            !includeNetworth || !pricesReady -> "..."
            networthNormal.total > 0 -> FormatUtil.shortCoins(networthNormal.total)
            else -> "-"
        }

        val purseCoins = NetworthCalculator.purse(member)
        val bankCoins = NetworthCalculator.bank(best, member)
        val bankTransactions = ProfileHomeParser.parseBankTransactions(best)

        val snapshot = ProfileSnapshot(
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
            SlayerMayorMods.from(electionRoot)
        )

        val dungeons = ProfileDungeonParser.parseDungeons(member, museumMember, electionRoot, inventoryCategories)

        val inventories = softParse("Inventory decode", name, { InventorySnapshot.empty() }) {
            InventoryDecoder.parseUi(member).also { SkyBlockItemFactory.warmAsync(it) }
        }
        val pets = softParse("Pets decode", name, { PetSnapshot.empty() }) {
            PetSnapshot.fromMember(member).also { SkyBlockItemFactory.warmPetsAsync(it) }
        }
        val collections = softParse("Collections decode", name, { CollectionSnapshot.empty() }) {
            CollectionSnapshot.fromProfile(members, uuid, name)
        }
        val garden = softParse("Garden member parse", name, { GardenSnapshot.empty() }) { GardenSnapshot.fromMember(member) }
        val mining = softParse("Mining member parse", name, { MiningSnapshot.empty() }) {
            val parsed = MiningSnapshot.fromMember(member)
            parsed.withColeWeight(ColeWeight.calculate(parsed, collections, member))
        }
        val foraging = softParse("Foraging member parse", name, { ForagingSnapshot.empty() }) { ForagingSnapshot.fromMember(member) }
        val fishing = softParse("Fishing member parse", name, { FishingSnapshot.empty() }) { FishingSnapshot.fromMember(member) }
        var crimson = softParse("Crimson member parse", name, { CrimsonSnapshot.empty() }) { CrimsonSnapshot.fromMember(member) }
        val rift = softParse("Rift member parse", name, { RiftSnapshot.empty() }) { RiftSnapshot.fromMember(member) }
        val bestiary = softParse("Bestiary member parse", name, { BestiarySnapshot.empty() }) { BestiarySnapshot.fromMember(member) }
        val events = softParse("Events member parse", name, { EventsSnapshot.empty() }) { EventsSnapshot.fromMember(member, root, uuid) }
        val misc = softParse("Misc stats parse", name, { MiscStatsSnapshot.empty() }) { MiscStatsSnapshot.from(best, member) }
        ProfileViewer.LOGGER.info("Profile ready for {} (nw={})", name, nwText)
        val auctionSnapshot = (auctions ?: AuctionSnapshot.empty()).withStats(AuctionSnapshot.Stats.fromMember(member))
        val playerStats = FarmingFortuneEstimate.attach(PlayerStatsCalculator.fromMember(member), member, inventoryCategories)
        crimson = crimson.withPlayerStats(playerStats)

        val loaded = LoadedProfile(
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
        )

        if (includeNetworth && !pricesReady) {
            scheduleNetworthRefresh(loaded, member, best, museumMember, inventoryCategories, null)
        }
        return loaded
    }

    private fun scheduleNetworthRefresh(
        base: LoadedProfile?,
        member: JsonObject,
        profile: JsonObject?,
        museumMember: JsonObject?,
        inventoryCategories: Map<String, List<InventoryDecoder.Stack>>,
        onUpdate: Consumer<LoadedProfile>?
    ) {
        if (base == null || !base.ok()) return
        val baseSnapshot = base.snapshot ?: return
        val uuid = baseSnapshot.playerUuid() ?: return
        val name = baseSnapshot.playerName()
        CompletableFuture.runAsync({
            ItemPricer.awaitReady(30_000L)
            if (!ItemPricer.isReady()) return@runAsync
            try {
                fun calc(mode: NetworthMode) = NetworthCalculator.calculate(member, profile, museumMember, inventoryCategories, mode)
                val normal = calc(NetworthMode.NORMAL)
                val nonCosmetic = calc(NetworthMode.NON_COSMETIC)
                val unsoulbound = calc(NetworthMode.UNSOULBOUND)
                val unsoulboundNonCosmetic = calc(NetworthMode.UNSOULBOUND_NON_COSMETIC)
                val nwText = if (normal.total > 0) FormatUtil.shortCoins(normal.total) else "-"
                val refreshed = base.copy(
                    snapshot = baseSnapshot.withNetworthText(nwText),
                    museumMember = museumMember ?: base.museumMember,
                    networthNormal = normal,
                    networthNonCosmetic = nonCosmetic,
                    networthUnsoulbound = unsoulbound,
                    networthUnsoulboundNonCosmetic = unsoulboundNonCosmetic,
                    error = null
                )
                putCache(uuidKey(uuid), refreshed)
                putCache(nameKey(name), refreshed)
                onUpdate?.accept(refreshed)
                for (listener in NETWORTH_LISTENERS) {
                    try {
                        listener.accept(refreshed)
                    } catch (listenerError: RuntimeException) {
                        ProfileViewer.LOGGER.debug("Networth listener failed for {}", name, listenerError)
                    }
                }
            } catch (exception: RuntimeException) {
                if (!SoftDataFailure.isSoft(exception)) throw exception
                ProfileViewer.LOGGER.warn("Deferred networth refresh failed for {}", name, exception)
            }
        }, ENRICH_EXECUTOR)
    }

    private val NETWORTH_LISTENERS = CopyOnWriteArrayList<Consumer<LoadedProfile>>()

    /** Registers a listener for deferred networth refreshes (screen generation must filter stale). */
    @JvmStatic
    fun addNetworthListener(listener: Consumer<LoadedProfile>?) {
        if (listener != null) NETWORTH_LISTENERS.addIfAbsent(listener)
    }

    @JvmStatic
    fun removeNetworthListener(listener: Consumer<LoadedProfile>?) {
        if (listener != null) NETWORTH_LISTENERS.remove(listener)
    }

    @JvmStatic
    fun coopMemberDisplayName(member: CoopMemberRef?): String = ProfileCoopIndex.coopMemberDisplayName(member)

    @JvmStatic
    fun coopNameResolved(member: CoopMemberRef?): Boolean = ProfileCoopIndex.coopNameResolved(member)

    @JvmStatic
    fun warmCoopMemberNames(
        choices: List<ProfileChoice>?,
        viewedUuidUndashed: String?,
        viewedName: String?,
        onResolved: Runnable?
    ) = ProfileCoopIndex.warmCoopMemberNames(choices, viewedUuidUndashed, viewedName, onResolved)

    @JvmStatic
    fun findMuseumMember(museumRoot: JsonObject?, profileId: String?, undashed: String): JsonObject? =
        ProfileMuseumLookup.findMuseumMember(museumRoot, profileId, undashed)
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import com.google.gson.JsonObject
import fishmod.compat.curScreen
import fishmod.pv.ProfileViewer
import fishmod.pv.api.EliteBotApiClient
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.api.ProfileFetcher
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.GuildStatus
import fishmod.pv.data.MuseumCache
import fishmod.pv.data.PlayerStatus
import fishmod.pv.data.UsernameHistory
import fishmod.pv.gui.events.EventsPage
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft

class ProfileScreenDataLoader(private val screen: ProfileViewerScreen) {
	private var gardenFetchStarted = false
	private var gardenContestsFetchStarted = false
	private var gardenWeightFetchStarted = false
	private var museumFetchStarted = false
	private var usernameHistoryFetchStarted = false
	private var statusFetchStarted = false
	private var bingoFetchStarted = false
	private var bingoFetchInFlight = false
	private var bingoRetryAtMs = 0L
	private var playerRankFetchStarted = false

	fun resetForNewLoad() {
		gardenFetchStarted = false
		gardenContestsFetchStarted = false
		gardenWeightFetchStarted = false
		museumFetchStarted = false
		bingoFetchStarted = false
		bingoFetchInFlight = false
		bingoRetryAtMs = 0L
		usernameHistoryFetchStarted = false
		statusFetchStarted = false
		playerRankFetchStarted = false
	}

	fun markMuseumLoaded() {
		museumFetchStarted = true
	}

	private fun onClient(generation: Int, action: () -> Unit) {
		val client = Minecraft.getInstance() ?: return
		client.execute {
			if (stillCurrent(client, generation)) {
				action()
			}
		}
	}

	fun ensurePlayerRank() {
		val uuid = screen.playerUuid()
		if (uuid == null || playerRankFetchStarted) {
			return
		}
		playerRankFetchStarted = true
		val generation = screen.loadGeneration()
		HypixelApiClient.player(uuid).whenComplete { opt, error ->
			onClient(generation) {
				if (error != null || opt == null || opt.isEmpty) {
					screen.homeMiscPage().applyFreeCookie(0L)
					return@onClient
				}
				val player = opt.get()
				screen.homePage().applyPlayerRank(player)
				var freeCookie = 0L
				if (player.has("skyblock_free_cookie") && player.get("skyblock_free_cookie").isJsonPrimitive) {
					try {
						freeCookie = player.get("skyblock_free_cookie").asDouble.toLong()
					} catch (_: Exception) {
					}
				}
				screen.homeMiscPage().applyFreeCookie(freeCookie)
			}
		}
	}

	fun ensureUsernameHistory() {
		val uuid = screen.playerUuid()
		if (uuid == null) {
			screen.homePage().applyUsernameHistory(UsernameHistory.error("Missing player"))
			return
		}
		if (screen.homePage().usernameHistory().state == UsernameHistory.State.LOADING) {
			return
		}
		usernameHistoryFetchStarted = true
		screen.homePage().applyUsernameHistory(UsernameHistory.loading())
		val generation = screen.loadGeneration()
		HypixelApiClient.usernameHistory(uuid).whenComplete { opt, error ->
			onClient(generation) {
				if (error != null || opt == null || opt.isEmpty) {
					screen.homePage().applyUsernameHistory(UsernameHistory.error("History unavailable"))
					return@onClient
				}
				val entries = ArrayList<UsernameHistory.Entry>()
				for (el in opt.get()) {
					if (el == null || !el.isJsonObject) {
						continue
					}
					val obj = el.asJsonObject
					val name = stringOrEmpty(obj, "username")
					val changed = stringOrEmpty(obj, "changed_at")
					if (name.isNotBlank()) {
						entries.add(UsernameHistory.Entry(name, changed))
					}
				}
				screen.homePage().applyUsernameHistory(UsernameHistory.ready(entries))
			}
		}
	}

	fun ensureGuild() {
		val uuid = screen.playerUuid()
		if (uuid == null) {
			screen.homeMiscPage().applyGuild(GuildStatus.error("Missing player"))
			return
		}
		val state = screen.homeMiscPage().guild().state
		if (state == GuildStatus.State.LOADING || state == GuildStatus.State.READY) {
			return
		}
		screen.homeMiscPage().applyGuild(GuildStatus.loading())
		val generation = screen.loadGeneration()
		HypixelApiClient.guild(uuid).whenComplete { opt, error ->
			onClient(generation) {
				if (error != null || opt == null || opt.isEmpty) {
					screen.homeMiscPage().applyGuild(GuildStatus.error("Guild unavailable"))
					return@onClient
				}
				screen.homeMiscPage().applyGuild(GuildStatus.fromHypixel(opt.get(), HypixelApiClient.undashed(uuid)))
			}
		}
	}

	fun ensurePlayerStatus() {
		val uuid = screen.playerUuid()
		if (uuid == null) {
			screen.homePage().applyPlayerStatus(PlayerStatus.error("Missing player"))
			return
		}
		if (screen.homePage().playerStatus().state == PlayerStatus.State.LOADING) {
			return
		}
		statusFetchStarted = true
		screen.homePage().applyPlayerStatus(PlayerStatus.loading())
		val generation = screen.loadGeneration()
		HypixelApiClient.status(uuid).whenComplete { opt, error ->
			onClient(generation) {
				if (error != null || opt == null || opt.isEmpty) {
					screen.homePage().applyPlayerStatus(PlayerStatus.error("Status unavailable"))
					return@onClient
				}
				val root = opt.get()
				val session = if (root.has("session") && root.get("session").isJsonObject) root.getAsJsonObject("session") else root
				val online = session.has("online") && session.get("online").isJsonPrimitive &&
					session.get("online").asBoolean
				if (!online) {
					applyOfflineStatus(uuid, generation)
					return@onClient
				}
				val game = stringOrEmpty(session, "gameType")
				val mode = stringOrEmpty(session, "mode")
				val map = stringOrEmpty(session, "map")
				screen.homePage().applyPlayerStatus(PlayerStatus.online(game, mode, map))
			}
		}
	}

	private fun applyOfflineStatus(uuid: UUID, generation: Int) {
		HypixelApiClient.player(uuid, STATUS_PLAYER_MAX_AGE_MS).whenComplete { opt, error ->
			onClient(generation) {
				val player = if (error == null && opt != null && opt.isPresent) opt.get() else null
				val status = PlayerStatus.offlineFromPlayer(player, System.currentTimeMillis())
				ProfileViewer.LOGGER.debug(
					"[PV status] {} offline: {} -> {}",
					HypixelApiClient.undashed(uuid),
					PlayerStatus.timestampDiagnostics(player),
					when {
						status.lastOnlineMs > 0L -> "lastOnline=" + status.lastOnlineMs
						status.sessionHidden -> "online status hidden"
						else -> "no timestamp"
					}
				)
				screen.homePage().applyPlayerStatus(status)
			}
		}
	}

	fun ensureGardenIsland() {
		if (gardenFetchStarted) {
			return
		}
		val current = screen.gardenPage().snapshot()
		if (current.islandLoaded) {
			return
		}
		val id = screen.profileId()
		if (id.isNullOrBlank()) {
			screen.gardenPage().apply(current.withIslandError("No profile selected"))
			gardenFetchStarted = true
			return
		}
		gardenFetchStarted = true
		screen.gardenPage().apply(current.withIslandLoading())
		val generation = screen.loadGeneration()
		HypixelApiClient.skyblockGarden(id).whenComplete { opt, error ->
			onClient(generation) {
				val base = screen.gardenPage().snapshot()
				if (error != null) {
					ProfileViewer.LOGGER.warn("Garden fetch failed for profile {}", id, error)
					screen.gardenPage().apply(base.withIslandError(error.message ?: "Couldn't load garden"))
					return@onClient
				}
				if (opt == null || opt.isEmpty) {
					screen.gardenPage().apply(base.withIslandError("Garden unavailable"))
					return@onClient
				}
				try {
					screen.gardenPage().apply(base.withIsland(opt.get()))
				} catch (exception: Exception) {
					ProfileViewer.LOGGER.warn("Garden parse failed for profile {}", id, exception)
					screen.gardenPage().apply(base.withIslandError("Couldn't load garden"))
				}
			}
		}
	}

	fun ensureGardenContests() {
		if (gardenContestsFetchStarted) {
			return
		}
		val current = screen.gardenPage().snapshot()
		val uuid = screen.playerUuid()
		val id = screen.profileId()
		if (uuid == null || id.isNullOrBlank()) {
			screen.gardenPage().apply(current.withContestsError("No profile selected"))
			gardenContestsFetchStarted = true
			return
		}
		gardenContestsFetchStarted = true
		screen.gardenPage().apply(current.withContestsLoading())
		val generation = screen.loadGeneration()
		EliteBotApiClient.contests(uuid, id).whenComplete { opt, error ->
			onClient(generation) {
				val base = screen.gardenPage().snapshot()
				if (error != null || opt == null || opt.isEmpty) {
					if (error != null) {
						ProfileViewer.LOGGER.warn("Elite contests failed for {}", id, error)
					}
					if (base.contests.isNotEmpty()) {
						screen.gardenPage().patch(base.withContestsReady())
					} else {
						screen.gardenPage().patch(base.withContestsError(error?.message ?: "Contests unavailable"))
					}
					return@onClient
				}
				try {
					screen.gardenPage().patch(base.withEliteContests(opt.get()))
				} catch (exception: Exception) {
					ProfileViewer.LOGGER.warn("Elite contests parse failed for {}", id, exception)
					screen.gardenPage().patch(base.withContestsError("Couldn't load contests"))
				}
			}
		}
	}

	fun ensureGardenWeight() {
		if (gardenWeightFetchStarted) {
			return
		}
		val current = screen.gardenPage().snapshot()
		val uuid = screen.playerUuid()
		val id = screen.profileId()
		if (uuid == null || id.isNullOrBlank()) {
			screen.gardenPage().apply(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("No profile selected")))
			gardenWeightFetchStarted = true
			return
		}
		gardenWeightFetchStarted = true
		screen.gardenPage().patch(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.pending()))
		val generation = screen.loadGeneration()
		EliteBotApiClient.weight(uuid, id).whenComplete { opt, error ->
			onClient(generation) {
				val base = screen.gardenPage().snapshot()
				if (error != null) {
					ProfileViewer.LOGGER.warn("Elite weight failed for {}", id, error)
					screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed(
						error.message ?: "Weight unavailable"
					)))
					return@onClient
				}
				if (opt == null || opt.isEmpty) {
					screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight unavailable")))
					return@onClient
				}
				try {
					screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.fromElite(opt.get())))
				} catch (exception: Exception) {
					ProfileViewer.LOGGER.warn("Elite weight parse failed for {}", id, exception)
					screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Couldn't load weight")))
				}
			}
		}
	}

	fun ensureMuseum(force: Boolean) {
		val uuid = screen.playerUuid()
		val id = screen.profileId()
		if (uuid == null || id.isNullOrBlank()) {
			screen.museumPage().applyError("No profile selected")
			museumFetchStarted = true
			return
		}
		val cached = MuseumCache.get(uuid, id)
		if (!force && cached != null) {
			screen.museumPage().applyMuseum(cached.museumMember)
			museumFetchStarted = true
			return
		}
		if (museumFetchStarted && !force) {
			return
		}
		if (force && !MuseumCache.canRefresh(uuid, id)) {
			screen.museumPage().applyError("Refresh ready in " + FormatUtil.prettySpan(MuseumCache.refreshReadyInMs(uuid, id)))
			return
		}
		museumFetchStarted = true
		screen.museumPage().applyLoading()
		val generation = screen.loadGeneration()
		HypixelApiClient.skyblockMuseum(uuid, id).whenComplete { opt, error ->
			onClient(generation) {
				if (error != null || opt == null || opt.isEmpty) {
					ProfileViewer.LOGGER.warn("Museum fetch failed for {}", id, error)
					screen.museumPage().applyError(error?.message ?: "Museum unavailable")
					return@onClient
				}
				try {
					val undashed = HypixelApiClient.undashed(uuid)
					val member = ProfileFetcher.findMuseumMember(opt.get(), id, undashed)
					if (member == null) {
						screen.museumPage().applyError("Museum unavailable")
						return@onClient
					}
					MuseumCache.put(uuid, id, member, member.entrySet().size)
					screen.museumPage().applyMuseum(member)
					ProfileFetcher.museumLoaded(uuid, id, member)
				} catch (exception: Exception) {
					ProfileViewer.LOGGER.warn("Museum parse failed for {}", id, exception)
					screen.museumPage().applyError("Couldn't load museum")
				}
			}
		}
	}

	fun ensureBingo(forceRefresh: Boolean) {
		val uuid = screen.playerUuid()
		if (uuid == null) {
			screen.eventsPage().applyBingoError("Missing player")
			return
		}
		val events = screen.eventsPage()
		if (!forceRefresh && events.bingoState() == EventsPage.BingoLoadState.READY && !events.needsBingoHistory()) {
			bingoFetchStarted = true
			return
		}
		if (bingoFetchInFlight) {
			return
		}
		val historyOnly = !forceRefresh &&
			events.bingoState() == EventsPage.BingoLoadState.READY &&
			events.needsBingoHistory()
		if (historyOnly) {
			if (System.currentTimeMillis() < bingoRetryAtMs) {
				return
			}
		} else if (!forceRefresh && bingoFetchStarted) {
			if (events.bingoState() != EventsPage.BingoLoadState.ERROR || System.currentTimeMillis() < bingoRetryAtMs) {
				return
			}
		}
		bingoFetchStarted = true
		bingoFetchInFlight = true
		if (!historyOnly) {
			events.applyBingoLoading()
		}
		val generation = screen.loadGeneration()
		val resFut: CompletableFuture<Optional<JsonObject>> =
			if (historyOnly) CompletableFuture.completedFuture(Optional.empty()) else HypixelApiClient.skyblockBingoResources()
		val histFut = HypixelApiClient.skyblockBingo(uuid)
		CompletableFuture.allOf(resFut, histFut).whenComplete { _, error ->
			val client = Minecraft.getInstance() ?: return@whenComplete
			client.execute {
				bingoFetchInFlight = false
				if (!stillCurrent(client, generation)) {
					return@execute
				}
				if (error != null) {
					ProfileViewer.LOGGER.warn("Bingo fetch failed for {}", uuid, error)
					if (!historyOnly) {
						screen.eventsPage().applyBingoError(error.message ?: "Couldn't load bingo")
						bingoRetryAtMs = System.currentTimeMillis() + 5_000L
					} else {
						bingoRetryAtMs = System.currentTimeMillis() + 15_000L
					}
					return@execute
				}
				try {
					val resources = resFut.join().orElse(null)
					val history = histFut.join().orElse(null)
					if (!historyOnly && resources == null && history == null) {
						screen.eventsPage().applyBingoError("Bingo unavailable")
						bingoRetryAtMs = System.currentTimeMillis() + 5_000L
						return@execute
					}
					var snapshot = screen.eventsPage().snapshot()
					if (resources != null) {
						snapshot = snapshot.withBingoResources(resources)
					}
					val historyLoaded = history != null
					if (historyLoaded) {
						snapshot = snapshot.withBingoHistory(history)
						bingoRetryAtMs = 0L
					} else {
						ProfileViewer.LOGGER.warn(
							"Bingo history missing for {} (worker miss / no local API key); will retry",
							uuid
						)
						bingoRetryAtMs = System.currentTimeMillis() + 15_000L
					}
					screen.eventsPage().applyBingoReady(snapshot, historyLoaded)
				} catch (exception: Exception) {
					ProfileViewer.LOGGER.warn("Bingo parse failed for {}", uuid, exception)
					if (!historyOnly) {
						screen.eventsPage().applyBingoError("Couldn't load bingo")
						bingoRetryAtMs = System.currentTimeMillis() + 5_000L
					} else {
						bingoRetryAtMs = System.currentTimeMillis() + 15_000L
					}
				}
			}
		}
	}

	private fun stillCurrent(client: Minecraft, generation: Int): Boolean =
		client.curScreen === screen && screen.isLoadGeneration(generation)

	private companion object {
		const val STATUS_PLAYER_MAX_AGE_MS = 60_000L

		fun stringOrEmpty(obj: JsonObject, key: String): String =
			if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else ""
	}
}

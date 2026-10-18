/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.BetterPV;
import fishmod.pv.api.EliteBotApiClient;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.api.ProfileFetcher;
import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.GuildStatus;
import fishmod.pv.data.MuseumCache;
import fishmod.pv.data.PlayerStatus;
import fishmod.pv.data.UsernameHistory;
import fishmod.pv.gui.events.EventsPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * Side fetches for an open profile screen (rank, history, guild, status, garden, museum, bingo).
 * Captures {@code loadGeneration} at fetch start; applies only while the screen is still current
 * and the generation matches.
 */
final class ProfileScreenDataLoader {
	private static final long STATUS_PLAYER_MAX_AGE_MS = 60_000L;

	private final ProfileViewerScreen screen;

	private boolean gardenFetchStarted;
	private boolean gardenContestsFetchStarted;
	private boolean gardenWeightFetchStarted;
	private boolean museumFetchStarted;
	private boolean usernameHistoryFetchStarted;
	private boolean statusFetchStarted;
	private boolean bingoFetchStarted;
	private boolean bingoFetchInFlight;
	private long bingoRetryAtMs;
	private boolean playerRankFetchStarted;

	ProfileScreenDataLoader(ProfileViewerScreen screen) {
		this.screen = screen;
	}

	void resetForNewLoad() {
		this.gardenFetchStarted = false;
		this.gardenContestsFetchStarted = false;
		this.gardenWeightFetchStarted = false;
		this.museumFetchStarted = false;
		this.bingoFetchStarted = false;
		this.bingoFetchInFlight = false;
		this.bingoRetryAtMs = 0L;
		this.usernameHistoryFetchStarted = false;
		this.statusFetchStarted = false;
		this.playerRankFetchStarted = false;
	}

	void markMuseumLoaded() {
		this.museumFetchStarted = true;
	}

	void ensurePlayerRank() {
		if (this.screen.playerUuid() == null || this.playerRankFetchStarted) {
			return;
		}
		this.playerRankFetchStarted = true;
		UUID uuid = this.screen.playerUuid();
		int generation = this.screen.loadGeneration();
		HypixelApiClient.player(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.screen.homeMiscPage().applyFreeCookie(0L);
					return;
				}
				this.screen.homePage().applyPlayerRank(opt.get());
				long freeCookie = 0L;
				var player = opt.get();
				if (player.has("skyblock_free_cookie") && player.get("skyblock_free_cookie").isJsonPrimitive()) {
					try {
						freeCookie = (long) player.get("skyblock_free_cookie").getAsDouble();
					} catch (Exception ignored) {
					}
				}
				this.screen.homeMiscPage().applyFreeCookie(freeCookie);
			});
		});
	}

	void ensureUsernameHistory() {
		if (this.screen.playerUuid() == null) {
			this.screen.homePage().applyUsernameHistory(UsernameHistory.error("Missing player"));
			return;
		}
		// Allow re-click refresh when already READY/ERROR so a truncated prior fetch can be replaced.
		if (this.screen.homePage().usernameHistory().state() == UsernameHistory.State.LOADING) {
			return;
		}
		this.usernameHistoryFetchStarted = true;
		this.screen.homePage().applyUsernameHistory(UsernameHistory.loading());
		UUID uuid = this.screen.playerUuid();
		int generation = this.screen.loadGeneration();
		HypixelApiClient.usernameHistory(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.screen.homePage().applyUsernameHistory(UsernameHistory.error("History unavailable"));
					return;
				}
				List<UsernameHistory.Entry> entries = new ArrayList<>();
				JsonArray arr = opt.get();
				for (JsonElement el : arr) {
					if (el == null || !el.isJsonObject()) {
						continue;
					}
					JsonObject obj = el.getAsJsonObject();
					String name = obj.has("username") && obj.get("username").isJsonPrimitive()
						? obj.get("username").getAsString() : "";
					String changed = obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()
						? obj.get("changed_at").getAsString() : "";
					if (!name.isBlank()) {
						entries.add(new UsernameHistory.Entry(name, changed));
					}
				}
				this.screen.homePage().applyUsernameHistory(UsernameHistory.ready(entries));
			});
		});
	}

	void ensureGuild() {
		if (this.screen.playerUuid() == null) {
			this.screen.homeMiscPage().applyGuild(GuildStatus.error("Missing player"));
			return;
		}
		if (this.screen.homeMiscPage().guild().state() == GuildStatus.State.LOADING
			|| this.screen.homeMiscPage().guild().state() == GuildStatus.State.READY) {
			return;
		}
		this.screen.homeMiscPage().applyGuild(GuildStatus.loading());
		UUID uuid = this.screen.playerUuid();
		int generation = this.screen.loadGeneration();
		HypixelApiClient.guild(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.screen.homeMiscPage().applyGuild(GuildStatus.error("Guild unavailable"));
					return;
				}
				this.screen.homeMiscPage().applyGuild(GuildStatus.fromHypixel(
					opt.get(),
					HypixelApiClient.undashed(uuid)
				));
			});
		});
	}

	void ensurePlayerStatus() {
		if (this.screen.playerUuid() == null) {
			this.screen.homePage().applyPlayerStatus(PlayerStatus.error("Missing player"));
			return;
		}
		if (this.screen.homePage().playerStatus().state() == PlayerStatus.State.LOADING) {
			return;
		}
		this.statusFetchStarted = true;
		this.screen.homePage().applyPlayerStatus(PlayerStatus.loading());
		UUID uuid = this.screen.playerUuid();
		int generation = this.screen.loadGeneration();
		HypixelApiClient.status(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.screen.homePage().applyPlayerStatus(PlayerStatus.error("Status unavailable"));
					return;
				}
				JsonObject root = opt.get();
				JsonObject session = root.has("session") && root.get("session").isJsonObject()
					? root.getAsJsonObject("session")
					: root;
				boolean online = session.has("online") && session.get("online").isJsonPrimitive()
					&& session.get("online").getAsBoolean();
				if (!online) {
					applyOfflineStatus(uuid, generation);
					return;
				}
				String game = session.has("gameType") && session.get("gameType").isJsonPrimitive()
					? session.get("gameType").getAsString() : "";
				String mode = session.has("mode") && session.get("mode").isJsonPrimitive()
					? session.get("mode").getAsString() : "";
				String map = session.has("map") && session.get("map").isJsonPrimitive()
					? session.get("map").getAsString() : "";
				this.screen.homePage().applyPlayerStatus(PlayerStatus.online(game, mode, map));
			});
		});
	}

	private void applyOfflineStatus(UUID uuid, int generation) {
		HypixelApiClient.player(uuid, STATUS_PLAYER_MAX_AGE_MS).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				JsonObject player = error == null && opt != null && opt.isPresent() ? opt.get() : null;
				PlayerStatus status = PlayerStatus.offlineFromPlayer(player, System.currentTimeMillis());
				BetterPV.LOGGER.debug(
					"[PV status] {} offline: {} -> {}",
					HypixelApiClient.undashed(uuid),
					PlayerStatus.timestampDiagnostics(player),
					status.lastOnlineMs() > 0L ? "lastOnline=" + status.lastOnlineMs()
						: status.sessionHidden() ? "online status hidden" : "no timestamp"
				);
				this.screen.homePage().applyPlayerStatus(status);
			});
		});
	}

	void ensureGardenIsland() {
		if (this.gardenFetchStarted) {
			return;
		}
		GardenSnapshot current = this.screen.gardenPage().snapshot();
		if (current.islandLoaded()) {
			return;
		}
		String id = this.screen.profileId();
		if (id == null || id.isBlank()) {
			this.screen.gardenPage().apply(current.withIslandError("No profile selected"));
			this.gardenFetchStarted = true;
			return;
		}
		this.gardenFetchStarted = true;
		this.screen.gardenPage().apply(current.withIslandLoading());
		int generation = this.screen.loadGeneration();
		HypixelApiClient.skyblockGarden(id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				GardenSnapshot base = this.screen.gardenPage().snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Garden fetch failed for profile {}", id, error);
					this.screen.gardenPage().apply(base.withIslandError(
						error.getMessage() == null ? "Couldn't load garden" : error.getMessage()
					));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.screen.gardenPage().apply(base.withIslandError("Garden unavailable"));
					return;
				}
				try {
					this.screen.gardenPage().apply(base.withIsland(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Garden parse failed for profile {}", id, exception);
					this.screen.gardenPage().apply(base.withIslandError("Couldn't load garden"));
				}
			});
		});
	}

	void ensureGardenContests() {
		if (this.gardenContestsFetchStarted) {
			return;
		}
		GardenSnapshot current = this.screen.gardenPage().snapshot();
		UUID uuid = this.screen.playerUuid();
		String id = this.screen.profileId();
		if (uuid == null || id == null || id.isBlank()) {
			this.screen.gardenPage().apply(current.withContestsError("No profile selected"));
			this.gardenContestsFetchStarted = true;
			return;
		}
		this.gardenContestsFetchStarted = true;
		this.screen.gardenPage().apply(current.withContestsLoading());
		int generation = this.screen.loadGeneration();
		EliteBotApiClient.contests(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				GardenSnapshot base = this.screen.gardenPage().snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite contests failed for {}", id, error);
					if (!base.contests().isEmpty()) {
						this.screen.gardenPage().patch(base.withContestsReady());
					} else {
						this.screen.gardenPage().patch(base.withContestsError(
							error.getMessage() == null ? "Contests unavailable" : error.getMessage()
						));
					}
					return;
				}
				if (opt == null || opt.isEmpty()) {
					// Keep Hypixel contest list if Elite miss.
					if (!base.contests().isEmpty()) {
						this.screen.gardenPage().patch(base.withContestsReady());
					} else {
						this.screen.gardenPage().patch(base.withContestsError("Contests unavailable"));
					}
					return;
				}
				try {
					this.screen.gardenPage().patch(base.withEliteContests(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite contests parse failed for {}", id, exception);
					this.screen.gardenPage().patch(base.withContestsError("Couldn't load contests"));
				}
			});
		});
	}

	void ensureGardenWeight() {
		if (this.gardenWeightFetchStarted) {
			return;
		}
		GardenSnapshot current = this.screen.gardenPage().snapshot();
		UUID uuid = this.screen.playerUuid();
		String id = this.screen.profileId();
		if (uuid == null || id == null || id.isBlank()) {
			this.screen.gardenPage().apply(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("No profile selected")));
			this.gardenWeightFetchStarted = true;
			return;
		}
		this.gardenWeightFetchStarted = true;
		this.screen.gardenPage().patch(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.pending()));
		int generation = this.screen.loadGeneration();
		EliteBotApiClient.weight(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				GardenSnapshot base = this.screen.gardenPage().snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite weight failed for {}", id, error);
					this.screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed(
						error.getMessage() == null ? "Weight unavailable" : error.getMessage()
					)));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight unavailable")));
					return;
				}
				try {
					this.screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.fromElite(opt.get())));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite weight parse failed for {}", id, exception);
					this.screen.gardenPage().patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Couldn't load weight")));
				}
			});
		});
	}

	void ensureMuseum(boolean force) {
		UUID uuid = this.screen.playerUuid();
		String id = this.screen.profileId();
		if (uuid == null || id == null || id.isBlank()) {
			this.screen.museumPage().applyError("No profile selected");
			this.museumFetchStarted = true;
			return;
		}
		MuseumCache.Entry cached = MuseumCache.get(uuid, id);
		if (!force && cached != null) {
			// applyMuseum no-ops when the same cached member is already applied.
			this.screen.museumPage().applyMuseum(cached.museumMember());
			this.museumFetchStarted = true;
			return;
		}
		if (this.museumFetchStarted && !force) {
			return;
		}
		if (force && !MuseumCache.canRefresh(uuid, id)) {
			this.screen.museumPage().applyError("Refresh ready in " + FormatUtil.prettySpan(
				MuseumCache.refreshReadyInMs(uuid, id)
			));
			return;
		}
		this.museumFetchStarted = true;
		this.screen.museumPage().applyLoading();
		int generation = this.screen.loadGeneration();
		HypixelApiClient.skyblockMuseum(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					BetterPV.LOGGER.warn("Museum fetch failed for {}", id, error);
					this.screen.museumPage().applyError(error == null || error.getMessage() == null ? "Museum unavailable" : error.getMessage());
					return;
				}
				try {
					String undashed = HypixelApiClient.undashed(uuid);
					var member = ProfileFetcher.findMuseumMember(opt.get(), id, undashed);
					if (member == null) {
						this.screen.museumPage().applyError("Museum unavailable");
						return;
					}
					int itemCount = member.entrySet().size();
					MuseumCache.put(uuid, id, member, itemCount);
					this.screen.museumPage().applyMuseum(member);
					ProfileFetcher.museumLoaded(uuid, id, member);
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Museum parse failed for {}", id, exception);
					this.screen.museumPage().applyError("Couldn't load museum");
				}
			});
		});
	}

	void ensureBingo(boolean forceRefresh) {
		if (this.screen.playerUuid() == null) {
			this.screen.eventsPage().applyBingoError("Missing player");
			return;
		}
		if (!forceRefresh
			&& this.screen.eventsPage().bingoState() == EventsPage.BingoLoadState.READY
			&& !this.screen.eventsPage().needsBingoHistory()) {
			this.bingoFetchStarted = true;
			return;
		}
		if (this.bingoFetchInFlight) {
			return;
		}
		boolean historyOnly = !forceRefresh
			&& this.screen.eventsPage().bingoState() == EventsPage.BingoLoadState.READY
			&& this.screen.eventsPage().needsBingoHistory();
		if (historyOnly) {
			if (System.currentTimeMillis() < this.bingoRetryAtMs) {
				return;
			}
		} else if (!forceRefresh && this.bingoFetchStarted) {
			if (this.screen.eventsPage().bingoState() != EventsPage.BingoLoadState.ERROR
				|| System.currentTimeMillis() < this.bingoRetryAtMs) {
				return;
			}
		}
		this.bingoFetchStarted = true;
		this.bingoFetchInFlight = true;
		if (!historyOnly) {
			this.screen.eventsPage().applyBingoLoading();
		}
		UUID uuid = this.screen.playerUuid();
		int generation = this.screen.loadGeneration();
		CompletableFuture<Optional<JsonObject>> resFut = historyOnly
			? CompletableFuture.completedFuture(Optional.empty())
			: HypixelApiClient.skyblockBingoResources();
		var histFut = HypixelApiClient.skyblockBingo(uuid);
		CompletableFuture.allOf(resFut, histFut).whenComplete((ignored, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				this.bingoFetchInFlight = false;
				if (!stillCurrent(client, generation)) {
					return;
				}
				if (error != null) {
					BetterPV.LOGGER.warn("Bingo fetch failed for {}", uuid, error);
					if (!historyOnly) {
						this.screen.eventsPage().applyBingoError(
							error.getMessage() == null ? "Couldn't load bingo" : error.getMessage());
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
					} else {
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
					return;
				}
				try {
					JsonObject resources = resFut.join().orElse(null);
					JsonObject history = histFut.join().orElse(null);
					if (!historyOnly && resources == null && history == null) {
						this.screen.eventsPage().applyBingoError("Bingo unavailable");
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
						return;
					}
					EventsSnapshot events = this.screen.eventsPage().snapshot();
					if (resources != null) {
						events = events.withBingoResources(resources);
					}
					boolean historyLoaded = history != null;
					if (historyLoaded) {
						events = events.withBingoHistory(history);
						this.bingoRetryAtMs = 0L;
					} else {
						BetterPV.LOGGER.warn(
							"Bingo history missing for {} (worker miss / no local API key); will retry",
							uuid
						);
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
					this.screen.eventsPage().applyBingoReady(events, historyLoaded);
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Bingo parse failed for {}", uuid, exception);
					if (!historyOnly) {
						this.screen.eventsPage().applyBingoError("Couldn't load bingo");
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
					} else {
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
				}
			});
		});
	}

	private boolean stillCurrent(Minecraft client, int generation) {
		return client.screen == this.screen && this.screen.isLoadGeneration(generation);
	}
}

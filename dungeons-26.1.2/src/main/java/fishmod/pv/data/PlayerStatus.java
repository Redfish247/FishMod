/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;

/** Hypixel player online status for the Home Status control. */
public final class PlayerStatus {
	private static final Map<String, String> LOCATION_NAMES = Map.ofEntries(
		Map.entry("dynamic", "Private Island"),
		Map.entry("hub", "Hub"),
		Map.entry("farming_1", "The Farming Islands"),
		Map.entry("combat_1", "Spider's Den"),
		Map.entry("combat_2", "Blazing Fortress"),
		Map.entry("combat_3", "The End"),
		Map.entry("foraging_1", "The Park"),
		Map.entry("foraging_2", "Galatea"),
		Map.entry("mining_1", "Gold Mine"),
		Map.entry("mining_2", "Deep Caverns"),
		Map.entry("mining_3", "Dwarven Mines"),
		Map.entry("crystal_hollows", "Crystal Hollows"),
		Map.entry("mineshaft", "Glacite Mineshafts"),
		Map.entry("crimson_isle", "Crimson Isle"),
		Map.entry("fishing_1", "Backwater Bayou"),
		Map.entry("dungeon", "Dungeon"),
		Map.entry("dungeon_hub", "Dungeon Hub"),
		Map.entry("dark_auction", "Dark Auction"),
		Map.entry("winter", "Jerry's Workshop"),
		Map.entry("rift", "The Rift"),
		Map.entry("garden", "The Garden"),
		Map.entry("kuudra", "Kuudra"),
		Map.entry("instanced", "Kuudra"),
		Map.entry("skyblock", "SkyBlock"),
		Map.entry("bedwars", "Bed Wars"),
		Map.entry("skywars", "SkyWars"),
		Map.entry("murder_mystery", "Murder Mystery"),
		Map.entry("build_battle", "Build Battle"),
		Map.entry("housing", "Housing"),
		Map.entry("arcade", "Arcade"),
		Map.entry("survival_games", "Blitz SG"),
		Map.entry("tntgames", "TNT Games"),
		Map.entry("uhc", "UHC"),
		Map.entry("speed_uhc", "Speed UHC"),
		Map.entry("duels", "Duels"),
		Map.entry("pit", "The Pit"),
		Map.entry("replay", "Replay"),
		Map.entry("wool_games", "Wool Wars"),
		Map.entry("mcgo", "Cops and Crims"),
		Map.entry("battleground", "Warlords"),
		Map.entry("super_smash", "Smash Heroes"),
		Map.entry("gingerbread", "Turbo Kart Racers"),
		Map.entry("legacy", "Classic Games"),
		Map.entry("prototype", "Prototype"),
		Map.entry("walls3", "Mega Walls"),
		Map.entry("smp", "SMP")
	);
	public enum State {
		IDLE,
		LOADING,
		ONLINE,
		OFFLINE,
		ERROR
	}

	public static final String LOCATION_HIDDEN = "Location hidden";
	// 2013-01-01, before any Hypixel login could have been recorded.
	private static final long MIN_TIMESTAMP_MS = 1_356_998_400_000L;
	private static final long SECONDS_CUTOFF = 100_000_000_000L;
	private static final long FUTURE_TOLERANCE_MS = 5 * 60_000L;
	private static final long MINUTE_MS = 60_000L;
	private static final long HOUR_MS = 60 * MINUTE_MS;
	private static final long DAY_MS = 24 * HOUR_MS;

	private final State state;
	private final String gameType;
	private final String mode;
	private final String map;
	private final String error;
	private final long lastOnlineMs;
	private final boolean sessionHidden;

	private PlayerStatus(
		State state, String gameType, String mode, String map, String error, long lastOnlineMs, boolean sessionHidden
	) {
		this.state = state == null ? State.IDLE : state;
		this.gameType = gameType == null ? "" : gameType;
		this.mode = mode == null ? "" : mode;
		this.map = map == null ? "" : map;
		this.error = error == null ? "" : error;
		this.lastOnlineMs = Math.max(0L, lastOnlineMs);
		this.sessionHidden = sessionHidden;
	}

	public static PlayerStatus idle() {
		return new PlayerStatus(State.IDLE, "", "", "", "", 0L, false);
	}

	public static PlayerStatus loading() {
		return new PlayerStatus(State.LOADING, "", "", "", "", 0L, false);
	}

	public static PlayerStatus online(String gameType, String mode, String map) {
		return new PlayerStatus(State.ONLINE, gameType, mode, map, "", 0L, false);
	}

	public static PlayerStatus offline() {
		return offline(0L);
	}

	/** {@code lastOnlineMs <= 0} means no reliable last-online time. */
	public static PlayerStatus offline(long lastOnlineMs) {
		return new PlayerStatus(State.OFFLINE, "", "", "", "", lastOnlineMs, false);
	}

	/** Hypixel reports offline because the player hid their online status, so they may be online. */
	public static PlayerStatus offlineHidden() {
		return new PlayerStatus(State.OFFLINE, "", "", "", "", 0L, true);
	}

	/** Offline status from the {@code /player} object, once {@code /status} has said offline. */
	public static PlayerStatus offlineFromPlayer(JsonObject player, long nowMs) {
		if (player != null && sessionHidden(player)) {
			return offlineHidden();
		}
		return offline(lastOnlineFromPlayer(player, nowMs));
	}

	public static PlayerStatus error(String message) {
		return new PlayerStatus(State.ERROR, "", "", "", message, 0L, false);
	}

	public State state() {
		return this.state;
	}

	public String gameType() {
		return this.gameType;
	}

	public String mode() {
		return this.mode;
	}

	public String map() {
		return this.map;
	}

	public String error() {
		return this.error;
	}

	public long lastOnlineMs() {
		return this.lastOnlineMs;
	}

	public boolean sessionHidden() {
		return this.sessionHidden;
	}

	public String buttonLabel() {
		return buttonLabel(System.currentTimeMillis());
	}

	public String buttonLabel(long nowMs) {
		return switch (this.state) {
			case IDLE -> "Status";
			case LOADING -> "...";
			case ONLINE -> location();
			case OFFLINE -> lastOnlineLabel(this.lastOnlineMs, nowMs);
			case ERROR -> "Status";
		};
	}

	public String location() {
		String loc = prettyLocation(this.map);
		if (loc.isBlank()) {
			loc = prettyLocation(this.mode);
		}
		if (loc.isBlank()) {
			loc = prettyLocation(this.gameType);
		}
		return loc.isBlank() ? LOCATION_HIDDEN : loc;
	}

	/**
	 * Latest time the player is known to have been on Hypixel, for a player {@code /status} reports offline.
	 * Usually {@code lastLogout}, but Hypixel sometimes never writes the logout, leaving {@code lastLogin}
	 * newer while the player is offline; the later of the two is still a real time they were online.
	 * Returns 0 when both are missing (hidden by the player, staff, very old accounts) or implausible.
	 */
	public static long lastOnlineFromPlayer(JsonObject player, long nowMs) {
		if (player == null) {
			return 0L;
		}
		long logout = plausible(normalizeTimestamp(readLong(player, "lastLogout")), nowMs);
		long login = plausible(normalizeTimestamp(readLong(player, "lastLogin")), nowMs);
		return Math.max(login, logout);
	}

	/** {@code settings.apiSession == false}: the player hid their online status, so offline may be wrong. */
	public static boolean sessionHidden(JsonObject player) {
		if (player == null || !(player.get("settings") instanceof JsonObject settings)) {
			return false;
		}
		JsonElement flag = settings.get("apiSession");
		return flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean() && !flag.getAsBoolean();
	}

	/** Raw last-online inputs for logs; all public Hypixel fields. */
	public static String timestampDiagnostics(JsonObject player) {
		if (player == null) {
			return "player=missing";
		}
		return "lastLogin=" + rawField(player, "lastLogin")
			+ " lastLogout=" + rawField(player, "lastLogout")
			+ " apiSession=" + (player.get("settings") instanceof JsonObject settings
				? rawField(settings, "apiSession") : "missing");
	}

	private static String rawField(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		return el == null || el.isJsonNull() ? "missing" : el.toString();
	}

	private static long plausible(long ms, long nowMs) {
		return ms > nowMs + FUTURE_TOLERANCE_MS ? 0L : ms;
	}

	/** Accepts epoch seconds or milliseconds; returns 0 for missing or pre-Hypixel values. */
	public static long normalizeTimestamp(long raw) {
		if (raw <= 0L) {
			return 0L;
		}
		long ms = raw < SECONDS_CUTOFF ? raw * 1000L : raw;
		return ms < MIN_TIMESTAMP_MS ? 0L : ms;
	}

	public static String lastOnlineLabel(long lastOnlineMs, long nowMs) {
		if (lastOnlineMs <= 0L) {
			return "Offline";
		}
		long ago = Math.max(0L, nowMs - lastOnlineMs);
		if (ago < MINUTE_MS) {
			return "Last online just now";
		}
		if (ago < HOUR_MS) {
			return "Last online " + plural(ago / MINUTE_MS, "minute") + " ago";
		}
		if (ago < DAY_MS) {
			return "Last online " + plural(ago / HOUR_MS, "hour") + " ago";
		}
		return "Last online " + plural(ago / DAY_MS, "day") + " ago";
	}

	private static String plural(long n, String unit) {
		return n + " " + unit + (n == 1 ? "" : "s");
	}

	private static long readLong(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
			return 0L;
		}
		try {
			return el.getAsLong();
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	public int buttonColor(int accent, int online, int offline, int muted) {
		return switch (this.state) {
			case ONLINE -> online;
			case OFFLINE -> offline;
			case LOADING -> muted;
			case ERROR -> muted;
			case IDLE -> accent;
		};
	}

	/** Hypixel {@code combat_3} / {@code dungeon_hub} → display name. */
	public static String prettyLocation(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		String mapped = LOCATION_NAMES.get(key);
		if (mapped != null) {
			return mapped;
		}
		String[] parts = key.split("_+");
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
}

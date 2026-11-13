/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject

class PlayerStatus private constructor(
    state: State?,
    gameType: String?,
    mode: String?,
    map: String?,
    error: String?,
    lastOnlineMs: Long,
    @get:JvmName("sessionHidden") val sessionHidden: Boolean,
) {
    enum class State {
        IDLE,
        LOADING,
        ONLINE,
        OFFLINE,
        ERROR,
    }

    @get:JvmName("state") val state: State = state ?: State.IDLE
    @get:JvmName("gameType") val gameType: String = gameType ?: ""
    @get:JvmName("mode") val mode: String = mode ?: ""
    @get:JvmName("map") val map: String = map ?: ""
    @get:JvmName("error") val error: String = error ?: ""
    @get:JvmName("lastOnlineMs") val lastOnlineMs: Long = maxOf(0L, lastOnlineMs)

    @JvmOverloads
    fun buttonLabel(nowMs: Long = System.currentTimeMillis()): String = when (state) {
        State.IDLE -> "Status"
        State.LOADING -> "..."
        State.ONLINE -> location()
        State.OFFLINE -> lastOnlineLabel(lastOnlineMs, nowMs)
        State.ERROR -> "Status"
    }

    fun location(): String =
        prettyLocation(map).ifBlank { prettyLocation(mode) }.ifBlank { prettyLocation(gameType) }.ifBlank { LOCATION_HIDDEN }

    fun buttonColor(accent: Int, online: Int, offline: Int, muted: Int): Int = when (state) {
        State.ONLINE -> online
        State.OFFLINE -> offline
        State.LOADING -> muted
        State.ERROR -> muted
        State.IDLE -> accent
    }

    companion object {
        private val LOCATION_NAMES = mapOf(
            "dynamic" to "Private Island",
            "hub" to "Hub",
            "farming_1" to "The Farming Islands",
            "combat_1" to "Spider's Den",
            "combat_2" to "Blazing Fortress",
            "combat_3" to "The End",
            "foraging_1" to "The Park",
            "foraging_2" to "Galatea",
            "mining_1" to "Gold Mine",
            "mining_2" to "Deep Caverns",
            "mining_3" to "Dwarven Mines",
            "crystal_hollows" to "Crystal Hollows",
            "mineshaft" to "Glacite Mineshafts",
            "crimson_isle" to "Crimson Isle",
            "fishing_1" to "Backwater Bayou",
            "dungeon" to "Dungeon",
            "dungeon_hub" to "Dungeon Hub",
            "dark_auction" to "Dark Auction",
            "winter" to "Jerry's Workshop",
            "rift" to "The Rift",
            "garden" to "The Garden",
            "kuudra" to "Kuudra",
            "instanced" to "Kuudra",
            "skyblock" to "SkyBlock",
            "bedwars" to "Bed Wars",
            "skywars" to "SkyWars",
            "murder_mystery" to "Murder Mystery",
            "build_battle" to "Build Battle",
            "housing" to "Housing",
            "arcade" to "Arcade",
            "survival_games" to "Blitz SG",
            "tntgames" to "TNT Games",
            "uhc" to "UHC",
            "speed_uhc" to "Speed UHC",
            "duels" to "Duels",
            "pit" to "The Pit",
            "replay" to "Replay",
            "wool_games" to "Wool Wars",
            "mcgo" to "Cops and Crims",
            "battleground" to "Warlords",
            "super_smash" to "Smash Heroes",
            "gingerbread" to "Turbo Kart Racers",
            "legacy" to "Classic Games",
            "prototype" to "Prototype",
            "walls3" to "Mega Walls",
            "smp" to "SMP",
        )
        private val UNDERSCORES = Regex("_+")

        const val LOCATION_HIDDEN = "Location hidden"
        private const val MIN_TIMESTAMP_MS = 1_356_998_400_000L
        private const val SECONDS_CUTOFF = 100_000_000_000L
        private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS

        @JvmStatic
        fun idle(): PlayerStatus = PlayerStatus(State.IDLE, "", "", "", "", 0L, false)

        @JvmStatic
        fun loading(): PlayerStatus = PlayerStatus(State.LOADING, "", "", "", "", 0L, false)

        @JvmStatic
        fun online(gameType: String?, mode: String?, map: String?): PlayerStatus =
            PlayerStatus(State.ONLINE, gameType, mode, map, "", 0L, false)

        @JvmStatic
        @JvmOverloads
        fun offline(lastOnlineMs: Long = 0L): PlayerStatus = PlayerStatus(State.OFFLINE, "", "", "", "", lastOnlineMs, false)

        /** Hypixel reports offline because the player hid their online status, so they may be online. */
        @JvmStatic
        fun offlineHidden(): PlayerStatus = PlayerStatus(State.OFFLINE, "", "", "", "", 0L, true)

        @JvmStatic
        fun offlineFromPlayer(player: JsonObject?, nowMs: Long): PlayerStatus {
            if (player != null && sessionHidden(player)) return offlineHidden()
            return offline(lastOnlineFromPlayer(player, nowMs))
        }

        @JvmStatic
        fun error(message: String?): PlayerStatus = PlayerStatus(State.ERROR, "", "", "", message, 0L, false)

        /**
         * Latest time the player is known to have been on Hypixel, for a player `/status` reports offline.
         * Usually `lastLogout`, but Hypixel sometimes never writes the logout, leaving `lastLogin`
         * newer while the player is offline; the later of the two is still a real time they were online.
         * Returns 0 when both are missing (hidden by the player, staff, very old accounts) or implausible.
         */
        @JvmStatic
        fun lastOnlineFromPlayer(player: JsonObject?, nowMs: Long): Long {
            if (player == null) return 0L
            val logout = plausible(normalizeTimestamp(readLong(player, "lastLogout")), nowMs)
            val login = plausible(normalizeTimestamp(readLong(player, "lastLogin")), nowMs)
            return maxOf(login, logout)
        }

        /** `settings.apiSession == false`: the player hid their online status, so offline may be wrong. */
        @JvmStatic
        fun sessionHidden(player: JsonObject?): Boolean {
            val settings = player?.get("settings") as? JsonObject ?: return false
            val flag = settings.get("apiSession")
            return flag != null && flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean && !flag.asBoolean
        }

        @JvmStatic
        fun timestampDiagnostics(player: JsonObject?): String {
            if (player == null) return "player=missing"
            val settings = player.get("settings") as? JsonObject
            return "lastLogin=" + rawField(player, "lastLogin") +
                " lastLogout=" + rawField(player, "lastLogout") +
                " apiSession=" + (if (settings != null) rawField(settings, "apiSession") else "missing")
        }

        private fun rawField(obj: JsonObject, key: String): String {
            val el = obj.get(key)
            return if (el == null || el.isJsonNull) "missing" else el.toString()
        }

        private fun plausible(ms: Long, nowMs: Long): Long = if (ms > nowMs + FUTURE_TOLERANCE_MS) 0L else ms

        @JvmStatic
        fun normalizeTimestamp(raw: Long): Long {
            if (raw <= 0L) return 0L
            val ms = if (raw < SECONDS_CUTOFF) raw * 1000L else raw
            return if (ms < MIN_TIMESTAMP_MS) 0L else ms
        }

        @JvmStatic
        fun lastOnlineLabel(lastOnlineMs: Long, nowMs: Long): String {
            if (lastOnlineMs <= 0L) return "Offline"
            val ago = maxOf(0L, nowMs - lastOnlineMs)
            return when {
                ago < MINUTE_MS -> "Last online just now"
                ago < HOUR_MS -> "Last online " + plural(ago / MINUTE_MS, "minute") + " ago"
                ago < DAY_MS -> "Last online " + plural(ago / HOUR_MS, "hour") + " ago"
                else -> "Last online " + plural(ago / DAY_MS, "day") + " ago"
            }
        }

        private fun plural(n: Long, unit: String): String = "$n $unit" + if (n == 1L) "" else "s"

        private fun readLong(obj: JsonObject, key: String): Long {
            val el = obj.get(key)
            if (el == null || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) return 0L
            return try {
                el.asLong
            } catch (_: NumberFormatException) {
                0L
            }
        }

        @JvmStatic
        fun prettyLocation(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val key = raw.trim().lowercase().replace('-', '_')
            LOCATION_NAMES[key]?.let { return it }
            return key.split(UNDERSCORES).filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    }
}

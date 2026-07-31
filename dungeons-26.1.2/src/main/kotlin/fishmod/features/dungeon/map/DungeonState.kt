package fishmod.features.dungeon.map

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import java.util.regex.Pattern

/** Dungeon lifecycle + floor tracking, ported from System22's DungeonState/DoorEsp chat trackers. */
object DungeonState {

    private val SIDEBAR_FLOOR = Pattern.compile("The Catacombs \\(([FM])(\\d)\\)")
    private val ENTERED_FLOOR = Pattern.compile("entered The Catacombs, Floor (\\w+)!")

    private var chatFloor = -1
    private var inBoss = false
    private var seenDungeonStart = false
    private var dungeonEnded = false

    private val BOSS_ENTRY = arrayOf(
        "[BOSS] Bonzo: Alright, maybe I'm just weak after all..",
        "[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
        "[BOSS] The Professor: I was burdened with terrible news recently...",
        "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!",
        "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.",
        "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!",
        "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"
    )
    private const val DUNGEON_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
    private val DUNGEON_END = setOf(
        "                        The Catacombs - Entrance",
        "                         The Catacombs - Floor I",
        "                         The Catacombs - Floor II",
        "                        The Catacombs - Floor III",
        "                        The Catacombs - Floor IV",
        "                         The Catacombs - Floor V",
        "                        The Catacombs - Floor VI",
        "                        The Catacombs - Floor VII",
        "                 Master Mode The Catacombs - Floor I",
        "                Master Mode The Catacombs - Floor II",
        "                Master Mode The Catacombs - Floor III",
        "                Master Mode The Catacombs - Floor IV",
        "                 Master Mode The Catacombs - Floor V",
        "                Master Mode The Catacombs - Floor VI",
        "                Master Mode The Catacombs - Floor VII"
    )

    // DoorEsp.java's blood-key chat tracking (rendering itself excluded, this half is kept).
    private val WITHER_KEY_CLAIM = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Wither Key!")
    private val WITHER_DOOR_OPEN = Pattern.compile("([A-Za-z0-9_]+) opened a WITHER door!")
    private val BLOOD_KEY_CLAIM = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Blood Key!")

    private var witherKeys = 0
    private var bloodKey = false
    @JvmStatic
    var bloodOpened = false
        private set

    @JvmStatic
    fun isInBoss() = inBoss

    @JvmStatic
    fun seenDungeonStart() = seenDungeonStart

    @JvmStatic
    fun dungeonEnded() = dungeonEnded

    @JvmStatic
    fun hasWitherKey() = witherKeys > 0

    @JvmStatic
    fun hasBloodKey() = bloodKey

    @JvmStatic
    fun onChatMessage(msg: String?) {
        if (msg == null) return

        val fm = ENTERED_FLOOR.matcher(msg)
        if (fm.find()) {
            val f = parseRomanFloor(fm.group(1))
            if (f >= 0) chatFloor = f
        }

        for (b in BOSS_ENTRY) {
            if (msg == b) {
                inBoss = true
                return
            }
        }

        if (msg in DUNGEON_END) {
            dungeonEnded = true
        } else if (msg == DUNGEON_START) {
            seenDungeonStart = true
        }

        val s = stripColors(msg)
        if (WITHER_KEY_CLAIM.matcher(s).matches() || s == "A Wither Key was picked up!") {
            witherKeys++
        } else if (WITHER_DOOR_OPEN.matcher(s).matches()) {
            witherKeys--
        } else if (BLOOD_KEY_CLAIM.matcher(s).matches() || s == "A Blood Key was picked up!") {
            bloodKey = true
        } else if (s == "The BLOOD DOOR has been opened!") {
            bloodKey = false
            bloodOpened = true
        }
    }

    private fun stripColors(s: String): String = s.replace(Regex("(?i)[&§][0-9a-fk-or]"), "")

    private fun parseRomanFloor(s0: String?): Int {
        if (s0 == null) return -1
        val s = s0.trim()
        if (s.equals("Entrance", ignoreCase = true)) return 0
        return when (s.uppercase()) {
            "I" -> 1
            "II" -> 2
            "III" -> 3
            "IV" -> 4
            "V" -> 5
            "VI" -> 6
            "VII" -> 7
            else -> try {
                s.toInt()
            } catch (e: Exception) {
                -1
            }
        }
    }

    @JvmStatic
    fun reset() {
        inBoss = false
        seenDungeonStart = false
        dungeonEnded = false
        chatFloor = -1
        witherKeys = 0
        bloodKey = false
        bloodOpened = false
    }

    @JvmStatic
    fun isInDungeon(): Boolean {
        try {
            val mc = Minecraft.getInstance()
            val conn = mc.connection
            if (mc.player == null || conn == null) return false

            for (info in conn.onlinePlayers) {
                val display = info.tabListDisplayName?.string ?: info.profile.name
                if (stripColors(display).contains("Dungeon: Catacombs")) return true
            }
        } catch (e: Exception) {
        }

        return sidebarFloorNumber() >= 0
    }

    @JvmStatic
    fun floorNumber(): Int = if (chatFloor >= 0) chatFloor else sidebarFloorNumber()

    private fun sidebarFloorNumber(): Int {
        try {
            val level = Minecraft.getInstance().level ?: return -1
            val sb = level.scoreboard
            val sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return -1
            for (entry in sb.listPlayerScores(sidebar)) {
                val owner = entry.owner()
                val team = sb.getPlayersTeam(owner)
                val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else owner
                val line = stripColors(raw)
                val m = SIDEBAR_FLOOR.matcher(line)
                if (m.find()) {
                    val f = m.group(2)
                    if (f == "E") return 0
                    return try {
                        f.toInt()
                    } catch (e: NumberFormatException) {
                        1
                    }
                }
            }
            return -1
        } catch (e: Exception) {
            return -1
        }
    }

    @JvmStatic
    fun isMasterMode(): Boolean {
        try {
            val level = Minecraft.getInstance().level ?: return false
            val sb = level.scoreboard
            val sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return false
            for (entry in sb.listPlayerScores(sidebar)) {
                val team = sb.getPlayersTeam(entry.owner()) ?: continue
                val line = stripColors(team.playerPrefix.string + team.playerSuffix.string)
                val m = SIDEBAR_FLOOR.matcher(line)
                if (m.find()) return m.group(1) == "M"
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
}

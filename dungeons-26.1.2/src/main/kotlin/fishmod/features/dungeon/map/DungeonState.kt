package fishmod.features.dungeon.map

import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import net.minecraft.world.scores.DisplaySlot
import java.util.regex.Pattern

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
            witherKeys = maxOf(0, witherKeys - 1)
        } else if (BLOOD_KEY_CLAIM.matcher(s).matches() || s == "A Blood Key was picked up!") {
            bloodKey = true
        } else if (s == "The BLOOD DOOR has been opened!") {
            bloodKey = false
            bloodOpened = true
        }
    }

    private fun stripColors(s: String): String = s.replace(MAP_COLOR_CODES, "")

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
        inDungeonCacheLevel = null
    }

    private var inDungeonCacheLevel: Level? = null
    private var inDungeonCacheStamp = 0L
    private var inDungeonCacheValue = false
    private const val IN_DUNGEON_TTL_MS = 1000L

    @JvmStatic
    fun isInDungeon(): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val now = System.currentTimeMillis()
        if (level === inDungeonCacheLevel && now - inDungeonCacheStamp < IN_DUNGEON_TTL_MS) return inDungeonCacheValue
        inDungeonCacheLevel = level
        inDungeonCacheStamp = now
        inDungeonCacheValue = computeInDungeon(mc)
        return inDungeonCacheValue
    }

    private fun computeInDungeon(mc: Minecraft): Boolean {
        try {
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

    @JvmStatic
    fun currentFloorKey(): String? {
        val f = floorNumber()
        if (f < 0) return null
        if (f == 0) return "E"
        return (if (isMasterMode()) "M" else "F") + f
    }

    private var sidebarLevel: Any? = null
    private var sidebarAt = 0L
    private var sidebarFloor = -1
    private var sidebarMaster = false

    private fun refreshSidebar() {
        val level = Minecraft.getInstance().level
        val now = System.currentTimeMillis()
        if (level === sidebarLevel && now - sidebarAt < 1000L) return
        sidebarLevel = level
        sidebarAt = now
        sidebarFloor = -1
        sidebarMaster = false
        if (level == null) return
        val sb = level.scoreboard
        val sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return
        for (entry in sb.listPlayerScores(sidebar)) {
            val owner = entry.owner()
            val team = sb.getPlayersTeam(owner)
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else owner
            val m = SIDEBAR_FLOOR.matcher(stripColors(raw))
            if (m.find()) {
                val f = m.group(2)
                sidebarFloor = if (f == "E") 0 else f.toIntOrNull() ?: 1
                sidebarMaster = team != null && m.group(1) == "M"
                return
            }
        }
    }

    private fun sidebarFloorNumber(): Int {
        refreshSidebar()
        return sidebarFloor
    }

    @JvmStatic
    fun isMasterMode(): Boolean {
        refreshSidebar()
        return sidebarMaster
    }
}

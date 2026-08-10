package fishmod.features.dungeon.map

import fishmod.utils.MayorApi
import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import java.util.regex.Pattern

/** Port of System22's map-package DungeonScore. Distinct from fishmod.features.dungeon.DungeonScore (unrelated existing feature). */
object DungeonScore {

    @JvmStatic var secretsFound = 0
    @JvmStatic var secretsPercent = 0.0f
    @JvmStatic var crypts = 0
    @JvmStatic var completedRooms = 0
    @JvmStatic var deaths = 0
    @JvmStatic var percentCleared = 0
    @JvmStatic var puzzleCount = 0
    @JvmStatic var puzzlesCompleted = 0
    @JvmStatic var mimicKilled = false
    @JvmStatic var princeKilled = false
    @JvmStatic var elapsedTime = "0s"
    @JvmStatic var score = 0
    @JvmStatic var paul = false

    private val SECRET_PERCENT = Pattern.compile("^ ?Secrets Found: ([\\d.]+)%$")
    private val SECRET_COUNT = Pattern.compile("^ ?Secrets Found: (\\d+)$")
    private val COMPLETED_ROOMS = Pattern.compile("^ ?Completed Rooms: (\\d+)$")
    private val CRYPTS = Pattern.compile("^ ?Crypts: (\\d+)$")
    private val DEATHS = Pattern.compile("^ ?Team Deaths: (\\d+)$")
    private val PUZZLE_COUNT = Pattern.compile("^ ?Puzzles: \\((\\d+)\\)$")
    private val CLEARED = Pattern.compile("^ ?Cleared: (\\d+)% ?\\(\\d+\\)$")
    private val RUN_TIME = Pattern.compile("^ ?Time: ((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$")
    private val PUZZLE_LINE = Pattern.compile("^ ?(?:\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])]")
    private val PRINCE_FALLS = Pattern.compile("^A Prince falls\\. \\+1 Bonus Score$")

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    private fun tick(mc: Minecraft) {
        if (mc.player == null) return
        if (!DungeonState.isInDungeon()) return
        val st = DungeonMapSettings
        if (st.mapEnabled || st.mapInfoEnabled == true || st.mapScoreMessages) {
            parseTab(mc)
            parseSidebar(mc)
            DungeonPlayers.updateRoster(mc)
            score = calculateScore()
            ScoreMessages.update(mc, score)
            paul = MayorApi.isPaulDungeonBonusActive()
        }
    }

    @JvmStatic
    fun reset() {
        secretsFound = 0
        secretsPercent = 0.0f
        crypts = 0
        completedRooms = 0
        deaths = 0
        percentCleared = 0
        puzzleCount = 0
        puzzlesCompleted = 0
        mimicKilled = false
        princeKilled = false
        elapsedTime = "0s"
        score = 0
        ScoreMessages.reset()
    }

    @JvmStatic
    fun onChatMessage(msg: String?) {
        if (msg == null) return
        val clean = stripColors(msg)
        if (PRINCE_FALLS.matcher(clean).matches()) princeKilled = true

        val lower = clean.lowercase()
        if (lower.contains("prince killed")) princeKilled = true
        if (lower.contains("mimic dead") || lower.contains("mimic killed")) mimicKilled = true
    }

    private fun stripColors(s: String): String = s.replace(Regex("(?i)[&§][0-9a-fk-or]"), "")

    private fun parseTab(mc: Minecraft) {
        val conn = mc.connection ?: return
        var completedPuzzles = 0

        for (info in conn.onlinePlayers) {
            val disp = info.tabListDisplayName ?: continue
            val line = stripColors(disp.string)
            var m = SECRET_PERCENT.matcher(line)
            if (m.find()) {
                try {
                    secretsPercent = m.group(1).toFloat()
                } catch (e: Exception) {
                }
            } else {
                m = SECRET_COUNT.matcher(line)
                if (m.find()) secretsFound = parseInt(m.group(1), secretsFound)
            }

            m = COMPLETED_ROOMS.matcher(line)
            if (m.find()) completedRooms = parseInt(m.group(1), completedRooms)

            m = CRYPTS.matcher(line)
            if (m.find()) crypts = parseInt(m.group(1), crypts)

            m = DEATHS.matcher(line)
            if (m.find()) deaths = parseInt(m.group(1), deaths)

            m = PUZZLE_COUNT.matcher(line)
            if (m.find()) puzzleCount = parseInt(m.group(1), puzzleCount)

            m = CLEARED.matcher(line)
            if (m.find()) percentCleared = parseInt(m.group(1), percentCleared)

            m = RUN_TIME.matcher(line)
            if (m.find()) elapsedTime = m.group(1)

            m = PUZZLE_LINE.matcher(line)
            if (m.find() && m.group(1) == "✔") completedPuzzles++
        }

        puzzlesCompleted = completedPuzzles
    }

    private fun parseSidebar(mc: Minecraft) {
        val level = mc.level ?: return
        val sb = level.scoreboard
        val sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return
        for (entry in sb.listPlayerScores(sidebar)) {
            val owner = entry.owner()
            val team = sb.getPlayersTeam(owner)
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else owner
            val line = stripColors(raw)
            val m = CLEARED.matcher(line)
            if (m.find()) percentCleared = parseInt(m.group(1), percentCleared)
        }
    }

    private fun parseInt(s: String, fallback: Int): Int {
        return try {
            s.toInt()
        } catch (e: Exception) {
            fallback
        }
    }

    private fun secretFactor(): Float {
        if (DungeonState.isMasterMode()) return 1.0f
        return when (DungeonState.floorNumber()) {
            0, 1 -> 0.3f
            2 -> 0.4f
            3 -> 0.5f
            4 -> 0.6f
            5 -> 0.7f
            6 -> 0.85f
            else -> 1.0f
        }
    }

    @JvmStatic
    fun isBloodDone(): Boolean {
        val b = Scan.blood
        return b != null && b.state == Room.State.GREEN
    }

    @JvmStatic
    fun calculateTotalSecrets(): Int {
        return if (Scan.loadedAllRooms && !MapColors.legit()) {
            Scan.allSecrets
        } else {
            if (secretsFound != 0 && secretsPercent != 0.0f)
                Math.floor((100.0f / secretsPercent * secretsFound).toDouble() + 0.5).toInt()
            else 0
        }
    }

    @JvmStatic
    fun calculateTotalRooms(): Int {
        return if (completedRooms != 0 && percentCleared != 0)
            Math.floor(completedRooms / (percentCleared * 0.01) + 0.4).toInt()
        else 0
    }

    @JvmStatic
    fun calculateMaxBonusScore(): Int {
        val prince = if (MapColors.legit()) Prince.legitPrince else Prince.cheaterPrince
        return 5 + (if (prince) 1 else 0) + (if (DungeonState.floorNumber() >= 6) 2 else 0)
    }

    @JvmStatic
    fun calculatePaulScore(): Int {
        val mode = DungeonMapSettings.mapScorePaul
        val active = (mode == 0 && paul) || mode == 1
        return if (active) 10 else 0
    }

    @JvmStatic
    fun calculateBonusScoreNoPaul(): Int {
        return (if (mimicKilled) 2 else 0) + (if (princeKilled) 1 else 0) + Math.min(crypts, 5)
    }

    @JvmStatic
    fun calculateBonusScore(): Int = calculateBonusScoreNoPaul() + calculatePaulScore()

    @JvmStatic
    fun calculateScore(): Int {
        val inBoss = DungeonState.isInBoss()
        val totalRooms = calculateTotalRooms()
        val completed = completedRooms + (if (!isBloodDone()) 1 else 0) + (if (!inBoss) 1 else 0)
        val total = if (totalRooms != 0) totalRooms else 36
        val exploration = clampInt(Math.floor((secretsPercent / secretFactor() / 100.0f * 40.0f).toDouble()).toInt(), 0, 40) +
            clampInt(Math.floor((completed.toFloat() / total * 60.0f).toDouble()).toInt(), 0, 60)
        val skillRooms = clampInt(Math.floor((completed.toFloat() / total * 80.0f).toDouble()).toInt(), 0, 80)
        val puzzlePenalty = (puzzleCount - puzzlesCompleted) * 10
        val skill = clampInt(20 + skillRooms - puzzlePenalty - Math.max(deaths * 2 - 1, 0), 20, 100)
        return exploration + skill + 100 + calculateBonusScore()
    }

    @JvmStatic
    fun calculateMinimumSecrets(forceNoMaxBonus: Boolean, forceNeeded: Boolean): Int {
        val bonus = (if (!forceNoMaxBonus && DungeonMapSettings.mapScoreMaxBonusMissing) calculateMaxBonusScore() else calculateBonusScoreNoPaul()) + calculatePaulScore()
        val found = if (!forceNeeded && DungeonMapSettings.mapScoreNeededInsteadOfMissing) 0 else secretsFound
        val need = Math.ceil((calculateTotalSecrets() * secretFactor() * (40 - bonus + Math.max(deaths * 2 - 1, 0)) / 40.0f).toDouble()).toInt() - found
        return Math.max(need, 0)
    }

    private fun clampInt(v: Int, lo: Int, hi: Int): Int = Math.max(lo, Math.min(hi, v))
}

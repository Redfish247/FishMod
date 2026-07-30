package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import java.util.regex.Pattern

/** Live S+ tracker using Odin's exact scoring formula. */
object DungeonScore {

    // ── Floor + required-secret-percent table (from Odin DungeonEnums.Floor) ──
    private enum class Floor(val requiredPercentage: Float) {
        E(0.3f), F1(0.3f), F2(0.4f), F3(0.5f), F4(0.6f), F5(0.7f), F6(0.85f), F7(1f),
        M1(1f), M2(1f), M3(1f), M4(1f), M5(1f), M6(1f), M7(1f);

        fun floorNumber(): Int = when (this) {
            E -> 0
            F1, M1 -> 1
            F2, M2 -> 2
            F3, M3 -> 3
            F4, M4 -> 4
            F5, M5 -> 5
            F6, M6 -> 6
            F7, M7 -> 7
        }
    }

    // ── Tab list regexes (port of Odin's DungeonListener) ──
    private val SECRET_PCT_PAT = Pattern.compile("^\\s*Secrets Found:\\s*([\\d.]+)%$")
    private val SECRET_COUNT_PAT = Pattern.compile("^\\s*Secrets Found:\\s*(\\d+)$")
    private val COMPLETED_ROOMS_PAT = Pattern.compile("^\\s*Completed Rooms:\\s*(\\d+)$")
    private val PUZZLE_COUNT_PAT = Pattern.compile("^Puzzles:\\s*\\((\\d+)\\)$")
    private val PUZZLE_STATUS_PAT = Pattern.compile("^\\s*([\\w?]+(?: \\w+)*|\\?\\?\\?):\\s*\\[([✖✔✦])\\]")
    private val DEATHS_PAT = Pattern.compile("^Team Deaths:\\s*(\\d+)$")
    private val CRYPTS_PAT = Pattern.compile("^\\s*Crypts:\\s*(\\d+)$")
    private val TIME_PAT = Pattern.compile("^\\s*Time:\\s*((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$")
    private val CLEARED_SIDEBAR_PAT = Pattern.compile("^Cleared:\\s*(\\d+)% \\(\\d+\\)$")
    private val FLOOR_PAT = Pattern.compile("The Catacombs \\((\\w+)\\)")

    // ── Chat regexes ──
    private val DEATH_CHAT = Pattern.compile("☠ \\S+ (?:was|were) killed by|☠ \\S+ (?:died|quit)|and became a ghost")
    private val EXPECTING_BLOOD = Pattern.compile("^\\[BOSS\\] The Watcher: You have proven yourself")
    private val PARTY_MSG = Pattern.compile("^Party > .*?: (.+)$")
    private val MIMIC_CHAT = Pattern.compile("(?i)mimic (?:killed|slain|dead)|killed a mimic|\\\$skytils-dungeon-score-mimic\\\$")
    private val PRINCE_CHAT = Pattern.compile("(?i)prince (?:killed|slain|dead)|killed the prince|\\\$skytils-dungeon-score-prince\\\$")

    // ── State ──
    private var currentFloor: Floor? = null
    private var secretCount = 0
    private var secretsPercent = 0f
    private var completedRooms = 0
    private var percentCleared = 0
    private var puzzleCount = 0
    private var puzzlesCompleted = 0
    private var deathCount = 0
    private var cryptCount = 0
    private var mimicKilled = false
    private var princeKilled = false
    private var bloodDone = false
    private var expectingBloodUpdate = false
    private var runStartMs: Long = -1
    private var alerted270 = false
    private var alerted300 = false
    private var alertedMissing = false

    /** Total puzzle count for this run, parsed from the tab list's "Puzzles: (N)" line. 0 if not seen yet. */
    @JvmStatic
    fun getPuzzleCount(): Int = puzzleCount

    /** Secrets found so far this run, from the tab list's secret-percent/count lines. */
    @JvmStatic
    fun getSecretCount(): Int = secretCount

    /** Estimated total secrets in the dungeon (back-computed from found-count and percent). 0 if not known yet. */
    @JvmStatic
    fun getTotalSecrets(): Int = totalSecrets()

    /** Crypts opened this run, from the tab list's "Crypts: (N)" line. */
    @JvmStatic
    fun getCryptCount(): Int = cryptCount

    @JvmStatic
    fun isMimicKilled(): Boolean = mimicKilled

    @JvmStatic
    fun isPrinceKilled(): Boolean = princeKilled

    /** Team deaths this run, from the tab list's "Team Deaths: (N)" line. */
    @JvmStatic
    fun getDeathCount(): Int = deathCount

    @JvmStatic
    fun init() {
        FishHudEditor.register("Dungeon Score",
            { FishSettings.dungeonScoreHudX }, { v -> FishSettings.dungeonScoreHudX = v },
            { FishSettings.dungeonScoreHudY }, { v -> FishSettings.dungeonScoreHudY = v },
            160, 14 * 3,
            { FishSettings.dungeonScoreScale }, { v -> FishSettings.dungeonScoreScale = v },
            {
                FishSettings.dungeonScoreEnabled
                    && Location.getCurrentLocation() == Location.DUNGEON
                    && !fishmod.utils.dungeon.Phase.inBoss()
            })

        Events.ON_LOCATION_CHANGE.register { loc ->
            if (loc == Location.DUNGEON) {
                resetRun()
                runStartMs = System.currentTimeMillis()
            }
            false
        }

        Events.ON_GAME_MESSAGE.register { message ->
            if (!FishSettings.dungeonScoreEnabled) return@register false
            val s = message.string.replace(Regex("§."), "")
            if (DEATH_CHAT.matcher(s).find()) deathCount++
            if (EXPECTING_BLOOD.matcher(s).find()) expectingBloodUpdate = true
            val fm = FLOOR_PAT.matcher(s)
            if (fm.find()) {
                try {
                    currentFloor = Floor.valueOf(fm.group(1))
                } catch (ignored: IllegalArgumentException) {
                }
            }
            val pm = PARTY_MSG.matcher(s)
            if (pm.find()) {
                val body = pm.group(1).lowercase()
                if (MIMIC_CHAT.matcher(body).find() && currentFloor != null && (currentFloor!!.floorNumber() == 6 || currentFloor!!.floorNumber() == 7)) mimicKilled = true
                if (PRINCE_CHAT.matcher(body).find()) princeKilled = true
            } else {
                // Local "Mimic dead!" type messages
                if (MIMIC_CHAT.matcher(s).find() && currentFloor != null && (currentFloor!!.floorNumber() == 6 || currentFloor!!.floorNumber() == 7)) mimicKilled = true
                if (PRINCE_CHAT.matcher(s).find()) princeKilled = true
            }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (!FishSettings.dungeonScoreEnabled) return@EndTick
            if (client.player == null || client.world == null) return@EndTick
            if (Location.getCurrentLocation() != Location.DUNGEON) return@EndTick
            scanTick++
            if (scanTick < 10) return@EndTick
            scanTick = 0
            scanTabList(client)
            scanSidebar(client)
            checkScoreAlerts()

            if (!alertedMissing && FishSettings.dungeonScoreMissingMsg
                && runStartMs > 0 && System.currentTimeMillis() - runStartMs >= 60_000
                && !fishmod.utils.dungeon.Phase.inBoss()
            ) {
                alertedMissing = true
                sendMissingScoreMessage()
            }
        })
    }

    private var scanTick = 0
    private val puzzleStatuses = HashMap<String, String>()

    private fun resetRun() {
        currentFloor = null
        secretCount = 0
        secretsPercent = 0f
        completedRooms = 0
        percentCleared = 0
        puzzleCount = 0
        puzzlesCompleted = 0
        deathCount = 0
        cryptCount = 0
        mimicKilled = false
        princeKilled = false
        bloodDone = false
        expectingBloodUpdate = false
        runStartMs = -1
        alerted270 = false
        alerted300 = false
        alertedMissing = false
        puzzleStatuses.clear()
    }

    private fun scanTabList(mc: MinecraftClient) {
        if (mc.networkHandler == null) return
        var newPuzzlesCompleted = 0
        for (entry in mc.networkHandler!!.playerList) {
            if (entry.displayName == null) continue
            val line = entry.displayName!!.string.replace(Regex("§."), "")

            var m = SECRET_PCT_PAT.matcher(line)
            if (m.find()) try { secretsPercent = m.group(1).toFloat() } catch (ignored: NumberFormatException) {}
            m = SECRET_COUNT_PAT.matcher(line)
            if (m.find()) try { secretCount = m.group(1).toInt() } catch (ignored: NumberFormatException) {}
            m = COMPLETED_ROOMS_PAT.matcher(line)
            if (m.find()) try { completedRooms = m.group(1).toInt() } catch (ignored: NumberFormatException) {}
            m = PUZZLE_COUNT_PAT.matcher(line)
            if (m.find()) try { puzzleCount = m.group(1).toInt() } catch (ignored: NumberFormatException) {}
            m = DEATHS_PAT.matcher(line)
            if (m.find()) try { deathCount = m.group(1).toInt() } catch (ignored: NumberFormatException) {}
            m = CRYPTS_PAT.matcher(line)
            if (m.find()) try { cryptCount = m.group(1).toInt() } catch (ignored: NumberFormatException) {}

            m = PUZZLE_STATUS_PAT.matcher(line)
            if (m.find() && m.group(1) != "???") {
                val status = m.group(2)
                if (status == "✔") newPuzzlesCompleted++
            }
        }
        puzzlesCompleted = newPuzzlesCompleted
    }

    private fun scanSidebar(mc: MinecraftClient) {
        val sb = mc.world!!.scoreboard
        val sidebar = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) ?: return
        for (entry in sb.getScoreboardEntries(sidebar)) {
            val team = sb.getScoreHolderTeam(entry.owner())
            val raw = if (team != null)
                team.prefix.string + entry.owner() + team.suffix.string
            else
                entry.name().string
            val line = raw.replace(Regex("§."), "").replace(Regex("[^\\x20-\\x7E%()]"), "").trim()
            var m = CLEARED_SIDEBAR_PAT.matcher(line)
            if (m.find()) {
                try {
                    val newPct = m.group(1).toInt()
                    if (percentCleared != newPct && expectingBloodUpdate) {
                        bloodDone = true
                        expectingBloodUpdate = false
                    }
                    percentCleared = newPct
                } catch (ignored: NumberFormatException) {
                }
            }
            m = FLOOR_PAT.matcher(line)
            if (m.find()) {
                try {
                    currentFloor = Floor.valueOf(m.group(1))
                } catch (ignored: IllegalArgumentException) {
                }
            }
        }
    }

    /** Odin's formula: score = exploration + skill(20..100) + 100 + bonus. */
    private fun totalSecrets(): Int {
        if (secretCount == 0 || secretsPercent == 0f) return 0
        return Math.floor((100f / secretsPercent * secretCount + 0.5).toDouble()).toInt()
    }

    private fun totalRooms(): Int {
        if (completedRooms == 0 || percentCleared == 0) return 0
        return Math.floor(((completedRooms / (percentCleared * 0.01f)) + 0.4f).toDouble()).toInt()
    }

    private fun getBonusScore(): Int {
        var s = Math.min(cryptCount, 5)
        if (mimicKilled) s += 2
        if (princeKilled) s += 1
        if (fishmod.utils.MayorApi.isPaulDungeonBonusActive()) s += 10
        return s
    }

    private fun inBoss(): Int = 0 // approximate: treat as not-in-boss until phase tracking added

    @JvmStatic
    fun computeScore(): Int {
        if (currentFloor == null) return 0
        val total = if (totalRooms() != 0) totalRooms() else 36
        val completed = completedRooms + (if (bloodDone) 0 else 1) + (if (inBoss() == 1) 0 else 1)

        val ts = totalSecrets()
        var secretScore = 0
        if (ts > 0) {
            secretScore = Math.floor(secretCount / (ts * currentFloor!!.requiredPercentage.toDouble()) * 40.0).toInt()
            secretScore = Math.max(0, Math.min(40, secretScore))
        }
        val roomScore = Math.max(0, Math.min(60, Math.floor(completed / total.toFloat() * 60f.toDouble()).toInt()))
        val exploration = secretScore + roomScore

        val skillRooms = Math.max(0, Math.min(80, Math.floor((completed / total.toFloat() * 80f).toDouble()).toInt()))
        val puzzlePenalty = (puzzleCount - puzzlesCompleted) * 10
        val deathPenalty = Math.max(0, deathCount * 2 - 1)
        val skill = Math.max(20, Math.min(100, 20 + skillRooms - puzzlePenalty - deathPenalty))

        val bonus = getBonusScore()
        return exploration + skill + 100 + bonus
    }

    /** Projected end-of-run score assuming a full clear (all rooms, all puzzles) with current secrets/bonuses. */
    private fun projectedFullClearScore(): Int {
        val ts = totalSecrets()
        val reqPct = if (currentFloor != null) currentFloor!!.requiredPercentage.toDouble() else 1.0
        var secretScore = 0
        if (ts > 0) secretScore = Math.max(0, Math.min(40, Math.floor(secretCount / (ts * reqPct) * 40.0).toInt()))
        val deathPenalty = Math.max(0, deathCount * 2 - 1)
        val skill = Math.max(20, Math.min(100, 100 - deathPenalty))
        return 60 + secretScore + skill + 100 + getBonusScore()
    }

    private fun sendMissingScoreMessage() {
        val ts = totalSecrets()
        val reqPct = if (currentFloor != null) currentFloor!!.requiredPercentage.toDouble() else 1.0
        val projected = projectedFullClearScore()
        val missing = 300 - projected

        val mc = MinecraftClient.getInstance()
        if (mc.networkHandler == null) return

        if (missing <= 0) {
            mc.networkHandler!!.sendChatCommand("pc On pace for 300! (projected $projected on full clear)")
            return
        }

        val parts = ArrayList<String>()
        var remaining = missing

        if (!princeKilled && remaining > 0) {
            parts.add("1 Prince [1 score max 1]")
            remaining -= 1
        }
        val cryptsAvail = 5 - Math.min(cryptCount, 5)
        if (cryptsAvail > 0 && remaining > 0) {
            val take = Math.min(cryptsAvail, remaining)
            parts.add(take.toString() + " Crypt" + (if (take == 1) "" else "s") + " [1 each max 5]")
            remaining -= take
        }
        val mimicFloor = currentFloor != null && (currentFloor!!.floorNumber() == 6 || currentFloor!!.floorNumber() == 7)
        if (!mimicKilled && mimicFloor && remaining > 0) {
            parts.add("1 Mimic [2 score max 1]")
            remaining -= 2
        }
        if (remaining > 0) {
            if (ts > 0) {
                val perSecret = 40.0 / (ts * reqPct)
                val need = Math.ceil(remaining / perSecret).toInt()
                parts.add(need.toString() + " Secret" + (if (need == 1) "" else "s") + " [$remaining score]")
            } else {
                parts.add("Secrets [$remaining score]")
            }
        }

        mc.networkHandler!!.sendChatCommand("pc $missing Score Missing (" + java.lang.String.join(", ", parts) + ")")
    }

    /** Fires the customizable 270/300 alerts once per run each. Jumping straight past 270 skips its alert. */
    private fun checkScoreAlerts() {
        val score = computeScore()
        if (!alerted270 && score >= 270 && score < 300) {
            alerted270 = true
            fireScoreAlert(FishSettings.score270TitleEnabled, FishSettings.score270ChatEnabled, FishSettings.score270Text)
        }
        if (!alerted300 && score >= 300) {
            alerted270 = true
            alerted300 = true
            fireScoreAlert(FishSettings.score300TitleEnabled, FishSettings.score300ChatEnabled, FishSettings.score300Text)
        }
    }

    private fun fireScoreAlert(title: Boolean, chat: Boolean, text: String) {
        val msg = net.minecraft.text.Text.literal(text.replace('&', '§'))
        if (title) fishmod.utils.Misc.setTitle(msg)
        if (chat) fishmod.utils.Misc.addChatMessage(msg)
    }

    private fun colorizeScore(s: Int): String {
        if (s < 270) return "§c$s"
        if (s < 300) return "§e$s"
        return "§a$s"
    }

    private fun colorizeCrypts(c: Int): String {
        if (c < 3) return "§c$c"
        if (c < 5) return "§e$c"
        return "§a$c"
    }

    private fun colorizeDeaths(d: Int): String {
        val floor = if (currentFloor != null) currentFloor!!.floorNumber() else 7
        if (d == 0) return "§a0"
        if (d <= (if (floor < 6) 2 else 3)) return "§e$d"
        if (d == (if (floor < 6) 3 else 4)) return "§c$d"
        return "§4$d"
    }

    private fun grade(s: Int): String {
        if (s >= 300) return "§a§lS+"
        if (s >= 270) return "§eS"
        if (s >= 230) return "§6A"
        if (s >= 160) return "§cB"
        if (s >= 100) return "§4C"
        return "§4D"
    }

    @JvmStatic
    fun renderHud(ctx: DrawContext, tick: RenderTickCounter) {
        if (!FishSettings.dungeonScoreEnabled) return
        val mc = MinecraftClient.getInstance()
        if (mc.player == null) return
        if (mc.currentScreen != null && mc.currentScreen !is net.minecraft.client.gui.screen.ChatScreen) return
        if (Location.getCurrentLocation() != Location.DUNGEON) return
        if (fishmod.utils.dungeon.Phase.inBoss()) return

        val score = computeScore()
        val ts = totalSecrets()
        val needed = if (currentFloor != null && ts > 0)
            Math.max(0, Math.ceil(ts * currentFloor!!.requiredPercentage.toDouble()).toInt())
        else 0

        val tail = if (FishSettings.dungeonScoreShowLeft)
            "§7-§c" + Math.max(0, 300 - projectedFullClearScore()) + "§7 left"
        else
            "§7-§c" + (if (ts > 0) ts.toString() else "?")
        val secretsLine = "§7Secrets: §b" + secretCount + "§7-§e" + needed + tail +
            "   §7Score: " + colorizeScore(score)
        val statsLine = "§7Deaths: " + colorizeDeaths(deathCount) +
            "  §7M:" + (if (mimicKilled) "§a✔" else "§c✘") +
            " §7P:" + (if (princeKilled) "§a✔" else "§c✘") +
            "  §7Crypts: " + colorizeCrypts(Math.min(cryptCount, 5)) +
            (if (fishmod.utils.MayorApi.isPaulDungeonBonusActive()) " §6Paul" else "")

        val lines = arrayOf(secretsLine, statsLine)

        val x = FishSettings.dungeonScoreHudX
        val y = FishSettings.dungeonScoreHudY
        val lh = Constants.TEXT_HEIGHT + 2
        val sc = FishSettings.dungeonScoreScale.toFloat()
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(x.toFloat(), y.toFloat())
        ctx.matrices.scale(sc, sc)
        for (i in lines.indices)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.matrices.popMatrix()
    }
}

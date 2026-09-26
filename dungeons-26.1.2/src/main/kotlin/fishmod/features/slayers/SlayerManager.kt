package fishmod.features.slayers

import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import java.util.regex.Pattern

object SlayerManager {

    enum class State {
        NONE,

        GRINDING,

        BOSS_SPAWNED,

        COCOONED,

        BOSS_SLAIN,
    }

    class SpawnProgress(
        @JvmField val percent: Double?,
        @JvmField val current: Double?,
        @JvmField val max: Double?,
        @JvmField val raw: String,
    )

    private val QUEST_STARTED = Pattern.compile("SLAYER QUEST STARTED!")
    private val QUEST_COMPLETE = Pattern.compile("SLAYER QUEST COMPLETE!")
    private val QUEST_FAILED = Pattern.compile("SLAYER QUEST FAILED!")
    private val COCOON_CHAT = Pattern.compile("YOU COCOONED YOUR SLAYER BOSS")
    private val MINIBOSS_CHAT = Pattern.compile("SLAYER MINI-?BOSS (.+?) has spawned!")

    private val PCT = Pattern.compile("(\\d{1,3})%")
    private val FRACTION = Pattern.compile("\\(?([\\d,.]+)\\s*/\\s*([\\d,.]+)\\)?")

    private val PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)")

    private const val SCAN_INTERVAL_TICKS = 5
    private var scanCounter = 0

    @JvmStatic @Volatile var type: SlayerType? = null; private set
    @JvmStatic @Volatile var tier: Int = 0; private set
    @JvmStatic @Volatile var state: State = State.NONE; private set
    @JvmStatic @Volatile var progress: SpawnProgress? = null; private set

    @JvmStatic @Volatile var bossEntity: LivingEntity? = null

    private var lastCategoryLine = ""
    private var lastProgressLine = ""
    private var cocoonLatchedAt = 0L

    private const val CHAT_DEDUPE_MS = 3_000L
    private var lastQuestCompleteMs = 0L
    private var lastQuestStartedMs = 0L
    private var lastCocoonChatMs = 0L
    private var lastMiniBossLine = ""
    private var lastMiniBossMs = 0L

    private const val QUEST_LOSS_GRACE = 3
    private var questMissScans = 0

    @JvmStatic fun hasActiveQuest(): Boolean = type != null

    private data class AreaRule(val island: Location, val areas: Set<String>?)

    private val AREA_RULES: Map<SlayerType, List<AreaRule>> = mapOf(
        SlayerType.REVENANT to listOf(AreaRule(Location.HUB, setOf("Graveyard", "Crypt", "Crypts", "Castle"))),
        SlayerType.SVEN to listOf(AreaRule(Location.THE_PARK, setOf("Howling Cave"))),
        SlayerType.VOIDGLOOM to listOf(AreaRule(Location.THE_END, null)),
        SlayerType.INFERNO to listOf(AreaRule(Location.CRIMSON_ISLE, setOf("Smoldering Tomb"))),
        SlayerType.TARANTULA to listOf(
            AreaRule(Location.SPIDERS_DEN, null),
            AreaRule(Location.CRIMSON_ISLE, setOf("Burning Desert")),
        ),
    )

    @Volatile private var correctArea: Boolean = false

    private fun updateAreaMatch(lines: List<String>) {
        val t = type
        correctArea = if (t == null) false else areaMatches(t, lines)
    }

    private fun areaMatches(t: SlayerType, lines: List<String>): Boolean {
        val rules = AREA_RULES[t] ?: return Location.`in`(t.island)
        val rule = rules.firstOrNull { Location.`in`(it.island) } ?: return false
        val names = rule.areas ?: return true
        return lines.any { line -> names.any { line.contains(it, ignoreCase = true) } }
    }

    @JvmStatic
    fun inCorrectArea(): Boolean = correctArea

    @JvmStatic
    fun isActiveSlayer(): Boolean = hasActiveQuest() && Location.inSkyblock()

    @JvmStatic
    fun init() {
        SlayerPersonalBests
        SlayerStatsTracker.init()
        SlayerProfitTracker.init()
        SlayerAlerts.init()
        SlayerBossDetector.init()
        SlayerBossPhases.init()
        SlayerHuds.init()

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!FishSettings.slayerAnyEnabled()) {
                if (state != State.NONE || type != null) fullReset()
                return@EndTick
            }
            if (mc.player == null || mc.connection == null || !Location.inSkyblock()) {
                if (type != null || state != State.NONE) softReset()
                return@EndTick
            }
            if (scanCounter++ % SCAN_INTERVAL_TICKS != 0) return@EndTick
            scanScoreboard(mc)
        })

        Events.ON_WORLD_CHANGE.register { fullReset(); false }
        Events.ON_LOCATION_CHANGE.register { _ ->
            bossEntity = null
            cocoonLatchedAt = 0L
            false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.slayerAnyEnabled()) return@register false
            val s = text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim()
            val now = System.currentTimeMillis()
            when {
                QUEST_STARTED.matcher(s).find() -> {
                    if (now - lastQuestStartedMs > CHAT_DEDUPE_MS) {
                        lastQuestStartedMs = now
                        rearmForNextBoss()
                        SlayerStatsTracker.onQuestStarted()
                        SlayerProfitTracker.onQuestStarted()
                    }
                }
                QUEST_COMPLETE.matcher(s).find() -> {
                    if (now - lastQuestCompleteMs > CHAT_DEDUPE_MS) {
                        lastQuestCompleteMs = now
                        onQuestComplete()
                    }
                }
                QUEST_FAILED.matcher(s).find() -> onQuestFailed()
                COCOON_CHAT.matcher(s).find() -> {
                    if (now - lastCocoonChatMs > CHAT_DEDUPE_MS) {
                        lastCocoonChatMs = now
                        enterCocoon()
                    }
                }
                else -> {
                    val mb = MINIBOSS_CHAT.matcher(s)
                    if (mb.find()) {
                        if (s != lastMiniBossLine || now - lastMiniBossMs > 1_000L) {
                            lastMiniBossLine = s
                            lastMiniBossMs = now
                            SlayerAlerts.miniBoss(mb.group(1).trim())
                        }
                    }
                }
            }
            false
        }
    }

    private fun scanScoreboard(mc: Minecraft) {
        val lines = sidebarLines(mc)

        var purse = -1.0
        for (l in lines) {
            val m = PURSE.matcher(l)
            if (m.find()) { purse = m.group(1).replace(",", "").toDoubleOrNull() ?: -1.0; break }
        }
        SlayerProfitTracker.observePurse(purse)

        if (state == State.COCOONED && cocoonLatchedAt > 0L &&
            System.currentTimeMillis() - cocoonLatchedAt > 8_000L
        ) {
            cocoonLatchedAt = 0L
            setState(State.BOSS_SPAWNED)
        }

        val header = lines.indexOfFirst { it.equals("Slayer Quest", ignoreCase = true) }
        val categoryLine = if (header == -1) "" else lines.getOrNull(header + 1)?.trim().orEmpty()
        val parsed = if (categoryLine.isEmpty()) null else SlayerType.parseCategory(categoryLine)

        if (parsed == null) {
            if (type != null && ++questMissScans >= QUEST_LOSS_GRACE) endQuest()
            updateAreaMatch(lines)
            return
        }
        questMissScans = 0

        val progressLine = lines.getOrNull(header + 2)?.trim().orEmpty()

        if (categoryLine != lastCategoryLine) {
            lastCategoryLine = categoryLine
            val (newType, newTier) = parsed
            if (newType != type || newTier != tier) onQuestChange(newType, newTier)
        }
        if (type == null) return

        if (progressLine != lastProgressLine) {
            lastProgressLine = progressLine
            applyProgress(progressLine)
        }
        updateAreaMatch(lines)
    }

    private fun applyProgress(line: String) {
        when {
            line.contains("Slay the boss", ignoreCase = true) -> {
                progress = null
                if (state != State.BOSS_SPAWNED) setState(State.BOSS_SPAWNED)
            }

            line.contains("Boss slain", ignoreCase = true) -> {
                progress = null
                if (state != State.BOSS_SLAIN) setState(State.BOSS_SLAIN)
            }

            line.isBlank() -> {  }

            else -> {
                progress = parseProgress(line)
                if (state == State.NONE || state == State.BOSS_SLAIN) {
                    if (state == State.BOSS_SLAIN) rearmForNextBoss()
                    setState(State.GRINDING)
                }
            }
        }
    }

    private fun rearmForNextBoss() {
        bossEntity = null
        cocoonLatchedAt = 0L
        SlayerAlerts.reset()
    }

    private fun parseProgress(line: String): SpawnProgress {
        val frac = FRACTION.matcher(line)
        if (frac.find()) {
            val cur = parseNum(frac.group(1))
            val max = parseNum(frac.group(2))
            val pct = if (max > 0) (cur / max * 100.0).coerceIn(0.0, 100.0) else null
            return SpawnProgress(pct, cur, max, line)
        }
        val pct = PCT.matcher(line)
        if (pct.find()) {
            return SpawnProgress(pct.group(1).toDouble().coerceIn(0.0, 100.0), null, null, line)
        }
        return SpawnProgress(null, null, null, line)
    }

    private fun parseNum(s: String): Double {
        val t = s.trim().lowercase().replace(",", "")
        val mult = when {
            t.endsWith("k") -> 1_000.0
            t.endsWith("m") -> 1_000_000.0
            t.endsWith("b") -> 1_000_000_000.0
            else -> 1.0
        }
        val body = if (mult == 1.0) t else t.dropLast(1)
        return (body.toDoubleOrNull() ?: 0.0) * mult
    }

    private fun setState(next: State) {
        if (next == state) return
        val prev = state
        state = next
        when (next) {
            State.BOSS_SPAWNED -> {
                if (prev != State.COCOONED) {
                    SlayerTimer.onBossSpawned()
                    type?.let { SlayerAlerts.bossSpawned(it) }
                } else {
                    cocoonLatchedAt = 0L
                }
            }
            State.COCOONED -> SlayerAlerts.cocoon()
            State.BOSS_SLAIN -> onBossSlain()
            State.GRINDING, State.NONE -> {}
        }
    }

    private fun onQuestChange(newType: SlayerType, newTier: Int) {
        type = newType
        tier = newTier
        progress = null
        bossEntity = null
        cocoonLatchedAt = 0L
        lastProgressLine = ""
        state = State.GRINDING
        SlayerTimer.reset()
        SlayerAlerts.reset()
        SlayerStatsTracker.onQuestChange(newType, newTier)
    }

    private fun onBossSlain() {
        val t = type ?: return
        val secs = SlayerTimer.onBossSlain()
        if (secs > 0.0) {
            val isPb = SlayerPersonalBests.record(t, tier, secs)
            SlayerTimer.publishResult(secs, isPb)
        }
        SlayerTimer.onBossKilled()
    }

    private fun onQuestComplete() {
        val t = type ?: return
        SlayerStatsTracker.onBossKill(t, tier)
        SlayerProfitTracker.onBossKill(t)
        if (state != State.BOSS_SLAIN) {
            val secs = SlayerTimer.onBossSlain()
            if (secs > 0.0) {
                val isPb = SlayerPersonalBests.record(t, tier, secs)
                SlayerTimer.publishResult(secs, isPb)
            }
            state = State.BOSS_SLAIN
        }
        SlayerTimer.onBossKilled()
    }

    private fun onQuestFailed() {
        SlayerTimer.reset()
        state = State.GRINDING
        progress = null
        bossEntity = null
        cocoonLatchedAt = 0L
    }

    private fun enterCocoon() {
        if (type == null) return
        if (state == State.NONE || state == State.BOSS_SLAIN) return
        cocoonLatchedAt = System.currentTimeMillis()
        setState(State.COCOONED)
    }

    private fun endQuest() {
        type = null
        tier = 0
        questMissScans = 0
        lastCategoryLine = ""
        lastProgressLine = ""
        setState(State.NONE)
        progress = null
        bossEntity = null
        cocoonLatchedAt = 0L
        SlayerTimer.reset()
        SlayerStatsTracker.onQuestEnded()
    }

    private fun softReset() {
        type = null
        tier = 0
        questMissScans = 0
        state = State.NONE
        progress = null
        bossEntity = null
        lastCategoryLine = ""
        lastProgressLine = ""
        cocoonLatchedAt = 0L
        SlayerTimer.reset()
        SlayerStatsTracker.onQuestEnded()
    }

    private fun fullReset() {
        softReset()
        scanCounter = 0
    }

    private fun sidebarLines(mc: Minecraft): List<String> {
        val level = mc.level ?: return emptyList()
        val sb = level.scoreboard
        val obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        val entries = sb.listPlayerScores(obj)
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() })
        val out = ArrayList<String>(entries.size)
        for (entry in entries) {
            val team = sb.getPlayersTeam(entry.owner())
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else entry.owner()
            out.add(raw.replace(Constants.STRIP_COLOR_REGEX, "").trim())
        }
        return out
    }
}

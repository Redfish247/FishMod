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

/**
 * Owns Slayer quest state. Everything else in the package (detector, alerts, timer, stats, HUDs)
 * reads cached fields off this object or reacts to the hooks it fires — nothing else parses the
 * scoreboard or tracks the quest lifecycle.
 *
 * Detection strategy
 * -----------------
 * The authoritative source is Hypixel's own "Slayer Quest" sidebar block, which every SkyBlock
 * mod relies on:
 *
 * ```
 * Slayer Quest
 *  Revenant Horror IV        <- category  (type + tier)
 *  Combat XP: 41%            <- progress  (grinding: % or current/max)
 * ```
 * When the boss is up the progress line becomes `Slay the boss!`, and after it dies `Boss slain!`.
 * Quest start / complete / fail and the boss cocoon are taken from chat because they're exact,
 * one-shot lines that can't bounce like a scoreboard value can.
 *
 * The scoreboard is parsed on a 5-tick cadence (never per frame), only strings that changed are
 * re-classified, and the heavy entity scan lives in [SlayerBossDetector] gated on this state.
 */
object SlayerManager {

    enum class State {
        /** No slayer quest on the board. */
        NONE,

        /** Quest active, gaining combat XP toward the spawn. */
        GRINDING,

        /** Boss is alive and being fought. */
        BOSS_SPAWNED,

        /** Boss is webbed into a cocoon (invulnerable, bursts after ~5s). */
        COCOONED,

        /** Boss has been slain, quest not yet cleared/restarted. */
        BOSS_SLAIN,
    }

    /** Parsed spawn-bar progress. Any field may be null when Hypixel only gives partial info. */
    class SpawnProgress(
        @JvmField val percent: Double?,
        @JvmField val current: Double?,
        @JvmField val max: Double?,
        @JvmField val raw: String,
    )

    // ---- chat lines (matched against a color-stripped, trimmed string) ----
    private val QUEST_STARTED = Pattern.compile("SLAYER QUEST STARTED!")
    private val QUEST_COMPLETE = Pattern.compile("SLAYER QUEST COMPLETE!")
    private val QUEST_FAILED = Pattern.compile("SLAYER QUEST FAILED!")
    private val COCOON_CHAT = Pattern.compile("YOU COCOONED YOUR SLAYER BOSS")

    // progress-line shapes
    private val PCT = Pattern.compile("(\\d{1,3})%")
    private val FRACTION = Pattern.compile("\\(?([\\d,.]+)\\s*/\\s*([\\d,.]+)\\)?")

    // sidebar purse line ("Purse: 1,234,567" / "Piggy: 1,234,567") -> profit tracker spawn cost / mob-kill coins
    private val PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)")


    private const val SCAN_INTERVAL_TICKS = 5
    private var scanCounter = 0

    // ---- cached quest state (written on the client thread only) ----
    @JvmStatic @Volatile var type: SlayerType? = null; private set
    @JvmStatic @Volatile var tier: Int = 0; private set
    @JvmStatic @Volatile var state: State = State.NONE; private set
    @JvmStatic @Volatile var progress: SpawnProgress? = null; private set

    /** The bound boss LivingEntity, or null. Set/cleared by [SlayerBossDetector]. */
    @JvmStatic @Volatile var bossEntity: LivingEntity? = null

    private var lastCategoryLine = ""
    private var lastProgressLine = ""
    private var cocoonLatchedAt = 0L

    // ON_GAME_MESSAGE fires from two mixin sites (system chat + bundle unwrap), so one Hypixel line
    // can arrive twice. Swallow a repeat of the same one-shot within this window.
    private const val CHAT_DEDUPE_MS = 3_000L
    private var lastQuestCompleteMs = 0L
    private var lastQuestStartedMs = 0L
    private var lastCocoonChatMs = 0L

    // Hypixel's sidebar drops lines for a scan or two under load, especially mid-fight. Don't tear
    // down the quest (which would reset the timer and re-arm every spawn alert) until the "Slayer
    // Quest" block has been missing for this many consecutive scans.
    private const val QUEST_LOSS_GRACE = 3
    private var questMissScans = 0

    @JvmStatic fun hasActiveQuest(): Boolean = type != null

    /** Right island for the active quest's slayer type. */
    /** Best-effort "is the quest's slayer island the one we're on". Only advisory — the Hypixel
     *  location packet is island-level, and some slayers live in a sub-zone (Sven in The Park), so
     *  a false here doesn't disable anything on its own. */
    @JvmStatic
    fun inCorrectArea(): Boolean = type?.let { Location.`in`(it.island) } ?: false

    /** Actively grinding/fighting: a live "Slayer Quest" block on the board while in SkyBlock. The
     *  block is only present during a quest and vanishes on complete/fail, so this is a reliable
     *  gate for HUD visibility and the stats "active time" clock (paired with idle detection so
     *  standing at the bank mid-quest still doesn't inflate rates). */
    @JvmStatic
    fun isActiveSlayer(): Boolean = hasActiveQuest() && Location.inSkyblock()

    @JvmStatic
    fun init() {
        SlayerPersonalBests // touch = load
        SlayerStatsTracker.init()
        SlayerProfitTracker.init()
        SlayerTimer.init()
        SlayerAlerts.init()
        SlayerBossDetector.init()
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
            // Keep quest identity (the board repopulates), but drop anything entity-bound.
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
            }
            false
        }
    }

    // ---------------------------------------------------------------- scoreboard

    private fun scanScoreboard(mc: Minecraft) {
        val lines = sidebarLines(mc)

        var purse = -1.0
        for (l in lines) {
            val m = PURSE.matcher(l)
            if (m.find()) { purse = m.group(1).replace(",", "").toDoubleOrNull() ?: -1.0; break }
        }
        SlayerProfitTracker.observePurse(purse)

        // Cocoon has no distinct progress string (still "Slay the boss!"), so it can't self-clear
        // from the scoreboard — time it out (cocoon bursts in ~5s, +grace) back to the fight.
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
            // no readable quest this scan — tolerate a couple before tearing down (scoreboard flicker)
            if (type != null && ++questMissScans >= QUEST_LOSS_GRACE) endQuest()
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

            line.isBlank() -> { /* keep last known */ }

            else -> {
                progress = parseProgress(line)
                if (state == State.NONE || state == State.BOSS_SLAIN) setState(State.GRINDING)
            }
        }
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

    // ---------------------------------------------------------------- transitions

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
        // fresh quest: drop all transient boss state, keep session stats running
        progress = null
        bossEntity = null
        cocoonLatchedAt = 0L
        lastProgressLine = ""
        state = State.GRINDING
        SlayerTimer.reset()
        SlayerBossDetector.clearSeenMiniBosses()
        SlayerAlerts.reset()
        SlayerStatsTracker.onQuestChange(newType, newTier)
    }

    private fun onBossSlain() {
        val t = type ?: return
        val secs = SlayerTimer.onBossSlain()
        // stats/XP are attributed on QUEST COMPLETE (below); the timer + PB happen here because the
        // scoreboard "Boss slain!" is a hair earlier and more precise than the chat block.
        if (secs > 0.0) {
            val isPb = SlayerPersonalBests.record(t, tier, secs)
            SlayerTimer.publishResult(secs, isPb)
        }
    }

    private fun onQuestComplete() {
        val t = type ?: return
        // one completed quest == exactly one boss kill of this type/tier
        SlayerStatsTracker.onBossKill(t, tier)
        SlayerProfitTracker.onBossKill(t)
        // if the board never showed "Boss slain!" (instant kill) still stop the timer now
        if (state != State.BOSS_SLAIN) {
            val secs = SlayerTimer.onBossSlain()
            if (secs > 0.0) {
                val isPb = SlayerPersonalBests.record(t, tier, secs)
                SlayerTimer.publishResult(secs, isPb)
            }
            state = State.BOSS_SLAIN
        }
    }

    private fun onQuestFailed() {
        SlayerTimer.reset()
        state = State.GRINDING
        progress = null
        bossEntity = null
        cocoonLatchedAt = 0L
    }

    /** "YOU COCOONED YOUR SLAYER BOSS" — a generic Hypixel line, fired for any slayer type when the
     *  boss is webbed into a cocoon (~5s invuln, then it bursts). */
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

    /** Left SkyBlock / lost connection: forget the quest but don't wipe persisted PBs. */
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

    // ---------------------------------------------------------------- sidebar read

    /** Color-stripped sidebar rows, top-to-bottom. Mirrors DungeonState's reader: Hypixel puts the
     *  visible text in the team prefix+suffix, so the score owner token is ignored. */
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

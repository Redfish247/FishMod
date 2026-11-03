package fishmod.features.achievements
import fishmod.compat.curScreen

import fishmod.features.croesus.CroesusPrices
import fishmod.features.dungeon.map.DungeonScore
import fishmod.features.dungeon.map.DungeonState
import fishmod.features.mining.Mining
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.data.PartyTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.PracticeMode
import fishmod.utils.events.FishEvents
import fishmod.utils.networth.ItemsDb
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents

// Turns chat, scoreboard and existing tracker state into AchEvents (passive only)
object AchHooks {

    private const val MORT_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
    private val DROP = Regex("^(RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|RNGESUS INCARNATE|PRAY RNGESUS) DROP! +\\(?(?:([\\d,]+)x )?(.+?)\\)?(?: \\(\\+.*)?$")
    private val BAZAAR_FILL = Regex("^\\[Bazaar] Your (?:Buy Order|Sell Offer) for .+ was filled!")
    private val AH_SOLD = Regex("^\\[Auction] .+ bought .+ for [\\d,]+ coins")

    var runStartMs = 0L; private set
    private var bloodSeen = false
    private var mimicSeen = false
    private var princeSeen = false
    private var sectionStartMs = 0L
    private var inTerms = false
    private var feastSeen = false
    private var lastFeastMs = 0L
    private var tick = 0
    private var lavaFalls = 0
    private var wasInLava = false
    private var lavaOutTicks = 0
    private val ELAPSED = Regex("(\\d+)([hms])")

    fun init() {
        FishEvents.chat.register { c ->
            if (!AchSettings.achEnabled) return@register false
            FishDiag.guard("AchHooks.1", "achievement chat parse") { onChat(c.string.replace(FishConstants.STRIP_COLOR_REGEX, "").trim()) }
            false
        }
        FishEvents.runEnd.register {
            FishDiag.guard("AchHooks.2", "achievement run end") { onRunEnd() }
            false
        }
        FishEvents.terminalStep.register { name, _, objective, _, _ ->
            val me = Minecraft.getInstance().player?.name?.string
            AchBus.post(AchEvent.TerminalDone(me != null && name.replace(FishConstants.STRIP_COLOR_REGEX, "").trim() == me, objective))
            false
        }
        FishEvents.sectionChange.register {
            val now = System.currentTimeMillis()
            if (inTerms && sectionStartMs > 0 && !PracticeMode.active) AchBus.post(AchEvent.TerminalSection((now - sectionStartMs) / 1000.0))
            sectionStartMs = now
            false
        }
        FishEvents.worldChange.register { resetRun(); false }
    }

    private fun resetRun() {
        flushRunEnd(-1)
        runStartMs = 0L; bloodSeen = false; mimicSeen = false; princeSeen = false
        sectionStartMs = 0L; inTerms = false; lavaFalls = 0; wasInLava = false
    }

    private val FISH_NAMES = setOf("fish", "redfish2471")
    private val HI_FISH = Regex("(?i)\\bhi fish\\b")

    // Own "Hi Fish" line while Fish is in the tab list
    private fun hiFish(s: String) {
        val mc = Minecraft.getInstance()
        val me = mc.player?.name?.string ?: return
        val i = s.indexOf("$me:")
        if (i < 0 || !HI_FISH.containsMatchIn(s.substring(i + me.length + 1))) return
        if (s.substring(0, i).any { it == ':' }) return
        val here = fishmod.utils.TabListCache.entries.any { it.info.profile.name.lowercase() in FISH_NAMES }
        if (here) AchStore.max("hifish.said", 1)
    }

    private val PLAYTIME = Regex("""^You have (?:(\d[\d,]*) hours?)?(?: and )?(?:(\d+) minutes?)? ?playtime!""")

    // Reply to the player's own /playtime: Hypixel's real total beats the local counter
    private fun playtime(s: String) {
        val m = PLAYTIME.find(s) ?: return
        val h = m.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: 0L
        if (h == 0L && min == 0L) return
        AchStore.max("play.total", h * 3600 + min * 60)
    }

    // "Detailed /playtime" chest, slot 4 lore rows "687.7 hours on X" / "28 minutes on X" (format per SkyHanni LimboPlaytime)
    private const val DETAILED_TITLE = "Detailed /playtime"
    private val DETAILED_ROW = Regex("""^([\d.,]+) (hours?|minutes?) on (.+)$""")
    private var detailedRead = false

    private fun detailedPlaytime() {
        val scr = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*>
        if (scr == null || scr.title.string != DETAILED_TITLE) { detailedRead = false; return }
        if (detailedRead) return
        val lore = scr.menu.slots.getOrNull(4)?.item?.get(DataComponents.LORE)?.lines() ?: return
        if (lore.isEmpty()) return
        detailedRead = true
        for (c in lore) {
            val m = DETAILED_ROW.find(c.string.replace(FishConstants.STRIP_COLOR_REGEX, "").trim()) ?: continue
            val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val secs = (if (m.groupValues[2].startsWith("hour")) n * 3600 else n * 60).toLong()
            val island = m.groupValues[3]
            // same scope as the local counter: inside runs only, not Dungeon Hub / Crimson Isle
            val key = when {
                island.contains("Dungeon Hub", true) -> null
                island.contains("Dungeon", true) || island.contains("Catacombs", true) -> "play.dungeon"
                island.contains("Kuudra", true) -> "play.kuudra"
                else -> null
            } ?: continue
            if (secs > 0) AchStore.max(key, secs)
        }
    }

    private fun onChat(s: String) {
        hiFish(s)
        playtime(s)
        AchFlips.onChat(s)
        if (!s.contains(": ")) {
            if (s.startsWith("TROPHY FISH!") && s.contains("Golden Fish")) {
                AchStore.max("fish.golden", 1)
                if (java.time.LocalTime.now().hour < 5) AchStore.max("secret.midnightGolden", 1)
            }
            if (s.contains("Plhlegblast")) AchStore.max("fish.plhleg", 1)
        }
        if (s == MORT_START) {
            resetRun()
            runStartMs = System.currentTimeMillis()
            AchBus.post(AchEvent.RunStarted(BossPhase.getFloor()))
            return
        }
        DROP.find(s)?.let { m ->
            val n = m.groupValues[2].replace(",", "").toIntOrNull() ?: 1
            val item = m.groupValues[3].trim()
            val id = ItemsDb.idFor(item)
            val unit = if (id != null) CroesusPrices.price(id) else 0.0
            val v = if (unit.isNaN() || unit < 0) 0.0 else unit * n
            AchBus.post(AchEvent.Drop(item, n, v, m.groupValues[1], SkyblockLocation.getCurrentLocation().label))
            return
        }
        TEAM_SCORE.find(s)?.let { flushRunEnd(it.groupValues[1].toInt()); return }
        when {
            s.startsWith("PUZZLE SOLVED!") && SkyblockLocation.inDungeon() -> {
                if (s.contains("Water Board") && s.contains(Minecraft.getInstance().player?.name?.string ?: "\u0000")) {
                    val t = fishmod.features.dungeon.puzzles.odin.WaterSolver.secondsSinceOpen()
                    if (t in 0.0..5.0) AchStore.inc("puzzle.waterSub5")
                }
                AchBus.post(AchEvent.PuzzleDone(false))
            }
            s.startsWith("PUZZLE FAIL!") && SkyblockLocation.inDungeon() -> AchBus.post(AchEvent.PuzzleDone(true))
            s.startsWith("☠ You ") -> AchBus.post(AchEvent.OwnDeath(s))
            s.startsWith("OFFER ACCEPTED with") -> AchBus.post(AchEvent.VisitorAccepted)
            s.contains("COMMISSION COMPLETE") -> AchBus.post(AchEvent.CommissionDone)
            BAZAAR_FILL.containsMatchIn(s) -> AchBus.post(AchEvent.MarketFill("bz"))
            AH_SOLD.containsMatchIn(s) -> AchBus.post(AchEvent.MarketFill("ah"))
        }
    }

    private fun onRunEnd() {
        if (PracticeMode.active) return
        val secs = if (runStartMs > 0) (System.currentTimeMillis() - runStartMs) / 1000.0 else parseElapsed(DungeonScore.elapsedTime)
        flushRunEnd(-1)
        // held until Hypixel's "Team Score" line, live score can lag the real one
        pendingEnd = AchEvent.RunEnded(BossPhase.getFloor(), secs, DungeonScore.score, DungeonScore.deaths,
            DungeonScore.secretsFound, !PartyTools.isInParty(), DungeonScore.percentCleared)
        runStartMs = 0L
    }

    private var pendingEnd: AchEvent.RunEnded? = null
    private val TEAM_SCORE = Regex("^Team Score: (\\d+)")

    private fun flushRunEnd(chatScore: Int) {
        val e = pendingEnd ?: return
        pendingEnd = null
        AchBus.post(if (chatScore > e.score) e.copy(score = chatScore) else e)
    }

    private fun parseElapsed(s: String): Double {
        var t = 0.0
        ELAPSED.findAll(s).forEach { m ->
            val n = m.groupValues[1].toDouble()
            t += when (m.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
        }
        return t
    }

    // Every tick so short lava touches count; 5 ticks out of lava ends a fall
    private fun trackLava() {
        if (!SkyblockLocation.inDungeon()) return
        if (Minecraft.getInstance().player?.isInLava == true) {
            if (!wasInLava && ++lavaFalls >= 10) AchStore.max("secret.lavaFalls", 1)
            wasInLava = true
            lavaOutTicks = 0
        } else if (wasInLava && ++lavaOutTicks >= 5) wasInLava = false
    }

    // State polled from existing trackers each tick
    fun onTick() {
        trackLava()
        if (tick++ % 5 != 0) return
        detailedPlaytime()
        if (fishmod.features.rift.Rift.inRift()) {
            val a = fishmod.features.rift.Rift.strip(Mining.area).replace("⏣", "").trim()
            if (a.isNotEmpty() && AchStore.data.riftAreas.add(a)) { AchStore.changed(); AchStore.max("rift.areas", AchStore.data.riftAreas.size.toLong()) }
        }
        if (SkyblockLocation.inDungeon() && runStartMs > 0) {
            if (!bloodSeen && DungeonState.bloodOpened) {
                bloodSeen = true
                AchBus.post(AchEvent.BloodOpened((System.currentTimeMillis() - runStartMs) / 1000.0))
            }
            if (!mimicSeen && DungeonScore.mimicKilled) { mimicSeen = true; AchBus.post(AchEvent.MimicKilled) }
            if (!princeSeen && DungeonScore.princeKilled) { princeSeen = true; AchBus.post(AchEvent.PrinceKilled) }
            val t = BossPhase.inTerminals()
            if (t && !inTerms) sectionStartMs = System.currentTimeMillis()
            inTerms = t || (inTerms && BossPhase.inP3())
        }
        if (tick % 100 == 0 && SkyblockLocation.inSkyblock()) {
            val feast = Mining.sidebar.any { it.contains("Feast", true) && it.contains("Harvest", true) }
            val now = System.currentTimeMillis()
            if (feast && !feastSeen && now - lastFeastMs > 30 * 60_000L) { lastFeastMs = now; AchBus.post(AchEvent.FeastStarted) }
            feastSeen = feast
        }
    }
}

package fishmod.features.achievements

import fishmod.features.croesus.CroesusPrices
import fishmod.features.dungeon.map.DungeonScore
import fishmod.features.dungeon.map.DungeonState
import fishmod.features.mining.Mining
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.data.PartyUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.PracticeMode
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.minecraft.client.Minecraft

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
    private val termMiss = HashMap<String, Int>()

    // A wrong click in a terminal; 5 on the same terminal type in one run
    @JvmStatic
    fun terminalMisclick(type: String) {
        if (!Location.inDungeon()) return
        if (termMiss.merge(type, 1, Int::plus)!! >= 5) AchStore.max("secret.sweatyHands", 1)
    }

    @JvmStatic
    fun i4Plate() { Minecraft.getInstance().execute { AchStore.max("secret.i4Plate", 1) } }

    fun init() {
        Events.ON_GAME_MESSAGE.register { c ->
            FishDiag.guard("AchHooks.1", "achievement chat parse") { onChat(c.string.replace(Constants.STRIP_COLOR_REGEX, "").trim()) }
            false
        }
        Events.ON_RUN_END.register {
            FishDiag.guard("AchHooks.2", "achievement run end") { onRunEnd() }
            false
        }
        Events.ON_TERMINAL.register { name, _, objective, _, _ ->
            val me = Minecraft.getInstance().player?.name?.string
            AchBus.post(AchEvent.TerminalDone(me != null && name.replace(Constants.STRIP_COLOR_REGEX, "").trim() == me, objective))
            false
        }
        Events.ON_SECTION_CHANGE.register {
            val now = System.currentTimeMillis()
            if (inTerms && sectionStartMs > 0 && !PracticeMode.active) AchBus.post(AchEvent.TerminalSection((now - sectionStartMs) / 1000.0))
            sectionStartMs = now
            false
        }
        Events.ON_WORLD_CHANGE.register { resetRun(); false }
    }

    private fun resetRun() {
        runStartMs = 0L; bloodSeen = false; mimicSeen = false; princeSeen = false
        sectionStartMs = 0L; inTerms = false; termMiss.clear()
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
        val here = mc.connection?.onlinePlayers?.any { it.profile.name.lowercase() in FISH_NAMES } ?: false
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

    private fun onChat(s: String) {
        hiFish(s)
        playtime(s)
        AchFlips.onChat(s)
        if (!s.contains(": ")) {
            if (s.startsWith("TROPHY FISH!") && s.contains("Golden Fish")) AchStore.max("fish.golden", 1)
            if (s.contains("Plhlegblast")) AchStore.max("fish.plhleg", 1)
        }
        if (s == MORT_START) {
            resetRun()
            runStartMs = System.currentTimeMillis()
            AchBus.post(AchEvent.RunStarted(Phase.getFloor()))
            return
        }
        DROP.find(s)?.let { m ->
            val n = m.groupValues[2].replace(",", "").toIntOrNull() ?: 1
            val item = m.groupValues[3].trim()
            val id = ItemsDb.idFor(item)
            val unit = if (id != null) CroesusPrices.price(id) else 0.0
            val v = if (unit.isNaN() || unit < 0) 0.0 else unit * n
            AchBus.post(AchEvent.Drop(item, n, v, m.groupValues[1], Location.getCurrentLocation().name2))
            return
        }
        when {
            s.startsWith("PUZZLE SOLVED!") && Location.inDungeon() -> {
                if (s.contains("Water Board") && s.contains(Minecraft.getInstance().player?.name?.string ?: "\u0000")) {
                    val t = fishmod.features.dungeon.puzzles.odin.WaterSolver.secondsSinceOpen()
                    if (t in 0.0..5.0) AchStore.inc("puzzle.waterSub5")
                }
                AchBus.post(AchEvent.PuzzleDone(false))
            }
            s.startsWith("PUZZLE FAIL!") && Location.inDungeon() -> AchBus.post(AchEvent.PuzzleDone(true))
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
        AchBus.post(AchEvent.RunEnded(Phase.getFloor(), secs, DungeonScore.score, DungeonScore.deaths,
            DungeonScore.secretsFound, !PartyUtil.isInParty(), DungeonScore.percentCleared))
        runStartMs = 0L
    }

    private fun parseElapsed(s: String): Double {
        var t = 0.0
        Regex("(\\d+)([hms])").findAll(s).forEach { m ->
            val n = m.groupValues[1].toDouble()
            t += when (m.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
        }
        return t
    }

    // State polled from existing trackers each tick
    fun onTick() {
        if (tick++ % 5 != 0) return
        if (fishmod.features.rift.Rift.inRift()) {
            val a = fishmod.features.rift.Rift.strip(Mining.area).replace("⏣", "").trim()
            if (a.isNotEmpty() && AchStore.data.riftAreas.add(a)) { AchStore.changed(); AchStore.max("rift.areas", AchStore.data.riftAreas.size.toLong()) }
        }
        if (Location.inDungeon() && runStartMs > 0) {
            if (!bloodSeen && DungeonState.bloodOpened) {
                bloodSeen = true
                AchBus.post(AchEvent.BloodOpened((System.currentTimeMillis() - runStartMs) / 1000.0))
            }
            if (!mimicSeen && DungeonScore.mimicKilled) { mimicSeen = true; AchBus.post(AchEvent.MimicKilled) }
            if (!princeSeen && DungeonScore.princeKilled) { princeSeen = true; AchBus.post(AchEvent.PrinceKilled) }
            val t = Phase.inTerminals()
            if (t && !inTerms) sectionStartMs = System.currentTimeMillis()
            inTerms = t || (inTerms && Phase.inP3())
        }
        if (tick % 100 == 0 && Location.inSkyblock()) {
            val feast = Mining.sidebar.any { it.contains("Feast", true) && it.contains("Harvest", true) }
            val now = System.currentTimeMillis()
            if (feast && !feastSeen && now - lastFeastMs > 30 * 60_000L) { lastFeastMs = now; AchBus.post(AchEvent.FeastStarted) }
            feastSeen = feast
        }
    }
}

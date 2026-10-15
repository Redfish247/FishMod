package fishmod.features.achievements

import fishmod.features.diana.DianaTracker
import fishmod.features.garden.Pests
import fishmod.features.kuudra.KuudraSettings
import fishmod.features.mining.MiningProfitTracker
import fishmod.features.slayers.SlayerManager
import fishmod.features.slayers.SlayerProfitTracker
import fishmod.utils.Location
import fishmod.utils.MayorApi
import fishmod.utils.Scheduler
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// Counters fed by AchEvents plus polled ledgers over the existing profit/Diana/mining trackers
object AchTrackers {

    private val RNG_RARITIES = setOf("CRAZY RARE", "INSANE", "PRAY TO RNGESUS", "RNGESUS INCARNATE", "PRAY RNGESUS")
    private val sessionRng = HashSet<String>()
    private val sessionStart = HashMap<String, Double>()
    private var lastKuudraChests = -1
    private var lastPestKills = -1
    private var tick = 0
    private var polls = 0
    private val rngSeen = HashMap<String, Long>()
    private var lastInputMs = System.currentTimeMillis()
    private var lastYaw = 0f
    private var lastPitch = 0f

    fun init() {
        AchBus.on(::onEvent)
    }

    private fun secret(k: String) = AchStore.max("secret.$k", 1)

    private fun onEvent(e: AchEvent) {
        val st = AchStore
        when (e) {
            is AchEvent.RunStarted -> {
                if (LocalTime.now().hour < 5) secret("midnightRun")
                Scheduler.scheduleTask(Runnable {
                    if (currentFloor() == "F1" && st.get("cata.level") >= 50) secret("wrongFloor")
                }, 60)
            }
            is AchEvent.RunEnded -> onRunEnded(e)
            is AchEvent.Drop -> onDrop(e)
            is AchEvent.PuzzleDone -> if (e.failed) st.set("puzzle.curStreak", 0) else {
                st.inc("puzzle.solved"); st.inc("puzzle.curStreak"); st.max("puzzle.bestStreak", st.get("puzzle.curStreak"))
            }
            is AchEvent.TerminalDone -> if (e.own && e.objective.contains("terminal", true)) st.inc("term.done")
            is AchEvent.TerminalSection -> if (e.seconds in 1.0..10.0) st.inc("term.sectionSub10")
            is AchEvent.BloodOpened -> if (e.seconds in 1.0..15.0) st.max("dungeon.bloodSub15", 1)
            AchEvent.MimicKilled -> st.inc("dungeon.mimic")
            AchEvent.PrinceKilled -> st.inc("dungeon.prince")
            is AchEvent.KuudraEnded -> { st.inc("kuudra.total"); if (e.tier in 1..5) st.inc("kuudra.t${e.tier}") }
            AchEvent.SupplyDelivered -> st.inc("kuudra.supplies")
            AchEvent.BallistaBuilt -> st.inc("kuudra.ballista")
            is AchEvent.ChestOpened -> if (e.kind == "kuudra" && e.paid) st.inc("kuudra.paidChests")
            is AchEvent.SlayerKill -> {
                st.inc("slayer.total")
                when {
                    e.type == "REVENANT" && e.tier >= 5 -> st.inc("slayer.zombieT5")
                    e.type == "TARANTULA" && e.tier >= 5 -> st.inc("slayer.spiderT5")
                    e.type == "SVEN" && e.tier >= 4 -> st.inc("slayer.wolfT4")
                    e.type == "VOIDGLOOM" && e.tier >= 4 -> st.inc("slayer.endermanT4")
                    e.type == "INFERNO" && e.tier >= 4 -> st.inc("slayer.blazeT4")
                }
            }
            is AchEvent.PersonalBest -> { st.inc("fm.pbs"); if (e.improvedBy > 0 && e.improvedBy < 0.01) secret("framePerfect") }
            is AchEvent.OwnDeath -> if (Location.inDungeon() && (e.message.contains("lava", true) || e.message.contains("burn", true))) secret("lavaDeath")
            AchEvent.FeastStarted -> st.inc("garden.feasts")
            AchEvent.VisitorAccepted -> st.inc("garden.visitors")
            AchEvent.CommissionDone -> st.inc("mining.commissions")
            is AchEvent.MarketFill -> st.inc(if (e.kind == "bz") "market.bzFilled" else "market.ahSold")
            is AchEvent.Unlocked -> {}
        }
    }

    private fun currentFloor(): String? = fishmod.utils.dungeon.Phase.getFloor()

    private fun onRunEnded(e: AchEvent.RunEnded) {
        val st = AchStore
        val f = e.floor ?: ""
        st.inc("dungeon.runs")
        if (f.isNotEmpty()) st.inc("floor.$f")
        if (f.startsWith("M")) st.inc("mm.total")
        val splus = e.score >= 300
        if (splus) st.inc("dungeon.splus")
        if (e.deaths == 0) st.inc("dungeon.deathless")
        st.max("secrets.bestRun", e.secrets.toLong())
        if (f == "M7") {
            if (e.seconds in 1.0..300.0) { st.inc("m7.sub5"); st.inc("m7.curSub5") } else st.set("m7.curSub5", 0)
            if (e.seconds in 1.0..240.0) st.inc("m7.sub4")
            if (splus) st.inc("m7.curSplus") else st.set("m7.curSplus", 0)
            st.max("m7.sub5Streak", st.get("m7.curSub5"))
            st.max("m7.splusStreak", st.get("m7.curSplus"))
            if (e.solo) secret("soloM7")
        }
        if (e.secrets == 0 && e.cleared >= 100) secret("noSecrets")
        st.addRun(AchStore.RunRec().also {
            it.t = System.currentTimeMillis(); it.floor = f; it.secs = e.seconds; it.score = e.score
            it.deaths = e.deaths; it.secrets = e.secrets; it.solo = e.solo
        })
    }

    private fun onDrop(e: AchEvent.Drop) {
        val st = AchStore
        if (e.rarity !in RNG_RARITIES && e.value < AchSettings.achRngMinValue) return
        st.inc("rng.chat")
        st.max("rng.count", st.get("rng.chat"))
        st.max("rng.best", e.value.toLong())
        if (SlayerManager.type != null) st.inc("slayer.rng")
        if (!sessionRng.add(e.item)) secret("chosenOne")
        if (recentRng(e.item)) return
        st.addRng(AchStore.RngRec().also { it.t = System.currentTimeMillis(); it.item = e.item; it.value = e.value; it.source = e.source })
    }

    private fun recentRng(item: String): Boolean {
        val now = System.currentTimeMillis()
        return AchStore.data.rng.takeLast(8).any { it.t > now - 10_000 && it.item.equals(item, true) }
    }

    fun onTick(mc: Minecraft) {
        tick++
        val p = mc.player ?: return
        if (p.yRot != lastYaw || p.xRot != lastPitch) { lastYaw = p.yRot; lastPitch = p.xRot; lastInputMs = System.currentTimeMillis() }
        if (tick % 20 == 0 && Location.inSkyblock()) playtimeSecond()
        if (tick % 100 == 0) FishDiag.guard("AchTrackers.1", "achievement ledgers") { pollLedgers() }
        if (tick % 1200 == 0) FishDiag.guard("AchTrackers.2", "playtime derived") { derivePlaytime() }
    }

    private fun playtimeSecond() {
        val st = AchStore
        st.inc("play.total")
        when (Location.getCurrentLocation()) {
            Location.DUNGEON -> st.inc("play.dungeon")
            Location.KUUDRA -> st.inc("play.kuudra")
            else -> {}
        }
        val d = LocalDate.now().toString()
        st.data.playDays[d] = (st.data.playDays[d] ?: 0L) + 1
        st.max("play.afkBest", (System.currentTimeMillis() - lastInputMs) / 1000)
    }

    private fun derivePlaytime() {
        val st = AchStore
        val days = st.data.playDays
        if (days.isEmpty()) return
        st.max("play.bestDay", days.values.max())
        val parsed = days.mapNotNull { (k, v) -> runCatching { LocalDate.parse(k) to v }.getOrNull() }.toMap()
        var bestWeek = 0L; var bestWeekend = 0L
        for (d in parsed.keys) {
            bestWeek = maxOf(bestWeek, (0L..6L).sumOf { parsed[d.plusDays(it)] ?: 0L })
            if (d.dayOfWeek == DayOfWeek.FRIDAY) bestWeekend = maxOf(bestWeekend, (0L..2L).sumOf { parsed[d.plusDays(it)] ?: 0L })
        }
        st.max("play.bestWeek", bestWeek)
        st.max("play.bestWeekend", bestWeekend)
        st.max("play.streak4h", streak(parsed, 4 * 3600L))
        st.max("play.streak8h", streak(parsed, 8 * 3600L))
        MayorApi.mayorLine()?.let { line ->
            val name = line.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim().substringBefore(' ').substringBefore(':')
            if (name.isNotBlank() && st.data.mayors.add(name)) { st.changed(); st.set("mayors.seen", st.data.mayors.size.toLong()) }
        }
    }

    private fun streak(days: Map<LocalDate, Long>, need: Long): Long {
        var best = 0L
        for (d in days.keys) {
            if ((days[d.minusDays(1)] ?: 0L) >= need) continue
            var n = 0L; var c = d
            while ((days[c] ?: 0L) >= need) { n++; c = c.plusDays(1) }
            best = maxOf(best, n)
        }
        return best
    }

    // Lifetime value per source; a big drop means the tracker was reset, so the old total moves into base
    private fun feed(name: String, cur: Double): Double {
        if (cur.isNaN() || cur.isInfinite()) return 0.0
        val f = AchStore.data.feeds.getOrPut(name) { AchStore.Feed() }
        if (cur < f.last * 0.5 && f.last > 1_000_000) f.base += f.last
        if (f.last != cur) { f.last = cur; AchStore.changed() }
        val v = f.base + cur
        if (v != 0.0 || polls >= 12) sessionStart.putIfAbsent(name, v)
        return v
    }

    // Lifetime dungeon chest loot value from the loot tracker rows (chest costs aren't recorded)
    private fun dungeonLootValue(): Double {
        var v = 0.0
        for (r in fishmod.features.croesus.LootTrackerStore.rows()) {
            if (r.id.isEmpty() || r.count <= 0) continue
            val id = r.id
            val p = fishmod.features.croesus.CroesusPrices.bazaarPrice(id, false)?.takeIf { it > 0 } ?: fishmod.features.croesus.CroesusPrices.price(id)
            if (p > 0) v += p * r.count
        }
        return v
    }

    private fun pollLedgers() {
        val st = AchStore
        polls++
        val kuudra = feed("kuudra", KuudraSettings.kuudraProfitTotal)
        val slayer = feed("slayer", SlayerProfitTracker.allProfit(false))
        val diana = feed("diana", DianaTracker.profit(DianaTracker.total))
        val mining = feed("mining", MiningProfitTracker.profitOf(false))
        val pests = feed("pests", Pests.profitValue())
        val dungeons = feed("dungeons", dungeonLootValue())
        st.max("dungeons.profit", dungeons.toLong())
        st.max("kuudra.profit", kuudra.toLong())
        st.max("slayer.profit", slayer.toLong())
        st.max("diana.profit", diana.toLong())
        st.max("mining.profit", mining.toLong())
        st.max("profit.total", (kuudra + slayer + diana + mining + pests + dungeons).toLong())
        val session = mapOf("kuudra" to kuudra, "slayer" to slayer, "diana" to diana, "mining" to mining, "pests" to pests, "dungeons" to dungeons)
            .entries.sumOf { (k, v) -> v - (sessionStart[k] ?: v) }
        st.max("profit.sessionBest", session.toLong())
        st.max("farming.sessionBest", Pests.profitValue().toLong())

        val chests = KuudraSettings.kuudraProfitChests
        if (lastKuudraChests >= 0 && chests > lastKuudraChests) repeat(chests - lastKuudraChests) { AchBus.post(AchEvent.ChestOpened("kuudra", true, 0.0)) }
        lastKuudraChests = chests
        val pk = Pests.killCount()
        if (lastPestKills >= 0 && pk > lastPestKills) st.inc("garden.pests", (pk - lastPestKills).toLong())
        lastPestKills = pk
        st.max("mining.corpses", MiningProfitTracker.totalCount("Corpses"))
        pollDiana()
        FishDiag.guard("AchTrackers.3", "rng feed") { pollRng() }
    }

    // Rare drops from the real trackers: count/best/log, idempotent via max() and a per-item baseline
    private fun pollRng() {
        val st = AchStore
        val min = AchSettings.achRngMinValue.toDouble()
        var total = 0L
        var best = 0.0
        fun feedItem(src: String, item: String, n: Long, unit: Double) {
            if (n <= 0 || unit.isNaN()) return
            total += n; best = maxOf(best, unit)
            val key = "$src|$item"
            val prev = rngSeen.put(key, n)
            val fresh = if (prev == null) (if (st.data.rng.none { it.source == src && it.item == item }) 1L else 0L) else n - prev
            repeat(fresh.coerceIn(0L, 5L).toInt()) { if (!recentRng(item)) st.addRng(AchStore.RngRec().also { it.t = System.currentTimeMillis(); it.item = item; it.value = unit; it.source = src }) }
        }
        val tot = DianaTracker.total
        for (d in DianaTracker.DROPS) {
            val unit = DianaTracker.priceOf(d.key)
            if (d.loud || unit >= min) feedItem("Diana", d.name, both(tot, d.key), unit)
        }
        for ((name, n) in SlayerProfitTracker.totalDrops()) {
            if (name.endsWith("Coins")) continue
            val unit = fishmod.utils.networth.ItemsDb.idFor(name)?.let { fishmod.features.croesus.CroesusPrices.price(it) } ?: 0.0
            if (unit >= min) feedItem("Slayer", name, n, unit)
        }
        for ((name, n) in MiningProfitTracker.totalItems()) {
            val unit = MiningProfitTracker.priceOf(name)
            if (unit >= min) feedItem("Mining", name, n, unit)
        }
        for (r in fishmod.features.croesus.LootTrackerStore.rows()) {
            if (r.id.isEmpty() || r.count <= 0) continue
            val unit = fishmod.features.croesus.CroesusPrices.bazaarPrice(r.id, false)?.takeIf { it > 0 } ?: fishmod.features.croesus.CroesusPrices.price(r.id)
            if (unit >= min) feedItem("Dungeons", r.name, r.count.toLong(), unit)
        }
        st.max("rng.trk", total)
        st.max("rng.count", maxOf(total, st.get("rng.chat")))
        st.max("rng.best", best.toLong())
    }

    private fun both(t: DianaTracker.Tracker, k: String) = t.item(k) + t.item(k + "_LS")

    private fun pollDiana() {
        val st = AchStore
        val tot = DianaTracker.total
        st.max("diana.burrows", tot.item("TOTAL_BURROWS"))
        st.max("diana.mobs", tot.mobs.values.sum())
        st.max("diana.inquis", tot.mob("MINOS_INQUISITOR"))
        for (k in listOf("GRIFFIN_FEATHER", "ANTIQUE_REMEDIES", "CROWN_OF_GREED", "DAEDALUS_STICK", "CHIMERA")) st.max("diana.item.$k", both(tot, k))
        val dropKeys = DianaTracker.DROPS.map { it.key }
        st.max("diana.rng", dropKeys.sumOf { both(tot, it) } + both(tot, "MYTHOLOGICAL_DYE"))
        if (dropKeys.all { both(tot, it) > 0 }) st.max("diana.allDrops", 1)
        val ev = DianaTracker.event
        val past = DianaTracker.pastEvents
        val curActive = ev.mobs.values.any { it > 0 }
        st.max("diana.events", (past.count { e -> e.mobs.values.any { it > 0 } } + if (curActive) 1 else 0).toLong())
        fun item(m: Map<String, Long>, k: String) = (m[k] ?: 0L) + (m[k + "_LS"] ?: 0L)
        for (p in past) {
            st.max("diana.eventBestBurrows", p.items["TOTAL_BURROWS"] ?: 0L)
            st.max("diana.eventBestInquis", p.mobs["MINOS_INQUISITOR"] ?: 0L)
            st.max("diana.eventBestTime", p.timeMs / 1000)
            st.max("diana.eventBestProfit", p.profit)
            st.max("diana.eventBestChimera", item(p.items, "CHIMERA"))
            st.max("diana.eventBestWool", item(p.items, "SHIMMERING_WOOL"))
        }
        if (curActive) {
            st.max("diana.eventBestBurrows", ev.item("TOTAL_BURROWS"))
            st.max("diana.eventBestInquis", ev.mob("MINOS_INQUISITOR"))
            st.max("diana.eventBestTime", ev.timeMs / 1000)
            st.max("diana.eventBestProfit", DianaTracker.profit(ev).toLong())
            st.max("diana.eventBestChimera", both(ev, "CHIMERA"))
            st.max("diana.eventBestWool", both(ev, "SHIMMERING_WOOL"))
        }
    }
}

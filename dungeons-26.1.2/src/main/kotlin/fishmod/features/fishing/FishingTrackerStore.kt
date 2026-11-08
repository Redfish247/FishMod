package fishmod.features.fishing

import fishmod.utils.SkyblockLocation

// Counters of one tracker bucket (session or total). Gson-persisted for totals.
class FishingBucket {
    // catch events (hooks that landed a sea creature) and creatures (double hook counts 2)
    var events = 0L
    var catches = 0L
    var doubles = 0L
    var cocoons = 0L
    var activeMs = 0L
    var scCounts: MutableMap<String, Long> = LinkedHashMap()
    var drops: MutableMap<String, Long> = LinkedHashMap()
    var dropCoins: MutableMap<String, Double> = LinkedHashMap()
    var lines: MutableMap<String, Long> = LinkedHashMap()
    // net coins from area chat lines (dice)
    var lineCoins = 0.0
    // pity: pitySc catches since the last pityDrop
    var pity = 0L
    @Transient var lastAt = 0L
    @Transient var lastLineAt = 0L

    fun dropValue(): Double = dropCoins.values.sum()
}

class FishingTotals { var buckets: MutableMap<String, FishingBucket> = LinkedHashMap() }

// Barn timer: started by the first catch, reset after an idle gap or a full-barn line.
class BarnState {
    var startedAt = 0L
    var lastAt = 0L
    var count = 0
    var full = false

    fun onCatch(now: Long, n: Int) {
        if (startedAt == 0L || now - lastAt > IDLE_MS) { startedAt = now; count = 0; full = false }
        lastAt = now
        count += n
    }

    fun reset() { startedAt = 0L; lastAt = 0L; count = 0; full = false }

    companion object { const val IDLE_MS = 300_000L }
}

// Fishing festival: shark counts, over on the end line or 61 min after the first shark.
class FestivalState {
    val sharks = LinkedHashMap<String, Long>()
    var startedAt = 0L
    var ended = false

    fun total(): Long = sharks.values.sum()

    fun active(now: Long): Boolean = startedAt != 0L && !ended && now - startedAt < DURATION_MS

    fun onShark(name: String, n: Int, now: Long) {
        if (startedAt == 0L || ended || now - startedAt >= DURATION_MS) { sharks.clear(); startedAt = now; ended = false }
        sharks.merge(name, n.toLong(), Long::plus)
    }

    fun end() { if (startedAt != 0L) ended = true }
    fun reset() { sharks.clear(); startedAt = 0L; ended = false }

    companion object {
        const val DURATION_MS = 61 * 60_000L
        val SHARKS = setOf("Great White Shark", "Tiger Shark", "Blue Shark", "Nurse Shark")
    }
}

// All tracker state and update rules; no Minecraft types, so tests drive it directly.
object FishingTrackerStore {
    const val MAIN = "main"

    @JvmField var totals = FishingTotals()
    private val session = HashMap<String, FishingBucket>()
    @JvmField val barn = BarnState()
    @JvmField val festival = FestivalState()

    @JvmField @Volatile var paused = false

    // Session clock: accumulated while running, paused with the pause key, cleared by resetSession
    private var sessionAcc = 0L
    private var sessionResumeAt = System.currentTimeMillis()
    @JvmStatic fun sessionElapsed(now: Long): Long = sessionAcc + (if (paused) 0L else (now - sessionResumeAt).coerceAtLeast(0L))

    // HUDs draw only while the player's own hook existed within the last 60 s
    const val CAST_WINDOW_MS = 60_000L
    @JvmField @Volatile var lastCastAt = 0L
    @JvmStatic fun noteCast(now: Long) { lastCastAt = now }
    @JvmStatic fun castRecent(now: Long): Boolean = lastCastAt != 0L && now - lastCastAt <= CAST_WINDOW_MS
    // Draw gate: fishing island (Fishing.active()) and a recent cast
    // either=false: both must hold (default); either=true: one is enough
    @JvmStatic @JvmOverloads fun shouldDraw(active: Boolean, now: Long, either: Boolean = false): Boolean =
        if (either) active || castRecent(now) else active && castRecent(now)
    // Bumped on every change; HUD line caches rebuild on it
    @JvmField @Volatile var version = 0
    @JvmField @Volatile var dirty = false

    private fun bump() { version++; dirty = true }

    @JvmStatic fun total(id: String): FishingBucket = totals.buckets.getOrPut(id) { FishingBucket() }
    @JvmStatic fun session(id: String): FishingBucket = session.getOrPut(id) { FishingBucket() }
    @JvmStatic fun bucket(id: String, total: Boolean): FishingBucket = if (total) total(id) else session(id)

    private inline fun both(id: String, f: (FishingBucket) -> Unit) { f(session(id)); f(total(id)) }

    @JvmStatic
    fun onCatch(sc: SeaCreature, count: Int, cocoon: Boolean, loc: SkyblockLocation, now: Long) {
        if (paused) return
        both(MAIN) { b ->
            b.activeMs += FishingTrackerMath.activeDelta(b.lastAt, now)
            b.lastAt = now
            b.events++
            b.catches += count
            if (count > 1) b.doubles++
            if (cocoon) b.cocoons++
            b.scCounts.merge(sc.name, count.toLong(), Long::plus)
        }
        for (a in FishingTrackerAreas.ALL) {
            if (a.id == FishingTrackerAreas.TREASURE || a.id == FishingTrackerAreas.DICE) continue
            if (!a.inArea(loc) || !a.sc(sc)) continue
            both("area:" + a.id) { b ->
                b.catches += count
                b.scCounts.merge(sc.name, count.toLong(), Long::plus)
                if (a.pitySc == sc.name) b.pity += count
            }
        }
        barn.onCatch(now, count)
        if (sc.name in FestivalState.SHARKS) festival.onShark(sc.name, count, now)
        bump()
    }

    // Rare drop with its ItemPricer price (0 when unknown)
    @JvmStatic
    fun onDrop(name: String, price: Double) {
        if (paused) return
        both(MAIN) { b ->
            b.drops.merge(name, 1L, Long::plus)
            b.dropCoins.merge(name, price, Double::plus)
        }
        for (a in FishingTrackerAreas.ALL) {
            if (name !in a.drops) continue
            both("area:" + a.id) { b ->
                b.drops.merge(name, 1L, Long::plus)
                b.dropCoins.merge(name, price, Double::plus)
                if (a.pityDrop == name) b.pity = 0
            }
        }
        bump()
    }

    // Fishing-sack gain (n items, total price) into the main bucket
    @JvmStatic
    fun onSackItem(name: String, n: Long, coins: Double) {
        if (paused || n <= 0) return
        both(MAIN) { b ->
            b.drops.merge(name, n, Long::plus)
            b.dropCoins.merge(name, coins, Double::plus)
        }
        bump()
    }

    // Chat line of an area def with a line parser (treasure, dice); true when counted
    @JvmStatic
    fun onLine(areaId: String, line: AreaLine, now: Long) {
        if (paused) return
        both("area:$areaId") { b ->
            b.lines.merge(line.key, 1L, Long::plus)
            b.lineCoins += line.coins
            b.lastLineAt = now
        }
        bump()
    }

    @JvmStatic fun onFestivalEnd() { festival.end(); bump() }
    @JvmStatic fun onBarnFull() { if (barn.startedAt != 0L) barn.full = true; bump() }
    @JvmStatic fun onWorldChange() { barn.reset(); festival.reset(); for (b in session.values) b.lastAt = 0L; for (b in totals.buckets.values) b.lastAt = 0L; bump() }

    // Pausing breaks the active-time gap so idle time is not counted
    @JvmStatic fun setPaused(p: Boolean, now: Long = System.currentTimeMillis()) {
        if (p == paused) return
        if (p) sessionAcc += (now - sessionResumeAt).coerceAtLeast(0L) else sessionResumeAt = now
        paused = p
        for (b in session.values) b.lastAt = 0L
        for (b in totals.buckets.values) b.lastAt = 0L
        bump()
    }

    @JvmStatic fun resetSession(now: Long = System.currentTimeMillis()) { sessionAcc = 0L; sessionResumeAt = now; session.clear(); barn.reset(); festival.reset(); bump() }
    @JvmStatic fun resetTotal() { totals = FishingTotals(); bump() }
}

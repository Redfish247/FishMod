package fishmod

import fishmod.features.fishing.FishingTrackerAreas
import fishmod.features.fishing.FishingTrackerLines
import fishmod.features.fishing.FishingTrackerMath
import fishmod.features.fishing.FishingTrackerStore
import fishmod.features.fishing.FishingTotals
import fishmod.features.fishing.SeaCreatures
import fishmod.utils.SkyblockLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Sample lines come from Feesh and are UNVERIFIED in game.
class FishingTrackerTest {
    @BeforeEach fun reset() {
        FishingTrackerMath.priceFn = { id -> when (id) { "ARCHFIEND_DICE" -> 1_000_000.0; "DYE_ARCHFIEND" -> 50_000_000.0; else -> 0.0 } }
        FishingTrackerStore.totals = FishingTotals()
        FishingTrackerStore.resetSession()
        FishingTrackerStore.setPaused(false)
    }

    private fun sc(n: String) = SeaCreatures.byName(n)!!

    @Test fun treasureParse() {
        assertEquals("GOOD CATCH", FishingTrackerMath.treasure("⛃ GOOD CATCH! You caught a Shinyfish Shard!"))
        assertEquals("OUTSTANDING JUNK CATCH", FishingTrackerMath.treasure(" OUTSTANDING JUNK CATCH! You caught a thing"))
        assertEquals("GREAT CATCH", FishingTrackerMath.treasure("GREAT CATCH! x"))
        assertNull(FishingTrackerMath.treasure("♪ MUSICAL CATCH! You caught a Music Disc - Cat!"))
        assertNull(FishingTrackerMath.treasure("nothing here"))
    }

    @Test fun diceParse() {
        val d = FishingTrackerMath.dice("Your Archfiend Dice rolled a 6! Nice!")!!
        assertFalse(d.highClass); assertEquals(6, d.roll)
        assertTrue(FishingTrackerMath.dice("Your High Class Archfiend Dice rolled a 3!")!!.highClass)
        assertNull(FishingTrackerMath.dice("Your Archfiend Dice rolled a 9!"))
    }

    @Test fun diceCoins() {
        assertEquals(-666_000.0, FishingTrackerMath.diceCoins(FishingTrackerMath.Dice(false, 2)))
        assertEquals(-666_000.0 + 15_000_000.0 - 1_000_000.0, FishingTrackerMath.diceCoins(FishingTrackerMath.Dice(false, 6)))
        assertEquals(-666_000.0 + 50_000_000.0 - 1_000_000.0, FishingTrackerMath.diceCoins(FishingTrackerMath.Dice(false, 7)))
    }

    @Test fun maths() {
        assertEquals(0.0, FishingTrackerMath.perHour(100.0, 30_000))
        assertEquals(3600.0, FishingTrackerMath.perHour(60.0, 60_000L), 1e-9)
        assertEquals(0L, FishingTrackerMath.activeDelta(0, 1000))
        assertEquals(FishingTrackerMath.ACTIVE_GAP_MS, FishingTrackerMath.activeDelta(1, 10_000_000))
        assertEquals("12.5%", FishingTrackerMath.pct(1, 8))
        assertEquals("1.50m", FishingTrackerMath.short(1_500_000.0))
        assertEquals("1:05", FishingTrackerMath.clock(65_000))
        assertEquals("1:01:01", FishingTrackerMath.clock(3_661_000))
    }

    @Test fun catchCountsDoubleAndRates() {
        val t0 = 1_000_000L
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.HUB, t0)
        FishingTrackerStore.onCatch(sc("Squid"), 2, false, SkyblockLocation.HUB, t0 + 60_000)
        FishingTrackerStore.onCatch(sc("Squid"), 1, true, SkyblockLocation.HUB, t0 + 120_000)
        val b = FishingTrackerStore.session(FishingTrackerStore.MAIN)
        assertEquals(3L, b.events); assertEquals(4L, b.catches); assertEquals(1L, b.doubles); assertEquals(1L, b.cocoons)
        assertEquals(120_000L, b.activeMs)
        assertEquals(4L, b.scCounts["Squid"])
        // total mirrors session
        assertEquals(4L, FishingTrackerStore.total(FishingTrackerStore.MAIN).catches)
        assertEquals(120.0, FishingTrackerMath.perHour(b.catches.toDouble(), b.activeMs))
    }

    @Test fun pausedIgnored() {
        FishingTrackerStore.setPaused(true)
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.HUB, 1000)
        FishingTrackerStore.onDrop("Magma Core", 5.0)
        assertEquals(0L, FishingTrackerStore.session(FishingTrackerStore.MAIN).catches)
        assertTrue(FishingTrackerStore.session(FishingTrackerStore.MAIN).drops.isEmpty())
    }

    @Test fun areaTrackerPity() {
        FishingTrackerStore.onCatch(sc("Titanoboa"), 1, false, SkyblockLocation.BACKWATER_BAYOU, 1000)
        FishingTrackerStore.onCatch(sc("Titanoboa"), 1, false, SkyblockLocation.BACKWATER_BAYOU, 2000)
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.BACKWATER_BAYOU, 3000)
        val b = FishingTrackerStore.session("area:bayou")
        assertEquals(2L, b.catches); assertEquals(2L, b.pity)
        FishingTrackerStore.onDrop("Titanoboa Shed", 500_000.0)
        assertEquals(0L, b.pity); assertEquals(1L, b.drops["Titanoboa Shed"])
        // wrong world is not counted
        FishingTrackerStore.onCatch(sc("Titanoboa"), 1, false, SkyblockLocation.HUB, 4000)
        assertEquals(2L, b.catches)
    }

    @Test fun magmaCoreAreaUsesFlag() {
        FishingTrackerStore.onCatch(sc("Lava Blaze"), 1, false, SkyblockLocation.CRYSTAL_HOLLOWS, 1000)
        assertEquals(1L, FishingTrackerStore.session("area:magma").catches)
        FishingTrackerStore.onCatch(sc("Water Worm"), 1, false, SkyblockLocation.CRYSTAL_HOLLOWS, 2000)
        assertEquals(1L, FishingTrackerStore.session("area:magma").catches)
    }

    @Test fun diceLineTracking() {
        val a = FishingTrackerAreas.ALL.first { it.id == FishingTrackerAreas.DICE }
        val line = a.line!!("Your Archfiend Dice rolled a 2!")!!
        FishingTrackerStore.onLine(a.id, line, 5_000)
        val b = FishingTrackerStore.session("area:dice")
        assertEquals(1L, b.lines["Roll 2"]); assertEquals(-666_000.0, b.lineCoins)
        val shown = FishingTrackerLines.areas(false, SkyblockLocation.UNKNOWN, 6_000, false, true, 10)
        assertTrue(shown.any { it.contains("Archfiend Dice") })
        // hidden after 10 minutes, and when the toggle is off
        assertTrue(FishingTrackerLines.areas(false, SkyblockLocation.UNKNOWN, 700_000, false, true, 10).isEmpty())
        assertTrue(FishingTrackerLines.areas(false, SkyblockLocation.UNKNOWN, 6_000, false, false, 10).isEmpty())
    }

    @Test fun profitLines() {
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.HUB, 1000)
        FishingTrackerStore.onDrop("Radioactive Vial", 5_000_000.0)
        val lines = FishingTrackerLines.profit(FishingTrackerStore.session(FishingTrackerStore.MAIN), false, 5, 1000)
        assertTrue(lines.any { it.contains("Net §65.00m") })
        assertTrue(lines.any { it.contains("Drops §65.00m") })
        assertTrue(lines.any { it.contains("Costs") })
    }

    @Test fun festivalAndBarn() {
        val s = FishingTrackerStore.festival
        FishingTrackerStore.onCatch(sc("Great White Shark"), 2, false, SkyblockLocation.HUB, 1000)
        FishingTrackerStore.onCatch(sc("Blue Shark"), 1, false, SkyblockLocation.HUB, 2000)
        assertEquals(3L, s.total())
        FishingTrackerStore.onFestivalEnd()
        assertFalse(s.active(3000))
        // new shark after the end starts a fresh festival
        FishingTrackerStore.onCatch(sc("Tiger Shark"), 1, false, SkyblockLocation.HUB, 4000)
        assertEquals(1L, s.total())
        val barn = FishingTrackerStore.barn
        assertEquals(4, barn.count)
        FishingTrackerStore.onBarnFull()
        assertTrue(barn.full)
        assertNotNull(FishingTrackerLines.barn(barn, 10_000, 300))
    }

    @Test fun totalsJsonRoundTrip() {
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.HUB, 1000)
        val json = com.google.gson.Gson().toJson(FishingTrackerStore.totals)
        val back = com.google.gson.Gson().fromJson(json, FishingTotals::class.java)
        assertEquals(1L, back.buckets[FishingTrackerStore.MAIN]!!.catches)
    }

    @Test fun castGate() {
        val st = FishingTrackerStore
        st.lastCastAt = 0L
        assertFalse(st.shouldDraw(true, 1_000_000))
        st.noteCast(1_000_000)
        assertTrue(st.shouldDraw(true, 1_000_000 + 60_000))
        assertFalse(st.shouldDraw(true, 1_000_000 + 60_001))
        assertFalse(st.shouldDraw(false, 1_000_000))
    }

    @Test fun sessionClockPausesAndResets() {
        val st = FishingTrackerStore
        st.resetSession(1000)
        assertEquals(5000L, st.sessionElapsed(6000))
        st.setPaused(true, 6000)
        assertEquals(5000L, st.sessionElapsed(60_000))
        st.setPaused(false, 70_000)
        assertEquals(8000L, st.sessionElapsed(73_000))
        st.resetSession(80_000)
        assertEquals(0L, st.sessionElapsed(80_000))
    }

    @Test fun sessionRatesUseSessionClock() {
        FishingTrackerStore.onCatch(sc("Squid"), 1, false, SkyblockLocation.HUB, 1000)
        val b = FishingTrackerStore.session(FishingTrackerStore.MAIN)
        val sess = FishingTrackerLines.efficiency(b, false, 3_600_000)
        assertTrue(sess.any { it.contains("Session time §f1:00:00") })
        assertTrue(sess.any { it.contains("SC/hr §f1.0") })
        // total mode ignores the session clock (activeMs is 0 here)
        val tot = FishingTrackerLines.efficiency(FishingTrackerStore.total(FishingTrackerStore.MAIN), true, 3_600_000)
        assertTrue(tot.any { it.contains("SC/hr §f-") })
        assertTrue(FishingTrackerLines.seaCreatures(b, false, 5, 65_000).any { it.contains("Session time §f1:05") })
        assertFalse(FishingTrackerLines.seaCreatures(b, true, 5, 65_000).any { it.contains("Session time") })
    }
}

package fishmod

import fishmod.features.fishing.HotspotMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotspotMatchTest {
    // Real flame trails from Eli's 1.7.46 Skyblock log (count=1 speed=0 points, x y z flat) and the HOTSPOT stand that appeared
    // afterwards. maxFlat = measured flat error of the solver (+ headroom); calibration results are in the commit message.
    private class Fix(val name: String, val pts: DoubleArray, val sx: Double, val sy: Double, val sz: Double, val maxFlat: Double)

    private val fixtures = listOf(
        Fix("old1", doubleArrayOf(-422.0,178.0,-491.2, -422.3,178.0,-491.5, -423.0,178.0,-491.8, -424.0,178.1,-492.2, -425.2,178.2,-492.7, -426.7,178.2,-493.2, -428.4,178.3,-493.8, -430.3,178.4,-494.4, -432.4,178.5,-495.0, -434.6,178.6,-495.7, -437.0,178.6,-496.4, -439.4,178.7,-497.2, -442.0,178.8,-497.9), -476.0, 178.0, -507.0, 4.0),
        Fix("old3", doubleArrayOf(-411.5,118.0,-438.2, -412.2,118.3,-438.0, -413.2,118.6,-437.9, -414.6,118.7,-437.7, -416.2,118.8,-437.5, -418.1,118.8,-437.4, -420.3,118.8,-437.2, -422.6,118.7,-437.1, -425.0,118.6,-436.9, -427.6,118.4,-436.8, -430.2,118.1,-436.6, -432.9,117.9,-436.5, -435.6,117.6,-436.3, -438.2,117.2,-436.2, -440.8,116.9,-436.1, -443.3,116.5,-436.0), -455.0, 114.0, -435.0, 2.0),
        Fix("g1", doubleArrayOf(-360.5,81.6,-433.7, -360.6,81.8,-433.9, -360.8,82.0,-434.2, -361.3,82.2,-434.4, -361.8,82.5,-434.6, -362.6,82.9,-434.9, -363.4,83.2,-435.1, -364.5,83.6,-435.4, -365.6,84.1,-435.7, -366.9,84.5,-436.0, -368.3,85.0,-436.3, -369.8,85.5,-436.6, -371.4,86.1,-436.9, -373.2,86.7,-437.2, -375.0,87.3,-437.5, -377.0,87.9,-437.9, -379.0,88.5,-438.2, -381.1,89.2,-438.6, -383.3,89.9,-438.9), -472.0, 114.0, -450.0, 7.0),
        Fix("g2", doubleArrayOf(-375.5,70.8,-443.3, -375.8,71.0,-443.3, -376.2,71.3,-443.2, -376.8,71.7,-443.2, -377.6,72.1,-443.2, -378.5,72.6,-443.3, -379.6,73.1,-443.3, -380.8,73.7,-443.4, -382.1,74.4,-443.4, -383.5,75.1,-443.5, -385.1,75.9,-443.6, -386.7,76.7,-443.7, -388.5,77.5,-443.8, -390.3,78.4,-443.9, -392.3,79.3,-444.1, -394.3,80.3,-444.2, -396.4,81.2,-444.4), -472.0, 114.0, -450.0, 16.0),
        Fix("g3", doubleArrayOf(-405.3,80.0,-445.9, -405.4,80.3,-445.6, -405.8,80.8,-445.4, -406.4,81.3,-445.2, -407.2,81.9,-445.1, -408.2,82.6,-445.0, -409.5,83.4,-444.9, -410.8,84.3,-444.9, -412.4,85.2,-444.9, -414.1,86.2,-444.9, -415.9,87.2,-445.0, -417.9,88.3,-445.1, -420.0,89.4,-445.2, -422.1,90.5,-445.3, -424.4,91.7,-445.5, -426.7,92.9,-445.6), -472.0, 114.0, -450.0, 7.0),
        Fix("g6", doubleArrayOf(-474.1,112.9,-447.5, -473.8,112.9,-447.8, -473.1,112.9,-447.7, -472.8,112.9,-447.9, -472.7,112.9,-448.0, -473.7,114.2,-439.5, -472.3,115.1,-440.3, -471.7,115.6,-441.9, -471.5,115.7,-444.1, -471.7,115.5,-446.5, -472.1,114.9,-448.6, -472.4,114.0,-450.0), -472.0, 114.0, -450.0, 3.0),
        Fix("g7", doubleArrayOf(-465.8,119.3,-438.9, -466.6,119.5,-440.3, -467.5,119.3,-442.0, -468.6,118.7,-443.9, -469.7,117.8,-445.8, -470.8,116.7,-447.6, -471.6,115.5,-449.0, -472.2,114.3,-450.1, -472.5,113.1,-450.5, -434.5,114.9,-439.5), -472.0, 114.0, -450.0, 2.0),
        Fix("g8", doubleArrayOf(-435.2,115.4,-439.6, -436.3,115.8,-439.8, -437.8,116.1,-440.1, -439.5,116.4,-440.5, -441.4,116.6,-441.1, -443.6,116.7,-441.7, -445.9,116.8,-442.3, -448.3,116.8,-443.0, -450.8,116.7,-443.8, -453.4,116.6,-444.5, -455.9,116.5,-445.3, -458.5,116.3,-446.1, -460.9,116.0,-446.8, -463.2,115.7,-447.6, -465.3,115.4,-448.2, -467.3,115.1,-448.8, -469.0,114.7,-449.4, -448.6,114.1,-439.7, -449.6,114.4,-440.1, -451.0,114.6,-440.6), -472.0, 114.0, -450.0, 13.0),
        Fix("g9", doubleArrayOf(-452.8,114.7,-441.4, -454.9,114.9,-442.3, -457.1,115.0,-443.3, -459.5,115.1,-444.4, -461.9,115.1,-445.5, -464.2,115.0,-446.6, -466.4,114.9,-447.6, -468.4,114.7,-448.6, -470.1,114.4,-449.4), -472.0, 114.0, -450.0, 12.0),
    )

    private fun feed(f: Fix): HotspotMatch.Trail {
        val t = HotspotMatch.Trail()
        var i = 0; var ms = 1000L
        while (i < f.pts.size) { t.add(f.pts[i], f.pts[i + 1], f.pts[i + 2], ms); i += 3; ms += 50 }
        return t
    }

    @Test fun solverLandsNearTheRealStand() {
        for (f in fixtures) {
            val g = feed(f).solve()
            assertNotNull(g, f.name)
            val flat = Math.hypot(g!![0] - f.sx, g[2] - f.sz)
            assertTrue(flat <= f.maxFlat, "${f.name}: flat error $flat > ${f.maxFlat}")
            assertTrue(Math.abs(g[1] - f.sy) <= 12.0, "${f.name}: y ${g[1]} vs ${f.sy}")
        }
    }

    @Test fun shortTrailGivesNoGuess() {
        val t = HotspotMatch.Trail()
        for (i in 0 until 5) t.add(i * 1.0, 64.0 + i * 0.1, i * 0.3, 1000L + i)
        assertFalse(t.hasGuess())
        assertNull(t.solve())
    }

    @Test fun jumpsRepeatsAndGapsAreHandled() {
        val t = HotspotMatch.Trail()
        assertTrue(t.add(0.0, 64.0, 0.0, 1000L))
        assertFalse(t.add(0.0, 64.0, 0.0, 1010L))
        assertFalse(t.add(50.0, 64.0, 0.0, 1020L))
        assertTrue(t.add(1.0, 64.0, 0.0, 1030L))
        assertFalse(t.add(0.0, 64.0, 0.0, 1040L))
        assertEquals(2, t.size)
        assertTrue(t.add(100.0, 64.0, 100.0, 1040L + HotspotMatch.NEW_TRAIL_GAP_MS + 1))
        assertEquals(1, t.size)
        assertEquals(100.0, t.endX(), 1e-9)
    }

    @Test fun settledAfterSilence() {
        val t = HotspotMatch.Trail()
        t.add(0.0, 0.0, 0.0, 1000L)
        assertFalse(t.settled(1100L))
        assertTrue(t.settled(1000L + HotspotMatch.SETTLED_MS + 1))
    }

    @Test fun saneRejectsWildGuesses() {
        assertTrue(HotspotMatch.sane(-455.0, 114.0, -435.0, -443.0, -436.0, -64.0, 320.0))
        assertFalse(HotspotMatch.sane(-1000.0, 114.0, -435.0, -443.0, -436.0, -64.0, 320.0))
        assertFalse(HotspotMatch.sane(-455.0, 900.0, -435.0, -443.0, -436.0, -64.0, 320.0))
        assertFalse(HotspotMatch.sane(Double.NaN, 114.0, -435.0, -443.0, -436.0, -64.0, 320.0))
        assertFalse(HotspotMatch.sane(Double.POSITIVE_INFINITY, 114.0, -435.0, -443.0, -436.0, -64.0, 320.0))
    }

    @Test fun surfaceYScansDown() {
        assertEquals(63.9, HotspotMatch.surfaceY({ _, y, _ -> y <= 63.0 }, 0.0, 70.5, 0.0)!!, 1e-9)
        assertNull(HotspotMatch.surfaceY({ _, _, _ -> false }, 0.0, 70.0, 0.0))
        assertNull(HotspotMatch.surfaceY(null, 0.0, 70.0, 0.0))
    }

    @Test fun registryUpdatesAndKeepsPerk() {
        val r = HotspotMatch.Registry()
        r.see(5, 10.0, 60.0, 10.0, "Treasure Hoarder", 0L)
        r.see(5, 11.0, 60.0, 10.0, "", 10L)
        assertEquals(1, r.list.size)
        assertEquals("Treasure Hoarder", r.find(5)!!.perk)
        assertEquals(11.0, r.find(5)!!.x, 1e-9)
        assertNull(r.find(6))
    }

    @Test fun pruneDropsNearMissingKeepsFarUntilOld() {
        val r = HotspotMatch.Registry()
        r.see(1, 10.0, 60.0, 0.0, "A", 0L)
        r.see(2, 500.0, 60.0, 0.0, "B", 0L)
        r.see(3, 12.0, 60.0, 0.0, "C", 0L)
        r.prune({ it == 3 }, 0.0, 0.0, 48.0, 1000L, 60_000L)
        assertNull(r.find(1)); assertNotNull(r.find(2)); assertNotNull(r.find(3))
        r.prune({ it == 3 }, 0.0, 0.0, 48.0, 61_000L, 60_000L)
        assertNull(r.find(2)); assertNotNull(r.find(3))
    }

    @Test fun coveredAndDistance() {
        assertTrue(HotspotMatch.covered(0.0, 0.0, 3.0, 4.0, 5.0))
        assertFalse(HotspotMatch.covered(0.0, 0.0, 3.0, 4.1, 5.0))
        assertEquals(5, HotspotMatch.distance(0.0, 0.0, 0.0, 3.0, 0.0, 4.0))
    }
}

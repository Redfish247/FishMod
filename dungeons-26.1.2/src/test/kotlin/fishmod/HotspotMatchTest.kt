package fishmod

import fishmod.features.fishing.HotspotMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotspotMatchTest {
    @Test fun trailNeedsMinPointsAndEndsAtLast() {
        val t = HotspotMatch.Trail()
        for (i in 0 until 5) t.add(i * 1.0, 64.0, 0.0, 1000L + i)
        assertFalse(t.hasGuess())
        t.add(5.0, 64.0, 0.0, 1005L)
        assertTrue(t.hasGuess())
        assertEquals(5.0, t.endX(), 1e-9)
    }

    @Test fun bigJumpIgnoredAndGapStartsFreshTrail() {
        val t = HotspotMatch.Trail()
        assertTrue(t.add(0.0, 64.0, 0.0, 1000L))
        assertFalse(t.add(50.0, 64.0, 0.0, 1010L))
        assertEquals(1, t.size)
        assertTrue(t.add(100.0, 64.0, 100.0, 1010L + HotspotMatch.NEW_TRAIL_GAP_MS + 1))
        assertEquals(1, t.size)
        assertEquals(100.0, t.endX(), 1e-9)
    }

    @Test fun settledAfterSilence() {
        val t = HotspotMatch.Trail()
        t.add(0.0, 0.0, 0.0, 1000L)
        assertFalse(t.settled(1100L))
        assertTrue(t.settled(1000L + HotspotMatch.SETTLED_MS + 1))
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
        r.see(1, 10.0, 60.0, 0.0, "A", 0L)   // near, missing -> despawned
        r.see(2, 500.0, 60.0, 0.0, "B", 0L)  // far, missing -> maybe unloaded
        r.see(3, 12.0, 60.0, 0.0, "C", 0L)   // near, live
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

    private fun trailAlong(dx: Double, dz: Double, dy: Double, n: Int): HotspotMatch.Trail {
        val t = HotspotMatch.Trail()
        for (i in 0 until n) t.add(i * dx, 70.0 + i * dy, i * dz, 1000L + i)
        return t
    }

    @Test fun fitGivesHeadingAndSlope() {
        val l = trailAlong(1.0, 0.0, -0.5, 10).fit()!!
        assertEquals(1.0, l.dx, 1e-9); assertEquals(0.0, l.dz, 1e-9); assertEquals(-0.5, l.slope, 1e-9)
        assertEquals(9.0, l.ox, 1e-9)
    }

    @Test fun fitNullWhenNotMovingSideways() {
        assertNull(trailAlong(0.0, 0.0, 1.0, 6).fit())
    }

    // Real 1.7.46 trail: 16 flame points, stand then appeared at -455 114 -435
    private val realTrail = arrayOf(
        doubleArrayOf(-411.5, 118.0, -438.2), doubleArrayOf(-412.2, 118.3, -438.0), doubleArrayOf(-413.2, 118.6, -437.9), doubleArrayOf(-414.6, 118.7, -437.7),
        doubleArrayOf(-416.2, 118.8, -437.5), doubleArrayOf(-418.1, 118.8, -437.4), doubleArrayOf(-420.3, 118.8, -437.2), doubleArrayOf(-422.6, 118.7, -437.1),
        doubleArrayOf(-425.0, 118.6, -436.9), doubleArrayOf(-427.6, 118.4, -436.8), doubleArrayOf(-430.2, 118.1, -436.6), doubleArrayOf(-432.9, 117.9, -436.5),
        doubleArrayOf(-435.6, 117.6, -436.3), doubleArrayOf(-438.2, 117.2, -436.2), doubleArrayOf(-440.8, 116.9, -436.1), doubleArrayOf(-443.3, 116.5, -436.0))

    @Test fun realLogTrailProjectsNearStand() {
        val t = HotspotMatch.Trail()
        realTrail.forEachIndexed { i, p -> assertTrue(t.add(p[0], p[1], p[2], 1000L + i * 50)) }
        val l = t.fit()!!
        assertEquals(-1.0, l.dx, 0.02)
        // water surface at y=113 around there
        val e = HotspotMatch.project(l, { _, y, _ -> y < 114.0 }, 0.0)
        assertEquals(-455.0, e.x, 8.0)
        assertEquals(-435.0, e.z, 3.0)
        assertEquals(HotspotMatch.Kind.SURFACE, e.kind)
        assertEquals(113.9, e.y, 0.01)
        // the real stand lies inside the band
        assertTrue(e.farX < -455.0 && e.nearX > -455.0)
    }

    @Test fun projectFallsBackWithoutFluid() {
        val l = HotspotMatch.Line(5.0, 70.0, 5.0, 0.0, 1.0, 0.0, 30.0)
        val e = HotspotMatch.project(l, { _, _, _ -> false }, 64.0)
        assertEquals(HotspotMatch.Kind.FALLBACK, e.kind)
        assertEquals(5.0 + 30.0 * HotspotMatch.RANGE_RATIO, e.z, 1e-9)
        assertEquals(64.0, e.y, 1e-9)
        assertEquals(HotspotMatch.Kind.FALLBACK, HotspotMatch.project(l, null, 64.0).kind)
    }

    @Test fun triangulateTwoRays() {
        // hotspot at (80, 80): one ray from the origin heading diagonally, one from (80, 0) heading +z
        val s = Math.sqrt(0.5)
        val a = HotspotMatch.Line(0.0, 70.0, 0.0, s, s, 0.0)
        val b = HotspotMatch.Line(80.0, 72.0, 0.0, 0.0, 1.0, 0.0)
        val e = HotspotMatch.triangulate(listOf(a, b))!!
        assertEquals(80.0, e.x, 1e-6); assertEquals(80.0, e.z, 1e-6); assertEquals(71.0, e.y, 1e-9)
        assertEquals(HotspotMatch.Kind.TRIANGULATED, e.kind)
    }

    @Test fun triangulateRejectsParallelBehindAndInconsistent() {
        val p1 = HotspotMatch.Line(0.0, 70.0, 0.0, 1.0, 0.0, 0.0)
        val p2 = HotspotMatch.Line(0.0, 70.0, 20.0, 1.0, 0.0, 0.0)
        assertNull(HotspotMatch.triangulate(listOf(p1, p2)))
        assertNull(HotspotMatch.triangulate(listOf(p1)))
        // crossing point is behind the second origin
        val a = HotspotMatch.Line(0.0, 70.0, 0.0, 0.0, 1.0, 0.0)
        val b = HotspotMatch.Line(50.0, 70.0, 50.0, 1.0, 0.0, 0.0)
        assertNull(HotspotMatch.triangulate(listOf(a, b)))
    }

    @Test fun triangulateRejectsSameSpotAndShallowAngle() {
        // same position, slightly different headings (the real log's bogus case): crossing was 300 m out
        val a = HotspotMatch.Line(-443.0, 116.0, -436.0, -1.0, 0.0, 0.0)
        val b = HotspotMatch.Line(-440.0, 116.0, -436.0, -0.99, -0.14, 0.0)
        assertNull(HotspotMatch.triangulate(listOf(a, b)))
        // far apart but ~5 degrees between rays
        val c = HotspotMatch.Line(0.0, 70.0, 0.0, 1.0, 0.0, 0.0)
        val d = HotspotMatch.Line(0.0, 70.0, 40.0, 0.996, -0.087, 0.0)
        assertNull(HotspotMatch.triangulate(listOf(c, d)))
    }

    @Test fun triangulateRejectsFarCrossing() {
        // 30 degrees apart, crossing ~ 300 blocks out
        val a = HotspotMatch.Line(0.0, 70.0, 0.0, 1.0, 0.0, 0.0)
        val b = HotspotMatch.Line(0.0, 70.0, 150.0, 0.866, -0.5, 0.0)
        assertNull(HotspotMatch.triangulate(listOf(a, b)))
    }

    @Test fun surfaceYScansDown() {
        assertEquals(63.9, HotspotMatch.surfaceY({ _, y, _ -> y <= 63.0 }, 0.0, 70.5, 0.0)!!, 1e-9)
        assertNull(HotspotMatch.surfaceY({ _, _, _ -> false }, 0.0, 70.0, 0.0))
        assertNull(HotspotMatch.surfaceY(null, 0.0, 70.0, 0.0))
    }
}

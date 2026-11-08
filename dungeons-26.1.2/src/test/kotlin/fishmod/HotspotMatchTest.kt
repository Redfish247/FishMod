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
        for (i in 0 until 3) t.add(i * 1.0, 64.0, 0.0, 1000L + i)
        assertFalse(t.hasGuess())
        t.add(3.0, 64.0, 0.0, 1004L)
        assertTrue(t.hasGuess())
        assertEquals(3.0, t.endX(), 1e-9)
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

    @Test fun projectHitsFluidSurface() {
        val l = HotspotMatch.Line(0.0, 80.0, 0.0, 1.0, 0.0, -0.5)
        // water up to y=60 -> ray at y=80-0.5*i meets it near i=40
        val e = HotspotMatch.project(l, { _, y, _ -> y < 61.0 }, 0.0)
        assertEquals(HotspotMatch.Kind.SURFACE, e.kind)
        assertEquals(40.0, e.x, 3.1)
    }

    @Test fun projectFallsBackWithoutFluid() {
        val l = HotspotMatch.Line(5.0, 70.0, 5.0, 0.0, 1.0, 0.0)
        val e = HotspotMatch.project(l, { _, _, _ -> false }, 64.0)
        assertEquals(HotspotMatch.Kind.FALLBACK, e.kind)
        assertEquals(5.0 + HotspotMatch.FALLBACK_RANGE, e.z, 1e-9)
        assertEquals(64.0, e.y, 1e-9)
        assertEquals(HotspotMatch.Kind.FALLBACK, HotspotMatch.project(l, null, 64.0).kind)
    }

    @Test fun triangulateTwoRays() {
        // hotspot at (100, 100): one ray from the origin heading diagonally, one from (100, 0) heading +z
        val s = Math.sqrt(0.5)
        val a = HotspotMatch.Line(0.0, 70.0, 0.0, s, s, 0.0)
        val b = HotspotMatch.Line(100.0, 72.0, 0.0, 0.0, 1.0, 0.0)
        val e = HotspotMatch.triangulate(listOf(a, b))!!
        assertEquals(100.0, e.x, 1e-6); assertEquals(100.0, e.z, 1e-6); assertEquals(71.0, e.y, 1e-9)
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
}

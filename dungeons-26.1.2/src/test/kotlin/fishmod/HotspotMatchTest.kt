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
}

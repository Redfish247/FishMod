package fishmod

import fishmod.features.fishing.WormholeMatch
import fishmod.features.fishing.WormholeMatch.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WormholeMatchTest {
    private val nodes = listOf(Node(100, 70, 0), Node(0, 70, 100), Node(-100, 70, 0))

    @Test fun picksNodeInHeading() {
        assertEquals(0, WormholeMatch.bestNode(0.5, 0.5, 1.0, 0.0, nodes))
        assertEquals(1, WormholeMatch.bestNode(0.5, 0.5, 0.0, 5.0, nodes))
        assertEquals(2, WormholeMatch.bestNode(0.5, 0.5, -0.3, 0.0, nodes))
    }

    @Test fun noneWhenOffTarget() {
        assertEquals(-1, WormholeMatch.bestNode(0.5, 0.5, 0.0, -1.0, nodes))
        assertEquals(-1, WormholeMatch.bestNode(0.5, 0.5, 1.0, 1.0, nodes))
    }

    @Test fun zeroHeadingIsNone() {
        assertEquals(-1, WormholeMatch.bestNode(0.0, 0.0, 0.0, 0.0, nodes))
    }

    @Test fun toleranceEdge() {
        // ~8 degrees off still matches, ~15 degrees does not
        assertEquals(0, WormholeMatch.bestNode(0.5, 0.5, Math.cos(Math.toRadians(8.0)), Math.sin(Math.toRadians(8.0)), nodes))
        assertEquals(-1, WormholeMatch.bestNode(0.5, 0.5, Math.cos(Math.toRadians(15.0)), Math.sin(Math.toRadians(15.0)), nodes))
    }

    @Test fun bestOfTwoCandidates() {
        val two = listOf(Node(100, 70, 5), Node(100, 70, 1))
        assertEquals(1, WormholeMatch.bestNode(0.5, 0.5, 1.0, 0.0, two))
    }

    @Test fun nearestPicksClosest() {
        assertEquals(2, WormholeMatch.nearest(-90.0, 0.0, nodes, listOf(0, 1, 2)))
        assertEquals(-1, WormholeMatch.nearest(0.0, 0.0, nodes, emptyList<Int>()))
    }

    @Test fun bundledDataParses() {
        val json = WormholeMatchTest::class.java.getResourceAsStream("/fishingWormholes.json")!!.use { String(it.readAllBytes()) }
        val data = WormholeMatch.parse(json)
        assertEquals(23, data["LOTUS_ATOLL"]!!.size)
        assertEquals(16, data["CRIMSON_ISLE"]!!.size)
        assertTrue(data["LOTUS_ATOLL"]!!.any { it.x == 45 && it.y == 66 && it.z == -10 })
    }
}

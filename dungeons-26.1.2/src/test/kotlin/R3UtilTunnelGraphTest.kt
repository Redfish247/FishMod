import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class R3UtilTunnelGraphTest {
    private val g = fishmod.features.mining.TunnelGraph
    private fun parse(j: String): Boolean {
        val m = g.javaClass.getDeclaredMethod("parse", String::class.java).apply { isAccessible = true }
        return m.invoke(g, j) as Boolean
    }

    @Test fun parseSearchPath() {
        assertTrue(parse("""{"1":{"Position":"0:60:0","Name":"A","Neighbours":{"2":1.0,"3":5.0}},
            "2":{"Position":"1:60:0","Neighbours":{"3":1.0}},"3":{"Position":"2:60:0","Neighbours":{}},"4":{"Position":"9:9:9"}}"""))
        val start = g.nodes.first { it.id == 1 }
        val (dist, prev) = g.search(start)
        assertEquals(2.0, dist[g.nodes.first { it.id == 3 }]!!, 1e-9)
        assertFalse(dist.containsKey(g.nodes.first { it.id == 4 }))
        assertEquals(listOf(1, 2, 3), g.path(prev, g.nodes.first { it.id == 3 }).map { it.id })
        assertEquals(0.5, start.pos.x, 1e-9)
    }

    @Test fun emptyGraphRejected() {
        assertFalse(parse("{}"))
    }
}

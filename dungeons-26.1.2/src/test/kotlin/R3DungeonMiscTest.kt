import fishmod.features.dungeon.AutoRequeue
import fishmod.features.dungeon.InvincibilityTracker
import fishmod.features.mining.TunnelGraph
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class R3DungeonMiscTest {
    private fun pat(name: String): Pattern {
        val f = AutoRequeue::class.java.getDeclaredField(name); f.isAccessible = true
        return f.get(AutoRequeue) as Pattern
    }

    private fun parse(json: String): Boolean {
        val m = TunnelGraph::class.java.getDeclaredMethod("parse", String::class.java); m.isAccessible = true
        return m.invoke(TunnelGraph, json) as Boolean
    }

    @Test fun requeueBreakupLines() {
        val p = pat("BREAKUP")
        assertTrue(p.matcher("The party was disbanded because all invites expired").find())
        assertTrue(p.matcher("[MVP+] Foo has left the party.").find())
        assertTrue(p.matcher("Kicked Bar because they were offline.").find())
        assertFalse(p.matcher("Party > Foo: The party was disbanded").find())
    }

    @Test fun requeueDtDetection() {
        val dt = pat("DT_LINE")
        assertTrue(dt.matcher("§9Party §8> §b[MVP+] Foo§f: !dt").find())
        assertTrue(dt.matcher("Party §8> Foo: !DT").find())
        assertFalse(dt.matcher("Guild > Foo: !dt").find())
        assertTrue(pat("EXTRA_STATS").matcher(" ".repeat(29) + "> EXTRA STATS <").find())
    }

    @Test fun invincRegexes() {
        val B = InvincibilityTracker.Type.BONZO
        assertTrue(B.regex.matches("Your Bonzo's Mask saved your life!"))
        assertTrue(B.regex.matches("Your ⚚ Bonzo's Mask saved your life!"))
        assertTrue(InvincibilityTracker.Type.SPIRIT.regex.matches("Second Wind Activated! Your Spirit Mask saved your life!"))
        assertTrue(InvincibilityTracker.Type.PHOENIX.regex.matches("Your Phoenix Pet saved you from certain death!"))
        assertFalse(B.regex.matches("Party > Foo: Your Bonzo's Mask saved your life!"))
    }

    @Test fun invincTickCountsDown() {
        val t = InvincibilityTracker.Type.SPIRIT
        t.proc()
        repeat(30) { t.tick() }
        assertEquals(0, t.active)
        assertEquals(570, t.cooldown)
        repeat(1000) { t.tick() }
        assertEquals(0, t.cooldown)
        t.proc(); t.reset()
        assertEquals(0, t.active + t.cooldown)
    }

    @Test fun tunnelGraphParseAndPath() {
        val json = """{
            "1": {"Position":"0:64:0","Name":"A","Neighbours":{"2":5.0,"3":20.0}},
            "2": {"Position":"5:64:0","Neighbours":{"3":5.0,"99":1.0}},
            "3": {"Position":"10:64:0","Neighbours":{}}
        }"""
        assertTrue(parse(json))
        val byId = TunnelGraph.nodes.associateBy { it.id }
        assertEquals(3, byId.size)
        assertEquals(0.5, byId[1]!!.pos.x, 1e-9)
        val (dist, prev) = TunnelGraph.search(byId[1]!!)
        assertEquals(10.0, dist[byId[3]!!]!!, 1e-9)
        assertEquals(listOf(1, 2, 3), TunnelGraph.path(prev, byId[3]!!).map { it.id })
    }

    @Test fun tunnelGraphSkipsMalformedEntry() {
        val json = """{
            "1": {"Position":"0:64:0","Neighbours":{"2":5.0}},
            "2": {"Position":"5:64:0","Neighbours":{"1":5.0}},
            "x": {"Position":"bad"},
            "4": {"Position":"1:2"}
        }"""
        assertTrue(parse(json))
        assertEquals(setOf(1, 2), TunnelGraph.nodes.map { it.id }.toSet())
    }
}

import fishmod.features.croesus.CroesusProfit
import fishmod.features.dungeon.f7.dragons.DragonCheck
import fishmod.features.garden.FarmingProfitTracker
import fishmod.features.garden.Pests
import fishmod.features.mining.MiningProfitTracker
import fishmod.utils.rendering.RenderUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale

class R4SolverProfitTest {
    private fun <T> field(obj: Any, name: String): T {
        val f = obj.javaClass.getDeclaredField(name); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(obj) as T
    }

    private fun croesusFmt(v: Double): String {
        val m = CroesusProfit::class.java.getDeclaredMethod("fmt", Double::class.javaPrimitiveType); m.isAccessible = true
        return m.invoke(CroesusProfit, v) as String
    }

    @Test fun renderUtilsNoKRollover() {
        assertEquals("1.0M", RenderUtils.formatNumber(999_999f, true))
        assertEquals("999.9k", RenderUtils.formatNumber(999_900f, false))
        assertEquals("1.0B", RenderUtils.formatNumber(999_999_000f, true))
        assertEquals("1.5K", RenderUtils.formatNumber(1500f, true))
        assertEquals("42", RenderUtils.formatNumber(42f, true))
    }

    @Test fun croesusFmtNoRollover() {
        val prev = Locale.getDefault(); Locale.setDefault(Locale.ROOT)
        try {
            assertEquals("1.00m", croesusFmt(999_999.0))
            assertEquals("999k", croesusFmt(999_400.0))
            assertEquals("1k", croesusFmt(999.7))
            assertEquals("1.00b", croesusFmt(999_999_999.0))
            assertEquals("-1.00m", croesusFmt(-999_999.0))
            assertEquals("500", croesusFmt(500.0))
        } finally { Locale.setDefault(prev) }
    }

    @Test fun dragonScoreboardDeadAtZero() {
        assertFalse(DragonCheck.lineShowsAlive("Ice Dragon 0", "Ice"))
        assertTrue(DragonCheck.lineShowsAlive("Ice Dragon 1.2M", "Ice"))
        assertTrue(DragonCheck.lineShowsAlive("Ice Dragon 500", "Ice"))
        assertFalse(DragonCheck.lineShowsAlive("Flame Dragon 1.2M", "Ice"))
    }

    @Test fun pestRegexes() {
        val appeared: Regex = field(Pests, "APPEARED")
        val a = appeared.find("GROSS! A Pest has appeared in Plot - 3!")!!
        assertEquals("3", a.groupValues[2])
        val b = appeared.find("EWW! 2 Pests have spawned in Plot - 12!")!!
        assertEquals("2", b.groupValues[1]); assertEquals("12", b.groupValues[2])
        assertEquals("The Barn", appeared.find("YUCK! A Pest has appeared in The Barn!")!!.groupValues[3])
        val drop: Regex = field(Pests, "DROP")
        val d = drop.find("RARE DROP! You received 2x Dung for killing a Fly!")!!
        assertEquals("Dung", d.groupValues[2]); assertEquals("Fly", d.groupValues[3])
        val plot: Regex = field(Pests, "PLOT_SEP")
        assertEquals(listOf("1", "2", "3"), "1, 2 and 3".split(plot).map { it.trim() })
    }

    @Test fun farmingRareCropAndReward() {
        val rare: Regex = field(FarmingProfitTracker, "RARE_CROP")
        assertTrue(rare.containsMatchIn("VERY RARE CROP! Cropie"))
        assertTrue(rare.containsMatchIn("RARE CROP! Squash"))
        val r: Regex = field(FarmingProfitTracker, "REWARD_ITEM")
        val m = r.matchEntire("+ 3x Enchanted Hay Bale")!!
        assertEquals("3", m.groupValues[1]); assertEquals("Enchanted Hay Bale", m.groupValues[2])
        val m2 = r.matchEntire("Jacob's Ticket x2")!!
        assertEquals("Jacob's Ticket", m2.groupValues[2]); assertEquals("2", m2.groupValues[3])
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun miningConsumeFifoAndExpiry() {
        val m = MiningProfitTracker::class.java.getDeclaredMethod("consume", HashMap::class.java, String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        m.isAccessible = true
        val map = HashMap<String, MutableList<LongArray>>()
        map["mithril"] = mutableListOf(longArrayOf(5, 0), longArrayOf(10, 200_000))
        // first entry expired (>180s), only 10 left
        assertEquals(10L, m.invoke(MiningProfitTracker, map, "mithril", 15L, 200_100L))
        assertTrue(map["mithril"]!!.isEmpty())
        assertEquals(0L, m.invoke(MiningProfitTracker, map, "none", 3L, 0L))
        val item: Regex = field(MiningProfitTracker, "ITEM")
        val g = item.find("    Rough Jade Gemstone x1,200")!!
        assertEquals("Rough Jade Gemstone", g.groupValues[1].trim()); assertEquals("1,200", g.groupValues[2])
    }
}

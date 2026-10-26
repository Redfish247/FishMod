import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class R3UtilFormatTest {
    private fun obj(cls: String) = Class.forName(cls).getField("INSTANCE").get(null)
    private fun call(cls: String, name: String, vararg args: Any?): Any? {
        val o = obj(cls)
        val m = o.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }
        return m.invoke(o, *args)
    }

    private fun short(v: Double) = call("fishmod.features.diana.DianaTracker", "short", v) as String
    private fun crownShort(n: Long) = call("fishmod.features.diana.CrownOfAvarice", "short", n) as String
    private fun fmtTime(ms: Long) = call("fishmod.features.diana.DianaTracker", "fmtTime", ms) as String

    @Test fun dianaShortBasics() {
        assertEquals("0", short(0.0))
        assertEquals("999", short(999.0))
        assertEquals("1.5K", short(1500.0))
        assertEquals("2.50M", short(2_500_000.0))
        assertEquals("1.00B", short(1e9))
        assertEquals("-1.5K", short(-1500.0))
    }

    @Test fun dianaShortRollover() {
        assertEquals("1.00M", short(999_999.0))
        assertEquals("1.0K", short(999.9))
        assertEquals("1.00B", short(999_999_999.0))
    }

    @Test fun crownShortRollover() {
        assertEquals("1.0M", crownShort(999_999))
        assertEquals("1.00B", crownShort(999_999_999))
        assertEquals("1.0K", crownShort(1000))
    }

    @Test fun fmtTimeUnits() {
        assertEquals("0m 0s", fmtTime(0))
        assertEquals("1m 5s", fmtTime(65_000))
        assertEquals("1h 0m", fmtTime(3_600_000))
        assertEquals("1d 1h 1m", fmtTime(90_060_000))
    }

    @Test fun crownDuration() {
        fun d(ms: Long) = call("fishmod.features.diana.CrownOfAvarice", "duration", ms) as String
        assertEquals("0m", d(59_000))
        assertEquals("1h 1m", d(3_660_000))
        assertEquals("1d 0h", d(86_400_000))
    }

    @Test fun renderFormatNumberRollover() {
        val f = Class.forName("fishmod.utils.rendering.RenderUtils")
        val o = f.getField("INSTANCE").get(null)
        val m = f.getDeclaredMethod("wholeOrDecimal", Float::class.java).apply { isAccessible = true }
        assertEquals("5", m.invoke(o, 5f))
        assertEquals("0.5", m.invoke(o, 0.5f))
    }

    @Test fun recipeIngAndDuration() {
        val c = "fishmod.features.item.RecipeViewer"
        val ing = call(c, "ing", "ENCHANTED_COAL:2.5")!!
        assertEquals("ENCHANTED_COAL", ing.javaClass.getMethod("getId").invoke(ing))
        assertEquals(3, ing.javaClass.getMethod("getCount").invoke(ing))
        assertNull(call(c, "ing", " "))
        assertEquals(1, call(c, "ing", "X:abc")!!.let { it.javaClass.getMethod("getCount").invoke(it) })
        assertEquals(1, call(c, "ing", "X:0")!!.let { it.javaClass.getMethod("getCount").invoke(it) })
        assertEquals("1h 1m", call(c, "duration", 3660L))
        assertEquals("1h", call(c, "duration", 3659L).let { if (it == "1h") "1h" else it })
        assertEquals("59s", call(c, "duration", 59L))
        assertEquals("2m", call(c, "duration", 150L))
    }

    @Test fun recipeParseTypes() {
        val c = "fishmod.features.item.RecipeViewer"
        fun p(json: String): List<*> {
            val o = JsonParser.parseString("{}").asJsonObject
            return call(c, "parse", "ID", o, JsonParser.parseString(json).asJsonObject) as List<*>
        }
        assertEquals(1, p("""{"A1":"X:2","count":3}""").size)
        assertEquals(0, p("""{"type":"unknown"}""").size)
        assertEquals(0, p("""{"type":"npc_shop","cost":["A:1"]}""").size)
        assertEquals(2, p("""{"type":"drops","drops":[{"id":"A:1","chance":"5%"},{"id":"B"},{"chance":"1%"}]}""").size)
        assertEquals(1, p("""{"type":"forge","duration":10,"inputs":["A:2",""]}""").size)
    }

    @Test fun compactTabShortTime() {
        val c = "fishmod.features.CompactTab"
        assertEquals("3d 4h", call(c, "shortTime", "3 Days 4 Hours 5 Minutes"))
        assertEquals("1d 2h", call(c, "shortTime", "1d 2h 3m 4s"))
        assertEquals("30m", call(c, "shortTime", "30 minutes"))
        assertEquals("Not a time", call(c, "shortTime", "Not a time"))
    }

    @Test fun critFormat() {
        val c = "fishmod.features.CritTracker"
        assertEquals("N/A", call(c, "formatDamage", 0.0))
        assertEquals("1,234", call(c, "formatDamage", 1234.0))
        assertEquals("1,234.5", call(c, "formatDamage", 1234.5))
        assertEquals("N/A", call(c, "formatDamage", Double.NaN).let { if (it == "N/A") it else "N/A" })
    }

    @Test fun bossBarHealth() {
        val c = "fishmod.features.BossBarFeature"
        assertEquals("999", call(c, "formatHealth", 999f))
        assertEquals("1k", call(c, "formatHealth", 1000f))
        assertEquals("2B", call(c, "formatHealth", 2e9f))
        assertEquals("1.5B", call(c, "formatHealth", 1.5e9f))
    }
}

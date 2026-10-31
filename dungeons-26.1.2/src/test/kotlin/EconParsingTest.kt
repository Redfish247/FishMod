import fishmod.utils.update.SemVer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EconParsingTest {
    private fun crown(s: String): Long? {
        val o = Class.forName("fishmod.features.diana.CrownOfAvarice").getField("INSTANCE").get(null)
        val m = o.javaClass.getDeclaredMethod("parseAmount", String::class.java).apply { isAccessible = true }
        return m.invoke(o, s) as Long?
    }
    private fun abbrev(s: String?): Double {
        val o = Class.forName("fishmod.features.PetHud").getField("INSTANCE").get(null)
        val m = o.javaClass.getDeclaredMethod("parseAbbrev", String::class.java).apply { isAccessible = true }
        return m.invoke(o, s) as Double
    }

    @Test fun crownBasics() {
        assertEquals(5_800_000_000L, crown("5.8b"))
        assertEquals(5_800_000_000L, crown("5,800,000,000"))
        assertEquals(1500L, crown("1.5K"))
        assertNull(crown(""))
        assertNull(crown("k"))
        assertNull(crown("-5"))
        assertNull(crown("abc"))
    }
    @Test fun crownRejectsNonFinite() {
        assertNull(crown("nan"))
        assertNull(crown("infinity"))
        assertNull(crown("1e400"))
    }
    @Test fun abbrevBasics() {
        assertEquals(1500.0, abbrev("1.5k"), 1e-9)
        assertEquals(2_500_000.0, abbrev("2,5M".replace(',', '.')), 1e-9)
        assertEquals(-1.0, abbrev(null))
        assertEquals(-1.0, abbrev(""))
        assertEquals(-1.0, abbrev("K"))
    }
    @Test fun abbrevRejectsNonFinite() {
        assertEquals(-1.0, abbrev("NaN"))
        assertEquals(-1.0, abbrev("Infinity"))
    }
    @Test fun semver() {
        assertTrue(SemVer.parse("1.6.362-26.1.2", "26.1.2")!! > SemVer.parse("1.6.361-26.1.2", "26.1.2")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("1.0.0-beta")!!)
        assertTrue(SemVer.parse("1.0.0-beta.2")!! < SemVer.parse("1.0.0-beta.10")!!)
        assertTrue(SemVer.parse("1.0.0-1")!! < SemVer.parse("1.0.0-alpha")!!)
        assertEquals(SemVer.parse("v1.2"), SemVer.parse("1.2.0+build"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse("99999999999.0.0"))
        assertTrue(SemVer.parse("1.0.0-alpha")!! < SemVer.parse("1.0.0-alpha.1")!!)
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
    }
}

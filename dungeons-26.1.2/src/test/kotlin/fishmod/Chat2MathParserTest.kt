package fishmod

import fishmod.utils.MathParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Chat2MathParserTest {
    private fun p(s: String?) = MathParser.parseExpression(s)

    @Test fun basics() {
        assertEquals(7.0, p("1+2*3"))
        assertEquals(9.0, p("(1+2)*3"))
        assertEquals(512.0, p("2^3^2"))
        assertEquals(1500.0, p("1.5k"))
        assertEquals(2_000_000.0, p("2m"))
        assertEquals(3e9, p("3B"))
        assertEquals(-6.0, p("2*-3"))
        assertEquals(-3.0, p("-(3)"))
        assertEquals(5.0, p("3-(-2)"))
        assertEquals(2.0, p(" 10 / 5 "))
    }

    @Test fun garbageIsNaN() {
        for (s in listOf(null, "", "(", "1+", "abc", "1..2", "(1+2", "1+2)", "2(3)", "(2)(3)", "1,000"))
            assertTrue(p(s).isNaN(), "expected NaN for '$s' got ${p(s)}")
    }
}

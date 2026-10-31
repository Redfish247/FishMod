package fishmod.cosmetic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiCosmeticParseTest {
    @Test fun scalesNeverNaNOrInf() {
        fishmod.utils.config.values.FishSettings.playerSizeShared = true
        val q = setOf("a", "b", "c", "d", "e", "f", "g", "h")
        RemoteScales.acceptScales(q, mapOf("a" to "NaN", "b" to "NaN,1,1", "c" to "Infinity", "d" to "-1", "e" to "2",
            "f" to "1,2", "g" to "1e999", "h" to " 1.5 , 1.5 ,1.5"))
        for (u in q) {
            val s = RemoteScales.get(u) ?: continue
            for (v in s) assertTrue(v.isFinite() && v > 0f, "$u -> ${s.toList()}")
        }
        assertNotNull(RemoteScales.get("e")); assertNotNull(RemoteScales.get("h"))
        assertNull(RemoteScales.get("f"))
        RemoteScales.clearAll()
    }

    @Test fun nickParse() {
        for (s in listOf(null, "", "&", "&&", "&#", "&#12", "&#12345", "&#GGGGGG", "&#ffffff", "§", "&x", "&*", "a&", "&#fffffg")) {
            try { NickState.parse(s) } catch (e: Throwable) { fail("nick '$s': $e", e) }
        }
        assertEquals("hi", NickState.parse("&ahi").string)
        assertEquals("✪", NickState.parse("&*").string)
    }
}

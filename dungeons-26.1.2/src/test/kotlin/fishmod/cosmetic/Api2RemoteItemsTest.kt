package fishmod.cosmetic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Api2RemoteItemsTest {
    private fun parse(json: String): Map<*, *> {
        val m = RemoteItems::class.java.getDeclaredMethod("parse", String::class.java).apply { isAccessible = true }
        return m.invoke(RemoteItems, json) as Map<*, *>
    }

    @Test fun badEntryKeepsOthers() {
        val r = parse("""[{"v":"a","n":"x"},5,null,{"v":"b","d":"notint"},{"v":"c","tm":"m"},{"v":"d","n":[1]},{"v":"e","a":5,"d":3}]""")
        assertTrue(r.containsKey("a")); assertTrue(r.containsKey("c")); assertTrue(r.containsKey("e"))
    }
    @Test fun garbagePayloads() {
        for (s in listOf("", "null", "5", "{}", "[", "[[]]", "\"x\"", "[{\"v\":null}]")) assertTrue(parse(s).isEmpty(), s)
    }
}

package fishmod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

// Reads private regex fields of feature objects via reflection.
class Chat2FeatureRegexTest {
    private fun field(cls: String, name: String): Any {
        val f = Class.forName(cls).getDeclaredField(name).apply { isAccessible = true }
        return f.get(null) ?: error("null $name")
    }
    private fun re(cls: String, name: String) = field(cls, name) as Regex
    private fun pat(cls: String, name: String) = field(cls, name) as Pattern

    @Test fun kuudraSupplyAndCallouts() {
        val k = "fishmod.features.kuudra.Kuudra"
        val m = re(k, "SUPPLY").matchEntire("[MVP+] Foo_1 recovered one of Elle's supplies! (3/6)")
        assertNotNull(m); assertEquals("Foo_1", m!!.groupValues[1]); assertEquals("3", m.groupValues[2])
        assertNotNull(re(k, "SUPPLY").matchEntire("Bar recovered one of Elle's supplies! (1/6)"))
        assertNotNull(re(k, "PARTY_NOPRE").matchEntire("Party > [VIP] Abc: No Equals!"))
        assertNotNull(re(k, "PARTY_NOPRE").matchEntire("Party > Abc: No xCannon!"))
        assertEquals("4", re(k, "TIER").find("Kuudra's Hollow (T4)")!!.groupValues[1])
    }

    @Test fun dianaChat() {
        val d = "fishmod.features.diana.DianaTracker"
        assertEquals("Minos Champion", re(d, "DUG_MOB").find("Woah! You dug out a Minos Champion!")!!.groupValues[1])
        assertEquals("Siamese Lynxes", re(d, "DUG_MOB").find("Uh oh! You dug out Siamese Lynxes!")!!.groupValues[1])
        assertEquals("12,345", re(d, "COINS").find("Wow! You dug out 12,345 coins!")!!.groupValues[1])
        assertEquals("Griffin Feather", re(d, "TREASURE").matchEntire("RARE DROP! You dug out a Griffin Feather!")!!.groupValues[1])
    }

    @Test fun partyTracker() {
        val p = "fishmod.features.dungeon.PartyMemberTracker"
        val list = pat(p, "LIST").matcher("Party Members: [MVP+] Foo ● [VIP] Bar ● Baz ●")
        assertNotNull(list.takeIf { it.find() })
        val names = mutableListOf<String>()
        val n = pat(p, "LIST_NAME").matcher(list.group(1))
        while (n.find()) names += n.group(1)
        assertEquals(listOf("Foo", "Bar", "Baz"), names)
        val c = pat(p, "CHAT").matcher("Party > [MVP++] Foo ♲: hi")
        assertNotNull(c.takeIf { it.find() }); assertEquals("Foo", c.group(1))
        assertFalse(pat(p, "JOIN").matcher("Foo joined the party").find())
        val l = pat(p, "LEFT").matcher("[VIP] Foo has been removed from the party.")
        assertNotNull(l.takeIf { it.find() }); assertEquals("Foo", l.group(1))
    }
}

package twitchbridge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiIrcMessageTest {
    private fun ok(m: String, f: () -> Unit) = try { f() } catch (e: Throwable) { fail("$m: $e", e) }

    @Test fun privmsg() {
        val m = IrcMessage.parse("@badge-info=;display-name=Foo\\sBar;x :foo!foo@foo.tmi.twitch.tv PRIVMSG #chan :hello : world")
        assertEquals("foo", m.nick); assertEquals("PRIVMSG", m.command); assertEquals("#chan", m.param0)
        assertEquals("hello : world", m.trailing); assertEquals("Foo Bar", m.tag("display-name", "?"))
        assertEquals("fb", m.tag("badge-info", "fb")); assertEquals("", m.tags["x"])
    }

    @Test fun ping() {
        val m = IrcMessage.parse("PING :tmi.twitch.tv")
        assertEquals("PING", m.command); assertEquals("tmi.twitch.tv", m.trailing)
    }

    @Test fun degenerate() {
        for (s in listOf("", " ", "@", "@a", "@a=b", ":", ":nick", ":nick ", "@a=b :n!u@h", "\\", "@a=\\", "@a=\\\\;b=\\:", ":a PRIVMSG", "  PING  ", "JOIN #c")) {
            ok(s) { IrcMessage.parse(s) }
        }
        assertEquals(";", IrcMessage.parse("@a=\\: PING").tags["a"])
        assertEquals("JOIN", IrcMessage.parse("join #c").command)
        assertEquals("#c", IrcMessage.parse("join #c").param0)
    }
}

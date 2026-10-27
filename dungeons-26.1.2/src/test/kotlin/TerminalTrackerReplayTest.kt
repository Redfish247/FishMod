import fishmod.features.dungeon.f7.TerminalTrackerState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// Replays Eli's 2026-10-26 22:35 run (Skyblock latest.log) at its real pace
class TerminalTrackerReplayTest {
    private class Ev(val ms: Long, val section: Int, val name: String, val objective: String)

    // ms after "Who dares trespass", section = Section counter at that moment
    private val run = listOf(
        Ev(1880, 1, "Olkebex", "terminal"), Ev(2250, 1, "Hayacyaa", "lever"), Ev(3260, 1, "Hayacyaa", "lever"),
        Ev(5110, 1, "Olkebex", "terminal"), Ev(5660, 1, "xkxr", "terminal"), Ev(5780, 1, "RedFish2471", "device"),
        Ev(8010, 1, "jtszn", "device"), Ev(8640, 1, "xkxr", "terminal"),
        Ev(10560, 2, "Hayacyaa", "device"), Ev(12710, 2, "RedFish2471", "terminal"), Ev(13050, 2, "Olkebex", "terminal"),
        Ev(13590, 2, "jtszn", "lever"), Ev(13670, 2, "xkxr", "terminal"), Ev(15690, 2, "Olkebex", "lever"),
        Ev(16560, 2, "RedFish2471", "terminal"), Ev(17600, 2, "Hayacyaa", "terminal"),
        Ev(20400, 3, "xkxr", "terminal"), Ev(22480, 3, "Olkebex", "terminal"), Ev(22530, 3, "jtszn", "terminal"),
        Ev(23530, 3, "xkxr", "lever"),
    )
    private val gates = mapOf(1 to 4030L, 2 to 14640L)
    private val sectionStarts = mapOf(2 to 8650L, 3 to 17610L)

    // Positions unknown (players out of render range): worst case for section detection
    private fun play(st: TerminalTrackerState, t0: Long, upTo: Long = Long.MAX_VALUE) {
        st.terminalsStarted(t0)
        var sec = 1
        val timeline = run.map { it.ms to { ->
            if (it.section != sec) { sec = it.section; st.sectionChanged(sec, t0 + sectionStarts[sec]!!) }
            st.record(it.name, it.objective, null, null, sec, it.name == "RedFish2471", true, t0 + it.ms); Unit
        } } + gates.map { (_, ms) -> ms to { -> st.gateDestroyed(t0 + ms) } }
        timeline.sortedBy { it.first }.filter { it.first <= upTo }.forEach { it.second() }
    }

    private fun done(st: TerminalTrackerState, s: Int) = st.list(s).count { it.done && !it.pre }

    @Test fun secondRunWithoutLocationChangeStartsClean() {
        val st = TerminalTrackerState()
        play(st, 0)
        st.sectionChanged(4, 30000)
        listOf("T1", "T2", "L1").forEach { lab -> st.list(4).first { it.label == lab }.doneAt = 31000 }
        assertTrue(done(st, 4) > 0)

        // Requeue straight into the next run: no location change, P3 starts again
        play(st, 1_000_000, upTo = 0)
        for (s in 1..4) assertEquals(0, done(st, s), "S$s should start empty")
        assertEquals(1, st.current(1))
    }

    @Test fun s1CountsEveryTerminal() {
        val st = TerminalTrackerState()
        play(st, 0)
        val s1 = st.list(1)
        assertEquals(4, s1.count { it.label.startsWith("T") && it.done })
        assertEquals(2, s1.count { it.label.startsWith("L") && it.done })
        assertTrue(st.gates[1]!! < st.gates[2]!!)
    }

    @Test fun s4EmptyWhenReached() {
        val st = TerminalTrackerState()
        play(st, 0)
        st.sectionChanged(4, 26000)
        assertEquals(0, done(st, 4), "S4 shows completions nobody did")
        assertEquals(4, st.current(4))
    }

    @Test fun slowS4PreDuringS2StaysOutOfS2() {
        for (berserk in listOf(true, false)) {
            val st = TerminalTrackerState()
            st.terminalsStarted(0)
            st.sectionChanged(2, 9000)
            // Standing at the S4 device (63.5, 35.5) when it completes mid S2
            st.record("RedFish2471", "device", 63.4, 35.6, 2, berserk, true, 11000)
            assertEquals(0, st.list(2).count { it.done }, "S2 got the S4 device (berserk=$berserk)")
            assertTrue(st.list(4).first { it.label == "D1" }.pre)
            assertEquals(2, st.current(2))
            assertNull(st.starts[4], "S4 timer started early")
        }
    }

    @Test fun s1ToS2PaceNoSpill() {
        val st = TerminalTrackerState()
        play(st, 0)
        assertEquals(5, st.list(2).count { it.label.startsWith("T") && it.done })
        assertEquals(3, st.list(3).count { it.label.startsWith("T") && it.done })
        assertEquals(1, st.list(3).count { it.label.startsWith("L") && it.done })
    }
}

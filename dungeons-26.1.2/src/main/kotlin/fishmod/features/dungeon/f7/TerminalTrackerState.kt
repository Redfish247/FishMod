package fishmod.features.dungeon.f7

// Pure Terminal Tracker bookkeeping (no Minecraft types) so runs can be replayed in tests
class TerminalTrackerState {
    class Slot(val label: String, val x: Double?, val z: Double?) {
        val shown = when (label[0]) { 'T' -> label.substring(1); 'D' -> "Dev"; else -> if (label == "L1") "LL" else if (label == "L2") "RL" else label }
        var player = ""
        var doneAt = -1L
        var pre = false
        val done get() = doneAt >= 0
    }

    companion object {
        const val SNAP_DIST = 12.0
        const val MAX_SECTION = 4
        const val DETECT_DIST = 15.0

        private fun t(n: Int, x: Double, z: Double) = Slot("T$n", x, z)
        private fun d(n: Int, x: Double, z: Double) = Slot("D$n", x, z)
        private fun l(n: Int, x: Double, z: Double) = Slot("L$n", x, z)

        // Layout per section, positions = player spot at completion (x, z)
        fun layout(section: Int): MutableList<Slot> = when (section) {
            1 -> mutableListOf(t(1, 109.9, 73.4), t(2, 109.5, 79.5), t(3, 91.9, 92.4), t(4, 91.8, 101.3),
                d(1, 108.8, 93.9), l(1, 105.3, 113.3), l(2, 95.5, 114.5))
            2 -> mutableListOf(t(1, 68.6, 124.9), t(2, 59.3, 124.6), t(3, 46.3, 122.3), t(4, 40.2, 124.2),
                t(5, 39.5, 140.6), d(1, 61.1, 140.1), l(1, 23.5, 138.5), l(2, 28.3, 128.1))
            3 -> mutableListOf(t(1, 1.0, 112.3), t(2, 0.6, 77.9), t(3, 0.4, 93.1), t(4, 15.5, 94.5),
                d(1, -0.4, 77.8), l(1, 5.0, 55.9), l(2, 11.9, 55.6))
            else -> mutableListOf(t(1, 41.6, 32.2), t(2, 44.9, 31.7), t(3, 67.2, 31.8), t(4, 71.8, 45.9),
                d(1, 63.5, 35.5), l(1, 84.8, 35.3), l(2, 86.5, 45.5))
        }
    }

    val slots = HashMap<Int, MutableList<Slot>>()
    val starts = HashMap<Int, Long>()
    val gates = HashMap<Int, Long>()
    var viewSection = 0

    fun list(section: Int): MutableList<Slot> = slots.getOrPut(section) { layout(section) }

    fun clear() { slots.clear(); starts.clear(); gates.clear(); viewSection = 0 }

    fun terminalsStarted(now: Long) { clear(); viewSection = 1; starts[1] = now }

    fun sectionChanged(counter: Int, now: Long) {
        viewSection = counter.coerceIn(1, MAX_SECTION)
        starts.putIfAbsent(viewSection, now)
    }

    fun gateDestroyed(now: Long) {
        val s = (1..3).firstOrNull { it !in gates && it <= maxOf(viewSection, 1) }
        if (s != null) gates[s] = now
    }

    // Section counter if valid, else our own
    fun current(counter: Int): Int = counter.takeIf { it in 1..MAX_SECTION } ?: viewSection

    // Section from where the player stood (survives reconnects), else the Section counter
    fun detectSection(x: Double?, z: Double?, counter: Int): Int {
        if (x != null && z != null) {
            var best = 0
            var bestD = DETECT_DIST
            for (s in 1..MAX_SECTION) for (sl in list(s)) {
                val d = if (sl.x == null || sl.z == null) continue else Math.hypot(sl.x - x, sl.z - z)
                if (d < bestD) { bestD = d; best = s }
            }
            if (best != 0) return best
        }
        return current(counter)
    }

    /** berserk/s1DeviceCounts come from the player's class; null slot = ignored. */
    fun record(name: String, objective: String, x: Double?, z: Double?, counter: Int, berserk: Boolean, s1DeviceCounts: Boolean, now: Long): Pair<Int, Slot>? {
        val section = detectSection(x, z, counter)
        if (section !in 1..MAX_SECTION) return null
        val live = current(counter)
        // S4 device done early (stood at it, or berserk before S3) is the S4 pre, never the live section's device
        if (objective == "device" && live in 1..3 && (section == 4 || berserk && live <= 2)) {
            return preCompleteS4Device(name, now)?.let { 4 to it }
        }
        viewSection = section
        if (objective == "device") {
            if (section == 1 && !s1DeviceCounts) return null
            if (section == 4 && list(4).any { it.label == "D1" && it.pre }) return null
        }
        val prefix = when (objective) { "terminal" -> "T"; "device" -> "D"; else -> "L" }
        starts.putIfAbsent(section, now)
        val list = list(section)
        val free = list.filter { !it.done && it.label.startsWith(prefix) }
        val slot = (if (x != null && z != null) free.filter { it.x != null }
            .minByOrNull { Math.hypot(it.x!! - x, it.z!! - z) }
            ?.takeIf { Math.hypot(it.x!! - x, it.z!! - z) <= SNAP_DIST } else null)
            ?: free.firstOrNull()
            ?: Slot("$prefix${list.count { it.label.startsWith(prefix) } + 1}", null, null).also { list.add(it) }
        slot.player = name
        slot.doneAt = now
        return section to slot
    }

    // Berserk's S1/S2 device counts as the S4 device
    private fun preCompleteS4Device(name: String, now: Long): Slot? {
        val dev = list(4).firstOrNull { it.label == "D1" && !it.done } ?: return null
        dev.player = name
        dev.doneAt = now
        dev.pre = true
        return dev
    }
}

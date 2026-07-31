package fishmod.cosmetic

/**
 * Client-side bad-word filter for cosmetic text (custom nicks + item names). It censors banned
 * words in the VISIBLE text of a Minecraft-formatted string (codes like `&a`, `§l` and
 * `&#rrggbb` are preserved) and is robust to the usual evasions: leetspeak (`n1gg3r`),
 * separators between letters (`f-u-c-k`) and padded letters (`shiiit`).
 *
 * Used in both directions: our own nick/item names are censored before they're shown or uploaded,
 * and other players' incoming nicks/item names are censored before they're displayed locally.
 */
object ProfanityFilter {

    /**
     * Banned words as canonical de-leeted, run-collapsed base forms (see [collapse]). The
     * list targets unambiguous slurs/profanity; short words that collide with normal text (e.g.
     * "ass") are intentionally left out to avoid false positives in legitimate names.
     */
    private val RAW_WORDS = arrayOf(
        "hitler", "nazi",
        "fuck", "fuk", "fuq", "motherfucker", "fucker",
        "shit",
        "nigger", "nigga", "niglet", "nignog",
        "faggot", "fagot", "faggit",
        "retard", "retarted",
        "cunt",
        "kike", "spic", "chink", "gook", "wetback", "beaner", "coon", "wop",
        "tranny", "dyke",
        "whore", "slut",
        "rape", "rapist", "pedo", "pedophile",
        "bitch",
        "asshole",
        "pussy",
    )

    /** Collapsed/normalized banned words (built once). */
    private val WORDS: Array<String>

    init {
        val ws = ArrayList<String>(RAW_WORDS.size)
        for (w in RAW_WORDS) {
            val n = StringBuilder()
            for (i in w.indices) {
                val c = normalize(w[i])
                if (c.code != 0) n.append(c)
            }
            val collapsed = collapse(n.toString())
            if (collapsed.length >= 3) ws.add(collapsed)
        }
        WORDS = ws.toTypedArray()
    }

    /** True if the visible text of `raw` contains any banned word. */
    @JvmStatic
    fun isProfane(raw: String?): Boolean {
        if (raw == null || raw.isEmpty()) return false
        val v = visible(raw)
        val c = compact(v)
        for (w in WORDS) if (c.text.contains(w)) return true
        return false
    }

    /**
     * Returns `raw` with every banned word's visible characters replaced by `*`,
     * leaving all color/format codes intact. Idempotent — re-running on the result is a no-op.
     */
    @JvmStatic
    fun censor(raw: String?): String? {
        if (raw == null || raw.isEmpty()) return raw
        val v = visible(raw)
        val c = compact(v)
        val censorVis = BooleanArray(v.chars.size)
        var any = false
        for (w in WORDS) {
            var from = 0
            var idx: Int
            while (true) {
                idx = c.text.indexOf(w, from)
                if (idx < 0) break
                val startVis = c.runStartVis[idx]
                val endVis = c.runEndVis[idx + w.length - 1]
                for (i in startVis..endVis) censorVis[i] = true
                any = true
                from = idx + 1
            }
        }
        if (!any) return raw

        val out = StringBuilder(raw.length)
        var vi = 0
        for (u in v.units) {
            if (u.code != null) {
                out.append(u.code)
            } else {
                out.append(if (censorVis[vi]) '*' else u.ch)
                vi++
            }
        }
        return out.toString()
    }

    // ── internals ────────────────────────────────────────────────────────────

    private data class Unit(val code: String?, val ch: Char)

    private class Visible {
        val units = ArrayList<Unit>()
        val chars = ArrayList<Char>() // visible chars only, in order
    }

    private class Compact {
        lateinit var text: String
        lateinit var runStartVis: IntArray // compact index -> first visible-char index of its run
        lateinit var runEndVis: IntArray   // compact index -> last visible-char index of its run
    }

    /** Splits a formatted string into code tokens + visible characters. */
    private fun visible(raw: String): Visible {
        val v = Visible()
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            if ((c == '&' || c == '§') && i + 1 < n) {
                val next = raw[i + 1]
                if (next == '#' && i + 7 < n && isHex(raw, i + 2, 6)) {
                    v.units.add(Unit(raw.substring(i, i + 8), 0.toChar()))
                    i += 8
                    continue
                }
                if (isFormatCode(next)) {
                    v.units.add(Unit(raw.substring(i, i + 2), 0.toChar()))
                    i += 2
                    continue
                }
            }
            v.units.add(Unit(null, c))
            v.chars.add(c)
            i++
        }
        return v
    }

    /** Builds the normalized, run-collapsed compact string with run-visible index maps. */
    private fun compact(v: Visible): Compact {
        val sb = StringBuilder(v.chars.size)
        val start = IntArray(v.chars.size)
        val end = IntArray(v.chars.size)
        var len = 0
        for (i in v.chars.indices) {
            val nc = normalize(v.chars[i])
            if (nc.code == 0) continue // separator — ignored, but spans still bridge over it
            if (len > 0 && sb[len - 1] == nc) {
                end[len - 1] = i // extend current run to include this padded duplicate
            } else {
                sb.append(nc)
                start[len] = i
                end[len] = i
                len++
            }
        }
        val c = Compact()
        c.text = sb.toString()
        c.runStartVis = IntArray(len)
        c.runEndVis = IntArray(len)
        System.arraycopy(start, 0, c.runStartVis, 0, len)
        System.arraycopy(end, 0, c.runEndVis, 0, len)
        return c
    }

    /** Lowercases + maps leetspeak to a base letter; returns 0 for non-alphanumeric separators. */
    private fun normalize(ch: Char): Char {
        val c = ch.lowercaseChar()
        return when (c) {
            '0' -> 'o'
            '1', '!', '|' -> 'i'
            '3', '£', '€' -> 'e'
            '4', '@' -> 'a'
            '5', '$' -> 's'
            '7', '+' -> 't'
            '8' -> 'b'
            '9' -> 'g'
            else -> {
                if (c in 'a'..'z') c
                else if (c in '0'..'9') c // unmapped digit — kept, harmless
                else 0.toChar()
            }
        }
    }

    /** Removes consecutive duplicate characters ("shiiit" -> "shit"). */
    private fun collapse(s: String): String {
        if (s.isEmpty()) return s
        val b = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            if (b.isEmpty() || b[b.length - 1] != c) b.append(c)
        }
        return b.toString()
    }

    private fun isFormatCode(ch: Char): Boolean {
        val c = ch.lowercaseChar()
        return (c in '0'..'9') || (c in 'a'..'f') ||
            c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r' || c == 'x'
    }

    private fun isHex(s: String, off: Int, count: Int): Boolean {
        for (i in 0 until count) {
            val c = s[off + i].lowercaseChar()
            if (!((c in '0'..'9') || (c in 'a'..'f'))) return false
        }
        return true
    }
}

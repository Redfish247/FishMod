package fishmod.cosmetic

object ProfanityFilter {

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

    private data class Unit(val code: String?, val ch: Char)

    private class Visible {
        val units = ArrayList<Unit>()
        val chars = ArrayList<Char>()
    }

    private class Compact {
        lateinit var text: String
        lateinit var runStartVis: IntArray
        lateinit var runEndVis: IntArray
    }

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

    private fun compact(v: Visible): Compact {
        val sb = StringBuilder(v.chars.size)
        val start = IntArray(v.chars.size)
        val end = IntArray(v.chars.size)
        var len = 0
        for (i in v.chars.indices) {
            val nc = normalize(v.chars[i])
            if (nc.code == 0) continue
            if (len > 0 && sb[len - 1] == nc) {
                end[len - 1] = i
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
                else if (c in '0'..'9') c
                else 0.toChar()
            }
        }
    }

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

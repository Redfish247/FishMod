package fishmod.compat.conflicts

object TextPatch {
    private class Cursor(val t: String) {
        var i = 0

        fun ws() {
            while (i < t.length) {
                val c = t[i]
                if (c.isWhitespace() || c == '﻿') i++
                else if (c == '/' && i + 1 < t.length && t[i + 1] == '/') { while (i < t.length && t[i] != '\n') i++ }
                else if (c == '#') { while (i < t.length && t[i] != '\n') i++ }
                else if (c == '/' && i + 1 < t.length && t[i + 1] == '*') {
                    val end = t.indexOf("*/", i + 2)
                    i = if (end < 0) t.length else end + 2
                } else break
            }
        }

        fun quoted(): String {
            val q = t[i++]
            val sb = StringBuilder()
            while (i < t.length && t[i] != q) {
                if (t[i] == '\\' && i + 1 < t.length) {
                    i++
                    when (val e = t[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'u' -> { sb.append(t.substring(i + 1, minOf(i + 5, t.length)).toIntOrNull(16)?.toChar() ?: '?'); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(t[i])
                i++
            }
            i++
            return sb.toString()
        }

        fun key(): String {
            if (t[i] == '"' || t[i] == '\'') return quoted()
            val st = i
            while (i < t.length && t[i] != ':' && !t[i].isWhitespace()) i++
            return t.substring(st, i)
        }

        fun skipValue() {
            ws()
            when (t[i]) {
                '{' -> container('}')
                '[' -> container(']')
                '"', '\'' -> quoted()
                else -> while (i < t.length && t[i] != ',' && t[i] != '}' && t[i] != ']' && !t[i].isWhitespace()) i++
            }
        }

        fun container(close: Char) {
            i++
            while (true) {
                ws()
                if (i >= t.length) return
                if (t[i] == close) { i++; return }
                if (t[i] == ',') { i++; continue }
                if (close == '}') { key(); ws(); if (i < t.length && (t[i] == ':' || t[i] == '=')) i++ }
                skipValue()
            }
        }

        fun find(steps: List<Any>, s: Int): Int {
            ws()
            if (i >= t.length) return -1
            if (s == steps.size) return i
            val step = steps[s]
            if (step is String) {
                if (t[i] != '{') return -1
                i++
                while (true) {
                    ws()
                    if (i >= t.length || t[i] == '}') return -1
                    if (t[i] == ',') { i++; continue }
                    val k = key(); ws()
                    if (i < t.length && (t[i] == ':' || t[i] == '=')) i++
                    if (k == step) return find(steps, s + 1)
                    skipValue()
                }
            } else {
                if (t[i] != '[') return -1
                i++
                var n = 0
                while (true) {
                    ws()
                    if (i >= t.length || t[i] == ']') return -1
                    if (t[i] == ',') { i++; continue }
                    if (n == step) return find(steps, s + 1)
                    skipValue(); n++
                }
            }
        }
    }

    fun setBoolean(text: String, steps: List<Any>, value: Boolean): String? {
        val c = Cursor(text)
        val pos = try { c.find(steps, 0) } catch (_: Throwable) { -1 }
        if (pos < 0) return null
        val cur = when {
            text.startsWith("true", pos) -> "true"
            text.startsWith("false", pos) -> "false"
            else -> return null
        }
        val end = pos + cur.length
        if (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) return null
        return text.substring(0, pos) + value.toString() + text.substring(end)
    }
}

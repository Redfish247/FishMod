package fishmod.utils

// Small calculator for search boxes: + - * / ^, brackets, k/m/b suffixes. Anything malformed gives NaN.
object ExprParser {

    private class Reader(val src: String) {
        var pos = 0
        fun peek(): Char? = src.getOrNull(pos)
        fun eat(c: Char): Boolean = (peek() == c).also { if (it) pos++ }
        fun fail(): Nothing = throw IllegalArgumentException("bad expression at $pos")
    }

    @JvmStatic
    fun parseExpression(string: String?): Double {
        if (string == null) return Double.NaN
        val r = Reader(string.replace(" ", ""))
        if (r.src.isEmpty()) return Double.NaN
        return try {
            val v = sum(r)
            if (r.pos != r.src.length) Double.NaN else v
        } catch (e: IllegalArgumentException) {
            Double.NaN
        }
    }

    private fun sum(r: Reader): Double {
        var v = product(r)
        while (true) v = when {
            r.eat('+') -> v + product(r)
            r.eat('-') -> v - product(r)
            else -> return v
        }
    }

    private fun product(r: Reader): Double {
        var v = power(r)
        while (true) v = when {
            r.eat('*') -> v * power(r)
            r.eat('/') -> v / power(r)
            else -> return v
        }
    }

    private fun power(r: Reader): Double {
        val base = operand(r)
        return if (r.eat('^')) Math.pow(base, power(r)) else base
    }

    private fun operand(r: Reader): Double {
        val c = r.peek() ?: r.fail()
        return when {
            c == '(' -> { r.pos++; sum(r).also { if (!r.eat(')')) r.fail() } }
            c == '-' && r.src.getOrNull(r.pos + 1)?.isDigit() == true -> { r.pos++; -literal(r) }
            c == '-' -> { r.pos++; -operand(r) }
            c.isDigit() -> literal(r)
            else -> r.fail()
        }
    }

    private fun literal(r: Reader): Double {
        val start = r.pos
        while (r.peek()?.isDigit() == true) r.pos++
        if (r.peek() == '.' && r.src.getOrNull(r.pos + 1)?.isDigit() == true) {
            r.pos++
            while (r.peek()?.isDigit() == true) r.pos++
        }
        val n = r.src.substring(start, r.pos).toDouble()
        val scale = when (r.peek()?.lowercaseChar()) {
            'k' -> 1e3
            'm' -> 1e6
            'b' -> 1e9
            else -> return n
        }
        r.pos++
        return n * scale
    }
}

package fishmod.features.scoreboard

import java.util.regex.Matcher
import java.util.regex.Pattern

/** Rewrites comma-grouped numbers (Hypixel's "1,234,567" style) into compact form ("1.2M").
 *  Only matches numbers that already carry thousands separators, so times ("12:34"), levels
 *  ("50"), percentages ("35.5%") and roman numerals are never touched. */
object CompactNumbers {

    private val GROUPED_NUMBER: Pattern = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d+)?")

    @JvmStatic
    fun apply(line: String): String {
        val m = GROUPED_NUMBER.matcher(line)
        val sb = StringBuffer()
        while (m.find()) {
            val value = m.group().replace(",", "").toDoubleOrNull()
            val replacement = if (value != null) Matcher.quoteReplacement(compact(value)) else m.group()
            m.appendReplacement(sb, replacement)
        }
        m.appendTail(sb)
        return sb.toString()
    }

    @JvmStatic
    fun compact(value: Double): String {
        val abs = Math.abs(value)
        val (div, suffix) = when {
            abs >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "T"
            abs >= 1_000_000_000.0 -> 1_000_000_000.0 to "B"
            abs >= 1_000_000.0 -> 1_000_000.0 to "M"
            abs >= 1_000.0 -> 1_000.0 to "K"
            else -> return trimTrailingZero(value)
        }
        return trimTrailingZero(value / div) + suffix
    }

    private fun trimTrailingZero(v: Double): String {
        val rounded = Math.round(v * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object FormatUtil {
	private val COMMAS = DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US))
	private val ONE_DEC = DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US))
	private val TWO_DEC = DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US))
	private val WEIGHT = DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US))
	private val DATE_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
		.withZone(ZoneOffset.UTC)
	private val SUFFIXES = arrayOf("k", "m", "b", "t")

	private fun fmt(format: DecimalFormat, value: Long): String = synchronized(format) { format.format(value) }

	private fun fmt(format: DecimalFormat, value: Double): String = synchronized(format) { format.format(value) }

	@JvmStatic
	fun prettyDate(ms: Long): String = if (ms <= 0L) "" else DATE_UTC.format(Instant.ofEpochMilli(ms))

	@JvmStatic
	fun ago(epochMs: Long): String =
		if (epochMs <= 0L) "" else prettySpan(System.currentTimeMillis() - epochMs) + " ago"

	@JvmStatic
	fun commas(value: Long): String = fmt(COMMAS, value)

	@JvmStatic
	fun oneDecimal(value: Double): String = fmt(ONE_DEC, value)

	@JvmStatic
	fun twoDecimals(value: Double): String = fmt(TWO_DEC, value)

	@JvmStatic
	fun percent(fraction: Double): String {
		val clamped = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0) else 0.0
		return fmt(ONE_DEC, clamped * 100.0) + "%"
	}

	@JvmStatic
	fun weight(value: Double): String = fmt(WEIGHT, value)

	@JvmStatic
	fun shortCoins(value: Double): String = shortCompact(value, false)

	/** Compact XP for overflow tooltips (e.g. 617M, 16.7M). */
	@JvmStatic
	fun shortXp(value: Double): String = shortCompact(value, true)

	@JvmStatic
	fun prettyTime(ms: Long): String {
		if (ms <= 0L) {
			return "N/A"
		}
		val totalSec = ms / 1000L
		val h = totalSec / 3600L
		val m = (totalSec % 3600L) / 60L
		val s = totalSec % 60L
		if (h > 0L) {
			return h.toString() + ":" + pad2(m) + ":" + pad2(s)
		}
		return m.toString() + ":" + pad2(s)
	}

	@JvmStatic
	fun prettySpan(ms: Long): String {
		var totalSec = maxOf(0L, ms / 1000L)
		if (totalSec < 1L) {
			return "0s"
		}
		val years = totalSec / 31_536_000L
		totalSec %= 31_536_000L
		val months = totalSec / 2_592_000L
		totalSec %= 2_592_000L
		val days = totalSec / 86_400L
		totalSec %= 86_400L
		val hours = totalSec / 3_600L
		totalSec %= 3_600L
		val minutes = totalSec / 60L
		val seconds = totalSec % 60L

		val out = StringBuilder()
		var parts = 0
		parts = appendUnit(out, years, "y", parts)
		parts = appendUnit(out, months, "mo", parts)
		parts = appendUnit(out, days, "d", parts)
		parts = appendUnit(out, hours, "h", parts)
		parts = appendUnit(out, minutes, "m", parts)
		appendUnit(out, seconds, "s", parts)
		return if (out.isEmpty()) "0s" else out.toString()
	}

	private fun appendUnit(out: StringBuilder, value: Long, suffix: String, parts: Int): Int {
		if (value <= 0L || parts >= 2) {
			return parts
		}
		if (out.isNotEmpty()) {
			out.append(' ')
		}
		out.append(value).append(suffix)
		return parts + 1
	}

	private fun pad2(value: Long): String = if (value < 10L) "0$value" else value.toString()

	/** Three significant figures with a lowercase suffix: 1.23m, 12.3m, 123m. */
	@JvmStatic
	fun compactCoins(value: Double): String {
		if (abs(value) < 1_000) {
			return fmt(COMMAS, Math.round(value))
		}
		var scaled = value / 1_000
		var unit = 0
		while (unit < SUFFIXES.size - 1 && abs(sigFigs(scaled).toDouble()) >= 1_000) {
			scaled /= 1_000
			unit++
		}
		return sigFigs(scaled) + SUFFIXES[unit]
	}

	private fun sigFigs(value: Double): String {
		val a = abs(value)
		val pattern = if (a < 10) "%.2f" else if (a < 100) "%.1f" else "%.0f"
		return stripZeros(String.format(Locale.US, pattern, value))
	}

	private fun shortCompact(value: Double, upperSuffix: Boolean): String {
		val a = abs(value)
		if (a >= 1_000_000_000_000L) {
			return trim(value / 1_000_000_000_000L) + if (upperSuffix) "T" else "t"
		}
		if (a >= 1_000_000_000L) {
			return trim(value / 1_000_000_000L) + if (upperSuffix) "B" else "b"
		}
		if (a >= 1_000_000L) {
			return trim(value / 1_000_000L) + if (upperSuffix) "M" else "m"
		}
		if (a >= 1_000L) {
			return trim(value / 1_000L) + if (upperSuffix) "K" else "k"
		}
		return fmt(COMMAS, Math.round(value))
	}

	private fun trim(value: Double): String = stripZeros(String.format(Locale.US, "%.3f", value))

	private fun stripZeros(formatted: String): String {
		var s = formatted
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length - 1)
		}
		return s
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtil {
	private static final DecimalFormat COMMAS = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat ONE_DEC = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat TWO_DEC = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat WEIGHT = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DateTimeFormatter DATE_UTC = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
		.withZone(ZoneOffset.UTC);

	private FormatUtil() {
	}

	// DecimalFormat isn't thread-safe; snapshots format off the render thread too.
	private static String fmt(DecimalFormat format, long value) {
		synchronized (format) {
			return format.format(value);
		}
	}

	private static String fmt(DecimalFormat format, double value) {
		synchronized (format) {
			return format.format(value);
		}
	}

	/** Calendar date in UTC (e.g. {@code Dec 1, 2024}). Empty when {@code ms <= 0}. */
	public static String prettyDate(long ms) {
		if (ms <= 0L) {
			return "";
		}
		return DATE_UTC.format(Instant.ofEpochMilli(ms));
	}

	/** Relative age from an epoch millis timestamp (e.g. {@code 6mo 4d ago}). */
	public static String ago(long epochMs) {
		if (epochMs <= 0L) {
			return "";
		}
		return prettySpan(System.currentTimeMillis() - epochMs) + " ago";
	}

	public static String commas(long value) {
		return fmt(COMMAS, value);
	}

	public static String oneDecimal(double value) {
		return fmt(ONE_DEC, value);
	}

	public static String twoDecimals(double value) {
		return fmt(TWO_DEC, value);
	}

	/** 0..1 fraction as a percentage (e.g. {@code 41.7%}). */
	public static String percent(double fraction) {
		double clamped = Double.isFinite(fraction) ? Math.max(0D, Math.min(1D, fraction)) : 0D;
		return fmt(ONE_DEC, clamped * 100D) + "%";
	}

	public static String weight(double value) {
		return fmt(WEIGHT, value);
	}

	/** Compact coins for networth display (e.g. 29.167b). */
	public static String shortCoins(double value) {
		return shortCompact(value, false);
	}

	/** Compact XP for overflow tooltips (e.g. 617M, 16.7M). */
	public static String shortXp(double value) {
		return shortCompact(value, true);
	}

	public static String prettyTime(long ms) {
		if (ms <= 0L) {
			return "N/A";
		}
		long totalSec = ms / 1000L;
		long h = totalSec / 3600L;
		long m = (totalSec % 3600L) / 60L;
		long s = totalSec % 60L;
		if (h > 0L) {
			return h + ":" + pad2(m) + ":" + pad2(s);
		}
		return m + ":" + pad2(s);
	}

	/**
	 * Compact age/remaining span: at most the two largest non-zero units.
	 * e.g. {@code 6d 4h}, {@code 3h 12m}, {@code 45s}.
	 */
	public static String prettySpan(long ms) {
		long totalSec = Math.max(0L, ms / 1000L);
		if (totalSec < 1L) {
			return "0s";
		}
		long years = totalSec / 31_536_000L; // 365d
		totalSec %= 31_536_000L;
		long months = totalSec / 2_592_000L; // 30d
		totalSec %= 2_592_000L;
		long days = totalSec / 86_400L;
		totalSec %= 86_400L;
		long hours = totalSec / 3_600L;
		totalSec %= 3_600L;
		long minutes = totalSec / 60L;
		long seconds = totalSec % 60L;

		StringBuilder out = new StringBuilder();
		int parts = 0;
		parts = appendUnit(out, years, "y", parts);
		parts = appendUnit(out, months, "mo", parts);
		parts = appendUnit(out, days, "d", parts);
		parts = appendUnit(out, hours, "h", parts);
		parts = appendUnit(out, minutes, "m", parts);
		appendUnit(out, seconds, "s", parts);
		return out.isEmpty() ? "0s" : out.toString();
	}

	private static int appendUnit(StringBuilder out, long value, String suffix, int parts) {
		if (value <= 0L || parts >= 2) {
			return parts;
		}
		if (!out.isEmpty()) {
			out.append(' ');
		}
		out.append(value).append(suffix);
		return parts + 1;
	}

	private static String pad2(long value) {
		return value < 10L ? "0" + value : Long.toString(value);
	}

	/** Three significant figures with a lowercase suffix: 1.23m, 12.3m, 123m. */
	public static String compactCoins(double value) {
		double abs = Math.abs(value);
		if (abs < 1_000) {
			return fmt(COMMAS, Math.round(value));
		}
		String[] suffixes = {"k", "m", "b", "t"};
		double scaled = value / 1_000;
		int unit = 0;
		while (unit < suffixes.length - 1 && Math.abs(Double.parseDouble(sigFigs(scaled))) >= 1_000) {
			scaled /= 1_000;
			unit++;
		}
		return sigFigs(scaled) + suffixes[unit];
	}

	private static String sigFigs(double value) {
		double abs = Math.abs(value);
		String s = String.format(Locale.US, abs < 10 ? "%.2f" : abs < 100 ? "%.1f" : "%.0f", value);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String shortCompact(double value, boolean upperSuffix) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000_000_000L) {
			return trim(value / 1_000_000_000_000L) + (upperSuffix ? "T" : "t");
		}
		if (abs >= 1_000_000_000L) {
			return trim(value / 1_000_000_000L) + (upperSuffix ? "B" : "b");
		}
		if (abs >= 1_000_000L) {
			return trim(value / 1_000_000L) + (upperSuffix ? "M" : "m");
		}
		if (abs >= 1_000L) {
			return trim(value / 1_000L) + (upperSuffix ? "K" : "k");
		}
		return fmt(COMMAS, Math.round(value));
	}

	private static String trim(double value) {
		String s = String.format(Locale.US, "%.3f", value);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}
}

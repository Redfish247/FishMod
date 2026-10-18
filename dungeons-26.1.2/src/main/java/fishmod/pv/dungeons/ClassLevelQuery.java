/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassLevelQuery {
	public record Parsed(String classId, String displayName, int targetLevel, boolean classAverage) {
		public static Parsed ofClass(String classId, int targetLevel) {
			return new Parsed(classId, ClassLevelQuery.displayName(classId), targetLevel, false);
		}

		public static Parsed ofAverage(int targetLevel) {
			return new Parsed(null, "CA", targetLevel, true);
		}
	}

	/** Class targets allow overflow levels (e.g. {@code M55}); CA stays 1–2 digits. */
	private static final Pattern CLASS_THEN_LEVEL = Pattern.compile("^([a-z]+)\\s*(\\d{1,3})$");
	private static final Pattern LEVEL_THEN_CLASS = Pattern.compile("^(\\d{1,3})\\s*([a-z]+)$");

	/** Longest CA phrases first so {@code class average} wins over {@code ca}/{@code avg}. */
	private static final Pattern CA_THEN_LEVEL = Pattern.compile(
		"^(?:class\\s*average|class\\s*avg|classaverage|classavg|average|avg|ca)\\s*(\\d{1,2})$"
	);
	private static final Pattern LEVEL_THEN_CA = Pattern.compile(
		"^(\\d{1,2})\\s*(?:class\\s*average|class\\s*avg|classaverage|classavg|average|avg|ca)$"
	);

	/** Longer aliases first so {@code berserker} wins over {@code bers}/{@code b}. */
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	static {
		put("mage", "mage");
		put("m", "mage");
		put("berserker", "berserk");
		put("berzerker", "berserk");
		put("berserk", "berserk");
		put("berz", "berserk");
		put("bers", "berserk");
		put("b", "berserk");
		put("tank", "tank");
		put("t", "tank");
		put("healer", "healer");
		put("h", "healer");
		put("archer", "archer");
		put("arch", "archer");
		put("a", "archer");
	}

	private ClassLevelQuery() {
	}

	private static void put(String alias, String classId) {
		ALIASES.put(alias, classId);
	}

	public static Parsed parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

		Parsed average = parseAverage(cleaned);
		if (average != null) {
			return average;
		}

		Matcher m = CLASS_THEN_LEVEL.matcher(cleaned);
		String alias;
		int level;
		if (m.matches()) {
			alias = m.group(1);
			level = Integer.parseInt(m.group(2));
		} else {
			m = LEVEL_THEN_CLASS.matcher(cleaned);
			if (!m.matches()) {
				return null;
			}
			level = Integer.parseInt(m.group(1));
			alias = m.group(2);
		}
		if (isAverageAlias(alias)) {
			return Parsed.ofAverage(clampAverageLevel(level));
		}
		String classId = resolveAlias(alias);
		if (classId == null) {
			return null;
		}
		return Parsed.ofClass(classId, clampClassLevel(level));
	}

	private static Parsed parseAverage(String cleaned) {
		Matcher ca = CA_THEN_LEVEL.matcher(cleaned);
		if (ca.matches()) {
			return Parsed.ofAverage(clampAverageLevel(Integer.parseInt(ca.group(1))));
		}
		ca = LEVEL_THEN_CA.matcher(cleaned);
		if (ca.matches()) {
			return Parsed.ofAverage(clampAverageLevel(Integer.parseInt(ca.group(1))));
		}
		return null;
	}

	private static boolean isAverageAlias(String alias) {
		return "ca".equals(alias)
			|| "avg".equals(alias)
			|| "average".equals(alias)
			|| "classavg".equals(alias)
			|| "classaverage".equals(alias);
	}

	/** Class average RTCA stays capped at soft max (50). */
	private static int clampAverageLevel(int level) {
		return Math.max(1, Math.min(CataXpMath.SOFT_CAP, level));
	}

	/** Individual class targets may use overflow levels from the XP table. */
	private static int clampClassLevel(int level) {
		return Math.max(1, Math.min(CataXpMath.maxLevel(), level));
	}

	private static String resolveAlias(String alias) {
		return alias == null ? null : ALIASES.get(alias);
	}

	public static String displayName(String classId) {
		if (classId == null || classId.isBlank()) {
			return "";
		}
		return Character.toUpperCase(classId.charAt(0)) + classId.substring(1);
	}
}

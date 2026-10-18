/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coin estimate for unlocking an Abiphone contact from its NEU requirement text.
 * Only "No Requirement", plain coin amounts and "Nx Item" lines with a known market price count.
 * Quests, kills, alternatives ("... or") and unpriced items make the whole estimate unknown.
 */
public final class AbiphoneCosts {
	private static final Pattern COINS = Pattern.compile("^([0-9][0-9.,]*) coins$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEM = Pattern.compile("^([0-9]+)x (.+)$", Pattern.CASE_INSENSITIVE);

	/** coins is null when the requirement can't be priced. */
	public record Estimate(Double coins) {
		public static final Estimate UNKNOWN = new Estimate(null);

		public boolean known() {
			return this.coins != null;
		}
	}

	private AbiphoneCosts() {
	}

	public static Estimate estimate(
		List<String> requirement,
		Function<String, String> idForName,
		Function<String, Double> knownPrice
	) {
		if (requirement == null || requirement.isEmpty()) {
			return Estimate.UNKNOWN;
		}
		double total = 0;
		boolean any = false;
		for (String raw : requirement) {
			String line = clean(raw);
			if (line.isEmpty()) {
				continue;
			}
			any = true;
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.endsWith(" or") || lower.startsWith("or ")) {
				return Estimate.UNKNOWN;
			}
			if (lower.equals("no requirement")) {
				continue;
			}
			Matcher coins = COINS.matcher(line);
			if (coins.matches()) {
				String digits = coins.group(1).replace(",", "").replace(".", "");
				try {
					total += Long.parseLong(digits);
				} catch (NumberFormatException e) {
					return Estimate.UNKNOWN;
				}
				continue;
			}
			Matcher item = ITEM.matcher(line);
			if (!item.matches()) {
				return Estimate.UNKNOWN;
			}
			int qty = Integer.parseInt(item.group(1));
			String id = resolve(item.group(2).trim(), idForName);
			Double price = id == null ? null : knownPrice.apply(id);
			if (price == null || price <= 0) {
				return Estimate.UNKNOWN;
			}
			total += qty * price;
		}
		return any ? new Estimate(total) : Estimate.UNKNOWN;
	}

	static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String line = raw.replaceAll("\u00a7.", "").trim();
		if (line.startsWith("-")) {
			line = line.substring(1).trim();
		}
		line = line.replaceAll("\\s*\\(.*\\)\\s*$", "");
		while (line.endsWith(".")) {
			line = line.substring(0, line.length() - 1).trim();
		}
		return line;
	}

	private static String resolve(String name, Function<String, String> idForName) {
		String id = idForName.apply(name);
		if (id != null) {
			return id;
		}
		if (name.endsWith("ies")) {
			return idForName.apply(name.substring(0, name.length() - 3) + "y");
		}
		if (name.endsWith("s") && !name.endsWith("ss")) {
			return idForName.apply(name.substring(0, name.length() - 1));
		}
		return null;
	}
}

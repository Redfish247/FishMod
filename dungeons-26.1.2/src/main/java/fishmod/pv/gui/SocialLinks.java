/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** External profile pages for the viewed player. */
final class SocialLinks {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

	enum Site {
		PLANCKE("Plancke"),
		SKYCRYPT("SkyCrypt"),
		ELITEBOT("EliteBot"),
		COFLNET("Coflnet"),
		NAMEMC("NameMC");

		private final String label;

		Site(String label) {
			this.label = label;
		}

		String label() {
			return this.label;
		}
	}

	/** Always the viewed player and their selected profile, never the logged-in account. */
	record Target(UUID uuid, String name, String profileId, String profileName) {
	}

	private SocialLinks() {
	}

	/** Returns null when the target has nothing the site can look up. */
	static String url(Site site, Target target) {
		if (site == null || target == null) {
			return null;
		}
		String undashed = target.uuid() == null ? null : target.uuid().toString().replace("-", "");
		String name = validName(target.name());
		String player = undashed != null ? undashed : name == null ? null : segment(name);
		return switch (site) {
			case PLANCKE -> player == null ? null : "https://plancke.io/hypixel/player/stats/" + player;
			case SKYCRYPT -> {
				if (player == null) {
					yield null;
				}
				// SkyCrypt rejects profile ids, only the profile name selects a profile.
				String profile = blankToNull(target.profileName());
				yield "https://sky.shiiyu.moe/stats/" + player + (profile == null ? "" : "/" + segment(profile));
			}
			case ELITEBOT -> {
				if (player == null) {
					yield null;
				}
				String profile = profileUuid(target.profileId());
				if (profile == null && blankToNull(target.profileName()) != null) {
					profile = segment(target.profileName().trim());
				}
				yield "https://eliteskyblock.com/@" + player + (profile == null ? "" : "/" + profile);
			}
			case COFLNET -> undashed == null ? null : "https://sky.coflnet.com/player/" + undashed;
			case NAMEMC -> target.uuid() != null
				? "https://namemc.com/profile/" + target.uuid()
				: name == null ? null : "https://namemc.com/profile/" + segment(name);
		};
	}

	static String segment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String validName(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		return NAME.matcher(trimmed).matches() ? trimmed : null;
	}

	private static String profileUuid(String profileId) {
		if (profileId == null) {
			return null;
		}
		String undashed = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
		return HEX32.matcher(undashed).matches() ? undashed : null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

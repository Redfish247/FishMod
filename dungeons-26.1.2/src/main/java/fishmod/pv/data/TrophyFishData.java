/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** NEU {@code constants/trophyfish.json} catalog (Odger order). */
public final class TrophyFishData {
	public enum Tier {
		DIAMOND,
		GOLD,
		SILVER,
		BRONZE;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String suffix() {
			return name();
		}

		/** Top → bottom render order. */
		public static Tier[] topToBottom() {
			return new Tier[] { DIAMOND, GOLD, SILVER, BRONZE };
		}
	}

	public record Def(String id, String name) {
		public String iconId(Tier tier) {
			return id + "_" + tier.suffix();
		}
	}

	/**
	 * Obtain hint from trophy item lore. Obfuscated 1/2 double as bait, so their lore opens with
	 * "Fishing Bait" before the real "Caught with ..." line; scan everything before falling back.
	 */
	public static String howtoFromLore(List<String> lore) {
		if (lore == null) {
			return null;
		}
		String first = null;
		for (String raw : lore) {
			String plain = raw == null ? "" : raw.replaceAll("§.", "").trim();
			if (plain.isBlank()) {
				continue;
			}
			if (first == null) {
				first = plain;
			}
			String lower = plain.toLowerCase(Locale.ROOT);
			if (lower.startsWith("caught") || lower.contains("near ") || lower.contains("found ")
				|| lower.contains("obtain") || lower.startsWith("can be") || lower.contains("fished")) {
				return plain;
			}
		}
		return first;
	}

	private static final List<String> FALLBACK_ORDER = List.of(
		"SULPHUR_SKITTER", "OBFUSCATED_FISH_1", "OBFUSCATED_FISH_2", "OBFUSCATED_FISH_3",
		"GUSHER", "BLOBFISH", "STEAMING_HOT_FLOUNDER", "SLUGFISH", "FLYFISH", "LAVA_HORSE",
		"MANA_RAY", "VOLCANIC_STONEFISH", "VANILLE", "SKELETON_FISH", "MOLDFIN", "SOUL_FISH",
		"KARATE_FISH", "GOLDEN_FISH"
	);

	private static volatile boolean loaded;
	private static volatile List<Def> defs = List.of();

	private TrophyFishData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (TrophyFishData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (TrophyFishData.class) {
			loaded = false;
			loadFromDisk();
			loaded = true;
		}
	}

	public static List<Def> all() {
		ensureLoaded();
		return defs;
	}

	private static void loadFromDisk() {
		Path path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", "trophyfish.json");
		Map<String, Def> map = new LinkedHashMap<>();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root != null && root.isJsonObject()) {
					JsonObject obj = root.getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
						String id = e.getKey();
						if (id == null || id.isBlank()) {
							continue;
						}
						String key = id.toUpperCase(Locale.ROOT);
						map.put(key, new Def(key, pretty(key)));
					}
				}
			} catch (IOException | RuntimeException ex) {
				if (ex instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
					throw runtime;
				}
				BetterPV.LOGGER.warn("Failed to load trophyfish.json", ex);
			}
		}
		List<Def> out = new ArrayList<>();
		if (!map.isEmpty()) {
			out.addAll(map.values());
		} else {
			for (String id : FALLBACK_ORDER) {
				out.add(new Def(id, pretty(id)));
			}
		}
		defs = List.copyOf(out);
	}

	private static String pretty(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		if ("LAVA_HORSE".equalsIgnoreCase(id)) {
			return "Lavahorse";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}
}

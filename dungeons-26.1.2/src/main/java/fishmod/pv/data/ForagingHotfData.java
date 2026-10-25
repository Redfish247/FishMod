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

/** NEU {@code hotflayout.json} perk defs for Heart of the Forest. */
public final class ForagingHotfData {
	public record PerkDef(String id, String name, int x, int y, int maxLevel, boolean ability) {
	}

	private static volatile boolean loaded;
	private static List<PerkDef> perks = List.of();
	private static Map<String, PerkDef> byId = Map.of();
	private static int maxX;
	private static int maxY;

	private ForagingHotfData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (ForagingHotfData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (ForagingHotfData.class) {
			loaded = false;
			loadFromDisk();
			loaded = true;
		}
	}

	public static List<PerkDef> perks() {
		ensureLoaded();
		return perks;
	}

	public static PerkDef perk(String id) {
		ensureLoaded();
		if (id == null || id.isBlank()) {
			return null;
		}
		return byId.get(id);
	}

	public static String displayName(String apiId) {
		PerkDef def = perk(apiId);
		if (def != null) {
			return def.name();
		}
		return titleCase(apiId == null ? "" : apiId);
	}

	/** 0 when the perk isn't in NEU's layout, so its max is unknown. */
	public static int maxLevel(String apiId) {
		PerkDef def = perk(apiId);
		return def == null ? 0 : Math.max(1, def.maxLevel());
	}

	public static int maxX() {
		ensureLoaded();
		return maxX;
	}

	public static int maxY() {
		ensureLoaded();
		return maxY;
	}

	private static void loadFromDisk() {
		Path path = Path.of(System.getProperty("user.home"),
			".betterpv", "neu-repo", "repo", "constants", "hotflayout.json");
		if (!Files.isRegularFile(path)) {
			BetterPV.LOGGER.debug("HOTF layout missing at {}", path);
			perks = List.of();
			byId = Map.of();
			maxX = 0;
			maxY = 0;
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject hotf = root.has("hotf") && root.get("hotf").isJsonObject()
				? root.getAsJsonObject("hotf") : root;
			JsonObject perkObj = hotf.has("perks") && hotf.get("perks").isJsonObject()
				? hotf.getAsJsonObject("perks") : null;
			if (perkObj == null) {
				return;
			}
			Map<String, PerkDef> map = new LinkedHashMap<>();
			List<PerkDef> list = new ArrayList<>();
			int mx = 0;
			int my = 0;
			for (Map.Entry<String, JsonElement> e : perkObj.entrySet()) {
				if (e.getValue() == null || !e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject();
				String id = e.getKey();
				String name = str(o, "name");
				if (name.isBlank()) {
					name = titleCase(id);
				}
				int x = (int) num(o, "x");
				int y = (int) num(o, "y");
				int max = Math.max(1, (int) num(o, "maxLevel"));
				boolean ability = o.has("isAbility") ? bool(o, "isAbility") : "axe_toss".equals(id) || "maniac_slicer".equals(id);
				PerkDef def = new PerkDef(id, name, x, y, max, ability);
				map.put(id, def);
				list.add(def);
				mx = Math.max(mx, x);
				my = Math.max(my, y);
			}
			list.sort((a, b) -> {
				int c = Integer.compare(a.y(), b.y());
				return c != 0 ? c : Integer.compare(a.x(), b.x());
			});
			byId = Map.copyOf(map);
			perks = List.copyOf(list);
			maxX = mx;
			maxY = my;
		} catch (IOException | RuntimeException ex) {
			if (ex instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed to load HOTF layout", ex);
			perks = List.of();
			byId = Map.of();
			maxX = 0;
			maxY = 0;
		}
	}

	private static String str(JsonObject o, String key) {
		if (o == null || key == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return o.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static boolean bool(JsonObject o, String key) {
		try {
			return o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
		} catch (IllegalStateException | ClassCastException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	private static float num(JsonObject o, String key) {
		Float n = Leveling.num(o == null ? null : o.get(key));
		return n == null ? 0f : n;
	}

	private static String titleCase(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}
}

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** NEU {@code hotmlayout.json} perk grid + Hypixel API id aliases. */
public final class MiningHotmData {
	public record PerkDef(
		String id, String name, int x, int y, int maxLevel, String powder, boolean ability
	) {
	}

	private static final Map<String, String> API_TO_LAYOUT = Map.ofEntries(
		Map.entry("special_0", "core_of_the_mountain"),
		Map.entry("forge_time", "quick_forge"),
		Map.entry("pickaxe_toss", "pickobulus"),
		Map.entry("mining_speed_2", "speedy_mineman"),
		Map.entry("mining_fortune_2", "fortunate_mineman"),
		Map.entry("fortunate", "gem_lover"),
		Map.entry("mining_experience", "seasoned_mineman"),
		Map.entry("daily_effect", "sky_mall"),
		Map.entry("random_event", "luck_of_the_cave"),
		Map.entry("hungry_for_more", "dead_mans_chest"),
		Map.entry("warm_hearted", "warm_heart")
	);

	private static volatile boolean loaded;
	private static List<PerkDef> perks = List.of();
	private static Map<String, PerkDef> byId = Map.of();
	private static int maxX;
	private static int maxY;

	private MiningHotmData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (MiningHotmData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (MiningHotmData.class) {
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

	public static int maxX() {
		ensureLoaded();
		return maxX;
	}

	public static int maxY() {
		ensureLoaded();
		return maxY;
	}

	/** Map Hypixel {@code mining_core.nodes} key → NEU layout id. */
	public static String layoutId(String apiId) {
		if (apiId == null || apiId.isBlank()) {
			return "";
		}
		String mapped = API_TO_LAYOUT.get(apiId);
		return mapped != null ? mapped : apiId;
	}

	public static String displayName(String apiOrLayoutId) {
		PerkDef def = perk(layoutId(apiOrLayoutId));
		if (def != null) {
			return def.name();
		}
		return titleCase(apiOrLayoutId == null ? "" : apiOrLayoutId);
	}

	private static void loadFromDisk() {
		Path path = Path.of(System.getProperty("user.home"),
			".betterpv", "neu-repo", "repo", "constants", "hotmlayout.json");
		if (!Files.isRegularFile(path)) {
			BetterPV.LOGGER.debug("HOTM layout missing at {}", path);
			perks = List.of();
			byId = Map.of();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject hotm = root.has("hotm") && root.get("hotm").isJsonObject()
				? root.getAsJsonObject("hotm") : root;
			JsonObject perkObj = hotm.has("perks") && hotm.get("perks").isJsonObject()
				? hotm.getAsJsonObject("perks") : null;
			if (perkObj == null) {
				return;
			}
			Map<String, PerkDef> map = new LinkedHashMap<>();
			List<PerkDef> list = new ArrayList<>();
			int mx = 0;
			int my = 0;
			for (Map.Entry<String, JsonElement> e : perkObj.entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject();
				String id = e.getKey();
				String name = stringOf(o, "name");
				if (name.isBlank()) {
					name = titleCase(id);
				}
				int x = intOf(o, "x");
				int y = intOf(o, "y");
				int maxLevel = Math.max(1, intOf(o, "maxLevel"));
				String powder = stringOf(o, "powder").toUpperCase(Locale.ROOT);
				if (powder.contains(" ")) {
					powder = "";
				}
				String itemExpr = stringOf(o, "item");
				boolean ability = itemExpr.contains("(api");
				PerkDef def = new PerkDef(id, name, x, y, maxLevel, powder, ability);
				map.put(id, def);
				list.add(def);
				mx = Math.max(mx, x);
				my = Math.max(my, y);
			}
			list.sort((a, b) -> {
				int c = Integer.compare(a.y(), b.y());
				return c != 0 ? c : Integer.compare(a.x(), b.x());
			});
			perks = Collections.unmodifiableList(list);
			byId = Collections.unmodifiableMap(map);
			maxX = mx;
			maxY = my;
			BetterPV.LOGGER.info("Loaded HOTM layout ({} perks)", list.size());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed loading hotmlayout.json", exception);
			perks = List.of();
			byId = Map.of();
		}
	}

	private static int intOf(JsonObject obj, String key) {
		Float n = Leveling.num(obj.get(key));
		return n == null ? 0 : Math.round(n);
	}

	private static String stringOf(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		try {
			return el.getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static String titleCase(String id) {
		if (id == null || id.isBlank()) {
			return "";
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

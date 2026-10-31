/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class RepoData {
	// Volatile: read from profile-loading executor threads as well as the render thread.
	private static volatile JsonObject weight;
	private static volatile JsonObject leveling;
	private static volatile JsonObject dungeonsXp;

	private RepoData() {
	}

	public static void ensureLoaded() {
		if (weight != null && leveling != null && dungeonsXp != null) {
			return;
		}
		synchronized (RepoData.class) {
			if (weight == null || leveling == null) {
				weight = loadJson("data/weight.json");
				leveling = loadJson("data/leveling.json");
			}
			if (dungeonsXp == null) {
				dungeonsXp = loadJson("data/dungeons_xp.json");
			}
		}
	}

	public static JsonObject weight() {
		ensureLoaded();
		return weight;
	}

	public static JsonObject leveling() {
		ensureLoaded();
		return leveling;
	}

	public static JsonObject dungeonsXp() {
		ensureLoaded();
		return dungeonsXp;
	}

	public static JsonArray levelingXp() {
		JsonObject root = leveling();
		return root != null && root.get("leveling_xp") instanceof JsonArray arr ? arr : null;
	}

	public static JsonArray catacombsXp() {
		JsonObject root = leveling();
		if (root != null && root.has("catacombs") && root.get("catacombs").isJsonArray()) {
			return root.getAsJsonArray("catacombs");
		}
		return levelingXp();
	}

	public static JsonArray slayerXp(String slayer) {
		JsonObject root = leveling();
		if (root == null || !root.has("slayer_xp") || !root.get("slayer_xp").isJsonObject()) {
			return null;
		}
		JsonObject map = root.getAsJsonObject("slayer_xp");
		return map.has(slayer) && map.get(slayer).isJsonArray() ? map.getAsJsonArray(slayer) : null;
	}

	/** XP rewarded per boss kill by tier (T1…T5). Prefers per-type override, else shared table. */
	public static JsonArray slayerBossXp(String slayer) {
		JsonObject root = leveling();
		if (root == null) {
			return null;
		}
		if (root.has("slayer_boss_xp_type") && root.get("slayer_boss_xp_type").isJsonObject()) {
			JsonObject byType = root.getAsJsonObject("slayer_boss_xp_type");
			if (slayer != null && byType.has(slayer) && byType.get(slayer).isJsonArray()) {
				return byType.getAsJsonArray(slayer);
			}
		}
		return root.has("slayer_boss_xp") && root.get("slayer_boss_xp").isJsonArray()
			? root.getAsJsonArray("slayer_boss_xp")
			: null;
	}

	/** Highest available boss tier for a slayer id (1-based), or 5 if unknown. */
	public static int slayerHighestTier(String slayer) {
		JsonObject root = leveling();
		if (root != null && root.has("slayer_to_highest_tier") && root.get("slayer_to_highest_tier").isJsonObject()) {
			JsonObject map = root.getAsJsonObject("slayer_to_highest_tier");
			if (slayer != null && map.has(slayer) && map.get(slayer).isJsonPrimitive()) {
				try {
					return Math.max(1, map.get(slayer).getAsInt());
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					// fall through
				}
			}
		}
		return 5;
	}

	public static int skillCap(String skill) {
		JsonObject root = leveling();
		if (root != null && root.has("leveling_caps") && root.get("leveling_caps").isJsonObject()) {
			JsonObject caps = root.getAsJsonObject("leveling_caps");
			Double cap = skill == null ? null : Leveling.numDouble(caps.get(skill));
			if (cap != null) {
				return cap.intValue();
			}
		}
		return 60;
	}

	public static JsonElement path(JsonObject root, String dotted) {
		if (root == null || dotted == null || dotted.isBlank()) {
			return null;
		}
		JsonElement current = root;
		for (String part : dotted.split("\\.")) {
			if (current == null || !current.isJsonObject() || !current.getAsJsonObject().has(part)) {
				return null;
			}
			current = current.getAsJsonObject().get(part);
		}
		return current;
	}

	private static JsonObject loadJson(String path) {
		JsonObject fromResources = loadFromResourceManager(path);
		if (fromResources != null) {
			return fromResources;
		}
		return loadFromClasspath(path);
	}

	private static JsonObject loadFromResourceManager(String path) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getResourceManager() == null) {
				return null;
			}
			Identifier id = Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, path);
			Optional<Resource> resource = client.getResourceManager().getResource(id);
			if (resource.isEmpty()) {
				return null;
			}
			try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				return element.isJsonObject() ? element.getAsJsonObject() : null;
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			return null;
		}
	}

	private static JsonObject loadFromClasspath(String path) {
		String classpath = "/assets/" + BetterPV.MOD_ID + "/" + path;
		try (InputStream stream = RepoData.class.getResourceAsStream(classpath)) {
			if (stream == null) {
				BetterPV.LOGGER.warn("Missing classpath resource {}", classpath);
				return null;
			}
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				return element.isJsonObject() ? element.getAsJsonObject() : null;
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed loading {}", path, exception);
			return null;
		}
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.RepoData;
import java.util.ArrayList;
import java.util.List;

public final class DungeonXpData {
	public record FloorDef(
		String id,
		String label,
		boolean master,
		int floor,
		double baseXp,
		int unlockCata
	) {
	}

	private static List<FloorDef> floors = List.of();
	private static double[] hecatombSPlus = new double[] { 0 };
	private static double expertRingBonus = 0.1;
	private static double experiencedFloorBonus = 0.5;
	private static int experiencedFloorMinCompletions = 5;
	private static volatile boolean loaded;

	private DungeonXpData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (DungeonXpData.class) {
			if (!loaded) {
				load();
				loaded = true;
			}
		}
	}

	// Fills the fields before loaded is published so other threads never see a half-loaded table.
	private static void load() {
		JsonObject root = RepoData.dungeonsXp();
		if (root == null) {
			return;
		}
		if (root.has("expert_ring_bonus") && root.get("expert_ring_bonus").isJsonPrimitive()) {
			expertRingBonus = root.get("expert_ring_bonus").getAsDouble();
		}
		if (root.has("experienced_floor_bonus") && root.get("experienced_floor_bonus").isJsonPrimitive()) {
			experiencedFloorBonus = root.get("experienced_floor_bonus").getAsDouble();
		}
		if (root.has("experienced_floor_min_completions") && root.get("experienced_floor_min_completions").isJsonPrimitive()) {
			experiencedFloorMinCompletions = root.get("experienced_floor_min_completions").getAsInt();
		}
		if (root.has("hecatomb_s_plus") && root.get("hecatomb_s_plus").isJsonArray()) {
			JsonArray arr = root.getAsJsonArray("hecatomb_s_plus");
			hecatombSPlus = new double[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				hecatombSPlus[i] = arr.get(i).getAsDouble();
			}
		}
		List<FloorDef> parsed = new ArrayList<>();
		if (root.has("floors") && root.get("floors").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("floors")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject o = el.getAsJsonObject();
				String id = o.has("id") ? o.get("id").getAsString() : "";
				String label = o.has("label") ? o.get("label").getAsString() : id;
				boolean master = o.has("mode") && "master".equalsIgnoreCase(o.get("mode").getAsString());
				int floor = o.has("floor") ? o.get("floor").getAsInt() : 0;
				double base = o.has("base") ? o.get("base").getAsDouble() : 0;
				int unlock = o.has("unlock_cata") ? o.get("unlock_cata").getAsInt() : 0;
				parsed.add(new FloorDef(id, label, master, floor, base, unlock));
			}
		}
		floors = List.copyOf(parsed);
	}

	public static List<FloorDef> floors() {
		ensureLoaded();
		return floors;
	}

	public static double expertRingBonus() {
		ensureLoaded();
		return expertRingBonus;
	}

	public static double experiencedFloorBonus() {
		ensureLoaded();
		return experiencedFloorBonus;
	}

	public static boolean isExperiencedFloor(long completions) {
		ensureLoaded();
		return completions >= experiencedFloorMinCompletions;
	}

	public static double hecatombBonus(int level) {
		ensureLoaded();
		if (level <= 0 || hecatombSPlus.length == 0) {
			return 0;
		}
		int idx = Math.min(level, hecatombSPlus.length - 1);
		return hecatombSPlus[idx];
	}
}

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
import fishmod.pv.data.Leveling;
import fishmod.pv.data.RepoData;
import java.util.Locale;

public final class CataXpMath {
	private CataXpMath() {
	}

	/**
	 * Soft / stat cap. Levels above this are cosmetic mastery levels (200M XP each in the
	 * NEU table) and do not grant dungeon item stat bonuses.
	 */
	public static final int SOFT_CAP = 50;

	/** Highest level the catacombs XP table supports (includes mastery steps). */
	public static int maxLevel() {
		JsonArray table = RepoData.catacombsXp();
		if (table == null || table.isEmpty()) {
			return SOFT_CAP;
		}
		return table.size();
	}

	/** Catacombs / class progress using the full XP table (not soft-capped at 50). */
	public static Leveling.Progress progress(float xp) {
		return Leveling.getLevel(RepoData.catacombsXp(), xp, maxLevel(), false);
	}

	/**
	 * Total XP required to reach {@code level}. Levels above {@link #SOFT_CAP} use overflow
	 * steps from the repo table (typically 200M XP each).
	 */
	public static float xpForLevel(int level) {
		if (level <= 0) {
			return 0F;
		}
		return Leveling.xpRequiredForLevel(RepoData.catacombsXp(), Math.min(level, maxLevel()), false);
	}

	public static float xpNeeded(float currentXp, int targetLevel) {
		float need = xpForLevel(targetLevel) - Math.max(0F, currentXp);
		return Math.max(0F, need);
	}

	public static String mayorName(JsonObject electionRoot) {
		if (electionRoot == null) {
			return "";
		}
		JsonObject mayor = Leveling.obj(electionRoot.get("mayor"));
		if (mayor == null) {
			return "";
		}
		if (mayor.has("name") && mayor.get("name").isJsonPrimitive()) {
			return mayor.get("name").getAsString();
		}
		if (mayor.has("key") && mayor.get("key").isJsonPrimitive()) {
			return mayor.get("key").getAsString();
		}
		return "";
	}

	public static double mayorXpFactor(JsonObject electionRoot) {
		String name = mayorName(electionRoot).toLowerCase(Locale.ROOT);
		String key = "";
		JsonObject mayor = electionRoot == null ? null : Leveling.obj(electionRoot.get("mayor"));
		if (mayor != null && mayor.has("key") && mayor.get("key").isJsonPrimitive()) {
			key = mayor.get("key").getAsString().toLowerCase(Locale.ROOT);
		}
		String id = key.isBlank() ? name : key;
		if (id.contains("derpy")) {
			return 1.5;
		}
		if (id.contains("aura")) {
			return 1.59;
		}
		if (mayor != null && mayor.has("perks") && mayor.get("perks").isJsonArray()) {
			JsonArray perks = mayor.getAsJsonArray("perks");
			for (JsonElement el : perks) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject perk = el.getAsJsonObject();
				String desc = "";
				if (perk.has("description") && perk.get("description").isJsonPrimitive()) {
					desc = perk.get("description").getAsString().toLowerCase(Locale.ROOT);
				}
				String perkName = perk.has("name") && perk.get("name").isJsonPrimitive()
					? perk.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
				boolean xpPerk = desc.contains("catacombs") && desc.contains("experi")
					|| desc.contains("dungeon") && desc.contains("experi")
					|| perkName.contains("derpy")
					|| perkName.contains("aura");
				if (!xpPerk) {
					continue;
				}
				if (desc.contains("59%") || id.contains("aura") || perkName.contains("aura")) {
					return 1.59;
				}
				if (desc.contains("50%") || id.contains("derpy") || perkName.contains("derpy")) {
					return 1.5;
				}
			}
		}
		return 1.0;
	}
}

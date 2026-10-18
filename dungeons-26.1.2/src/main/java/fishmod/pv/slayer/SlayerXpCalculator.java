/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer;

import com.google.gson.JsonArray;
import fishmod.pv.data.Leveling;
import fishmod.pv.data.RepoData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SlayerXpCalculator {
	private static final int[] QUEST_COSTS = { 2_000, 7_500, 20_000, 50_000, 100_000 };

	private SlayerXpCalculator() {
	}

	public record TierLine(int tier, long bosses, long coins, int color) {
	}

	public record Result(
		String slayerId,
		String slayerName,
		String bossName,
		Item item,
		int currentLevel,
		int targetLevel,
		float xpNeeded,
		List<TierLine> tiers,
		SlayerMayorMods mods
	) {
	}

	public static int maxLevel(String slayerId) {
		if ("vampire".equalsIgnoreCase(slayerId)) {
			return 5;
		}
		JsonArray table = RepoData.slayerXp(slayerId);
		if (table == null || table.isEmpty()) {
			return 9;
		}
		return Math.min(9, table.size());
	}

	public static Result calculate(
		String slayerId,
		String slayerName,
		float currentXp,
		int currentLevel,
		int targetLevel,
		SlayerMayorMods mods
	) {
		String id = slayerId == null ? "" : slayerId.toLowerCase(Locale.ROOT);
		SlayerMayorMods used = mods == null ? SlayerMayorMods.none() : mods;
		int max = maxLevel(id);
		int target = Math.max(1, Math.min(max, targetLevel));
		JsonArray table = RepoData.slayerXp(id);
		float needXp = Math.max(0F, Leveling.xpRequiredForLevel(table, target, true) - Math.max(0F, currentXp));
		JsonArray bossXp = RepoData.slayerBossXp(id);
		int highest = RepoData.slayerHighestTier(id);
		double xpMult = used.xpMultiplier();
		double priceMult = used.priceMultiplier();
		List<TierLine> lines = new ArrayList<>();
		int limit = Math.max(1, Math.min(highest, QUEST_COSTS.length));
		if (bossXp != null) {
			limit = Math.min(limit, bossXp.size());
		}
		for (int i = 0; i < limit; i++) {
			float baseXp = bossXp != null && i < bossXp.size() ? bossXp.get(i).getAsFloat() : 0F;
			float perBoss = (float) (baseXp * xpMult);
			long bosses = 0L;
			if (needXp > 0F && perBoss > 0F) {
				bosses = (long) Math.ceil(needXp / perBoss);
			}
			long coins = Math.round(bosses * QUEST_COSTS[i] * priceMult);
			lines.add(new TierLine(i + 1, bosses, coins, tierColor(i)));
		}
		return new Result(
			id,
			slayerName == null ? "" : slayerName,
			bossName(id),
			item(id),
			Math.max(0, currentLevel),
			target,
			needXp,
			List.copyOf(lines),
			used
		);
	}

	public static String bossName(String slayerId) {
		return switch (slayerId == null ? "" : slayerId.toLowerCase(Locale.ROOT)) {
			case "zombie" -> "Revenant Horror";
			case "spider" -> "Tarantula Broodfather";
			case "wolf" -> "Sven Packmaster";
			case "enderman" -> "Voidgloom Seraph";
			case "blaze" -> "Inferno Demonlord";
			case "vampire" -> "Riftstalker Bloodfiend";
			default -> "Slayer";
		};
	}

	public static Item item(String slayerId) {
		return switch (slayerId == null ? "" : slayerId.toLowerCase(Locale.ROOT)) {
			case "zombie" -> Items.ROTTEN_FLESH;
			case "spider" -> Items.STRING;
			case "wolf" -> Items.BONE;
			case "enderman" -> Items.ENDER_PEARL;
			case "blaze" -> Items.BLAZE_POWDER;
			case "vampire" -> Items.REDSTONE;
			default -> Items.PAPER;
		};
	}

	private static int tierColor(int index) {
		return switch (index) {
			case 0 -> 0xFF55FF55;
			case 1 -> 0xFFFFFF55;
			case 2 -> 0xFFFF5555;
			case 3 -> 0xFFAA0000;
			default -> 0xFFAA00AA;
		};
	}
}

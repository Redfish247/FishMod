/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.function.Function;

/** Per-shop totals for the Essence overview. Perks without NEU cost data are counted, not guessed. */
public record EssenceSummary(
	boolean loaded,
	int perksMaxed,
	int perkCount,
	int levels,
	int maxLevels,
	long toMax,
	int unknownCostPerks
) {
	public static EssenceSummary of(DungeonSnapshot.EssenceShop shop, Function<String, int[]> costs) {
		if (shop == null || shop.perks() == null || shop.perks().isEmpty()) {
			return new EssenceSummary(false, 0, 0, 0, 0, 0L, 0);
		}
		int maxed = 0;
		int levels = 0;
		int maxLevels = 0;
		long toMax = 0L;
		int unknown = 0;
		for (DungeonSnapshot.EssencePerk perk : shop.perks()) {
			levels += Math.max(0, perk.level());
			maxLevels += Math.max(0, perk.maxLevel());
			if (perk.maxed()) {
				maxed++;
				continue;
			}
			int[] tiers = costs == null ? null : costs.apply(perk.id());
			if (tiers == null || tiers.length < perk.maxLevel()) {
				unknown++;
				continue;
			}
			for (int i = Math.max(0, perk.level()); i < perk.maxLevel(); i++) {
				toMax += tiers[i];
			}
		}
		return new EssenceSummary(true, maxed, shop.perks().size(), levels, maxLevels, toMax, unknown);
	}

	public boolean complete() {
		return this.loaded && this.perkCount > 0 && this.perksMaxed == this.perkCount;
	}
}

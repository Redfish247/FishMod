/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.List;
import java.util.OptionalDouble;

/** Computed overview combat/fortune stats from profile + equipped gear lore. */
public final class PlayerStatsSnapshot {
	public record Entry(String id, String label, OptionalDouble value) {
		public Entry {
			id = id == null ? "" : id;
			label = label == null ? "" : label;
			value = value == null ? OptionalDouble.empty() : value;
		}

		public boolean present() {
			return value.isPresent();
		}
	}

	private final List<Entry> entries;
	private final FarmingFortuneEstimate farmingFortune;

	public PlayerStatsSnapshot(List<Entry> entries) {
		this(entries, null);
	}

	private PlayerStatsSnapshot(List<Entry> entries, FarmingFortuneEstimate farmingFortune) {
		this.entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
		this.farmingFortune = farmingFortune;
	}

	public PlayerStatsSnapshot withFarmingFortune(FarmingFortuneEstimate estimate) {
		return new PlayerStatsSnapshot(this.entries, estimate);
	}

	/** Null until full inventories have been parsed. */
	public FarmingFortuneEstimate farmingFortune() {
		return this.farmingFortune;
	}

	public static PlayerStatsSnapshot empty() {
		return new PlayerStatsSnapshot(List.of());
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public boolean isEmpty() {
		return this.entries.isEmpty() || this.entries.stream().noneMatch(Entry::present);
	}
}

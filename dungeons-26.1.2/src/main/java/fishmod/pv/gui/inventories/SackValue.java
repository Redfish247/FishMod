/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.networth.ItemWorth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Market value of what a sack page holds. */
record SackValue(double total, List<Entry> priced, List<Entry> unpriced, List<Shared> shared) {
	record Entry(String id, String name, long count, double unitPrice) {
		double subtotal() {
			return this.unitPrice * this.count;
		}
	}

	record Shared(String name, long count, String countedIn) {
	}

	/** No priced contents, so there is nothing honest to show. */
	boolean known() {
		return !this.priced.isEmpty() && this.total > 0;
	}

	/**
	 * One value per page. Sack counts are global per item id, so an item listed by several sacks is only
	 * valued in the first page that lists it.
	 */
	static List<SackValue> compute(
		List<InventorySnapshot.Page> pages,
		ToDoubleFunction<String> price,
		Function<String, String> name
	) {
		List<SackValue> out = new ArrayList<>();
		Map<String, String> claimedBy = new HashMap<>();
		for (InventorySnapshot.Page page : pages == null ? List.<InventorySnapshot.Page>of() : pages) {
			List<Entry> priced = new ArrayList<>();
			List<Entry> unpriced = new ArrayList<>();
			List<Shared> shared = new ArrayList<>();
			double total = 0;
			for (InventorySnapshot.Slot slot : page.slots()) {
				if (slot == null || slot.isEmpty() || slot.count() <= 0) {
					continue;
				}
				String key = slot.id().toUpperCase(Locale.ROOT);
				String owner = claimedBy.putIfAbsent(key, page.title());
				if (owner != null) {
					if (!owner.equals(page.title())) {
						shared.add(new Shared(name.apply(slot.id()), slot.count(), owner));
					}
					continue;
				}
				double unit = price.applyAsDouble(slot.id());
				Entry entry = new Entry(slot.id(), name.apply(slot.id()), slot.count(), unit);
				if (unit > 0) {
					priced.add(entry);
					total += entry.subtotal();
				} else {
					unpriced.add(entry);
				}
			}
			priced.sort(Comparator.comparingDouble(Entry::subtotal).reversed());
			out.add(new SackValue(total, List.copyOf(priced), List.copyOf(unpriced), List.copyOf(shared)));
		}
		return out;
	}

	ItemWorth.Breakdown breakdown() {
		List<ItemWorth.Part> parts = new ArrayList<>();
		for (Entry entry : this.priced) {
			parts.add(new ItemWorth.Part(
				ItemWorth.Section.CONTENTS,
				ItemWorth.Role.DETAIL,
				entry.name(),
				FormatUtil.commas(entry.count()) + " x " + FormatUtil.shortCoins(Math.round(entry.unitPrice())),
				entry.subtotal(),
				0
			));
		}
		for (Entry entry : this.unpriced) {
			parts.add(new ItemWorth.Part(
				ItemWorth.Section.CONTENTS,
				ItemWorth.Role.NOTE,
				entry.name() + " x" + FormatUtil.commas(entry.count()) + ": no price",
				"",
				0,
				0
			));
		}
		for (Shared entry : this.shared) {
			parts.add(new ItemWorth.Part(
				ItemWorth.Section.CONTENTS,
				ItemWorth.Role.NOTE,
				entry.name() + ": counted in " + entry.countedIn(),
				"",
				0,
				0
			));
		}
		return new ItemWorth.Breakdown(this.total, parts);
	}
}

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
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelItemsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Accessories missing from the bag, SkyHelper style: only the top missing tier of each chain, priced after
 * selling the tier already owned, cheapest coins per MP first.
 * Upgrade chains come from NEU {@code talisman_upgrades} plus BetterPV's {@code accessory_families.json}.
 */
public final class MissingAccessories {
	/** {@code soldId} is the owned lower tier replaced by this one, or null. */
	public record Entry(
		String id, String name, String tier, double price, int mpGain,
		String soldId, String soldName, double soldPrice, boolean soulbound
	) {
		public boolean priced() {
			return this.price > 0D;
		}

		public double netCost() {
			return Math.max(0D, this.price - Math.max(0D, this.soldPrice));
		}

		public double coinsPerMp() {
			return this.mpGain > 0 ? netCost() / this.mpGain : Double.MAX_VALUE;
		}
	}

	/** {@code costToMax} is the summed net cost of every listed entry. */
	public record Result(List<Entry> entries, double costToMax, int mpToMax, int unpricedTop) {
		public static Result empty() {
			return new Result(List.of(), 0D, 0, 0);
		}
	}

	private MissingAccessories() {
	}

	public static Result compute(Collection<String> ownedIds) {
		Collection<JsonObject> items = HypixelItemsCache.allItems();
		if (items.isEmpty()) {
			return Result.empty();
		}
		Map<String, List<String>> upgrades = NeuRepoCache.talismanUpgrades();
		Set<String> ignored = NeuRepoCache.ignoredTalismans();

		Set<String> owned = new HashSet<>();
		boolean ownsAbicase = false;
		for (String raw : ownedIds) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String id = raw.trim().toUpperCase(Locale.ROOT);
			owned.add(id);
			if (id.contains("ABICASE")) {
				ownsAbicase = true;
			}
		}

		List<Candidate> missing = new ArrayList<>();
		for (JsonObject item : items) {
			if (!"ACCESSORY".equals(str(item.get("category")))) {
				continue;
			}
			String id = str(item.get("id")).toUpperCase(Locale.ROOT);
			if (id.isBlank() || ignored.contains(id) || owned.contains(id)) {
				continue;
			}
			if (id.contains("ABICASE") && ownsAbicase) {
				continue;
			}
			// Rift accessories only work in the Rift unless Hypixel marks them transferable.
			if ("RIFT".equals(str(item.get("origin"))) && !bool(item.get("rift_transferrable"))) {
				continue;
			}
			if (ownsHigherTier(id, owned, upgrades)) {
				continue;
			}
			String tier = tierOf(str(item.get("tier")));
			String name = str(item.get("name"));
			boolean soulbound = !str(item.get("soulbound")).isBlank();
			missing.add(new Candidate(id, name.isBlank() ? id : name, tier, soulbound));
		}

		Set<String> missingIds = new HashSet<>();
		for (Candidate candidate : missing) {
			missingIds.add(candidate.id());
		}

		// Keyed by family so ids that share one (abicases) collapse to a single suggestion.
		Map<String, Entry> byFamily = new LinkedHashMap<>();
		for (Candidate candidate : missing) {
			List<String> higher = upgrades.getOrDefault(candidate.id(), List.of());
			if (higher.stream().anyMatch(missingIds::contains) || hasMissingHigherFamilyMember(candidate.id(), missingIds)) {
				continue;
			}
			OwnedTier sold = bestOwnedInChain(candidate.id(), owned, upgrades);
			int mpGain = Math.max(0, MagicalPowerCalculator.magicalPower(candidate.id(), candidate.tier()) - sold.mp());
			// Same-rarity upgrade of something already owned (e.g. Campfire 13 -> 20) adds nothing.
			if (sold.id() != null && mpGain == 0) {
				continue;
			}
			double price = ItemPricer.price(candidate.id());
			double soldPrice = sold.id() == null ? 0D : ItemPricer.price(sold.id());
			Entry entry = new Entry(
				candidate.id(), candidate.name(), candidate.tier(), price, mpGain,
				sold.id(), sold.id() == null ? null : nameOf(sold.id()), soldPrice, candidate.soulbound()
			);
			byFamily.merge(MagicalPowerCalculator.accessoryFamily(candidate.id()), entry, MissingAccessories::better);
		}

		List<Entry> entries = new ArrayList<>(byFamily.values());
		double costToMax = 0D;
		int mpToMax = 0;
		int unpricedTop = 0;
		for (Entry entry : entries) {
			mpToMax += entry.mpGain();
			if (entry.priced()) {
				costToMax += entry.netCost();
			} else {
				unpricedTop++;
			}
		}

		entries.sort(Comparator
			.comparing((Entry e) -> !e.priced())
			.thenComparingDouble(e -> e.priced() ? e.coinsPerMp() : 0D)
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		return new Result(List.copyOf(entries), costToMax, mpToMax, unpricedTop);
	}

	private static Entry better(Entry a, Entry b) {
		if (a.mpGain() != b.mpGain()) {
			return a.mpGain() > b.mpGain() ? a : b;
		}
		if (a.priced() != b.priced()) {
			return a.priced() ? a : b;
		}
		return a.netCost() <= b.netCost() ? a : b;
	}

	private static boolean ownsHigherTier(String id, Set<String> owned, Map<String, List<String>> upgrades) {
		for (String higher : upgrades.getOrDefault(id, List.of())) {
			if (owned.contains(higher)) {
				return true;
			}
		}
		String family = MagicalPowerCalculator.accessoryFamily(id);
		int rank = MagicalPowerCalculator.accessoryFamilyRank(id);
		for (String ownedId : owned) {
			if (family.equals(MagicalPowerCalculator.accessoryFamily(ownedId))
				&& MagicalPowerCalculator.accessoryFamilyRank(ownedId) >= rank) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasMissingHigherFamilyMember(String id, Set<String> missingIds) {
		String family = MagicalPowerCalculator.accessoryFamily(id);
		int rank = MagicalPowerCalculator.accessoryFamilyRank(id);
		for (String other : missingIds) {
			if (!other.equals(id)
				&& family.equals(MagicalPowerCalculator.accessoryFamily(other))
				&& MagicalPowerCalculator.accessoryFamilyRank(other) > rank) {
				return true;
			}
		}
		return false;
	}

	/** Best owned lower tier in the same chain; its MP is what the upgrade replaces. */
	private static OwnedTier bestOwnedInChain(String id, Set<String> owned, Map<String, List<String>> upgrades) {
		String family = MagicalPowerCalculator.accessoryFamily(id);
		String bestId = null;
		int best = 0;
		for (String ownedId : owned) {
			// Owned ids in the same chain are always lower tiers, otherwise this id would not be missing.
			boolean sameChain = upgrades.getOrDefault(ownedId, List.of()).contains(id)
				|| family.equals(MagicalPowerCalculator.accessoryFamily(ownedId));
			if (!sameChain) {
				continue;
			}
			JsonObject def = HypixelItemsCache.get(ownedId);
			String tier = tierOf(def == null ? "" : str(def.get("tier")));
			int mp = MagicalPowerCalculator.magicalPower(ownedId, tier);
			if (bestId == null || mp > best) {
				bestId = ownedId;
				best = mp;
			}
		}
		return new OwnedTier(bestId, best);
	}

	private static String nameOf(String id) {
		JsonObject def = HypixelItemsCache.get(id);
		String name = def == null ? "" : str(def.get("name"));
		return name.isBlank() ? id : name;
	}

	/** Hypixel omits {@code tier} for common items. */
	private static String tierOf(String tier) {
		return tier == null || tier.isBlank() ? "COMMON" : tier.trim().toUpperCase(Locale.ROOT);
	}

	private static String str(JsonElement element) {
		return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
	}

	private static boolean bool(JsonElement element) {
		return element != null && element.isJsonPrimitive() && "true".equalsIgnoreCase(element.getAsString());
	}

	private record Candidate(String id, String name, String tier, boolean soulbound) {
	}

	private record OwnedTier(String id, int mp) {
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import fishmod.pv.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catacombs / Kuudra boss collections (not in Hypixel's collections resource).
 * Amounts mirror SkyCrypt: floor completions + 2× master, Kuudra weighted by tier.
 */
public final class BossCollections {
	public record BossDef(String id, String name, String iconId, long[] thresholds) {
		public HypixelCollectionsCache.Item toItem() {
			List<HypixelCollectionsCache.Tier> tiers = new ArrayList<>();
			for (int i = 0; i < thresholds.length; i++) {
				tiers.add(new HypixelCollectionsCache.Tier(i + 1, thresholds[i], List.of()));
			}
			return new HypixelCollectionsCache.Item(id, name, thresholds.length, tiers);
		}
	}

	private static final List<BossDef> BOSSES = List.of(
		new BossDef("BOSS_BONZO", "Bonzo", "GOLD_BONZO_HEAD", new long[] { 25, 50, 100, 150, 250, 1000 }),
		new BossDef("BOSS_SCARF", "Scarf", "GOLD_SCARF_HEAD", new long[] { 25, 50, 100, 150, 250, 1000 }),
		new BossDef("BOSS_PROFESSOR", "Professor", "GOLD_PROFESSOR_HEAD", new long[] { 25, 50, 100, 150, 250, 1000 }),
		new BossDef("BOSS_THORN", "Thorn", "GOLD_THORN_HEAD", new long[] { 50, 100, 150, 250, 400, 1000 }),
		new BossDef("BOSS_LIVID", "Livid", "GOLD_LIVID_HEAD", new long[] { 50, 100, 150, 250, 500, 750, 1000 }),
		new BossDef("BOSS_SADAN", "Sadan", "GOLD_SADAN_HEAD", new long[] { 50, 100, 150, 250, 500, 750, 1000 }),
		new BossDef("BOSS_NECRON", "Necron", "GOLD_NECRON_HEAD", new long[] { 50, 100, 150, 250, 500, 750, 1000 }),
		new BossDef("BOSS_KUUDRA", "Kuudra", "KUUDRA;4", new long[] { 10, 100, 500, 2000, 5000 })
	);

	private static final String[] KUUDRA_TIERS = { "none", "hot", "burning", "fiery", "infernal" };

	private BossCollections() {
	}

	public static boolean isBossId(String id) {
		return id != null && id.toUpperCase(Locale.ROOT).startsWith("BOSS_");
	}

	public static HypixelCollectionsCache.Category category() {
		List<HypixelCollectionsCache.Item> items = new ArrayList<>(BOSSES.size());
		for (BossDef boss : BOSSES) {
			items.add(boss.toItem());
		}
		return new HypixelCollectionsCache.Category("BOSS", "Boss", items);
	}

	public static List<BossDef> bosses() {
		return BOSSES;
	}

	/** Floor / Kuudra completion amounts keyed by {@code BOSS_*} ids. */
	public static Map<String, Long> amountsFromMember(JsonObject member) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (member == null) {
			return out;
		}
		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : Leveling.obj(dungeons.get("dungeon_types"));
		JsonObject normal = types == null ? null : Leveling.obj(types.get("catacombs"));
		JsonObject master = types == null ? null : Leveling.obj(types.get("master_catacombs"));
		JsonObject normalCompletions = normal == null ? null : Leveling.obj(normal.get("tier_completions"));
		JsonObject masterCompletions = master == null ? null : Leveling.obj(master.get("tier_completions"));

		// Bonzo..Necron map to floors 1..7
		for (int i = 0; i < 7; i++) {
			BossDef boss = BOSSES.get(i);
			String floor = String.valueOf(i + 1);
			long amount = readLong(normalCompletions, floor) + readLong(masterCompletions, floor) * 2L;
			if (amount > 0L) {
				out.put(boss.id(), amount);
			}
		}

		long kuudra = kuudraAmount(member);
		if (kuudra > 0L) {
			out.put("BOSS_KUUDRA", kuudra);
		}
		return out;
	}

	private static long kuudraAmount(JsonObject member) {
		JsonObject nether = Leveling.obj(member.get("nether_island_player_data"));
		JsonObject tiers = nether == null ? null : Leveling.obj(nether.get("kuudra_completed_tiers"));
		if (tiers == null) {
			return 0L;
		}
		long sum = 0L;
		for (int i = 0; i < KUUDRA_TIERS.length; i++) {
			sum += readLong(tiers, KUUDRA_TIERS[i]) * (i + 1L);
		}
		return sum;
	}

	private static long readLong(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key)) {
			return 0L;
		}
		return Leveling.longAmount(obj.get(key));
	}
}

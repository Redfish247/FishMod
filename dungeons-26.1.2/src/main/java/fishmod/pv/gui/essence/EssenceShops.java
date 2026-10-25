/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence;

import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.MiningSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The 11 essence shops in selector order. Shops are read from snapshots the other tabs already decoded. */
public final class EssenceShops {
	public enum Group {
		DUNGEONS("Dungeons"),
		MINING("Mining"),
		OTHER("Other");

		private final String label;

		Group(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	public record Def(String id, String name, Group group, int color) {
	}

	public record Entry(Def def, DungeonSnapshot.EssenceShop shop) {
	}

	public static final List<Def> ALL = List.of(
		new Def("wither", "Wither", Group.DUNGEONS, 0xFFAAAAAA),
		new Def("undead", "Undead", Group.DUNGEONS, 0xFFFF8EC8),
		new Def("ice", "Ice", Group.DUNGEONS, 0xFF55FFFF),
		new Def("spider", "Spider", Group.DUNGEONS, 0xFF55FF55),
		new Def("dragon", "Dragon", Group.DUNGEONS, 0xFFFF5555),
		new Def("gold", "Gold", Group.MINING, 0xFFFFAA00),
		new Def("diamond", "Diamond", Group.MINING, 0xFF55FFFF),
		new Def("fossil", "Fossil", Group.MINING, 0xFFE8D5A3),
		new Def("forest", "Forest", Group.OTHER, 0xFF55AA55),
		new Def("safari", "Safari", Group.OTHER, 0xFF7CFF9A),
		new Def("crimson", "Crimson", Group.OTHER, 0xFFFF5555)
	);

	private EssenceShops() {
	}

	public static List<Entry> resolve(Function<String, DungeonSnapshot.EssenceShop> lookup) {
		List<Entry> out = new ArrayList<>(ALL.size());
		for (Def def : ALL) {
			DungeonSnapshot.EssenceShop shop = lookup == null ? null : lookup.apply(def.id());
			out.add(new Entry(def, shop == null ? DungeonSnapshot.EssenceShop.empty(def.id(), def.name()) : shop));
		}
		return out;
	}

	public static Function<String, DungeonSnapshot.EssenceShop> lookup(
		DungeonSnapshot dungeons, MiningSnapshot mining, ForagingSnapshot foraging, CrimsonSnapshot crimson
	) {
		DungeonSnapshot d = dungeons == null ? DungeonSnapshot.empty() : dungeons;
		MiningSnapshot m = mining == null ? MiningSnapshot.empty() : mining;
		ForagingSnapshot f = foraging == null ? ForagingSnapshot.empty() : foraging;
		CrimsonSnapshot c = crimson == null ? CrimsonSnapshot.empty() : crimson;
		return id -> switch (id) {
			case "wither" -> d.witherShop();
			case "undead" -> d.undeadShop();
			case "ice" -> d.iceShop();
			case "spider" -> d.spiderShop();
			case "dragon" -> d.dragonShop();
			case "gold" -> m.goldShop();
			case "diamond" -> m.diamondShop();
			case "fossil" -> m.fossilShop();
			case "forest" -> f.forestShop();
			case "safari" -> f.safariShop();
			case "crimson" -> c.crimsonShop();
			default -> null;
		};
	}

	public static int indexOf(String id) {
		for (int i = 0; i < ALL.size(); i++) {
			if (ALL.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}

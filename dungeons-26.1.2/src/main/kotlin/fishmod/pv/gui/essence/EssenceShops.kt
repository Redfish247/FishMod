/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence

import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.ForagingSnapshot
import fishmod.pv.data.MiningSnapshot
import java.util.function.Function

object EssenceShops {
	enum class Group(private val label: String) {
		DUNGEONS("Dungeons"),
		MINING("Mining"),
		OTHER("Other");

		fun label(): String = label
	}

	@JvmRecord
	data class Def(val id: String, val name: String, val group: Group, val color: Int)

	@JvmRecord
	data class Entry(val def: Def, val shop: DungeonSnapshot.EssenceShop)

	@JvmField
	val ALL: List<Def> = listOf(
		Def("wither", "Wither", Group.DUNGEONS, 0xFFAAAAAA.toInt()),
		Def("undead", "Undead", Group.DUNGEONS, 0xFFFF8EC8.toInt()),
		Def("ice", "Ice", Group.DUNGEONS, 0xFF55FFFF.toInt()),
		Def("spider", "Spider", Group.DUNGEONS, 0xFF55FF55.toInt()),
		Def("dragon", "Dragon", Group.DUNGEONS, 0xFFFF5555.toInt()),
		Def("gold", "Gold", Group.MINING, 0xFFFFAA00.toInt()),
		Def("diamond", "Diamond", Group.MINING, 0xFF55FFFF.toInt()),
		Def("fossil", "Fossil", Group.MINING, 0xFFE8D5A3.toInt()),
		Def("forest", "Forest", Group.OTHER, 0xFF55AA55.toInt()),
		Def("safari", "Safari", Group.OTHER, 0xFF7CFF9A.toInt()),
		Def("crimson", "Crimson", Group.OTHER, 0xFFFF5555.toInt()),
	)

	@JvmStatic
	fun resolve(lookup: Function<String, DungeonSnapshot.EssenceShop?>?): List<Entry> =
		ALL.map { def ->
			Entry(def, lookup?.apply(def.id) ?: DungeonSnapshot.EssenceShop.empty(def.id, def.name))
		}

	@JvmStatic
	fun lookup(
		dungeons: DungeonSnapshot?, mining: MiningSnapshot?, foraging: ForagingSnapshot?, crimson: CrimsonSnapshot?,
	): Function<String, DungeonSnapshot.EssenceShop?> {
		val d = dungeons ?: DungeonSnapshot.empty()
		val m = mining ?: MiningSnapshot.empty()
		val f = foraging ?: ForagingSnapshot.empty()
		val c = crimson ?: CrimsonSnapshot.empty()
		return Function { id ->
			when (id) {
				"wither" -> d.witherShop
				"undead" -> d.undeadShop
				"ice" -> d.iceShop
				"spider" -> d.spiderShop
				"dragon" -> d.dragonShop
				"gold" -> m.goldShop
				"diamond" -> m.diamondShop
				"fossil" -> m.fossilShop
				"forest" -> f.forestShop
				"safari" -> f.safariShop
				"crimson" -> c.crimsonShop
				else -> null
			}
		}
	}

	@JvmStatic
	fun indexOf(id: String?): Int = ALL.indexOfFirst { it.id == id }
}

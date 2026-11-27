/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.networth.ItemWorth
import java.util.Locale
import java.util.function.Function
import java.util.function.ToDoubleFunction

@JvmRecord
data class SackValue(val total: Double, val priced: List<Entry>, val unpriced: List<Entry>, val shared: List<Shared>) {
	@JvmRecord
	data class Entry(val id: String, val name: String, val count: Long, val unitPrice: Double) {
		fun subtotal(): Double = unitPrice * count
	}

	@JvmRecord
	data class Shared(val name: String, val count: Long, val countedIn: String)

	fun known(): Boolean = priced.isNotEmpty() && total > 0

	fun breakdown(): ItemWorth.Breakdown {
		val parts = ArrayList<ItemWorth.Part>()
		for (entry in priced) {
			parts.add(
				ItemWorth.Part(
					ItemWorth.Section.CONTENTS,
					ItemWorth.Role.DETAIL,
					entry.name,
					FormatUtil.commas(entry.count) + " x " + FormatUtil.shortCoins(Math.round(entry.unitPrice).toDouble()),
					entry.subtotal(),
					0
				)
			)
		}
		for (entry in unpriced) {
			parts.add(
				ItemWorth.Part(
					ItemWorth.Section.CONTENTS,
					ItemWorth.Role.NOTE,
					entry.name + " x" + FormatUtil.commas(entry.count) + ": no price",
					"",
					0.0,
					0
				)
			)
		}
		for (entry in shared) {
			parts.add(
				ItemWorth.Part(
					ItemWorth.Section.CONTENTS,
					ItemWorth.Role.NOTE,
					entry.name + ": counted in " + entry.countedIn,
					"",
					0.0,
					0
				)
			)
		}
		return ItemWorth.Breakdown(total, parts)
	}

	companion object {
		@JvmStatic
		fun compute(
			pages: List<InventorySnapshot.Page>?,
			price: ToDoubleFunction<String>,
			name: Function<String, String>
		): List<SackValue> {
			val out = ArrayList<SackValue>()
			val claimedBy = HashMap<String, String>()
			for (page in pages ?: listOf()) {
				val priced = ArrayList<Entry>()
				val unpriced = ArrayList<Entry>()
				val shared = ArrayList<Shared>()
				var total = 0.0
				for (slot in page.slots) {
					if (slot == null || slot.isEmpty() || slot.count <= 0) {
						continue
					}
					val id = slot.id ?: continue
					val key = id.uppercase(Locale.ROOT)
					val owner = claimedBy.putIfAbsent(key, page.title)
					if (owner != null) {
						if (owner != page.title) {
							shared.add(Shared(name.apply(id), slot.count.toLong(), owner))
						}
						continue
					}
					val unit = price.applyAsDouble(id)
					val entry = Entry(id, name.apply(id), slot.count.toLong(), unit)
					if (unit > 0) {
						priced.add(entry)
						total += entry.subtotal()
					} else {
						unpriced.add(entry)
					}
				}
				priced.sortByDescending { it.subtotal() }
				out.add(SackValue(total, priced.toList(), unpriced.toList(), shared.toList()))
			}
			return out
		}
	}
}

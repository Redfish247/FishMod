/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer

import com.google.gson.JsonObject
import fishmod.pv.data.Leveling
import java.util.Locale

/** Mayor / city-project slayer XP and quest-price modifiers. */
@JvmRecord
data class SlayerMayorMods(
	val aatroxXp: Boolean,
	val aatroxPrice: Boolean,
	val cityProject: Boolean,
	val auraXp: Boolean,
	val auraPrice: Boolean
) {
	fun xpMultiplier(): Double {
		var mult = 1.0
		if (aatroxXp) mult *= 1.25
		if (auraXp) mult *= 1.50
		return mult
	}

	fun priceMultiplier(): Double {
		var mult = 1.0
		if (aatroxPrice) mult *= 0.50
		if (cityProject) mult *= 0.95
		if (auraPrice) mult *= 2.0
		return mult
	}

	fun any(): Boolean = aatroxXp || aatroxPrice || cityProject || auraXp || auraPrice

	fun labels(): MutableList<String> {
		val out = ArrayList<String>()
		if (aatroxXp) out.add("Aatrox +25% XP")
		if (aatroxPrice) out.add("Aatrox -50% price")
		if (cityProject) out.add("City Project -5% price")
		if (auraXp) out.add("Aura +50% XP")
		if (auraPrice) out.add("Aura 2x price")
		return out
	}

	private data class Perk(val name: String, val description: String)

	companion object {
		@JvmStatic
		fun none(): SlayerMayorMods = SlayerMayorMods(false, false, false, false, false)

		@JvmStatic
		fun from(electionRoot: JsonObject?): SlayerMayorMods {
			if (electionRoot == null) return none()
			var aatroxXp = false
			var aatroxPrice = false
			var cityProject = false
			var auraXp = false
			var auraPrice = false
			for (perk in collectPerks(electionRoot)) {
				val blob = (perk.name + " " + perk.description).lowercase(Locale.ROOT)
				if (blob.contains("slayer xp buff") ||
					(blob.contains("slayer") && hasBarePercent(blob, 25) && blob.contains("xp"))
				) {
					aatroxXp = true
				}
				if (blob.contains("slashed") ||
					(blob.contains("slayer") && (blob.contains("half price") || (hasBarePercent(blob, 50) && blob.contains("price")))) ||
					(blob.contains("starting slayer") && blob.contains("half"))
				) {
					aatroxPrice = true
				}
				if (blob.contains("city project") ||
					blob.contains("cityproject") ||
					blob.contains("slayer discount") ||
					blob.contains("slayer discounts") ||
					(blob.contains("slayer") && hasBarePercent(blob, 5) &&
						(blob.contains("price") || blob.contains("cost") || blob.contains("discount") || blob.contains("cheaper")))
				) {
					cityProject = true
				}
				if (blob.contains("work smarter") ||
					(blob.contains("slayer") && hasBarePercent(blob, 50) && blob.contains("xp")) ||
					(blob.contains("skill") && hasBarePercent(blob, 50) && blob.contains("slayer"))
				) {
					auraXp = true
				}
				if (blob.contains("fundraising") ||
					blob.contains("taxes and costs") ||
					(blob.contains("vastly increased") && blob.contains("cost")) ||
					((blob.contains("2x") || blob.contains("twice")) && (blob.contains("price") || blob.contains("cost")))
				) {
					auraPrice = true
				}
			}
			return SlayerMayorMods(aatroxXp, aatroxPrice, cityProject, auraXp, auraPrice)
		}

		/** True when `n%` appears as its own number, so `5%` does not match `25%` / `50%`. */
		private fun hasBarePercent(blob: String?, n: Int): Boolean {
			if (blob.isNullOrBlank()) return false
			val token = "$n%"
			var from = 0
			while (from < blob.length) {
				val at = blob.indexOf(token, from)
				if (at < 0) return false
				if (at == 0 || !blob[at - 1].isDigit()) return true
				from = at + 1
			}
			return false
		}

		private fun collectPerks(root: JsonObject): List<Perk> {
			val out = ArrayList<Perk>()
			val mayor = Leveling.obj(root.get("mayor"))
			addPerks(out, mayor)
			if (mayor != null) addPerks(out, Leveling.obj(mayor.get("minister")))
			addPerks(out, Leveling.obj(root.get("minister")))
			return out
		}

		private fun addPerks(out: MutableList<Perk>, obj: JsonObject?) {
			val raw = obj?.get("perks")
			if (raw == null || !raw.isJsonArray) return
			for (el in raw.asJsonArray) {
				val perk = Leveling.obj(el) ?: continue
				out.add(Perk(primitiveString(perk, "name"), primitiveString(perk, "description")))
			}
		}

		private fun primitiveString(obj: JsonObject, key: String): String {
			val el = obj.get(key)
			return if (el != null && el.isJsonPrimitive) el.asString else ""
		}
	}
}

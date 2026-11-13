/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.utils.TextStrip

import java.util.Locale
import java.util.function.Function
import java.util.regex.Pattern

object AbiphoneCosts {
	private val COINS: Pattern = Pattern.compile("^([0-9][0-9.,]*) coins$", Pattern.CASE_INSENSITIVE)
	private val ITEM: Pattern = Pattern.compile("^([0-9]+)x (.+)$", Pattern.CASE_INSENSITIVE)
	private val TRAILING_PAREN = Regex("\\s*\\(.*\\)\\s*$")

	/** coins is null when the requirement can't be priced. */
	@JvmRecord
	data class Estimate(val coins: Double?) {
		fun known(): Boolean = coins != null

		companion object {
			@JvmField
			val UNKNOWN = Estimate(null)
		}
	}

	@JvmStatic
	fun estimate(
		requirement: List<String?>?,
		idForName: Function<String, String?>,
		knownPrice: Function<String, Double?>
	): Estimate {
		if (requirement.isNullOrEmpty()) {
			return Estimate.UNKNOWN
		}
		var total = 0.0
		var any = false
		for (raw in requirement) {
			val line = clean(raw)
			if (line.isEmpty()) {
				continue
			}
			any = true
			val lower = line.lowercase(Locale.ROOT)
			if (lower.endsWith(" or") || lower.startsWith("or ")) {
				return Estimate.UNKNOWN
			}
			if (lower == "no requirement") {
				continue
			}
			val coins = COINS.matcher(line)
			if (coins.matches()) {
				val digits = coins.group(1).replace(",", "").replace(".", "")
				total += digits.toLongOrNull() ?: return Estimate.UNKNOWN
				continue
			}
			val item = ITEM.matcher(line)
			if (!item.matches()) {
				return Estimate.UNKNOWN
			}
			val qty = item.group(1).toInt()
			val id = resolve(item.group(2).trim(), idForName)
			val price = if (id == null) null else knownPrice.apply(id)
			if (price == null || price <= 0) {
				return Estimate.UNKNOWN
			}
			total += qty * price
		}
		return if (any) Estimate(total) else Estimate.UNKNOWN
	}

	@JvmStatic
	fun clean(raw: String?): String {
		if (raw == null) {
			return ""
		}
		var line = TextStrip.plain(raw).trim()
		if (line.startsWith("-")) {
			line = line.substring(1).trim()
		}
		line = TRAILING_PAREN.replace(line, "")
		while (line.endsWith(".")) {
			line = line.substring(0, line.length - 1).trim()
		}
		return line
	}

	private fun resolve(name: String, idForName: Function<String, String?>): String? {
		idForName.apply(name)?.let { return it }
		if (name.endsWith("ies")) {
			return idForName.apply(name.substring(0, name.length - 3) + "y")
		}
		if (name.endsWith("s") && !name.endsWith("ss")) {
			return idForName.apply(name.substring(0, name.length - 1))
		}
		return null
	}
}

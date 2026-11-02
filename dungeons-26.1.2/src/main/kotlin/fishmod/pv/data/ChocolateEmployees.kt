/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.util.Locale

/**
 * Chocolate Factory rabbit employees - skull textures + hire-order rarities.
 * Texture hashes from community wiki {@code SkinRender} pages.
 */
object ChocolateEmployees {
	@JvmRecord
	data class Def(val id: String, val displayName: String, val rarity: String, val skullValue: String, val order: Int)

	private val BY_ID: Map<String, Def> = listOf(
		Def("rabbit_bro", "Rabbit Bro", "COMMON",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjg3OTM0YmRkOWRmMjcwNWIyNTFiYjk5N2UwMjliMThjMWU5NGRmMTI5OTJiODEwN2U3NDQ5N2IyMDVjYTdlOCJ9fX0=",
			0),
		Def("rabbit_cousin", "Rabbit Cousin", "UNCOMMON",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTk4MjgyNWMwMWI2NThmMzQ4YTA5OWI0NTc5MDI5YTE4MGQyZTQxNTE4Mzk1MWIyZTZlNWUyNzI1N2RmNDI1NCJ9fX0=",
			1),
		Def("rabbit_sis", "Rabbit Sis", "RARE",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmQwNzZlMGUzZDQwNzJkMGZmZmVlMGE4N2E1ZDcyNmZjMzRiMmJjZWMzOGMyNjRmYjliNjc4NzFhOGVhZDYzMyJ9fX0=",
			2),
		Def("rabbit_father", "Rabbit Daddy", "EPIC",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTdjYWIwYzM0ZDdkZGNmNzJkYjU2ZmYzNmYyODgzZjU1NGNmZjc2ZWI1ZDNiM2UwNTYyMzM4MDM2Yzk3NjA0MyJ9fX0=",
			3),
		Def("rabbit_grandma", "Rabbit Granny", "LEGENDARY",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDZlYjJkODVlZThlM2FmMWMyZWM5MzRiZWI3MGEzOWM1ZTc2NmIyM2JkYWI2MzIxMGJkMmFhY2Q3M2NiYmZjOCJ9fX0=",
			4),
		Def("rabbit_uncle", "Rabbit Uncle", "MYTHIC",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg2NTE3NjcyM2EwYjllZTI5MTYxODBhNTVhMDRjY2NiNzcwNGFkMWYzMWZkZjNlOWQ4OWM3OThmNjgwMmU2YiJ9fX0=",
			5),
		Def("rabbit_dog", "Rabbit Dog", "DIVINE",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzVjYTk4YmVkZTM4NjVkZDEyMDVlNGQwOTEwMzZjZDlkYzM2NzkxYjgzZWE0ZTBmZjRhOTlhZDYxYjcxZTg5OCJ9fX0=",
			6)
	).associateBy { it.id }

	@JvmStatic
	fun def(employeeId: String?): Def? {
		if (employeeId.isNullOrBlank()) {
			return null
		}
		return BY_ID[employeeId.lowercase(Locale.ROOT)]
	}

	@JvmStatic
	fun rarityOf(employeeId: String?): String = def(employeeId)?.rarity ?: "COMMON"

	@JvmStatic
	fun displayName(employeeId: String?, fallback: String?): String? {
		def(employeeId)?.let { return it.displayName }
		return if (fallback.isNullOrBlank()) employeeId else fallback
	}

	@JvmStatic
	fun orderOf(employeeId: String?): Int = def(employeeId)?.order ?: 100

	@JvmStatic
	fun skullValue(employeeId: String?): String? = def(employeeId)?.skullValue
}

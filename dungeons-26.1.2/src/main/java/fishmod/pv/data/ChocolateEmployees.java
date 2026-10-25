/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.Locale;
import java.util.Map;

/**
 * Chocolate Factory rabbit employees - skull textures + hire-order rarities.
 * Texture hashes from community wiki {@code SkinRender} pages.
 */
public final class ChocolateEmployees {
	public record Def(String id, String displayName, String rarity, String skullValue, int order) {
	}

	private static final Map<String, Def> BY_ID = Map.ofEntries(
		entry("rabbit_bro", "Rabbit Bro", "COMMON",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjg3OTM0YmRkOWRmMjcwNWIyNTFiYjk5N2UwMjliMThjMWU5NGRmMTI5OTJiODEwN2U3NDQ5N2IyMDVjYTdlOCJ9fX0=",
			0),
		entry("rabbit_cousin", "Rabbit Cousin", "UNCOMMON",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTk4MjgyNWMwMWI2NThmMzQ4YTA5OWI0NTc5MDI5YTE4MGQyZTQxNTE4Mzk1MWIyZTZlNWUyNzI1N2RmNDI1NCJ9fX0=",
			1),
		entry("rabbit_sis", "Rabbit Sis", "RARE",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmQwNzZlMGUzZDQwNzJkMGZmZmVlMGE4N2E1ZDcyNmZjMzRiMmJjZWMzOGMyNjRmYjliNjc4NzFhOGVhZDYzMyJ9fX0=",
			2),
		entry("rabbit_father", "Rabbit Daddy", "EPIC",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTdjYWIwYzM0ZDdkZGNmNzJkYjU2ZmYzNmYyODgzZjU1NGNmZjc2ZWI1ZDNiM2UwNTYyMzM4MDM2Yzk3NjA0MyJ9fX0=",
			3),
		entry("rabbit_grandma", "Rabbit Granny", "LEGENDARY",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDZlYjJkODVlZThlM2FmMWMyZWM5MzRiZWI3MGEzOWM1ZTc2NmIyM2JkYWI2MzIxMGJkMmFhY2Q3M2NiYmZjOCJ9fX0=",
			4),
		entry("rabbit_uncle", "Rabbit Uncle", "MYTHIC",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg2NTE3NjcyM2EwYjllZTI5MTYxODBhNTVhMDRjY2NiNzcwNGFkMWYzMWZkZjNlOWQ4OWM3OThmNjgwMmU2YiJ9fX0=",
			5),
		entry("rabbit_dog", "Rabbit Dog", "DIVINE",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzVjYTk4YmVkZTM4NjVkZDEyMDVlNGQwOTEwMzZjZDlkYzM2NzkxYjgzZWE0ZTBmZjRhOTlhZDYxYjcxZTg5OCJ9fX0=",
			6)
	);

	private ChocolateEmployees() {
	}

	public static Def def(String employeeId) {
		if (employeeId == null || employeeId.isBlank()) {
			return null;
		}
		return BY_ID.get(employeeId.toLowerCase(Locale.ROOT));
	}

	public static String rarityOf(String employeeId) {
		Def def = def(employeeId);
		return def == null ? "COMMON" : def.rarity();
	}

	public static String displayName(String employeeId, String fallback) {
		Def def = def(employeeId);
		if (def != null) {
			return def.displayName();
		}
		return fallback == null || fallback.isBlank() ? employeeId : fallback;
	}

	public static int orderOf(String employeeId) {
		Def def = def(employeeId);
		return def == null ? 100 : def.order();
	}

	public static String skullValue(String employeeId) {
		Def def = def(employeeId);
		return def == null ? null : def.skullValue();
	}

	private static Map.Entry<String, Def> entry(
		String id, String name, String rarity, String skull, int order
	) {
		return Map.entry(id, new Def(id, name, rarity, skull, order));
	}
}

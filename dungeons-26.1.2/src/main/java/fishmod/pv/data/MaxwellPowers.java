/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maxwell accessory-bag powers. Scaled stats use Hypixel's MP multiplier:
 * {@code 29.97 * ln(1 + 0.0019 * MP)^1.2}. Base values are the wiki "Base" column
 * (≈ stats at multiplier 1); unique bonuses are flat.
 */
public final class MaxwellPowers {
	private static final Map<String, PowerDef> POWERS = new HashMap<>();

	static {
		// Starter / intermediate (unlocked without stones)
		put("fortuitous", Map.of(
			"health", 3.35, "defense", 1.2, "strength", 4.8, "critical_chance", 4.35, "critical_damage", 4.8
		), Map.of());
		put("pretty", Map.of(
			"health", 1.65, "defense", 1.2, "speed", 0.65, "strength", 4.8, "intelligence", 10.8,
			"critical_chance", 0.475, "critical_damage", 1.2
		), Map.of());
		put("protected", Map.of(
			"health", 11.75, "defense", 10.8, "strength", 2.4, "critical_chance", 0.475, "critical_damage", 1.2
		), Map.of());
		put("simple", Map.of(
			"health", 5.02, "defense", 3.6, "speed", 1.2, "strength", 3.6, "intelligence", 5.4,
			"critical_chance", 1.45, "critical_damage", 3.6
		), Map.of());
		put("warrior", Map.of(
			"health", 3.35, "defense", 1.2, "strength", 8.4, "critical_chance", 2.4, "critical_damage", 6.0
		), Map.of());
		put("commando", Map.of(
			"health", 5.02, "defense", 2.4, "strength", 8.4, "critical_chance", 0.475, "critical_damage", 8.4
		), Map.of());
		put("disciplined", Map.of(
			"health", 5.02, "defense", 2.4, "strength", 7.2, "critical_chance", 1.45, "critical_damage", 7.2
		), Map.of());
		put("inspired", Map.of(
			"health", 1.65, "defense", 1.2, "strength", 4.8, "intelligence", 16.2,
			"critical_chance", 0.95, "critical_damage", 3.6
		), Map.of());
		put("ominous", Map.of(
			"health", 5.02, "speed", 0.95, "strength", 3.6, "intelligence", 6.1,
			"critical_chance", 1.45, "critical_damage", 3.6, "attack_speed", 0.9
		), Map.of());
		put("prepared", Map.of(
			"health", 12.4, "defense", 11.3, "strength", 1.95, "critical_chance", 0.4, "critical_damage", 0.95
		), Map.of());

		// Stone powers
		put("silky", Map.of("speed", 0.6, "critical_damage", 22.8), Map.of("attack_speed", 5.0));
		put("sweet", Map.of("health", 15.1, "defense", 10.8, "speed", 1.2), Map.of("speed", 5.0));
		put("forceful", Map.of("health", 1.7, "strength", 18.0, "critical_damage", 4.8), Map.of("ferocity", 4.0));
		put("bloody", Map.of("strength", 10.8, "intelligence", 3.6, "critical_damage", 10.8), Map.of("attack_speed", 10.0));
		put("shaded", Map.of("speed", 0.6, "strength", 4.8, "critical_damage", 18.0),
			Map.of("attack_speed", 3.0, "ferocity", 3.0));
		put("sighted", Map.of("intelligence", 36.0), Map.of("ability_damage", 3.0));
		put("adept", Map.of("health", 16.8, "defense", 9.6, "intelligence", 3.6),
			Map.of("health", 100.0, "defense", 50.0));
		put("itchy", Map.of("speed", 0.6, "strength", 7.2, "critical_damage", 8.4, "attack_speed", 2.15),
			Map.of("strength", 15.0, "critical_damage", 15.0));
		put("mythical", Map.of(
			"health", 5.71, "defense", 4.08, "speed", 0.96, "strength", 4.08, "intelligence", 6.12,
			"critical_chance", 1.63, "critical_damage", 4.08
		), Map.of("health", 150.0, "strength", 40.0));
		put("hurtful", Map.of("strength", 4.8, "critical_damage", 19.2), Map.of("attack_speed", 15.0));
		put("demonic", Map.of("strength", 5.5, "intelligence", 27.725), Map.of("critical_damage", 50.0));
		put("strong", Map.of("strength", 12.0, "critical_damage", 12.0),
			Map.of("strength", 25.0, "critical_damage", 25.0));
		put("healthy", Map.of("health", 33.6), Map.of("health", 200.0));
		put("slender", Map.of(
			"health", 8.4, "defense", 6.0, "speed", 0.6, "strength", 6.0, "intelligence", 7.2,
			"critical_damage", 6.0, "attack_speed", 1.1
		), Map.of("defense", 100.0, "strength", 50.0));
		put("scorching", Map.of("strength", 8.4, "critical_damage", 9.6, "attack_speed", 1.8),
			Map.of("ferocity", 7.0));
		put("bubba", Map.of(
			"health", 5.1, "defense", -9.6, "strength", 6.0, "critical_chance", 0.9,
			"critical_damage", 10.8, "attack_speed", 1.8, "true_defense", 1.2
		), Map.of());
		put("bizarre", Map.of("strength", -2.4, "intelligence", 43.2, "critical_damage", -2.4),
			Map.of("ability_damage", 5.0));
		put("sanguisuge", Map.of(
			"health", 5.1, "strength", 12.0, "critical_damage", 4.8, "vitality", 1.2
		), Map.of("intelligence", 100.0));
		put("frozen", Map.of(
			"defense", 14.4, "critical_damage", 12.0, "strength", 6.0, "speed", -1.8
		), Map.of("strength", 25.0, "critical_damage", 25.0, "true_defense", 10.0));
		put("crumbly", Map.of(
			"health", 10.1, "intelligence", 5.4, "true_defense", 0.6, "vitality", 2.4, "health_regen", 1.8
		), Map.of("speed", 25.0));
		put("pleasant", Map.of("health", 20.16, "defense", 9.6), Map.of());
		put("buttery", Map.of("attack_speed", 2.16, "speed", 2.4), Map.of("attack_speed", 5.0));
	}

	private MaxwellPowers() {
	}

	public static Map<String, Double> statsFor(String power, int magicalPower) {
		if (power == null || power.isBlank() || magicalPower <= 0) {
			return Map.of();
		}
		PowerDef def = POWERS.get(power.toLowerCase(Locale.ROOT));
		if (def == null) {
			return Map.of();
		}
		double mult = 29.97 * Math.pow(Math.log(1.0 + 0.0019 * magicalPower), 1.2);
		Map<String, Double> out = new HashMap<>();
		for (var e : def.base.entrySet()) {
			out.merge(e.getKey(), e.getValue() * mult, Double::sum);
		}
		for (var e : def.unique.entrySet()) {
			out.merge(e.getKey(), e.getValue(), Double::sum);
		}
		return out;
	}

	private static void put(String id, Map<String, Double> base, Map<String, Double> unique) {
		POWERS.put(id, new PowerDef(Map.copyOf(base), Map.copyOf(unique)));
	}

	private record PowerDef(Map<String, Double> base, Map<String, Double> unique) {
	}
}

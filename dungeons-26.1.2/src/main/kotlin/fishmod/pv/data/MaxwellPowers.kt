/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import kotlin.math.ln
import kotlin.math.pow

object MaxwellPowers {
    private class PowerDef(val base: Map<String, Double>, val unique: Map<String, Double>)

    private val POWERS = HashMap<String, PowerDef>()

    init {
        put("fortuitous", mapOf(
            "health" to 3.35, "defense" to 1.2, "strength" to 4.8, "critical_chance" to 4.35, "critical_damage" to 4.8,
        ), mapOf())
        put("pretty", mapOf(
            "health" to 1.65, "defense" to 1.2, "speed" to 0.65, "strength" to 4.8, "intelligence" to 10.8,
            "critical_chance" to 0.475, "critical_damage" to 1.2,
        ), mapOf())
        put("protected", mapOf(
            "health" to 11.75, "defense" to 10.8, "strength" to 2.4, "critical_chance" to 0.475, "critical_damage" to 1.2,
        ), mapOf())
        put("simple", mapOf(
            "health" to 5.02, "defense" to 3.6, "speed" to 1.2, "strength" to 3.6, "intelligence" to 5.4,
            "critical_chance" to 1.45, "critical_damage" to 3.6,
        ), mapOf())
        put("warrior", mapOf(
            "health" to 3.35, "defense" to 1.2, "strength" to 8.4, "critical_chance" to 2.4, "critical_damage" to 6.0,
        ), mapOf())
        put("commando", mapOf(
            "health" to 5.02, "defense" to 2.4, "strength" to 8.4, "critical_chance" to 0.475, "critical_damage" to 8.4,
        ), mapOf())
        put("disciplined", mapOf(
            "health" to 5.02, "defense" to 2.4, "strength" to 7.2, "critical_chance" to 1.45, "critical_damage" to 7.2,
        ), mapOf())
        put("inspired", mapOf(
            "health" to 1.65, "defense" to 1.2, "strength" to 4.8, "intelligence" to 16.2,
            "critical_chance" to 0.95, "critical_damage" to 3.6,
        ), mapOf())
        put("ominous", mapOf(
            "health" to 5.02, "speed" to 0.95, "strength" to 3.6, "intelligence" to 6.1,
            "critical_chance" to 1.45, "critical_damage" to 3.6, "attack_speed" to 0.9,
        ), mapOf())
        put("prepared", mapOf(
            "health" to 12.4, "defense" to 11.3, "strength" to 1.95, "critical_chance" to 0.4, "critical_damage" to 0.95,
        ), mapOf())

        put("silky", mapOf("speed" to 0.6, "critical_damage" to 22.8), mapOf("attack_speed" to 5.0))
        put("sweet", mapOf("health" to 15.1, "defense" to 10.8, "speed" to 1.2), mapOf("speed" to 5.0))
        put("forceful", mapOf("health" to 1.7, "strength" to 18.0, "critical_damage" to 4.8), mapOf("ferocity" to 4.0))
        put("bloody", mapOf("strength" to 10.8, "intelligence" to 3.6, "critical_damage" to 10.8), mapOf("attack_speed" to 10.0))
        put("shaded", mapOf("speed" to 0.6, "strength" to 4.8, "critical_damage" to 18.0),
            mapOf("attack_speed" to 3.0, "ferocity" to 3.0))
        put("sighted", mapOf("intelligence" to 36.0), mapOf("ability_damage" to 3.0))
        put("adept", mapOf("health" to 16.8, "defense" to 9.6, "intelligence" to 3.6),
            mapOf("health" to 100.0, "defense" to 50.0))
        put("itchy", mapOf("speed" to 0.6, "strength" to 7.2, "critical_damage" to 8.4, "attack_speed" to 2.15),
            mapOf("strength" to 15.0, "critical_damage" to 15.0))
        put("mythical", mapOf(
            "health" to 5.71, "defense" to 4.08, "speed" to 0.96, "strength" to 4.08, "intelligence" to 6.12,
            "critical_chance" to 1.63, "critical_damage" to 4.08,
        ), mapOf("health" to 150.0, "strength" to 40.0))
        put("hurtful", mapOf("strength" to 4.8, "critical_damage" to 19.2), mapOf("attack_speed" to 15.0))
        put("demonic", mapOf("strength" to 5.5, "intelligence" to 27.725), mapOf("critical_damage" to 50.0))
        put("strong", mapOf("strength" to 12.0, "critical_damage" to 12.0),
            mapOf("strength" to 25.0, "critical_damage" to 25.0))
        put("healthy", mapOf("health" to 33.6), mapOf("health" to 200.0))
        put("slender", mapOf(
            "health" to 8.4, "defense" to 6.0, "speed" to 0.6, "strength" to 6.0, "intelligence" to 7.2,
            "critical_damage" to 6.0, "attack_speed" to 1.1,
        ), mapOf("defense" to 100.0, "strength" to 50.0))
        put("scorching", mapOf("strength" to 8.4, "critical_damage" to 9.6, "attack_speed" to 1.8),
            mapOf("ferocity" to 7.0))
        put("bubba", mapOf(
            "health" to 5.1, "defense" to -9.6, "strength" to 6.0, "critical_chance" to 0.9,
            "critical_damage" to 10.8, "attack_speed" to 1.8, "true_defense" to 1.2,
        ), mapOf())
        put("bizarre", mapOf("strength" to -2.4, "intelligence" to 43.2, "critical_damage" to -2.4),
            mapOf("ability_damage" to 5.0))
        put("sanguisuge", mapOf(
            "health" to 5.1, "strength" to 12.0, "critical_damage" to 4.8, "vitality" to 1.2,
        ), mapOf("intelligence" to 100.0))
        put("frozen", mapOf(
            "defense" to 14.4, "critical_damage" to 12.0, "strength" to 6.0, "speed" to -1.8,
        ), mapOf("strength" to 25.0, "critical_damage" to 25.0, "true_defense" to 10.0))
        put("crumbly", mapOf(
            "health" to 10.1, "intelligence" to 5.4, "true_defense" to 0.6, "vitality" to 2.4, "health_regen" to 1.8,
        ), mapOf("speed" to 25.0))
        put("pleasant", mapOf("health" to 20.16, "defense" to 9.6), mapOf())
        put("buttery", mapOf("attack_speed" to 2.16, "speed" to 2.4), mapOf("attack_speed" to 5.0))
    }

    @JvmStatic
    fun statsFor(power: String?, magicalPower: Int): Map<String, Double> {
        if (power.isNullOrBlank() || magicalPower <= 0) return emptyMap()
        val def = POWERS[power.lowercase()] ?: return emptyMap()
        val mult = 29.97 * ln(1.0 + 0.0019 * magicalPower).pow(1.2)
        val out = HashMap<String, Double>()
        for ((k, v) in def.base) out.merge(k, v * mult, Double::plus)
        for ((k, v) in def.unique) out.merge(k, v, Double::plus)
        return out
    }

    private fun put(id: String, base: Map<String, Double>, unique: Map<String, Double>) {
        POWERS[id] = PowerDef(base, unique)
    }
}

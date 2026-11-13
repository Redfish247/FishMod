/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.networth.InventoryDecoder
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NumericTag
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object DungeonModifierScanner {
	@JvmRecord
	data class Mods(val expertRing: Boolean, val hecatombLevel: Int, val scarfBonus: Double) {
		companion object {
			@JvmStatic
			fun none(): Mods = Mods(false, 0, 0.0)
		}
	}

	private val SCARF_BONUS = mapOf(
		"SCARF_STUDIES" to 0.02,
		"SCARF_THESIS" to 0.04,
		"SCARF_GRIMOIRE" to 0.06
	)

	private val CLASS_IDS = listOf("mage", "berserk", "archer", "healer", "tank")
	private val CLASS_BOOST_KEYS = listOf("experience_boost", "xp_boost", "class_xp_boost")

	@JvmStatic
	fun scan(categories: Map<String, List<InventoryDecoder.Stack?>?>?): Mods {
		if (categories.isNullOrEmpty()) return Mods.none()
		var ring = false
		var hecatomb = 0
		var scarf = 0.0
		for (stacks in categories.values) {
			if (stacks == null) continue
			for (stack in stacks) {
				if (stack == null) continue
				val id = stack.id.uppercase(Locale.ROOT)
				if (id == "CATACOMBS_EXPERT_RING") ring = true
				SCARF_BONUS[id]?.let { scarf = max(scarf, it) }
				hecatomb = max(hecatomb, hecatombOn(stack))
			}
		}
		return Mods(ring, hecatomb, scarf)
	}

	@JvmStatic
	fun readCatacombsGraduateBonus(member: JsonObject?): Double {
		if (member == null) return 0.20
		val level = attributeLevel(member, "catacombs_graduate") ?: return 0.20
		if (level <= 0) return 0.0
		return min(0.20, level * 0.02)
	}

	private fun attributeLevel(member: JsonObject, key: String): Float? {
		obj(member.get("attributes"))?.let { attrs -> num(attrs.get(key))?.let { return it } }
		val playerData = obj(member.get("player_data")) ?: return null
		return obj(playerData.get("attributes"))?.let { num(it.get(key)) }
	}

	@JvmStatic
	fun readEssenceClassBonuses(member: JsonObject?): MutableMap<String, Double> {
		val out = HashMap<String, Double>()
		val dungeons = obj(member?.get("dungeons")) ?: return out
		readPerkMap(out, obj(dungeons.get("perks")))
		obj(member?.get("player_data"))?.let { readPerkMap(out, obj(it.get("perks"))) }
		val classes = obj(dungeons.get("player_classes"))
		if (classes != null) {
			for (classId in CLASS_IDS) {
				val clazz = obj(classes.get(classId)) ?: continue
				val fromClass = essenceFromClassObject(clazz)
				if (fromClass != null && fromClass > 0) out.merge(classId, fromClass, ::maxOf)
			}
		}
		return out
	}

	private fun readPerkMap(out: MutableMap<String, Double>, perks: JsonObject?) {
		if (perks == null) return
		putPerk(out, perks, "mage", "cold_efficiency", "mage_xp", "class_xp_mage")
		putPerk(out, perks, "berserk", "unbridled_rage", "berserk_xp", "class_xp_berserk")
		putPerk(out, perks, "archer", "toxophilite", "archer_xp", "class_xp_archer")
		putPerk(out, perks, "healer", "heart_of_gold", "healer_xp", "class_xp_healer")
		putPerk(out, perks, "tank", "diamond_in_the_rough", "tank_xp", "class_xp_tank")
	}

	private fun putPerk(out: MutableMap<String, Double>, perks: JsonObject, classId: String, vararg keys: String) {
		for (key in keys) {
			if (!perks.has(key)) continue
			val level = num(perks.get(key))
			if (level == null || level <= 0) continue
			out.merge(classId, min(0.10, level * 0.02), ::maxOf)
		}
	}

	private fun essenceFromClassObject(clazz: JsonObject): Double? {
		for (key in CLASS_BOOST_KEYS) {
			if (!clazz.has(key)) continue
			val level = num(clazz.get(key))
			if (level != null && level > 0) return min(0.10, level * 0.02)
		}
		return null
	}

	private fun obj(el: JsonElement?): JsonObject? = if (el != null && el.isJsonObject) el.asJsonObject else null

	private fun num(el: JsonElement?): Float? {
		if (el == null || el.isJsonNull || !el.isJsonPrimitive) return null
		return try {
			el.asFloat
		} catch (_: Exception) {
			null
		}
	}

	private fun hecatombOn(stack: InventoryDecoder.Stack): Int {
		val ea = stack.extraAttributes
		if (!ea.contains("enchantments")) return 0
		val map = ea.get("enchantments") as? CompoundTag ?: return 0
		for (key in map.keySet()) {
			if (key != null && key.equals("hecatomb", ignoreCase = true)) return clampHec(intValue(map, key))
		}
		return 0
	}

	private fun clampHec(level: Int): Int = max(0, min(10, level))

	private fun intValue(tag: CompoundTag, key: String): Int = try {
		tag.getIntOr(key, 0)
	} catch (_: Throwable) {
		(tag.get(key) as? NumericTag)?.intValue() ?: 0
	}
}

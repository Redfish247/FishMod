/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.PetWorth
import fishmod.pv.neu.NeuRepoCache

class PetSnapshot(
    pets: List<Entry>?,
    highestPetScore: Int,
    sacrificedTypes: List<String>?,
    autopetRuleCount: Int,
    autopetRulesLimit: Int,
    autopetRules: List<AutopetRule>?,
) {
    class Entry(
        type: String?,
        displayName: String?,
        tier: String?,
        @get:JvmName("tierIndex") val tierIndex: Int,
        neuId: String?,
        level: Int,
        maxLevel: Int,
        @get:JvmName("exp") val exp: Double,
        @get:JvmName("xpMax") val xpMax: Double,
        xpIntoLevel: Double,
        xpToNext: Double,
        progressToNext: Float,
        @get:JvmName("active") val active: Boolean,
        heldItem: String?,
        candyUsed: Int,
        skin: String?,
        uuid: String?,
        networth: Double,
    ) {
        @get:JvmName("type") val type: String = type ?: ""
        @get:JvmName("displayName") val displayName: String = if (displayName.isNullOrBlank()) this.type else displayName
        @get:JvmName("tier") val tier: String = if (tier.isNullOrBlank()) "COMMON" else tier
        @get:JvmName("neuId") val neuId: String = neuId ?: ""
        @get:JvmName("heldItem") val heldItem: String = heldItem ?: ""
        @get:JvmName("skin") val skin: String = skin ?: ""
        @get:JvmName("uuid") val uuid: String = uuid ?: ""
        @get:JvmName("level") val level: Int = maxOf(1, level)
        @get:JvmName("maxLevel") val maxLevel: Int = maxOf(this.level, maxLevel)
        @get:JvmName("candyUsed") val candyUsed: Int = maxOf(0, candyUsed)
        @get:JvmName("xpIntoLevel") val xpIntoLevel: Double = maxOf(0.0, xpIntoLevel)
        @get:JvmName("xpToNext") val xpToNext: Double = maxOf(0.0, xpToNext)
        @get:JvmName("progressToNext") val progressToNext: Float = progressToNext.coerceIn(0f, 1f)
        @get:JvmName("networth") val networth: Double = maxOf(0.0, networth)

        fun hasHeldItem(): Boolean = heldItem.isNotBlank()

        fun hasSkin(): Boolean = skin.isNotBlank()
    }

    class AutopetRule(id: String?, petName: String?, trigger: String?, detail: String?, disabled: Boolean) {
        @get:JvmName("id") val id: String = id ?: ""
        @get:JvmName("petName") val petName: String = petName ?: ""
        @get:JvmName("trigger") val trigger: String = trigger ?: ""
        @get:JvmName("detail") val detail: String = detail ?: ""
        @get:JvmName("disabled") val disabled: Boolean = disabled
    }

    @get:JvmName("pets") val pets: List<Entry> = pets?.toList() ?: emptyList()
    @get:JvmName("highestPetScore") val highestPetScore: Int = maxOf(0, highestPetScore)
    @get:JvmName("sacrificedTypes") val sacrificedTypes: List<String> = sacrificedTypes?.toList() ?: emptyList()
    @get:JvmName("autopetRuleCount") val autopetRuleCount: Int = maxOf(0, autopetRuleCount)
    @get:JvmName("autopetRulesLimit") val autopetRulesLimit: Int = maxOf(0, autopetRulesLimit)
    @get:JvmName("autopetRules") val autopetRules: List<AutopetRule> = autopetRules?.toList() ?: emptyList()

    constructor(pets: List<Entry>?) : this(pets, 0, emptyList(), 0, 0, emptyList())

    fun isEmpty(): Boolean = pets.isEmpty()

    companion object {
        @JvmStatic
        fun empty(): PetSnapshot = PetSnapshot(emptyList(), 0, emptyList(), 0, 0, emptyList())

        @JvmStatic
        fun fromMember(member: JsonObject?): PetSnapshot {
            if (member == null) return empty()
            val petsData = if (member.has("pets_data") && member.get("pets_data").isJsonObject) member.getAsJsonObject("pets_data") else null
            val pets: JsonArray? = when {
                petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray -> petsData.getAsJsonArray("pets")
                member.has("pets") && member.get("pets").isJsonArray -> member.getAsJsonArray("pets")
                else -> null
            }

            val entries = ArrayList<Entry>()
            if (pets != null) {
                for (element in pets) {
                    if (element == null || !element.isJsonObject) continue
                    fromPet(element.asJsonObject)?.let { entries.add(it) }
                }
                entries.sortWith(compareByDescending<Entry> { it.exp }.thenBy { it.displayName.lowercase() })
            }

            val leveling = Leveling.obj(member.get("leveling"))
            val highestScore = intOf(leveling, "highest_pet_score")

            val sacrificed = ArrayList<String>()
            val petCare = Leveling.obj(petsData?.get("pet_care"))
            val sacrificedEl = petCare?.get("pet_types_sacrificed")
            if (sacrificedEl != null && sacrificedEl.isJsonArray) {
                for (el in sacrificedEl.asJsonArray) {
                    if (el == null || !el.isJsonPrimitive) continue
                    try {
                        val type: String? = el.asString
                        if (!type.isNullOrBlank()) sacrificed.add(InventoryDecoder.prettyWords(type))
                    } catch (_: IllegalStateException) {
                    } catch (_: ClassCastException) {
                    } catch (_: NumberFormatException) {
                    } catch (_: UnsupportedOperationException) {
                    }
                }
            }

            val autopet = Leveling.obj(petsData?.get("autopet"))
            val rulesLimit = intOf(autopet, "rules_limit")
            val rules = ArrayList<AutopetRule>()
            val rulesEl = autopet?.get("rules")
            if (rulesEl != null && rulesEl.isJsonArray) {
                for (el in rulesEl.asJsonArray) {
                    if (el == null || !el.isJsonObject) continue
                    val rule = el.asJsonObject
                    var name: String? = ""
                    if (rule.has("name") && rule.get("name").isJsonPrimitive) {
                        try {
                            name = rule.get("name").asString
                        } catch (_: IllegalStateException) {
                        } catch (_: ClassCastException) {
                        } catch (_: NumberFormatException) {
                        } catch (_: UnsupportedOperationException) {
                        }
                    }
                    val id = str(rule, "id")
                    val disabled = rule.has("disabled") && rule.get("disabled").isJsonPrimitive && rule.get("disabled").asBoolean
                    val data = Leveling.obj(rule.get("data"))
                    val category = if (data == null) "" else str(data, "category")
                    val boss = if (data == null) "" else str(data, "boss")
                    val detail = when {
                        category.isNotBlank() && boss.isNotBlank() ->
                            InventoryDecoder.prettyWords(category) + " · " + InventoryDecoder.prettyWords(boss)
                        boss.isNotBlank() -> InventoryDecoder.prettyWords(boss)
                        category.isNotBlank() -> InventoryDecoder.prettyWords(category)
                        else -> ""
                    }
                    val trigger = prettyAutopetTrigger(id)
                    val petName = stripSectionCodes(name)
                    if (petName.isBlank() && trigger.isBlank()) continue
                    rules.add(AutopetRule(id, petName, trigger, detail, disabled))
                }
            }

            return PetSnapshot(entries, highestScore, sacrificed, rules.size, rulesLimit, rules)
        }

        private fun str(obj: JsonObject?, key: String?): String {
            if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) return ""
            return try {
                obj.get(key).asString ?: ""
            } catch (_: IllegalStateException) {
                ""
            } catch (_: ClassCastException) {
                ""
            } catch (_: NumberFormatException) {
                ""
            } catch (_: UnsupportedOperationException) {
                ""
            }
        }

        private fun prettyAutopetTrigger(id: String?): String {
            if (id.isNullOrBlank()) return "Rule"
            return when (id.uppercase()) {
                "BOSS_SPAWN" -> "Boss spawn"
                "START_SLAYER_QUEST" -> "Start slayer quest"
                "ENTER_DUNGEON" -> "Enter dungeon"
                "ENTER_LOCATION" -> "Enter location"
                else -> InventoryDecoder.prettyWords(id.lowercase())
            }
        }

        private fun fromPet(pet: JsonObject?): Entry? {
            if (pet == null || !pet.has("type") || !pet.get("type").isJsonPrimitive) return null
            val type = pet.get("type").asString
            val tier = if (pet.has("tier") && pet.get("tier").isJsonPrimitive) pet.get("tier").asString else "COMMON"
            val tierIndex = petTierIndex(tier)
            val neuId = neuIdFor(type, tierIndex)
            val info = PetWorth.levelInfo(pet)
            val active = pet.has("active") && pet.get("active").isJsonPrimitive && pet.get("active").asBoolean
            val heldItem = if (pet.has("heldItem") && pet.get("heldItem").isJsonPrimitive) pet.get("heldItem").asString else ""
            val candyUsed = intOf(pet, "candyUsed")
            val skin = if (pet.has("skin") && pet.get("skin").isJsonPrimitive) pet.get("skin").asString else ""
            val uuid = when {
                pet.has("uniqueId") && pet.get("uniqueId").isJsonPrimitive -> pet.get("uniqueId").asString
                pet.has("uuid") && pet.get("uuid").isJsonPrimitive -> pet.get("uuid").asString
                else -> ""
            }
            val nw = PetWorth.value(pet)
            return Entry(
                type,
                InventoryDecoder.prettyWords(type),
                tier,
                tierIndex,
                neuId,
                info.level,
                info.maxLevel,
                info.exp,
                info.xpMax,
                info.xpIntoLevel,
                info.xpToNext,
                info.progressToNext,
                active,
                heldItem,
                candyUsed,
                skin,
                uuid,
                nw,
            )
        }

        private fun petTierIndex(tier: String?): Int = when (tier?.uppercase()) {
            null -> 0
            "COMMON" -> 0
            "UNCOMMON" -> 1
            "RARE" -> 2
            "EPIC" -> 3
            "LEGENDARY" -> 4
            "MYTHIC" -> 5
            "DIVINE" -> 6
            "SPECIAL" -> 7
            "VERY_SPECIAL" -> 8
            else -> 4
        }

        @JvmStatic
        fun neuIdFor(type: String, tierIndex: Int): String {
            val base = type.uppercase()
            val exact = "$base;$tierIndex"
            if (!NeuRepoCache.isReady() || NeuRepoCache.get(exact) != null) return exact
            for (d in 1..8) {
                for (idx in intArrayOf(tierIndex + d, tierIndex - d)) {
                    if (idx in 0..8 && NeuRepoCache.get("$base;$idx") != null) return "$base;$idx"
                }
            }
            return exact
        }

        private fun intOf(obj: JsonObject?, key: String?): Int {
            if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) return 0
            return try {
                maxOf(0, obj.get(key).asDouble.toInt())
            } catch (_: IllegalStateException) {
                0
            } catch (_: ClassCastException) {
                0
            } catch (_: NumberFormatException) {
                0
            } catch (_: UnsupportedOperationException) {
                0
            }
        }

        private fun stripSectionCodes(text: String?): String {
            if (text.isNullOrBlank()) return ""
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '§' && i + 1 < text.length) {
                    i += 2
                    continue
                }
                out.append(c)
                i++
            }
            return out.toString().trim()
        }
    }
}

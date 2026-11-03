/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot
import java.util.Locale

/** Named loadout / pet preset decoding. */
internal object LoadoutDecoder {
    const val LOADOUT_SLOT_COUNT = 27

    private val TUNING_STATS = listOf(
        "health" to "HP",
        "defense" to "Def",
        "walk_speed" to "Spd",
        "strength" to "Str",
        "critical_damage" to "CD",
        "critical_chance" to "CC",
        "attack_speed" to "AS",
        "intelligence" to "Int",
    )

    private fun primString(obj: JsonObject, key: String): String? {
        val value = obj.get(key)
        return if (value != null && value.isJsonPrimitive) value.asString else null
    }

    /**
     * Named presets under `loadout.loadouts`, resolving armor/equipment set ids and pet uniqueId.
     * Equipped wardrobe sets store pieces in inv_armor / equipment_contents (layout entry is empty).
     */
    @JvmStatic
    fun parseNamedLoadouts(
        member: JsonObject,
        loadout: JsonObject?,
        accessoryInfo: InventorySnapshot.AccessoryInfo?,
    ): List<InventorySnapshot.Loadout> {
        val byIndex = LinkedHashMap<Int, InventorySnapshot.Loadout>()
        if (loadout == null) return padLoadouts(byIndex)
        val inventory = InventoryDecodeSupport.obj(member.get("inventory"))
            ?: InventoryDecodeSupport.obj(member.get("inventories"))
        val named = InventoryDecodeSupport.obj(loadout.get("loadouts"))
        val armorLayouts = InventoryDecodeSupport.obj(loadout.get("armor"))
        val equipLayouts = InventoryDecodeSupport.obj(loadout.get("equipment"))
        val petsByUuid = indexPets(member)

        if (named != null) {
            for (key in named.keySet()) {
                val entry = InventoryDecodeSupport.obj(named.get(key)) ?: continue
                val index = InventoryDecodeSupport.tryParseInt(key) ?: byIndex.size
                val name = primString(entry, "name") ?: "Loadout $index"
                val armorId = InventoryDecodeSupport.jsonInt(entry, "armor_set_id")
                val equipId = InventoryDecodeSupport.jsonInt(entry, "equipment_set_id")
                val armor = WardrobeDecoder.readArmorSet(inventory, armorLayouts, armorId, null)
                val equip = WardrobeDecoder.readEquipSet(inventory, equipLayouts, equipId, null)
                val power = primString(entry, "power_stone") ?: ""
                val tuneSlot = InventoryDecodeSupport.jsonInt(entry, "tuning_points_slot")
                val tuning = resolveTuning(accessoryInfo, tuneSlot)
                val petUuid = primString(entry, "pet") ?: ""
                val petJson = if (petUuid.isBlank()) null else petsByUuid[InventoryDecodeSupport.normalizeUuid(petUuid)]
                byIndex[index] = InventorySnapshot.Loadout(
                    name, equip, armor, power, tuneSlot, tuning, petSlot(petJson), petLabel(petJson),
                    presetSlot(entry, "mining_core_selected_slot"),
                    presetSlot(entry, "foraging_core_selected_slot"),
                )
            }
        }

        // Fallback: wardrobe set keys if named presets are empty.
        if (byIndex.isEmpty()) {
            val keys = LinkedHashSet<String>()
            armorLayouts?.keySet()?.filterTo(keys) { it != "equipped_set" }
            equipLayouts?.keySet()?.filterTo(keys) { it != "equipped_set" }
            for (key in keys) {
                val id = InventoryDecodeSupport.tryParseInt(key) ?: continue
                byIndex[id] = InventorySnapshot.Loadout(
                    "Loadout $id",
                    WardrobeDecoder.readEquipSet(inventory, equipLayouts, id, key),
                    WardrobeDecoder.readArmorSet(inventory, armorLayouts, id, key),
                    "",
                    null,
                    emptyList(),
                    null,
                    "",
                    null,
                    null,
                )
            }
        }
        return padLoadouts(byIndex)
    }

    /** Loadouts with gear, pet, power, tuning or a tree preset, in key order (API keys are 1-based). */
    @JvmStatic
    fun padLoadouts(byIndex: Map<Int, InventorySnapshot.Loadout?>?): List<InventorySnapshot.Loadout> {
        val out = ArrayList<InventorySnapshot.Loadout>()
        if (byIndex.isNullOrEmpty()) return out
        for (key in byIndex.keys.sorted()) {
            val loadout = byIndex[key]
            if (loadout != null && loadoutHasContent(loadout) && out.size < LOADOUT_SLOT_COUNT) out.add(loadout)
        }
        return out
    }

    @JvmStatic
    fun presetSlot(entry: JsonObject?, key: String): Int? =
        InventoryDecodeSupport.jsonInt(entry, key)?.takeIf { it in 1..5 }

    @JvmStatic
    fun loadoutHasContent(loadout: InventorySnapshot.Loadout?): Boolean {
        if (loadout == null) return false
        if (loadout.powerStone.isNotBlank() || loadout.petLabel.isNotBlank()) return true
        if (loadout.hotmPreset != null || loadout.hotfPreset != null) return true
        val pet = loadout.pet
        if (pet != null && !pet.isEmpty()) return true
        if (!loadout.tuning.isNullOrEmpty()) return true
        if (loadout.armor.any { it != null && !it.isEmpty() }) return true
        return loadout.equipment.any { it != null && !it.isEmpty() }
    }

    @JvmStatic
    fun emptyLoadout(index: Int): InventorySnapshot.Loadout = InventorySnapshot.Loadout(
        "Loadout ${index + 1}",
        MutableList(4) { null },
        MutableList(4) { null },
        "",
        null,
        emptyList(),
        null,
        "",
        null,
        null,
    )

    @JvmStatic
    fun resolveTuning(info: InventorySnapshot.AccessoryInfo?, slot: Int?): List<InventorySnapshot.StatPoint> {
        if (info == null || slot == null) return emptyList()
        return info.tunings.firstOrNull { it.slot == slot }?.stats ?: emptyList()
    }

    @JvmStatic
    fun readTuningStats(slot: JsonObject?): List<InventorySnapshot.StatPoint> {
        if (slot == null) return emptyList()
        return TUNING_STATS.mapNotNull { (key, label) ->
            InventoryDecodeSupport.jsonInt(slot, key)?.takeIf { it != 0 }?.let { InventorySnapshot.StatPoint(key, label, it) }
        }
    }

    @JvmStatic
    fun indexPets(member: JsonObject): Map<String, JsonObject> {
        val map = LinkedHashMap<String, JsonObject>()
        val petsData = InventoryDecodeSupport.obj(member.get("pets_data"))
        val pets: JsonArray = petsData?.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: member.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return map
        for (element in pets) {
            val pet = InventoryDecodeSupport.obj(element) ?: continue
            // Loadouts reference uniqueId; some payloads also expose uuid.
            primString(pet, "uniqueId")?.let { map[InventoryDecodeSupport.normalizeUuid(it)] = pet }
            primString(pet, "uuid")?.let { map[InventoryDecodeSupport.normalizeUuid(it)] = pet }
        }
        return map
    }

    @JvmStatic
    fun petSlot(pet: JsonObject?): InventorySnapshot.Slot? {
        val type = pet?.let { primString(it, "type") } ?: return null
        val tier = primString(pet, "tier") ?: "COMMON"
        val lore = ArrayList<String>()
        if (tier.isNotBlank()) lore.add(InventoryDecodeSupport.prettyWords(tier))
        primString(pet, "heldItem")?.let(lore::add)
        // NEU pet items are TYPE;tierIndex (COMMON=0 … MYTHIC=5).
        val neuId = type.uppercase(Locale.ROOT) + ";" + petTierIndex(tier)
        return InventorySnapshot.Slot(neuId, 1, lore, petLabel(pet), null, null, null)
    }

    @JvmStatic
    fun petLabel(pet: JsonObject?): String {
        val type = pet?.let { primString(it, "type") } ?: return ""
        return InventoryDecodeSupport.prettyWords(type)
    }

    @JvmStatic
    fun petTierIndex(tier: String?): Int = when (tier?.uppercase(Locale.ROOT)) {
        null, "COMMON" -> 0
        "UNCOMMON" -> 1
        "RARE" -> 2
        "EPIC" -> 3
        "LEGENDARY" -> 4
        "MYTHIC" -> 5
        else -> 4
    }
}

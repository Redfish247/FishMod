/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot

/** Wardrobe / equipment-set page decoding. */
internal object WardrobeDecoder {
    /** Rank max (18) + Community Center wardrobe/loadout upgrades (9). */
    const val WARDROBE_SLOT_COUNT = 27

    /** Hypixel wardrobe UI shows 9 set columns per page. */
    const val WARDROBE_SETS_PER_PAGE = 9

    private val ARMOR_SLOTS = listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
    private val EQUIP_SLOTS = listOf("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")

    data class WardrobeSet(
        @get:JvmName("slots") val slots: List<InventorySnapshot.Slot?>,
        @get:JvmName("equipped") val equipped: Boolean,
    )

    @JvmStatic
    fun parseWardrobePages(inventory: JsonObject?, loadout: JsonObject?): List<InventorySnapshot.Page> {
        val byId = LinkedHashMap<Int, WardrobeSet>()
        val armorLayouts = InventoryDecodeSupport.obj(loadout?.get("armor"))
        val equippedId = armorLayouts?.let { InventoryDecodeSupport.jsonInt(it, "equipped_set") }
        if (armorLayouts != null) {
            for (key in armorLayouts.keySet()) {
                if (key == "equipped_set") continue
                val setId = layoutSetId(armorLayouts.get(key), key) ?: continue
                byId[setId] = WardrobeSet(readArmorSet(inventory, armorLayouts, setId, key), equippedId == setId)
            }
        }
        if (byId.isEmpty()) {
            val flat = InventoryDecodeSupport.toUiSlots(
                InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "wardrobe_contents", 72)
            )
            var i = 0
            var setId = 0
            while (i + 3 < flat.size) {
                byId[setId] = WardrobeSet(ArrayList(flat.subList(i, i + 4)), false)
                i += 4
                setId++
            }
        }
        return packWardrobeSetPages(padWardrobeSets(byId), "Wardrobe")
    }

    @JvmStatic
    fun armorPresets(inventory: JsonObject?, loadout: JsonObject?): List<InventoryDecoder.WardrobePreset> {
        val armorLayouts = InventoryDecodeSupport.obj(loadout?.get("armor")) ?: return emptyList()
        val equippedId = InventoryDecodeSupport.jsonInt(armorLayouts, "equipped_set")
        val out = ArrayList<InventoryDecoder.WardrobePreset>()
        for (key in armorLayouts.keySet()) {
            if (key == "equipped_set") continue
            val setId = layoutSetId(armorLayouts.get(key), key) ?: continue
            val page = findLayout(armorLayouts, setId, key)
            var pieces: List<InventoryDecoder.Stack?> = ARMOR_SLOTS.map { slot ->
                page?.let { InventoryDecodeCache.decodeDataElement(it.get(slot)).firstOrNull() }
            }
            val equipped = equippedId == setId
            if (equipped && pieces.all { it == null }) {
                val live = InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "inv_armor", 4)
                pieces = (3 downTo 0).map { live.getOrNull(it) }
            }
            out.add(InventoryDecoder.WardrobePreset(setId, equipped, pieces))
        }
        out.sortBy { it.slot }
        return out
    }

    @JvmStatic
    fun parseEquipmentWardrobePages(inventory: JsonObject?, loadout: JsonObject?): List<InventorySnapshot.Page> {
        val byId = LinkedHashMap<Int, WardrobeSet>()
        val equipLayouts = InventoryDecodeSupport.obj(loadout?.get("equipment"))
        val equippedId = equipLayouts?.let { InventoryDecodeSupport.jsonInt(it, "equipped_set") }
        if (equipLayouts != null) {
            for (key in equipLayouts.keySet()) {
                if (key == "equipped_set") continue
                val setId = layoutSetId(equipLayouts.get(key), key) ?: continue
                byId[setId] = WardrobeSet(readEquipSet(inventory, equipLayouts, setId, key), equippedId == setId)
            }
        }
        if (byId.isEmpty()) {
            // Hypixel equipped_set is 1-based when loadout layouts exist; live-only fallback treats missing id as slot 1.
            val idx = if (equippedId == null) 1 else equippedId.coerceIn(1, WARDROBE_SLOT_COUNT)
            byId[idx] = WardrobeSet(liveEquipment(inventory), true)
        }
        return packWardrobeSetPages(padWardrobeSets(byId), "Equipment")
    }

    /** Armor/equipment sets per page, each set a vertical column. */
    @JvmStatic
    fun packWardrobeSetPages(sets: List<WardrobeSet>, titlePrefix: String): List<InventorySnapshot.Page> {
        val all = sets.ifEmpty { padWardrobeSets(emptyMap()) }
        val perPage = WARDROBE_SETS_PER_PAGE
        val totalPages = (all.size + perPage - 1) / perPage
        val pages = ArrayList<InventorySnapshot.Page>()
        for (start in all.indices step perPage) {
            val count = minOf(all.size, start + perPage) - start
            val slots = ArrayList<InventorySnapshot.Slot?>(4 * count)
            var equippedColumn = -1
            // Row-major by piece so columns=count draws piece rows with sets as columns.
            for (piece in 0 until 4) {
                for (col in 0 until count) {
                    val set = all[start + col]
                    slots.add(set.slots.getOrNull(piece))
                    if (piece == 0 && set.equipped) equippedColumn = col
                }
            }
            val pageIndex = start / perPage + 1
            val title = if (totalPages <= 1) titlePrefix else "$titlePrefix $pageIndex"
            pages.add(InventorySnapshot.Page(title, slots, count, equippedColumn))
        }
        return pages
    }

    /**
     * Fill every wardrobe slot (including empty / locked) so page totals stay fixed.
     * Loadout set ids are 1-based (1..27); legacy flat `wardrobe_contents` uses 0-based ids.
     */
    @JvmStatic
    fun padWardrobeSets(byId: Map<Int, WardrobeSet>?): List<WardrobeSet> {
        val first = if (byId != null && byId.containsKey(0)) 0 else 1
        return (first until first + WARDROBE_SLOT_COUNT).map { byId?.get(it) ?: emptyWardrobeSet() }
    }

    private fun emptyWardrobeSet() = WardrobeSet(MutableList(4) { null }, false)

    @JvmStatic
    fun layoutSetId(element: JsonElement?, key: String?): Int? {
        val id = InventoryDecodeSupport.obj(element)?.get("id")
        if (id != null && id.isJsonPrimitive) {
            try {
                return id.asInt
            } catch (ignored: Exception) {
            }
        }
        return InventoryDecodeSupport.tryParseInt(key)
    }

    private fun readLayoutSlots(layouts: JsonObject?, id: Int?, keyHint: String?, slotNames: List<String>): List<InventorySnapshot.Slot?> {
        val page = findLayout(layouts, id, keyHint)
        return slotNames.mapTo(ArrayList(4)) { slot ->
            page?.let { InventoryDecodeCache.decodeDataElement(it.get(slot)).firstOrNull() }
                ?.let(InventoryDecodeSupport::toUiSlot)
        }
    }

    private fun isEquipped(layouts: JsonObject?, id: Int?): Boolean {
        val equippedId = layouts?.let { InventoryDecodeSupport.jsonInt(it, "equipped_set") }
        return id != null && equippedId != null && id == equippedId
    }

    @JvmStatic
    fun readArmorSet(inventory: JsonObject?, layouts: JsonObject?, id: Int?, keyHint: String?): List<InventorySnapshot.Slot?> {
        val slots = readLayoutSlots(layouts, id, keyHint, ARMOR_SLOTS)
        if (isEquipped(layouts, id) && slots.all { it == null || it.isEmpty() }) return liveArmorHelmetToBoots(inventory)
        return slots
    }

    @JvmStatic
    fun readEquipSet(inventory: JsonObject?, layouts: JsonObject?, id: Int?, keyHint: String?): List<InventorySnapshot.Slot?> {
        val slots = readLayoutSlots(layouts, id, keyHint, EQUIP_SLOTS)
        if (isEquipped(layouts, id) && slots.all { it == null || it.isEmpty() }) return liveEquipment(inventory)
        return slots
    }

    private fun liveEquipment(inventory: JsonObject?): MutableList<InventorySnapshot.Slot?> {
        val live = InventoryDecodeSupport.toUiSlots(
            InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "equipment_contents", 4)
        )
        while (live.size < 4) live.add(null)
        return ArrayList(live.subList(0, 4))
    }

    /** inv_armor is boots→helmet; loadout/wardrobe columns are helmet→boots. */
    @JvmStatic
    fun liveArmorHelmetToBoots(inventory: JsonObject?): List<InventorySnapshot.Slot?> {
        val raw = InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "inv_armor", 4))
        while (raw.size < 4) raw.add(null)
        return arrayListOf(raw[3], raw[2], raw[1], raw[0])
    }

    @JvmStatic
    fun findLayout(layouts: JsonObject?, id: Int?, keyHint: String?): JsonObject? {
        if (layouts == null) return null
        if (keyHint != null && layouts.has(keyHint)) return InventoryDecodeSupport.obj(layouts.get(keyHint))
        if (id != null) {
            if (layouts.has(id.toString())) return InventoryDecodeSupport.obj(layouts.get(id.toString()))
            for ((_, value) in layouts.entrySet()) {
                val page = InventoryDecodeSupport.obj(value)
                if (page != null && id == InventoryDecodeSupport.jsonInt(page, "id")) return page
            }
        }
        return null
    }
}

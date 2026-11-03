/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.MagicalPowerCalculator
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.Locale
import java.util.function.Supplier
import net.minecraft.nbt.CompoundTag

/** Decodes Hypixel inventory base64 → SkyBlock item stacks for networth. */
object InventoryDecoder {
    data class Stack @JvmOverloads constructor(
        @get:JvmName("id") val id: String,
        @get:JvmName("count") val count: Int,
        @get:JvmName("extraAttributes") val extraAttributes: CompoundTag,
        @get:JvmName("lore") val lore: List<String>,
        @get:JvmName("soulbound") val soulbound: Boolean,
        @get:JvmName("displayName") val displayName: String? = null,
        @get:JvmName("dyeColor") val dyeColor: Int? = null,
        @get:JvmName("skullValue") val skullValue: String? = null,
        @get:JvmName("skullSignature") val skullSignature: String? = null,
    )

    /** One loadout wardrobe set; `pieces` is helmet to boots with nulls for empty slots. */
    data class WardrobePreset(
        @get:JvmName("slot") val slot: Int,
        @get:JvmName("equipped") val equipped: Boolean,
        @get:JvmName("pieces") val pieces: List<Stack?>,
    )

    data class RiftInventories(
        @get:JvmName("inventory") val inventory: InventorySnapshot.Page,
        @get:JvmName("equipment") val equipment: List<InventorySnapshot.Slot?>,
        @get:JvmName("armor") val armor: List<InventorySnapshot.Slot?>,
        @get:JvmName("enderPages") val enderPages: List<InventorySnapshot.Page>,
    )

    /** One toolkit slot. `inUse` means the tool was taken out, so the copy in the inventory is the live one. */
    data class ToolkitItem(
        @get:JvmName("category") val category: String,
        @get:JvmName("index") val index: Int,
        @get:JvmName("inUse") val inUse: Boolean,
        @get:JvmName("stack") val stack: Stack?,
    )

    private val ARMOR_SLOTS = listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
    private val EQUIP_SLOTS = listOf("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")
    private val TIME_POCKET_KEYS = listOf("time_pocket", "time_bag", "timed_items", "timepocket", "time_pocket_contents")

    /** Runs work with a thread-local NBT decode cache; nested calls reuse it, decoded lists are immutable. */
    @JvmStatic
    fun <T> withSharedDecode(work: Supplier<T>): T = InventoryDecodeCache.withSharedDecode(work)

    @JvmStatic
    fun withSharedDecode(work: Runnable) = InventoryDecodeCache.withSharedDecode(work)

    private fun inventoryObject(member: JsonObject): JsonObject? =
        InventoryDecodeSupport.obj(member.get("inventory")) ?: InventoryDecodeSupport.obj(member.get("inventories"))

    private fun bagField(bags: JsonObject?, field: String): List<Stack> =
        if (bags == null) emptyList() else InventoryDecodeCache.decodeField(bags, field)

    /** Home-first-paint gear only: armor, equipment, accessories (no inventory / backpack / wardrobe NBT). */
    @JvmStatic
    fun parseHomeGear(member: JsonObject?): MutableMap<String, List<Stack>> {
        val categories = LinkedHashMap<String, List<Stack>>()
        if (member == null) return categories
        val inventory = inventoryObject(member)
        val bags = InventoryDecodeSupport.obj(inventory?.get("bag_contents"))
        categories["armor"] = InventoryDecodeCache.decodeField(inventory, "inv_armor")
        categories["equipment"] = InventoryDecodeCache.decodeField(inventory, "equipment_contents")
        categories["accessories"] = bagField(bags, "talisman_bag")
        return categories
    }

    /** Accessory bag plus armor accessories that still grant Magical Power (non-bag ones need an ACCESSORY lore line). */
    @JvmStatic
    fun accessoryCandidates(member: JsonObject?): MutableList<Stack> {
        val out = ArrayList<Stack>()
        if (member == null) return out
        val home = parseHomeGear(member)
        out.addAll(home["accessories"].orEmpty())
        home["armor"].orEmpty().filterTo(out, ::looksLikeAccessory)
        // Inventory accessories don't count: Hypixel's Magical Power only reads the bag.
        return out
    }

    private fun looksLikeAccessory(stack: Stack?): Boolean {
        if (stack == null || stack.id.isBlank()) return false
        val last = stack.lore.lastOrNull() ?: return false
        return last.contains("ACCESSORY") || last.contains("HATCESSORY")
    }

    @JvmStatic
    fun parseCategories(member: JsonObject, museumMember: JsonObject?): MutableMap<String, List<Stack>> {
        val categories = LinkedHashMap<String, List<Stack>>()
        val inventory = inventoryObject(member)
        val shared = InventoryDecodeSupport.obj(member.get("shared_inventory"))
        val bags = InventoryDecodeSupport.obj(inventory?.get("bag_contents"))

        categories["armor"] = InventoryDecodeCache.decodeField(inventory, "inv_armor")
        categories["equipment"] = InventoryDecodeCache.decodeField(inventory, "equipment_contents")
        categories["inventory"] = InventoryDecodeCache.decodeField(inventory, "inv_contents")
        categories["enderchest"] = InventoryDecodeCache.decodeField(inventory, "ender_chest_contents")
        categories["accessories"] = bagField(bags, "talisman_bag")
        categories["personal_vault"] = InventoryDecodeCache.decodeField(inventory, "personal_vault_contents")
        categories["fishing_bag"] = bagField(bags, "fishing_bag")
        categories["potion_bag"] = bagField(bags, "potion_bag")
        categories["sacks_bag"] = bagField(bags, "sacks_bag")
        categories["quiver"] = bagField(bags, "quiver")
        categories["candy_inventory"] = bagField(shared, "candy_inventory_contents")
        categories["carnival_mask_inventory"] = bagField(shared, "carnival_mask_inventory_contents")

        val storage = ArrayList<Stack>()
        for (field in listOf("backpack_contents", "backpack_icons")) {
            InventoryDecodeSupport.obj(inventory?.get(field))?.entrySet()?.forEach { (_, value) ->
                storage.addAll(InventoryDecodeCache.decodeDataElement(value))
            }
        }
        categories["storage"] = storage

        val wardrobe = ArrayList<Stack>()
        val loadout = InventoryDecodeSupport.obj(member.get("loadout"))
        InventoryDecodeSupport.obj(loadout?.get("armor"))?.entrySet()?.forEach { (_, layout) ->
            val page = InventoryDecodeSupport.obj(layout) ?: return@forEach
            for (slot in ARMOR_SLOTS) wardrobe.addAll(InventoryDecodeCache.decodeDataElement(page.get(slot)))
        }
        // Legacy wardrobe blob
        if (wardrobe.isEmpty()) wardrobe.addAll(InventoryDecodeCache.decodeField(inventory, "wardrobe_contents"))
        categories["wardrobe"] = wardrobe

        val equipmentLayouts = ArrayList(categories["equipment"].orEmpty())
        InventoryDecodeSupport.obj(loadout?.get("equipment"))?.entrySet()?.forEach { (_, layout) ->
            val page = InventoryDecodeSupport.obj(layout) ?: return@forEach
            for (slot in EQUIP_SLOTS) equipmentLayouts.addAll(InventoryDecodeCache.decodeDataElement(page.get(slot)))
        }
        categories["equipment"] = equipmentLayouts

        categories["museum"] = MuseumInventoryDecoder.parseMuseum(museumMember)
        categories["sacks"] = SackDecoder.parseSacks(member, inventory)
        categories["essence"] = parseEssence(member)
        categories["pets"] = emptyList() // pets valued separately from JSON
        return categories
    }

    @JvmStatic
    fun parseMuseum(museumMember: JsonObject?): List<Stack> = MuseumInventoryDecoder.parseMuseum(museumMember)

    @JvmStatic
    fun parseMuseumById(museumMember: JsonObject?): MutableMap<String, Stack> =
        MuseumInventoryDecoder.parseMuseumById(museumMember)

    /**
     * Toolkit JSON is `{IS_UNLOCKED, IN_USE: {CROP: {"0": bool}}, CROP: {"0": {data}}}`. Older saves may use an
     * array or a single `{data}` per crop, so all three are read.
     */
    @JvmStatic
    fun parseToolkit(toolkit: JsonObject?): MutableList<ToolkitItem> {
        val out = ArrayList<ToolkitItem>()
        if (toolkit == null) return out
        val inUse = toolkit.get("IN_USE") as? JsonObject
        for ((key, value) in toolkit.entrySet()) {
            if (key == null || key.equals("IS_UNLOCKED", ignoreCase = true) || key.equals("IN_USE", ignoreCase = true)) continue
            val used = inUse?.get(key) as? JsonObject
            when (value) {
                is JsonArray -> value.forEachIndexed { i, element -> out.add(toolkitItem(key, i, used, element)) }
                is JsonObject -> {
                    if (value.has("data")) {
                        out.add(toolkitItem(key, 0, used, value))
                        continue
                    }
                    value.keySet().mapNotNull { it.toIntOrNull() }.sorted().forEach { index ->
                        out.add(toolkitItem(key, index, used, value.get(index.toString())))
                    }
                }
            }
        }
        return out
    }

    private fun toolkitItem(category: String, index: Int, used: JsonObject?, data: JsonElement?): ToolkitItem {
        val decoded = if (data == null || data.isJsonNull) emptyList() else InventoryDecodeCache.decodeDataElement(data)
        val flag = used?.get(index.toString())
        val inUse = flag != null && flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean && flag.asBoolean
        return ToolkitItem(category, index, inUse, decoded.firstOrNull())
    }

    @JvmStatic
    fun stacksFromItemBytes(itemBytes: JsonElement?): List<Stack> = InventoryDecodeCache.decodeDataElement(itemBytes)

    @JvmStatic
    fun slotFromItemBytes(itemBytes: JsonElement?): InventorySnapshot.Slot? =
        InventoryDecodeCache.decodeDataElement(itemBytes).firstOrNull()?.let(InventoryDecodeSupport::toUiSlot)

    /** Slot for an item tag, with optional rarity for `PET_*` tags. */
    @JvmStatic
    @JvmOverloads
    fun slotFromTag(tag: String?, displayName: String?, tier: String? = ""): InventorySnapshot.Slot? {
        if (tag.isNullOrBlank()) return null
        val upper = tag.uppercase(Locale.ROOT)
        if (upper.startsWith("PET_") && !upper.startsWith("PET_ITEM_")) {
            SkyBlockItemFactory.auctionPetSlot(upper, displayName, tier)?.let { return it }
        }
        return InventorySnapshot.Slot(upper, 1, emptyList(), displayName ?: "", null, null, null)
    }

    @JvmStatic
    fun parseUi(member: JsonObject?): InventorySnapshot {
        if (member == null) return InventorySnapshot.empty()
        val inventory = inventoryObject(member)
        val bags = InventoryDecodeSupport.obj(inventory?.get("bag_contents"))
        val loadout = InventoryDecodeSupport.obj(member.get("loadout"))

        val invSlots = uiSlots(inventory, "inv_contents", 36)
        val armorSlots = InventoryDecodeSupport.toUiSlots(readArmorSlots(member))
        val equipSlots = uiSlots(inventory, "equipment_contents", 4)
        val combinedInv = playerSlots(equipSlots, armorSlots, invSlots)

        val enderPages = InventoryDecodeSupport.chunkPages(uiSlots(inventory, "ender_chest_contents", 45), 45, 9, "Ender Chest")

        var backpackPages: List<InventorySnapshot.Page> = ArrayList()
        InventoryDecodeSupport.obj(inventory?.get("backpack_contents"))?.let { backpacks ->
            backpackPages = backpacks.keySet().sortedWith(InventoryDecodeSupport::compareKeys).map { key ->
                val slots = InventoryDecodeSupport.toUiSlots(
                    InventoryDecodeCache.decodeDataElementKeepingEmpty(backpacks.get(key), 27)
                )
                val num = InventoryDecodeSupport.tryParseInt(key)
                InventorySnapshot.Page(if (num != null) "Backpack ${num + 1}" else "Backpack $key", slots, 9)
            }
        }
        if (backpackPages.isEmpty()) backpackPages = listOf(InventorySnapshot.emptyPage("Backpacks", 9))

        val wardrobePages = WardrobeDecoder.parseWardrobePages(inventory, loadout)
        val equipmentPages = WardrobeDecoder.parseEquipmentWardrobePages(inventory, loadout)
        val accessoryInfo = parseAccessoryInfo(member)
        val loadouts = LoadoutDecoder.parseNamedLoadouts(member, loadout, accessoryInfo)

        val sackPages = SackDecoder.parseSackPages(member, inventory)
        val shared = InventoryDecodeSupport.obj(member.get("shared_inventory"))
        val carnivalPresent = hasInventoryField(shared, "carnival_mask_inventory_contents") ||
            hasInventoryField(inventory, "carnival_mask_inventory_contents") ||
            hasInventoryField(member, "carnival_mask_inventory_contents")
        val candyPresent = hasInventoryField(shared, "candy_inventory_contents") ||
            hasInventoryField(inventory, "candy_inventory_contents") ||
            hasInventoryField(bags, "candy_inventory_contents") ||
            hasInventoryField(member, "candy_inventory_contents")
        val carnivalPage = sharedPage(shared, inventory, member, "carnival_mask_inventory_contents", "Carnival Masks")
        val candyPage = sharedPage(shared, inventory, member, "candy_inventory_contents", "Candy Bag")
        val timePocket = timePocketPage(bags)

        return InventorySnapshot(
            InventorySnapshot.Page("Inventory", combinedInv, 9),
            enderPages.ifEmpty { listOf(InventorySnapshot.emptyPage("Ender Chest", 9)) },
            backpackPages,
            wardrobePages,
            equipmentPages,
            loadouts,
            sackPages,
            bagPage(bags, "fishing_bag", "Fishing Bag"),
            bagPage(bags, "potion_bag", "Potion Bag"),
            bagPage(bags, "quiver", "Quiver"),
            accessoryBagPages(bags),
            accessoryInfo,
            timePocket ?: InventorySnapshot.emptyPage("Time Pocket", 9),
            InventorySnapshot.Page("Personal Vault", uiSlots(inventory, "personal_vault_contents", 27), 9),
            carnivalPage,
            carnivalPresent,
            candyPage,
            candyPresent,
            inventoryApiEnabled(inventory),
            hasInventoryField(inventory, "personal_vault_contents"),
            timePocket != null,
        )
    }

    private fun uiSlots(container: JsonObject?, field: String, minSlots: Int): MutableList<InventorySnapshot.Slot?> =
        InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(container, field, minSlots))

    @JvmStatic
    fun hasInventoryApi(member: JsonObject?): Boolean = member != null && inventoryApiEnabled(inventoryObject(member))

    /** Wardrobe sets from `loadout.armor`. Empty when the profile only has the legacy wardrobe blob. */
    @JvmStatic
    fun wardrobePresets(member: JsonObject?): List<WardrobePreset> {
        if (member == null) return emptyList()
        return WardrobeDecoder.armorPresets(inventoryObject(member), InventoryDecodeSupport.obj(member.get("loadout")))
    }

    /** Hypixel omits inv_contents entirely (even for an empty inventory) when the inventory API is off. */
    @JvmStatic
    fun inventoryApiEnabled(inventory: JsonObject?): Boolean {
        val contents = inventory?.get("inv_contents") ?: return false
        return !contents.isJsonNull
    }

    private fun hasInventoryField(container: JsonObject?, field: String): Boolean {
        val value = container?.get(field) ?: return false
        if (value.isJsonNull) return false
        return InventoryDecodeCache.extractData(value).isNotBlank()
    }

    private fun sharedPage(
        shared: JsonObject?,
        inventory: JsonObject?,
        member: JsonObject?,
        field: String,
        title: String,
    ): InventorySnapshot.Page {
        val source = listOf(shared, inventory, member).firstOrNull { it != null && it.has(field) }
            ?: return InventorySnapshot.emptyPage(title, 9)
        return InventorySnapshot.Page(title, uiSlots(source, field, 27), 9)
    }

    private fun bagPage(bags: JsonObject?, field: String, title: String): InventorySnapshot.Page =
        InventorySnapshot.Page(title, uiSlots(bags, field, 27), 9)

    /** Accessory bag is one flat NBT list; in-game UI pages every 45 slots (9×5). */
    private fun accessoryBagPages(bags: JsonObject?): List<InventorySnapshot.Page> =
        InventoryDecodeSupport.chunkPages(uiSlots(bags, "talisman_bag", 45), 45, 9, "Accessory Bag")
            .ifEmpty { listOf(InventorySnapshot.emptyPage("Accessory Bag", 9)) }

    /** Null when no Time Pocket field exists; the API key is unconfirmed, so a few candidates are probed. */
    private fun timePocketPage(bags: JsonObject?): InventorySnapshot.Page? {
        if (bags == null) return null
        TIME_POCKET_KEYS.firstOrNull { bags.has(it) }?.let {
            return InventorySnapshot.Page("Time Pocket", uiSlots(bags, it, 27), 9)
        }
        // Fallback: any bag key containing "time"
        for ((key, value) in bags.entrySet()) {
            if (key != null && key.lowercase(Locale.ROOT).contains("time")) {
                return InventorySnapshot.Page(
                    "Time Pocket",
                    InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeDataElementKeepingEmpty(value, 27)),
                    9,
                )
            }
        }
        return null
    }

    @JvmStatic
    fun parseAccessoryInfo(member: JsonObject?): InventorySnapshot.AccessoryInfo {
        val storage = InventoryDecodeSupport.obj(member?.get("accessory_bag_storage"))
        var highest = 0
        var power = ""
        var bagUpgrades = 0
        val unlockedPowers = ArrayList<String>()
        val tunings = ArrayList<InventorySnapshot.TuningTemplate>()
        if (storage != null) {
            highest = maxOf(0, InventoryDecodeSupport.jsonInt(storage, "highest_magical_power") ?: 0)
            val selected = storage.get("selected_power")
            power = if (selected != null && selected.isJsonPrimitive) selected.asString else ""
            bagUpgrades = maxOf(0, InventoryDecodeSupport.jsonInt(storage, "bag_upgrades_purchased") ?: 0)
            val powers = storage.get("unlocked_powers")
            if (powers != null && powers.isJsonArray) {
                for (el in powers.asJsonArray) {
                    if (el == null || !el.isJsonPrimitive) continue
                    try {
                        val id = el.asString
                        if (!id.isNullOrBlank()) unlockedPowers.add(id)
                    } catch (ignored: Exception) {
                    }
                }
            }
            InventoryDecodeSupport.obj(storage.get("tuning"))?.let { tuning ->
                val keys = tuning.keySet().filter { it != null && it.startsWith("slot_") }
                    .sortedWith(InventoryDecodeSupport::compareKeys)
                for (key in keys) {
                    val index = InventoryDecodeSupport.tryParseInt(key.substring("slot_".length)) ?: continue
                    val slot = InventoryDecodeSupport.obj(tuning.get(key))
                    val stats = LoadoutDecoder.readTuningStats(slot)
                    if (stats.isNotEmpty() || slot != null) tunings.add(InventorySnapshot.TuningTemplate(index, stats))
                }
            }
        }
        val current = MagicalPowerCalculator.fromMember(member)
        return InventorySnapshot.AccessoryInfo(current, highest, power, tunings, bagUpgrades, unlockedPowers)
    }

    @JvmStatic
    fun parseRiftUi(member: JsonObject?): RiftInventories {
        val rift = InventoryDecodeSupport.obj(member?.get("rift"))
        val inv = InventoryDecodeSupport.obj(rift?.get("inventory"))
        val invSlots = uiSlots(inv, "inv_contents", 36)
        val armorSlots = uiSlots(inv, "inv_armor", 4)
        val equipSlots = uiSlots(inv, "equipment_contents", 4)
        val invPage = InventorySnapshot.Page("Inventory", playerSlots(equipSlots, armorSlots, invSlots), 9)
        // Rift ender chest is a flat list paged by 45 like the main one (page icons in ender_chest_page_icons).
        val ender = InventoryDecodeSupport.chunkPages(uiSlots(inv, "ender_chest_contents", 45), 45, 9, "Ender Chest")
        return RiftInventories(invPage, equipSlots, armorSlots, ender)
    }

    /**
     * Player layout used by both inventory renderers: [equip x4][helmet..boots][inv_contents x36].
     * Hypixel inv_armor is boots-first; inv_contents is 0-8 hotbar, 9-35 main.
     */
    @JvmStatic
    fun playerSlots(
        equipment: List<InventorySnapshot.Slot?>?,
        armor: List<InventorySnapshot.Slot?>?,
        inventory: List<InventorySnapshot.Slot?>?,
    ): MutableList<InventorySnapshot.Slot?> {
        val out = ArrayList<InventorySnapshot.Slot?>(44)
        for (i in 0 until 4) out.add(equipment?.getOrNull(i))
        if (armor != null && armor.size >= 4) {
            out.add(armor[3])
            out.add(armor[2])
            out.add(armor[1])
            out.add(armor[0])
        } else {
            for (i in 0 until 4) out.add(armor?.getOrNull(i))
        }
        for (i in 0 until 36) out.add(inventory?.getOrNull(i))
        return out
    }

    @JvmStatic
    fun prettyWords(raw: String?): String = InventoryDecodeSupport.prettyWords(raw)

    private fun parseEssence(member: JsonObject): List<Stack> {
        val currencies = InventoryDecodeSupport.obj(member.get("currencies"))
        val essence = InventoryDecodeSupport.obj(currencies?.get("essence")) ?: return emptyList()
        val out = ArrayList<Stack>()
        for ((key, value) in essence.entrySet()) {
            val amount = InventoryDecodeSupport.jsonInt(InventoryDecodeSupport.obj(value), "current")
            if (amount != null && amount > 0) {
                out.add(Stack("ESSENCE_" + key.uppercase(Locale.ROOT), amount, CompoundTag(), emptyList(), false))
            }
        }
        return out
    }

    /** Hypixel inv_armor order: 0 boots, 1 leggings, 2 chestplate, 3 helmet (empties kept). */
    @JvmStatic
    fun readArmorSlots(member: JsonObject): List<Stack?> {
        val inventory = inventoryObject(member)
        var data = ""
        if (inventory != null) {
            data = InventoryDecodeCache.extractData(inventory.get("inv_armor"))
            if (data.isBlank()) data = InventoryDecodeCache.extractData(inventory.get("armor"))
        }
        if (data.isBlank()) data = InventoryDecodeCache.extractData(member.get("inv_armor"))
        if (data.isBlank()) return emptyList()
        return try {
            InventoryDecodeCache.decodeKeepingEmpty(data, 4)
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    /** Flatten Hypixel JSON text components / quoted SNBT into a §-legacy string. */
    @JvmStatic
    fun cleanJsonText(raw: String?): String = NbtInventoryCodec.cleanJsonText(raw)
}

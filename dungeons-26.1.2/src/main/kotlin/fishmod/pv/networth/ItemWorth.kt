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
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.price.HypixelItemsCache
import fishmod.pv.price.ItemPricer
import fishmod.utils.TextStrip
import java.util.Locale
import net.minecraft.nbt.CompoundTag

object ItemWorth {
    private data class UpgradeEnchant(val item: String, val tier: Int)

    private data class GemBucket(val label: String, val value: Double, val count: Int) {
        fun plus(other: GemBucket) = GemBucket(label, value + other.value, count + other.count)
    }

    private val ENCHANT_UPGRADES = mapOf(
        "SCAVENGER" to UpgradeEnchant("GOLDEN_BOUNTY", 6),
        "PESTERMINATOR" to UpgradeEnchant("PESTHUNTING_GUIDE", 6),
        "LUCK_OF_THE_SEA" to UpgradeEnchant("GOLD_BOTTLE_CAP", 7),
        "PISCARY" to UpgradeEnchant("TROUBLED_BUBBLE", 7),
        "FRAIL" to UpgradeEnchant("SEVERED_PINCER", 7),
        "SPIKED_HOOK" to UpgradeEnchant("OCTOPUS_TENDRIL", 7),
        "CHARM" to UpgradeEnchant("CHAIN_END_TIMES", 6),
        "VENOMOUS" to UpgradeEnchant("FATEFUL_STINGER", 7),
    )

    private const val MAX_ENCHANT_DETAILS = 7

    private val NAME_TIERS = listOf(
        "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON",
        "DIVINE", "VERY SPECIAL", "SPECIAL", "SUPREME", "ULTIMATE",
    )
    private val NON_DIGIT = Regex("\\D")

    @JvmStatic
    @JvmOverloads
    fun value(stack: InventoryDecoder.Stack?, includeCosmetics: Boolean = true): Double =
        breakdown(stack, includeCosmetics).total

    /** Labeled coin breakdown for one item (base + priced modifiers); same math as [value], zero amounts omitted. */
    @JvmStatic
    @JvmOverloads
    fun breakdown(stack: InventoryDecoder.Stack?, includeCosmetics: Boolean = true): Breakdown {
        if (stack == null) return Breakdown.empty()
        if (!includeCosmetics && isCosmeticItem(stack)) return Breakdown.empty()

        val parts = ArrayList<Part>()
        val ea = stack.extraAttributes
        if (ea.isEmpty) {
            val material = ItemPricer.materialPrice(stack.id) * maxOf(0L, stack.count.toLong())
            if (material > 0) parts.add(Part.item(baseItemLabel(stack), material, itemNameColor(stack)))
            return Breakdown(maxOf(0.0, material), parts)
        }

        val skyblockItem = HypixelItemsCache.get(stack.id)
        val itemId = resolveItemId(stack, includeCosmetics)
        val base = ItemPricer.price(itemId) * stack.count
        var total = base
        if (base > 0) parts.add(Part.item(baseItemLabel(stack), base, itemNameColor(stack)))

        total += addPotatoParts(parts, ea)
        val recombVal = recomb(stack, skyblockItem, ea)
        if (recombVal > 0) {
            parts.add(Part.upgrade("Recombobulator", "", recombVal))
            total += recombVal
        }

        total += addCounted(parts, ea, "art_of_war_count", "Art of War", "THE_ART_OF_WAR", NetworthData.worth("artOfWar", 0.6))
        total += addCounted(parts, ea, "artOfPeaceApplied", "Art of Peace", "THE_ART_OF_PEACE", NetworthData.worth("artOfPeace", 0.8))
        total += addStarParts(parts, skyblockItem, ea)

        val reforgeVal = reforge(skyblockItem, ea)
        if (reforgeVal > 0) {
            val modifier = NbtAttrs.string(ea, "modifier")
            val stone = NetworthData.reforgeItem(modifier)
            val stoneName = if (stone.isNullOrBlank()) InventoryDecoder.prettyWords(modifier) else SkyBlockItemFactory.plainDisplayName(stone)
            parts.add(Part.upgrade("Reforge", stoneName, reforgeVal))
            total += reforgeVal
        }

        total += addFlag(parts, ea, "ethermerge", "Etherwarp", "ETHERWARP_CONDUIT", NetworthData.worth("etherwarp", 1.0))
        total += addCounted(parts, ea, "tuned_transmission", "Transmission Tuner", "TRANSMISSION_TUNER", NetworthData.worth("tunedTransmission", 0.7))
        total += addCounted(parts, ea, "wood_singularity_count", "Wood Singularity", "WOOD_SINGULARITY", NetworthData.worth("woodSingularity", 0.5))
        total += addCounted(parts, ea, "divan_powder_coating", "Divan Powder Coating", "DIVAN_POWDER_COATING", NetworthData.worth("divanPowderCoating", 0.8))
        total += addCounted(parts, ea, "farming_for_dummies_count", "Farming for Dummies", "FARMING_FOR_DUMMIES", NetworthData.worth("farmingForDummies", 0.5))
        total += addCounted(parts, ea, "jalapeno_count", "Jalapeno Book", "JALAPENO_BOOK", NetworthData.worth("jalapenoBook", 0.8))
        total += addCounted(parts, ea, "mana_disintegrator", "Mana Disintegrator", "MANA_DISINTEGRATOR", NetworthData.worth("manaDisintegrator", 0.8))
        total += addCounted(parts, ea, "polarvoid", "Polarvoid Book", "POLARVOID_BOOK", NetworthData.worth("polarvoidBook", 1.0))

        val enrichVal = enrichment(ea)
        if (enrichVal > 0) {
            parts.add(Part.other("Enrichment", InventoryDecoder.prettyWords(NbtAttrs.string(ea, "talisman_enrichment")), enrichVal))
            total += enrichVal
        }

        total += addGemParts(parts, ea)
        total += addScrollParts(parts, ea)
        total += addDrillParts(parts, ea)
        total += addRodParts(parts, ea)
        total += addEnchantParts(parts, stack, ea)
        total += addBookParts(parts, stack, ea)

        if (includeCosmetics) {
            val dyeVal = dye(ea)
            if (dyeVal > 0) {
                parts.add(Part.other("Dye", SkyBlockItemFactory.plainDisplayName(NbtAttrs.string(ea, "dye_item")), dyeVal))
                total += dyeVal
            }
            val runeVal = runeOnItem(stack, ea)
            if (runeVal > 0) {
                val runes = NbtAttrs.compound(ea, "runes")
                val type = if (runes == null || runes.isEmpty) "Rune" else runes.keySet().first()
                val tier = if (runes == null) 1 else NbtAttrs.intValue(runes, type, 1)
                parts.add(Part.other("Rune", InventoryDecoder.prettyWords(type) + " " + tier, runeVal))
                total += runeVal
            }
            val skinVal = soulboundSkin(stack, ea)
            if (skinVal > 0) {
                parts.add(Part.other("Skin", SkyBlockItemFactory.plainDisplayName(NbtAttrs.string(ea, "skin")), skinVal))
                total += skinVal
            }
        }

        return Breakdown(maxOf(0.0, total), parts)
    }

    class Breakdown(@get:JvmName("total") val total: Double, parts: List<Part>?) {
        @get:JvmName("parts")
        val parts: List<Part> = parts?.toList() ?: emptyList()

        companion object {
            @JvmStatic
            fun empty() = Breakdown(0.0, emptyList())
        }
    }

    enum class Section(@get:JvmName("title") val title: String) {
        BASE("Base item"),
        UPGRADES("Upgrades"),
        GEMSTONES("Gemstones"),
        ENCHANTMENTS("Enchantments"),
        CONTENTS("Contents"),
        OTHER("Other"),
    }

    enum class Role {
        /** Catalog base item name. */
        ITEM,

        /** Summary upgrade line (HPB's 10/10, Stars 5/5). */
        UPGRADE,

        /** Nested contributor under a section. */
        DETAIL,

        /** Collapsed remainder note. */
        NOTE,
    }

    /** One priced row in an item breakdown. */
    class Part(
        section: Section?,
        role: Role?,
        label: String?,
        detail: String?,
        @get:JvmName("amount") val amount: Double,
        @get:JvmName("accentColor") val accentColor: Int,
    ) {
        @get:JvmName("section")
        val section: Section = section ?: Section.OTHER
        @get:JvmName("role")
        val role: Role = role ?: Role.DETAIL
        @get:JvmName("label")
        val label: String = label ?: ""
        @get:JvmName("detail")
        val detail: String = detail ?: ""

        companion object {
            @JvmStatic
            fun item(name: String?, amount: Double, rarityColor: Int) = Part(Section.BASE, Role.ITEM, name, "", amount, rarityColor)

            @JvmStatic
            fun upgrade(label: String?, detail: String?, amount: Double) = Part(Section.UPGRADES, Role.UPGRADE, label, detail, amount, 0)

            @JvmStatic
            fun detail(section: Section?, label: String?, amount: Double) = Part(section, Role.DETAIL, label, "", amount, 0)

            @JvmStatic
            fun other(label: String?, detail: String?, amount: Double) = Part(Section.OTHER, Role.DETAIL, label, detail, amount, 0)

            @JvmStatic
            fun note(section: Section?, text: String?) = Part(section, Role.NOTE, text, "", 0.0, 0)
        }
    }

    private fun displayName(stack: InventoryDecoder.Stack): String {
        val name = stack.displayName
        if (!name.isNullOrBlank()) return TextStrip.plain(name).trim()
        return SkyBlockItemFactory.plainDisplayName(stack.id)
    }

    /** Catalog name without reforge prefix (e.g. Dark Claymore, not Withered Dark Claymore). */
    private fun baseItemLabel(stack: InventoryDecoder.Stack): String {
        val plain: String? = SkyBlockItemFactory.plainDisplayName(stack.id)
        return if (!plain.isNullOrBlank()) plain else displayName(stack)
    }

    private fun itemNameColor(stack: InventoryDecoder.Stack): Int {
        val upper = (stack.lore.lastOrNull() ?: "").uppercase(Locale.ROOT)
        NAME_TIERS.firstOrNull { upper.contains(it) }?.let { return SkyBlockItemFactory.tierArgb(it.replace(' ', '_')) }
        return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(stack.id))
    }

    private fun addPotatoParts(parts: MutableList<Part>, ea: CompoundTag): Double {
        val count = NbtAttrs.intValue(ea, "hot_potato_count", 0)
        if (count <= 0) return 0.0
        var total = 0.0
        val hot = minOf(count, 10)
        val hotVal = ItemPricer.price("HOT_POTATO_BOOK") * hot * NetworthData.worth("hotPotatoBook", 1.0)
        if (hotVal > 0) {
            parts.add(Part.upgrade("HPB's", "$hot/10", hotVal))
            total += hotVal
        }
        if (count > 10) {
            val fuming = count - 10
            val fumVal = ItemPricer.price("FUMING_POTATO_BOOK") * fuming * NetworthData.worth("fumingPotatoBook", 0.6)
            if (fumVal > 0) {
                parts.add(Part.upgrade("Fuming", "$fuming/5", fumVal))
                total += fumVal
            }
        }
        return total
    }

    private fun addEnchantParts(parts: MutableList<Part>, stack: InventoryDecoder.Stack, ea: CompoundTag): Double {
        if (stack.id == "ENCHANTED_BOOK") return 0.0
        val enchants = NbtAttrs.intMap(ea, "enchantments")
        if (enchants.isEmpty()) return 0.0
        var total = 0.0
        val children = ArrayList<Part>()
        val blocked = NetworthData.blockedEnchants()
        val ignored = NetworthData.ignoredEnchants()
        val stacking = NetworthData.stackingEnchants()
        val ignoreSilex = NetworthData.ignoreSilex()
        for ((key, value) in enchants) {
            val name = key.uppercase(Locale.ROOT)
            if (blocked[stack.id]?.contains(name) == true) continue
            if ((ignored[name] ?: Int.MIN_VALUE) == value) continue
            val displayLevel = if (stacking.contains(name)) 1 else value
            var line = 0.0
            if (name == "EFFICIENCY" && value >= 6 && !ignoreSilex.contains(stack.id)) {
                val efficiencyLevel = value - if (stack.id == "STONK_PICKAXE") 6 else 5
                if (efficiencyLevel > 0) {
                    line += ItemPricer.price("SIL_EX") * efficiencyLevel * NetworthData.worth("silex", 0.75)
                }
            }
            val upgrade = ENCHANT_UPGRADES[name]
            if (upgrade != null && value >= upgrade.tier) {
                line += ItemPricer.price(upgrade.item) * NetworthData.worth("enchantmentUpgrades", 0.8)
            }
            line += ItemPricer.price("ENCHANTMENT_${name}_$value")
            if (line > 0) {
                children.add(Part.detail(Section.ENCHANTMENTS, enchantLabel(name, displayLevel), line))
                total += line
            }
        }
        if (total > 0) {
            children.sortByDescending { it.amount }
            val shown = minOf(MAX_ENCHANT_DETAILS, children.size)
            parts.addAll(children.subList(0, shown))
            val hidden = children.size - shown
            if (hidden > 0) {
                parts.add(Part.note(Section.ENCHANTMENTS, "+$hidden more enchant" + if (hidden == 1) "" else "s"))
            }
        }
        return total
    }

    private fun addBookParts(parts: MutableList<Part>, stack: InventoryDecoder.Stack, ea: CompoundTag): Double {
        if (stack.id != "ENCHANTED_BOOK") return 0.0
        var total = 0.0
        for ((key, value) in NbtAttrs.intMap(ea, "enchantments")) {
            val name = key.uppercase(Locale.ROOT)
            val line = ItemPricer.price("ENCHANTMENT_${name}_$value")
            if (line > 0) {
                parts.add(Part.detail(Section.ENCHANTMENTS, enchantLabel(name, value), line))
                total += line
            }
        }
        return total
    }

    private fun addStarParts(parts: MutableList<Part>, skyblockItem: JsonObject?, ea: CompoundTag): Double {
        val master = masterStars(skyblockItem, ea)
        val essence = essenceStars(skyblockItem, ea)
        if (essence <= 0 && master <= 0) return 0.0
        val level = upgradeLevel(ea)
        var total = 0.0
        if (essence > 0) {
            parts.add(Part.upgrade("Stars", "${minOf(level, 5)}/5", essence))
            total += essence
        }
        if (master > 0) {
            parts.add(Part.upgrade("Master Stars", "${minOf(maxOf(0, level - 5), 5)}/5", master))
            total += master
        }
        return total
    }

    private fun addGemParts(parts: MutableList<Part>, ea: CompoundTag): Double {
        val gems = NbtAttrs.compound(ea, "gems")
        if (gems == null || gems.isEmpty) return 0.0
        var total = 0.0
        val buckets = LinkedHashMap<String, GemBucket>()
        for (key in gems.keySet()) {
            if (key == "unlocked_slots" || key.endsWith("_gem")) continue
            val value = gems.get(key)
            val tier = if (value is CompoundTag) NbtAttrs.string(value, "quality") else NbtAttrs.string(gems, key)
            if (tier.isNullOrBlank()) continue
            var type = NbtAttrs.string(gems, key + "_gem")
            if (type.isNullOrBlank()) {
                val underscore = key.indexOf('_')
                type = if (underscore > 0) key.substring(0, underscore) else key
            }
            val id = (tier + "_" + type + "_GEM").uppercase(Locale.ROOT)
            val line = ItemPricer.price(id) * NetworthData.worth("gemstone", 1.0)
            if (line > 0) {
                val label = InventoryDecoder.prettyWords(tier) + " " + InventoryDecoder.prettyWords(type)
                buckets.merge(id, GemBucket(label, line, 1), GemBucket::plus)
                total += line
            }
        }
        if (total > 0) {
            for (bucket in buckets.values) {
                val label = if (bucket.count > 1) "${bucket.label} (x${bucket.count})" else bucket.label
                parts.add(Part.detail(Section.GEMSTONES, label, bucket.value))
            }
        }
        return total
    }

    private fun addScrollParts(parts: MutableList<Part>, ea: CompoundTag): Double {
        var total = 0.0
        for (id in NbtAttrs.stringList(ea, "ability_scroll")) {
            val line = ItemPricer.price(id.uppercase(Locale.ROOT)) * NetworthData.worth("necronBladeScroll", 1.0)
            if (line > 0) {
                parts.add(Part.other("Scroll", SkyBlockItemFactory.plainDisplayName(id), line))
                total += line
            }
        }
        return total
    }

    private fun addCounted(parts: MutableList<Part>, ea: CompoundTag, key: String, label: String, itemId: String, mult: Double): Double {
        val count = NbtAttrs.intValue(ea, key, 0)
        if (count <= 0) return 0.0
        val value = ItemPricer.price(itemId) * count * mult
        if (value > 0) parts.add(Part.upgrade(label, if (count > 1) "x$count" else "", value))
        return value
    }

    private fun addFlag(parts: MutableList<Part>, ea: CompoundTag, key: String, label: String, itemId: String, mult: Double): Double {
        if (!NbtAttrs.has(ea, key)) return 0.0
        val value = ItemPricer.price(itemId) * mult
        if (value > 0) parts.add(Part.upgrade(label, "", value))
        return value
    }

    private fun addDrillParts(parts: MutableList<Part>, ea: CompoundTag): Double {
        var total = 0.0
        for (key in listOf("drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine")) {
            val part = NbtAttrs.string(ea, key)
            if (part.isNullOrBlank()) continue
            val line = ItemPricer.price(part.uppercase(Locale.ROOT)) * NetworthData.worth("drillPart", 1.0)
            if (line > 0) {
                parts.add(Part.other("Drill Part", SkyBlockItemFactory.plainDisplayName(part), line))
                total += line
            }
        }
        return total
    }

    private fun addRodParts(parts: MutableList<Part>, ea: CompoundTag): Double {
        var total = 0.0
        for (key in listOf("line", "hook", "sinker")) {
            val id = NbtAttrs.compound(ea, key)?.let { NbtAttrs.string(it, "part") }
            if (id.isNullOrBlank()) continue
            val line = ItemPricer.price(id.uppercase(Locale.ROOT)) * NetworthData.worth("rodPart", 1.0)
            if (line > 0) {
                parts.add(Part.other(InventoryDecoder.prettyWords(key), SkyBlockItemFactory.plainDisplayName(id), line))
                total += line
            }
        }
        return total
    }

    private fun enchantLabel(name: String, level: Int): String = InventoryDecoder.prettyWords(name) + " " + roman(level)

    private fun roman(value: Int): String {
        if (value <= 0) return value.toString()
        val nums = intArrayOf(10, 9, 5, 4, 1)
        val glyphs = arrayOf("X", "IX", "V", "IV", "I")
        val sb = StringBuilder()
        var n = minOf(value, 20)
        for (i in nums.indices) {
            while (n >= nums[i]) {
                sb.append(glyphs[i])
                n -= nums[i]
            }
        }
        return sb.toString()
    }

    /** Standalone cosmetic items (skins, dyes, furniture, etc.) - zeroed when cosmetics off. */
    private fun isCosmeticItem(stack: InventoryDecoder.Stack): Boolean {
        val id = stack.id.uppercase(Locale.ROOT)
        if (id.contains("_SKIN") || id.startsWith("PET_SKIN_") || id.endsWith("_DYE") || id.contains("DYE_")) return true
        if (id == "RUNE" || id == "UNIQUE_RUNE" || id.startsWith("RUNE_")) return true
        val category = HypixelItemsCache.get(id)?.get("category")
        return category != null && category.isJsonPrimitive && category.asString.equals("COSMETIC", ignoreCase = true)
    }

    private fun resolveItemId(stack: InventoryDecoder.Stack, includeCosmetics: Boolean): String {
        val itemId = stack.id
        val ea = stack.extraAttributes
        val skin = NbtAttrs.string(ea, "skin")
        if (includeCosmetics && !skin.isNullOrBlank()) {
            val skinned = itemId + "_SKINNED_" + skin
            if (ItemPricer.price(skinned) > ItemPricer.price(itemId)) return skinned
        }
        if (itemId == "RUNE" || itemId == "UNIQUE_RUNE") {
            val runes = NbtAttrs.compound(ea, "runes")
            if (runes != null && !runes.isEmpty) {
                val type = runes.keySet().first()
                val tier = NbtAttrs.intValue(runes, type, 1)
                return "RUNE_${type}_$tier".uppercase(Locale.ROOT)
            }
        }
        if (itemId == "NEW_YEAR_CAKE") return "NEW_YEAR_CAKE_" + NbtAttrs.intValue(ea, "new_years_cake", 0)
        if (includeCosmetics && NbtAttrs.has(ea, "is_shiny") && ItemPricer.price(itemId + "_SHINY") > 0) {
            return itemId + "_SHINY"
        }
        if (itemId.startsWith("STARRED_") && ItemPricer.price(itemId) <= 0) {
            val unstarred = itemId.substring("STARRED_".length)
            if (ItemPricer.price(unstarred) > 0) return unstarred
        }
        return itemId
    }

    private fun category(skyblockItem: JsonObject?): String {
        val category = skyblockItem?.get("category")
        return if (category != null && category.isJsonPrimitive) category.asString else ""
    }

    private fun recomb(stack: InventoryDecoder.Stack, skyblockItem: JsonObject?, ea: CompoundTag): Double {
        val upgrades = NbtAttrs.intValue(ea, "rarity_upgrades", 0)
        if (upgrades <= 0 || NbtAttrs.has(ea, "item_tier")) return 0.0
        val hasEnchants = NbtAttrs.intMap(ea, "enchantments").isNotEmpty()
        val allows = NetworthData.allowedRecombCategories().contains(category(skyblockItem)) ||
            NetworthData.allowedRecombIds().contains(stack.id)
        val lastLore = stack.lore.lastOrNull() ?: ""
        val accessory = lastLore.contains("ACCESSORY") || lastLore.contains("HATCESSORY")
        if (!(hasEnchants || allows || accessory)) return 0.0
        return ItemPricer.price("RECOMBOBULATOR_3000") * NetworthData.worth("recombobulator", 0.8)
    }

    // Digits-only like the original string round-trip, so a negative level reads as its absolute value.
    private fun upgradeLevel(ea: CompoundTag): Int {
        fun digits(n: Int) = n.toString().replace(NON_DIGIT, "").ifEmpty { "0" }.toInt()
        return maxOf(digits(NbtAttrs.intValue(ea, "dungeon_item_level", 0)), digits(NbtAttrs.intValue(ea, "upgrade_level", 0)))
    }

    private fun upgradeCosts(skyblockItem: JsonObject?): JsonArray? = skyblockItem?.get("upgrade_costs") as? JsonArray

    private fun masterStars(skyblockItem: JsonObject?, ea: CompoundTag): Double {
        val level = upgradeLevel(ea)
        if (level <= 5) return 0.0
        val costs = upgradeCosts(skyblockItem)
        if (costs != null && costs.size() > 5) return 0.0
        val starsUsed = minOf(level - 5, 5)
        val stars = NetworthData.masterStars()
        var total = 0.0
        for (i in 0 until minOf(starsUsed, stars.size)) {
            total += ItemPricer.price(stars[i]) * NetworthData.worth("masterStar", 1.0)
        }
        return total
    }

    private fun essenceStars(skyblockItem: JsonObject?, ea: CompoundTag): Double {
        val costs = upgradeCosts(skyblockItem) ?: return 0.0
        val level = upgradeLevel(ea)
        if (level <= 0) return 0.0
        var total = 0.0
        for (i in 0 until minOf(level, costs.size())) {
            val step = costs.get(i)
            if (step.isJsonArray) {
                for (cost in step.asJsonArray) {
                    if (cost.isJsonObject) total += starCost(cost.asJsonObject)
                }
            } else if (step.isJsonObject) {
                total += starCost(step.asJsonObject)
            }
        }
        return total
    }

    private fun starCost(upgrade: JsonObject?): Double {
        if (upgrade == null) return 0.0
        val amountEl = upgrade.get("amount")
        val amount = if (amountEl != null && amountEl.isJsonPrimitive) amountEl.asInt else 0
        val essenceType = upgrade.get("essence_type")
        if (essenceType != null && essenceType.isJsonPrimitive) {
            val type = essenceType.asString.uppercase(Locale.ROOT)
            return ItemPricer.price("ESSENCE_$type") * amount * NetworthData.worth("essence", 0.75)
        }
        val itemId = upgrade.get("item_id")
        if (itemId != null && itemId.isJsonPrimitive) return ItemPricer.price(itemId.asString) * amount
        return 0.0
    }

    private fun enrichment(ea: CompoundTag): Double {
        if (NbtAttrs.string(ea, "talisman_enrichment").isNullOrBlank()) return 0.0
        var min = Double.POSITIVE_INFINITY
        for (id in NetworthData.enrichments()) {
            val price = ItemPricer.price(id)
            if (price > 0) min = minOf(min, price)
        }
        if (!min.isFinite()) return 0.0
        return min * NetworthData.worth("enrichment", 0.5)
    }

    private fun reforge(skyblockItem: JsonObject?, ea: CompoundTag): Double {
        val modifier = NbtAttrs.string(ea, "modifier")
        if (modifier.isNullOrBlank()) return 0.0
        if (category(skyblockItem) == "ACCESSORY") return 0.0
        val item = NetworthData.reforgeItem(modifier) ?: return 0.0
        return ItemPricer.price(item) * NetworthData.worth("reforge", 1.0)
    }

    private fun dye(ea: CompoundTag): Double {
        val dye = NbtAttrs.string(ea, "dye_item")
        if (dye.isNullOrBlank()) return 0.0
        return ItemPricer.price(dye) * NetworthData.worth("dye", 0.9)
    }

    private fun runeOnItem(stack: InventoryDecoder.Stack, ea: CompoundTag): Double {
        if (stack.id.startsWith("RUNE")) return 0.0
        val runes = NbtAttrs.compound(ea, "runes")
        if (runes == null || runes.isEmpty) return 0.0
        val type = runes.keySet().first()
        val tier = NbtAttrs.intValue(runes, type, 1)
        return ItemPricer.price("RUNE_${type}_$tier".uppercase(Locale.ROOT)) * NetworthData.worth("runes", 0.6)
    }

    private fun soulboundSkin(stack: InventoryDecoder.Stack, ea: CompoundTag): Double {
        val skin = NbtAttrs.string(ea, "skin")
        if (skin.isNullOrBlank() || !stack.soulbound) return 0.0
        if (stack.id.contains(skin)) return 0.0
        return ItemPricer.price(skin) * NetworthData.worth("soulboundSkins", 0.8)
    }
}

package fishmod.features.croesus

import fishmod.utils.HypixelApi
import fishmod.utils.networth.ItemsDb
import java.util.regex.Pattern

object CroesusRewardParser {

    private val ULTIMATE_ENCHANTS: Set<String> = setOf(
        "Bank", "Bobbin Time", "Chimera", "Combo", "Duplex", "Fatal Tempo", "Flash",
        "Habanero Tactics", "Inferno", "Last Stand", "Legion", "No Pain No Gain", "One For All",
        "Rend", "Soul Eater", "Swarm", "The One", "Ultimate Jerry", "Ultimate Wise", "Wisdom"
    )
    private val ITEM_REPLACEMENTS: MutableMap<String, String> = HashMap()
    private val BOOK_PATTERN: Pattern = Pattern.compile("Enchanted Book \\((?:§.)*([\\w' ]+?) ((?:[IVX]+|\\d+))(?:§.)*\\)")
    private val ESSENCE_PATTERN: Pattern = Pattern.compile("^(\\w+) Essence x(\\d+)$")

    init {
        ITEM_REPLACEMENTS["Shiny Wither Boots"] = "WITHER_BOOTS"
        ITEM_REPLACEMENTS["Shiny Wither Leggings"] = "WITHER_LEGGINGS"
        ITEM_REPLACEMENTS["Shiny Wither Chestplate"] = "WITHER_CHESTPLATE"
        ITEM_REPLACEMENTS["Shiny Wither Helmet"] = "WITHER_HELMET"
        ITEM_REPLACEMENTS["Necron's Handle"] = "NECRON_HANDLE"
        ITEM_REPLACEMENTS["Shiny Necron's Handle"] = "NECRON_HANDLE"
        ITEM_REPLACEMENTS["Wither Shard"] = "SHARD_WITHER"
        ITEM_REPLACEMENTS["Thorn Shard"] = "SHARD_THORN"
        ITEM_REPLACEMENTS["Apex Dragon Shard"] = "SHARD_APEX_DRAGON"
        ITEM_REPLACEMENTS["Power Dragon Shard"] = "SHARD_POWER_DRAGON"
        ITEM_REPLACEMENTS["Scarf Shard"] = "SHARD_SCARF"
        ITEM_REPLACEMENTS["Necron Dye"] = "DYE_NECRON"
        ITEM_REPLACEMENTS["Livid Dye"] = "DYE_LIVID"
    }

    private fun strip(s: String): String = HypixelApi.STRIP_COLOR.matcher(s).replaceAll("")

    private fun tryParseBook(line: String): Array<String>? {
        val m = BOOK_PATTERN.matcher(line)
        if (!m.find()) return null
        val bookName = strip(m.group(1).trim()).trim()
        val tierStr = m.group(2).trim()
        val isUltimate = ULTIMATE_ENCHANTS.contains(bookName)

        val tier: Int = try {
            tierStr.toInt()
        } catch (e: NumberFormatException) {
            fishmod.utils.data.Roman.toInt(tierStr)
        }

        val enchantPart = bookName.uppercase().replace(" ", "_").replace("'", "")
        var sbId = "ENCHANTMENT_" + (if (isUltimate) "ULTIMATE_" else "") + enchantPart + "_" + tier
        sbId = sbId.replace("ULTIMATE_ULTIMATE_", "ULTIMATE_")
        return arrayOf(sbId, "1")
    }

    private fun tryParseEssence(line: String): Array<String>? {
        val m = ESSENCE_PATTERN.matcher(line)
        return if (!m.matches()) null else arrayOf("ESSENCE_" + m.group(1).uppercase(), m.group(2))
    }

    @JvmStatic
    fun parseLine(line: String): Array<String> {
        val book = tryParseBook(line)
        if (book != null) return book

        val clean = strip(line).trim()
        val essence = tryParseEssence(clean)
        if (essence != null) return essence
        if (ITEM_REPLACEMENTS.containsKey(clean)) return arrayOf(ITEM_REPLACEMENTS[clean]!!, "1")

        val id = ItemsDb.idFor(clean)
        if (id != null && !id.startsWith("STARRED_")) return arrayOf(id, "1")

        return arrayOf("false", "Could not find item ID for line \"$clean\"")
    }

    @JvmStatic
    fun parseRewards(fullTooltip: List<String>, errorOut: Array<String?>?): ChestInfo? {
        var costIdx = -1
        for (i in fullTooltip.indices) {
            if (strip(fullTooltip[i]).contains("Cost")) {
                costIdx = i
                break
            }
        }
        if (costIdx < 0) {
            if (errorOut != null) errorOut[0] = "Could not find Cost line"
            return null
        }

        val info = ChestInfo()
        val lootEnd = costIdx - 1
        for (ix in 2 until lootEnd) {
            val line = fullTooltip[ix]
            val clean = strip(line).trim()
            if (clean.isEmpty()) continue

            val result = parseLine(line)
            if (result[0] == "false") {
                if (errorOut != null) errorOut[0] = result[1]
                fishmod.utils.debug.Debug.LOGGER.debug("[Loot] unresolved reward line: '{}'", clean)
                continue
            }

            val ri = RewardItem()
            ri.id = result[0]
            ri.qty = result[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            ri.displayName = clean
            fishmod.utils.debug.Debug.LOGGER.debug("[Loot] reward '{}' -> id={} qty={}", clean, ri.id, ri.qty)
            info.items.add(ri)
        }
        return info
    }

    class ChestInfo {
        @JvmField
        val items: MutableList<RewardItem> = ArrayList()
    }

    class RewardItem {
        @JvmField
        var id: String? = null
        @JvmField
        var qty: Int = 0
        @JvmField
        var displayName: String? = null
    }
}

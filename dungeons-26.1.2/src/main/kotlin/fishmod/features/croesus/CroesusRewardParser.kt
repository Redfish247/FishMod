package fishmod.features.croesus

import fishmod.utils.SkyblockItems
import java.util.regex.Pattern

/**
 * Parses a Croesus reward-chest tooltip into item ids/quantities/display names, one [RewardItem]
 * per reward line up to the "Cost" line. Ported from FishModAddons' ItemParser (via AutoCroesus,
 * UnclaimedBloom6, used with permission); item-id resolution uses [SkyblockItems.idFor] instead.
 */
object CroesusRewardParser {
    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

    private val ULTIMATE_ENCHANTS: Set<String> = setOf(
        "Bank", "Bobbin Time", "Chimera", "Combo", "Duplex", "Fatal Tempo", "Flash",
        "Habanero Tactics", "Inferno", "Last Stand", "Legion", "No Pain No Gain", "One For All",
        "Rend", "Soul Eater", "Swarm", "The One", "Ultimate Jerry", "Ultimate Wise", "Wisdom"
    )
    private val ITEM_REPLACEMENTS: MutableMap<String, String> = HashMap()
    private val BOOK_PATTERN: Pattern = Pattern.compile("Enchanted Book \\((?:§.)*([\\w' ]+?) ((?:[IVX]+|\\d+))(?:§.)*\\)")
    private val ESSENCE_PATTERN: Pattern = Pattern.compile("^(\\w+) Essence x(\\d+)$")
    private val ROMAN_VALUES: MutableMap<Char, Int> = HashMap()

    init {
        ITEM_REPLACEMENTS["Shiny Wither Boots"] = "WITHER_BOOTS"
        ITEM_REPLACEMENTS["Shiny Wither Leggings"] = "WITHER_LEGGINGS"
        ITEM_REPLACEMENTS["Shiny Wither Chestplate"] = "WITHER_CHESTPLATE"
        ITEM_REPLACEMENTS["Shiny Wither Helmet"] = "WITHER_HELMET"
        ITEM_REPLACEMENTS["Shiny Necron's Handle"] = "NECRON_HANDLE"
        ITEM_REPLACEMENTS["Wither Shard"] = "SHARD_WITHER"
        ITEM_REPLACEMENTS["Thorn Shard"] = "SHARD_THORN"
        ITEM_REPLACEMENTS["Apex Dragon Shard"] = "SHARD_APEX_DRAGON"
        ITEM_REPLACEMENTS["Power Dragon Shard"] = "SHARD_POWER_DRAGON"
        ITEM_REPLACEMENTS["Scarf Shard"] = "SHARD_SCARF"
        ITEM_REPLACEMENTS["Necron Dye"] = "DYE_NECRON"
        ITEM_REPLACEMENTS["Livid Dye"] = "DYE_LIVID"
        ROMAN_VALUES['I'] = 1
        ROMAN_VALUES['V'] = 5
        ROMAN_VALUES['X'] = 10
        ROMAN_VALUES['L'] = 50
        ROMAN_VALUES['C'] = 100
        ROMAN_VALUES['D'] = 500
        ROMAN_VALUES['M'] = 1000
    }

    @JvmStatic
    fun decodeRoman(s: String): Int {
        var sum = 0
        var i = 0
        while (i < s.length) {
            val curr = ROMAN_VALUES.getOrDefault(s[i], 0)
            val next = if (i < s.length - 1) ROMAN_VALUES.getOrDefault(s[i + 1], 0) else 0
            if (curr < next) {
                sum += next - curr
                i++
            } else {
                sum += curr
            }
            i++
        }
        return sum
    }

    private fun strip(s: String): String = COLOR_STRIP.matcher(s).replaceAll("")

    private fun tryParseBook(line: String): Array<String>? {
        val m = BOOK_PATTERN.matcher(line)
        if (!m.find()) return null
        val bookName = strip(m.group(1).trim()).trim()
        val tierStr = m.group(2).trim()
        val isUltimate = ULTIMATE_ENCHANTS.contains(bookName)

        val tier: Int = try {
            tierStr.toInt()
        } catch (e: NumberFormatException) {
            decodeRoman(tierStr)
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

    /** Returns {id, qty}, or {"false", errorMessage} when the line couldn't be resolved. */
    @JvmStatic
    fun parseLine(line: String): Array<String> {
        val book = tryParseBook(line)
        if (book != null) return book

        val clean = strip(line).trim()
        val essence = tryParseEssence(clean)
        if (essence != null) return essence
        if (ITEM_REPLACEMENTS.containsKey(clean)) return arrayOf(ITEM_REPLACEMENTS[clean]!!, "1")

        val id = SkyblockItems.idFor(clean)
        if (id != null && !id.startsWith("STARRED_")) return arrayOf(id, "1")

        return arrayOf("false", "Could not find item ID for line \"$clean\"")
    }

    /** Returns null if no "Cost" line was found (e.g. container still loading). */
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
                // Skip unresolved lines rather than discarding the whole chest's rewards.
                if (errorOut != null) errorOut[0] = result[1]
                continue
            }

            val ri = RewardItem()
            ri.id = result[0]
            ri.qty = result[1].toInt()
            ri.displayName = line.replace(Regex("^§5§o"), "").trim()
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

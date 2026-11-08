package fishmod.features.fishing

import fishmod.pv.price.ItemPricer

// Rare drop lines adapted from Feesh (Sleepy-Panda, Apache-2.0); see NOTICE. ALL STRINGS UNVERIFIED IN GAME.
// Feesh matched these on §-formatted text; ours match the colour-stripped chat line instead.
enum class RareDropKind { ITEM, BOOK, PET, DYE, PHOENIX }

class RareDropInfo(val name: String, val id: String, val extreme: Boolean)

class RareDrop(
    val kind: RareDropKind,
    val itemName: String,
    // SkyBlock id when known, else null
    val itemId: String?,
    val magicFind: Int,
    // ItemPricer price, 0 when unknown
    val price: Double,
    val extreme: Boolean,
    val petRarity: SeaRarity?,
    val raw: String,
)

object RareDrops {
    private fun d(name: String, id: String, extreme: Boolean = false) = RareDropInfo(name, id, extreme)

    @JvmField val ITEMS: List<RareDropInfo> = listOf(
        d("Lucky Clover Core", "PET_ITEM_LUCKY_CLOVER_DROP"), d("Deep Sea Orb", "DEEP_SEA_ORB"),
        d("Radioactive Vial", "RADIOACTIVE_VIAL", true), d("Magma Core", "MAGMA_CORE"),
        d("Tiki Mask", "TIKI_MASK", true), d("Titanoboa Shed", "TITANOBOA_SHED", true),
        d("Snake Eyes", "SNAKE_EYES", true), d("Octopus Tendril", "OCTOPUS_TENDRIL"),
        d("Troubled Bubble", "TROUBLED_BUBBLE"), d("Scuttler Shell", "SCUTTLER_SHELL"),
        d("Burnt Texts", "BURNT_TEXTS", true), d("Vibrant Coral", "VIBRANT_CORAL"),
        d("True Ice", "HILT_OF_TRUE_ICE"), d("Water Hyacinth", "WATER_HYACINTH"),
        d("Distant Echo", "DISTANT_ECHO"), d("Reinforced Netting", "REINFORCED_NETTING"),
        d("Prince's Crown Jewel", "PRINCE_CROWN_JEWEL"),
    )

    // Enchanted Book names shown as "Enchanted Book (<name>)"
    @JvmField val BOOKS: List<RareDropInfo> = listOf(
        d("Flash 1", "ENCHANTMENT_ULTIMATE_FLASH_1"), d("Flash I", "ENCHANTMENT_ULTIMATE_FLASH_1"),
        d("Pyroclasm 6", "ENCHANTMENT_MAGMARIZER_6"), d("Pyroclasm VI", "ENCHANTMENT_MAGMARIZER_6"),
    )

    // Pet drops with a priceable id; the rarity index is appended (COMMON=0..LEGENDARY=4)
    private val PET_IDS = mapOf(
        "flying fish" to "FLYING_FISH", "megalodon" to "MEGALODON", "squid" to "SQUID", "phoenix" to "PHOENIX",
    )

    @JvmField val DYES: List<RareDropInfo> = listOf(
        d("Carmine Dye", "DYE_CARMINE"), d("Midnight Dye", "DYE_MIDNIGHT"), d("Aquamarine Dye", "DYE_AQUAMARINE"),
        d("Iceberg Dye", "DYE_ICEBERG"), d("Treasure Dye", "DYE_TREASURE"), d("Periwinkle Dye", "DYE_PERIWINKLE"),
        d("Bone Dye", "DYE_BONE"),
    )

    private const val RARE = "RARE DROP! "
    private const val PET = "PET DROP! "

    private val ITEM_RE = Regex("""^RARE DROP! (.+?)(?: \(\+(\d+) \S+ Magic Find\))?$""")
    private val BOOK_RE = Regex("""^Enchanted Book \((.+?)\)$""")
    private val PET_RE = Regex("""^PET DROP! (?:(LEGENDARY|EPIC|RARE|UNCOMMON|COMMON) )?(.+?)(?: \(\+(\d+) \S+ Magic Find\))?$""")
    private val DYE_RE = Regex("""^WOW! (.+?) found (?:a|an) (.+?)(?: #\d+)?!.*$""")
    private val PHOENIX_RE = Regex("""^Wow! (.+?) found a Phoenix pet!.*$""")

    private val ITEM_BY_NAME = ITEMS.associateBy { it.name }
    private val BOOK_BY_NAME = BOOKS.associateBy { it.name }
    private val DYE_BY_NAME = DYES.associateBy { it.name }

    // Parses a colour-stripped chat line; null when not a rare drop. ownName gates dye/phoenix lines.
    @JvmStatic fun parse(plain: String, ownName: String?): RareDrop? {
        val c = plain.firstOrNull() ?: return null
        when (c) {
            'R' -> if (plain.startsWith(RARE)) return parseItem(plain)
            'P' -> if (plain.startsWith(PET)) return parsePet(plain)
            'W' -> if (plain.startsWith("WOW! ")) return parseDye(plain, ownName)
            else -> {}
        }
        if (c == 'W' && plain.startsWith("Wow! ")) return parsePhoenix(plain, ownName)
        return null
    }

    private fun parseItem(plain: String): RareDrop? {
        val m = ITEM_RE.matchEntire(plain) ?: return null
        val item = m.groupValues[1]
        val mf = m.groupValues[2].toIntOrNull() ?: 0
        val book = BOOK_RE.matchEntire(item)
        if (book != null) {
            val info = BOOK_BY_NAME[book.groupValues[1]]
            return RareDrop(RareDropKind.BOOK, book.groupValues[1], info?.id, mf, price(info?.id), false, null, plain)
        }
        val info = ITEM_BY_NAME[item]
        return RareDrop(RareDropKind.ITEM, item, info?.id, mf, price(info?.id), info?.extreme == true, null, plain)
    }

    private fun parsePet(plain: String): RareDrop? {
        val m = PET_RE.matchEntire(plain) ?: return null
        val rarity = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { SeaRarity.valueOf(it) }
        val pet = m.groupValues[2]
        val base = PET_IDS[pet.lowercase()]
        val id = if (base != null && rarity != null) "$base;${rarity.ordinal}" else null
        return RareDrop(RareDropKind.PET, pet, id, m.groupValues[3].toIntOrNull() ?: 0, price(id), false, rarity, plain)
    }

    private fun parseDye(plain: String, own: String?): RareDrop? {
        val m = DYE_RE.matchEntire(plain) ?: return null
        if (own == null || !m.groupValues[1].contains(own)) return null
        val info = DYE_BY_NAME[m.groupValues[2]]
        return RareDrop(RareDropKind.DYE, m.groupValues[2], info?.id, 0, price(info?.id), false, null, plain)
    }

    private fun parsePhoenix(plain: String, own: String?): RareDrop? {
        val m = PHOENIX_RE.matchEntire(plain) ?: return null
        if (own == null || !m.groupValues[1].contains(own)) return null
        return RareDrop(RareDropKind.PHOENIX, "Phoenix", null, 0, 0.0, true, null, plain)
    }

    // Overridable so unit tests never touch the price caches
    @JvmField var priceFn: (String) -> Double = { ItemPricer.price(it) }

    private fun price(id: String?): Double = if (id == null) 0.0 else priceFn(id)
}

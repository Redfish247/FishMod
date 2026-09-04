package fishmod.utils.networth

object NwConstants {

    // APPLICATION_WORTH: fraction of value retained when each modifier is applied
    @JvmField val ENRICHMENT = 0.5
    @JvmField val FARMING_FOR_DUMMIES = 0.5
    @JvmField val OVERCLOCKER_3000 = 0.9
    @JvmField val GEMSTONE_POWER_SCROLL = 0.5
    @JvmField val WOOD_SINGULARITY = 0.5
    @JvmField val ART_OF_WAR = 0.6
    @JvmField val FUMING_POTATO_BOOK = 0.6
    @JvmField val GEMSTONE_SLOTS = 0.6
    @JvmField val RUNES = 0.6
    @JvmField val TUNED_TRANSMISSION = 0.7
    @JvmField val POCKET_SACK_IN_A_SACK = 0.7
    @JvmField val ESSENCE = 0.75
    @JvmField val SILEX = 0.75
    @JvmField val ART_OF_PEACE = 0.8
    @JvmField val DIVAN_POWDER_COATING = 0.8
    @JvmField val ENCHANTMENT_UPGRADES = 0.8
    @JvmField val JALAPENO_BOOK = 0.8
    @JvmField val MANA_DISINTEGRATOR = 0.8
    @JvmField val RECOMBOBULATOR = 0.8
    @JvmField val THUNDER_IN_A_BOTTLE = 0.8
    @JvmField val ENCHANTMENTS = 0.85
    @JvmField val SHENS_AUCTION_PRICE = 0.85
    @JvmField val BOOSTER = 0.8
    @JvmField val DYE = 0.9
    @JvmField val GEMSTONE_CHAMBERS = 0.9
    @JvmField val ROD_PART = 1.0
    @JvmField val DRILL_PART = 1.0
    @JvmField val ETHERWARP = 1.0
    @JvmField val MASTER_STAR = 1.0
    @JvmField val GEMSTONE = 1.0
    @JvmField val HOT_POTATO_BOOK = 1.0
    @JvmField val NECRON_BLADE_SCROLL = 1.0
    @JvmField val POLARVOID_BOOK = 1.0
    @JvmField val PRESTIGE_ITEM = 1.0
    @JvmField val REFORGE = 1.0
    @JvmField val PET_CANDY = 0.65
    @JvmField val SOULBOUND_PET_SKINS = 0.8
    @JvmField val SOULBOUND_SKINS = 0.8
    @JvmField val PET_ITEM = 1.0

    @JvmField
    val ENCHANTMENTS_WORTH: MutableMap<String, Double> = HashMap<String, Double>().apply {
        put("COUNTER_STRIKE", 0.2)
        put("BIG_BRAIN", 0.35)
        put("ULTIMATE_INFERNO", 0.35)
        put("OVERLOAD", 0.35)
        put("ULTIMATE_SOUL_EATER", 0.35)
        put("ULTIMATE_FATAL_TEMPO", 0.65)
    }

    @JvmField
    val BLOCKED_ENCHANTMENTS: MutableMap<String, Set<String>> = HashMap<String, Set<String>>().apply {
        put("BONE_BOOMERANG", hashSetOf("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"))
        put("DEATH_BOW", hashSetOf("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"))
        put("GARDENING_AXE", hashSetOf("REPLENISH"))
        put("GARDENING_HOE", hashSetOf("REPLENISH"))
        put("ADVANCED_GARDENING_AXE", hashSetOf("REPLENISH"))
        put("ADVANCED_GARDENING_HOE", hashSetOf("REPLENISH"))
    }

    /** IGNORED_ENCHANTMENTS: enchant name -> level to skip exactly. */
    @JvmField
    val IGNORED_ENCHANTMENTS: MutableMap<String, Int> = HashMap<String, Int>().apply {
        put("SCAVENGER", 5)
    }

    @JvmField
    val STACKING_ENCHANTMENTS: Set<String> = hashSetOf(
        "EXPERTISE", "COMPACT", "ABSORB", "CULTIVATING", "CHAMPION", "HECATOMB", "TOXOPHILITE"
    )

    @JvmField
    val IGNORE_SILEX: Set<String> = hashSetOf("PROMISING_SPADE", "PROMISING_AXE")

    // total pet XP required to reach level 100, per rarity
    @JvmField
    val PET_XP_TO_100: Map<String, Double> = mapOf(
        "COMMON" to 5_624_785.0, "UNCOMMON" to 8_644_220.0, "RARE" to 12_626_665.0,
        "EPIC" to 18_608_500.0, "LEGENDARY" to 25_353_230.0, "MYTHIC" to 25_353_230.0,
    )

    // order is load-bearing (drives PET_ITEM_TIER_BOOST)
    @JvmField
    val PET_TIERS: Array<String> = arrayOf(
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC",
        "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE",
    )

    @JvmField
    val BLOCKED_CANDY_REDUCE_PETS: Set<String> = hashSetOf(
        "ENDER_DRAGON", "GOLDEN_DRAGON", "SCATHA", "JADE_DRAGON", "ROSE_DRAGON",
    )

    // pets that go past level 100
    @JvmField
    val PET_SPECIAL_MAX: Map<String, Int> = mapOf(
        "GOLDEN_DRAGON" to 200, "JADE_DRAGON" to 200, "ROSE_DRAGON" to 200,
    )

    @JvmField
    val MASTER_STARS: Array<String> = arrayOf(
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    )

    @JvmField
    val ALLOWED_RECOMBOBULATED_CATEGORIES: Set<String> = hashSetOf(
        "ACCESSORY", "NECKLACE", "GLOVES", "BRACELET", "BELT", "CLOAK", "VACUUM"
    )

    @JvmField
    val ALLOWED_RECOMBOBULATED_IDS: Set<String> = hashSetOf(
        "DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS",
        "FERMENTO_HELMET", "FERMENTO_CHESTPLATE", "FERMENTO_LEGGINGS", "FERMENTO_BOOTS",
        "SHADOW_ASSASSIN_CLOAK", "STARRED_SHADOW_ASSASSIN_CLOAK"
    )

    @JvmField
    val ENRICHMENTS: List<String> = listOf(
        "TALISMAN_ENRICHMENT_CRITICAL_CHANCE", "TALISMAN_ENRICHMENT_CRITICAL_DAMAGE",
        "TALISMAN_ENRICHMENT_DEFENSE", "TALISMAN_ENRICHMENT_HEALTH", "TALISMAN_ENRICHMENT_INTELLIGENCE",
        "TALISMAN_ENRICHMENT_MAGIC_FIND", "TALISMAN_ENRICHMENT_WALK_SPEED", "TALISMAN_ENRICHMENT_STRENGTH",
        "TALISMAN_ENRICHMENT_ATTACK_SPEED", "TALISMAN_ENRICHMENT_FEROCITY", "TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE"
    )

    @JvmField
    val GEMSTONE_SLOT_TYPES: Set<String> = hashSetOf(
        "COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING", "UNIVERSAL", "CHISEL"
    )

    // ENCHANTMENT_UPGRADES: enchant -> {upgradeItem, tier}
    @JvmField
    val ENCHANTMENT_UPGRADE_TIER: MutableMap<String, IntArray> = HashMap()
    @JvmField
    val ENCHANTMENT_UPGRADE_ITEM: MutableMap<String, String> = HashMap()

    private fun put(ench: String, item: String, tier: Int) {
        ENCHANTMENT_UPGRADE_ITEM[ench] = item
        ENCHANTMENT_UPGRADE_TIER[ench] = intArrayOf(tier)
    }

    init {
        put("SCAVENGER", "GOLDEN_BOUNTY", 6)
        put("PESTERMINATOR", "PESTHUNTING_GUIDE", 6)
        put("LUCK_OF_THE_SEA", "GOLD_BOTTLE_CAP", 7)
        put("PISCARY", "TROUBLED_BUBBLE", 7)
        put("FRAIL", "SEVERED_PINCER", 7)
        put("SPIKED_HOOK", "OCTOPUS_TENDRIL", 7)
        put("CHARM", "CHAIN_END_TIMES", 6)
        put("VENOMOUS", "FATEFUL_STINGER", 7)
    }

    // MIDAS_SWORDS: id -> {maxBid, type}
    @JvmField
    val MIDAS_SWORDS: MutableMap<String, Array<Any>> = HashMap<String, Array<Any>>().apply {
        put("MIDAS_SWORD", arrayOf(50_000_000L, "MIDAS_SWORD_50M"))
        put("STARRED_MIDAS_SWORD", arrayOf(250_000_000L, "STARRED_MIDAS_SWORD_250M"))
        put("MIDAS_STAFF", arrayOf(100_000_000L, "MIDAS_STAFF_100M"))
        put("STARRED_MIDAS_STAFF", arrayOf(500_000_000L, "STARRED_MIDAS_STAFF_500M"))
    }

    // REFORGES: reforge modifier -> reforge-stone item id
    @JvmField
    val REFORGES: MutableMap<String, String> = HashMap<String, String>().apply {
        put("stiff", "HARDENED_WOOD")
        put("trashy", "OVERFLOWING_TRASH_CAN")
        put("salty", "SALT_CUBE")
        put("aote_stone", "AOTE_STONE")
        put("blazing", "BLAZEN_SPHERE")
        put("waxed", "BLAZE_WAX")
        put("rooted", "BURROWING_SPORES")
        put("calcified", "CALCIFIED_HEART")
        put("candied", "CANDY_CORN")
        put("perfect", "DIAMOND_ATOM")
        put("fleet", "DIAMONITE")
        put("fabled", "DRAGON_CLAW")
        put("spiked", "DRAGON_SCALE")
        put("royal", "DWARVEN_TREASURE")
        put("warped", "ENDSTONE_GEODE")
        put("coldfusion", "ENTROPY_SUPPRESSOR")
        put("blooming", "FLOWERING_BOUQUET")
        put("fanged", "FULL_JAW_FANGING_KIT")
        put("jaded", "JADERALD")
        put("jerry_stone", "JERRY_STONE")
        put("magnetic", "LAPIS_CRYSTAL")
        put("earthy", "LARGE_WALNUT")
        put("groovy", "MANGROVE_GEM")
        put("fortified", "METEOR_SHARD")
        put("gilded", "MIDAS_JEWEL")
        put("cubic", "MOLTEN_CUBE")
        put("moonglade", "MOONGLADE_JEWEL")
        put("lunar", "MOONSTONE")
        put("necrotic", "NECROMANCER_BROOCH")
        put("fruitful", "ONYX")
        put("precise", "OPTICAL_LENS")
        put("mossy", "OVERGROWN_GRASS")
        put("pitchin", "PITCHIN_KOI")
        put("undead", "PREMIUM_FLESH")
        put("blood_soaked", "PRESUMED_GALLON_OF_RED_PAINT")
        put("mithraic", "PURE_MITHRIL")
        put("reinforced", "RARE_DIAMOND")
        put("ridiculous", "RED_NOSE")
        put("loving", "RED_SCARF")
        put("auspicious", "ROCK_GEMSTONE")
        put("treacherous", "RUSTY_ANCHOR")
        put("headstrong", "SALMON_OPAL")
        put("strengthened", "SEARING_STONE")
        put("glistening", "SHINY_PRISM")
        put("bustling", "SKYMART_BROCHURE")
        put("spiritual", "SPIRIT_DECOY")
        put("squeaky", "SQUEAKY_TOY")
        put("sunny", "SUNSTONE")
        put("suspicious", "SUSPICIOUS_VIAL")
        put("snowy", "TERRY_SNOWGLOBE")
        put("dimensional", "TITANIUM_TESSERACT")
        put("ambered", "AMBER_MATERIAL")
        put("beady", "BEADY_EYES")
        put("blessed", "BLESSED_FRUIT")
        put("bulky", "BULKY_STONE")
        put("buzzing", "CLIPPED_WINGS")
        put("erudite", "DAEDALUS_NOTES")
        put("submerged", "DEEP_SEA_ORB")
        put("renowned", "DRAGON_HORN")
        put("festive", "FROZEN_BAUBLE")
        put("giant", "GIANT_TOOTH")
        put("lustrous", "GLEAMING_CRYSTAL")
        put("bountiful", "GOLDEN_BALL")
        put("chomp", "KUUDRA_MANDIBLE")
        put("lucky", "LUCKY_DICE")
        put("mantid", "MANTID_CLAW")
        put("stellar", "PETRIFIED_STARFALL")
        put("scraped", "POCKET_ICEBERG")
        put("ancient", "PRECURSOR_GEAR")
        put("refined", "REFINED_AMBER")
        put("empowered", "SADAN_BROOCH")
        put("withered", "WITHER_BLOOD")
        put("glacial", "FRIGID_HUSK")
        put("heated", "HOT_STUFF")
        put("blood_shot", "SHRIVELED_CORNEA")
        put("dirty", "DIRT_BOTTLE")
        put("moil", "MOIL_LOG")
        put("toil", "TOIL_LOG")
        put("greater_spook", "BOO_STONE")
    }

    // PRESTIGES: item id -> list of prestige item ids (in order)
    @JvmField
    val PRESTIGES: MutableMap<String, Array<String>> = HashMap()

    init {
        val families = arrayOf("CRIMSON", "TERROR", "FERVOR", "HOLLOW", "AURORA")
        val pieces = arrayOf("CHESTPLATE", "HELMET", "LEGGINGS", "BOOTS")
        // ordered prestige tiers (low -> high)
        val tiers = arrayOf("", "HOT_", "BURNING_", "FIERY_", "INFERNAL_")
        for (fam in families) {
            for (piece in pieces) {
                // for each tier above base, list lower tiers in descending order
                for (t in 1 until tiers.size) {
                    val key = tiers[t] + fam + "_" + piece
                    val lowers = ArrayList<String>()
                    for (j in t - 1 downTo 0) {
                        lowers.add(tiers[j] + fam + "_" + piece)
                    }
                    PRESTIGES[key] = lowers.toTypedArray()
                }
            }
        }
    }
}

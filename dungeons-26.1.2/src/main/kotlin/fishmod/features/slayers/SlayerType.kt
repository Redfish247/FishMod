package fishmod.features.slayers

import fishmod.utils.Location
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.animal.wolf.Wolf

/**
 * The five combat Slayer types Hypixel SkyBlock supports (Vampire/Bloodfiend is intentionally out of
 * scope — it has no spawn bar and a different fight model).
 *
 * Boss / miniboss nametag strings and the per-tier boss Slayer-XP table are lifted from the values
 * every SkyBlock mod uses (SkyHanni `SlayerType`, SkyCrypt) and have been stable for years. The XP
 * table is only used to attribute XP *gained* per completed quest (Feature 4) — the *spawn*
 * requirement (Feature 3) is always read live from the scoreboard, never from a constant.
 *
 * @property displayName the scoreboard "Slayer Quest" category label, minus the tier (e.g. "Revenant Horror").
 * @property bossNames every nametag the main boss can carry across tiers (T5 renames included).
 * @property miniBosses nametag substrings of the adds that count as minibosses for this type.
 * @property island the SkyBlock island this slayer is fought on, for area gating.
 * @property mobClass the vanilla entity class the boss is (used to bind the boss entity reference).
 * @property bossXpByTier boss Slayer XP granted for one completed quest, keyed by tier (1-5).
 */
enum class SlayerType(
    val displayName: String,
    val bossNames: Set<String>,
    val miniBosses: Set<String>,
    val island: Location,
    val mobClass: Class<out LivingEntity>,
    val bossXpByTier: Map<Int, Int>,
) {
    REVENANT(
        "Revenant Horror",
        setOf("Revenant Horror", "Atoned Horror"),
        setOf("Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant"),
        Location.HUB,
        Zombie::class.java,
        mapOf(1 to 5, 2 to 25, 3 to 100, 4 to 500, 5 to 1500),
    ),
    TARANTULA(
        "Tarantula Broodfather",
        setOf("Tarantula Broodfather"),
        setOf("Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula", "Primordial Jockey", "Primordial Viscount"),
        Location.SPIDERS_DEN,
        Spider::class.java,
        mapOf(1 to 5, 2 to 25, 3 to 100, 4 to 500),
    ),
    SVEN(
        "Sven Packmaster",
        setOf("Sven Packmaster"),
        setOf("Pack Enforcer", "Sven Follower", "Sven Alpha"),
        Location.THE_PARK,
        Wolf::class.java,
        mapOf(1 to 5, 2 to 25, 3 to 100, 4 to 500),
    ),
    VOIDGLOOM(
        "Voidgloom Seraph",
        setOf("Voidgloom Seraph"),
        setOf("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac"),
        Location.THE_END,
        EnderMan::class.java,
        mapOf(1 to 5, 2 to 25, 3 to 100, 4 to 500),
    ),
    INFERNO(
        "Inferno Demonlord",
        setOf("Inferno Demonlord"),
        setOf("Flare Demon", "Kindleheart Demon", "Burningsoul Demon"),
        Location.CRIMSON_ISLE,
        Blaze::class.java,
        mapOf(1 to 5, 2 to 25, 3 to 100, 4 to 500),
    );

    fun bossXp(tier: Int): Int = bossXpByTier[tier] ?: 0

    /** Nametag of the boss, unqualified, for alerts (its primary name). */
    val bossLabel: String get() = bossNames.first()

    companion object {
        /**
         * Parses a scoreboard "Slayer Quest" category line such as `Revenant Horror IV` into
         * `(type, tier)`. Returns null when the line isn't a known slayer category.
         */
        @JvmStatic
        fun parseCategory(line: String): Pair<SlayerType, Int>? {
            val t = entries.firstOrNull { line.startsWith(it.displayName) } ?: return null
            val tierStr = line.removePrefix(t.displayName).trim()
            val tier = romanToInt(tierStr).takeIf { it in 1..5 } ?: return null
            return t to tier
        }

        /** True when [name] (color-stripped nametag text) contains any type's miniboss name. */
        @JvmStatic
        fun miniBossTypeFor(name: String): SlayerType? =
            entries.firstOrNull { type -> type.miniBosses.any { name.contains(it) } }

        /** True when [name] contains this or any boss's primary/renamed nametag. */
        @JvmStatic
        fun bossTypeFor(name: String): SlayerType? =
            entries.firstOrNull { type -> type.bossNames.any { name.contains(it) } }

        private fun romanToInt(roman: String): Int {
            if (roman.isEmpty()) return 0
            var sum = 0
            var prev = 0
            for (i in roman.indices.reversed()) {
                val v = when (roman[i]) {
                    'I' -> 1; 'V' -> 5; 'X' -> 10; 'L' -> 50; 'C' -> 100; 'D' -> 500; 'M' -> 1000
                    else -> return 0
                }
                if (v < prev) sum -= v else sum += v
                prev = v
            }
            return sum
        }
    }
}

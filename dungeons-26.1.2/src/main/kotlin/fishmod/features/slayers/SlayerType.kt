package fishmod.features.slayers

import fishmod.utils.Location
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.animal.wolf.Wolf

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

    val bossLabel: String get() = bossNames.first()

    companion object {
        @JvmStatic
        fun parseCategory(line: String): Pair<SlayerType, Int>? {
            val t = entries.firstOrNull { line.startsWith(it.displayName) } ?: return null
            val tierStr = line.removePrefix(t.displayName).trim()
            val tier = fishmod.utils.data.Roman.toInt(tierStr).takeIf { it in 1..5 } ?: return null
            return t to tier
        }
    }
}

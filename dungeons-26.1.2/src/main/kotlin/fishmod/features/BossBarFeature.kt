package fishmod.features

import fishmod.mixin.accessors.LerpingBossEventAccessor
import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.dungeon.Phase
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Boss-bar health number. The mixin wraps the boss-bar name; here we append " - <cur>/<max>❤" for
 * the bosses whose max HP is known. Uses the boss bar's target percent (snappy, not the lerped fill).
 */
object BossBarFeature {

    @JvmStatic
    fun appendHealth(instance: LerpingBossEvent, name: Component): Component {
        if (!Dungeons.bossHealthNumbers || !Location.inDungeon()) return name
        val maxHealth = getMaxHealth(name) ?: return name

        val percent = (instance as LerpingBossEventAccessor).targetPercent
        val currentHealth = (percent * maxHealth).roundToInt().toFloat()

        return name.copy().append(
            Component.literal(" §r§8- §a${formatHealth(currentHealth)}§7/§a${formatHealth(maxHealth)}§c❤")
        )
    }

    private fun getMaxHealth(nameComponent: Component): Float? {
        val name = nameComponent.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()
        val floor = Phase.getFloor()
        val master = floor?.startsWith("M", ignoreCase = true) == true
        val floorNum = floor?.filter { it.isDigit() }?.toFloatOrNull() ?: 0f

        return when (name) {
            "The Watcher" -> 12f + floorNum
            "Thorn" -> if (master) 6f else 4f
            "Maxor" -> if (master) 800_000_000f else 100_000_000f
            "Storm" -> if (master) 1_000_000_000f else 400_000_000f
            "Goldor" -> if (master) 1_200_000_000f else 750_000_000f
            "Necron" -> if (master) 1_400_000_000f else 1_000_000_000f
            else -> null
        }
    }

    private fun formatHealth(health: Float): String = when {
        health >= 1_000_000_000 -> {
            val h = health / 1_000_000_000f
            if (h % 1f == 0f) "${h.toInt()}B" else String.format(Locale.US, "%.1fB", h)
        }
        health >= 1_000_000 -> "${(health / 1_000_000f).toInt()}M"
        health >= 1000 -> "${(health / 1000f).toInt()}k"
        else -> health.toInt().toString()
    }
}

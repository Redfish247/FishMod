package fishmod.features.other

import fishmod.utils.FishMsg
import fishmod.utils.Misc
import fishmod.utils.data.ItemUtil
import net.minecraft.client.Minecraft

/**
 * Odin-style "get from sacks" helper — `/fm twap [n]` and friends. Counts how many of an
 * item you already carry, then runs Hypixel's `/gfs <id> <shortfall>` to top you up to [n].
 * Mirrors Odin's `/od twap` (MainCommand.kt -> fillItemFromSack).
 */
object SackFill {

    private class Sack(val id: String, val default: Int, val label: String)

    private val SACKS = mapOf(
        "ep"   to Sack("ENDER_PEARL", 16, "Ender Pearls"),
        "ij"   to Sack("INFLATABLE_JERRY", 64, "Inflatable Jerry"),
        "sl"   to Sack("SPIRIT_LEAP", 16, "Spirit Leaps"),
        "sb"   to Sack("SUPERBOOM_TNT", 64, "Superboom TNT"),
        "dd"   to Sack("DUNGEON_DECOY", 64, "Dungeon Decoys"),
        "tap"  to Sack("TOXIC_ARROW_POISON", 64, "Toxic Arrow Poison"),
        "twap" to Sack("TWILIGHT_ARROW_POISON", 64, "Twilight Arrow Poison"),
    )

    val aliases: Set<String> get() = SACKS.keys

    @JvmStatic
    fun fill(alias: String, amount: Int?) {
        val sack = SACKS[alias] ?: return
        val target = (amount ?: sack.default).coerceIn(1, 9999)
        val player = Minecraft.getInstance().player ?: return

        var have = 0
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (!stack.isEmpty && sack.id.equals(ItemUtil.getId(stack), ignoreCase = true)) have += stack.count
        }

        if (have >= target) {
            FishMsg.send("§7Already have §a$have§7 ${sack.label} §8(target $target)")
            return
        }
        Misc.executeCommand("gfs ${sack.id} ${target - have}")
    }
}

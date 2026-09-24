package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object ExplosiveShot {

    private val PATTERN: Pattern = Pattern.compile(
        "Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,]+(?:\\.\\d+)?) damage"
    )

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text -> onMessage(text) }
    }

    private fun onMessage(text: Component?): Boolean {
        if (text == null) return false
        val s = text.string ?: return false

        if (!FishSettings.explosiveShotEnabled) return false
        if (s.indexOf("Explosive Shot") < 0) return false

        val m = PATTERN.matcher(s)
        if (!m.find()) return false

        val enemies: Int
        val total: Double
        try {
            enemies = m.group(1).toInt()
            total = m.group(2).replace(",", "").toDouble()
        } catch (e: NumberFormatException) {
            return false
        }
        if (enemies <= 0) return false

        val perEnemy = total / enemies
        val dmg = formatDamage(perEnemy)

        val mc = Minecraft.getInstance()
        // chat line shows on every Explosive Shot; title + party announce stay P1-only
        if (FishSettings.explosiveShotChatMessage) {
            val chatLine = Component.literal(
                "§7[Explosive Shot] §f$dmg §7dmg per " + (if (enemies == 1) "enemy" else "enemies") + " §8(" + enemies + ")"
            )
            mc.execute { mc.player?.sendSystemMessage(chatLine) }
        }

        if (!Phase.inP1()) return false

        if (FishSettings.explosiveShotShowTitle) {
            val title = Component.literal(dmg).withStyle(ChatFormatting.RED)
            val subtitle = Component.literal(
                "§7Explosive Shot §8• §f" + enemies + (if (enemies == 1) " enemy" else " enemies")
            )
            mc.execute {
                val hud = mc.gui
                hud.setTimes(0, 25, 8)
                hud.setTitle(title)
                hud.setSubtitle(subtitle)
            }
        }

        if (FishSettings.explosiveShotAnnounceParty && DungeonClass.isClass(DungeonClass.ARCHER)) {
            announceToParty(dmg, enemies)
        }
        return false
    }

    private fun announceToParty(dmg: String, enemies: Int) {
        val message = "Explosive Shot: $dmg dmg per enemy (" + enemies + (if (enemies == 1) " enemy)" else " enemies)")
        fishmod.utils.ChatQueue.enqueue("pc $message")
    }

    private fun formatDamage(v: Double): String {
        return when {
            v >= 1_000_000_000_000.0 -> String.format("%.1fT", v / 1_000_000_000_000.0).replace(".0", "")
            v >= 1_000_000_000.0     -> String.format("%.1fB", v / 1_000_000_000.0).replace(".0", "")
            v >= 1_000_000.0         -> String.format("%.1fM", v / 1_000_000.0).replace(".0", "")
            v >= 1_000.0             -> String.format("%.1fk", v / 1_000.0).replace(".0", "")
            v == Math.floor(v) && !v.isInfinite() -> String.format("%,d", v.toLong())
            else                     -> String.format("%,.1f", v)
        }
    }
}

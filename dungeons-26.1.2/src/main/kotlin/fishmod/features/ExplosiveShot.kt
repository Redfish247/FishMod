package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Parses the Terminator "Explosive Shot" chat line and shows the per-enemy damage as a title.
 * Only active during the F7 Maxor fight ([Phase.inP1]). The chat damage is the TOTAL across all
 * enemies hit, so it's divided by enemy count for the per-target hit. Never cancels the chat line.
 */
object ExplosiveShot {

    // hit N enemy/enemies for D damage  (D may carry thousands commas and a decimal)
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

        // Phase.inP1() rather than matching the boss taunt text — a hand-typed copy drifted out of sync and broke this gate
        if (!FishSettings.explosiveShotEnabled || !Phase.inP1()) return false
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
        val title = Component.literal(dmg).withStyle(ChatFormatting.RED)
        val subtitle = Component.literal(
            "§7Explosive Shot §8• §f" + enemies + (if (enemies == 1) " enemy" else " enemies")
        )

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        val mc = Minecraft.getInstance()
        mc.execute {
            val hud = mc.gui
            hud.setTimes(0, 25, 8) // snappy: no fade-in, ~1.25s hold, quick fade-out
            hud.setTitle(title)
            hud.setSubtitle(subtitle)
        }

        if (FishSettings.explosiveShotAnnounceParty && DungeonClass.isClass(DungeonClass.ARCHER)) {
            announceToParty(dmg, enemies)
        }
        return false // keep the original chat line
    }

    /** Shares the same info shown on screen with the party, throttled via the shared [fishmod.utils.ChatQueue]. */
    private fun announceToParty(dmg: String, enemies: Int) {
        val message = "Explosive Shot: $dmg dmg per enemy(" + enemies + (if (enemies == 1) " enemy)" else " enemies)")
        fishmod.utils.ChatQueue.enqueue("pc $message")
    }

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private fun formatDamage(v: Double): String {
        if (v == Math.floor(v) && !v.isInfinite()) return String.format("%,d", v.toLong())
        return String.format("%,.1f", v)
    }
}

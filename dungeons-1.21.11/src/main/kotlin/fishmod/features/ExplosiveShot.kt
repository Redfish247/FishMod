package fishmod.features

import fishmod.features.dungeon.ChatCommandState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Parses the Terminator "Explosive Shot" chat line and shows the per-enemy damage as a title.
 *
 *   "Your Explosive Shot hit 1 enemy for 472.5 damage."  -> title "472.5", subtitle "Explosive Shot . 1 enemy"
 *   "Your Explosive Shot hit 8 enemies for 4,000 damage." -> title "500",  subtitle "Explosive Shot . 8 enemies"
 *
 * Only active during the F7 Maxor fight ([Phase.inP1]) — Terminator/Explosive Shot readings
 * aren't relevant to any other boss phase.
 *
 * The damage in the message is the TOTAL across all enemies hit; dividing by the enemy count gives
 * the per-target hit. Reads [Events.ON_GAME_MESSAGE] but never cancels (the chat line stays).
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

    private fun onMessage(text: Text?): Boolean {
        if (text == null) return false
        val s = text.string ?: return false

        // Phase.inP1() is the same verified Maxor-phase detection MaxorTickTimer already relies on
        // (driven by splits.json's boss chat lines) — reusing it instead of hand-matching the boss
        // taunt text here directly, since a hand-typed copy of that text previously drifted out of
        // sync with the real line and silently broke this gate (missing "!" after each "WELL").
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
        val title = Text.literal(dmg).formatted(Formatting.RED)
        val subtitle = Text.literal(
            "§7Explosive Shot §8• §f" + enemies + (if (enemies == 1) " enemy" else " enemies")
        )

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        val mc = MinecraftClient.getInstance()
        mc.execute {
            val hud = mc.inGameHud
            hud.setTitleTicks(0, 25, 8) // snappy: no fade-in, ~1.25s hold, quick fade-out
            hud.setTitle(title)
            hud.setSubtitle(subtitle)
        }

        if (FishSettings.explosiveShotAnnounceParty && DungeonClass.isClass(DungeonClass.ARCHER)) {
            announceToParty(dmg, enemies)
        }
        return false // keep the original chat line
    }

    /** Shares the same crit-hit info already shown on screen with the party. Delayed + rate-limit
     *  suppressed the same way `PartyCommandHandler.sendCmd` does. */
    private fun announceToParty(dmg: String, enemies: Int) {
        val message = "Explosive Shot: $dmg dmg (" + enemies + (if (enemies == 1) " enemy)" else " enemies)")
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute {
                MinecraftClient.getInstance().execute {
                    val mc = MinecraftClient.getInstance()
                    if (mc.networkHandler != null) {
                        mc.networkHandler!!.sendChatCommand("pc $message")
                        ChatCommandState.lastPartyCommandAt = System.currentTimeMillis()
                    }
                }
            }
    }

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private fun formatDamage(v: Double): String {
        if (v == Math.floor(v) && !v.isInfinite()) return String.format("%,d", v.toLong())
        return String.format("%,.1f", v)
    }
}

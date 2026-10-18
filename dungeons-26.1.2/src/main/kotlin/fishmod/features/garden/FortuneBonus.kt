package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.FishMsg
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

// Chat message when the Garden's bonus Farming Fortune changes (tab "Bonus:" line) or the game announces a fortune bonus
object FortuneBonus {

    private val CHAT = Regex("""\+?([\d,.]+)\W{0,2}\s*Farming Fortune""", RegexOption.IGNORE_CASE)
    private var last: String? = null
    private var lastChatMs = 0L
    private var tick = 0

    private fun send(bonus: String) {
        val txt = S.gardenFortuneMsgText.replace("{bonus}", bonus)
        FishMsg.send(Garden.legacy(S.gardenFortuneMsgColor) + txt)
    }

    fun init() {
        Events.ON_WORLD_CHANGE.register { last = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !S.gardenFortuneMsg || !Garden.inGarden()) return@register
            if (tick++ % 20 != 0) return@register
            val cur = Mining.tabLine("Bonus:")?.substringAfter(":")?.trim()?.takeIf { it.isNotEmpty() } ?: return@register
            val prev = last
            last = cur
            // First read is the baseline; only changes notify
            if (prev != null && prev != cur) send(cur)
        }
        // Server announcements ("+N Farming Fortune ..."); throttled so the tab change doesn't double up (guess at wording)
        Events.ON_GAME_MESSAGE.register { t ->
            if (S.gardenFortuneMsg && Garden.inGarden()) {
                val s = Garden.strip(t.string).trim()
                val now = System.currentTimeMillis()
                if (!s.startsWith("[") && now - lastChatMs > 3000 && s.contains("bonus", true)) {
                    CHAT.find(s)?.let { lastChatMs = now; send("+" + it.groupValues[1]) }
                }
            }
            false
        }
    }
}

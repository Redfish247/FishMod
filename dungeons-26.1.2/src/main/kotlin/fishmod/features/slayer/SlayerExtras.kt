package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component

// Cocoon burst countdown + Seraph summons recall alert
object SlayerExtras {

    // from SkyHanni (SlayerApi: cocoons burst after 5s)
    private const val COCOON_MS = 5_000L
    // from SkyHanni (SummoningMobManager)
    private val RECALL = Regex("^The Seraph recalled your (\\d+) summoned allies!$")

    private var cocoonAt = 0L
    private var lastShown = -1

    fun init() {
        FishEvents.chat.register { text ->
            if (S.summonsRecallAlert) {
                RECALL.find(text.chatPlain.trim())?.let { m -> title("§c§lSUMMONS RECALLED", "§7Seraph took §f${m.groupValues[1]}§7 allies", 2000) }
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    private fun onTick() {
        if (!S.cocoonTimer) { cocoonAt = 0L; lastShown = -1; return } // stale start time would skew the next cocoon
        val cocooned = SlayerManager.state == SlayerManager.State.COCOONED
        if (!cocooned) { cocoonAt = 0L; lastShown = -1; return }
        val now = System.currentTimeMillis()
        if (cocoonAt == 0L) cocoonAt = now
        val left = ((COCOON_MS - (now - cocoonAt) + 999) / 1000).toInt()
        if (left == lastShown || left < 0) return
        lastShown = left
        if (left == 0) title("§d§lCOCOON BURST", "§7Kill the boss", 1200)
        else title("§d§lCOCOON §f${left}s", "§7Boss bursts out soon", 1100)
    }

    private fun title(main: String, sub: String, ms: Int) {
        try {
            FishUtil.forceTitle(Component.literal(main), Component.literal(sub), ms)
        } catch (e: Exception) {
            FishDiag.fail("SlayerExtras.1", "slayer extras title failed", e)
        }
    }
}

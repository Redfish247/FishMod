package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Auto GFS (ported from NoammAddons' AutoGFS). Every N seconds while in a dungeon, tops up your
 * dungeon consumables from your own sacks with `/gfs` when they run low. Opt-in, default off.
 */
object AutoGFS {

    private data class Refill(val id: String, val gfs: String, val max: Int, val enabled: () -> Boolean)

    private val ITEMS = listOf(
        Refill("ENDER_PEARL", "ender_pearl", 16) { FishSettings.autoGfsPearls },
        Refill("SUPERBOOM_TNT", "superboom_tnt", 64) { FishSettings.autoGfsTnt },
        Refill("SPIRIT_LEAP", "spirit_leap", 16) { FishSettings.autoGfsLeaps },
        Refill("INFLATABLE_JERRY", "inflatable_jerry", 64) { FishSettings.autoGfsJerry },
    )

    private var lastCheck = 0L
    private var lastCommand = 0L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    private fun tick(mc: Minecraft) {
        if (!FishSettings.autoGfsEnabled || !Location.inDungeon()) return
        if (mc.screen != null) return
        val p = mc.player ?: return
        if (p.isDeadOrDying) return

        val now = System.currentTimeMillis()
        if (now - lastCheck < FishSettings.autoGfsDelaySec.coerceIn(5, 60) * 1000L) return
        lastCheck = now

        val counts = HashMap<String, Int>()
        for (stack in p.inventory.nonEquipmentItems) {
            val id = ItemUtil.getId(stack) ?: continue
            counts[id] = (counts[id] ?: 0) + stack.count
        }

        for (item in ITEMS) {
            if (!item.enabled()) continue
            val have = counts[item.id] ?: continue   // only refill something you already carry
            val need = item.max - have
            if (need < 4) continue
            if (now - lastCommand < 3000) return
            lastCommand = now
            mc.connection?.sendCommand("gfs ${item.gfs} $need")
            return   // one command per check
        }
    }
}

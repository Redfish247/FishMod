package fishmod.features.storage

import fishmod.utils.HypixelApi
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object StorageAutoLoader {

    private var busy = false
    private var generation = 0

    @JvmStatic fun running(): Boolean = busy

    @JvmStatic fun stop() { busy = false; generation++ }

    @JvmStatic
    fun start() {
        val mc = Minecraft.getInstance()
        if (!FishSettings.storageOverlayEnabled) {
            msg("§c[Storage] Enable the Storage Overlay feature first."); return
        }
        if (mc.player == null) { msg("§c[Storage] Not in a world."); return }
        if (busy) { msg("§e[Storage] Already loading…"); return }

        busy = true
        val gen = ++generation
        msg("§b[Storage] Reading your storage layout from the API…")
        HypixelApi.getStorageLayout(mc) { rows, error ->
            mc.execute {
                if (gen != generation) return@execute
                try {
                    if (rows == null) { msg("§c[Storage] ${error ?: "request failed"}"); return@execute }
                    StorageCache.registerLayout(rows)
                    val ec = rows.keys.count { it < 9 }
                    val bp = rows.keys.count { it >= 9 }
                    msg("§a[Storage] Found $ec ender chest page(s) + $bp backpack(s) — open each to load its items.")
                } finally {
                    busy = false
                }
            }
        }
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal(s))
}

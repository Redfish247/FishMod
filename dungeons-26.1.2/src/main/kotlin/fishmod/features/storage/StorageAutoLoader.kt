package fishmod.features.storage

import fishmod.utils.HypixelApi
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Learns which ender-chest / backpack pages you own (and their sizes) from the Hypixel API, so the
 * storage overlay lists every real page even before you've opened it. Item contents are still
 * captured load-based by [StorageCache] as you page through `/storage`.
 *
 * Trigger: the "Load pages" button in the Storage Viewer, or `/storageload`.
 */
object StorageAutoLoader {

    @Volatile private var busy = false

    @JvmStatic fun init() {}

    @JvmStatic fun running(): Boolean = busy

    @JvmStatic fun stop() { busy = false }

    @JvmStatic
    fun start() {
        val mc = Minecraft.getInstance()
        if (!FishSettings.storageOverlayEnabled) {
            msg("§c[Storage] Enable the Storage Overlay feature first."); return
        }
        if (mc.player == null) { msg("§c[Storage] Not in a world."); return }
        if (busy) { msg("§e[Storage] Already loading…"); return }

        busy = true
        msg("§b[Storage] Reading your storage layout from the API…")
        HypixelApi.getStorageLayout(mc) { rows, error ->
            mc.execute {
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

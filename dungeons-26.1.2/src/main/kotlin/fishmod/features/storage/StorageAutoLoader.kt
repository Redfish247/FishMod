package fishmod.features.storage

import fishmod.utils.HypixelApi
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Manual "load everything" for the storage overlay/viewer — pulls every ender-chest and backpack
 * page from the Hypixel API in one shot (via FishMod's proxy) and caches them, instead of making
 * you open each `/enderchest` / `/backpack` by hand.
 *
 * Trigger: the "Load all pages" button in the Storage Viewer, or `/storageload`.
 */
object StorageAutoLoader {

    @Volatile private var busy = false

    @JvmStatic fun init() {}

    @JvmStatic fun running(): Boolean = busy

    @JvmStatic
    fun stop() { busy = false } // the API call is one request; nothing to truly cancel

    @JvmStatic
    fun start() {
        val mc = Minecraft.getInstance()
        if (!FishSettings.storageOverlayEnabled) {
            msg("§c[Storage] Enable the Storage Overlay feature first."); return
        }
        if (mc.player == null) { msg("§c[Storage] Not in a world."); return }
        if (busy) { msg("§e[Storage] Already loading…"); return }

        busy = true
        msg("§b[Storage] Fetching all pages from the Hypixel API…")
        HypixelApi.getStorage(mc) { pages, error ->
            mc.execute {
                try {
                    if (pages == null) {
                        msg("§c[Storage] ${error ?: "request failed"}")
                        return@execute
                    }
                    StorageCache.ensureLoaded()
                    var n = 0
                    for ((idx, items) in pages) {
                        if (items.any { !it.isEmpty }) { StorageCache.put(idx, items); n++ }
                    }
                    StorageCache.forceSave()
                    msg(
                        if (n == 0) "§e[Storage] API returned no filled pages." + (error?.let { " §7($it)" } ?: "")
                        else "§a[Storage] Loaded $n page(s) from the API."
                    )
                } finally {
                    busy = false
                }
            }
        }
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal(s))
}

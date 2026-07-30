package fishmod.cosmetic

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

/**
 * Single combined poller for shared cosmetics (nicks + item customs). Replaces the two separate
 * ~30s refreshes that [RemoteNicks] and [RemoteItems] used to run with one version-gated
 * `/sync` request every ~5s.
 *
 * The worker keeps a global change-version that bumps on any write. We send our last-seen version;
 * when nothing has changed the worker returns just the version (a single cached row read — no table
 * reads, no second request), and we do nothing. Only when the version moved (someone changed a nick
 * or an item) does it return the full nick/item maps for the players we asked about. The upshot:
 * updates land in ~5s instead of ~30s, while Cloudflare/D1 usage drops (one request instead of two,
 * and almost all of them are tiny "nothing changed" replies).
 *
 * We force a full fetch (since = -1) whenever new players appear in the tab list, since a roster
 * change does not bump the version but we still need those newcomers' cosmetics.
 */
object RemoteSync {

    private const val BASE_TICKS = 20 * 5   // 5s — poll spacing while things are changing
    private const val MAX_TICKS = 20 * 10   // 10s — backed-off spacing when nothing changes
    private const val STEP_TICKS = 20 * 5   // grow 5s per idle (unchanged) poll

    private var tick = 0
    private var interval = BASE_TICKS       // current poll spacing (adaptive)
    @Volatile
    private var version: Long = -1          // last server version we've applied
    private var lastUuids: Set<String> = setOf() // uuids covered by the last successful sync
    private var lastTabSize = 0             // tab-list size at last (re)sync, for cheap growth detection

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            reset()
            refresh()
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Cheap O(1) check: if players just appeared in the tab list, snap to fast and poll now —
            // a roster change doesn't move the server version, so we must fetch the newcomers eagerly.
            val size = tabSize()
            if (size != lastTabSize) {
                val grew = size > lastTabSize
                lastTabSize = size
                if (grew) {
                    interval = BASE_TICKS
                    tick = interval
                } // fire on the next tick
            }
            tick++
            if (tick < interval) return@register
            tick = 0
            refresh()
        }
    }

    private fun reset() {
        tick = 0
        interval = BASE_TICKS
        version = -1
        lastUuids = setOf()
        lastTabSize = 0
    }

    private fun tabSize(): Int {
        val mc = Minecraft.getInstance()
        return if (mc.connection == null) 0 else mc.connection!!.onlinePlayers.size
    }

    /** Force an immediate poll that ignores the cached version (used by debug commands / toggles). */
    @JvmStatic
    fun forceSync() {
        version = -1
        interval = BASE_TICKS
        tick = 0
        refresh()
    }

    private fun refresh() {
        val nicksOn = FishSettings.remoteNicksEnabled
        val itemsOn = FishSettings.remoteItemsEnabled
        val sizeOn = FishSettings.playerSizeShared
        if (!nicksOn) RemoteNicks.clearAll()
        if (!itemsOn) RemoteItems.clearAll()
        if (!sizeOn) RemoteScales.clearAll()
        if (!nicksOn && !itemsOn && !sizeOn) return

        val mc = Minecraft.getInstance()
        if (mc.connection == null || mc.player == null) return
        val selfUuid = mc.player!!.getUUID().toString().replace("-", "")
        val uuidToName = HashMap<String, String>()
        for (entry in mc.connection!!.onlinePlayers) {
            val gp = entry.profile ?: continue
            if (gp.id() == null) continue
            val name = gp.name()
            if (name == null || name.isEmpty()) continue
            val u = gp.id().toString().replace("-", "")
            if (u == selfUuid) continue
            uuidToName[u] = name
        }
        if (uuidToName.isEmpty()) return

        // New players in the tab list won't have moved the version, so force a full fetch for them.
        val newPlayers = !lastUuids.containsAll(uuidToName.keys)
        val since = if (newPlayers) -1L else version
        val keys: Set<String> = HashSet(uuidToName.keys)

        HypixelApi.fetchSync(uuidToName.keys, since) { ver, nicks, items, scales ->
            mc.execute {
                version = ver
                lastUuids = keys
                lastTabSize = tabSize()
                val changed = nicks != null || items != null || scales != null // server only sends maps when something moved
                // Back off when idle (most of the time), snap back to fast on any change. Keeps updates
                // near-instant during activity while collapsing steady-state requests ~6x.
                interval = if (changed) BASE_TICKS else minOf(interval + STEP_TICKS, MAX_TICKS)
                if (nicks != null && FishSettings.remoteNicksEnabled) RemoteNicks.acceptNicks(uuidToName, nicks)
                if (items != null && FishSettings.remoteItemsEnabled) RemoteItems.acceptItems(keys, items)
                if (scales != null && FishSettings.playerSizeShared) RemoteScales.acceptScales(keys, scales)
            }
        }
    }
}

package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.utils.debug.FishDiag
import net.minecraft.client.multiplayer.PlayerInfo

object TabListCache {

    private const val SCAN_INTERVAL_TICKS = 5
    private var tickCounter = 0

    class Entry(@JvmField val info: PlayerInfo, @JvmField val stripped: String)

    @JvmStatic
    var version: Int = 0
        private set

    @JvmStatic
    var entries: List<Entry> = emptyList()
        private set

    private var lastSig = 0
    private var lastIdentity = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try {
                tick(mc)
            } catch (e: Exception) {
                FishDiag.fail("TabListCache.1", "tab list scan failed (${entries.size} cached)", e)
            }
        })
    }

    private fun tick(mc: Minecraft) {
        if (mc.player == null || mc.connection == null) {
            if (entries.isNotEmpty()) {
                entries = emptyList()
                version++
            }
            return
        }
        if (tickCounter++ % SCAN_INTERVAL_TICKS != 0) return
        scan(mc)
    }

    @JvmStatic
    fun forceScan() {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) return
        scan(mc)
    }

    private fun scan(mc: Minecraft) {
        // onlinePlayers is unordered; sort like vanilla's tab so widgets read top-to-bottom per column
        val online = mc.connection!!.onlinePlayers.sortedWith(
            compareBy<PlayerInfo> { -it.tabListOrder }.thenBy { it.team?.name ?: "" }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.name })
        var identity = online.size
        for (info in online) {
            identity = identity * 31 + System.identityHashCode(info)
            identity = identity * 31 + System.identityHashCode(info.tabListDisplayName)
        }
        if (identity == lastIdentity && entries.size == online.size) return
        lastIdentity = identity
        var sig = online.size
        val built = ArrayList<Entry>(online.size)
        for (info in online) {
            val raw = info.tabListDisplayName?.string ?: ""
            sig = sig * 31 + raw.hashCode()
            val stripped = if (raw.isEmpty()) "" else Constants.STRIP_COLOR_REGEX.replace(raw, "")
            built.add(Entry(info, stripped))
        }
        if (sig == lastSig) return
        lastSig = sig
        entries = built
        version++
    }
}

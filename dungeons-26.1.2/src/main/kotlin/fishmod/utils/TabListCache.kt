package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
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

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
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
        val online = mc.connection!!.onlinePlayers
        var sig = online.size
        val built = ArrayList<Entry>(online.size)
        for (info in online) {
            val dn = info.tabListDisplayName
            val raw = dn?.string ?: ""
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

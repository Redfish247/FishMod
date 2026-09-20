package fishmod.cosmetic

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

object RemoteSync {

    private const val BASE_TICKS = 20 * 5
    private const val MAX_TICKS = 20 * 10
    private const val STEP_TICKS = 20 * 5

    private var tick = 0
    private var interval = BASE_TICKS
    @Volatile
    private var version: Long = -1
    private var lastUuids: Set<String> = setOf()
    private var lastTabSize = 0

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            reset()
            refresh()
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val size = tabSize()
            if (size != lastTabSize) {
                val grew = size > lastTabSize
                lastTabSize = size
                if (grew) {
                    interval = BASE_TICKS
                    tick = interval
                }
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

    @JvmStatic
    fun forceSync() {
        version = -1
        interval = BASE_TICKS
        tick = 0
        refresh()
    }

    private fun refresh() {
        val nicksOn = FishSettings.remoteNicksEnabled
        val sizeOn = FishSettings.playerSizeShared
        if (!nicksOn) RemoteNicks.clearAll()
        if (!sizeOn) RemoteScales.clearAll()
        if (!nicksOn && !sizeOn) return

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

        val newPlayers = !lastUuids.containsAll(uuidToName.keys)
        val since = if (newPlayers) -1L else version
        val keys: Set<String> = HashSet(uuidToName.keys)

        HypixelApi.fetchSync(uuidToName.keys, since) { ver, nicks, _, scales ->
            mc.execute {
                version = ver
                lastUuids = keys
                lastTabSize = tabSize()
                val changed = nicks != null || scales != null
                interval = if (changed) BASE_TICKS else minOf(interval + STEP_TICKS, MAX_TICKS)
                if (nicks != null && FishSettings.remoteNicksEnabled) RemoteNicks.acceptNicks(uuidToName, nicks)
                if (scales != null && FishSettings.playerSizeShared) RemoteScales.acceptScales(keys, scales)
            }
        }
    }
}

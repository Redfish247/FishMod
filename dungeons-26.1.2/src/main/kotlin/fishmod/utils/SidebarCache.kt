package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.utils.debug.FishDiag
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

object SidebarCache {

    private const val SCAN_INTERVAL_TICKS = 5
    private var tickCounter = 0

    // raw = team prefix+suffix (or owner if no team), sorted by score descending
    class Entry(@JvmField val owner: String, @JvmField val team: PlayerTeam?, @JvmField val score: Int,
                @JvmField val raw: String, @JvmField val plain: String)

    @JvmStatic
    var version: Int = 0
        private set

    @JvmStatic
    var entries: List<Entry> = emptyList()
        private set

    private var lastSig = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try {
                tick(mc)
            } catch (e: Exception) {
                FishDiag.fail("SidebarCache.1", "sidebar scan failed (${entries.size} cached)", e)
            }
        })
    }

    private fun tick(mc: Minecraft) {
        if (mc.level == null) {
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
        if (Minecraft.getInstance().level != null) scan(Minecraft.getInstance())
    }

    private fun scan(mc: Minecraft) {
        val sb = mc.level!!.scoreboard
        val obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR)
        val scores = if (obj == null) emptyList() else sb.listPlayerScores(obj).sortedByDescending { it.value() }
        var sig = scores.size
        val built = ArrayList<Entry>(scores.size)
        for (e in scores) {
            val owner = e.owner()
            val team = sb.getPlayersTeam(owner)
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else owner
            sig = (sig * 31 + raw.hashCode()) * 31 + e.value()
            built.add(Entry(owner, team, e.value(), raw, TextStrip.plain(raw)))
        }
        if (sig == lastSig && entries.size == built.size) return
        lastSig = sig
        entries = built
        version++
    }
}

package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import fishmod.utils.debug.FishDiag
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

object SidebarCache {

    private const val SCAN_INTERVAL_TICKS = 5
    private var tickCounter = 0

    // raw = team prefix+suffix (or owner if no team), sorted by score descending
    class Entry(@JvmField val owner: String, @JvmField val team: PlayerTeam?, @JvmField val score: Int,
                @JvmField val raw: String, @JvmField val plain: String,
                @JvmField val hidden: Boolean, @JvmField val display: Component?)

    @JvmStatic
    var version: Int = 0
        private set

    // sidebar objective title, null when no sidebar is shown
    @JvmStatic
    var title: Component? = null
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
            if (entries.isNotEmpty() || title != null) {
                entries = emptyList()
                title = null
                lastSig = 0
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
        val newTitle = obj?.displayName
        var sig = scores.size * 31 + (newTitle?.string?.hashCode() ?: 0)
        val built = ArrayList<Entry>(scores.size)
        for (e in scores) {
            val owner = e.owner()
            val team = sb.getPlayersTeam(owner)
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else owner
            val hidden = e.isHidden
            val display = e.display()
            sig = ((sig * 31 + raw.hashCode()) * 31 + e.value()) * 31 + (if (hidden) 1 else 0) + (display?.string?.hashCode() ?: 0)
            built.add(Entry(owner, team, e.value(), raw, TextStrip.plain(raw), hidden, display))
        }
        title = newTitle
        if (sig == lastSig && entries.size == built.size) return
        lastSig = sig
        entries = built
        version++
    }
}

package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.FishMsg
import fishmod.utils.Misc
import fishmod.utils.NameList
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.PartyUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.utils.events.Events
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** While you're party leader, auto-kicks any current party member whose name is on [FishSettings.pcKickList]. */
object KickListManager {

    private const val CHECK_INTERVAL_TICKS = 40 // 2s
    private var tickCounter = 0

    private val JOIN = Pattern.compile("^(?:\\[[^]]+]\\s+)?(\\w{1,16}) joined the party\\.$")

    /** lowercased names kicked this party — re-kicked on sight until they leave/world change clears it. */
    private val kicked = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        Events.ON_WORLD_CHANGE.register { kicked.clear(); false }
        // Reacts the instant someone joins (rather than waiting for the next sweep), and — unlike the
        // tab-list lookup below — this is the exact display name Hypixel used, so it works for /nick'd
        // targets that the local tab list may not resolve back to a real account.
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.pcKickListEnabled && FishSettings.pcKickList.isNotBlank()) {
                val stripped = Constants.STRIP_COLOR_REGEX.replace(text.string, "")
                JOIN.matcher(stripped).let { if (it.find()) tryKick(it.group(1)) }
            }
            false
        }
    }

    private fun tryKick(name: String) {
        val mc = Minecraft.getInstance()
        val self = mc.player?.name?.string ?: return
        if (name.equals(self, ignoreCase = true)) return
        if (!NameList.contains(FishSettings.pcKickList, name)) return
        if (!PartyUtil.amLeader()) return
        val key = name.lowercase()
        if (!kicked.add(key)) return
        FishMsg.send("§9Kick List §7> kicking §e$name")
        Scheduler.scheduleTask({ Misc.executeCommand("party kick $name") }, 2)
    }

    private fun onTick() {
        if (!FishSettings.pcKickListEnabled) return
        if (FishSettings.pcKickList.isBlank()) return
        if (++tickCounter < CHECK_INTERVAL_TICKS) return
        tickCounter = 0

        val mc = Minecraft.getInstance()
        val connection = mc.connection ?: return
        if (!PartyUtil.amLeader()) return
        val self = mc.player?.name?.string ?: return

        for (uuid in PartyUtil.getMemberUuids()) {
            val name = connection.getPlayerInfo(uuid)?.profile?.name ?: continue
            if (name.equals(self, ignoreCase = true)) continue
            if (!NameList.contains(FishSettings.pcKickList, name)) continue

            val key = name.lowercase()
            if (!kicked.add(key)) continue
            FishMsg.send("§9Kick List §7> kicking §e$name")
            Scheduler.scheduleTask({ Misc.executeCommand("party kick $name") }, 2)
        }
    }
}

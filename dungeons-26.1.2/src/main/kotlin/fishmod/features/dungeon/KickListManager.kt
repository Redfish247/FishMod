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

object KickListManager {

    private const val CHECK_INTERVAL_TICKS = 40
    private const val KICK_COOLDOWN_MS = 5_000L
    private const val PENDING_TIMEOUT_MS = 5_000L
    private var tickCounter = 0

    private val JOIN = Pattern.compile("^(?:\\[[^]]+]\\s+)?(\\w{1,16}) joined the party\\.$")
    private val PF_JOIN = Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group!")

    private val lastKick = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        Events.ON_WORLD_CHANGE.register { lastKick.clear(); pending.clear(); false }
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.pcKickListEnabled && FishSettings.pcKickList.isNotBlank()) {
                val stripped = Constants.STRIP_COLOR_REGEX.replace(text.string, "")
                val m = PF_JOIN.matcher(stripped).takeIf { it.find() }
                    ?: JOIN.matcher(stripped).takeIf { it.find() }
                m?.let { onJoin(it.group(1)) }
            }
            false
        }
    }

    private fun onJoin(name: String) {
        val self = Minecraft.getInstance().player?.name?.string ?: return
        if (name.equals(self, ignoreCase = true)) return
        if (!NameList.contains(FishSettings.pcKickList, name)) return
        if (PartyUtil.amLeader()) {
            kick(name)
        } else {
            pending[name] = System.currentTimeMillis()
            PartyUtil.forceRefresh()
        }
    }

    private fun kick(name: String) {
        val now = System.currentTimeMillis()
        val key = name.lowercase()
        val prev = lastKick[key]
        if (prev != null && now - prev < KICK_COOLDOWN_MS) return
        lastKick[key] = now
        FishMsg.send("§9Kick List §7> kicking §e$name")
        Scheduler.scheduleTask({ Misc.executeCommand("party kick $name") }, 2)
        Scheduler.scheduleTask({ PartyUtil.forceRefresh() }, 20)
    }

    private fun onTick() {
        if (!FishSettings.pcKickListEnabled) return
        if (FishSettings.pcKickList.isBlank()) return

        if (pending.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val it = pending.entries.iterator()
            while (it.hasNext()) {
                val (name, requested) = it.next()
                if (PartyUtil.lastReceived >= requested) {
                    it.remove()
                    if (PartyUtil.amLeader()) kick(name)
                } else if (now - requested > PENDING_TIMEOUT_MS) {
                    it.remove()
                }
            }
        }

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
            kick(name)
        }
    }
}

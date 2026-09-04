package fishmod.utils.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * "P3 practice" override for F7/M7 boss practice servers (default: `hypixelp3sim.zapto.org`).
 *
 * Those servers copy Hypixel's sidebar, so [Phase.isInFloor7] already works — but they never send
 * the Hypixel Mod API location packet ([fishmod.utils.Location.inDungeon] stays false) and don't
 * relay the `[BOSS]` chat lines that advance [Phase]'s phase counter. While connected to a
 * configured practice IP this forces `inDungeon` / `inSkyblock` true and lets you set the boss phase
 * by hand (`/fmpractice p1..p5`), so the terminal solver, Goldor devices, Wither dragons, etc. all
 * activate.
 */
object PracticeMode {

    @Volatile var active = false
        private set

    /** -1 = follow the real [Phase] tracker; else a forced `Phase.currentPhase` value. */
    @Volatile var phaseOverride = -1

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, mc -> refresh(mc) })
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            active = false
            phaseOverride = -1
        })
    }

    private fun ips(): List<String> =
        FishSettings.practiceServerIps.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun refresh(mc: Minecraft) {
        val addr = mc.currentServer?.ip?.trim()?.lowercase().orEmpty()
        val was = active
        active = addr.isNotEmpty() && ips().any { addr == it || addr.contains(it) }
        if (active && !was) {
            if (phaseOverride < 0) phaseOverride = 6 // P3 sim: default to the terminals phase
            // This sim never sends the real Hypixel location packet, so ON_LOCATION_CHANGE — the
            // event every per-run feature (Section/Goldor splits included) resets on — would otherwise
            // never fire here. Without it, state from the previous practice attempt (e.g. Section's
            // currentSection) just keeps accumulating across reconnects instead of starting fresh.
            Events.ON_LOCATION_CHANGE.invoke { it.onLocationChange(Location.DUNGEON) }
            msg("§aPractice mode ON §7— phase §f${label(phaseOverride)}§7. §8/fmpractice for options")
        } else if (!active && was) {
            phaseOverride = -1
        }
    }

    fun label(p: Int): String = when (p) {
        4 -> "P1"; 5 -> "P2"; 6 -> "P3 terminals"; 7 -> "P3 tunnel"; 8 -> "P4"; 9 -> "P5 dragons"
        else -> "auto"
    }

    /** `/fmpractice [status|here|off|p1|p2|p3|p3g|p4|p5]` */
    @JvmStatic
    fun command(arg: String?) {
        when (arg?.lowercase()?.trim()) {
            null, "", "status" -> msg("active=§f$active §7phase=§f${label(phaseOverride)} §7ips=§f${FishSettings.practiceServerIps}")
            "off", "auto" -> { phaseOverride = -1; msg("phase override cleared — following the real tracker") }
            "here" -> addHere()
            "p1" -> set(4)
            "p2" -> set(5)
            "p3", "p3t", "terminals" -> set(6)
            "p3g", "tunnel", "goldor" -> set(7)
            "p4" -> set(8)
            "p5", "dragons" -> set(9)
            else -> msg("§cusage: /fmpractice [status|here|off|p1|p2|p3|p3g|p4|p5]")
        }
    }

    private fun addHere() {
        val ip = Minecraft.getInstance().currentServer?.ip?.trim()
        if (ip.isNullOrEmpty()) { msg("§cnot connected to a multiplayer server"); return }
        val cur = FishSettings.practiceServerIps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (cur.any { it.equals(ip, ignoreCase = true) }) { msg("§e$ip is already in the list"); return }
        FishSettings.practiceServerIps = (cur + ip).joinToString(",")
        runCatching { FishConfig.manager.save() }
        msg("§aadded §f$ip §7to the practice IP list")
        refresh(Minecraft.getInstance())
    }

    private fun set(p: Int) {
        phaseOverride = p
        msg("phase → §f${label(p)}" + if (!active) " §8(takes effect on a practice server)" else "")
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal("§b[fmpractice] §7$s"))
}

package fishmod.features.achievements
import fishmod.compat.showScreen

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.features.achievements.AchRegistry.Ach
import fishmod.utils.FishMsg
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

// Unlock checks, AP totals, toasts and /fm ach
object AchEngine {

    private var evaluatedVersion = -1
    private var tick = 0
    private var syncedThisLogin = false
    private var skyblockSinceMs = 0L

    @JvmStatic
    fun init() {
        AchStore.load()
        AchHooks.init()
        AchTrackers.init()
        AchUsage.init()
        AchLeaderboard.init()
        AchPopup.init()
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player != null) FishDiag.guard("AchEngine.2", "achievement owner bind") { AchStore.bind() }
            if (!AchSettings.achEnabled) return@register
            FishDiag.guard("AchEngine.1", "achievement tick") {
                AchHooks.onTick()
                AchTrackers.onTick(mc)
                if (tick++ % 20 == 0) { evaluate(); AchStore.tick() }
                autoSync(mc)
                AchLeaderboard.onTick(mc)
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { AchStore.saveNow() }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { skyblockSinceMs = 0L; false }
    }

    private fun autoSync(mc: Minecraft) {
        if (syncedThisLogin || !AchSettings.achApiSyncOnLogin || mc.player == null) return
        if (!Location.inSkyblock()) return
        val now = System.currentTimeMillis()
        if (skyblockSinceMs == 0L) { skyblockSinceMs = now; return }
        if (now - skyblockSinceMs < 15_000) return
        syncedThisLogin = true
        AchApiSync.sync(false)
    }

    fun value(a: Ach): Long = when (a.metric) {
        "meta.completionist" -> if (AchRegistry.ALL.all { it.secret || it.section == "Ultimate" || unlocked(it) }) 1 else 0
        "meta.fishKing" -> if (AchRegistry.ALL.all { !it.secret || it.metric == "meta.fishKing" || unlocked(it) }) 1 else 0
        "meta.emperor" -> if (AchRegistry.ALL.all { it.metric == "meta.emperor" || unlocked(it) }) 1 else 0
        else -> AchStore.get(a.metric)
    }

    fun unlocked(a: Ach): Boolean = AchStore.data.unlocked.containsKey(a.id)

    fun points(): Int = AchRegistry.ALL.filter { unlocked(it) }.sumOf { it.points }
    fun maxPoints(): Int = AchRegistry.ALL.sumOf { it.points }

    @JvmStatic
    fun evaluate() {
        if (AchStore.version == evaluatedVersion) return
        val fresh = ArrayList<Ach>()
        // Metas depend on other unlocks, so loop until nothing new
        do {
            var any = false
            for (a in AchRegistry.ALL) {
                if (!a.tracked || unlocked(a) || a.goal <= 0) continue
                if (value(a) >= a.goal) {
                    AchStore.data.unlocked[a.id] = System.currentTimeMillis()
                    fresh.add(a); any = true
                }
            }
        } while (any)
        if (fresh.isNotEmpty()) {
            AchStore.changed()
            notify(fresh)
            for (a in fresh) AchBus.post(AchEvent.Unlocked(a.id))
        }
        evaluatedVersion = AchStore.version
        AchMenu.refresh()
    }

    private fun notify(fresh: List<Ach>) {
        val mc = Minecraft.getInstance()
        val pts = fresh.sumOf { it.points }
        if (fresh.size > 3) {
            if (AchSettings.achChat) FishMsg.send("§6[Achievements] §eUnlocked ${fresh.size} achievements §6+$pts AP §7(${points()} AP total)")
            if (AchSettings.achToast) AchPopup.show("${fresh.size} Achievements", "Unlocked in one go", pts, "")
        } else for (a in fresh) {
            if (AchSettings.achChat) FishMsg.send("§6[Achievement] §e${a.name} §7- ${a.desc} §6+${a.points} AP")
            if (AchSettings.achToast) AchPopup.show(a.name, a.desc, a.points, a.section)
        }
        AchPopup.playSound()
    }

    fun fmt(a: Ach, v: Long): String = when (a.unit) {
        "coins" -> fishmod.features.diana.DianaTracker.short(v.toDouble())
        "time" -> "%.1fh".format(v / 3600.0)
        else -> "%,d".format(v)
    }

    private fun runs() {
        val r = AchStore.data.runs.takeLast(10)
        if (r.isEmpty()) { FishMsg.send("§7No runs logged yet."); return }
        FishMsg.send("§6[Run Log] §7last ${r.size} of ${AchStore.data.runs.size}")
        for (x in r.asReversed()) FishMsg.send(" §e${x.floor} §f${fishmod.features.dungeon.PbMessages.fmt(x.secs)} §7score §f${x.score} §7deaths §f${x.deaths} §7secrets §f${x.secrets}")
    }

    private fun rng() {
        val r = AchStore.data.rng.takeLast(10)
        FishMsg.send("§6[RNG] §7count §f${AchStore.get("rng.count")} §7best §6${fishmod.features.diana.DianaTracker.short(AchStore.get("rng.best").toDouble())}")
        if (r.isEmpty()) { FishMsg.send("§7No RNG drops logged yet."); return }
        FishMsg.send("§6[RNG Log] §7last ${r.size} of ${AchStore.data.rng.size}")
        for (x in r.asReversed()) FishMsg.send(" §d${x.item} §6${fishmod.features.diana.DianaTracker.short(x.value)} §8${x.source}")
    }

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("ach")
            .executes { Minecraft.getInstance().schedule { Minecraft.getInstance().showScreen(AchScreen()) }; 1 }
            .then(ClientCommands.literal("test").executes { AchPopup.test(); 1 })
            .then(ClientCommands.literal("sync").executes { AchApiSync.sync(true); 1 })
            .then(ClientCommands.literal("lb")
                .then(ClientCommands.literal("here")
                    .executes { AchLeaderboard.here(false); 1 }
                    .then(ClientCommands.literal("points").executes { AchLeaderboard.here(false); 1 })
                    .then(ClientCommands.literal("secrets").executes { AchLeaderboard.here(true); 1 }))
                .then(ClientCommands.literal("refresh").executes { AchLeaderboard.submit(true); AchLeaderboard.fetch(); 1 }))
            .then(ClientCommands.literal("runs").executes { runs(); 1 })
            .then(ClientCommands.literal("rng").executes { rng(); 1 })
}

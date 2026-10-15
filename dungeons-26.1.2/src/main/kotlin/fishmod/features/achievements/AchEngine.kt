package fishmod.features.achievements

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
import net.minecraft.client.gui.components.toasts.SystemToast
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
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!AchSettings.achEnabled) return@register
            FishDiag.guard("AchEngine.1", "achievement tick") {
                AchHooks.onTick()
                AchTrackers.onTick(mc)
                if (tick++ % 20 == 0) { evaluate(); AchStore.tick() }
                autoSync(mc)
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
            toast(mc, "${fresh.size} Achievements Unlocked", "+$pts AP")
        } else for (a in fresh) {
            if (AchSettings.achChat) FishMsg.send("§6[Achievement] §e${a.name} §7- ${a.desc} §6+${a.points} AP")
            toast(mc, "Achievement Unlocked §6+${a.points} AP", a.name)
        }
        if (AchSettings.achSound) {
            SoundManager.play(SoundManager.preset(AchSettings.achSoundName), AchSettings.achSoundVolume / 100f, AchSettings.achSoundPitch.toFloat())
        }
    }

    private fun toast(mc: Minecraft, title: String, line: String) {
        if (!AchSettings.achToast) return
        FishDiag.guard("AchEngine.2", "achievement toast") {
            mc.toastManager.addToast(SystemToast.multiline(mc, SystemToast.SystemToastId(5000L), Component.literal(title), Component.literal(line)))
        }
    }

    fun fmt(a: Ach, v: Long): String = when (a.unit) {
        "coins" -> fishmod.features.diana.DianaTracker.short(v.toDouble())
        "time" -> "%.1fh".format(v / 3600.0)
        else -> "%,d".format(v)
    }

    private fun summary() {
        val all = AchRegistry.ALL
        FishMsg.send("§6[Achievements] §e${all.count { unlocked(it) }}/${all.size} unlocked §7| §6${points()}/${maxPoints()} AP")
        for (sec in AchRegistry.SECTIONS) {
            val list = all.filter { it.section == sec }
            FishMsg.send(" §7$sec: §f${list.count { unlocked(it) }}/${list.size}")
        }
        FishMsg.send("§7Browse in /fm -> Achievements. §8/fm ach sync | runs | rng")
    }

    private fun runs() {
        val r = AchStore.data.runs.takeLast(10)
        if (r.isEmpty()) { FishMsg.send("§7No runs logged yet."); return }
        FishMsg.send("§6[Run Log] §7last ${r.size} of ${AchStore.data.runs.size}")
        for (x in r.asReversed()) FishMsg.send(" §e${x.floor} §f${fishmod.features.dungeon.PbMessages.fmt(x.secs)} §7score §f${x.score} §7deaths §f${x.deaths} §7secrets §f${x.secrets}")
    }

    private fun rng() {
        val r = AchStore.data.rng.takeLast(10)
        if (r.isEmpty()) { FishMsg.send("§7No RNG drops logged yet."); return }
        FishMsg.send("§6[RNG Log] §7last ${r.size} of ${AchStore.data.rng.size}")
        for (x in r.asReversed()) FishMsg.send(" §d${x.item} §6${fishmod.features.diana.DianaTracker.short(x.value)} §8${x.source}")
    }

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("ach")
            .executes { summary(); 1 }
            .then(ClientCommands.literal("sync").executes { AchApiSync.sync(true); 1 })
            .then(ClientCommands.literal("runs").executes { runs(); 1 })
            .then(ClientCommands.literal("rng").executes { rng(); 1 })
}

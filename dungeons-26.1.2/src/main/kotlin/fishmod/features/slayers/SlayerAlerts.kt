package fishmod.features.slayers

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component

/** Drawn through [Misc.forceTitle] like other FishMod alerts. Boss-spawn is latched here so a flickering scoreboard can't repeat it within a quest; [reset] re-arms it for the next boss. */
object SlayerAlerts {

    private var bossSpawnLatched = false

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { reset(); false }
    }

    /** New quest / quest ended — re-arm the latches. */
    @JvmStatic
    fun reset() {
        bossSpawnLatched = false
    }

    @JvmStatic
    fun bossSpawned(type: SlayerType) {
        if (!FishSettings.slayerSpawnAlertEnabled || !FishSettings.slayerBossAlert) return
        if (bossSpawnLatched) return
        bossSpawnLatched = true
        // distinct from the miniboss alert: red, "SLAYER BOSS", with the boss name as the subtitle
        titleParts(
            "§c§l☠ SLAYER BOSS ☠",
            "§e${type.bossLabel}",
            FishSettings.slayerAlertDurationMs,
        )
    }

    @JvmStatic
    fun miniBoss(label: String) {
        if (!FishSettings.slayerSpawnAlertEnabled || !FishSettings.slayerMiniBossAlert) return
        titleParts("§6§l${label.uppercase()}", "§7Miniboss spawned", FishSettings.slayerAlertDurationMs)
    }

    @JvmStatic
    fun cocoon() {
        if (!FishSettings.slayerCocoonAlertEnabled) return
        titleParts("§d§lCOCOON!", "§7Slayer boss is cocooned", FishSettings.slayerCocoonAlertDurationMs)
    }

    private fun titleParts(main: String, sub: String, durationMs: Int) {
        Misc.forceTitle(
            Component.literal(main),
            if (sub.isEmpty()) Component.empty() else Component.literal(sub),
            durationMs.coerceIn(250, 10_000),
        )
    }
}

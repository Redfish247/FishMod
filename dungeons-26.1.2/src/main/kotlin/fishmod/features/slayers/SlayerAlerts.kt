package fishmod.features.slayers

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component

/**
 * On-screen alerts for Slayer events, drawn through the mod's existing fading-title system
 * ([Misc.forceTitle] with a hold time) so they look and behave exactly like every other FishMod
 * title alert — no new rendering path.
 *
 * De-duplication:
 *  - miniboss alerts are gated per entity id by [SlayerBossDetector] (fires once per add),
 *  - the boss-spawn alert is latched here so a flickering scoreboard can't repeat it within a quest,
 *  - the cocoon alert is only reached on the GRINDING/BOSS_SPAWNED -> COCOONED state edge, so it's
 *    inherently one-per-cocoon; [reset] re-arms it for the next boss.
 */
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

package fishmod.features.slayers

import fishmod.utils.FishConstants
import fishmod.utils.FishUtil
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component

object SlayerAlerts {

    private var bossSpawnLatched = false
    private val RNG_METER = Regex("^RNG Meter - (.+)$")

    @JvmStatic
    fun init() {
        FishEvents.worldChange.register { reset(); false }
        FishEvents.chat.register { text ->
            if (FishSettings.slayerAnyEnabled()) {
                val s = text.string.replace(FishConstants.STRIP_COLOR_REGEX, "").trim()
                RNG_METER.find(s)?.let {
                    FishDiag.guard("SlayerAlerts.2", "RNG meter line handling failed: '$s'") { SlayerStatsTracker.onRng(it.groupValues[1].trim()) }
                }
            }
            false
        }
    }

    @JvmStatic
    fun reset() {
        bossSpawnLatched = false
    }

    @JvmStatic
    fun bossSpawned(type: SlayerType) {
        if (!FishSettings.slayerSpawnAlertEnabled || !FishSettings.slayerBossAlert) return
        if (bossSpawnLatched) return
        bossSpawnLatched = true
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
        try {
            FishUtil.forceTitle(
                Component.literal(main),
                if (sub.isEmpty()) Component.empty() else Component.literal(sub),
                durationMs.coerceIn(250, 10_000),
            )
        } catch (e: Exception) {
            FishDiag.fail("SlayerAlerts.1", "failed to show slayer title '$main'", e)
        }
    }
}

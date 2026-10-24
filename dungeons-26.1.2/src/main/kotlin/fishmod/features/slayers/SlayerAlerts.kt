package fishmod.features.slayers

import fishmod.utils.Constants
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component

object SlayerAlerts {

    private var bossSpawnLatched = false
    private val RNG_METER = Regex("^RNG Meter - (.+)$")
    private var lastRngMs = 0L

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { reset(); false }
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.slayerRngTitleEnabled) {
                val s = text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim()
                RNG_METER.find(s)?.let { rngMeter(it.groupValues[1]) }
            }
            false
        }
    }

    private fun rngMeter(value: String) {
        val now = System.currentTimeMillis()
        if (now - lastRngMs < 1_000L) return
        lastRngMs = now
        try {
            Misc.forceTitle(
                Component.literal("RNG Meter").withStyle { it.withColor(FishSettings.slayerRngTitleColor and 0xFFFFFF).withBold(true) },
                Component.literal("§e$value"),
                FishSettings.slayerRngTitleDurationMs.coerceIn(250, 10_000),
            )
        } catch (e: Exception) {
            FishDiag.fail("SlayerAlerts.2", "failed to show RNG meter title", e)
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
            Misc.forceTitle(
                Component.literal(main),
                if (sub.isEmpty()) Component.empty() else Component.literal(sub),
                durationMs.coerceIn(250, 10_000),
            )
        } catch (e: Exception) {
            FishDiag.fail("SlayerAlerts.1", "failed to show slayer title '$main'", e)
        }
    }
}

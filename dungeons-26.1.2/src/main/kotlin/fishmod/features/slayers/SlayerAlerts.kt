package fishmod.features.slayers

import fishmod.utils.events.chatPlain
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
        FishEvents.worldChange.register { reset(); seenMinis.clear(); false }
        FishEvents.chat.register { text ->
            if (FishSettings.slayerAnyEnabled()) {
                val s = text.chatPlain.trim()
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
        lastMiniMs = System.currentTimeMillis()
        titleParts("§6§l${label.uppercase()}", "§7Miniboss spawned", FishSettings.slayerAlertDurationMs)
    }

    private val seenMinis = HashSet<Int>()
    private var lastMiniMs = 0L

    // #590: Hypixel sends no miniboss chat line, so detect the nametag near the player (Skytils approach)
    @JvmStatic
    fun scanMiniBosses(mc: net.minecraft.client.Minecraft, type: SlayerType) {
        if (!FishSettings.slayerSpawnAlertEnabled || !FishSettings.slayerMiniBossAlert) return
        val player = mc.player ?: return
        val level = mc.level ?: return
        for (e in level.entitiesForRendering()) {
            if (e === player || e is net.minecraft.world.entity.player.Player) continue
            val raw = e.customName?.string ?: continue
            if (e.distanceToSqr(player) > 15.0 * 15.0) continue
            val name = type.miniBosses.firstOrNull { raw.contains(it) } ?: continue
            if (!seenMinis.add(e.id)) continue
            fishmod.utils.debug.FishDebug.LOGGER.debug("[SlayerAlerts] miniboss nametag '{}' id={}", raw, e.id)
            if (System.currentTimeMillis() - lastMiniMs > 1_500L) miniBoss(name)
        }
        if (seenMinis.size > 256) seenMinis.clear()
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

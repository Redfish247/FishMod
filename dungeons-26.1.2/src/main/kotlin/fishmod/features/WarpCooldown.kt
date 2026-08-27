package fishmod.features

import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Revives blade-addons' `enableWarpCooldown`: after you fire a `/warp` (or the island shortcuts),
 * shows a small countdown until Hypixel will accept another warp, so you're not blind-spamming the
 * command. Duration is [FishSettings.warpCooldownSeconds] (Hypixel's is ~3s for island warps).
 */
object WarpCooldown {

    private const val NAME = "Warp Cooldown"
    private val WARP_HEADS = setOf("warp", "warpforge", "is", "hub", "dungeonhub", "dhub", "garden")

    @Volatile private var warpAt = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.warpCooldownHudX }, { v -> FishSettings.warpCooldownHudX = v },
            { FishSettings.warpCooldownHudY }, { v -> FishSettings.warpCooldownHudY = v },
            80, 12,
            { FishSettings.warpCooldownScale }, { v -> FishSettings.warpCooldownScale = v }
        )

        ClientSendMessageEvents.COMMAND.register { command ->
            if (!Dungeons.enableWarpCooldown) return@register
            val head = command.trim().substringBefore(' ').lowercase()
            if (head in WARP_HEADS) warpAt = System.currentTimeMillis()
        }
    }

    private fun remainingMs(): Long {
        if (warpAt == 0L) return 0
        val total = FishSettings.warpCooldownSeconds.coerceIn(1, 30) * 1000L
        return (total - (System.currentTimeMillis() - warpAt)).coerceAtLeast(0)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Dungeons.enableWarpCooldown) return
        val rem = remainingMs()
        if (rem <= 0L) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val label = "§bWarp §f" + String.format("%.1fs", rem / 1000.0)
        val sc = FishSettings.warpCooldownScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.warpCooldownHudX.toFloat(), FishSettings.warpCooldownHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}

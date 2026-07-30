package fishmod.features

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.Text
import java.util.regex.Pattern

object SoulflowHud {

    private val SF_PATTERN: Pattern = Pattern.compile("Soulflow:\\s*([\\d,]+)")
    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

    private var soulflow = -1
    private var tickCount = 0

    private var missCount = 0
    private var warnedThisSession = false
    private const val MISS_SCANS_BEFORE_WARN = 5 // ~5s at 10-tick scan interval

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            "Soulflow",
            { FishSettings.soulflowHudX }, { v -> FishSettings.soulflowHudX = v },
            { FishSettings.soulflowHudY }, { v -> FishSettings.soulflowHudY = v },
            145, 14,
            { FishSettings.soulflowHudScale }, { v -> FishSettings.soulflowHudScale = v }
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.soulflowHudEnabled) return@register
            if (client.player == null || client.networkHandler == null) {
                soulflow = -1
                return@register
            }
            if (!Location.inSkyblock()) {
                soulflow = -1
                return@register
            }
            tickCount++
            if (tickCount < 10) return@register
            tickCount = 0
            scanTabList(client.networkHandler!!)
        }
    }

    private fun scanTabList(handler: ClientPlayNetworkHandler) {
        val entries = handler.playerList
        for (entry in entries) {
            val displayName = entry.displayName ?: continue
            val text = COLOR_STRIP.matcher(displayName.string).replaceAll("").trim()
            val m = SF_PATTERN.matcher(text)
            if (m.find()) {
                val numStr = m.group(1).replace(",", "")
                soulflow = try {
                    numStr.toInt()
                } catch (ignored: NumberFormatException) {
                    -1
                }
                missCount = 0
                warnedThisSession = false
                return
            }
        }
        soulflow = -1
        missCount++
        if (FishSettings.soulflowMissingNotifier &&
            !warnedThisSession &&
            missCount >= MISS_SCANS_BEFORE_WARN
        ) {
            warnedThisSession = true
            Misc.addChatMessage(
                Text.literal(
                    "§3[FishMod] §eSoulflow not visible in /tab. Run §b/tab §e→ click §bProfile §e→ enable §bSoulflow§e."
                )
            )
        }
    }

    @JvmStatic
    fun renderHud(ctx: DrawContext, tickCounter: RenderTickCounter) {
        if (!FishSettings.soulflowHudEnabled) return
        val mc = MinecraftClient.getInstance()
        if (mc.player == null || mc.world == null) return
        if (!Location.inSkyblock()) return
        if (soulflow < 0) return

        // Check for the warning threshold
        val warn = FishSettings.soulflowWarningThreshold > 0 && soulflow < FishSettings.soulflowWarningThreshold

        // Force the format: §e is Yellow (&e), §
        // If warning, we can keep it Red or add the warning symbol
        val label = if (warn) {
            "§3Soulflow: " + String.format("%,d", soulflow) + " ⚠"
        } else {
            "§3Soulflow: §f" + String.format("%,d", soulflow)
        }

        val sc = FishSettings.soulflowHudScale.toFloat()
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(FishSettings.soulflowHudX.toFloat(), FishSettings.soulflowHudY.toFloat())
        ctx.matrices.scale(sc, sc)
        ctx.drawText(mc.textRenderer, label, 0, 0, -1, true)
        ctx.matrices.popMatrix()
    }
}

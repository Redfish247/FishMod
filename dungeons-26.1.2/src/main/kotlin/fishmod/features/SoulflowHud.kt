package fishmod.features

import fishmod.utils.SkyblockLocation
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

object SoulflowHud {

    private val SF_PATTERN: Pattern = Pattern.compile("Soulflow:\\s*([\\d,]+)")

    private var soulflow = -1
    private var tickCount = 0

    private var missCount = 0
    private var warnedThisSession = false
    private const val MISS_SCANS_BEFORE_WARN = 5

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            "Soulflow",
            { FishSettings.soulflowHudX }, { v -> FishSettings.soulflowHudX = v },
            { FishSettings.soulflowHudY }, { v -> FishSettings.soulflowHudY = v },
            145, 14,
            { FishSettings.soulflowHudScale }, { v -> FishSettings.soulflowHudScale = v },
            { FishSettings.soulflowHudEnabled }
        )
        HudPreview.register("Soulflow") { HudPreview.text(listOf(label(12345))) }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.soulflowHudEnabled) return@register
            if (client.player == null || client.getConnection() == null) {
                soulflow = -1
                return@register
            }
            if (!SkyblockLocation.inSkyblock()) {
                soulflow = -1
                return@register
            }
            tickCount++
            if (tickCount < 10) return@register
            tickCount = 0
            FishDiag.guard("SoulflowHud.2", "soulflow tab scan failed") { scanTabList() }
        }
    }

    private fun scanTabList() {
        for (entry in TabListCache.entries) {
            val text = entry.stripped.trim()
            val m = SF_PATTERN.matcher(text)
            if (m.find()) {
                val numStr = m.group(1).replace(",", "")
                soulflow = try {
                    numStr.toInt()
                } catch (e: NumberFormatException) {
                    FishDiag.fail("SoulflowHud.1", "soulflow not an int: '$numStr'", e)
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
            fishmod.utils.FishUtil.addChatMessage(
                net.minecraft.network.chat.Component.literal(
                    "§3[FishMod] §eSoulflow not visible in /tab. Run §b/tab §e→ click §bProfile §e→ enable §bSoulflow§e."
                )
            )
        }
    }

    private fun label(soulflow: Int): String {
        val warn = FishSettings.soulflowWarningThreshold > 0 && soulflow < FishSettings.soulflowWarningThreshold
        return if (warn) "§3Soulflow: " + fishmod.utils.Fmt.grouped(soulflow.toLong()) + " ⚠"
        else "§3Soulflow: §f" + fishmod.utils.Fmt.grouped(soulflow.toLong())
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.soulflowHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return
        if (!SkyblockLocation.inSkyblock()) return
        if (soulflow < 0) return

        val sc = FishSettings.soulflowHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(FishSettings.soulflowHudX.toFloat(), FishSettings.soulflowHudY.toFloat())
            ctx.pose().scale(sc, sc)
            HudPreview.drawText(ctx, listOf(label(soulflow)))
        } catch (e: Exception) {
            FishDiag.fail("SoulflowHud.3", "soulflow hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}

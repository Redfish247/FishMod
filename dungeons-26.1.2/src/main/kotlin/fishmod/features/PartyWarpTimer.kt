package fishmod.features
import fishmod.utils.events.chatPlain
import fishmod.compat.hudHidden
import fishmod.utils.TextStrip

import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

object PartyWarpTimer {

    private const val NAME = "Party Warp Timer"
    private val ENTERED: Pattern =
        Pattern.compile("\\b(\\w{1,16}) entered (?:MM )?\\w+ Catacombs, Floor \\w+!")
    private val KICKED: Pattern =
        Pattern.compile("^(?:You were kicked while joining that server!|You are no longer allowed to access this instance!)$")

    @Volatile private var enteredAt = 0L
    @Volatile private var armedAt = 0L
    private const val ARM_TIMEOUT_MS = 10_000L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.warpCooldownHudX }, { v -> FishSettings.warpCooldownHudX = v },
            { FishSettings.warpCooldownHudY }, { v -> FishSettings.warpCooldownHudY = v },
            80, 12,
            { FishSettings.warpCooldownScale }, { v -> FishSettings.warpCooldownScale = v }
        )
        HudPreview.register(NAME) {
            // 70% of the player's cooldown left, so it never exceeds the setting
            val l = label(FishSettings.warpCooldownSeconds.coerceIn(1, 120) * 700L)
            HudExample(Minecraft.getInstance().font.width(l), HudPreview.TEXT_H) { ctx -> ctx.text(Minecraft.getInstance().font, l, 0, 0, -1, true) }
        }

        FishEvents.chat.register { text ->
            if (!DungeonSettings.partyWarpTimer || fishmod.compat.BladeCompat.active) return@register false
            try {
                val s = text.chatPlain
                if (ENTERED.matcher(s).find()) {
                    if (remainingMs() <= 0L) armedAt = System.currentTimeMillis()
                } else if (FishSettings.warpAnnounceKick && KICKED.matcher(s).matches()) {
                    fishmod.utils.ChatQueue.enqueue("pc ${FishSettings.warpKickText}")
                }
            } catch (e: Exception) {
                FishDiag.fail("PartyWarpTimer.1", "warp cooldown chat handling failed", e)
            }
            false
        }
        FishEvents.worldChange.register {
            if (armedAt != 0L) { enteredAt = System.currentTimeMillis(); armedAt = 0L }
            false
        }
    }

    private fun remainingMs(): Long {
        if (armedAt != 0L && System.currentTimeMillis() - armedAt > ARM_TIMEOUT_MS) { enteredAt = armedAt + ARM_TIMEOUT_MS; armedAt = 0L }
        if (enteredAt == 0L) return 0
        val total = FishSettings.warpCooldownSeconds.coerceIn(1, 120) * 1000L
        val rem = total - (System.currentTimeMillis() - enteredAt)
        FishDiag.check(rem <= total, "PartyWarpTimer.2") { "warp cooldown enteredAt in the future: rem=$rem total=$total" }
        return rem.coerceAtLeast(0)
    }

    private fun label(rem: Long) = net.minecraft.network.chat.Component.literal("§eWarp: ")
        .append(net.minecraft.network.chat.Component.literal(fishmod.utils.Fmt.f1(rem / 1000.0) + "s")
            .withColor(FishSettings.warpCooldownColor and 0xFFFFFF))

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!DungeonSettings.partyWarpTimer || fishmod.compat.BladeCompat.active) return
        val rem = remainingMs()
        if (rem <= 0L) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return

        val label = label(rem)
        val sc = FishSettings.warpCooldownScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(FishSettings.warpCooldownHudX.toFloat(), FishSettings.warpCooldownHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, label, 0, 0, -1, true)
        } catch (e: Exception) {
            FishDiag.fail("PartyWarpTimer.3", "warp cooldown hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}

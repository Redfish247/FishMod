package fishmod.cosmetic.prestige

import fishmod.mixin.ChatHudInvoker
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessage

object PrestigeChatFade {
    private const val REFRESH_INTERVAL_TICKS = 3
    private const val ANIMATE_TICKS = 200

    private var tickCounter = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register {
            if (!FishSettings.prestigeColorsEnabled || !FishSettings.prestigeColorsChat
                || !FishSettings.prestigeColorsAnimated) return@register
            if (++tickCounter < REFRESH_INTERVAL_TICKS) return@register
            tickCounter = 0
            tick()
        }
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val hud = mc.gui?.chat ?: return
        val nowTick = mc.gui.guiTicks

        val invoker = hud as ChatHudInvoker
        val messages = invoker.messages
        if (messages == null || messages.isEmpty()) return

        var changed = false
        for (i in messages.indices) {
            val line = messages[i]
            val age = nowTick - line.addedTime()
            if (age > ANIMATE_TICKS) break

            val recolored = PrestigeLevelColors.colorizeChatLevel(line.content()) ?: continue
            if (recolored != line.content()) {
                messages[i] = GuiMessage(line.addedTime(), recolored, line.signature(), line.source(), line.tag())
                changed = true
            }
        }
        if (changed) invoker.invokeRefresh()
    }
}

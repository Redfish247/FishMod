package fishmod.cosmetic.prestige

import fishmod.mixin.ChatHudInvoker
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessage

/**
 * Keeps the Prestige Colors animation alive on chat lines after they're added.
 * [fishmod.mixin.CosmeticChatMixin] bakes the [level] badge recolour once, at add-time, so
 * without this the animated phase freezes the instant a chat line appears (unlike nametags/tab,
 * which recolour every frame from live components). This re-runs the recolour on lines younger
 * than [ANIMATE_TICKS], then leaves them alone for good once they age out of the window.
 */
object PrestigeChatFade {
    private const val REFRESH_INTERVAL_TICKS = 3   // ~150ms between re-colours
    private const val ANIMATE_TICKS = 200          // 10s at 20 ticks/sec

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
        // messages are newest-first, so once a line ages out the rest are older still — stop.
        for (i in messages.indices) {
            val line = messages[i]
            val age = nowTick - line.addedTime()
            if (age > ANIMATE_TICKS) break

            val recolored = PrestigeLevelColors.colorizeChatLevel(line.content()) ?: continue
            if (recolored !== line.content()) {
                messages[i] = GuiMessage(line.addedTime(), recolored, line.signature(), line.source(), line.tag())
                changed = true
            }
        }
        if (changed) invoker.invokeRefresh()
    }
}

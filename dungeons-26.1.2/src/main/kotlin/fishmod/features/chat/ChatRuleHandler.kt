package fishmod.features.chat

import fishmod.features.FishHudEditor
import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents

object ChatRuleHandler {

    private class ActiveTitle(val text: String, val untilMs: Long)
    private val activeTitles = ArrayList<ActiveTitle>()

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun init() {
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            if (!ChatRuleStore.isMasterEnabled()) return@register false
            val raw = message.string.replace(COLOR, "")
            for (rule in ChatRuleStore.rules()) {
                if (!rule.enabled || rule.filter.isBlank()) continue
                if (!matches(rule, raw)) continue
                fire(rule)
            }
            false
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "chat_notifications")) { ctx, tc -> if (!fishmod.features.FishHudEditor.isOpen()) renderHud(ctx, tc) }

        FishHudEditor.register(
            "Chat Notifications",
            { ChatRuleStore.hudX() }, { v -> ChatRuleStore.setHudX(v) },
            { ChatRuleStore.hudY() }, { v -> ChatRuleStore.setHudY(v) },
            220, 14,
            { ChatRuleStore.hudScale() }, { v -> ChatRuleStore.setHudScale(v) },
            { ChatRuleStore.isMasterEnabled() }
        )
    }

    @JvmStatic
    fun shouldHideAtDisplay(message: Component?): Boolean {
        if (message == null || !ChatRuleStore.isMasterEnabled()) return false
        val raw = message.string?.replace(COLOR, "") ?: return false
        for (rule in ChatRuleStore.rules()) {
            if (!rule.enabled || !rule.hideMessage || rule.filter.isBlank()) continue
            if (matches(rule, raw)) return true
        }
        return false
    }

    @JvmStatic
    fun matches(rule: ChatRule, raw: String): Boolean {
        val testStr = if (rule.ignoreCase) raw.lowercase() else raw
        val testFilter = if (rule.ignoreCase) rule.filter.lowercase() else rule.filter
        if (testFilter.isBlank()) return false

        return if (rule.regex) {
            val pattern = rule.compiledPattern() ?: return false
            val m = pattern.matcher(testStr)
            if (rule.partialMatch) m.find() else m.matches()
        } else {
            if (rule.partialMatch) testStr.contains(testFilter) else testStr == testFilter
        }
    }

    private fun fire(rule: ChatRule) {
        val mc = Minecraft.getInstance()

        if (rule.chatMessage.isNotBlank()) {
            Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + rule.chatMessage))
        }
        if (rule.actionBarMessage.isNotBlank()) {
            mc.player?.sendOverlayMessage(Component.literal(rule.actionBarMessage))
        }
        if (rule.titleMessage.isNotBlank()) {
            activeTitles.add(ActiveTitle(rule.titleMessage, System.currentTimeMillis() + maxOf(200L, rule.titleDurationMs)))
        }
        if (rule.soundEnabled) {
            mc.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f)
        }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tc: DeltaTracker) {
        if (!ChatRuleStore.isMasterEnabled()) return
        val now = System.currentTimeMillis()
        activeTitles.removeIf { it.untilMs < now }
        if (activeTitles.isEmpty()) return

        val mc = Minecraft.getInstance()
        val sc = ChatRuleStore.hudScale().toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(ChatRuleStore.hudX().toFloat(), ChatRuleStore.hudY().toFloat())
        ctx.pose().scale(sc, sc)
        val hudWidth = 220
        for ((i, t) in activeTitles.withIndex()) {
            val line = "§e⚑ ${t.text}"
            val x = (hudWidth - mc.font.width(line)) / 2
            ctx.text(mc.font, line, x, i * 11, -1, true)
        }
        ctx.pose().popMatrix()
    }
}

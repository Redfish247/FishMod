package fishmod.features.chat

import fishmod.utils.events.chatPlain
import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.utils.TextStrip
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
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


    @JvmStatic
    fun init() {
        fishmod.utils.events.FishEvents.chat.register { message ->
            if (!ChatRuleStore.isMasterEnabled()) return@register false
            val raw = message.chatPlain
            for (rule in ChatRuleStore.rules()) {
                if (!rule.enabled || rule.filter.isBlank()) continue
                try {
                    if (!matches(rule, raw)) continue
                    fire(rule)
                } catch (e: Exception) {
                    FishDiag.fail("ChatRuleHandler.1", "chat rule '${rule.filter}' failed", e)
                }
            }
            false
        }

        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "chat_notifications")) { ctx, tc -> if (!fishmod.features.FishHudEditor.isOpen()) renderHud(ctx, tc) }

        FishHudEditor.register(
            "Chat Alerts",
            { ChatRuleStore.hudX() }, { v -> ChatRuleStore.setHudX(v) },
            { ChatRuleStore.hudY() }, { v -> ChatRuleStore.setHudY(v) },
            220, 14,
            { ChatRuleStore.hudScale() }, { v -> ChatRuleStore.setHudScale(v) },
            { ChatRuleStore.isMasterEnabled() }
        )
        HudPreview.register("Chat Alerts") {
            // The user's first title rule text; no title rules = nothing ever shows
            val t = ChatRuleStore.rules().firstOrNull { it.enabled && it.titleMessage.isNotBlank() }?.titleMessage ?: return@register null
            HudExample(HUD_WIDTH, HudPreview.textHeight(1, 11)) { ctx -> drawAlerts(ctx, listOf(t)) }
        }
    }

    @JvmStatic
    fun shouldHideAtDisplay(message: Component?): Boolean {
        if (message == null || !ChatRuleStore.isMasterEnabled()) return false
        val raw = message.string?.let(TextStrip::plain) ?: return false
        for (rule in ChatRuleStore.rules()) {
            if (!rule.enabled || !rule.hideMessage || rule.filter.isBlank()) continue
            if (matches(rule, raw)) return true
        }
        return false
    }

    @JvmStatic
    fun matches(rule: ChatRule, raw: String): Boolean {
        val filter = rule.filter
        if (filter.isBlank()) return false

        return if (rule.regex) {
            val pattern = rule.compiledPattern() ?: run { FishDiag.fail("ChatRuleHandler.2", "chat rule regex does not compile: '$filter'"); return false }
            val m = pattern.matcher(raw)
            if (rule.partialMatch) m.find() else m.matches()
        } else {
            if (rule.partialMatch) raw.contains(filter, rule.ignoreCase) else raw.equals(filter, rule.ignoreCase)
        }
    }

    private fun fire(rule: ChatRule) {
        val mc = Minecraft.getInstance()

        if (rule.chatMessage.isNotBlank()) {
            FishUtil.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + rule.chatMessage))
        }
        if (rule.actionBarMessage.isNotBlank()) {
            mc.player?.sendOverlayMessage(Component.literal(rule.actionBarMessage))
        }
        if (rule.titleMessage.isNotBlank()) {
            if (activeTitles.size >= 16) activeTitles.removeAt(0)
            activeTitles.add(ActiveTitle(rule.titleMessage, System.currentTimeMillis() + maxOf(200L, rule.titleDurationMs)))
        }
        if (rule.soundEnabled) {
            mc.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f)
        }
    }

    private const val HUD_WIDTH = 220

    private fun drawAlerts(ctx: GuiGraphicsExtractor, texts: List<String>) {
        val font = Minecraft.getInstance().font
        for ((i, t) in texts.withIndex()) {
            val line = "§e⚑ $t"
            ctx.text(font, line, (HUD_WIDTH - font.width(line)) / 2, i * 11, -1, true)
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
        try {
            ctx.pose().translate(ChatRuleStore.hudX().toFloat(), ChatRuleStore.hudY().toFloat())
            ctx.pose().scale(sc, sc)
            drawAlerts(ctx, activeTitles.map { it.text })
        } catch (e: Exception) {
            FishDiag.fail("ChatRuleHandler.3", "chat notification hud render failed (${activeTitles.size} titles)", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}

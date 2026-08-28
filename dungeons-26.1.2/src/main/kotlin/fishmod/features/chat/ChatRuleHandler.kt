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
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Watches chat against user-defined [ChatRule]s (see [ChatRuleStore]) and fires each rule's
 * outputs on a match: optionally hide the original line, echo a reply to your own chat, show an
 * action-bar message, show a fading on-screen title, and/or play a sound. Matching logic mirrors
 * Skyblocker's ChatRule (github.com/SkyblockerMod/Skyblocker, MIT): plain substring/exact or
 * regex, case-sensitivity toggle, partial-vs-full match toggle.
 */
object ChatRuleHandler {

    private class ActiveTitle(val text: String, val untilMs: Long)
    private val activeTitles = ArrayList<ActiveTitle>()

    @JvmStatic
    fun init() {
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            if (!ChatRuleStore.isMasterEnabled()) return@register false
            val raw = message.string.replace(Regex("§."), "")
            for (rule in ChatRuleStore.rules()) {
                if (!rule.enabled || rule.filter.isBlank()) continue
                if (!matches(rule, raw)) continue
                fire(rule)
            }
            // Never drop the packet here — that would also skip vanilla's chat logging, so the line
            // would vanish from logs/latest.log too. "Hide Original Message" is applied at the chat
            // DISPLAY layer instead (see [shouldHideAtDisplay] / ChatHudMixin): the line still parses
            // and still logs, it just isn't drawn in your chat.
            false
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "chat_notifications")) { ctx, tc -> renderHud(ctx, tc) }

        FishHudEditor.register(
            "Chat Notifications",
            { ChatRuleStore.hudX() }, { v -> ChatRuleStore.setHudX(v) },
            { ChatRuleStore.hudY() }, { v -> ChatRuleStore.setHudY(v) },
            220, 14,
            { ChatRuleStore.hudScale() }, { v -> ChatRuleStore.setHudScale(v) },
            { ChatRuleStore.isMasterEnabled() }
        )
    }

    /**
     * DISPLAY-layer predicate (called from ChatHudMixin): true if some enabled rule with
     * "Hide Original Message" matches this line. Pure — side-effect outputs already fired from the
     * packet-level handler above, so this only decides whether to draw the line.
     */
    @JvmStatic
    fun shouldHideAtDisplay(message: Component?): Boolean {
        if (message == null || !ChatRuleStore.isMasterEnabled()) return false
        val raw = message.string?.replace(Regex("§."), "") ?: return false
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
            val pattern = try {
                Pattern.compile(testFilter)
            } catch (e: PatternSyntaxException) {
                return false
            }
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

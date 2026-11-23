package fishmod.features.chat

import fishmod.features.extras.*

import fishmod.features.extras.ExtrasSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent

object CommandTooltip {
    fun init() {
        ClientReceiveMessageEvents.MODIFY_GAME.register { msg, overlay ->
            if (overlay || !ExtrasSettings.commandTooltip) return@register msg
            try { rewrite(msg) } catch (t: Throwable) { FishDiag.fail("CommandTooltip.1", "command tooltip failed", t); msg }
        }
    }

    private fun rewrite(c: Component): Component {
        val out: MutableComponent = c.plainCopy()
        val cmd = when (val click = c.style.clickEvent) {
            is ClickEvent.RunCommand -> "§7Runs: §e" + click.command().let { if (it.startsWith("/")) it else "/$it" }
            is ClickEvent.SuggestCommand -> "§7Suggests: §e" + click.command()
            else -> null
        }
        var style = c.style
        if (cmd != null) {
            val old = (style.hoverEvent as? HoverEvent.ShowText)?.value()
            val text = if (old != null) old.copy().append("\n").append(cmd) else Component.literal(cmd)
            style = style.withHoverEvent(HoverEvent.ShowText(text))
        }
        out.setStyle(style)
        c.siblings.forEach { out.append(rewrite(it)) }
        return out
    }
}

package fishmod.features.chat

import fishmod.features.dungeon.PartyCommandHandler
import fishmod.mixin.accessors.EditBoxAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox

// Light-gray argument key after a typed .cmd in the chat input (fishmod.dev #536); render-only
object PartyCommandHint {
    private const val COLOR = 0xFFAAAAAA.toInt()

    // Channels whose messages PartyCommandHandler answers; no slash = current chat channel
    private val PREFIX = Regex(
        "^(?:/(?:pc|pchat|gc|gchat|ac|achat|r|(?:p|party|g|guild) chat|(?:msg|w|tell|whisper|message) \\w+) )?[.!]",
        RegexOption.IGNORE_CASE
    )

    private var lastInput: String? = null
    private var lastGhost: String? = null

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, input: EditBox) {
        val value = input.value
        if (input.cursorPosition != value.length) return
        val acc = input as EditBoxAccessor
        if (acc.fishmodSuggestion() != null) return // vanilla ghost text wins
        val ghost = ghostFor(value) ?: return
        val font = Minecraft.getInstance().font
        val textX = acc.fishmodTextX()
        val endX = textX + font.width(value.substring(acc.fishmodDisplayPos().coerceIn(0, value.length)))
        val avail = textX + input.innerWidth - endX
        if (avail <= 0) return
        ctx.text(font, font.plainSubstrByWidth(ghost, avail), endX, acc.fishmodTextY(), COLOR, true)
    }

    @JvmStatic
    fun ghostFor(value: String): String? {
        if (value == lastInput) return lastGhost
        lastInput = value
        lastGhost = compute(value)
        return lastGhost
    }

    private fun compute(value: String): String? {
        val m = PREFIX.find(value) ?: return null
        val rest = value.substring(m.range.last + 1)
        if (rest.isEmpty()) return null
        val sp = rest.indexOf(' ')
        val cmd = if (sp < 0) rest else rest.substring(0, sp)
        val usage = PartyCommandHandler.USAGE[cmd]
        if (sp < 0) {
            if (usage != null) return join(usage, 0, " ")
            // Command-name completion: first known name starting with what's typed
            val hit = PartyCommandHandler.USAGE.entries.firstOrNull { it.key.startsWith(cmd) } ?: return null
            return hit.key.substring(cmd.length) + (join(hit.value, 0, " ") ?: "")
        }
        if (usage == null) return null
        val argStr = rest.substring(sp + 1)
        val typed = argStr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val used = if (typed.isEmpty()) 0 else typed.size - 1 + PartyCommandHandler.hintSlotsForFirstArg(cmd, typed[0])
        return join(usage, used, if (typed.isEmpty() || argStr.endsWith(" ")) "" else " ")
    }

    private fun join(args: Array<String>, from: Int, lead: String): String? =
        if (from >= args.size) null else lead + args.copyOfRange(from, args.size).joinToString(" ")
}

package fishmod.features

import fishmod.mixin.ChatHudInvoker
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.regex.Matcher
import java.util.regex.Pattern

object CompactChat {

    private const val WINDOW_TICKS = 60 * 20

    private val COUNT_SUFFIX: Pattern = Pattern.compile("\\s\\((\\d+)\\)$")

    @JvmStatic
    fun tryCompact(message: Component, hud: ChatComponent, ci: CallbackInfo): Boolean {
        val incoming = stripKey(message.string)
        if (incoming.isEmpty()) return false

        val mc = Minecraft.getInstance()
        if (mc.gui == null) return false
        val nowTick = mc.gui.guiTicks

        val acc = hud as ChatHudInvoker
        val messages = acc.messages
        if (messages == null || messages.isEmpty()) return false

        if (keys.size > 512) keys.clear()
        for (i in messages.indices) {
            val line = messages[i]
            if (nowTick - line.addedTime() > WINDOW_TICKS) break
            if (incoming != keyOf(line)) continue

            val next = extractCount(line.content().string) + 1
            val removedLines = removeDisplayedLines(acc, i, messages.size)
            messages.removeAt(i)
            if (!removedLines) acc.invokeRefresh()
            ci.cancel()
            hud.addClientSystemMessage(withCount(message, next))
            return true
        }
        return false
    }

    private val keys = HashMap<Int, Pair<GuiMessage, String>>()

    private fun keyOf(line: GuiMessage): String {
        val id = System.identityHashCode(line)
        keys[id]?.let { if (it.first === line) return it.second }
        return stripKey(line.content().string).also { keys[id] = line to it }
    }

    private fun removeDisplayedLines(acc: ChatHudInvoker, index: Int, messageCount: Int): Boolean {
        val lines = acc.visibleMessages ?: return false
        var groups = 0
        var start = -1
        var end = lines.size
        for (j in lines.indices) {
            if (!lines[j].endOfEntry()) continue
            if (groups == index) start = j
            else if (groups == index + 1) end = j
            groups++
        }
        if (start < 0 || groups != messageCount) return false
        lines.subList(start, end).clear()
        return true
    }

    private fun stripKey(s: String): String {
        var plain = s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        val m: Matcher = COUNT_SUFFIX.matcher(plain)
        if (m.find()) plain = plain.substring(0, m.start())
        return plain.trim()
    }

    private fun extractCount(s: String): Int {
        val m = COUNT_SUFFIX.matcher(s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, ""))
        return if (m.find()) m.group(1).toInt() else 1
    }

    private fun withCount(message: Component, n: Int): Component {
        return message.copy().append(Component.literal(" ($n)").withStyle(ChatFormatting.GRAY))
    }
}

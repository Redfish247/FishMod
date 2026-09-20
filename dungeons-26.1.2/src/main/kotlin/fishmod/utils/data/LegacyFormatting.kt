package fishmod.utils.data

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object LegacyFormatting {

    @JvmStatic
    fun parse(raw: String): MutableComponent {
        val root = Component.literal("")
        var style = Style.EMPTY
        val sb = StringBuilder()
        var i = 0

        fun flush() {
            if (sb.isNotEmpty()) {
                root.append(Component.literal(sb.toString()).setStyle(style))
                sb.setLength(0)
            }
        }

        while (i < raw.length) {
            val c = raw[i]
            if ((c == '&' || c == '§') && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == '*') {
                    sb.append('✪')
                    i += 2
                    continue
                }
                if (next == '#' && i + 7 < raw.length) {
                    val hex = raw.substring(i + 2, i + 8)
                    if (hex.matches(Regex("[0-9a-fA-F]{6}"))) {
                        flush()
                        style = Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.parseColor("#$hex").getOrThrow())
                        i += 8
                        continue
                    }
                }
                val fmt = ChatFormatting.getByCode(next.lowercaseChar())
                if (fmt != null) {
                    flush()
                    style = if (fmt == ChatFormatting.RESET) Style.EMPTY else style.applyFormat(fmt)
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        flush()
        return root
    }

    @JvmStatic
    fun previewRuns(raw: String): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var color = 0xFFFFFFFF.toInt()
        val sb = StringBuilder()
        var i = 0
        fun flush() { if (sb.isNotEmpty()) { out.add(sb.toString() to color); sb.setLength(0) } }
        while (i < raw.length) {
            val c = raw[i]
            if ((c == '&' || c == '§') && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == '*') { sb.append('*'); i += 2; continue }
                if (next == '#' && i + 7 < raw.length) {
                    val hex = raw.substring(i + 2, i + 8)
                    if (hex.matches(Regex("[0-9a-fA-F]{6}"))) {
                        flush()
                        color = (0xFF shl 24) or hex.toInt(16)
                        i += 8
                        continue
                    }
                }
                val fmt = ChatFormatting.getByCode(next.lowercaseChar())
                if (fmt != null) {
                    flush()
                    if (fmt == ChatFormatting.RESET) color = 0xFFFFFFFF.toInt()
                    else fmt.color?.let { color = (0xFF shl 24) or it }
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        flush()
        return out
    }
}

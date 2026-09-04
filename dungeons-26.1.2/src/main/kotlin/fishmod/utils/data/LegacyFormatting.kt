package fishmod.utils.data

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

/** Parses a raw string containing legacy '&'/'§'-formatting codes (plus "&*" ✪ stars and "&#rrggbb"
 *  hex colors) into a real styled [Component], so custom item names typed with color/format codes
 *  in /fm customize actually render styled wherever the item's hover name is drawn (tooltips,
 *  hotbar, etc), not just as literal text. Mirrors [fishmod.cosmetic.NickState.parse]. */
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
                // "&*" inserts a SkyBlock star (✪) in the color set right before it.
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

    /** Runs of (text, argb color) for a cheap NanoVG-only preview — bold/italic/underline are
     *  applied to the real Component via [parse] but ignored here since NanoVG only has one face loaded. */
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
                // plain '*' here: the NanoVG Inter face has no ✪ glyph (the real name via MC's font keeps '✪' — see parse)
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

package fishmod.utils.data

import fishmod.utils.Constants
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.util.Formatting

object TextUtil {

    private class StyleTracker {
        var isBold = false
        var isItalic = false
        var isUnderlined = false
        var isStrikeThrough = false
        var isObfuscated = false
        var currentColor = 0

        override fun toString(): String {
            return "StyleTracker{" +
                    "isBold=" + isBold +
                    ", isItalic=" + isItalic +
                    ", isUnderlined=" + isUnderlined +
                    ", isStrikeThrough=" + isStrikeThrough +
                    ", isObfuscated=" + isObfuscated +
                    ", currentColor=" + currentColor +
                    '}'
        }
    }

    @JvmStatic
    fun orderedTextToString(text: OrderedText): String {
        val builder = StringBuilder()
        acceptOrderedText(builder, text)
        return builder.toString()
    }

    @JvmStatic
    fun acceptOrderedText(builder: StringBuilder, orderedText: OrderedText) {
        val tracker = StyleTracker()
        acceptOrderedText(builder, tracker, orderedText)
    }

    private fun acceptOrderedText(builder: StringBuilder, tracker: StyleTracker, orderedText: OrderedText) {
        orderedText.accept { _, style, codePoint ->
            acceptStyle(builder, tracker, style)
            builder.appendCodePoint(codePoint)
            true
        }
    }

    /** Appends the color codes missing between the tracked style and the current char's style. */
    private fun acceptStyle(builder: StringBuilder, tracker: StyleTracker, style: Style?) {
        if (style == null) return

        val color = style.color
        if (color != null && color.rgb != tracker.currentColor) {
            builder.append('§')
            builder.append(getFormatChar(color.rgb))
            tracker.currentColor = color.rgb
        }

        if (style.isObfuscated && !tracker.isObfuscated) {
            builder.append("§k")
            tracker.isObfuscated = true
        } else if (!style.isObfuscated && tracker.isObfuscated) {
            tracker.isObfuscated = false
        }

        if (style.isBold && !tracker.isBold) {
            builder.append("§l")
            tracker.isBold = true
        } else if (!style.isBold && tracker.isBold) {
            tracker.isBold = false
        }

        if (style.isStrikethrough && !tracker.isStrikeThrough) {
            builder.append("§m")
            tracker.isStrikeThrough = true
        } else if (!style.isStrikethrough && tracker.isStrikeThrough) {
            tracker.isStrikeThrough = false
        }

        if (style.isUnderlined && !tracker.isUnderlined) {
            builder.append("§n")
            tracker.isUnderlined = true
        } else if (!style.isUnderlined && tracker.isUnderlined) {
            tracker.isUnderlined = false
        }

        if (style.isItalic && !tracker.isItalic) {
            builder.append("§o")
            tracker.isItalic = true
        } else if (!style.isItalic && tracker.isItalic) {
            tracker.isItalic = false
        }
    }

    private fun getFormatChar(color: Int): Char {
        for (format in Formatting.entries) {
            val colorValue = format.colorValue ?: continue
            if (colorValue == color) {
                return format.code
            }
        }

        return '0'
    }

    @JvmStatic
    fun formatTicks(tick: Int): String {
        return Constants.DECIMAL_FORMAT.format(tick * Constants.TICK_DURATION)
    }

    @JvmStatic
    fun capitaliseFirst(message: String): String {
        val strippedMessage = message.trim()
        if (strippedMessage.length < 2) return message
        return strippedMessage.substring(0, 1).uppercase() + strippedMessage.substring(1).lowercase()
    }
}

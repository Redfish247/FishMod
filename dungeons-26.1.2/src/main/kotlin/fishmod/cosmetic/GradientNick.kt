package fishmod.cosmetic

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor

/** Builds a per-character &rrggbb gradient string over a fixed name (no custom text allowed). */
object GradientNick {

    /**
     * Interpolates the given RGB stops across the visible characters of `name`, preserving any
     * inline format codes (`&l`/`&o`/`&m`/`&n`/`&k`/`&r`). Color
     * codes inside the input are ignored — the gradient overrides them. Format codes are accumulated
     * as the input is scanned and re-emitted after every per-letter color code (because in Minecraft
     * a new color code resets formatting, so bold/italic would otherwise be wiped out).
     */
    @JvmStatic
    fun build(name: String?, stops: Array<IntArray>?): String? {
        if (name == null || name.isEmpty() || stops == null || stops.isEmpty()) return name

        // First pass: count visible characters (those that aren't part of a color/format code).
        var letterCount = 0
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if ((c == '&' || c == '§') && i + 1 < name.length) {
                val next = name[i + 1]
                if (next == '#' && i + 7 < name.length && name.substring(i + 2, i + 8).matches(Regex("[0-9a-fA-F]{6}"))) {
                    i += 7
                    i++
                    continue
                }
                val low = next.lowercaseChar()
                if ("0123456789abcdefxklmnor".indexOf(low) >= 0) {
                    i++
                    i++
                    continue
                }
            }
            letterCount++
            i++
        }
        if (letterCount == 0) return name

        // Second pass: emit each letter prefixed by the per-position color AND any active formats.
        val out = StringBuilder()
        val activeFormats = StringBuilder()
        var letterIdx = 0
        val solid = stops.size == 1
        val solidHex = if (solid) hex(stops[0]) else null

        i = 0
        while (i < name.length) {
            val c = name[i]
            if ((c == '&' || c == '§') && i + 1 < name.length) {
                val next = name[i + 1]
                if (next == '#' && i + 7 < name.length && name.substring(i + 2, i + 8).matches(Regex("[0-9a-fA-F]{6}"))) {
                    i += 7
                    i++
                    continue // hex color — overridden by gradient
                }
                val low = next.lowercaseChar()
                if ("0123456789abcdefx".indexOf(low) >= 0) {
                    activeFormats.setLength(0) // color codes reset formatting in MC
                    i++
                    i++
                    continue
                }
                if ("klmno".indexOf(low) >= 0) {
                    activeFormats.append('&').append(low)
                    i++
                    i++
                    continue
                }
                if (low == 'r') {
                    activeFormats.setLength(0)
                    i++
                    i++
                    continue
                }
            }
            // Visible character → emit color, formats, then the char.
            val colorPrefix: String?
            if (solid) {
                colorPrefix = solidHex
            } else {
                val t = if (letterCount == 1) 0.0 else letterIdx.toDouble() / (letterCount - 1)
                val scaled = t * (stops.size - 1)
                var seg = Math.floor(scaled).toInt()
                if (seg >= stops.size - 1) seg = stops.size - 2
                val lt = scaled - seg
                val a = stops[seg]
                val b = stops[seg + 1]
                colorPrefix = hex(intArrayOf(lerp(a[0], b[0], lt), lerp(a[1], b[1], lt), lerp(a[2], b[2], lt)))
            }
            out.append(colorPrefix).append(activeFormats).append(c)
            letterIdx++
            i++
        }
        return out.toString()
    }

    private fun lerp(a: Int, b: Int, t: Double): Int = Math.round(a + (b - a) * t).toInt()

    private fun hex(rgb: IntArray): String {
        return String.format("&#%02x%02x%02x", rgb[0] and 0xFF, rgb[1] and 0xFF, rgb[2] and 0xFF)
    }

    /** {r,g,b} from a packed ARGB/RGB int. */
    @JvmStatic
    fun rgb(packed: Int): IntArray {
        return intArrayOf((packed shr 16) and 0xFF, (packed shr 8) and 0xFF, packed and 0xFF)
    }

    /** Parses a hex (#rrggbb / rrggbb), color code (&a / a), or color name (red, dark_blue...). Null if invalid. */
    @JvmStatic
    fun parseColor(token: String?): IntArray? {
        if (token == null) return null
        var t = token.trim()
        if (t.startsWith("&")) t = t.substring(1)
        if (t.startsWith("#")) t = t.substring(1)
        if (t.matches(Regex("[0-9a-fA-F]{6}"))) {
            return intArrayOf(
                t.substring(0, 2).toInt(16),
                t.substring(2, 4).toInt(16),
                t.substring(4, 6).toInt(16)
            )
        }
        var f: ChatFormatting? = null
        if (t.length == 1) f = ChatFormatting.getByCode(t[0])
        if (f == null) {
            try {
                f = ChatFormatting.valueOf(t.uppercase())
            } catch (ignored: IllegalArgumentException) {
            }
        }
        if (f != null) {
            val tc = TextColor.fromLegacyFormat(f)
            if (tc != null) return rgb(tc.value)
        }
        return null
    }

    /** red→orange→yellow→green→blue→purple, for /nick rainbow. */
    @JvmStatic
    fun rainbow(): Array<IntArray> {
        return arrayOf(
            intArrayOf(255, 85, 85), intArrayOf(255, 170, 0), intArrayOf(255, 255, 85),
            intArrayOf(85, 255, 85), intArrayOf(85, 85, 255), intArrayOf(170, 0, 170)
        )
    }
}

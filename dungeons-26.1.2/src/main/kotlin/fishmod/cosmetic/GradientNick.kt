package fishmod.cosmetic

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor

object GradientNick {

    private val HEX6 = Regex("[0-9a-fA-F]{6}")

    @JvmStatic
    fun build(name: String?, stops: Array<IntArray>?): String? {
        if (name == null || name.isEmpty() || stops == null || stops.isEmpty()) return name

        var letterCount = 0
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if ((c == '&' || c == '§') && i + 1 < name.length) {
                val next = name[i + 1]
                if (next == '#' && i + 7 < name.length && name.substring(i + 2, i + 8).matches(HEX6)) {
                    i += 8
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
                if (next == '#' && i + 7 < name.length && name.substring(i + 2, i + 8).matches(HEX6)) {
                    i += 8
                    continue
                }
                val low = next.lowercaseChar()
                if ("0123456789abcdefx".indexOf(low) >= 0) {
                    activeFormats.setLength(0)
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
                colorPrefix = hex(mixOklab(a, b, lt))
            }
            out.append(colorPrefix).append(activeFormats).append(c)
            letterIdx++
            i++
        }
        return out.toString()
    }

    // Blend in OKLab so brightness stays even; raw RGB lerp makes light/dark bands between hues
    private fun mixOklab(a: IntArray, b: IntArray, t: Double): IntArray {
        val la = toOklab(a)
        val lb = toOklab(b)
        return fromOklab(DoubleArray(3) { la[it] + (lb[it] - la[it]) * t })
    }

    private fun toLinear(c: Int): Double {
        val v = (c and 0xFF) / 255.0
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun toSrgb(v: Double): Int {
        val c = if (v <= 0.0031308) v * 12.92 else 1.055 * Math.pow(v, 1 / 2.4) - 0.055
        return Math.round(c.coerceIn(0.0, 1.0) * 255).toInt()
    }

    private fun toOklab(rgb: IntArray): DoubleArray {
        val r = toLinear(rgb[0]); val g = toLinear(rgb[1]); val b = toLinear(rgb[2])
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromOklab(lab: DoubleArray): IntArray {
        val l = Math.pow(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2], 3.0)
        val m = Math.pow(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2], 3.0)
        val s = Math.pow(lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2], 3.0)
        return intArrayOf(
            toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        )
    }

    private fun hex(rgb: IntArray): String {
        return String.format("&#%02x%02x%02x", rgb[0] and 0xFF, rgb[1] and 0xFF, rgb[2] and 0xFF)
    }

    @JvmStatic
    fun rgb(packed: Int): IntArray {
        return intArrayOf((packed shr 16) and 0xFF, (packed shr 8) and 0xFF, packed and 0xFF)
    }

    @JvmStatic
    fun parseColor(token: String?): IntArray? {
        if (token == null) return null
        var t = token.trim()
        if (t.startsWith("&")) t = t.substring(1)
        if (t.startsWith("#")) t = t.substring(1)
        if (t.matches(HEX6)) {
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

    @JvmStatic
    fun rainbow(): Array<IntArray> {
        return arrayOf(
            intArrayOf(255, 85, 85), intArrayOf(255, 170, 0), intArrayOf(255, 255, 85),
            intArrayOf(85, 255, 85), intArrayOf(85, 85, 255), intArrayOf(170, 0, 170)
        )
    }
}

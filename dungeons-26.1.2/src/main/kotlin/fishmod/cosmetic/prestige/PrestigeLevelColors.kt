package fishmod.cosmetic.prestige

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional

object PrestigeLevelColors {

    private const val TIER_WIDTH = 20
    private const val MAX_LEVEL = 700

    private const val WHITE = 0xFFFFFF
    private const val GRAY = 0xB9C2CF
    private const val DARK_GRAY = 0x5B6472
    private const val BLACK = 0x17171C
    private const val AQUA = 0x59C4FF
    private const val DARK_AQUA = 0x21E6C1
    private const val BLUE = 0x4D7CFF
    private const val DARK_BLUE = 0x2740E0
    private const val GREEN = 0x86FF4D
    private const val DARK_GREEN = 0x23B94B
    private const val YELLOW = 0xFFDA2E
    private const val GOLD = 0xFFA92E
    private const val LIGHT_PURPLE = 0xFF6FC5
    private const val DARK_PURPLE = 0xB061F0
    private const val RED = 0xFF4757
    private const val DARK_RED = 0xC31F2C
    private const val PINK = 0xFFB3D9
    private const val MID_GREEN = 0x3FD467

    class Tier(
        @JvmField val index: Int,
        @JvmField val name: String,
        @JvmField val gradient: Boolean,
        @JvmField val a: Int,
        @JvmField val b: Int,
        @JvmField val c: Int,
    ) {
        val lo: Int get() = index * TIER_WIDTH
        val hi: Int get() = lo + TIER_WIDTH
    }

    private fun solid(i: Int, name: String, rgb: Int) = Tier(i, name, false, rgb, rgb, rgb)
    private fun grad(i: Int, name: String, a: Int, b: Int, c: Int) = Tier(i, name, true, a, b, c)

    @JvmField
    val TIERS: Array<Tier> = arrayOf(
        solid(0, "White", WHITE),
        solid(1, "Gray", GRAY),
        solid(2, "Light Blue", AQUA),
        solid(3, "Cyan", DARK_AQUA),
        solid(4, "Blue", BLUE),
        solid(5, "Lime", GREEN),
        solid(6, "Dark Green", DARK_GREEN),
        solid(7, "Yellow", YELLOW),
        solid(8, "Gold", GOLD),
        solid(9, "Pink", LIGHT_PURPLE),
        solid(10, "Purple", DARK_PURPLE),
        solid(11, "Red", RED),
        solid(12, "Dark Red", DARK_RED),
        solid(13, "Dark Gray", DARK_GRAY),
        solid(14, "Black", BLACK),
        grad(15, "Smooth Dark Gradient", GRAY, DARK_GRAY, BLACK),
        grad(16, "Bright Spring", WHITE, YELLOW, GREEN),
        grad(17, "Cool Oceanside", DARK_GREEN, DARK_AQUA, BLUE),
        grad(18, "Ender Pastel", PINK, DARK_PURPLE, LIGHT_PURPLE),
        grad(19, "Nether Fire", GOLD, RED, DARK_RED),
        grad(20, "Radioactive Slime", GRAY, GREEN, MID_GREEN),
        grad(21, "Forest Shadow", DARK_GRAY, DARK_GREEN, MID_GREEN),
        grad(22, "Glacial Frost", DARK_BLUE, AQUA, WHITE),
        grad(23, "Classic Royal", DARK_BLUE, RED, WHITE),
        grad(24, "Deep Sea Trench", DARK_GRAY, BLUE, AQUA),
        grad(25, "Vampiric Nebula", DARK_PURPLE, LIGHT_PURPLE, RED),
        grad(26, "Crimson Void", BLACK, DARK_RED, RED),
        grad(27, "Solar Flare", GOLD, YELLOW, WHITE),
        grad(28, "Cyber Sunset", DARK_AQUA, LIGHT_PURPLE, GOLD),
        grad(29, "Ancient Relic", BLACK, DARK_AQUA, GOLD),
        grad(30, "Twilight Sky", DARK_BLUE, DARK_PURPLE, DARK_AQUA),
        grad(31, "Cherry Blossom", RED, LIGHT_PURPLE, WHITE),
        grad(32, "Desolation Crown", DARK_RED, DARK_GRAY, GOLD),
        grad(33, "Matrix Grid", BLACK, WHITE, DARK_AQUA),
        grad(34, "The Ultimate Prestige", BLACK, GOLD, WHITE),
    )

    @JvmStatic
    fun tierAt(level: Int): Tier {
        val v = level.coerceIn(0, MAX_LEVEL)
        val idx = (v / TIER_WIDTH).coerceAtMost(TIERS.size - 1)
        return TIERS[idx]
    }

    private fun localFraction(level: Int): Float {
        val t = tierAt(level)
        return ((level.coerceIn(0, MAX_LEVEL) - t.lo).toFloat() / TIER_WIDTH).coerceIn(0f, 1f)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = (a ushr 16) and 0xFF; val ag = (a ushr 8) and 0xFF; val ab = a and 0xFF
        val br = (b ushr 16) and 0xFF; val bg = (b ushr 8) and 0xFF; val bb = b and 0xFF
        val r = Math.round(ar + (br - ar) * tt)
        val g = Math.round(ag + (bg - ag) * tt)
        val bl = Math.round(ab + (bb - ab) * tt)
        return (r shl 16) or (g shl 8) or bl
    }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    @JvmStatic
    fun gradientRgb(t: Tier, frac: Float): Int {
        val f = frac.coerceIn(0f, 1f)
        return if (f <= 0.5f) lerp(t.a, t.b, smoothstep(f / 0.5f))
        else lerp(t.b, t.c, smoothstep((f - 0.5f) / 0.5f))
    }

    fun cyclicGradientRgb(t: Tier, p: Float): Int {
        val x = p - Math.floor(p.toDouble()).toFloat()
        return when {
            x < 0.25f -> lerp(t.a, t.b, smoothstep(x / 0.25f))
            x < 0.50f -> lerp(t.b, t.c, smoothstep((x - 0.25f) / 0.25f))
            x < 0.75f -> lerp(t.c, t.b, smoothstep((x - 0.50f) / 0.25f))
            else      -> lerp(t.b, t.a, smoothstep((x - 0.75f) / 0.25f))
        }
    }

    private val START_NANOS = System.nanoTime()
    private fun animSeconds(): Double = (System.nanoTime() - START_NANOS) / 1_000_000_000.0

    @JvmStatic
    fun rgb(level: Int): Int {
        val t = tierAt(level)
        if (!t.gradient) return t.a
        if (!FishSettings.prestigeColorsGradientTiers) return t.b
        return gradientRgb(t, localFraction(level))
    }

    private val LEVEL_PREFIX = Regex("""^\s{0,2}\[(\d{1,4})[^\[\]\d]{0,4}]""")
    private val LEVEL_ANYWHERE = Regex("""\[(\d{1,4})[^\[\]\d]{0,4}]""")

    @JvmField var debug = false
    private val seenByTag = HashMap<String, HashSet<String>>()

    @JvmStatic
    fun dbg(tag: String, raw: String?) {
        if (!debug || raw == null) return
        val set = seenByTag.getOrPut(tag) { HashSet() }
        if (set.size < 10 && set.add(raw)) {
            fishmod.utils.debug.Debug.LOGGER.info("[PrestigeDBG] $tag raw=\"$raw\"")
        }
    }

    @JvmStatic
    fun colorizeLevelPrefix(c: Component?): Component? = recolor(c, LEVEL_PREFIX, "prefix")

    @JvmStatic
    fun colorizeChatLevel(c: Component?): Component? {
        if (!FishSettings.prestigeColorsChat) return c
        return recolor(c, LEVEL_ANYWHERE, "chat")
    }

    private fun recolor(c: Component?, pattern: Regex, tag: String): Component? {
        if (c == null || !FishSettings.prestigeColorsEnabled) return c

        val segs = ArrayList<Seg>()
        c.visit({ style, text -> if (text.isNotEmpty()) segs.add(Seg(text, style)); Optional.empty<Any>() }, Style.EMPTY)
        if (segs.isEmpty()) return c
        val full = buildString { for (s in segs) append(s.text) }

        val clean = StringBuilder(full.length)
        val mapToFull = IntArray(full.length)
        var i = 0
        while (i < full.length) {
            if (full[i] == '§' && i + 1 < full.length) { i += 2; continue }
            mapToFull[clean.length] = i
            clean.append(full[i]); i++
        }
        val cleanStr = clean.toString()

        val m = pattern.find(cleanStr)
        if (debug) dbg(tag, full)
        if (m == null) return c
        val digits = m.groups[1] ?: return c
        val level = digits.value.toIntOrNull() ?: return c
        val numFrom = mapToFull[digits.range.first]
        val numTo = mapToFull[digits.range.last] + 1

        val out: MutableComponent = Component.empty()
        appendRange(out, segs, 0, numFrom)
        out.append(styledNumber(level, digits.value, styleAt(segs, numFrom)))
        appendRange(out, segs, numTo, full.length)
        return out
    }

    private fun styleAt(segs: List<Seg>, idx: Int): Style {
        var pos = 0
        for (s in segs) {
            if (idx < pos + s.text.length) return s.style
            pos += s.text.length
        }
        return segs.first().style
    }

    private fun styledNumber(level: Int, text: String, baseStyle: Style): MutableComponent {
        val t = tierAt(level)
        val useGradient = t.gradient && FishSettings.prestigeColorsGradientTiers
        if (!useGradient) {
            return Component.literal(text).setStyle(baseStyle.withColor(TextColor.fromRgb(rgb(level))))
        }
        val speed = FishSettings.prestigeColorsAnimSpeed
        val animated = FishSettings.prestigeColorsAnimated && speed > 0.0
        val phase = if (animated) (animSeconds() * speed / 5.0).toFloat() else 0f
        val digitSpan = if (FishSettings.prestigeColorsAnimStyle.equals("FLOW", true)) 0.6f else 0.05f
        val n = text.length
        val root: MutableComponent = Component.empty()
        var runStart = 0
        var runRgb = -1
        for (i in 0 until n) {
            val spread = if (n <= 1) 0.5f else i.toFloat() / (n - 1)
            val rgb = if (animated) {
                cyclicGradientRgb(t, spread * digitSpan - phase)
            } else {
                gradientRgb(t, spread)
            }
            if (runRgb == -1) runRgb = rgb
            if (rgb != runRgb) {
                root.append(Component.literal(text.substring(runStart, i)).setStyle(baseStyle.withColor(TextColor.fromRgb(runRgb))))
                runStart = i
                runRgb = rgb
            }
        }
        root.append(Component.literal(text.substring(runStart, n)).setStyle(baseStyle.withColor(TextColor.fromRgb(runRgb))))
        return root
    }

    private class Seg(@JvmField val text: String, @JvmField val style: Style)

    private fun appendRange(out: MutableComponent, segs: List<Seg>, from: Int, to: Int) {
        if (from >= to) return
        var pos = 0
        for (s in segs) {
            val segEnd = pos + s.text.length
            if (segEnd <= from) { pos = segEnd; continue }
            if (pos >= to) break
            val a = maxOf(0, from - pos)
            val b = minOf(s.text.length, to - pos)
            if (b > a) out.append(Component.literal(s.text.substring(a, b)).setStyle(s.style))
            pos = segEnd
        }
    }
}

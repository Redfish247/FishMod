package fishmod.cosmetic.prestige

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional

/**
 * Re-skins the Hypixel SkyBlock level badge — the leading "[123]" on player nametags and tab-list
 * entries — with a level-driven colour progression instead of Hypixel's own steps.
 *
 * 0–300: 15 solid tiers of width 20, each the vanilla §-colour it is named after.
 * 300–700: 20 three-stop (A→B→C) gradient tiers of width 20, optionally animated.
 * Past 700 the final tier holds.
 *
 * Tier table is the palette agreed with Eli (see Obsidian: "Prestige Colors Addon (standalone)").
 * Two readings that the spec left implicit:
 *   - gradient stop "Pink" uses a real pastel pink (0xFFA6C9), not §d, so "Ender Pastel" reads
 *     pink→purple→light-purple rather than repeating §d twice.
 *   - gradient stop "Green" (Radioactive Slime / Forest Shadow) uses a mid green (0x22AA22),
 *     distinct from the "Lime" (§a) and "Dark Green" (§2) solid tiers.
 */
object PrestigeLevelColors {

    private const val TIER_WIDTH = 20
    private const val MAX_LEVEL = 700

    // Tuned full-RGB palette — same tier identities as the vanilla §-colours they're named after,
    // but hand-picked hex so every tier (and every gradient stop) reads richer than the flat 16.
    private const val WHITE = 0xFFFFFF
    private const val GRAY = 0xB9C2CF
    private const val DARK_GRAY = 0x5B6472
    private const val BLACK = 0x17171C
    private const val AQUA = 0x59C4FF          // "Light Blue"
    private const val DARK_AQUA = 0x21E6C1     // "Cyan"
    private const val BLUE = 0x4D7CFF
    private const val DARK_BLUE = 0x2740E0
    private const val GREEN = 0x86FF4D         // "Lime"
    private const val DARK_GREEN = 0x23B94B
    private const val YELLOW = 0xFFDA2E
    private const val GOLD = 0xFFA92E
    private const val LIGHT_PURPLE = 0xFF6FC5  // "Pink" (solid tier) / "Light Purple" (gradient stop)
    private const val DARK_PURPLE = 0xB061F0   // "Purple"
    private const val RED = 0xFF4757
    private const val DARK_RED = 0xC31F2C
    private const val PINK = 0xFFB3D9          // gradient-stop pastel "Pink"
    private const val MID_GREEN = 0x3FD467     // gradient-stop "Green"

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
        // --- 15 solid tiers, 0–300 -------------------------------------------------
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
        // --- 20 gradient tiers, 300–700 (A → B → C) ------------------------------
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

    /** Three-stop A→B→C interpolation across a gradient tier, with a smoothstep ease on each half. */
    @JvmStatic
    fun gradientRgb(t: Tier, frac: Float): Int {
        val f = frac.coerceIn(0f, 1f)
        return if (f <= 0.5f) lerp(t.a, t.b, smoothstep(f / 0.5f))
        else lerp(t.b, t.c, smoothstep((f - 0.5f) / 0.5f))
    }

    /**
     * Seamless looping colour ramp A→B→C→B→A over [p] mod 1, smoothstep on each quarter. Because
     * the ends meet (p and p+1 both give A) a phase that drifts with time slides the band along the
     * text with no jump — a fade, not a flash.
     */
    fun cyclicGradientRgb(t: Tier, p: Float): Int {
        val x = p - Math.floor(p.toDouble()).toFloat() // wrap into [0,1), negatives included
        return when {
            x < 0.25f -> lerp(t.a, t.b, smoothstep(x / 0.25f))
            x < 0.50f -> lerp(t.b, t.c, smoothstep((x - 0.25f) / 0.25f))
            x < 0.75f -> lerp(t.c, t.b, smoothstep((x - 0.50f) / 0.25f))
            else      -> lerp(t.b, t.a, smoothstep((x - 0.75f) / 0.25f))
        }
    }

    private val START_NANOS = System.nanoTime()
    private fun animSeconds(): Double = (System.nanoTime() - START_NANOS) / 1_000_000_000.0

    /** Flat representative colour for a level, honouring the "Gradient Tiers" toggle. */
    @JvmStatic
    fun rgb(level: Int): Int {
        val t = tierAt(level)
        if (!t.gradient) return t.a
        if (!FishSettings.prestigeColorsGradientTiers) return t.b
        return gradientRgb(t, localFraction(level))
    }

    // ---------------------------------------------------------------------
    // Component recolouring — the entry point both mixins call
    // ---------------------------------------------------------------------

    // "[123]" optionally with an emblem glyph before the closing bracket ("[123✿]").
    // Anchored form (nametags / tab): only up to a couple of leading spaces before it.
    private val LEVEL_PREFIX = Regex("""^\s{0,2}\[(\d{1,4})[^\[\]\d]{0,4}]""")
    // Unanchored form (chat): the first such badge anywhere in the line.
    private val LEVEL_ANYWHERE = Regex("""\[(\d{1,4})[^\[\]\d]{0,4}]""")

    // one-shot diagnostics: log the first few distinct strings per call-site
    @JvmField var debug = true
    private val seenByTag = HashMap<String, HashSet<String>>()

    @JvmStatic
    fun dbg(tag: String, raw: String?) {
        if (!debug || raw == null) return
        val set = seenByTag.getOrPut(tag) { HashSet() }
        if (set.size < 10 && set.add(raw)) {
            fishmod.utils.debug.Debug.LOGGER.info("[PrestigeDBG] $tag raw=\"$raw\"")
        }
    }

    /**
     * If [c] begins with a "[123]" SkyBlock level badge, return a copy with just the **number**
     * recoloured by its prestige tier. The brackets, any emblem glyph, and the rest of the name
     * keep their original styling; otherwise [c] is returned unchanged. Used for nametags + tab.
     */
    @JvmStatic
    fun colorizeLevelPrefix(c: Component?): Component? = recolor(c, LEVEL_PREFIX, "prefix")

    /**
     * Chat lines: recolour the number in the **first** "[123]" badge anywhere in the line (covers
     * "Guild > [123] Name: ...", "[123] Name: ...", party/co-op/whisper prefixes, etc).
     */
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

        // Some sources (Hypixel lobby tab) hand us a flat string with literal "§x" codes rather
        // than styled sub-components. Match against a code-free copy, keeping a map back to `full`.
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
        appendRange(out, segs, 0, numFrom)                 // everything up to the number — untouched
        out.append(styledNumber(level, digits.value, styleAt(segs, numFrom))) // recoloured number only
        appendRange(out, segs, numTo, full.length)         // emblem + "]" + rest of the line — untouched
        return out
    }

    /** Style in effect at flattened-text index [idx] (the number's own segment style). */
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
        // phase drifts ~1 full loop every 5s at speed 1; the band slides across the number.
        val phase = if (animated) (animSeconds() * speed / 5.0).toFloat() else 0f
        val n = text.length
        val root: MutableComponent = Component.empty()
        for (i in 0 until n) {
            val spread = if (n <= 1) 0.5f else i.toFloat() / (n - 1)
            val rgb = if (animated) {
                // 0.6 of the loop spans the digits, minus phase = flow left→right, seamless wrap
                cyclicGradientRgb(t, spread * 0.6f - phase)
            } else {
                gradientRgb(t, spread) // static A→B→C across the number
            }
            root.append(
                Component.literal(text[i].toString())
                    .setStyle(baseStyle.withColor(TextColor.fromRgb(rgb)))
            )
        }
        return root
    }

    private class Seg(@JvmField val text: String, @JvmField val style: Style)

    /** Append characters [from, to) of the flattened text, each keeping its own segment's style. */
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

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

    // vanilla §-colour RGBs
    private const val WHITE = 0xFFFFFF
    private const val GRAY = 0xAAAAAA
    private const val DARK_GRAY = 0x555555
    private const val BLACK = 0x000000
    private const val AQUA = 0x55FFFF          // "Light Blue"
    private const val DARK_AQUA = 0x00AAAA     // "Cyan"
    private const val BLUE = 0x5555FF
    private const val DARK_BLUE = 0x0000AA
    private const val GREEN = 0x55FF55         // "Lime"
    private const val DARK_GREEN = 0x00AA00
    private const val YELLOW = 0xFFFF55
    private const val GOLD = 0xFFAA00
    private const val LIGHT_PURPLE = 0xFF55FF  // "Pink" (solid tier) / "Light Purple" (gradient stop)
    private const val DARK_PURPLE = 0xAA00AA   // "Purple"
    private const val RED = 0xFF5555
    private const val DARK_RED = 0xAA0000
    private const val PINK = 0xFFA6C9          // gradient-stop "Pink"
    private const val MID_GREEN = 0x22AA22     // gradient-stop "Green"

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

    // "[123]" optionally with an emblem glyph before the closing bracket ("[123✿]"), after up to
    // a couple of leading spaces. Anchored at the string start.
    private val LEVEL_PREFIX = Regex("""^\s{0,2}\[(\d{1,4})[^\[\]\d]{0,4}]""")

    // one-shot diagnostics: log the first few distinct name strings we're handed
    @JvmField var debug = false
    private val seen = HashSet<String>()

    /**
     * If [c] begins with a "[123]" SkyBlock level badge, return a copy with that badge recoloured
     * by its prestige tier; otherwise return [c] unchanged. Everything after the "]" keeps its
     * original styling.
     */
    @JvmStatic
    fun colorizeLevelPrefix(c: Component?): Component? {
        if (c == null || !FishSettings.prestigeColorsEnabled) return c

        val segs = ArrayList<Seg>()
        c.visit({ style, text -> if (text.isNotEmpty()) segs.add(Seg(text, style)); Optional.empty<Any>() }, Style.EMPTY)
        if (segs.isEmpty()) return c
        val full = buildString { for (s in segs) append(s.text) }

        val m = LEVEL_PREFIX.find(full)
        if (seen.size < 12 && seen.add(full)) {
            fishmod.utils.debug.Debug.LOGGER.info(
                "[PrestigeDBG] match=${m != null} lvl=${m?.groupValues?.get(1)} segs=${segs.size} raw=\"$full\""
            )
        }
        if (m == null) return c
        val level = m.groupValues[1].toIntOrNull() ?: return c
        val cut = m.range.last + 1

        val baseStyle = segs.first().style
        val out: MutableComponent = Component.empty()
        out.append(styledPrefix(level, full.substring(0, cut), baseStyle))
        appendRange(out, segs, cut, full.length)
        return out
    }

    private fun styledPrefix(level: Int, text: String, baseStyle: Style): MutableComponent {
        val t = tierAt(level)
        val useGradient = t.gradient && FishSettings.prestigeColorsGradientTiers
        if (!useGradient) {
            return Component.literal(text).setStyle(baseStyle.withColor(TextColor.fromRgb(rgb(level))))
        }
        val speed = FishSettings.prestigeColorsAnimSpeed
        val shimmer = if (FishSettings.prestigeColorsAnimated && speed > 0.0)
            (Math.sin(animSeconds() * speed * 0.5 * 2.0 * Math.PI) * 0.15).toFloat() else 0f
        val base = 0.30f * localFraction(level)
        val n = text.length
        val root: MutableComponent = Component.empty()
        for (i in 0 until n) {
            val spread = if (n <= 1) 0f else i.toFloat() / (n - 1)
            val frac = (base + 0.70f * spread + shimmer).coerceIn(0f, 1f)
            root.append(
                Component.literal(text[i].toString())
                    .setStyle(baseStyle.withColor(TextColor.fromRgb(gradientRgb(t, frac))))
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

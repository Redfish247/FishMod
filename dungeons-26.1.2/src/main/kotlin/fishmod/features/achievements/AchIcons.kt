package fishmod.features.achievements

import fishmod.utils.rendering.UiRecorder
import kotlin.math.cos
import kotlin.math.sin

// Vector glyphs for achievement tiles: one per section, tinted by difficulty
object AchIcons {

    fun diffColor(diff: String): Int = when (diff) {
        "Easy" -> 0xFF4ADE80.toInt()
        "Medium" -> 0xFF38BDF8.toInt()
        "Hard" -> 0xFFA78BFA.toInt()
        "Insane" -> 0xFFFB7185.toInt()
        "Impossible" -> 0xFFF2C14E.toInt()
        else -> 0xFFC77DFF.toInt()
    }

    fun dim(c: Int, f: Float): Int {
        val r = ((c shr 16 and 255) * f).toInt(); val g = ((c shr 8 and 255) * f).toInt(); val b = ((c and 255) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private val KIND = mapOf(
        "Money" to "coin", "Diana Profit" to "coin", "Bazaar & Auction House" to "coins",
        "Dungeons" to "gate", "Secrets" to "chest", "Secret Achievements" to "eye", "Puzzles" to "puzzle",
        "F7/M7 Terminals" to "terminal", "Floor Progression" to "stairs", "Master Mode" to "crown",
        "M7 Speedrunning" to "bolt", "Kuudra" to "skull", "Fishing" to "fish", "FishMod / General" to "fish",
        "Slayer" to "sword", "Farming" to "sprout", "Mining" to "pick", "Garden" to "flower",
        "Foraging & Hunting" to "tree", "Burrows" to "shovel", "Inquisitors" to "skull", "Mythological Mobs" to "shield",
        "Diana RNG" to "dice", "Diana Events" to "calendar", "Crimson Isle" to "flame", "Rift" to "portal",
        "Pets" to "paw", "Museum & Collections" to "museum", "Minions" to "minion",
        "Events & Misc" to "star", "Ultimate" to "star", "Playtime (All parts of game)" to "clock", "Skill Levels" to "arrows",
    )

    fun draw(section: String, cx: Float, cy: Float, r: Float, c: Int) {
        val lw = r * 0.2f
        fun l(x0: Float, y0: Float, x1: Float, y1: Float) = UiRecorder.line(cx + x0 * r, cy + y0 * r, cx + x1 * r, cy + y1 * r, lw, c)
        fun poly(vararg p: Float) { var i = 0; while (i + 3 < p.size) { l(p[i], p[i + 1], p[i + 2], p[i + 3]); i += 2 } }
        fun dot(x: Float, y: Float, rad: Float) = UiRecorder.disc(cx + x * r, cy + y * r, rad * r, c)
        fun ring(x: Float, y: Float, rad: Float) = UiRecorder.roundedRectRing(cx + (x - rad) * r, cy + (y - rad) * r, rad * 2 * r, rad * 2 * r, rad * r, lw, 0, c)
        fun box(x: Float, y: Float, w: Float, h: Float, rad: Float = 0.12f) = UiRecorder.roundedRectRing(cx + x * r, cy + y * r, w * r, h * r, rad * r, lw, 0, c)
        fun fill(x: Float, y: Float, w: Float, h: Float, rad: Float = 0.1f) = UiRecorder.fillRoundedRect(cx + x * r, cy + y * r, w * r, h * r, rad * r, c)
        when (KIND[section] ?: "star") {
            "coin" -> { ring(0f, 0f, 0.85f); dot(0f, 0f, 0.3f) }
            "coins" -> { ring(-0.25f, 0.25f, 0.55f); ring(0.3f, -0.25f, 0.55f) }
            "gate" -> { poly(-0.65f, 0.85f, -0.65f, -0.15f, -0.35f, -0.65f, 0f, -0.85f, 0.35f, -0.65f, 0.65f, -0.15f, 0.65f, 0.85f); l(-0.9f, 0.85f, 0.9f, 0.85f); fill(-0.15f, 0.1f, 0.3f, 0.75f, 0.05f) }
            "chest" -> { box(-0.85f, -0.35f, 1.7f, 1.2f); l(-0.85f, 0.1f, 0.85f, 0.1f); dot(0f, 0.1f, 0.18f) }
            "eye" -> { poly(-0.95f, 0f, -0.45f, -0.5f, 0f, -0.62f, 0.45f, -0.5f, 0.95f, 0f, 0.45f, 0.5f, 0f, 0.62f, -0.45f, 0.5f, -0.95f, 0f); dot(0f, 0f, 0.28f) }
            "puzzle" -> { fill(-0.75f, -0.45f, 1.3f, 1.2f, 0.12f); dot(-0.1f, -0.55f, 0.28f); dot(0.65f, 0.15f, 0.28f) }
            "terminal" -> { box(-0.9f, -0.7f, 1.8f, 1.4f); poly(-0.55f, -0.3f, -0.15f, 0f, -0.55f, 0.3f); l(0f, 0.32f, 0.5f, 0.32f) }
            "stairs" -> { fill(-0.9f, 0.3f, 0.65f, 0.55f, 0.05f); fill(-0.3f, -0.15f, 0.65f, 1f, 0.05f); fill(0.3f, -0.6f, 0.6f, 1.45f, 0.05f) }
            "crown" -> { poly(-0.85f, 0.55f, -0.85f, -0.5f, -0.4f, 0f, 0f, -0.75f, 0.4f, 0f, 0.85f, -0.5f, 0.85f, 0.55f, -0.85f, 0.55f); fill(-0.85f, 0.55f, 1.7f, 0.25f, 0.05f) }
            "bolt" -> poly(0.25f, -0.95f, -0.55f, 0.1f, 0f, 0.1f, -0.25f, 0.95f, 0.55f, -0.2f, 0f, -0.2f, 0.25f, -0.95f)
            "skull" -> { ring(0f, -0.15f, 0.7f); dot(-0.28f, -0.2f, 0.17f); dot(0.28f, -0.2f, 0.17f); l(-0.3f, 0.55f, -0.3f, 0.9f); l(0f, 0.55f, 0f, 0.9f); l(0.3f, 0.55f, 0.3f, 0.9f) }
            "fish" -> { box(-0.9f, -0.45f, 1.5f, 0.9f, 0.45f); poly(0.6f, 0f, 0.95f, -0.45f, 0.95f, 0.45f, 0.6f, 0f); dot(-0.45f, -0.1f, 0.1f) }
            "sword" -> { l(-0.75f, 0.75f, 0.8f, -0.8f); l(-0.6f, 0f, 0f, 0.6f); dot(-0.8f, 0.8f, 0.12f) }
            "sprout" -> { l(0f, 0.9f, 0f, -0.1f); poly(0f, 0.2f, -0.45f, 0.1f, -0.7f, -0.35f, -0.25f, -0.3f, 0f, 0.2f); poly(0f, -0.1f, 0.4f, -0.2f, 0.7f, -0.7f, 0.25f, -0.65f, 0f, -0.1f) }
            "pick" -> { l(-0.7f, 0.85f, 0.35f, -0.3f); poly(-0.6f, -0.45f, -0.15f, -0.8f, 0.4f, -0.8f, 0.85f, -0.45f, 0.9f, 0.1f) }
            "flower" -> { for (i in 0 until 5) { val a = i * 1.2566f - 1.5708f; dot(cos(a) * 0.5f, sin(a) * 0.5f, 0.3f) }; dot(0f, 0f, 0.25f) }
            "tree" -> { poly(0f, -0.9f, -0.6f, -0.1f, -0.3f, -0.1f, -0.75f, 0.5f, 0.75f, 0.5f, 0.3f, -0.1f, 0.6f, -0.1f, 0f, -0.9f); l(0f, 0.5f, 0f, 0.9f) }
            "shovel" -> { l(0.7f, -0.85f, -0.15f, 0.1f); l(0.45f, -0.55f, 0.85f, -0.2f); fill(-0.75f, 0.05f, 0.7f, 0.85f, 0.3f) }
            "shield" -> poly(-0.7f, -0.7f, 0.7f, -0.7f, 0.7f, 0.05f, 0f, 0.9f, -0.7f, 0.05f, -0.7f, -0.7f)
            "dice" -> { box(-0.8f, -0.8f, 1.6f, 1.6f, 0.3f); dot(-0.35f, -0.35f, 0.14f); dot(0.35f, -0.35f, 0.14f); dot(0f, 0f, 0.14f); dot(-0.35f, 0.35f, 0.14f); dot(0.35f, 0.35f, 0.14f) }
            "calendar" -> { box(-0.85f, -0.6f, 1.7f, 1.45f); l(-0.85f, -0.15f, 0.85f, -0.15f); l(-0.4f, -0.85f, -0.4f, -0.45f); l(0.4f, -0.85f, 0.4f, -0.45f); dot(0f, 0.4f, 0.2f) }
            "flame" -> { poly(0f, -0.95f, 0.55f, -0.2f, 0.55f, 0.45f, 0f, 0.9f, -0.55f, 0.45f, -0.55f, -0.2f, 0f, -0.95f); dot(0f, 0.35f, 0.25f) }
            "portal" -> { ring(0f, 0f, 0.9f); ring(0f, 0f, 0.5f); dot(0f, 0f, 0.15f) }
            "paw" -> { dot(0f, 0.4f, 0.45f); dot(-0.65f, -0.1f, 0.2f); dot(-0.25f, -0.6f, 0.2f); dot(0.25f, -0.6f, 0.2f); dot(0.65f, -0.1f, 0.2f) }
            "museum" -> { poly(-0.9f, -0.35f, 0f, -0.9f, 0.9f, -0.35f, -0.9f, -0.35f); l(-0.55f, -0.1f, -0.55f, 0.55f); l(0f, -0.1f, 0f, 0.55f); l(0.55f, -0.1f, 0.55f, 0.55f); l(-0.9f, 0.8f, 0.9f, 0.8f) }
            "minion" -> { box(-0.7f, -0.5f, 1.4f, 1.2f); dot(-0.3f, 0f, 0.15f); dot(0.3f, 0f, 0.15f); l(0f, -0.5f, 0f, -0.85f); dot(0f, -0.9f, 0.1f) }
            "clock" -> { ring(0f, 0f, 0.85f); l(0f, 0f, 0f, -0.55f); l(0f, 0f, 0.4f, 0.2f) }
            "arrows" -> { poly(-0.6f, 0.05f, 0f, -0.55f, 0.6f, 0.05f); poly(-0.6f, 0.65f, 0f, 0.05f, 0.6f, 0.65f) }
            else -> {
                val p = FloatArray(22)
                for (i in 0..10) { val a = i * 0.62832f - 1.5708f; val rr = if (i % 2 == 0) 0.95f else 0.4f; p[i * 2] = cos(a) * rr; p[i * 2 + 1] = sin(a) * rr }
                poly(*p)
            }
        }
    }
}

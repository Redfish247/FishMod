package fishmod.utils.rendering

import net.minecraft.client.Minecraft

/**
 * Extra shrink factor applied to FishMod's custom NanoVG screens (the /fm settings panel and its
 * sibling popups), on top of whatever Minecraft's own GUI scale already does.
 *
 * These screens lay themselves out in fixed logical-pixel amounts. Minecraft's GUI scale controls
 * how many logical pixels fit on screen (higher scale = fewer, larger logical pixels), so a panel
 * sized in constant logical pixels eats a bigger share of the screen as GUI scale climbs — the
 * layout was tuned to look right at scale 1-2 and becomes oversized at scale 3+. [factor] cancels
 * that growth above [REFERENCE_GUI_SCALE], then applies [FLAT_SHRINK] on top so the panel reads
 * smaller everywhere, not just at high scale.
 */
object UiScale {
    private const val REFERENCE_GUI_SCALE = 2.0
    private const val FLAT_SHRINK = 0.77f

    // FishModScreen's sibling popups (Chat Notifications, Command Aliases/Keys, Loot Tracker, Item
    // Customize, Credits) render 30% larger than the main /fm panel so they stay readable — the
    // panel itself is deliberately left at the base size.
    private const val SIBLING_ENLARGE = 1.30f

    /** Combined scale to shrink a screen's drawing by; also divide incoming mouse coordinates by
     *  this before hit-testing against layout computed in the same (virtual) space. */
    fun factor(): Float {
        val guiScale = Minecraft.getInstance().window.guiScale
        val compensation = (REFERENCE_GUI_SCALE / guiScale).coerceAtMost(1.0)
        val base = (compensation * FLAT_SHRINK).toFloat()
        return if (isSiblingScreen()) base * SIBLING_ENLARGE else base
    }

    /** True when the open screen is one of FishModScreen's NanoVG siblings (not the panel itself). */
    private fun isSiblingScreen(): Boolean {
        val s = Minecraft.getInstance().screen ?: return false
        return s is fishmod.features.HasNvgOverlay && s !is fishmod.features.FishModScreen
    }

    /** Converts a real mouse/screen coordinate into the virtual (pre-shrink) space screens lay
     *  themselves out in, so hit-testing lines up with visuals drawn through [factor]. */
    fun vx(real: Number): Int = (real.toDouble() / factor()).toInt()
}

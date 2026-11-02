/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.ProfileViewer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

/** A Font that renders every string with FishMod's Inter font (assets/fishmod/font/inter.json). */
object PvFont {
	private val INTER: FontDescription = FontDescription.Resource(Identifier.fromNamespaceAndPath("fishmod", "inter"))
	private var cached: Font? = null
	private var cachedFor: Font? = null

	/** Inter font for the viewer, or the given vanilla font if it cannot be built. */
	@JvmStatic
	fun get(vanilla: Font): Font {
		cached?.let { if (cachedFor === vanilla) return it }
		try {
			val providerField = Font::class.java.declaredFields
				.firstOrNull { Font.Provider::class.java.isAssignableFrom(it.type) }
				?: return vanilla
			providerField.isAccessible = true
			val base = providerField.get(vanilla) as Font.Provider
			val font = Font(object : Font.Provider {
				override fun glyphs(description: FontDescription): GlyphSource = base.glyphs(INTER)

				override fun effect(): EffectGlyph = base.effect()
			})
			cached = font
			cachedFor = vanilla
			return font
		} catch (exception: ReflectiveOperationException) {
			ProfileViewer.LOGGER.warn("Inter font unavailable, using the default font", exception)
		} catch (exception: RuntimeException) {
			ProfileViewer.LOGGER.warn("Inter font unavailable, using the default font", exception)
		}
		return vanilla
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.BetterPV;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** A Font that renders every string with FishMod's Inter font (assets/fishmod/font/inter.json). */
public final class PvFont {
	private static final FontDescription INTER = new FontDescription.Resource(Identifier.fromNamespaceAndPath("fishmod", "inter"));
	private static Font cached;
	private static Font cachedFor;

	private PvFont() {}

	/** Inter font for the viewer, or the given vanilla font if it cannot be built. */
	public static Font get(Font vanilla) {
		if (cached != null && cachedFor == vanilla) {
			return cached;
		}
		try {
			Field providerField = null;
			for (Field field : Font.class.getDeclaredFields()) {
				if (Font.Provider.class.isAssignableFrom(field.getType())) {
					providerField = field;
					break;
				}
			}
			if (providerField == null) {
				return vanilla;
			}
			providerField.setAccessible(true);
			Font.Provider base = (Font.Provider) providerField.get(vanilla);
			cached = new Font(new Font.Provider() {
				@Override
				public GlyphSource glyphs(FontDescription description) {
					return base.glyphs(INTER);
				}

				@Override
				public EffectGlyph effect() {
					return base.effect();
				}
			});
			cachedFor = vanilla;
			return cached;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			BetterPV.LOGGER.warn("Inter font unavailable, using the default font", exception);
			return vanilla;
		}
	}
}

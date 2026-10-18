/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.page;

import fishmod.pv.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Minimal page contract for Profile Viewer body routing.
 * Pages with extra params (Home open-scale, Museum sort, inventory split) stay special-cased on the screen.
 */
public interface ProfilePage {
	void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	);

	default boolean mouseClicked(double mx, double my, PvSubTab sub) {
		return false;
	}

	default boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		return false;
	}
}

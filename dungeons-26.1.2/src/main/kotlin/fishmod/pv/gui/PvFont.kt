/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import net.minecraft.client.gui.Font

object PvFont {
	// Bundled Inter is gone: the profile viewer uses Minecraft's default font.
	@JvmStatic
	fun get(vanilla: Font): Font = vanilla
}

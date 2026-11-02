/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

object ItemIcons {
	@JvmStatic
	fun drawItemIcon(g: GuiGraphicsExtractor, icon: ItemStack?, x: Int, y: Int, size: Int) {
		if (icon == null || icon.isEmpty) {
			return
		}
		if (size == 16) {
			g.item(icon, x, y)
			return
		}
		val scale = size / 16f
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		g.item(icon, 0, 0)
		g.pose().popMatrix()
	}
}

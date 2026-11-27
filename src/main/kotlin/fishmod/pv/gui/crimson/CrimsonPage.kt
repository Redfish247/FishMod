/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson

import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.gui.crimson.page.AbiphonePage
import fishmod.pv.gui.crimson.page.CrimsonOverviewPage
import fishmod.pv.gui.crimson.page.KuudraPage
import fishmod.pv.gui.nav.PvSubTab
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class CrimsonPage {
	private var snapshot: CrimsonSnapshot = CrimsonSnapshot.empty()
	private var activeSub: PvSubTab = PvSubTab.CRIMSON_OVERVIEW

	private val overview = CrimsonOverviewPage()
	private val kuudra = KuudraPage()
	private val abiphone = AbiphonePage()

	fun apply(snapshot: CrimsonSnapshot?) {
		this.snapshot = snapshot ?: CrimsonSnapshot.empty()
		kuudra.resetScroll()
		abiphone.resetScroll()
	}

	fun snapshot(): CrimsonSnapshot = snapshot

	fun mouseClicked(mx: Double, my: Double): Boolean = when (activeSub) {
		PvSubTab.CRIMSON_OVERVIEW -> overview.mouseClicked(mx, my)
		PvSubTab.CRIMSON_ABIPHONE -> abiphone.mouseClicked(mx, my)
		PvSubTab.CRIMSON_KUUDRA -> kuudra.mouseClicked(mx, my)
		else -> false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, sub: PvSubTab?): Boolean = when (sub) {
		PvSubTab.CRIMSON_ABIPHONE -> abiphone.mouseScrolled(mouseX, mouseY, scrollY)
		PvSubTab.CRIMSON_KUUDRA -> kuudra.mouseScrolled(mouseX, mouseY, scrollY)
		else -> false
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, sub: PvSubTab?,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int,
	) {
		activeSub = sub ?: PvSubTab.CRIMSON_OVERVIEW
		when (activeSub) {
			PvSubTab.CRIMSON_KUUDRA -> kuudra.render(g, font, snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH)
			PvSubTab.CRIMSON_ABIPHONE -> abiphone.render(g, font, snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH)
			else -> overview.render(g, font, snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		when (activeSub) {
			PvSubTab.CRIMSON_KUUDRA -> kuudra.renderTooltip(g, font, mouseX, mouseY, screenW, screenH)
			PvSubTab.CRIMSON_ABIPHONE -> abiphone.renderTooltip(g, font, mouseX, mouseY, screenW, screenH)
			else -> overview.renderTooltip(g, font, mouseX, mouseY, screenW, screenH)
		}
	}
}

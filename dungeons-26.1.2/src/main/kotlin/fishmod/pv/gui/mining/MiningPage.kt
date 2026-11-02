/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining

import fishmod.pv.data.MiningHotmData
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.mining.MiningUi.HoverZone
import fishmod.pv.gui.mining.page.GlacitePage
import fishmod.pv.gui.mining.page.HotmPage
import fishmod.pv.gui.mining.page.MiningOverviewPage
import fishmod.pv.gui.nav.PvSubTab
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Mining: Overview (Garden-style two panels) + HOTM (powders | tree | forge). */
class MiningPage {
	private var snapshot: MiningSnapshot = MiningSnapshot.empty()
	private var lastSub: PvSubTab? = null
	private val zones = ArrayList<HoverZone>()
	private val overview = MiningOverviewPage()
	private val hotm = HotmPage()
	private val glacite = GlacitePage()
	private var playerStats: PlayerStatsSnapshot = PlayerStatsSnapshot.empty()

	fun apply(snapshot: MiningSnapshot?) {
		this.snapshot = snapshot ?: MiningSnapshot.empty()
		zones.clear()
		overview.resetScroll()
		hotm.reset()
		glacite.reset()
		MiningHotmData.ensureLoaded()
	}

	fun setPlayerStats(stats: PlayerStatsSnapshot?) {
		playerStats = stats ?: PlayerStatsSnapshot.empty()
	}

	fun snapshot(): MiningSnapshot = snapshot

	fun mouseClicked(mx: Double, my: Double): Boolean = when (lastSub) {
		PvSubTab.MINING_HOTM -> hotm.mouseClicked(mx, my)
		PvSubTab.MINING_GLACITE -> glacite.mouseClicked(mx, my)
		else -> overview.mouseClicked(mx, my)
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, sub: PvSubTab?,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int, @Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		zones.clear()
		val mode = sub ?: PvSubTab.MINING_OVERVIEW
		if (mode != lastSub) {
			lastSub = mode
			when (mode) {
				PvSubTab.MINING_HOTM -> hotm.onEnter()
				PvSubTab.MINING_GLACITE -> glacite.onEnter()
				else -> overview.onEnter()
			}
			if (mode != PvSubTab.MINING_OVERVIEW) {
				overview.leaveOverview()
			}
		}

		when (mode) {
			PvSubTab.MINING_HOTM -> hotm.render(g, font, snapshot, zones, x, y, w, h, mouseX, mouseY)
			PvSubTab.MINING_GLACITE -> glacite.render(g, font, snapshot, zones, x, y, w, h, mouseX, mouseY)
			else -> overview.render(g, font, snapshot, playerStats, zones, x, y, w, h, mouseX, mouseY)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		MiningUi.drawHover(g, font, zones, snapshot, mouseX, mouseY, screenW, screenH)
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, sub: PvSubTab?): Boolean = when (sub ?: PvSubTab.MINING_OVERVIEW) {
		PvSubTab.MINING_HOTM -> hotm.mouseScrolled(mouseX, mouseY, scrollY)
		PvSubTab.MINING_GLACITE -> glacite.mouseScrolled(mouseX, mouseY, scrollY)
		else -> overview.mouseScrolled(mouseX, mouseY, scrollY)
	}
}

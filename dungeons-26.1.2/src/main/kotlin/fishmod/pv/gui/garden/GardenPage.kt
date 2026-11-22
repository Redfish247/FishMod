/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden

import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.garden.page.CropsPage
import fishmod.pv.gui.garden.page.FarmingFortunePage
import fishmod.pv.gui.garden.page.GardenOverviewPage
import fishmod.pv.gui.garden.page.GreenhousePage
import fishmod.pv.gui.garden.page.JacobPage
import fishmod.pv.gui.garden.page.VisitorsPage
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.PvSubTab
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class GardenPage {
	private var snapshot: GardenSnapshot = GardenSnapshot.empty()
	private var lastSub: PvSubTab? = null
	private val ui = GardenUi()
	private val overview = GardenOverviewPage()
	private val fortune = FarmingFortunePage()
	private val visitors = VisitorsPage()
	private val crops = CropsPage()
	private val greenhouse = GreenhousePage()
	private val jacob = JacobPage()
	private var playerStats: PlayerStatsSnapshot = PlayerStatsSnapshot.empty()

	fun apply(snapshot: GardenSnapshot?) {
		this.snapshot = snapshot ?: GardenSnapshot.empty()
		overview.resetScroll()
		visitors.resetScroll()
		crops.resetScroll()
		greenhouse.resetScroll()
		jacob.resetScroll()
		jacob.resetFlip()
		fortune.close()
		ui.clearZones()
		prefetch()
	}

	fun setPlayerStats(stats: PlayerStatsSnapshot?) {
		playerStats = stats ?: PlayerStatsSnapshot.empty()
	}

	fun patch(snapshot: GardenSnapshot?) {
		this.snapshot = snapshot ?: GardenSnapshot.empty()
		prefetch()
	}

	fun snapshot(): GardenSnapshot = snapshot

	fun render(
		g: GuiGraphicsExtractor, font: Font, sub: PvSubTab?,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int
	) {
		ui.clearZones()
		val mode = sub ?: PvSubTab.GARDEN_OVERVIEW
		if (mode != lastSub) {
			lastSub = mode
			fortune.close()
			resetActiveScroll(mode)
		}
		if (mode == PvSubTab.GARDEN_OVERVIEW && fortune.isOpen()) {
			fortune.render(snapshot, playerStats, ui, g, font, x, y, w, h, mouseX, mouseY)
			return
		}

		val islandReady = snapshot.islandLoaded
		val islandBusy = snapshot.islandLoading || (!islandReady && snapshot.islandError.isBlank())
		val needsIsland = mode != PvSubTab.GARDEN_JACOB && mode != PvSubTab.GARDEN_GREENHOUSE

		if (!islandReady && needsIsland) {
			if (mode == PvSubTab.GARDEN_OVERVIEW) {
				overview.render(snapshot, playerStats, ui, g, font, x, y, w, h, mouseX, mouseY, true)
			} else {
				clearActiveScrollExtents(mode)
				PvDraw.innerPanel(g, x, y, w, h)
				val msg = when {
					islandBusy -> "Loading garden..."
					snapshot.islandError.isBlank() -> "Garden unavailable"
					else -> snapshot.islandError
				}
				PvDraw.textCentered(g, font, msg, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			}
			return
		}

		when (mode) {
			PvSubTab.GARDEN_VISITORS -> visitors.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
			PvSubTab.GARDEN_CROPS, PvSubTab.GARDEN_COMPOSTER -> crops.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
			PvSubTab.GARDEN_GREENHOUSE -> greenhouse.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
			PvSubTab.GARDEN_JACOB -> jacob.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
			else -> overview.render(snapshot, playerStats, ui, g, font, x, y, w, h, mouseX, mouseY, false)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		ui.drawHover(g, font, mouseX, mouseY, screenW, screenH)
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, sub: PvSubTab?): Boolean {
		val mode = sub ?: lastSub ?: PvSubTab.GARDEN_OVERVIEW
		if (mode == PvSubTab.GARDEN_OVERVIEW && fortune.isOpen()) {
			return fortune.mouseScrolled(mouseY, scrollY)
		}
		return when (mode) {
			PvSubTab.GARDEN_VISITORS -> visitors.mouseScrolled(mouseX, mouseY, scrollY)
			PvSubTab.GARDEN_CROPS, PvSubTab.GARDEN_COMPOSTER -> crops.mouseScrolled(mouseX, mouseY, scrollY)
			PvSubTab.GARDEN_GREENHOUSE -> greenhouse.mouseScrolled(mouseX, mouseY, scrollY)
			PvSubTab.GARDEN_JACOB -> jacob.mouseScrolled(mouseX, mouseY, scrollY)
			else -> overview.mouseScrolled(mouseX, mouseY, scrollY)
		}
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (lastSub == null || lastSub == PvSubTab.GARDEN_OVERVIEW) {
			if (fortune.isOpen()) {
				return fortune.mouseClicked(mx, my)
			}
			if (overview.fortuneRowHit(mx, my)) {
				fortune.open()
				return true
			}
			return false
		}
		if (lastSub != PvSubTab.GARDEN_JACOB) {
			return false
		}
		return jacob.mouseClicked(mx, my)
	}

	private fun resetActiveScroll(mode: PvSubTab) {
		when (mode) {
			PvSubTab.GARDEN_VISITORS -> visitors.resetScroll()
			PvSubTab.GARDEN_CROPS, PvSubTab.GARDEN_COMPOSTER -> crops.resetScroll()
			PvSubTab.GARDEN_GREENHOUSE -> greenhouse.resetScroll()
			PvSubTab.GARDEN_JACOB -> jacob.resetScroll()
			else -> overview.resetScroll()
		}
	}

	private fun clearActiveScrollExtents(mode: PvSubTab) {
		when (mode) {
			PvSubTab.GARDEN_VISITORS -> visitors.clearScrollExtents()
			PvSubTab.GARDEN_CROPS, PvSubTab.GARDEN_COMPOSTER -> crops.clearScrollExtents()
			PvSubTab.GARDEN_GREENHOUSE -> greenhouse.clearScrollExtents()
			PvSubTab.GARDEN_JACOB -> jacob.clearScrollExtents()
			else -> overview.clearScrollExtents()
		}
	}

	private fun prefetch() {
		val ids = ArrayList<String>()
		for (v in snapshot.visitors) {
			if (v.npcItemId.isNotBlank()) {
				ids.add(v.npcItemId)
			}
		}
		for (c in snapshot.crops) {
			ids.add(c.iconId)
		}
		for (chip in snapshot.gardenChips) {
			if (chip.iconId.isNotBlank()) {
				ids.add(chip.iconId)
			}
		}
		for (gh in snapshot.greenhouse) {
			if (gh.iconId.isNotBlank()) {
				ids.add(gh.iconId)
			}
			val pack = GardenData.greenhousePackModel(gh.id)
			if (pack.isNotBlank()) {
				SkyBlockItemFactory.customIconModel(gh.iconId, pack)
			}
		}
		for (c in snapshot.contests) {
			if (c.iconId.isNotBlank()) {
				ids.add(c.iconId)
			}
		}
		for (medal in snapshot.cropMedals()) {
			if (medal.iconId.isNotBlank()) {
				ids.add(medal.iconId)
			}
		}
		for (cropId in snapshot.uniqueGoldCrops()) {
			ids.add(GardenData.cropIconId(cropId))
		}
		for (pb in snapshot.personalBests) {
			ids.add(GardenData.cropIconId(pb.id))
		}
		if (ids.isNotEmpty()) {
			SkyBlockItemFactory.prefetchIds(ids)
		}
	}
}

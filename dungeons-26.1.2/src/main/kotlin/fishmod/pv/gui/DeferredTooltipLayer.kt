/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.gui.bestiary.BestiaryPage
import fishmod.pv.gui.collections.CollectionsPage
import fishmod.pv.gui.crimson.CrimsonPage
import fishmod.pv.gui.dungeons.DungeonChestsPage
import fishmod.pv.gui.dungeons.DungeonPage
import fishmod.pv.gui.events.EventsPage
import fishmod.pv.gui.fishing.FishingPage
import fishmod.pv.gui.fishing.FishingTrophyTrialPage
import fishmod.pv.gui.foraging.ForagingPage
import fishmod.pv.gui.garden.GardenPage
import fishmod.pv.gui.home.HomePage
import fishmod.pv.gui.home.page.EssencePage
import fishmod.pv.gui.home.page.MiscStatsPage
import fishmod.pv.gui.home.page.RecordsPage
import fishmod.pv.gui.inventories.InventoryPage
import fishmod.pv.gui.mining.MiningPage
import fishmod.pv.gui.museum.MuseumPage
import fishmod.pv.gui.nav.IconButtonBar
import fishmod.pv.gui.nav.PvSubTab
import fishmod.pv.gui.nav.PvTab
import fishmod.pv.gui.pets.PetsPage
import fishmod.pv.gui.rift.RiftPage
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Collects and draws deferred page / selector tooltips above search chrome. */
object DeferredTooltipLayer {
	private const val PAD = 8

	@JvmStatic
	@Suppress("UNUSED_PARAMETER")
	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		width: Int,
		height: Int,
		panelX: Int,
		panelY: Int,
		panelW: Int,
		panelH: Int,
		mouseX: Int,
		mouseY: Int,
		tab: PvTab,
		homeSub: PvSubTab?,
		selector: ProfileSelectorController,
		inventoryLayout: InventorySplitLayout?,
		bestiaryLayout: BestiarySplitLayout,
		inventoryBar: IconButtonBar,
		homePage: HomePage,
		homeMiscPage: MiscStatsPage,
		homeRecordsPage: RecordsPage,
		homeEssencePage: EssencePage,
		dungeonPage: DungeonPage,
		dungeonChestsPage: DungeonChestsPage,
		petsPage: PetsPage,
		collectionsPage: CollectionsPage,
		gardenPage: GardenPage,
		miningPage: MiningPage,
		foragingPage: ForagingPage,
		fishingPage: FishingPage,
		fishingTrophyTrialPage: FishingTrophyTrialPage,
		crimsonPage: CrimsonPage,
		riftPage: RiftPage,
		museumPage: MuseumPage,
		eventsPage: EventsPage,
		inventoryPage: InventoryPage,
		bestiaryPage: BestiaryPage
	) {
		val footerHover = selector.footerHover(mouseX, mouseY)
		val profileTip = selector.tooltip(mouseX, mouseY, footerHover)
		if (profileTip != null && !selector.menuOpen()) {
			PvTooltip.drawStyled(g, font, profileTip, mouseX, mouseY, width, height)
		}
		if (selector.menuOpen() && selector.combinedHover(mouseX, mouseY)) {
			return
		}

		val bodyX = panelX + PAD
		val bodyY = panelY + PAD
		val bodyW = panelW - PAD * 2
		val bodyH = panelH - PAD * 2

		when (tab) {
			PvTab.HOME -> when (homeSub) {
				PvSubTab.HOME_MISC -> homeMiscPage.renderTooltip(g, font, mouseX, mouseY, width, height)
				PvSubTab.HOME_RECORDS -> homeRecordsPage.renderTooltip(g, font, mouseX, mouseY, width, height)
				PvSubTab.HOME_ESSENCE -> homeEssencePage.renderTooltip(g, font, mouseX, mouseY, width, height)
				else -> homePage.renderTooltip(g, font, mouseX, mouseY, width, height)
			}
			PvTab.DUNGEONS -> if (homeSub == PvSubTab.DUNGEON_CHESTS) {
				dungeonChestsPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			} else {
				dungeonPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			}
			PvTab.PETS -> petsPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.COLLECTIONS -> collectionsPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.GARDEN -> gardenPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.MINING -> miningPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.FORAGING -> foragingPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.FISHING -> if (homeSub == PvSubTab.FISHING_TROPHIES) {
				fishingTrophyTrialPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			} else {
				fishingPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			}
			PvTab.CRIMSON -> crimsonPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.RIFT -> riftPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.MUSEUM -> museumPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			PvTab.EVENTS -> eventsPage.renderTooltip(
				g, font, mouseX, mouseY, width, height, bodyX, bodyY, bodyW, bodyH
			)
			else -> {}
		}

		if (tab.isInventorySplit()) {
			inventoryPage.renderTooltip(g, font, mouseX, mouseY, width, height)
		}
		if (tab.isBestiarySplit()) {
			bestiaryPage.renderTooltip(g, font, mouseX, mouseY, width, height)
			bestiaryLayout.categoryTip()?.let {
				inventoryBar.maybeTooltip(
					g,
					font,
					it,
					true,
					bestiaryLayout.categoryTipX(),
					bestiaryLayout.categoryTipY()
				)
			}
		}
	}
}

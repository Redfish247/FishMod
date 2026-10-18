/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.gui.bestiary.BestiaryPage;
import fishmod.pv.gui.collections.CollectionsPage;
import fishmod.pv.gui.crimson.CrimsonPage;
import fishmod.pv.gui.dungeons.DungeonChestsPage;
import fishmod.pv.gui.dungeons.DungeonPage;
import fishmod.pv.gui.events.EventsPage;
import fishmod.pv.gui.fishing.FishingPage;
import fishmod.pv.gui.fishing.FishingTrophyTrialPage;
import fishmod.pv.gui.foraging.ForagingPage;
import fishmod.pv.gui.garden.GardenPage;
import fishmod.pv.gui.home.HomePage;
import fishmod.pv.gui.home.page.EssencePage;
import fishmod.pv.gui.home.page.MiscStatsPage;
import fishmod.pv.gui.home.page.RecordsPage;
import fishmod.pv.gui.inventories.InventoryPage;
import fishmod.pv.gui.mining.MiningPage;
import fishmod.pv.gui.museum.MuseumPage;
import fishmod.pv.gui.nav.IconButtonBar;
import fishmod.pv.gui.nav.PvSubTab;
import fishmod.pv.gui.nav.PvTab;
import fishmod.pv.gui.pets.PetsPage;
import fishmod.pv.gui.rift.RiftPage;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Collects and draws deferred page / selector tooltips above search chrome. */
final class DeferredTooltipLayer {
	private static final int PAD = 8;

	private DeferredTooltipLayer() {
	}

	static void render(
		GuiGraphicsExtractor g,
		Font font,
		int width,
		int height,
		int panelX,
		int panelY,
		int panelW,
		int panelH,
		int mouseX,
		int mouseY,
		PvTab tab,
		PvSubTab homeSub,
		ProfileSelectorController selector,
		InventorySplitLayout inventoryLayout,
		BestiarySplitLayout bestiaryLayout,
		IconButtonBar inventoryBar,
		HomePage homePage,
		MiscStatsPage homeMiscPage,
		RecordsPage homeRecordsPage,
		EssencePage homeEssencePage,
		DungeonPage dungeonPage,
		DungeonChestsPage dungeonChestsPage,
		PetsPage petsPage,
		CollectionsPage collectionsPage,
		GardenPage gardenPage,
		MiningPage miningPage,
		ForagingPage foragingPage,
		FishingPage fishingPage,
		FishingTrophyTrialPage fishingTrophyTrialPage,
		CrimsonPage crimsonPage,
		RiftPage riftPage,
		MuseumPage museumPage,
		EventsPage eventsPage,
		InventoryPage inventoryPage,
		BestiaryPage bestiaryPage
	) {
		boolean footerHover = selector.footerHover(mouseX, mouseY);
		List<PvTooltip.Line> profileTip = selector.tooltip(mouseX, mouseY, footerHover);
		if (profileTip != null && !selector.menuOpen()) {
			PvTooltip.drawStyled(g, font, profileTip, mouseX, mouseY, width, height);
		}
		if (selector.menuOpen() && selector.combinedHover(mouseX, mouseY)) {
			return;
		}

		int bodyX = panelX + PAD;
		int bodyY = panelY + PAD;
		int bodyW = panelW - PAD * 2;
		int bodyH = panelH - PAD * 2;

		switch (tab) {
			case HOME -> {
				if (homeSub == PvSubTab.HOME_MISC) {
					homeMiscPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else if (homeSub == PvSubTab.HOME_RECORDS) {
					homeRecordsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else if (homeSub == PvSubTab.HOME_ESSENCE) {
					homeEssencePage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else {
					homePage.renderTooltip(g, font, mouseX, mouseY, width, height);
				}
			}
			case DUNGEONS -> {
				if (homeSub == PvSubTab.DUNGEON_CHESTS) {
					dungeonChestsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else {
					dungeonPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				}
			}
			case PETS -> petsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case COLLECTIONS -> collectionsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case GARDEN -> gardenPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case MINING -> miningPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case FORAGING -> foragingPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case FISHING -> {
				if (homeSub == PvSubTab.FISHING_TROPHIES) {
					fishingTrophyTrialPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else {
					fishingPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				}
			}
			case CRIMSON -> crimsonPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case RIFT -> riftPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case MUSEUM -> museumPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case EVENTS -> eventsPage.renderTooltip(
				g, font, mouseX, mouseY, width, height, bodyX, bodyY, bodyW, bodyH
			);
			default -> { }
		}

		if (tab.isInventorySplit()) {
			inventoryPage.renderTooltip(g, font, mouseX, mouseY, width, height);
		}
		if (tab.isBestiarySplit()) {
			bestiaryPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			if (bestiaryLayout.categoryTip() != null) {
				inventoryBar.maybeTooltip(
					g,
					font,
					bestiaryLayout.categoryTip(),
					true,
					bestiaryLayout.categoryTipX(),
					bestiaryLayout.categoryTipY()
				);
			}
		}
	}
}

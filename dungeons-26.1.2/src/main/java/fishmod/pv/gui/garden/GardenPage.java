/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden;

import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.garden.page.CropsPage;
import fishmod.pv.gui.garden.page.FarmingFortunePage;
import fishmod.pv.gui.garden.page.GardenOverviewPage;
import fishmod.pv.gui.garden.page.GreenhousePage;
import fishmod.pv.gui.garden.page.JacobPage;
import fishmod.pv.gui.garden.page.VisitorsPage;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Garden page: Overview / Visitors / Crops / Composter / Greenhouse / Jacob. */
public final class GardenPage {
	private GardenSnapshot snapshot = GardenSnapshot.empty();
	private PvSubTab lastSub;
	private final GardenUi ui = new GardenUi();
	private final GardenOverviewPage overview = new GardenOverviewPage();
	private final FarmingFortunePage fortune = new FarmingFortunePage();
	private final VisitorsPage visitors = new VisitorsPage();
	private final CropsPage crops = new CropsPage();
	private final GreenhousePage greenhouse = new GreenhousePage();
	private final JacobPage jacob = new JacobPage();
	private PlayerStatsSnapshot playerStats = PlayerStatsSnapshot.empty();

	public void apply(GardenSnapshot snapshot) {
		this.snapshot = snapshot == null ? GardenSnapshot.empty() : snapshot;
		this.overview.resetScroll();
		this.visitors.resetScroll();
		this.crops.resetScroll();
		this.greenhouse.resetScroll();
		this.jacob.resetScroll();
		this.jacob.resetFlip();
		this.fortune.close();
		this.ui.clearZones();
		prefetch();
	}

	public void setPlayerStats(PlayerStatsSnapshot stats) {
		this.playerStats = stats == null ? PlayerStatsSnapshot.empty() : stats;
	}

	/** Update data without resetting scroll (async weight / contests). */
	public void patch(GardenSnapshot snapshot) {
		this.snapshot = snapshot == null ? GardenSnapshot.empty() : snapshot;
		prefetch();
	}

	public GardenSnapshot snapshot() {
		return this.snapshot;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.ui.clearZones();
		PvSubTab mode = sub == null ? PvSubTab.GARDEN_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.fortune.close();
			resetActiveScroll(mode);
		}
		if (mode == PvSubTab.GARDEN_OVERVIEW && this.fortune.isOpen()) {
			this.fortune.render(this.snapshot, this.playerStats, this.ui, g, font, x, y, w, h, mouseX, mouseY);
			return;
		}

		boolean islandReady = this.snapshot.islandLoaded();
		boolean islandBusy = this.snapshot.islandLoading() || (!islandReady && this.snapshot.islandError().isBlank());
		boolean needsIsland = mode != PvSubTab.GARDEN_JACOB && mode != PvSubTab.GARDEN_GREENHOUSE;

		if (!islandReady && needsIsland) {
			if (mode == PvSubTab.GARDEN_OVERVIEW) {
				this.overview.render(this.snapshot, this.playerStats, this.ui, g, font, x, y, w, h, mouseX, mouseY, true);
			} else {
				clearActiveScrollExtents(mode);
				PvDraw.innerPanel(g, x, y, w, h);
				String msg = islandBusy
					? "Loading garden..."
					: (this.snapshot.islandError().isBlank() ? "Garden unavailable" : this.snapshot.islandError());
				PvDraw.textCentered(g, font, msg, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			}
			return;
		}

		switch (mode) {
			case GARDEN_VISITORS -> this.visitors.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_CROPS, GARDEN_COMPOSTER -> this.crops.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_GREENHOUSE -> this.greenhouse.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_JACOB -> this.jacob.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
			default -> this.overview.render(this.snapshot, this.playerStats, this.ui, g, font, x, y, w, h, mouseX, mouseY, false);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		this.ui.drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		PvSubTab mode = sub == null ? this.lastSub : sub;
		if (mode == null) {
			mode = PvSubTab.GARDEN_OVERVIEW;
		}
		if (mode == PvSubTab.GARDEN_OVERVIEW && this.fortune.isOpen()) {
			return this.fortune.mouseScrolled(mouseY, scrollY);
		}
		return switch (mode) {
			case GARDEN_VISITORS -> this.visitors.mouseScrolled(mouseX, mouseY, scrollY);
			case GARDEN_CROPS, GARDEN_COMPOSTER -> this.crops.mouseScrolled(mouseX, mouseY, scrollY);
			case GARDEN_GREENHOUSE -> this.greenhouse.mouseScrolled(mouseX, mouseY, scrollY);
			case GARDEN_JACOB -> this.jacob.mouseScrolled(mouseX, mouseY, scrollY);
			default -> this.overview.mouseScrolled(mouseX, mouseY, scrollY);
		};
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub == null || this.lastSub == PvSubTab.GARDEN_OVERVIEW) {
			if (this.fortune.isOpen()) {
				return this.fortune.mouseClicked(mx, my);
			}
			if (this.overview.fortuneRowHit(mx, my)) {
				this.fortune.open();
				return true;
			}
			return false;
		}
		if (this.lastSub != PvSubTab.GARDEN_JACOB) {
			return false;
		}
		return this.jacob.mouseClicked(mx, my);
	}

	private void resetActiveScroll(PvSubTab mode) {
		switch (mode) {
			case GARDEN_VISITORS -> this.visitors.resetScroll();
			case GARDEN_CROPS, GARDEN_COMPOSTER -> this.crops.resetScroll();
			case GARDEN_GREENHOUSE -> this.greenhouse.resetScroll();
			case GARDEN_JACOB -> this.jacob.resetScroll();
			default -> this.overview.resetScroll();
		}
	}

	private void clearActiveScrollExtents(PvSubTab mode) {
		switch (mode) {
			case GARDEN_VISITORS -> this.visitors.clearScrollExtents();
			case GARDEN_CROPS, GARDEN_COMPOSTER -> this.crops.clearScrollExtents();
			case GARDEN_GREENHOUSE -> this.greenhouse.clearScrollExtents();
			case GARDEN_JACOB -> this.jacob.clearScrollExtents();
			default -> this.overview.clearScrollExtents();
		}
	}

	private void prefetch() {
		List<String> ids = new ArrayList<>();
		for (GardenSnapshot.VisitorRow v : this.snapshot.visitors()) {
			if (v.npcItemId() != null && !v.npcItemId().isBlank()) {
				ids.add(v.npcItemId());
			}
		}
		for (GardenSnapshot.CropRow c : this.snapshot.crops()) {
			ids.add(c.iconId());
		}
		for (GardenSnapshot.ChipEntry chip : this.snapshot.gardenChips()) {
			if (chip.iconId() != null && !chip.iconId().isBlank()) {
				ids.add(chip.iconId());
			}
		}
		for (GardenSnapshot.GreenhouseRow g : this.snapshot.greenhouse()) {
			if (g.iconId() != null && !g.iconId().isBlank()) {
				ids.add(g.iconId());
			}
			String pack = GardenData.greenhousePackModel(g.id());
			if (!pack.isBlank()) {
				SkyBlockItemFactory.customIconModel(g.iconId(), pack);
			}
		}
		for (GardenSnapshot.ContestEntry c : this.snapshot.contests()) {
			if (c.iconId() != null && !c.iconId().isBlank()) {
				ids.add(c.iconId());
			}
		}
		for (GardenSnapshot.CropMedal medal : this.snapshot.cropMedals()) {
			if (medal.iconId() != null && !medal.iconId().isBlank()) {
				ids.add(medal.iconId());
			}
		}
		for (String cropId : this.snapshot.uniqueGoldCrops()) {
			ids.add(GardenData.cropIconId(cropId));
		}
		for (GardenSnapshot.PersonalBest pb : this.snapshot.personalBests()) {
			ids.add(GardenData.cropIconId(pb.id()));
		}
		if (!ids.isEmpty()) {
			SkyBlockItemFactory.prefetchIds(ids);
		}
	}
}

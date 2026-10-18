/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining;

import fishmod.pv.data.MiningHotmData;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.gui.mining.MiningUi.HoverZone;
import fishmod.pv.gui.mining.page.GlacitePage;
import fishmod.pv.gui.mining.page.HotmPage;
import fishmod.pv.gui.mining.page.MiningOverviewPage;
import fishmod.pv.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Mining: Overview (Garden-style two panels) + HOTM (powders | tree | forge). */
public final class MiningPage {
	private MiningSnapshot snapshot = MiningSnapshot.empty();
	private PvSubTab lastSub;
	private final List<HoverZone> zones = new ArrayList<>();
	private final MiningOverviewPage overview = new MiningOverviewPage();
	private final HotmPage hotm = new HotmPage();
	private final GlacitePage glacite = new GlacitePage();
	private PlayerStatsSnapshot playerStats = PlayerStatsSnapshot.empty();

	public void apply(MiningSnapshot snapshot) {
		this.snapshot = snapshot == null ? MiningSnapshot.empty() : snapshot;
		this.zones.clear();
		this.overview.resetScroll();
		this.hotm.reset();
		this.glacite.reset();
		MiningHotmData.ensureLoaded();
	}

	public void setPlayerStats(PlayerStatsSnapshot stats) {
		this.playerStats = stats == null ? PlayerStatsSnapshot.empty() : stats;
	}

	public MiningSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub == PvSubTab.MINING_HOTM) {
			return this.hotm.mouseClicked(mx, my);
		}
		if (this.lastSub == PvSubTab.MINING_GLACITE) {
			return this.glacite.mouseClicked(mx, my);
		}
		return this.overview.mouseClicked(mx, my);
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		PvSubTab mode = sub == null ? PvSubTab.MINING_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			switch (mode) {
				case MINING_HOTM -> this.hotm.onEnter();
				case MINING_GLACITE -> this.glacite.onEnter();
				default -> this.overview.onEnter();
			}
			if (mode != PvSubTab.MINING_OVERVIEW) {
				this.overview.leaveOverview();
			}
		}

		switch (mode) {
			case MINING_HOTM -> this.hotm.render(g, font, this.snapshot, this.zones, x, y, w, h, mouseX, mouseY);
			case MINING_GLACITE -> this.glacite.render(g, font, this.snapshot, this.zones, x, y, w, h, mouseX, mouseY);
			default -> this.overview.render(g, font, this.snapshot, this.playerStats, this.zones, x, y, w, h, mouseX, mouseY);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		MiningUi.drawHover(g, font, this.zones, this.snapshot, mouseX, mouseY, screenW, screenH);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		PvSubTab mode = sub == null ? PvSubTab.MINING_OVERVIEW : sub;
		return switch (mode) {
			case MINING_HOTM -> this.hotm.mouseScrolled(mouseX, mouseY, scrollY);
			case MINING_GLACITE -> this.glacite.mouseScrolled(mouseX, mouseY, scrollY);
			default -> this.overview.mouseScrolled(mouseX, mouseY, scrollY);
		};
	}
}

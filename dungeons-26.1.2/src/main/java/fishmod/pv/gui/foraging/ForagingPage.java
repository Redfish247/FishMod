/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging;

import fishmod.pv.data.ForagingHotfData;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.gui.foraging.page.AttributeShardsPage;
import fishmod.pv.gui.foraging.page.ForagingOverviewPage;
import fishmod.pv.gui.foraging.page.HotfPage;
import fishmod.pv.gui.foraging.page.HuntingPage;
import fishmod.pv.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Foraging: Overview / HOTF / Hunting / Attribute Shards. */
public final class ForagingPage {
	private ForagingSnapshot snapshot = ForagingSnapshot.empty();
	private PvSubTab lastSub;

	private final ForagingOverviewPage overview = new ForagingOverviewPage();
	private final HotfPage hotf = new HotfPage();
	private final HuntingPage hunting = new HuntingPage();
	private final AttributeShardsPage attributeShards = new AttributeShardsPage();

	public void apply(ForagingSnapshot snapshot) {
		this.snapshot = snapshot == null ? ForagingSnapshot.empty() : snapshot;
		this.overview.reset();
		this.hotf.reset();
		this.hunting.reset();
		this.attributeShards.reset();
		ForagingHotfData.ensureLoaded();
	}

	public ForagingSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub == PvSubTab.FORAGING_ATTRIBUTE_SHARDS) {
			return this.attributeShards.mouseClicked(mx, my);
		}
		if (this.lastSub != PvSubTab.FORAGING_OVERVIEW) {
			return false;
		}
		return this.overview.mouseClicked(mx, my);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		PvSubTab mode = sub == null ? PvSubTab.FORAGING_OVERVIEW : sub;
		return switch (mode) {
			case FORAGING_ATTRIBUTE_SHARDS -> this.attributeShards.mouseScrolled(mouseX, mouseY, scrollY);
			case FORAGING_HUNTING -> this.hunting.mouseScrolled(mouseX, mouseY, scrollY);
			default -> false;
		};
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		PvSubTab mode = sub == null ? PvSubTab.FORAGING_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.attributeShards.resetScroll();
			this.hunting.resetScroll();
			if (mode != PvSubTab.FORAGING_OVERVIEW) {
				this.overview.onLeave();
			}
		}

		switch (mode) {
			case FORAGING_HOTF -> this.hotf.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			case FORAGING_HUNTING -> this.hunting.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			case FORAGING_ATTRIBUTE_SHARDS ->
				this.attributeShards.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			default -> this.overview.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		PvSubTab mode = this.lastSub == null ? PvSubTab.FORAGING_OVERVIEW : this.lastSub;
		switch (mode) {
			case FORAGING_HOTF -> this.hotf.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			case FORAGING_HUNTING -> this.hunting.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			case FORAGING_ATTRIBUTE_SHARDS ->
				this.attributeShards.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			default -> this.overview.drawHover(g, font, mouseX, mouseY, screenW, screenH);
		}
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.garden.GardenUi;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.garden.GardenUi.*;

/** Garden greenhouse subpage. */
public final class GreenhousePage {
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void render(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int leftW = Math.max(150, w * 32 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		GardenSnapshot.GreenhouseMeta meta = snap.greenhouseMeta();
		List<GardenSnapshot.GreenhouseRow> rows = snap.greenhouse();
		int analyzed = 0;
		for (GardenSnapshot.GreenhouseRow row : rows) {
			if (row.analyzed()) {
				analyzed++;
			}
		}

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;
		PvDraw.text(g, font, "Greenhouse", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = GardenUi.statLine(g, font, "Analyzed", String.valueOf(analyzed), lx, ly, lw, COMPLETED_C) + 1;
		ly = GardenUi.statLine(g, font, "Discovered", String.valueOf(Math.max(0, rows.size() - analyzed)), lx, ly, lw, GOLD) + 1;
		ly = GardenUi.statLine(g, font, "Tracked", String.valueOf(rows.size()), lx, ly, lw, PvDraw.COLOR_TEXT) + 4;

		boolean island = snap.islandLoaded();
		PvDraw.text(g, font, "Desk upgrades", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 2;
		ly = GardenUi.statLine(g, font, "Slots unlocked",
			island ? String.valueOf(meta.slotsUnlocked()) : "...", lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = GardenUi.statLine(g, font, "Yield",
			island ? String.valueOf(meta.yieldLevel()) : "...", lx, ly, lw, BAR_COMPOST) + 1;
		ly = GardenUi.statLine(g, font, "Plot limit",
			island ? String.valueOf(meta.plotLimitLevel()) : "...", lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = GardenUi.statLine(g, font, "Growth speed",
			island ? String.valueOf(meta.growthSpeedLevel()) : "...", lx, ly, lw, BAR_FARM) + 3;
		if (island && meta.lastGrowthStageMs() > 0L) {
			long raw = meta.lastGrowthStageMs();
			long ms = raw > 1_000_000_000_000L ? raw : raw * 1000L;
			long agoSec = Math.max(0L, (System.currentTimeMillis() - ms) / 1000L);
			GardenUi.statLine(g, font, "Last growth", agoSec < 60 ? agoSec + "s ago"
				: agoSec < 3600 ? (agoSec / 60) + "m ago"
				: (agoSec / 3600) + "h ago", lx, ly, lw, PvDraw.COLOR_MUTED);
		}

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Mutations", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 8;

		int cols = Math.max(8, Math.min(13, (rw + SLOT_GAP) / (GH_CELL_W + SLOT_GAP)));
		int cellW = Math.max(GH_CELL_W, (rw - (cols - 1) * SLOT_GAP) / cols);
		int cellH = cellW;
		int gridW = cols * cellW + (cols - 1) * SLOT_GAP;
		int gridX = rx + Math.max(0, (rw - gridW) / 2);
		int gridRows = rows.isEmpty() ? 0 : (rows.size() + cols - 1) / cols;
		int gridTop = ry;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, gridRows * (cellH + SLOT_GAP) - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No greenhouse crops", x + leftW + GAP + rightW / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}

		g.enableScissor(rx, gridTop, rx + rw + 3, gridTop + gridH);
		int baseY = gridTop - this.scroll;
		for (int i = 0; i < rows.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = gridX + col * (cellW + SLOT_GAP);
			int by = baseY + row * (cellH + SLOT_GAP);
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.GreenhouseRow gh = rows.get(i);
			PvDraw.fill(g, bx, by, cellW, cellH, gh.analyzed() ? 0xFF1E5A2A : 0xFF2A2A32);
			GardenUi.drawCellBorder(g, bx, by, cellW, cellH);
			int iconX = bx + (cellW - ICON) / 2;
			GardenUi.drawIcon(g, gh.iconId(), iconX, by + (cellH - ICON) / 2, ICON, GardenData.greenhousePackModel(gh.id()));
			String status = gh.analyzed() ? "Analyzed" : "Discovered";
			int statusColor = gh.analyzed() ? COMPLETED_C : GOLD;
			ui.zones.add(new GardenUi.HoverZone(bx, by, cellW, cellH, List.of(
				PvTooltip.Line.of(gh.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of(status, statusColor)
			)));
		}
		g.disableScissor();
	}


	public void resetScroll() {
		this.scroll = 0;
	}

	public void clearScrollExtents() {
		this.maxScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.maxScroll <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

}

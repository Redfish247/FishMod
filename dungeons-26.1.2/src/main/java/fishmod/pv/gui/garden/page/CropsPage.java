/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenCosts;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.garden.GardenUi;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static fishmod.pv.gui.garden.GardenUi.*;

/** Garden crops + composter subpage. */
public final class CropsPage {
	private static final int PLOT_GAP = 2;
	private static final int PLOT_UNLOCKED = 0xFF1F3A1F;
	private static final int PLOT_LOCKED = 0x1CFFFFFF;
	private static final int PLOT_BARN = 0xFF3A2C1E;

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void render(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		List<GardenSnapshot.CropRow> crops = snap.crops();
		PvDraw.text(g, font, "Crop milestones", lx, ly, PvDraw.COLOR_MUTED);
		if (!crops.isEmpty()) {
			long sum = 0L;
			for (GardenSnapshot.CropRow crop : crops) {
				sum += crop.milestone();
			}
			String avg = "Average: " + FormatUtil.oneDecimal(sum / (double) crops.size());
			if (font.width("Crop milestones") + 10 + font.width(avg) <= lw) {
				PvDraw.textRight(g, font, avg, lx + lw, ly, PvDraw.COLOR_MUTED);
			}
		}
		ly += font.lineHeight + 4;

		// Readable 2-col Overview-style rows; last odd item centered; row gap fills panel height.
		int cols = 2;
		int colGap = GAP;
		int colW = (lw - colGap) / cols;
		int miniBarH = 3;
		int labelGap = 2;
		int blockH = font.lineHeight + labelGap + miniBarH;
		int minRowH = Math.max(ICON, blockH) + 2;
		int gridRows = crops.isEmpty() ? 0 : (crops.size() + cols - 1) / cols;
		int gridTop = ly;
		int gridH = y + h - PAD - gridTop;
		int rowH = gridRows <= 0 ? minRowH : Math.max(minRowH, gridH / gridRows);
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = 0;
		this.scroll = 0;

		g.enableScissor(lx, gridTop, lx + lw + 3, gridTop + gridH);
		int lastRow = Math.max(0, gridRows - 1);
		int lastRowCount = crops.isEmpty() ? 0 : ((crops.size() - 1) % cols) + 1;
		for (int i = 0; i < crops.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int rowItems = row == lastRow ? lastRowCount : cols;
			int rowW = rowItems * colW + Math.max(0, rowItems - 1) * colGap;
			int rowX = lx + Math.max(0, (lw - rowW) / 2);
			int bx = rowX + col * (colW + colGap);
			int by = gridTop + row * rowH;
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.CropRow crop = crops.get(i);
			int textX = bx + ICON + 3;
			int barW = Math.max(24, colW - ICON - 3);
			int iconY = by + Math.max(0, (blockH - ICON) / 2);
			GardenUi.drawIcon(g, crop.iconId(), bx, iconY, ICON, GardenData.cropPackModel(crop.id()));
			String name = crop.name();
			String lvl = String.valueOf(crop.milestone());
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, GardenUi.trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, crop.milestoneMaxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + labelGap, barW, miniBarH,
				crop.milestoneFill(), BAR_CROP, crop.milestoneMaxed());
			ui.zones.add(new GardenUi.HoverZone(bx, by, colW, blockH + 1, ui.cropMilestoneTip(snap, crop)));
		}
		g.disableScissor();

		GardenSnapshot.Composter c = snap.composter();
		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Composter", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		int cColGap = 8;
		int cColW = (rw - cColGap) / 2;
		int leftCol = rx;
		int rightCol = rx + cColW + cColGap;
		int row1 = ry;
		int row2 = ry + STAT_ROW + 1;
		GardenUi.statLine(g, font, "Organic Matter", FormatUtil.shortXp(c.organicMatter()), leftCol, row1, cColW, PvDraw.COLOR_TEXT);
		GardenUi.statLine(g, font, "Fuel", FormatUtil.shortXp(c.fuelUnits()), leftCol, row2, cColW, PvDraw.COLOR_TEXT);
		GardenUi.statLine(g, font, "Compost", FormatUtil.shortXp(c.compostUnits()), rightCol, row1, cColW, BAR_COMPOST);
		GardenUi.statLine(g, font, "Items", FormatUtil.shortXp(c.compostItems()), rightCol, row2, cColW, PvDraw.COLOR_MUTED);
		ry = row2 + STAT_ROW + 4;

		PvDraw.fill(g, rx, ry, rw, 1, PvDraw.COLOR_DIVIDER);
		ry += 5;

		int mapAvailH = Math.max(0, y + h - PAD - (ry + font.lineHeight + 4));
		int mapCell = Math.max(10, Math.min(16, (Math.min(mapAvailH, rw * 2 / 5) - PLOT_GAP * 4) / 5));
		int mapW = mapCell * 5 + PLOT_GAP * 4;
		boolean showMap = !GardenCosts.plots().isEmpty() && rw - mapW - GAP >= 110 && mapAvailH >= mapW;
		int listW = showMap ? rw - mapW - GAP : rw;
		int mapX = rx + listW + GAP;

		PvDraw.text(g, font, "Upgrades", rx, ry, PvDraw.COLOR_MUTED);
		drawCostToMax(snap, ui, g, font, rx, ry, listW);
		if (showMap) {
			PvDraw.text(g, font, "Plots", mapX, ry, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, snap.plotsUnlocked() + "/" + snap.plotsMax(), mapX + mapW, ry, PvDraw.COLOR_TEXT);
		}
		ry += font.lineHeight + 4;
		if (showMap) {
			drawPlotMap(snap, ui, g, mapX, ry, mapCell, mx, my, font);
		}

		List<GardenSnapshot.ComposterUpgrade> upgrades = c.upgrades();
		int upBarH = 3;
		int upLabelGap = 2;
		int upBlockH = font.lineHeight + upLabelGap + upBarH;
		int upRowH = Math.max(ICON, upBlockH) + 3;
		int availH = Math.max(upRowH, y + h - PAD - ry);
		if (!upgrades.isEmpty()) {
			upRowH = Math.max(Math.max(ICON, upBlockH) + 1, availH / upgrades.size());
		}
		for (int i = 0; i < upgrades.size(); i++) {
			int by = ry + i * upRowH;
			GardenSnapshot.ComposterUpgrade u = upgrades.get(i);
			int textX = rx + ICON + 4;
			int barW = Math.max(20, listW - ICON - 4);
			int iconY = by + Math.max(0, (upBlockH - ICON) / 2);
			GardenUi.drawIcon(g, u.iconId(), rx, iconY, ICON, GardenData.composterUpgradePackModel(u.id()));
			String name = shortComposterName(u);
			String lvl = u.level() + " / " + u.maxLevel();
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, GardenUi.trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, u.maxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + upLabelGap, barW, upBarH, u.fill(), BAR_COMPOST, u.maxed());
			List<PvTooltip.Line> tip = new ArrayList<>(List.of(
				PvTooltip.Line.of(u.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Purchased: " + u.level(), COMPLETED_C),
				PvTooltip.Line.of("Missing: " + u.missing(), REJECTED_C),
				PvTooltip.Line.of(u.maxed() ? "MAX" : FormatUtil.oneDecimal(u.fill() * 100) + "%", PvDraw.COLOR_MUTED)
			));
			if (!u.maxed() && GardenCosts.hasComposterCosts(u.id())) {
				tip.add(PvTooltip.Line.blank());
				tip.add(PvTooltip.Line.of("Cost to max", PvDraw.COLOR_TEXT));
				GardenUi.addComposterRemaining(tip, GardenCosts.composterRemaining(u.id(), u.level()));
			}
			ui.zones.add(new GardenUi.HoverZone(rx, by, listW, upBlockH + 1, tip));
		}
	}

	// Header totals the coin value of the crops still needed; Copper can't be bought so it stays in the tooltip.
	// A "+" marks a lower bound when some items have no price, "?" when none do.
	static String costToMaxLabel(GardenCosts.ComposterRemaining total, ToDoubleFunction<String> price, ToIntFunction<String> width, int avail) {
		if (total.none()) {
			return "Maxed";
		}
		if (!total.needsItems()) {
			return fit(List.of("To max: Copper only", "Copper only"), width, avail);
		}
		double coins = total.coins(price);
		String amount = coins <= 0D ? "?" : FormatUtil.shortCoins(coins) + (total.unpriced(price) > 0 ? "+" : "");
		return fit(List.of("To max: " + amount + " coins", amount + " coins", amount), width, avail);
	}

	private static String fit(List<String> labels, ToIntFunction<String> width, int avail) {
		for (String label : labels) {
			if (width.applyAsInt(label) <= avail) {
				return label;
			}
		}
		return labels.get(labels.size() - 1);
	}

	private static void drawCostToMax(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		GardenCosts.ComposterRemaining total = snap.composterCostToMax();
		String label = costToMaxLabel(total, ItemPricer::materialPrice, font::width, w - font.width("Upgrades") - 8);
		int labelW = font.width(label);
		PvDraw.textRight(g, font, label, x + w, y, total.none() ? COMPLETED_C : total.needsItems() ? PvDraw.COLOR_GOLD : COPPER);
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Composter cost to max", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		GardenUi.addComposterRemaining(tip, total);
		tip.add(PvTooltip.Line.blank());
		tip.add(PvTooltip.Line.meta("Remaining levels, all upgrades"));
		ui.zones.add(new GardenUi.HoverZone(x + w - labelW, y, labelW, font.lineHeight, tip));
	}

	private static void drawPlotMap(
		GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, int x, int y, int cell, int mx, int my, Font font
	) {
		int step = cell + PLOT_GAP;
		int barnX = x + 2 * step;
		int barnY = y + 2 * step;
		PvDraw.fill(g, barnX, barnY, cell, cell, PLOT_BARN);
		GardenUi.drawCellBorder(g, barnX, barnY, cell, cell);
		ui.zones.add(new GardenUi.HoverZone(barnX, barnY, cell, cell, List.of(PvTooltip.Line.title("Barn", PvDraw.COLOR_TEXT))));

		for (GardenSnapshot.PlotCell plotCell : snap.plotCells()) {
			GardenCosts.Plot plot = plotCell.plot();
			if (plot.x() < 0 || plot.x() > 4 || plot.y() < 0 || plot.y() > 4) {
				continue;
			}
			int cx = x + plot.x() * step;
			int cy = y + plot.y() * step;
			PvDraw.fill(g, cx, cy, cell, cell, plotCell.unlocked() ? PLOT_UNLOCKED : PLOT_LOCKED);
			boolean hovered = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell;
			if (hovered) {
				g.outline(cx, cy, cell, cell, PvDraw.COLOR_ACCENT);
			} else {
				GardenUi.drawCellBorder(g, cx, cy, cell, cell);
			}
			String num = String.valueOf(plot.number());
			if (font.width(num) <= cell - 2) {
				PvDraw.text(g, font, num, cx + (cell - font.width(num) + 1) / 2, cy + (cell - font.lineHeight) / 2 + 1,
					plotCell.unlocked() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			}

			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Plot " + plot.number(), plotCell.unlocked() ? COMPLETED_C : PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED,
				plotCell.unlocked() ? "Unlocked" : "Locked", plotCell.unlocked() ? COMPLETED_C : REJECTED_C));
			tip.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED, GardenUi.title(plot.tier()), PvDraw.COLOR_TEXT));
			GardenCosts.Cost cost = plotCell.unlockCost();
			if (cost != null) {
				tip.add(PvTooltip.Line.row("Unlock cost", PvDraw.COLOR_MUTED,
					FormatUtil.commas(cost.amount()) + "x " + GardenUi.costItemName(cost.itemId()), BAR_COMPOST));
				tip.add(PvTooltip.Line.meta("Cost scales with " + GardenUi.title(plot.tier()) + " plots owned"));
			}
			ui.zones.add(new GardenUi.HoverZone(cx, cy, cell, cell, tip));
		}
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

	private static String shortComposterName(GardenSnapshot.ComposterUpgrade u) {
		if (u == null || u.id() == null) {
			return "?";
		}
		return switch (u.id().toLowerCase(Locale.ROOT)) {
			case "organic_matter_cap" -> "Organic Cap";
			case "cost_reduction" -> "Cost Reduction";
			case "multi_drop" -> "Multi Drop";
			case "fuel_cap" -> "Fuel Cap";
			case "speed" -> "Speed";
			default -> u.name();
		};
	}
}

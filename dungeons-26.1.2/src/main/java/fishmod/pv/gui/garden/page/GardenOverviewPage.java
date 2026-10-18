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
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.garden.GardenUi;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.overview.SkillStatRows;
import fishmod.pv.gui.overview.SummaryCards;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.garden.GardenUi.*;

public final class GardenOverviewPage {
	private static final List<String> FORTUNE_EXCLUSIONS = List.of(
		"Excludes held tool, crop-specific fortune,",
		"set bonuses and temporary buffs."
	);

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int[] fortuneRow;

	/** True when the click lands on the Farming Fortune row and its breakdown is available. */
	public boolean fortuneRowHit(double mx, double my) {
		int[] r = this.fortuneRow;
		return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	public void render(GardenSnapshot snap, PlayerStatsSnapshot stats, GardenUi ui, 
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my, boolean partial
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;
		int rowH = GardenUi.barRowH(font);

		int gardenOverflow = snap.gardenOverflowLevel();
		String gardenValue = gardenOverflow > snap.gardenLevel()
			? snap.gardenLevel() + " (" + gardenOverflow + ")"
			: String.valueOf(snap.gardenLevel());
		ly = ui.drawBar(g, font, "Garden", gardenValue,
			snap.gardenFill(), snap.gardenMaxed(), BAR_GARDEN,
			snap.gardenHover(), lx, ly, lw, mx, my) + BAR_AFTER;
		ly = ui.drawBar(g, font, "Farming", String.valueOf(snap.farmingLevel()),
			snap.farmingFill(), snap.farmingMaxed(), BAR_FARM,
			snap.farmingHover(), lx, ly, lw, mx, my) + BAR_AFTER + 2;

		ly = GardenUi.statLine(g, font, "Plots",
			snap.islandLoaded()
				? snap.plotsUnlocked() + " / " + snap.plotsMax()
				: "...",
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = GardenUi.statLine(g, font, "Copper", FormatUtil.commas(snap.copper()), lx, ly, lw, COPPER) + 1;
		ly = GardenUi.statLine(g, font, "Larva consumed", FormatUtil.commas(snap.larvaConsumed()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 2;

		GardenSnapshot.FarmingWeightInfo weight = snap.farmingWeight();
		String weightValue = weight.loading() ? "..."
			: weight.loaded() ? FormatUtil.oneDecimal(weight.displayTotal()) : "-";
		ly = GardenUi.statLine(g, font, "Farming weight", weightValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 1;
		if (weight.loaded()) {
			ui.zones.add(new GardenUi.HoverZone(lx, ly - STAT_ROW, lw, STAT_ROW, farmingWeightHover(weight)));
			ly += 2;
		} else if (!weight.error().isBlank()) {
			ly = GardenUi.statLine(g, font, "Weight", GardenUi.trim(font, weight.error(), lw / 2), lx, ly, lw, PvDraw.COLOR_MUTED) + 3;
		} else {
			ly += 2;
		}
		SummaryCards.Stat fortune = FarmingFortuneTip.row(stats, snap);
		this.fortuneRow = null;
		if (fortune == null) {
			fortune = SkillStatRows.row(stats, "farming_fortune", FORTUNE_EXCLUSIONS);
		} else {
			this.fortuneRow = new int[] {lx - 2, ly - 1, lw + 4, STAT_ROW};
			if (mx >= lx - 2 && mx < lx + lw + 2 && my >= ly - 1 && my < ly - 1 + STAT_ROW) {
				PvDraw.fill(g, lx - 2, ly - 1, lw + 4, STAT_ROW, 0x14FFFFFF);
			}
		}
		ui.zones.add(new GardenUi.HoverZone(lx, ly, lw, STAT_ROW, fortune.tip()));
		GardenUi.drawPair(g, font, fortune.label(), fortune.value(), lx, ly, lw, fortune.labelColor(), fortune.valueColor());
		ly += STAT_ROW + 2;

		if (!snap.islandLoaded() && partial) {
			PvDraw.text(g, font, GardenUi.trim(font, snap.islandLoading() || snap.islandError().isBlank()
				? "Loading island..." : snap.islandError(), lw), lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 4;
		}

		List<GardenSnapshot.ChipEntry> chips = snap.gardenChips();
		if (!chips.isEmpty()) {
			ly += 8;
			PvDraw.text(g, font, "Garden chips", lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 3;
			int cols = Math.min(5, Math.max(4, (lw + CHIP_GAP) / (CHIP_CELL + CHIP_GAP)));
			int rowsNeeded = Math.max(1, (chips.size() + cols - 1) / cols);
			// Leave room for farming toolkit below; shrink row height so every chip fits (do not clip to 1 row).
			int toolkitReserve = 52;
			int avail = Math.max(CHIP_CELL + font.lineHeight + 2, (y + h - PAD - toolkitReserve) - ly);
			int naturalCellH = CHIP_CELL + font.lineHeight + 2;
			int cellH = Math.min(naturalCellH, Math.max(CHIP_CELL + 2, (avail - (rowsNeeded - 1) * CHIP_GAP) / rowsNeeded));
			int iconSize = Math.min(ICON, Math.max(10, cellH - font.lineHeight - 2));
			int gridW = cols * CHIP_CELL + (cols - 1) * CHIP_GAP;
			int gridX = lx + Math.max(0, (lw - gridW) / 2);
			for (int i = 0; i < chips.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int bx = gridX + col * (CHIP_CELL + CHIP_GAP);
				int by = ly + row * (cellH + CHIP_GAP);
				GardenSnapshot.ChipEntry chip = chips.get(i);
				GardenUi.drawCellBorder(g, bx, by, CHIP_CELL, Math.min(CHIP_CELL, cellH - font.lineHeight));
				int iconX = bx + (CHIP_CELL - iconSize) / 2;
				int iconY = by + Math.max(0, (Math.min(CHIP_CELL, cellH - font.lineHeight) - iconSize) / 2);
				GardenUi.drawIcon(g, chip.iconId(), iconX, iconY, iconSize, chipPackModel(chip.id()));
				String lvl = String.valueOf(chip.level());
				int lvlY = by + Math.min(CHIP_CELL, cellH - font.lineHeight) + 1;
				PvDraw.text(g, font, lvl, bx + (CHIP_CELL - font.width(lvl)) / 2, lvlY, PvDraw.COLOR_ACCENT);
				GardenCosts.Progress sowdust = chip.sowdust();
				ui.zones.add(new GardenUi.HoverZone(bx, by, CHIP_CELL, cellH, List.of(
					PvTooltip.Line.of(chip.name(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.of("Level " + chip.level(), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Sowdust", PvDraw.COLOR_MUTED,
						FormatUtil.commas(sowdust.spent()) + " / " + FormatUtil.commas(sowdust.total())
							+ " (" + FormatUtil.percent(sowdust.fraction()) + ")",
						sowdust.maxed() ? COMPLETED_C : PvDraw.COLOR_TEXT)
				)));
			}
			ly += rowsNeeded * (cellH + CHIP_GAP) + 4;
		}

		drawFarmingToolkit(snap, ui, g, font, lx, ly, lw, y + h - PAD, mx, my);

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Crop milestones", rx, ry, PvDraw.COLOR_MUTED);
		if (snap.islandLoaded() && !snap.crops().isEmpty()) {
			int[] totals = milestoneTotals(snap.crops());
			String maxed = totals[0] + "/" + snap.crops().size() + " maxed";
			boolean allMaxed = totals[0] == snap.crops().size();
			PvDraw.textRight(g, font, maxed, rx + rw, ry, allMaxed ? COMPLETED_C : PvDraw.COLOR_MUTED);
			ui.zones.add(new GardenUi.HoverZone(rx + rw - font.width(maxed), ry, font.width(maxed), font.lineHeight, List.of(
				PvTooltip.Line.row("Crops maxed", PvDraw.COLOR_MUTED, totals[0] + "/" + snap.crops().size(),
					allMaxed ? COMPLETED_C : PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Milestone levels", PvDraw.COLOR_MUTED, FormatUtil.commas(totals[1]), PvDraw.COLOR_TEXT)
			)));
		}
		ry += font.lineHeight + 4;

		if (!snap.islandLoaded()) {
			PvDraw.textCentered(g, font, partial ? "..." : "No crop data",
				x + leftW + GAP + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		List<GardenSnapshot.CropRow> crops = snap.crops();
		int cols = 2;
		int iconGap = 3;
		int colW = (rw - GAP) / cols;
		int barW = Math.max(40, colW - ICON - iconGap);
		int gridTop = ry;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, ((crops.size() + 1) / cols) * rowH - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, gridTop, rx + rw + 3, gridTop + gridH);
		int yy = gridTop - this.scroll;
		for (int i = 0; i < crops.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = rx + col * (colW + GAP);
			int by = yy + row * rowH;
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.CropRow crop = crops.get(i);
			int iconY = by + Math.max(0, (font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT - ICON) / 2);
			GardenUi.drawIcon(g, crop.iconId(), bx, iconY, ICON, GardenData.cropPackModel(crop.id()));
			ui.drawBar(g, font, crop.name(), String.valueOf(crop.milestone()), crop.milestoneFill(),
				crop.milestoneMaxed(), BAR_CROP, null, bx + ICON + iconGap, by, barW, mx, my);
			int barBottom = by + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
			ui.zones.add(new GardenUi.HoverZone(bx, by, colW, Math.max(rowH, barBottom - by), ui.cropMilestoneTip(snap, crop)));
		}
		g.disableScissor();
	}


	/** {maxed crops, summed milestone levels}. */
	static int[] milestoneTotals(List<GardenSnapshot.CropRow> crops) {
		int maxed = 0;
		int levels = 0;
		for (GardenSnapshot.CropRow crop : crops) {
			if (crop.milestoneMaxed()) {
				maxed++;
			}
			levels += Math.max(0, crop.milestone());
		}
		return new int[] { maxed, levels };
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

	/** Crops holding a tool, crop sections in the toolkit, and filled slots. */
	static int[] stockedCrops(List<GardenSnapshot.FarmingToolkitSlot> slots) {
		Set<String> sections = new HashSet<>();
		Set<String> stocked = new HashSet<>();
		int tools = 0;
		for (GardenSnapshot.FarmingToolkitSlot slot : slots) {
			sections.add(slot.cropId());
			if (slot.item() != null && !slot.item().isEmpty()) {
				stocked.add(slot.cropId());
				tools++;
			}
		}
		return new int[] {stocked.size(), sections.size(), tools};
	}

	private static void drawFarmingToolkit(
		GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int bottom, int mx, int my
	) {
		if (!snap.farmingToolkitUnlocked() && snap.farmingToolkitSlots().isEmpty()) {
			return;
		}
		if (y + font.lineHeight + STAT_ROW > bottom) {
			return;
		}
		PvDraw.text(g, font, "Farming toolkit", x, y, PvDraw.COLOR_MUTED);
		y += font.lineHeight + 3;
		y = GardenUi.statLine(g, font, "Unlocked", snap.farmingToolkitUnlocked() ? "Yes" : "No",
			x, y, w, snap.farmingToolkitUnlocked() ? 0xFF55FF55 : PvDraw.COLOR_MUTED) + 1;
		int[] stocked = stockedCrops(snap.farmingToolkitSlots());
		ui.zones.add(new GardenUi.HoverZone(x, y, w, STAT_ROW, List.of(
			PvTooltip.Line.title("Crops with tools", PvDraw.COLOR_TEXT),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Crop sections holding at least one tool.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.row("Tools stored", PvDraw.COLOR_MUTED, String.valueOf(stocked[2]), PvDraw.COLOR_TEXT)
		)));
		y = GardenUi.statLine(g, font, "Crops with tools", stocked[0] + " / " + stocked[1],
			x, y, w, PvDraw.COLOR_ACCENT) + 3;

		List<GardenSnapshot.FarmingToolkitSlot> slots = snap.farmingToolkitSlots();
		if (slots.isEmpty() || y + SLOT > bottom) {
			return;
		}
		int cols = Math.max(3, Math.min(5, (w + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int col = 0;
		int rowY = y;
		String lastCrop = "";
		for (GardenSnapshot.FarmingToolkitSlot slot : slots) {
			if (!slot.cropId().equals(lastCrop)) {
				if (!lastCrop.isEmpty()) {
					rowY += SLOT + SLOT_GAP + 2;
					col = 0;
				}
				if (rowY + font.lineHeight + SLOT > bottom) {
					break;
				}
				PvDraw.text(g, font, slot.label(), x, rowY, PvDraw.COLOR_MUTED);
				rowY += font.lineHeight + 2;
				lastCrop = slot.cropId();
			}
			if (col >= cols) {
				col = 0;
				rowY += SLOT + SLOT_GAP;
			}
			if (rowY + SLOT > bottom) {
				break;
			}
			int bx = x + col * (SLOT + SLOT_GAP);
			int by = rowY;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			GardenUi.drawCellBorder(g, bx, by, SLOT, SLOT);
			if (hovered) {
				g.outline(bx, by, SLOT, SLOT, PvDraw.COLOR_ACCENT);
			}
			InventorySnapshot.Slot item = slot.item();
			boolean filled = item != null && !item.isEmpty();
			ItemStack stack = ItemStack.EMPTY;
			if (filled) {
				stack = SkyBlockItemFactory.toStack(item);
				if (stack == null || stack.isEmpty()) {
					stack = SkyBlockItemFactory.iconStack(item.id());
				}
			}
			if (stack == null || stack.isEmpty()) {
				String cropIcon = GardenData.cropIconId(slot.cropId());
				stack = SkyBlockItemFactory.iconStack(cropIcon);
			}
			if (stack == null || stack.isEmpty()) {
				stack = new ItemStack(Items.WHEAT);
			}
			g.item(stack, bx + 1, by + 1);
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(slot.label(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			if (filled) {
				String name = item.displayName() == null || item.displayName().isBlank()
					? GardenData.prettyCrop(item.id()) : item.displayName();
				tip.add(PvTooltip.Line.row("Item", PvDraw.COLOR_MUTED, name, PvDraw.COLOR_ACCENT));
			} else {
				tip.add(PvTooltip.Line.meta("Empty"));
			}
			ui.zones.add(new GardenUi.HoverZone(bx, by, SLOT, SLOT, tip));
			col++;
		}
	}

	private static String chipPackModel(String id) {
		String key = GardenData.normalizeChipKey(id);
		if (key.isBlank()) {
			return "";
		}
		String file = switch (key) {
			case "vermin_vaporize" -> "vermin_vaporizer_chip";
			default -> key + "_chip";
		};
		return "hypixel_skyblock:item/island_relevant/garden/chips/" + file;
	}

	static List<PvTooltip.Line> farmingWeightHover(GardenSnapshot.FarmingWeightInfo weight) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("Farming weight", PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.of("Total: " + FormatUtil.oneDecimal(weight.displayTotal()), PvDraw.COLOR_TEXT));
		List<Map.Entry<String, Double>> bonuses = new ArrayList<>(weight.bonusWeight().entrySet());
		bonuses.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Map.Entry<String, Double>> shownBonus = new ArrayList<>();
		for (Map.Entry<String, Double> e : bonuses) {
			if (e.getValue() != null && e.getValue() > 0) {
				shownBonus.add(e);
			}
		}
		if (!shownBonus.isEmpty()) {
			tip.add(PvTooltip.Line.of("Bonus", PvDraw.COLOR_ACCENT));
			for (Map.Entry<String, Double> e : shownBonus) {
				tip.add(PvTooltip.Line.of(
					prettyBonusWeight(e.getKey()) + ": " + FormatUtil.oneDecimal(e.getValue()),
					PvDraw.COLOR_MUTED
				));
			}
		}
		List<Map.Entry<String, Double>> crops = new ArrayList<>(weight.cropWeight().entrySet());
		crops.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Map.Entry<String, Double>> shownCrops = new ArrayList<>();
		for (Map.Entry<String, Double> e : crops) {
			if (e.getValue() != null && e.getValue() > 0) {
				shownCrops.add(e);
			}
		}
		if (!shownCrops.isEmpty()) {
			tip.add(PvTooltip.Line.of("Crops", PvDraw.COLOR_ACCENT));
			int shown = 0;
			for (Map.Entry<String, Double> e : shownCrops) {
				if (shown++ >= 12) {
					break;
				}
				tip.add(PvTooltip.Line.of(
					GardenData.prettyCrop(e.getKey()) + ": " + FormatUtil.oneDecimal(e.getValue()),
					PvDraw.COLOR_MUTED
				));
			}
		}
		tip.add(PvTooltip.Line.of("Provided by elitebot.dev", CREDIT_C));
		return tip;
	}

	private static String prettyBonusWeight(String key) {
		if (key == null || key.isBlank()) {
			return "?";
		}
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "farming_level", "farminglevel" -> "Farming level";
			case "anita" -> "Anita";
			case "contests", "jacob_contests", "jacob" -> "Contests";
			case "minions" -> "Minions";
			case "pests" -> "Pests";
			default -> GardenUi.title(key.replace('-', '_'));
		};
	}
}

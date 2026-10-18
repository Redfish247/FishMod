/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.garden.GardenUi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.garden.GardenUi.*;

/** Garden visitors subpage. */
public final class VisitorsPage {
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void render(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int rightW = Math.max(140, w * 30 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		List<GardenSnapshot.VisitorRow> list = snap.visitors();
		int cols = Math.max(1, (lw + SLOT_GAP) / (VISITOR_CELL_W + SLOT_GAP));
		int cellW = VISITOR_CELL_W;
		int cellH = VISITOR_CELL_H;
		int rows = (list.size() + cols - 1) / cols;
		int gridTop = ly;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, rows * (cellH + SLOT_GAP) - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(lx, gridTop, lx + lw + 3, gridTop + gridH);
		int baseY = gridTop - this.scroll;
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (cellW + SLOT_GAP);
			int by = baseY + row * (cellH + SLOT_GAP);
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.VisitorRow v = list.get(i);
			GardenSnapshot.VisitorState state = v.state();
			PvDraw.fill(g, bx, by, cellW, cellH, state == GardenSnapshot.VisitorState.NEVER ? 0xFF2A2A32 : 0xFF1E5A2A);
			GardenUi.drawCellBorder(g, bx, by, cellW, cellH);
			int iconX = bx + (cellW - ICON) / 2;
			GardenUi.drawIcon(g, v.npcItemId(), iconX, by + (cellH - ICON) / 2, ICON, null);
			String rarity = GardenData.visitorRarity(v.id());
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(v.name(), PvDraw.COLOR_TEXT));
			if (!rarity.isBlank()) {
				tip.add(PvTooltip.Line.of(GardenData.prettyVisitorRarity(rarity), rarityColor(rarity)));
			}
			tip.add(switch (state) {
				case ACCEPTED -> PvTooltip.Line.of("Offer accepted", COMPLETED_C);
				case VISITED -> PvTooltip.Line.of("Visited, never accepted", 0xFFFFFF55);
				case NEVER -> PvTooltip.Line.of("Not encountered", PvDraw.COLOR_MUTED);
			});
			tip.add(PvTooltip.Line.of("Visits: " + FormatUtil.commas(v.visits()), VISITS_C));
			tip.add(PvTooltip.Line.of("Completed: " + FormatUtil.commas(v.completed()), COMPLETED_C));
			tip.add(PvTooltip.Line.of("Rejected: " + FormatUtil.commas(v.rejected()), REJECTED_C));
			ui.zones.add(new GardenUi.HoverZone(bx, by, cellW, cellH, tip));
		}
		g.disableScissor();

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		ry = ui.drawBar(g, font, "Offers MS", String.valueOf(snap.visitorMilestone()),
			snap.visitorMilestoneFill(), snap.visitorMilestoneMaxed(), BAR_VISITOR,
			snap.visitorMilestoneHover(), rx, ry, rw, mx, my) + BAR_AFTER + 2;
		ry = ui.drawBar(g, font, "Unique MS", String.valueOf(snap.uniqueVisitorMilestone()),
			snap.uniqueVisitorMilestoneFill(), snap.uniqueVisitorMilestoneMaxed(), BAR_GARDEN,
			snap.uniqueVisitorMilestoneHover(), rx, ry, rw, mx, my) + BAR_AFTER + 2;
		ry = GardenUi.statLine(g, font, "Offers accepted", FormatUtil.commas(snap.visitorsCompleted()),
			rx, ry, rw, COMPLETED_C) + 1;
		ry = GardenUi.statLine(g, font, "Offers declined", FormatUtil.commas(snap.totalRejected()),
			rx, ry, rw, REJECTED_C) + 1;
		ry = GardenUi.statLine(g, font, "Total visits", FormatUtil.commas(snap.totalVisits()),
			rx, ry, rw, VISITS_C) + 1;
		ry = GardenUi.statLine(g, font, "Unique served", FormatUtil.commas(snap.uniqueVisitors()),
			rx, ry, rw, PvDraw.COLOR_MUTED) + 1;

		List<GardenSnapshot.ActiveVisitor> active = snap.activeVisitors();
		String activeValue = !snap.islandLoaded()
			? "..."
			: FormatUtil.commas(active.size());
		int activeY = ry;
		ry = GardenUi.statLine(g, font, "Active now", activeValue,
			rx, ry, rw, active.isEmpty() ? PvDraw.COLOR_MUTED : COMPLETED_C) + 1;
		if (snap.islandLoaded() && !active.isEmpty()) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Active visitors", PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			int shown = 0;
			for (GardenSnapshot.ActiveVisitor v : active) {
				if (shown >= 8) {
					tip.add(PvTooltip.Line.meta("+" + (active.size() - shown) + " more"));
					break;
				}
				String status = v.status() == null || v.status().isBlank() ? "" : v.status();
				String detail = v.detail() == null || v.detail().isBlank() ? "" : v.detail();
				String right = !status.isBlank() && !detail.isBlank()
					? status + " · " + detail
					: (!status.isBlank() ? status : detail);
				tip.add(PvTooltip.Line.row(
					v.name(), PvDraw.COLOR_TEXT,
					right.isBlank() ? "-" : right, PvDraw.COLOR_MUTED
				));
				shown++;
			}
			ui.zones.add(new GardenUi.HoverZone(rx, activeY, rw, STAT_ROW, tip));
		}

		ry += STAT_ROW + 1;
		PvDraw.text(g, font, "Accepted Rarities", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		Map<String, Long> accepted = acceptedRarityTotals(snap.visitors());
		for (String rarity : GardenData.visitorRarityOrder()) {
			long count = accepted.getOrDefault(rarity, 0L);
			if (count <= 0L) {
				continue;
			}
			ry = GardenUi.statLine(g, font, GardenData.prettyVisitorRarity(rarity), FormatUtil.commas(count),
				rx, ry, rw, rarityColor(rarity)) + 1;
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

	private static Map<String, Long> acceptedRarityTotals(List<GardenSnapshot.VisitorRow> visitors) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (GardenSnapshot.VisitorRow v : visitors) {
			if (v.completed() <= 0L) {
				continue;
			}
			String rarity = GardenData.visitorRarity(v.id());
			if (rarity.isBlank()) {
				continue;
			}
			out.merge(rarity.toUpperCase(Locale.ROOT), v.completed(), Long::sum);
		}
		return out;
	}

	private static int rarityColor(String rarity) {
		if (rarity == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			default -> PvDraw.COLOR_MUTED;
		};
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.fishing;

import fishmod.pv.data.FishingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.TrophyFishData;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Trophy table: one row per fish with a count per tier, split across two blocks. */
public final class FishingTrophyTrialPage {
	private static final int PAD = 6;
	private static final int ICON = 12;
	private static final int BLOCK_GAP = 10;
	private static final int ROW_H_MAX = 14;
	private static final int ROW_H_MIN = 10;
	private static final int COUNT_W = 26;
	private static final TrophyFishData.Tier[] TIERS = {
		TrophyFishData.Tier.BRONZE,
		TrophyFishData.Tier.SILVER,
		TrophyFishData.Tier.GOLD,
		TrophyFishData.Tier.DIAMOND
	};

	private record Zone(int x, int y, int w, int h, FishingSnapshot.TrophyRow row) {
	}

	private final List<Zone> zones = new ArrayList<>();

	public void render(
		GuiGraphicsExtractor g, Font font, FishingSnapshot snapshot,
		int x, int y, int w, int h
	) {
		this.zones.clear();
		FishingSnapshot data = snapshot == null ? FishingSnapshot.empty() : snapshot;
		List<FishingSnapshot.TrophyRow> rows = new ArrayList<>(FishingPage.sortedRows(data.trophyFish()));
		rows.addAll(FishingPage.sortedRows(data.trophyFrogs()));

		PvDraw.innerPanel(g, x, y, w, h);
		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No data", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int innerX = x + PAD;
		int innerW = w - PAD * 2;
		int top = y + PAD;
		PvDraw.text(g, font, "Trophy fish", innerX, top, PvDraw.COLOR_MUTED);
		String totals = FormatUtil.commas(data.trophyFishTotal()) + " caught";
		PvDraw.textRight(g, font, totals, innerX + innerW, top, PvDraw.COLOR_ACCENT);
		top += font.lineHeight + 4;

		int blocks = innerW >= 330 ? 2 : 1;
		int perBlock = (rows.size() + blocks - 1) / blocks;
		int blockW = (innerW - BLOCK_GAP * (blocks - 1)) / blocks;
		int headerH = font.lineHeight + 3;
		int avail = y + h - PAD - top - headerH;
		int rowH = Math.max(ROW_H_MIN, Math.min(ROW_H_MAX, avail / Math.max(1, perBlock)));

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (int b = 0; b < blocks; b++) {
			int bx = innerX + b * (blockW + BLOCK_GAP);
			drawHeader(g, font, bx, top, blockW);
			int ry = top + headerH;
			for (int i = b * perBlock; i < Math.min(rows.size(), (b + 1) * perBlock); i++) {
				drawRow(g, font, rows.get(i), bx, ry, blockW, rowH, (i - b * perBlock) % 2 == 1);
				ry += rowH;
			}
		}
		g.disableScissor();
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (Zone zone : this.zones) {
			if (mouseX >= zone.x() && mouseX < zone.x() + zone.w() && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
				FishingPage.drawTrophyStatTooltip(g, font, zone.row(), mouseX, mouseY, screenW, screenH);
				return;
			}
		}
	}

	private static void drawHeader(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		for (int i = 0; i < TIERS.length; i++) {
			int right = x + w - (TIERS.length - 1 - i) * COUNT_W;
			String title = switch (TIERS[i]) {
				case BRONZE -> "Brz";
				case SILVER -> "Slv";
				case GOLD -> "Gld";
				case DIAMOND -> "Dia";
			};
			PvDraw.textRight(g, font, title, right, y, FishingPage.tierColor(TIERS[i]));
		}
		PvDraw.fill(g, x, y + font.lineHeight + 1, w, 1, 0x33FFFFFF);
	}

	private void drawRow(
		GuiGraphicsExtractor g, Font font, FishingSnapshot.TrophyRow row, int x, int y, int w, int h, boolean stripe
	) {
		if (stripe) {
			PvDraw.fill(g, x, y, w, h, 0x14FFFFFF);
		}
		int iconY = y + (h - ICON) / 2;
		TrophyFishData.Tier best = row.counts().bestDiscovered();
		ItemStack skull = ItemStack.EMPTY;
		if (best != null) {
			skull = SkyBlockItemFactory.trophySkullStack(row.id() + "_" + best.suffix());
		}
		if (skull.isEmpty()) {
			skull = new ItemStack(best == null ? Items.GRAY_DYE : Items.TROPICAL_FISH);
		}
		float scale = ICON / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x + 1, iconY);
		g.pose().scale(scale, scale);
		g.item(skull, 0, 0);
		g.pose().popMatrix();

		int textY = y + (h - font.lineHeight) / 2;
		int nameX = x + ICON + 5;
		int nameMax = Math.max(8, w - TIERS.length * COUNT_W - (nameX - x));
		String name = FishingPage.trim(font, row.name() == null ? "" : row.name(), nameMax);
		PvDraw.text(g, font, name, nameX, textY, row.anyDiscovered() ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);

		for (int i = 0; i < TIERS.length; i++) {
			int right = x + w - (TIERS.length - 1 - i) * COUNT_W;
			int count = row.counts().of(TIERS[i]);
			if (count > 0) {
				PvDraw.textRight(g, font, FormatUtil.commas(count), right, textY, FishingPage.tierColor(TIERS[i]));
			} else {
				PvDraw.textRight(g, font, "-", right, textY, PvDraw.COLOR_BAR_BG);
			}
		}
		this.zones.add(new Zone(x, y, w, h, row));
	}
}

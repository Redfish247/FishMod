/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page;

import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.EssenceSummary;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.dungeons.EssencePerkTips;
import fishmod.pv.dungeons.EssenceShopCosts;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.essence.EssenceIcons;
import fishmod.pv.gui.essence.EssenceLayout;
import fishmod.pv.gui.essence.EssenceShopColumn;
import fishmod.pv.gui.essence.EssenceShops;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Home → Essence: shop selector on the left, the selected shop's perks and progress on the right. */
public final class EssencePage {
	private static final int ROW_SELECTED = 0x50386CC8;
	private static final int ROW_HOVER = 0x24FFFFFF;
	private static final int SCROLL_TRACK = 0x662A2A35;
	private static final int ICON_GAP = 4;
	private static final int NAME_BALANCE_GAP = 8;
	private static final List<Integer> GROUP_SIZES = groupSizes();

	private List<EssenceShops.Entry> entries = EssenceShops.resolve(null);
	private int selected;
	private int selectorScroll;
	private int perkScroll;
	private boolean revealSelected;
	private EssenceLayout.Selector selectorLayout;
	private EssenceLayout.Perks perkLayout;
	private final List<Zone> zones = new ArrayList<>();
	private final List<RowHit> rowHits = new ArrayList<>();

	public void apply(DungeonSnapshot dungeons, MiningSnapshot mining, ForagingSnapshot foraging, CrimsonSnapshot crimson) {
		applyShops(EssenceShops.lookup(dungeons, mining, foraging, crimson));
	}

	public void applyShops(Function<String, DungeonSnapshot.EssenceShop> lookup) {
		this.entries = EssenceShops.resolve(lookup);
		this.zones.clear();
		this.rowHits.clear();
	}

	public List<EssenceShops.Entry> entries() {
		return this.entries;
	}

	public EssenceShops.Entry selectedEntry() {
		return this.entries.get(this.selected);
	}

	public boolean select(String id) {
		int index = EssenceShops.indexOf(id);
		if (index < 0) {
			return false;
		}
		select(index);
		return true;
	}

	public void select(int index) {
		int clamped = Math.max(0, Math.min(this.entries.size() - 1, index));
		if (clamped != this.selected) {
			this.selected = clamped;
			this.perkScroll = 0;
		}
		this.revealSelected = true;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		this.zones.clear();
		this.rowHits.clear();
		EssenceLayout.Rect body = new EssenceLayout.Rect(x, y, w, h);
		int selectorW = EssenceLayout.selectorWidth(w, widestRow(font));
		drawSelector(g, font, new EssenceLayout.Rect(x, y, selectorW, h), mouseX, mouseY);
		drawDetail(g, font, EssenceLayout.detail(body, selectorW), mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (Zone zone : this.zones) {
			if (zone.rect().contains(mouseX, mouseY)) {
				PvTooltip.drawStyled(g, font, zone.lines(), mouseX, mouseY, screenW, screenH);
				return;
			}
		}
	}

	public boolean mouseClicked(double mx, double my) {
		for (RowHit hit : this.rowHits) {
			if (hit.rect().contains(mx, my)) {
				select(hit.index());
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double scrollY) {
		if (scrollY == 0D) {
			return false;
		}
		int dir = scrollY > 0D ? -1 : 1;
		EssenceLayout.Selector sel = this.selectorLayout;
		if (sel != null && sel.panel().contains(mx, my)) {
			if (sel.maxScroll() <= 0) {
				return false;
			}
			this.selectorScroll = EssenceLayout.clampScroll(this.selectorScroll + dir * sel.rowH(), sel.maxScroll());
			return true;
		}
		EssenceLayout.Perks perks = this.perkLayout;
		if (perks != null && perks.list().contains(mx, my) && perks.maxScroll() > 0) {
			this.perkScroll = EssenceLayout.clampScroll(this.perkScroll + dir * perks.rowH(), perks.maxScroll());
			return true;
		}
		return false;
	}

	public boolean keyPressed(int keyCode) {
		if (keyCode == 264) {
			select(this.selected + 1);
			return true;
		}
		if (keyCode == 265) {
			select(this.selected - 1);
			return true;
		}
		return false;
	}

	private int widestRow(Font font) {
		int widest = 0;
		for (EssenceShops.Entry entry : this.entries) {
			int row = 16 + ICON_GAP + font.width(entry.def().name()) + NAME_BALANCE_GAP + font.width(balanceShort(entry.shop()));
			widest = Math.max(widest, row);
		}
		return widest;
	}

	private void drawSelector(GuiGraphicsExtractor g, Font font, EssenceLayout.Rect panel, int mouseX, int mouseY) {
		PvDraw.innerPanel(g, panel.x(), panel.y(), panel.w(), panel.h());
		EssenceLayout.Selector layout = EssenceLayout.selector(panel, font.lineHeight, GROUP_SIZES);
		this.selectorLayout = layout;
		EssenceLayout.Rect list = layout.list();
		if (this.revealSelected) {
			int top = layout.rowY()[this.selected];
			if (top < this.selectorScroll) {
				this.selectorScroll = top;
			} else if (top + layout.rowH() > this.selectorScroll + list.h()) {
				this.selectorScroll = top + layout.rowH() - list.h();
			}
			this.revealSelected = false;
		}
		this.selectorScroll = EssenceLayout.clampScroll(this.selectorScroll, layout.maxScroll());
		int scroll = this.selectorScroll;
		boolean mouseInList = list.contains(mouseX, mouseY);

		g.enableScissor(panel.x() + 1, list.y(), panel.right() - 1, list.bottom());
		for (int gi = 0; gi < layout.headerY().length; gi++) {
			PvDraw.sectionHeader(g, font, EssenceShops.Group.values()[gi].label(), list.x(), list.y() + layout.headerY()[gi] - scroll, list.w());
		}
		for (int dy : layout.dividerY()) {
			PvDraw.fill(g, list.x(), list.y() + dy - scroll, list.w(), 1, PvDraw.COLOR_DIVIDER);
		}
		int rowH = layout.rowH();
		int iconSize = Math.min(16, rowH);
		for (int i = 0; i < this.entries.size(); i++) {
			int rowTop = list.y() + layout.rowY()[i] - scroll;
			if (rowTop + rowH <= list.y() || rowTop >= list.bottom()) {
				continue;
			}
			EssenceShops.Entry entry = this.entries.get(i);
			EssenceLayout.Rect row = new EssenceLayout.Rect(panel.x() + 1, rowTop, panel.w() - 2, rowH);
			boolean hovered = mouseInList && row.contains(mouseX, mouseY);
			if (i == this.selected) {
				PvDraw.fill(g, row.x(), row.y(), row.w(), row.h(), ROW_SELECTED);
			} else if (hovered) {
				PvDraw.fill(g, row.x(), row.y(), row.w(), row.h(), ROW_HOVER);
			}
			PvDraw.IconTextAlign align = PvDraw.IconTextAlign.of(rowTop, rowH, iconSize, font.lineHeight);
			drawIcon(g, EssenceIcons.shop(entry.shop().iconId()), list.x(), align.iconY(), iconSize);
			String balance = balanceShort(entry.shop());
			int balanceW = font.width(balance);
			int nameX = list.x() + iconSize + ICON_GAP;
			String name = trim(font, entry.def().name(), Math.max(4, list.right() - balanceW - NAME_BALANCE_GAP - nameX));
			PvDraw.text(g, font, name, nameX, align.textY(), entry.def().color());
			PvDraw.textRight(g, font, balance, list.right(), align.textY(), loaded(entry.shop()) ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
			int hitTop = Math.max(rowTop, list.y());
			int hitBottom = Math.min(rowTop + rowH, list.bottom());
			this.rowHits.add(new RowHit(i, new EssenceLayout.Rect(row.x(), hitTop, row.w(), hitBottom - hitTop)));
		}
		g.disableScissor();

		if (layout.maxScroll() > 0) {
			drawScrollCue(g, panel.right() - 3, list.y(), list.h(), scroll, layout.maxScroll());
		}
	}

	private void drawDetail(GuiGraphicsExtractor g, Font font, EssenceLayout.Rect detail, int mouseX, int mouseY) {
		PvDraw.innerPanel(g, detail.x(), detail.y(), detail.w(), detail.h());
		EssenceShops.Entry entry = selectedEntry();
		DungeonSnapshot.EssenceShop shop = entry.shop();
		int color = entry.def().color();
		int cx = detail.x() + EssenceLayout.PAD;
		int cw = detail.w() - EssenceLayout.PAD * 2;
		int top = detail.y() + EssenceLayout.PAD;

		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(top, EssenceLayout.DETAIL_HEADER_H, 16, font.lineHeight);
		g.item(EssenceIcons.shop(shop.iconId()), cx, headerAlign.iconY());
		String balance = loaded(shop) ? FormatUtil.commas(shop.balance()) : "...";
		String balanceLabel = "Balance ";
		int balanceW = PvDraw.widthBold(font, balance);
		int titleX = cx + 16 + ICON_GAP;
		String title = entry.def().name() + " Essence";
		int titleRoom = cx + cw - titleX - balanceW - NAME_BALANCE_GAP;
		boolean showLabel = PvDraw.widthBold(font, title) + font.width(balanceLabel) <= titleRoom;
		if (!showLabel && PvDraw.widthBold(font, title) > titleRoom) {
			while (title.length() > 1 && PvDraw.widthBold(font, title + "...") > titleRoom) {
				title = title.substring(0, title.length() - 1);
			}
			title += "...";
		}
		PvDraw.textBold(g, font, title, titleX, headerAlign.textY(), color);
		PvDraw.textBold(g, font, balance, cx + cw - balanceW, headerAlign.textY(), PvDraw.COLOR_TEXT);
		if (showLabel) {
			PvDraw.textRight(g, font, balanceLabel, cx + cw - balanceW, headerAlign.textY(), PvDraw.COLOR_MUTED);
		}
		PvDraw.fill(g, cx, top + EssenceLayout.DETAIL_HEADER_H + 1, cw, 1, PvDraw.COLOR_DIVIDER);

		EssenceSummary summary = EssenceSummary.of(shop, EssenceShopCosts::costs);
		int statY = top + EssenceLayout.DETAIL_HEADER_H + 4;
		int colW = (cw - 12) / 2;
		int rightX = cx + cw - colW;
		if (summary.loaded()) {
			int maxedColor = summary.complete() ? EssenceShopColumn.MAXED_COLOR : PvDraw.COLOR_TEXT;
			stat(g, font, "Perks maxed", summary.perksMaxed() + "/" + summary.perkCount(), maxedColor, cx, statY, colW);
			stat(g, font, "Perk levels", summary.levels() + "/" + summary.maxLevels(), maxedColor, cx, statY + EssenceLayout.STAT_ROW, colW);
			if (summary.complete()) {
				stat(g, font, "Essence to max", "Maxed", EssenceShopColumn.MAXED_COLOR, rightX, statY, colW);
			} else {
				String toMax = FormatUtil.commas(summary.toMax()) + (summary.unknownCostPerks() > 0 ? "+" : "");
				stat(g, font, "Essence to max", toMax, PvDraw.COLOR_GOLD, rightX, statY, colW);
				int y2 = statY + EssenceLayout.STAT_ROW;
				if (summary.unknownCostPerks() > 0) {
					int n = summary.unknownCostPerks();
					stat(g, font, "Unknown cost", n + (n == 1 ? " perk" : " perks"), PvDraw.COLOR_MUTED, rightX, y2, colW);
				} else {
					long needed = Math.max(0L, summary.toMax() - shop.balance());
					stat(g, font, "Still needed", needed == 0L ? "None" : FormatUtil.commas(needed),
						needed == 0L ? EssenceShopColumn.MAXED_COLOR : PvDraw.COLOR_TEXT, rightX, y2, colW);
				}
			}
		} else {
			stat(g, font, "Perks", "Loading...", PvDraw.COLOR_MUTED, cx, statY, colW);
		}
		this.zones.add(new Zone(new EssenceLayout.Rect(cx, statY, cw, EssenceLayout.STAT_ROW * 2), summaryTip(entry, summary)));

		int barY = statY + EssenceLayout.STAT_ROW * 2 + 4;
		float progress = summary.maxLevels() <= 0 ? 0F : summary.levels() / (float) summary.maxLevels();
		PvDraw.progressBar(g, cx, barY, cw, EssenceLayout.BAR_H, progress, PvDraw.COLOR_BAR_FILL, summary.loaded() && summary.complete());

		int sectionY = barY + EssenceLayout.BAR_H + 6;
		PvDraw.sectionHeader(g, font, "Perks", cx, sectionY, cw);
		drawPerks(g, font, detail, shop, mouseX, mouseY);
	}

	private void drawPerks(GuiGraphicsExtractor g, Font font, EssenceLayout.Rect detail, DungeonSnapshot.EssenceShop shop, int mouseX, int mouseY) {
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		EssenceLayout.Perks layout = EssenceLayout.perks(detail, font.lineHeight, perks.size());
		this.perkLayout = layout;
		EssenceLayout.Rect list = layout.list();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "Loading...", list.x() + list.w() / 2, list.y() + 4, PvDraw.COLOR_MUTED);
			return;
		}
		this.perkScroll = EssenceLayout.clampScroll(this.perkScroll, layout.maxScroll());
		int scroll = this.perkScroll;
		int rowH = layout.rowH();
		boolean mouseInList = list.contains(mouseX, mouseY);

		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		if (layout.cols() > 1) {
			int lineX = list.x() + layout.colW() + EssenceLayout.PERK_COL_GAP / 2;
			PvDraw.fill(g, lineX, list.y(), 1, Math.min(list.h(), layout.contentH() - scroll), PvDraw.COLOR_DIVIDER);
		}
		for (int i = 0; i < perks.size(); i++) {
			int px = list.x() + layout.col(i) * (layout.colW() + EssenceLayout.PERK_COL_GAP);
			int py = list.y() + layout.row(i) * rowH - scroll;
			if (py + rowH <= list.y() || py >= list.bottom()) {
				continue;
			}
			DungeonSnapshot.EssencePerk perk = perks.get(i);
			EssenceLayout.Rect row = new EssenceLayout.Rect(px, py, layout.colW(), rowH);
			if (mouseInList && row.contains(mouseX, mouseY)) {
				PvDraw.fill(g, row.x(), row.y(), row.w(), row.h(), ROW_HOVER);
			}
			PvDraw.IconTextAlign align = PvDraw.IconTextAlign.of(py, rowH, 16, font.lineHeight);
			g.item(EssenceIcons.perk(perk.id()), px + 1, align.iconY());
			String levels = perk.level() + "/" + perk.maxLevel();
			int levelColor = perk.maxed() ? EssenceShopColumn.MAXED_COLOR
				: perk.level() <= 0 ? PvDraw.COLOR_MUTED : PvDraw.COLOR_TEXT;
			int right = px + layout.colW() - 2;
			int nameX = px + 1 + 16 + ICON_GAP;
			String name = trim(font, perk.name(), Math.max(4, right - font.width(levels) - NAME_BALANCE_GAP - nameX));
			PvDraw.text(g, font, name, nameX, align.textY(), perk.level() <= 0 ? PvDraw.COLOR_MUTED : PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, levels, right, align.textY(), levelColor);
			int hitTop = Math.max(py, list.y());
			int hitBottom = Math.min(py + rowH, list.bottom());
			this.zones.add(new Zone(new EssenceLayout.Rect(px, hitTop, layout.colW(), hitBottom - hitTop), EssencePerkTips.tip(perk)));
		}
		g.disableScissor();

		if (layout.maxScroll() > 0) {
			drawScrollCue(g, detail.right() - 3, list.y(), list.h(), scroll, layout.maxScroll());
		}
	}

	private static void stat(GuiGraphicsExtractor g, Font font, String label, String value, int valueColor, int x, int y, int w) {
		int valueW = font.width(value);
		PvDraw.text(g, font, trim(font, label, Math.max(4, w - valueW - 6)), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value, x + w, y, valueColor);
	}

	private static void drawIcon(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
		if (size >= 16) {
			g.item(stack, x, y);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		float s = size / 16F;
		g.pose().scale(s, s);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private static void drawScrollCue(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int maxScroll) {
		PvDraw.fill(g, x, y, 2, h, SCROLL_TRACK);
		int thumbH = Math.max(8, h * h / (h + maxScroll));
		int thumbY = y + Math.round((h - thumbH) * (scroll / (float) maxScroll));
		PvDraw.fill(g, x, thumbY, 2, thumbH, PvDraw.COLOR_ACCENT);
	}

	private static List<PvTooltip.Line> summaryTip(EssenceShops.Entry entry, EssenceSummary summary) {
		DungeonSnapshot.EssenceShop shop = entry.shop();
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(entry.def().name() + " Essence", entry.def().color()));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row("Balance", PvDraw.COLOR_MUTED, FormatUtil.commas(shop.balance()), PvDraw.COLOR_TEXT));
		if (!summary.loaded()) {
			lines.add(PvTooltip.Line.meta("Loading shop..."));
			return lines;
		}
		lines.add(PvTooltip.Line.row(
			"Perks maxed", PvDraw.COLOR_MUTED, summary.perksMaxed() + "/" + summary.perkCount(), PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.row(
			"Perk levels", PvDraw.COLOR_MUTED, summary.levels() + "/" + summary.maxLevels(), PvDraw.COLOR_TEXT));
		if (summary.complete()) {
			lines.add(PvTooltip.Line.of("All perks maxed", EssenceShopColumn.MAXED_COLOR));
			return lines;
		}
		String toMax = FormatUtil.commas(summary.toMax());
		if (summary.unknownCostPerks() > 0) {
			toMax += "+";
		}
		lines.add(PvTooltip.Line.row("Essence to max", PvDraw.COLOR_MUTED, toMax, PvDraw.COLOR_GOLD));
		if (summary.unknownCostPerks() == 0) {
			long needed = Math.max(0L, summary.toMax() - shop.balance());
			lines.add(needed == 0L
				? PvTooltip.Line.of("Enough to max", EssenceShopColumn.MAXED_COLOR)
				: PvTooltip.Line.row("Still needed", PvDraw.COLOR_MUTED, FormatUtil.commas(needed), PvDraw.COLOR_TEXT));
		} else {
			lines.add(PvTooltip.Line.meta(summary.unknownCostPerks() == 1
				? "1 perk has an unknown cost, not counted"
				: summary.unknownCostPerks() + " perks have unknown costs, not counted"));
		}
		return lines;
	}

	private static boolean loaded(DungeonSnapshot.EssenceShop shop) {
		return !shop.perks().isEmpty();
	}

	private static String balanceShort(DungeonSnapshot.EssenceShop shop) {
		return loaded(shop) ? EssenceShopColumn.balance(shop.balance()) : "...";
	}

	private static List<Integer> groupSizes() {
		List<Integer> sizes = new ArrayList<>();
		for (EssenceShops.Group group : EssenceShops.Group.values()) {
			sizes.add((int) EssenceShops.ALL.stream().filter(def -> def.group() == group).count());
		}
		return List.copyOf(sizes);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = Math.max(0, maxW - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private record Zone(EssenceLayout.Rect rect, List<PvTooltip.Line> lines) {
	}

	private record RowHit(int index, EssenceLayout.Rect rect) {
	}
}

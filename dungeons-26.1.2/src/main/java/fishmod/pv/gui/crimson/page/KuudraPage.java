/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page;

import static fishmod.pv.gui.crimson.CrimsonUi.DISABLED;
import static fishmod.pv.gui.crimson.CrimsonUi.ENABLED;
import static fishmod.pv.gui.crimson.CrimsonUi.GAP;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.crimson.CrimsonUi.KUUDRA_COLOR;
import static fishmod.pv.gui.crimson.CrimsonUi.PAD;
import static fishmod.pv.gui.crimson.CrimsonUi.SEP_GAP;
import static fishmod.pv.gui.crimson.CrimsonUi.SLOT;
import static fishmod.pv.gui.crimson.CrimsonUi.SLOT_GAP;
import static fishmod.pv.gui.crimson.CrimsonUi.STAT_ROW;
import static fishmod.pv.gui.crimson.CrimsonUi.drawHover;
import static fishmod.pv.gui.crimson.CrimsonUi.sectionSeparator;
import static fishmod.pv.gui.crimson.CrimsonUi.statLine;
import static fishmod.pv.gui.crimson.CrimsonUi.stripFormatting;
import static fishmod.pv.gui.crimson.CrimsonUi.trim;
import static fishmod.pv.gui.crimson.CrimsonUi.visible;

import fishmod.pv.data.CrimsonKuudraCard;
import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson Kuudra subtab: tier stats + scrollable Kuudra card. */
public final class KuudraPage {
	private final List<HoverZone> zones = new ArrayList<>();

	private int kuudraScroll;
	private int kuudraMaxScroll;
	private int kuudraX;
	private int kuudraY;
	private int kuudraW;
	private int kuudraH;

	private final KuudraChecklistColumn itemsColumn = new KuudraChecklistColumn();
	private final KuudraChecklistColumn armourColumn = new KuudraChecklistColumn();
	private boolean showingChecklist;
	private int buttonX;
	private int buttonY;
	private int buttonW;
	private int buttonH;

	public void resetScroll() {
		this.kuudraScroll = 0;
		this.itemsColumn.resetScroll();
		this.armourColumn.resetScroll();
	}

	public boolean mouseClicked(double mouseX, double mouseY) {
		if (this.buttonW <= 0) {
			return false;
		}
		if (mouseX >= this.buttonX && mouseX < this.buttonX + this.buttonW
			&& mouseY >= this.buttonY && mouseY < this.buttonY + this.buttonH) {
			this.showingChecklist = !this.showingChecklist;
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.showingChecklist) {
			return this.itemsColumn.mouseScrolled(mouseX, mouseY, scrollY)
				|| this.armourColumn.mouseScrolled(mouseX, mouseY, scrollY);
		}
		if (this.kuudraMaxScroll > 0
			&& mouseX >= this.kuudraX && mouseX < this.kuudraX + this.kuudraW
			&& mouseY >= this.kuudraY && mouseY < this.kuudraY + this.kuudraH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.kuudraMaxScroll, this.kuudraScroll + delta));
			if (next != this.kuudraScroll) {
				this.kuudraScroll = next;
				return true;
			}
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		if (this.showingChecklist) {
			drawChecklist(g, font, snapshot.kuudraCard(), x, y, w, h, mouseX, mouseY);
			return;
		}
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawKuudraTiers(g, font, snapshot, x, y, leftW, h, mouseX, mouseY);
		drawKuudraCard(g, font, snapshot, x + leftW + GAP, y, rightW, h, mouseX, mouseY);
	}

	private void drawChecklist(
		GuiGraphicsExtractor g, Font font, CrimsonKuudraCard card, int x, int y, int w, int h, int mx, int my
	) {
		int headerH = 22;
		PvDraw.innerPanel(g, x, y, w, headerH);
		String title = "Kuudra Gear Checklist";
		int textY = y + (headerH - font.lineHeight) / 2 + 1;
		PvDraw.text(g, font, title, x + PAD, textY, KUUDRA_COLOR);
		String back = "\u2190 Back to Overview";
		int bw = font.width(back) + 14;
		int bx = x + w - PAD - bw;
		drawViewButton(g, font, back, bx, y + 3, bw, headerH - 6, mx, my);
		int legendX = x + PAD + font.width(title) + LEGEND_GAP;
		for (LegendPart part : headerLegend(bx - LEGEND_GAP - legendX, font::width)) {
			PvDraw.text(g, font, part.text(), legendX, textY, part.color());
			legendX += font.width(part.text());
		}
		int top = y + headerH + GAP;
		int colH = h - headerH - GAP;
		int colW = (w - GAP) / 2;
		this.itemsColumn.render(g, font, x, top, colW, colH, "Important Items", card.gear().items(), mx, my, this.zones);
		this.armourColumn.render(g, font, x + colW + GAP, top, w - colW - GAP, colH, "Armour", card.gear().armour(),
			mx, my, this.zones);
	}

	private static final int LEGEND_GAP = 12;

	record LegendPart(String text, int color) {
	}

	private static final List<List<LegendPart>> LEGENDS = List.of(
		legend("\u2714 Good", "! Upgrade", "\u2718 Missing", "   "),
		legend("\u2714 Good", "! Upgrade", "\u2718 Missing", "  "),
		legend("\u2714 OK", "! Upg", "\u2718 Miss", " "),
		legend("\u2714", "!", "\u2718", " ")
	);

	private static List<LegendPart> legend(String good, String upgrade, String missing, String gap) {
		return List.of(
			new LegendPart(good, KuudraChecklistColumn.COLOR_OK), new LegendPart(gap, 0),
			new LegendPart(upgrade, KuudraChecklistColumn.COLOR_UPGRADE), new LegendPart(gap, 0),
			new LegendPart(missing, KuudraChecklistColumn.COLOR_MISSING));
	}

	/** Widest legend that fits beside the title, shortening labels instead of clipping; empty if nothing fits. */
	static List<LegendPart> headerLegend(int available, ToIntFunction<String> width) {
		for (List<LegendPart> parts : LEGENDS) {
			int total = 0;
			for (LegendPart part : parts) {
				total += width.applyAsInt(part.text());
			}
			if (total <= available) {
				return parts;
			}
		}
		return List.of();
	}

	private void drawViewButton(
		GuiGraphicsExtractor g, Font font, String label, int bx, int by, int bw, int bh, int mx, int my
	) {
		this.buttonX = bx;
		this.buttonY = by;
		this.buttonW = bw;
		this.buttonH = bh;
		boolean hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
		PvDraw.roundedFill(g, bx, by, bw, bh, 3, hovered ? 0x40FF5555 : 0x26FF5555);
		PvDraw.roundedRing(g, bx, by, bw, bh, 3, hovered ? KUUDRA_COLOR : 0xFF8A3A3A, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, label, bx + bw / 2, by + (bh - font.lineHeight) / 2 + 1, PvDraw.COLOR_WHITE);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, this.zones, mouseX, mouseY, screenW, screenH);
	}

	private void drawKuudraTiers(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int buttonH = font.lineHeight + 9;
		int buttonY = y + h - PAD - buttonH;
		drawViewButton(g, font, "Gear Checklist \u2192", lx, buttonY, lw, buttonH, mx, my);
		int tiersBottom = buttonY - 4;

		PvDraw.text(g, font, "Kuudra", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		int tierGap = tierGap(tiersBottom - ly, CrimsonSnapshot.KuudraTier.values().length);
		for (CrimsonSnapshot.KuudraTier tier : CrimsonSnapshot.KuudraTier.values()) {
			if (ly + STAT_ROW * 2 > tiersBottom) {
				break;
			}
			CrimsonSnapshot.KuudraTierStats stats = snapshot.kuudra(tier);
			PvDraw.text(g, font, tier.label() + ":", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, FormatUtil.commas(stats.completions()),
				lx + lw, ly, KUUDRA_COLOR);
			ly += STAT_ROW;
			PvDraw.text(g, font, "Highest wave:", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font,
				stats.highestWave() > 0 ? String.valueOf(stats.highestWave()) : "-",
				lx + lw, ly, PvDraw.COLOR_TEXT);
			ly += STAT_ROW + tierGap;
		}
	}

	/** Gap between tier blocks, shrinking so every tier still fits above the checklist button. */
	static int tierGap(int available, int tiers) {
		return Math.max(0, Math.min(6, available / Math.max(1, tiers) - STAT_ROW * 2));
	}

	private void drawKuudraCard(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		CrimsonKuudraCard card = snapshot.kuudraCard();
		int lx = x + PAD;
		int contentTop = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		int contentH = measureKuudraCard(font, card);
		this.kuudraX = x;
		this.kuudraY = contentTop;
		this.kuudraW = w;
		this.kuudraH = Math.max(0, bottom - contentTop);
		this.kuudraMaxScroll = Math.max(0, contentH - this.kuudraH);
		this.kuudraScroll = Math.min(this.kuudraScroll, this.kuudraMaxScroll);

		g.enableScissor(lx, this.kuudraY, lx + lw + 3, this.kuudraY + this.kuudraH);
		int ly = contentTop - this.kuudraScroll;
		drawKuudraCardBody(g, font, card, lx, ly, lw, mx, my, this.kuudraY, this.kuudraY + this.kuudraH);
		g.disableScissor();
	}

	private int measureKuudraCard(Font font, CrimsonKuudraCard card) {
		int ly = 0;
		ly += font.lineHeight + 2;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += card.importantItems().size() * STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += SLOT + 4;
		return ly;
	}

	private void drawKuudraCardBody(
		GuiGraphicsExtractor g, Font font, CrimsonKuudraCard card,
		int lx, int ly, int lw, int mx, int my, int clipTop, int clipBottom
	) {
		String scoreText = FormatUtil.shortXp(card.kuudraScore())
			+ " [Level: " + FormatUtil.commas(card.kuudraLevel()) + "]";
		PvDraw.text(g, font, "Kuudra Score:", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, scoreText, lx + lw, ly, KUUDRA_COLOR);
		if (visible(ly, font.lineHeight, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			for (String line : card.scoreHoverLines()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(ly, clipTop), lw,
				Math.min(ly + font.lineHeight, clipBottom) - Math.max(ly, clipTop), tip));
		}
		ly += font.lineHeight + 2;

		String mp = FormatUtil.commas(card.magicalPower())
			+ " [" + card.selectedPowerLabel() + "]";
		ly = statLine(g, font, "Magical Power", trim(font, mp, lw - font.width("Magical Power") - 8),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		String skills = "Cata " + card.cataLevel()
			+ ", Combat " + card.combatLevel()
			+ ", Foraging " + card.foragingLevel()
			+ ", SB " + card.skyBlockLevel();
		ly = statLine(g, font, "Skills", trim(font, skills, lw - font.width("Skills") - 8),
			lx, ly, lw, PvDraw.COLOR_TEXT);

		String vanq = String.format(Locale.ROOT, "%.3f%%", card.vanquisherChancePct());
		int vanqY = ly;
		ly = statLine(g, font, "Vanquisher Chance", vanq, lx, ly, lw, ENABLED);
		if (visible(vanqY, STAT_ROW, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Vanquisher Chance", PvDraw.COLOR_TEXT));
			for (String line : card.vanquisherHover()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(vanqY, clipTop), lw,
				Math.min(vanqY + STAT_ROW, clipBottom) - Math.max(vanqY, clipTop), tip));
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		PvDraw.text(g, font, "Important Items", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;

		for (CrimsonKuudraCard.ImportantItem item : card.importantItems()) {
			if (visible(ly, STAT_ROW, clipTop, clipBottom)) {
				int color = item.owned() ? ENABLED : DISABLED;
				String mark = item.owned() ? "✔ " : "✘ ";
				String detail = item.details().isEmpty() ? "" : " [" + String.join(" · ", item.details()) + "]";
				String line = mark + item.label() + detail;
				PvDraw.text(g, font, trim(font, line, lw), lx, ly, color);
				addLoreHoverZone(lx, Math.max(ly, clipTop), lw,
					Math.min(ly + STAT_ROW, clipBottom) - Math.max(ly, clipTop),
					item.label(), item.displayName(), item.owned(), item.details(), item.lore());
			}
			ly += STAT_ROW;
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		int colGap = 10;
		int colW = Math.max(SLOT, (lw - colGap) / 2);
		int rightColX = lx + colW + colGap;
		PvDraw.text(g, font, "Mage Armor", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, "Archer Armor", rightColX, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		drawArmorRow(g, font, card.mageArmor(), lx, ly, mx, my, clipTop, clipBottom);
		drawArmorRow(g, font, card.archerArmor(), rightColX, ly, mx, my, clipTop, clipBottom);
	}

	private int drawArmorRow(
		GuiGraphicsExtractor g, Font font, List<CrimsonKuudraCard.ArmorPiece> pieces,
		int lx, int ly, int mx, int my, int clipTop, int clipBottom
	) {
		int i = 0;
		for (CrimsonKuudraCard.ArmorPiece piece : pieces) {
			int bx = lx + i * (SLOT + SLOT_GAP);
			int by = ly;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= clipTop && my < clipBottom;
			if (visible(by, SLOT, clipTop, clipBottom)) {
				PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
				g.outline(bx, by, SLOT, SLOT,
					hovered ? PvDraw.COLOR_ACCENT : (piece.owned() ? ITEM_SLOT_BORDER : DISABLED));
				ItemStack icon = armorIconStack(piece);
				int iconPad = Math.max(0, (SLOT - 16) / 2);
				if (!icon.isEmpty()) {
					g.item(icon, bx + iconPad, by + iconPad);
					if (!piece.owned()) {
						PvDraw.fill(g, bx + 1, by + 1, SLOT - 2, SLOT - 2, 0x88000000);
					}
				} else {
					g.item(new ItemStack(fishmod.compat.CI.GRAY_DYE), bx + iconPad, by + iconPad);
				}
				if (piece.owned() && piece.dyeColor() != null) {
					PvDraw.fill(g, bx + SLOT - 4, by + 1, 3, 3, 0xFF000000 | (piece.dyeColor() & 0xFFFFFF));
				}
				int y0 = Math.max(by, clipTop);
				int y1 = Math.min(by + SLOT, clipBottom);
				if (y1 > y0) {
					addLoreHoverZone(bx, y0, SLOT, y1 - y0,
						piece.label().isBlank() ? piece.slot() : piece.label(),
						piece.displayName(),
						piece.owned(), piece.details(), piece.lore());
				}
			}
			i++;
		}
		return ly + SLOT + 4;
	}

	private static ItemStack armorIconStack(CrimsonKuudraCard.ArmorPiece piece) {
		ItemStack icon = SkyBlockItemFactory.iconStack(piece.iconId());
		if (icon.isEmpty()) {
			return icon;
		}
		ItemStack copy = icon.copy();
		if (piece.dyeColor() != null) {
			var item = copy.getItem();
			if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
				|| item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS
				|| item == Items.LEATHER_HORSE_ARMOR) {
				copy.set(DataComponents.DYED_COLOR,
					new net.minecraft.world.item.component.DyedItemColor(piece.dyeColor() & 0xFFFFFF));
			}
		}
		return copy;
	}

	/** Full item lore (when present) via legacy-formatted components, else a plain fallback tip. */
	private void addLoreHoverZone(
		int x, int y, int w, int h,
		String label, String displayName, boolean owned, List<String> details, List<String> lore
	) {
		if (lore != null && !lore.isEmpty()) {
			String title = displayName != null && !displayName.isBlank() ? displayName : label;
			this.zones.add(HoverZone.ofComponents(x, y, w, h, loreComponents(title, lore)));
			return;
		}
		List<PvTooltip.Line> tip = new ArrayList<>();
		String title = displayName != null && !displayName.isBlank() ? stripFormatting(displayName) : label;
		tip.add(PvTooltip.Line.of(title == null ? "" : title, PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of(owned ? "Owned" : "Missing", owned ? ENABLED : DISABLED));
		for (String d : details) {
			tip.add(PvTooltip.Line.of(d, PvDraw.COLOR_MUTED));
		}
		this.zones.add(HoverZone.of(x, y, w, h, tip));
	}

	static List<Component> loreComponents(String title, List<String> lore) {
		List<Component> tip = new ArrayList<>();
		tip.add(SkyBlockItemFactory.legacyLine(title == null ? "" : title));
		String titlePlain = stripFormatting(title);
		int start = 0;
		if (!lore.isEmpty()) {
			String firstPlain = stripFormatting(lore.get(0));
			if (!titlePlain.isBlank() && !firstPlain.isBlank()
				&& (firstPlain.equalsIgnoreCase(titlePlain)
				|| titlePlain.regionMatches(true, 0, firstPlain, 0, Math.min(titlePlain.length(), firstPlain.length()))
				|| firstPlain.regionMatches(true, 0, titlePlain, 0, Math.min(titlePlain.length(), firstPlain.length())))) {
				start = 1;
			}
		}
		for (int i = start; i < lore.size(); i++) {
			String loreLine = lore.get(i);
			tip.add(loreLine == null || loreLine.isBlank()
				? Component.empty()
				: SkyBlockItemFactory.legacyLine(loreLine));
		}
		return tip;
	}
}

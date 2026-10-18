/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.networth.ItemWorth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Click-to-open item coin breakdown for the Inventories tab. */
public final class ItemValueOverlay {
	private static final int PAD = 8;
	private static final int CLOSE_SIZE = 12;

	/** Strong green for estimated value / total labels. */
	private static final int COLOR_TOTAL = 0xFF55FF55;
	/** Consistent coin prices. */
	private static final int COLOR_PRICE = PvDraw.COLOR_GOLD;
	/** Upgrade rows and upgrade section header. */
	private static final int COLOR_UPGRADE = 0xFFFFAA55;
	/** Gemstone section + rows. */
	private static final int COLOR_GEM = 0xFF55FFFF;
	/** Enchantment section + rows. */
	private static final int COLOR_ENCHANT = 0xFFC88CFF;
	/** Unlock / apply / slot cost style rows. */
	private static final int COLOR_COST = 0xFFFF7744;
	/** Soft structural labels. */
	private static final int COLOR_LABEL = PvDraw.COLOR_TEXT;
	private static final int COLOR_MUTED = PvDraw.COLOR_MUTED;

	private boolean open;
	private ItemWorth.Breakdown breakdown;
	private String title = "Item value";
	private String totalLabel = "Estimated value";
	private ItemStack icon = ItemStack.EMPTY;
	private String skyblockId = "";
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeX;
	private int closeY;
	private int scroll;
	private int maxScroll;

	public boolean isOpen() {
		return this.open;
	}

	public void open(String title, ItemStack icon, ItemWorth.Breakdown breakdown) {
		open(title, icon, null, breakdown);
	}

	public void open(String title, ItemStack icon, String skyblockId, ItemWorth.Breakdown breakdown) {
		open(title, icon, skyblockId, breakdown, "Estimated value");
	}

	public void open(String title, ItemStack icon, String skyblockId, ItemWorth.Breakdown breakdown, String totalLabel) {
		this.totalLabel = totalLabel == null || totalLabel.isBlank() ? "Estimated value" : totalLabel;
		this.title = title == null || title.isBlank() ? "Item value" : title;
		this.icon = icon == null ? ItemStack.EMPTY : icon;
		this.skyblockId = skyblockId == null ? "" : skyblockId;
		this.breakdown = breakdown == null ? ItemWorth.Breakdown.empty() : breakdown;
		this.open = true;
		this.scroll = 0;
	}

	public void close() {
		this.open = false;
		this.breakdown = null;
		this.icon = ItemStack.EMPTY;
		this.skyblockId = "";
		this.scroll = 0;
		this.maxScroll = 0;
	}

	public boolean mouseScrolled(double delta) {
		if (!this.open || this.maxScroll <= 0) {
			return false;
		}
		int before = this.scroll;
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.round(delta * 12)));
		return this.scroll != before;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int screenW,
		int screenH,
		int mouseX,
		int mouseY
	) {
		if (!this.open || this.breakdown == null) {
			return;
		}

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000);

		List<Row> rows = buildRows(this.breakdown, this.totalLabel, font);
		int contentH = PAD + 16 + 6;
		for (Row row : rows) {
			contentH += row.height;
		}
		contentH += PAD;

		this.panelW = Math.min(300, Math.max(210, screenW / 3));
		this.panelH = Math.min(Math.max(contentH, 80), screenH - 40);
		this.panelX = (screenW - this.panelW) / 2;
		this.panelY = (screenH - this.panelH) / 2;
		this.maxScroll = Math.max(0, contentH - this.panelH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		PvDraw.overlayPanel(g, this.panelX, this.panelY, this.panelW, this.panelH);

		int titleX = this.panelX + PAD;
		if (!this.icon.isEmpty() || (this.skyblockId != null && !this.skyblockId.isBlank())) {
			// Pass skyblock id so Hypixel models / official PNGs match the inventory slot.
			SkyBlockIconRenderer.draw(g, this.icon, this.skyblockId, titleX, this.panelY + PAD, 16);
			titleX += 20;
		}
		PvDraw.textBold(g, font, trim(font, this.title, this.panelW - PAD * 2 - CLOSE_SIZE - 24),
			titleX, this.panelY + PAD + (16 - font.lineHeight) / 2, titleColor(this.breakdown));

		this.closeX = this.panelX + this.panelW - PAD - CLOSE_SIZE;
		this.closeY = this.panelY + PAD;
		boolean closeHover = mouseX >= this.closeX && mouseX < this.closeX + CLOSE_SIZE
			&& mouseY >= this.closeY && mouseY < this.closeY + CLOSE_SIZE;
		PvDraw.roundedFill(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, 3, closeHover ? 0xFF3A3A4A : 0x24FFFFFF);
		PvDraw.roundedRing(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, "x", this.closeX + CLOSE_SIZE / 2, this.closeY + 1, COLOR_MUTED);

		int bodyTop = this.panelY + PAD + 16 + 6;
		int bodyBottom = this.panelY + this.panelH - PAD;
		g.enableScissor(this.panelX + 1, bodyTop, this.panelX + this.panelW - 1, bodyBottom);
		int y = bodyTop - this.scroll;
		int innerW = this.panelW - PAD * 2;
		for (Row row : rows) {
			if (y + row.height > bodyTop && y < bodyBottom) {
				drawRow(g, font, row, this.panelX + PAD, y, innerW);
			}
			y += row.height;
		}
		g.disableScissor();
	}

	/** @return true if the click was consumed. */
	public boolean mouseClicked(double mx, double my) {
		if (!this.open) {
			return false;
		}
		if (mx >= this.closeX && mx < this.closeX + CLOSE_SIZE
			&& my >= this.closeY && my < this.closeY + CLOSE_SIZE) {
			close();
			return true;
		}
		if (mx >= this.panelX && mx < this.panelX + this.panelW
			&& my >= this.panelY && my < this.panelY + this.panelH) {
			return true;
		}
		close();
		return true;
	}

	private static List<Row> buildRows(ItemWorth.Breakdown breakdown, String totalLabel, Font font) {
		List<Row> rows = new ArrayList<>();
		int lineH = font.lineHeight + 2;

		if (breakdown.parts().isEmpty()) {
			rows.add(Row.text("No priced components", COLOR_MUTED, lineH));
			return rows;
		}

		rows.add(Row.total(
			totalLabel,
			FormatUtil.shortCoins(Math.round(breakdown.total())) + " coins",
			headerColor(breakdown),
			lineH + 4
		));
		rows.add(Row.spacer(4));
		rows.add(Row.divider(6));

		Map<ItemWorth.Section, List<ItemWorth.Part>> bySection = new EnumMap<>(ItemWorth.Section.class);
		for (ItemWorth.Part part : breakdown.parts()) {
			bySection.computeIfAbsent(part.section(), s -> new ArrayList<>()).add(part);
		}

		boolean firstSection = true;
		for (ItemWorth.Section section : ItemWorth.Section.values()) {
			List<ItemWorth.Part> parts = bySection.get(section);
			if (parts == null || parts.isEmpty()) {
				continue;
			}
			double sectionTotal = 0;
			for (ItemWorth.Part part : parts) {
				if (part.role() != ItemWorth.Role.NOTE) {
					sectionTotal += part.amount();
				}
			}

			if (!firstSection) {
				rows.add(Row.spacer(6));
			}
			firstSection = false;

			sortSectionParts(section, parts);

			String sectionPrice = sectionTotal > 0
				? FormatUtil.shortCoins(Math.round(sectionTotal))
				: "";
			rows.add(Row.section(section.title(), sectionPrice, sectionHeaderColor(section), lineH + 2));

			for (ItemWorth.Part part : parts) {
				rows.add(rowForPart(part, lineH));
			}
		}
		return rows;
	}

	private static int headerColor(ItemWorth.Breakdown breakdown) {
		if (breakdown == null) {
			return COLOR_TOTAL;
		}
		for (ItemWorth.Part part : breakdown.parts()) {
			if (part.role() == ItemWorth.Role.ITEM && part.accentColor() != 0) {
				return part.accentColor();
			}
		}
		return COLOR_TOTAL;
	}

	private static int titleColor(ItemWorth.Breakdown breakdown) {
		return headerColor(breakdown);
	}

	/** Enchantments: highest value first. Everything else: stable logical order. */
	private static void sortSectionParts(ItemWorth.Section section, List<ItemWorth.Part> parts) {
		if (section == ItemWorth.Section.ENCHANTMENTS) {
			parts.sort((a, b) -> {
				boolean aNote = a.role() == ItemWorth.Role.NOTE;
				boolean bNote = b.role() == ItemWorth.Role.NOTE;
				if (aNote != bNote) {
					return aNote ? 1 : -1;
				}
				return Double.compare(b.amount(), a.amount());
			});
			return;
		}
		if (section == ItemWorth.Section.UPGRADES) {
			parts.sort((a, b) -> Integer.compare(upgradeRank(a.label()), upgradeRank(b.label())));
		}
	}

	private static int upgradeRank(String label) {
		if (label == null || label.isBlank()) {
			return 900;
		}
		return switch (label) {
			case "HPB's" -> 0;
			case "Fuming" -> 1;
			case "Recombobulator" -> 2;
			case "Art of War" -> 3;
			case "Art of Peace" -> 4;
			case "Stars" -> 5;
			case "Master Stars" -> 6;
			case "Reforge" -> 7;
			case "Etherwarp" -> 8;
			case "Transmission Tuner" -> 9;
			case "Wood Singularity" -> 10;
			case "Divan Powder Coating" -> 11;
			case "Farming for Dummies" -> 12;
			case "Jalapeno Book" -> 13;
			case "Mana Disintegrator" -> 14;
			case "Polarvoid Book" -> 15;
			default -> 800;
		};
	}

	private static int sectionHeaderColor(ItemWorth.Section section) {
		return switch (section) {
			case UPGRADES -> COLOR_UPGRADE;
			case GEMSTONES -> COLOR_GEM;
			case ENCHANTMENTS -> COLOR_ENCHANT;
			case CONTENTS, OTHER -> COLOR_MUTED;
			case BASE -> COLOR_MUTED;
		};
	}

	private static int rowLabelColor(ItemWorth.Part part) {
		if (part.role() == ItemWorth.Role.NOTE) {
			return COLOR_MUTED;
		}
		if (part.role() == ItemWorth.Role.ITEM && part.accentColor() != 0) {
			return part.accentColor();
		}
		if (isCostLike(part)) {
			return COLOR_COST;
		}
		return switch (part.section()) {
			case UPGRADES -> COLOR_UPGRADE;
			case GEMSTONES -> COLOR_GEM;
			case ENCHANTMENTS -> COLOR_ENCHANT;
			case CONTENTS, OTHER -> COLOR_LABEL;
			case BASE -> COLOR_LABEL;
		};
	}

	private static boolean isCostLike(ItemWorth.Part part) {
		String blob = (part.label() + " " + part.detail()).toLowerCase(Locale.ROOT);
		return blob.contains("cost") || blob.contains("unlock") || blob.contains("apply");
	}

	private static Row rowForPart(ItemWorth.Part part, int lineH) {
		if (part.role() == ItemWorth.Role.NOTE || part.amount() <= 0) {
			return Row.note("  " + part.label(), lineH);
		}
		String price = FormatUtil.shortCoins(Math.round(part.amount()));
		int color = rowLabelColor(part);
		if (part.role() == ItemWorth.Role.ITEM) {
			return Row.item(part.label(), price, color, lineH);
		}
		if (part.role() == ItemWorth.Role.UPGRADE) {
			if (part.detail() != null && !part.detail().isBlank()) {
				// "Reforge" soft + stone/progress warm; keeps upgrade rows scannable.
				return Row.upgradeSplit(part.label(), part.detail(), price, COLOR_LABEL, color, lineH);
			}
			return Row.upgrade(part.label(), price, color, lineH);
		}
		String left = formatDetailLabel(part);
		return Row.detail("  " + left, price, color, lineH);
	}

	private static String formatDetailLabel(ItemWorth.Part part) {
		if (part.detail() == null || part.detail().isBlank()) {
			return part.label();
		}
		return part.label() + ": " + part.detail();
	}

	private static void drawRow(GuiGraphicsExtractor g, Font font, Row row, int x, int y, int w) {
		int textY = y + Math.max(0, (row.height - font.lineHeight) / 2);
		switch (row.kind) {
			case SPACER -> {
			}
			case DIVIDER -> PvDraw.fill(g, x, y + row.height / 2, w, 1, PvDraw.COLOR_DIVIDER);
			case SECTION -> {
				int ruleY = textY + font.lineHeight / 2;
				int labelW = font.width(row.left);
				int valueW = row.right.isEmpty() ? 0 : font.width(row.right);
				int gap = 6;
				int ruleStart = x + labelW + gap;
				int ruleEnd = x + w - (valueW > 0 ? valueW + gap : 0);
				PvDraw.text(g, font, row.left, x, textY, row.leftColor);
				if (ruleEnd > ruleStart) {
					PvDraw.fill(g, ruleStart, ruleY, ruleEnd - ruleStart, 1, PvDraw.COLOR_DIVIDER);
				}
				if (!row.right.isEmpty()) {
					PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE);
				}
			}
			case TOTAL -> {
				PvDraw.textBold(g, font, row.left, x, textY, row.leftColor);
				PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE);
			}
			case NOTE -> PvDraw.text(g, font, trim(font, row.left, w), x, textY, COLOR_MUTED);
			case UPGRADE_SPLIT -> {
				int valueW = font.width(row.right);
				int maxLeft = Math.max(40, w - valueW - 8);
				String prefix = row.left;
				String detail = row.detail;
				int prefixW = font.width(prefix + " ");
				PvDraw.text(g, font, prefix, x, textY, row.leftColor);
				String shownDetail = trim(font, detail, Math.max(8, maxLeft - prefixW));
				PvDraw.text(g, font, shownDetail, x + prefixW, textY, row.detailColor);
				PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE);
			}
			case ITEM, UPGRADE, DETAIL -> {
				int valueW = font.width(row.right);
				PvDraw.text(g, font, trim(font, row.left, Math.max(40, w - valueW - 8)), x, textY, row.leftColor);
				PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE);
			}
		}
	}

	private static String trim(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private enum Kind {
		TOTAL, SECTION, ITEM, UPGRADE, UPGRADE_SPLIT, DETAIL, NOTE, SPACER, DIVIDER
	}

	private record Row(
		Kind kind,
		String left,
		String detail,
		String right,
		int leftColor,
		int detailColor,
		int height
	) {
		static Row total(String left, String right, int color, int height) {
			return new Row(Kind.TOTAL, left, "", right, color, 0, height);
		}

		static Row section(String left, String right, int color, int height) {
			return new Row(Kind.SECTION, left, "", right, color, 0, height);
		}

		static Row item(String left, String right, int color, int height) {
			return new Row(Kind.ITEM, left, "", right, color, 0, height);
		}

		static Row upgrade(String left, String right, int color, int height) {
			return new Row(Kind.UPGRADE, left, "", right, color, 0, height);
		}

		static Row upgradeSplit(
			String label,
			String detail,
			String right,
			int labelColor,
			int detailColor,
			int height
		) {
			return new Row(Kind.UPGRADE_SPLIT, label, detail, right, labelColor, detailColor, height);
		}

		static Row detail(String left, String right, int color, int height) {
			return new Row(Kind.DETAIL, left, "", right, color, 0, height);
		}

		static Row note(String left, int height) {
			return new Row(Kind.NOTE, left, "", "", COLOR_MUTED, 0, height);
		}

		static Row text(String left, int color, int height) {
			return new Row(Kind.NOTE, left, "", "", color, 0, height);
		}

		static Row spacer(int height) {
			return new Row(Kind.SPACER, "", "", "", 0, 0, height);
		}

		static Row divider(int height) {
			return new Row(Kind.DIVIDER, "", "", "", 0, 0, height);
		}
	}
}

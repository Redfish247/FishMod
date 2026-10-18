/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.pv.data.ColeWeight;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Shared mining GUI helpers and constants. */
public final class MiningUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int BAR_LABEL_GAP = 2;
	public static final int BAR_AFTER = 4;
	public static final int STAT_ROW = 12;
	public static final int SEP_GAP = 10;
	public static final int BAR_HOTM = 0xFF5B8CFF;
	public static final int BAR_MINING = 0xFFAAAAAA;
	public static final int BAR_MITHRIL = 0xFF55AA55;
	public static final int BAR_GEM = 0xFFCC55CC;
	public static final int BAR_GLACITE = 0xFF55CCCC;
	public static final int PLACED = 0xFF55FF55;
	public static final int FOUND = 0xFFE8C84A;
	public static final int MISSING = 0xFF9A9AAC;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;

	private MiningUi() {
	}

	public static void drawHover(
		GuiGraphicsExtractor g, Font font, List<HoverZone> zones, MiningSnapshot snapshot,
		int mx, int my, int screenW, int screenH
	) {
		for (HoverZone zone : zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				List<PvTooltip.Line> tip = zone.coleWeight
					? coleWeightHover(snapshot.coleWeight(), leftShiftDown())
					: zone.lines;
				PvTooltip.drawStyled(g, font, tip, mx, my, screenW, screenH);
				return;
			}
		}
	}

	public static int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, int mx, int my, List<HoverZone> zones
	) {
		List<PvTooltip.Line> lines = fishmod.pv.data.Leveling.hoverTextLines(hover);
		return drawBar(g, font, label, value, fill, maxed, color, lines, x, y, w, mx, my, zones);
	}

	public static int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, List<PvTooltip.Line> hover, int x, int y, int w, int mx, int my, List<HoverZone> zones
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isEmpty()) {
			zones.add(HoverZone.of(x, y, w, bottom - y, hover));
		}
		return bottom;
	}

	public static int barRowH(Font font) {
		return font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER;
	}

	public static int coloredLabelStat(
		GuiGraphicsExtractor g, Font font, String label, String value,
		int x, int y, int w, int labelColor, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, labelColor);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	public static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		return coloredLabelStat(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor);
	}

	public static void wrapText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
		String t = text == null || text.isBlank() ? "-" : text;
		if (font.width(t) <= w) {
			PvDraw.text(g, font, t, x, y, color);
			return;
		}
		PvDraw.text(g, font, trim(font, t, w), x, y, color);
	}

	/**
	 * Horizontal rule centered between previous and next text.
	 * {@code y} is the cursor after a STAT_ROW-based line; corrects for unused row slack.
	 */
	public static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int visualBottom = y - Math.max(0, STAT_ROW - font.lineHeight);
		int pad = SEP_GAP / 2;
		int lineY = visualBottom + pad;
		int lineInset = PAD + 6;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, PvDraw.COLOR_DIVIDER);
		}
		return lineY + 1 + pad;
	}

	/** Header + three powder totals (no bars). Returns bottom Y. */
	public static int drawPowdersBlock(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, int x, int y, int w, List<HoverZone> zones
	) {
		PvDraw.text(g, font, "Powders", x, y, PvDraw.COLOR_MUTED);
		int ly = y + font.lineHeight + 3;
		ly = coloredLabelStat(g, font, "Mithril", FormatUtil.shortXp(snapshot.mithril().total()),
			x, ly, w, BAR_MITHRIL, PvDraw.COLOR_TEXT) + 1;
		zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Mithril", snapshot.mithril())));
		ly = coloredLabelStat(g, font, "Gemstone", FormatUtil.shortXp(snapshot.gemstone().total()),
			x, ly, w, BAR_GEM, PvDraw.COLOR_TEXT) + 1;
		zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Gemstone", snapshot.gemstone())));
		ly = coloredLabelStat(g, font, "Glacite", FormatUtil.shortXp(snapshot.glacite().total()),
			x, ly, w, BAR_GLACITE, PvDraw.COLOR_TEXT) + 1;
		zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Glacite", snapshot.glacite())));
		return ly;
	}

	public static List<PvTooltip.Line> powderHover(String name, MiningSnapshot.Powder powder) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(name + " powder", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of("Total: " + FormatUtil.commas(powder.total()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.of("Available: " + FormatUtil.commas(powder.available()), PvDraw.COLOR_MUTED));
		tip.add(PvTooltip.Line.of("Spent: " + FormatUtil.commas(powder.spent()), PvDraw.COLOR_MUTED));
		return tip;
	}

	public static List<PvTooltip.Line> coleWeightHover(ColeWeight.Result weight, boolean shiftDetails) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("ColeWeight: " + FormatUtil.oneDecimal(weight.total()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.blank());

		Map<String, List<ColeWeight.Line>> byCat = weight.byCategory();
		ColeWeight.Line miningXp = firstLine(byCat.get("experience"), "Mining Experience");
		if (miningXp != null) {
			tip.add(PvTooltip.Line.of(
				"Mining XP: " + FormatUtil.oneDecimal(miningXp.weight()),
				PvDraw.COLOR_TEXT
			));
			tip.add(PvTooltip.Line.blank());
		}

		List<ColeWeight.Line> powders = byCat.get("powder");
		if (powders != null && !powders.isEmpty()) {
			tip.add(PvTooltip.Line.of("Powder", PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.of(
				"Mithril: " + FormatUtil.oneDecimal(lineWeight(powders, "Mithril Powder")),
				BAR_MITHRIL
			));
			tip.add(PvTooltip.Line.of(
				"Gemstone: " + FormatUtil.oneDecimal(lineWeight(powders, "Gemstone Powder")),
				BAR_GEM
			));
			tip.add(PvTooltip.Line.of(
				"Glacite: " + FormatUtil.oneDecimal(lineWeight(powders, "Glacite Powder")),
				BAR_GLACITE
			));
			tip.add(PvTooltip.Line.blank());
		}

		List<ColeWeight.Line> collections = byCat.get("collection");
		double collectionTotal = sumWeight(collections);
		tip.add(PvTooltip.Line.of("Collection", PvDraw.COLOR_ACCENT));
		if (shiftDetails && collections != null && !collections.isEmpty()) {
			List<ColeWeight.Line> shown = new ArrayList<>(collections);
			shown.sort((a, b) -> Double.compare(b.weight(), a.weight()));
			for (ColeWeight.Line line : shown) {
				tip.add(PvTooltip.Line.of(
					line.label() + ": " + FormatUtil.oneDecimal(line.weight()),
					PvDraw.COLOR_MUTED
				));
			}
		} else {
			tip.add(PvTooltip.Line.of(
				"Overall: " + FormatUtil.oneDecimal(collectionTotal),
				PvDraw.COLOR_TEXT
			));
		}
		tip.add(PvTooltip.Line.blank());

		List<ColeWeight.Line> misc = byCat.get("miscellaneous");
		tip.add(PvTooltip.Line.of("Misc", PvDraw.COLOR_ACCENT));
		tip.add(PvTooltip.Line.of(
			"Worm kills: " + FormatUtil.oneDecimal(lineWeight(misc, "Worm Kills")),
			PvDraw.COLOR_MUTED
		));
		tip.add(PvTooltip.Line.of(
			"Nuc runs: " + FormatUtil.oneDecimal(lineWeight(misc, "Nucleus Runs")),
			PvDraw.COLOR_MUTED
		));

		if (!shiftDetails) {
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.of("Hold L-shift for details", PvDraw.COLOR_MUTED));
		}
		return tip;
	}

	public static ColeWeight.Line firstLine(List<ColeWeight.Line> lines, String label) {
		if (lines == null) {
			return null;
		}
		for (ColeWeight.Line line : lines) {
			if (label.equals(line.label())) {
				return line;
			}
		}
		return lines.isEmpty() ? null : lines.getFirst();
	}

	public static double lineWeight(List<ColeWeight.Line> lines, String label) {
		if (lines == null) {
			return 0;
		}
		for (ColeWeight.Line line : lines) {
			if (label.equals(line.label())) {
				return line.weight();
			}
		}
		return 0;
	}

	public static double sumWeight(List<ColeWeight.Line> lines) {
		if (lines == null || lines.isEmpty()) {
			return 0;
		}
		double sum = 0;
		for (ColeWeight.Line line : lines) {
			sum += line.weight();
		}
		return sum;
	}

	public static void drawItemIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		if (size == 16) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	public static String title(String raw) {
		String[] parts = raw.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}

	public static String fitValue(Font font, String label, String value, int w) {
		int max = Math.max(8, w - font.width(label) - 10);
		return trim(font, value, max);
	}

	public static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ew = font.width(ellipsis);
		if (maxW <= ew) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() > 0 && font.width(sb.toString()) + ew > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	public static boolean leftShiftDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT);
	}

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines, boolean coleWeight) {
		public static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines, false);
		}

		public static HoverZone coleWeight(int x, int y, int w, int h) {
			return new HoverZone(x, y, w, h, List.of(), true);
		}
	}
}

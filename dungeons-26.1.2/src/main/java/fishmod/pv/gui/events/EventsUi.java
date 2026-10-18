/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared Events UI helpers and hover zones. */
public final class EventsUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int STAT_ROW = 12;
	public static final int SEP_GAP = 10;
	public static final int BINGO_COLS = 5;
	public static final int BINGO_ROWS = 5;
	public static final int RABBIT_SLOT = 16;
	public static final int RABBIT_ROW = 18;
	public static final int COLOR_COMPLETE = 0xFF55FF55;
	public static final int COLOR_COMMUNITY = 0xFF7C9CFF;
	public static final int COLOR_CHOCOLATE = 0xFFD4A574;
	public static final int SLOT_BG = 0x1CFFFFFF;
	public static final int SLOT_BORDER = 0x30FFFFFF;
	public static final int FLIP_MS = 480;
	public static final int PANEL_HOVER = 0x0AFFFFFF;

	public final List<HoverZone> zones = new ArrayList<>();
	public int contentX;
	public int contentY;
	public int contentW;
	public int contentH;

	public void beginFrame(int x, int y, int w, int h) {
		this.zones.clear();
		this.contentX = x;
		this.contentY = y;
		this.contentW = w;
		this.contentH = h;
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH, int x, int y, int w, int h) {
		boolean mouseInGui = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		if (!mouseInGui) {
			return;
		}
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				break;
			}
		}
	}

	public int tipStat(
		GuiGraphicsExtractor g, Font font, String label, String value, int valueColor,
		int x, int y, int w, int mx, int my, List<PvTooltip.Line> tip
	) {
		int next = statLine(g, font, label, value, x, y, w, valueColor);
		if (tip != null && !tip.isEmpty()) {
			addClippedHover(x, y, w, STAT_ROW, this.contentX, this.contentY, this.contentW, this.contentH, tip);
		}
		return next;
	}

	public void addClippedHover(
		int bx, int by, int bw, int bh,
		int clipX, int clipY, int clipW, int clipH,
		List<PvTooltip.Line> tip
	) {
		if (tip == null || tip.isEmpty()) {
			return;
		}
		int x0 = Math.max(bx, Math.max(clipX, this.contentX));
		int y0 = Math.max(by, Math.max(clipY, this.contentY));
		int x1 = Math.min(bx + bw, Math.min(clipX + clipW, this.contentX + this.contentW));
		int y1 = Math.min(by + bh, Math.min(clipY + clipH, this.contentY + this.contentH));
		if (x1 > x0 && y1 > y0) {
			this.zones.add(new HoverZone(x0, y0, x1 - x0, y1 - y0, tip));
		}
	}

	public static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String safe = value == null || value.isBlank() ? "-" : value;
		int leftMax = Math.max(8, w - font.width(safe) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, safe, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	public static int sectionSeparator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	public static List<PvTooltip.Line> tipTitle(String title, int titleColor, PvTooltip.Line... rows) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(title, titleColor));
		tip.add(PvTooltip.Line.divider());
		for (PvTooltip.Line row : rows) {
			if (row != null) {
				tip.add(row);
			}
		}
		return tip;
	}

	public static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	public static String formatAgo(long epochMs) {
		if (epochMs <= 0L) {
			return "-";
		}
		long age = Math.max(0L, System.currentTimeMillis() - epochMs);
		return FormatUtil.prettySpan(age) + " ago";
	}

	public static String prettyModifier(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	public static String prettyGoalId(String id) {
		return prettyModifier(id == null ? "" : id);
	}

	public static List<String> wrapLore(String lore, int maxChars) {
		List<String> lines = new ArrayList<>();
		if (lore == null || lore.isBlank()) {
			return lines;
		}
		String cleaned = lore.replace('\n', ' ').trim();
		while (!cleaned.isEmpty()) {
			if (cleaned.length() <= maxChars) {
				lines.add(cleaned);
				break;
			}
			int breakAt = cleaned.lastIndexOf(' ', maxChars);
			if (breakAt <= 0) {
				breakAt = maxChars;
			}
			lines.add(cleaned.substring(0, breakAt).trim());
			cleaned = cleaned.substring(breakAt).trim();
			if (lines.size() >= 8) {
				if (!cleaned.isEmpty()) {
					lines.add("…");
				}
				break;
			}
		}
		return lines;
	}

	public static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (maxW <= 0 || font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
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

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class PvDraw {
	public static final int COLOR_PANEL = 0x8A10162A;
	public static final int COLOR_PANEL_OUTER = 0xE00A0A10;
	public static final int COLOR_PANEL_INNER = 0xC0181824;
	public static final int COLOR_BORDER = 0x00FFFFFF;
	public static final int COLOR_TEXT = 0xFFE8E8F0;
	public static final int COLOR_MUTED = 0xFF9A9AAC;
	public static final int COLOR_GOLD = 0xFFFFAA00;
	public static final int COLOR_WHITE = 0xFFFFFFFF;
	public static int COLOR_ACCENT = 0xFF5B8CFF;
	public static final int COLOR_DIVIDER = 0x26FFFFFF;
	/** Visible empty track (must read against inner panel). */
	public static final int COLOR_BAR_BG = 0x38FFFFFF;
	public static final int COLOR_BAR_FILL = 0xFF3D8B5A;
	public static final int COLOR_BAR_PROGRESS = 0xFF8E8E9C;
	public static final int COLOR_BAR_FILL_SLAYER = 0xFF8B4A3D;
	/** Matches catgirllivid cosmetics name gradient. */
	public static final int COLOR_MAXED_BAR_LEFT = 0xFF9278C5;
	public static final int COLOR_MAXED_BAR_RIGHT = 0xFFF3E6FD;
	public static final int BAR_HEIGHT = 6;

	// Liquid-glass theme: dark translucent bodies, soft light edge, faint top gloss.
	/** Theme-driven cell background (set by PvTheme). */
	public static int CELL = 0x38000000;
	public static int GLASS_TOP = 0x8C141B30;
	public static int GLASS_BOTTOM = 0x960A0F1E;
	public static int GLASS_INNER_TOP = 0x22FFFFFF;
	public static int GLASS_INNER_BOTTOM = 0x12FFFFFF;
	public static final int GLASS_EDGE = 0x00FFFFFF;
	public static final int GLASS_GLOSS = 0x00FFFFFF;
	public static final int C_TL = 1;
	public static final int C_TR = 2;
	public static final int C_BR = 4;
	public static final int C_BL = 8;
	public static final int C_ALL = 15;

	private static final int[][] INSETS_CACHE = new int[128][];

	private PvDraw() {
	}

	public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + h, argb);
	}

	// Cached per radius: called many times per frame; callers only read the array.
	private static int[] cornerInsets(int r) {
		if (r < INSETS_CACHE.length) {
			int[] hit = INSETS_CACHE[r];
			if (hit == null) {
				hit = computeInsets(r);
				INSETS_CACHE[r] = hit;
			}
			return hit;
		}
		return computeInsets(r);
	}

	private static int[] computeInsets(int r) {
		int[] ins = new int[r];
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			ins[i] = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * (double) r - dy * dy)));
		}
		return ins;
	}

	/** Device pixels per local unit under the current pose (GUI scale times any viewer scaling). */
	private static float deviceScale(GuiGraphicsExtractor g) {
		float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		float poseScale = Math.abs(g.pose().m00());
		return Math.max(1f, guiScale * (poseScale <= 0f ? 1f : poseScale));
	}

	public static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
		roundedFill(g, x, y, w, h, r, argb, C_ALL);
	}

	/** Filled rect with circular corners; drawn on the device-pixel grid so the arc has no chunky steps. */
	public static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb, int mask) {
		float k = deviceScale(g);
		if (k < 1.2f) {
			roundedFillDev(g, x, y, w, h, r, argb, mask);
			return;
		}
		g.pose().pushMatrix();
		g.pose().scale(1f / k, 1f / k);
		int dx = Math.round(x * k);
		int dy = Math.round(y * k);
		int dw = Math.round((x + w) * k) - dx;
		int dh = Math.round((y + h) * k) - dy;
		roundedFillDev(g, dx, dy, dw, dh, Math.round(r * k), argb, mask);
		g.pose().popMatrix();
	}

	private static void roundedFillDev(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb, int mask) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 1) {
			fill(g, x, y, w, h, argb);
			return;
		}
		int[] ins = cornerInsets(r);
		for (int i = 0; i < r; i++) {
			int l = (mask & C_TL) != 0 ? ins[i] : 0;
			int rt = (mask & C_TR) != 0 ? ins[i] : 0;
			fill(g, x + l, y + i, w - l - rt, 1, argb);
			int bl = (mask & C_BL) != 0 ? ins[i] : 0;
			int br = (mask & C_BR) != 0 ? ins[i] : 0;
			fill(g, x + bl, y + h - 1 - i, w - bl - br, 1, argb);
		}
		fill(g, x, y + r, w, h - 2 * r, argb);
	}

	public static void roundedGradient(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom, int mask
	) {
		float k = deviceScale(g);
		if (k < 1.2f) {
			roundedGradientDev(g, x, y, w, h, r, top, bottom, mask, 4);
			return;
		}
		g.pose().pushMatrix();
		g.pose().scale(1f / k, 1f / k);
		int dx = Math.round(x * k);
		int dy = Math.round(y * k);
		int dw = Math.round((x + w) * k) - dx;
		int dh = Math.round((y + h) * k) - dy;
		roundedGradientDev(g, dx, dy, dw, dh, Math.round(r * k), top, bottom, mask, Math.max(2, Math.round(k)));
		g.pose().popMatrix();
	}

	private static void roundedGradientDev(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom, int mask, int band
	) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (h <= 0 || w <= 0) {
			return;
		}
		int[] ins = r > 1 ? cornerInsets(r) : new int[0];
		for (int i = 0; i < ins.length; i++) {
			int l = (mask & C_TL) != 0 ? ins[i] : 0;
			int rt = (mask & C_TR) != 0 ? ins[i] : 0;
			fill(g, x + l, y + i, w - l - rt, 1, lerpArgb(top, bottom, (i + 0.5f) / h));
			int row = h - 1 - i;
			int bl = (mask & C_BL) != 0 ? ins[i] : 0;
			int br = (mask & C_BR) != 0 ? ins[i] : 0;
			fill(g, x + bl, y + row, w - bl - br, 1, lerpArgb(top, bottom, (row + 0.5f) / h));
		}
		int start = ins.length;
		int end = h - ins.length;
		band = Math.max(band, (end - start + 11) / 12);
		for (int yy = start; yy < end; yy += band) {
			int bh = Math.min(band, end - yy);
			fill(g, x, y + yy, w, bh, lerpArgb(top, bottom, (yy + bh / 2f) / h));
		}
	}

	/** One-pixel-ish rounded outline on the device grid; leaves the inside untouched (no tint). */
	public static void roundedRing(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb, int mask) {
		float k = deviceScale(g);
		g.pose().pushMatrix();
		g.pose().scale(1f / k, 1f / k);
		int dx = Math.round(x * k);
		int dy = Math.round(y * k);
		int dw = Math.round((x + w) * k) - dx;
		int dh = Math.round((y + h) * k) - dy;
		int dr = Math.min(Math.round(r * k), Math.min(dw, dh) / 2);
		int t = Math.max(1, Math.round(k * 0.5f));
		int[] outer = dr > 1 ? cornerInsets(dr) : new int[0];
		int ri = Math.max(0, dr - t);
		int[] inner = ri > 1 ? cornerInsets(ri) : new int[0];
		int cornerRows = Math.min(dr, dh / 2);
		// Only the corner bands need per-row work; the straight sides are two strips.
		for (int i = 0; i < cornerRows; i++) {
			for (int half = 0; half < 2; half++) {
				int row = half == 0 ? i : dh - 1 - i;
				boolean top = half == 0;
				int lo = ((top ? mask & C_TL : mask & C_BL) != 0 && i < outer.length) ? outer[i] : 0;
				int ro = ((top ? mask & C_TR : mask & C_BR) != 0 && i < outer.length) ? outer[i] : 0;
				if (i < t) {
					fill(g, dx + lo, dy + row, dw - lo - ro, 1, argb);
					continue;
				}
				int irow = i - t;
				int ii = irow < inner.length ? inner[irow] : 0;
				int li = (top ? mask & C_TL : mask & C_BL) != 0 ? ii : 0;
				int rin = (top ? mask & C_TR : mask & C_BR) != 0 ? ii : 0;
				int leftEnd = dx + t + li;
				int rightStart = dx + dw - t - rin;
				if (leftEnd > dx + lo) {
					fill(g, dx + lo, dy + row, leftEnd - (dx + lo), 1, argb);
				}
				if (dx + dw - ro > rightStart) {
					fill(g, rightStart, dy + row, dx + dw - ro - rightStart, 1, argb);
				}
			}
		}
		int midH = dh - 2 * cornerRows;
		if (midH > 0) {
			fill(g, dx, dy + cornerRows, t, midH, argb);
			fill(g, dx + dw - t, dy + cornerRows, t, midH, argb);
		}
		g.pose().popMatrix();
	}

	/** Opaque floating surface (tooltips, menus) so nothing underneath shows through the text. */
	public static void popupSurface(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		roundedFill(g, x, y, w, h, 4, 0xFA090B11, C_ALL);
		roundedRing(g, x, y, w, h, 4, 0x00FFFFFF, C_ALL);
	}

	/** Near-opaque panel for popups and overlays so text on top stays readable. */
	public static void overlayPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		glassShadow(g, x, y, w, h, 8);
		int base = (GLASS_BOTTOM & 0x00FFFFFF) | 0xF6000000;
		int top = (GLASS_TOP & 0x00FFFFFF) | 0xF6000000;
		roundedGradient(g, x, y, w, h, 8, top, base, C_ALL);
	}

	/** Soft dark halo under a glass surface. */
	public static void glassShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
		roundedFill(g, x - 3, y - 2, w + 6, h + 6, r + 3, 0x08000000);
		roundedFill(g, x - 2, y - 1, w + 4, h + 4, r + 2, 0x0C000000);
		roundedFill(g, x - 1, y, w + 2, h + 2, r + 1, 0x12000000);
	}

	public static void glassSurface(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom, int mask
	) {
		glassSurface(g, x, y, w, h, r, top, bottom, mask, GLASS_EDGE);
	}

	public static void glassSurface(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom, int mask, int edge
	) {
		roundedGradient(g, x, y, w, h, r, top, bottom, mask);
		roundedRing(g, x, y, w, h, r, edge, mask);
		if ((mask & (C_TL | C_TR)) != 0 && w > 2 * r + 2) {
			fill(g, x + r, y + 1, w - 2 * r, 1, GLASS_GLOSS);
		}
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		glassShadow(g, x, y, w, h, 8);
		// Top-left stays square: the tab row and side tabs attach there.
		glassSurface(g, x, y, w, h, 8, GLASS_TOP, GLASS_BOTTOM, C_TR | C_BR | C_BL);
	}

	public static void innerPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		glassSurface(g, x, y, w, h, 5, GLASS_INNER_TOP, GLASS_INNER_BOTTOM, C_ALL);
	}

	public static void layeredPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int pad) {
		innerPanel(g, x, y, w, h);
	}

	/** Inter Regular is thin at this size; a second pass a fraction of a pixel over thickens the strokes. */
	private static final float THICKEN = 0F;

	public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		g.text(font, value, x, y, color, false);
	}

	/** Draw text scaled about its top-left corner (e.g. footer credits). */
	public static void textScaled(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float scale) {
		if (scale == 1.0F) {
			text(g, font, value, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, value, 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component value, int x, int y) {
		if (value == null) {
			return;
		}
		// White is the fallback for unstyled runs; per-sibling Style colours still apply.
		g.text(font, value, x, y, COLOR_WHITE, false);
	}

	public static Component styled(String value, int rgb, boolean bold) {
		return styled(value, rgb, bold, false);
	}

	public static Component styled(String value, int rgb, boolean bold, boolean italic) {
		Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
		// Bold is skipped: vanilla bold doubles Inter a whole pixel over and turns chunky.
		if (italic) {
			style = style.withItalic(true);
		}
		return Component.literal(value).setStyle(style);
	}

	public static void textBold(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		text(g, font, styled(value, color, true), x, y);
	}

	public static void textBoldScaled(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float scale) {
		if (scale == 1.0F) {
			textBold(g, font, value, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		text(g, font, styled(value, color, true), 0, 0);
		g.pose().popMatrix();
	}

	/**
	 * Y positions so a square item icon and a text line share the same vertical center.
	 * Works when {@code rowH} is shorter than the icon (dense lists used to pin icons to the top
	 * while centering text, which made icons look low).
	 */
	public record IconTextAlign(int iconY, int textY) {
		public static IconTextAlign of(int rowY, int rowH, int iconSize, int lineHeight) {
			int pairH = Math.max(Math.max(1, iconSize), Math.max(1, lineHeight));
			int pairTop = rowY + Math.max(0, (Math.max(1, rowH) - pairH) / 2);
			int iconY = pairTop + Math.max(0, (pairH - iconSize) / 2);
			int textY = pairTop + Math.max(0, (pairH - lineHeight) / 2);
			return new IconTextAlign(iconY, textY);
		}
	}

	public static int width(Font font, Component value) {
		return font.width(value);
	}

	public static int widthBold(Font font, String value) {
		return font.width(styled(value, 0xFFFFFF, true));
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, String value, int cx, int y, int color) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y, color);
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, Component value, int cx, int y) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y);
	}

	public static void textRight(GuiGraphicsExtractor g, Font font, String value, int rightX, int y, int color) {
		text(g, font, value, rightX - font.width(value), y, color);
	}

	public static int sectionHeader(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w) {
		text(g, font, label, x, y, COLOR_MUTED);
		int lineX = x + font.width(label) + 4;
		int lineY = y + font.lineHeight / 2;
		int lineW = x + w - lineX;
		if (lineW > 0) {
			fill(g, lineX, lineY, lineW, 1, COLOR_BORDER);
		}
		return font.lineHeight + 3;
	}

	public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int fillColor) {
		progressBar(g, x, y, w, h, progress, fillColor, false);
	}

	public static void progressBar(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		float progress,
		int fillColor,
		boolean maxedShiny
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		roundedFill(g, x, y, w, h, h / 2, COLOR_BAR_BG);
		int filled = Math.max(0, Math.round(w * clamped));
		if (filled <= 0) {
			return;
		}
		// Always round both ends of the fill, including incomplete bars.
		// One scheme everywhere: gold when maxed, gray while still filling.
		boolean maxed = maxedShiny || clamped >= 0.999f;
		roundedFill(g, x, y, Math.max(filled, Math.min(w, h)), h, h / 2, maxed ? COLOR_GOLD : COLOR_BAR_PROGRESS);
	}

	/** Hue wheel colour; blends toward cosmetics purple so it still nods to the name gradient. */
	public static int chromaRgb(float hue) {
		return rainbowArgb(hue);
	}

	public static Component chromaText(String text, boolean bold) {
		if (text == null || text.isEmpty()) {
			return Component.empty();
		}
		float t = (System.currentTimeMillis() % 2800L) / 2800F;
		MutableComponent out = Component.empty();
		for (int i = 0; i < text.length(); i++) {
			int rgb = rainbowArgb(positiveModulo(t + i * 0.07F, 1.0F)) & 0xFFFFFF;
			Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false);
			if (bold) {
				style = style.withBold(true);
			}
			out.append(Component.literal(String.valueOf(text.charAt(i))).setStyle(style));
		}
		return out;
	}

	/** Hue wheel colour; blends toward cosmetics purple so it still nods to the name gradient. */
	private static int rainbowArgb(float hue) {
		float h = positiveModulo(hue, 1.0F);
		int rgb = java.awt.Color.HSBtoRGB(h, 0.85F, 1.0F) & 0xFFFFFF;
		int cosmetics = COLOR_MAXED_BAR_LEFT & 0xFFFFFF;
		return lerpArgb(rgb | 0xFF000000, cosmetics | 0xFF000000, 0.18F);
	}

	private static float positiveModulo(float value, float modulus) {
		float remainder = value % modulus;
		return remainder < 0.0F ? remainder + modulus : remainder;
	}

	private static int lerpArgb(int from, int to, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int a1 = (from >>> 24) & 0xFF;
		int r1 = (from >>> 16) & 0xFF;
		int g1 = (from >>> 8) & 0xFF;
		int b1 = from & 0xFF;
		int a2 = (to >>> 24) & 0xFF;
		int r2 = (to >>> 16) & 0xFF;
		int g2 = (to >>> 8) & 0xFF;
		int b2 = to & 0xFF;
		int a = Math.round(a1 + (a2 - a1) * t);
		int r = Math.round(r1 + (r2 - r1) * t);
		int g = Math.round(g1 + (g2 - g1) * t);
		int b = Math.round(b1 + (b2 - b1) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor
	) {
		labeledBar(g, font, label, value, progress, x, y, w, fillColor, false);
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny
	) {
		labeledBar(g, font, label, value, progress, x, y, w, fillColor, maxedShiny, COLOR_TEXT, false);
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny,
		int labelColor,
		boolean labelBold
	) {
		drawLabelValue(g, font, label, value, x, y, w, labelColor, labelBold);
		int barY = y + font.lineHeight + 2;
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny);
	}

	/** Prefers a prebuilt label line (one prepareText) for skill / slayer grids. */
	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		Component labelLine,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny
	) {
		g.text(font, labelLine, x, y, COLOR_WHITE, false);
		int barY = y + font.lineHeight + 2;
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny);
	}

	/** Label left, value flush-right within {@code w} (true alignment, not space padding). */
	public static void drawLabelValue(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		int x,
		int y,
		int w,
		int labelColor,
		boolean labelBold
	) {
		String left = label == null ? "" : label;
		String right = value == null ? "" : value;
		Component leftComp = styled(left, labelColor, labelBold);
		g.text(font, leftComp, x, y, COLOR_WHITE, false);
		if (!right.isEmpty()) {
			int vw = font.width(right);
			g.text(font, styled(right, COLOR_MUTED, false), x + Math.max(0, w - vw), y, COLOR_WHITE, false);
		}
	}

	/** Right-align {@code value} after {@code label} within {@code w} using space padding. */
	public static Component paddedLabelValue(Font font, String label, String value, int w) {
		return paddedLabelValue(font, label, value, w, COLOR_TEXT, false);
	}

	public static Component paddedLabelValue(
		Font font, String label, String value, int w, int labelColor, boolean labelBold
	) {
		String left = label == null ? "" : label;
		String right = value == null ? "" : value;
		Component leftComp = styled(left, labelColor, labelBold);
		int gap = w - font.width(leftComp) - font.width(right);
		int spaceW = Math.max(1, font.width(" "));
		int spaces = Math.max(1, gap / spaceW);
		MutableComponent line = Component.empty();
		line.append(leftComp);
		line.append(Component.literal(" ".repeat(spaces)));
		line.append(styled(right, COLOR_MUTED, false));
		return line;
	}
}

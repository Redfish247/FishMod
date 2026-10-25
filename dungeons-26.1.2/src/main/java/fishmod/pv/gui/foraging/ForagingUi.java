/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging;

import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared foraging UI constants and helpers. */
public final class ForagingUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int BAR_LABEL_GAP = 2;
	public static final int BAR_AFTER = 4;
	public static final int STAT_ROW = 12;
	public static final int FLIP_MS = 480;
	public static final int PANEL_HOVER = 0x0AFFFFFF;
	public static final int BAR_FORAGE = 0xFF55AA55;
	public static final int BAR_HUNT = 0xFFAA7733;
	public static final int ENABLED = 0xFF55FF55;
	public static final int DISABLED = 0xFF9A9AAC;
	public static final int ICON = 14;
	public static final int CHIP = 16;
	public static final int SLOT = 18;
	public static final int SLOT_GAP = 3;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	public static final int ITEM_SLOT_LOCKED_BG = 0xFF3A1010;
	public static final int ITEM_SLOT_LOCKED_BORDER = 0xFFCC3333;
	public static final int FOREST_COLOR = 0xFF55AA55;
	public static final int COLOR_MAXED = 0xFF7CFF9A;
	public static final int SEP_GAP = 10;

	private ForagingUi() {}

	public static int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, List<HoverZone> zones
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			zones.add(HoverZone.of(x, y, w, bottom - y, fishmod.pv.data.Leveling.hoverTextLines(hover)));
		}
		return bottom;
	}

	public static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		// Center the rule in the gap between previous and next content (matches mining).
		int visualBottom = y - Math.max(0, STAT_ROW - font.lineHeight);
		int pad = SEP_GAP / 2;
		int lineY = visualBottom + pad;
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return lineY + 1 + pad;
	}

	public static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	public static void wrapText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
		String t = text == null || text.isBlank() ? "-" : text;
		PvDraw.text(g, font, trim(font, t, w), x, y, color);
	}

	public static void drawHover(
		GuiGraphicsExtractor g, Font font, List<HoverZone> zones, int mx, int my, int screenW, int screenH
	) {
		for (HoverZone zone : zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	/** Only the visible (scissor-clipped) portion of a slot/row is hoverable. */
	public static void addClippedHover(
		List<HoverZone> zones,
		int bx, int by, int bw, int bh,
		int clipX, int clipY, int clipW, int clipH,
		List<PvTooltip.Line> tip
	) {
		int x0 = Math.max(bx, clipX);
		int y0 = Math.max(by, clipY);
		int x1 = Math.min(bx + bw, clipX + clipW);
		int y1 = Math.min(by + bh, clipY + clipH);
		if (x1 > x0 && y1 > y0) {
			zones.add(HoverZone.of(x0, y0, x1 - x0, y1 - y0, tip));
		}
	}

	public static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	public static String formatRaceMs(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long totalSec = ms / 1000L;
		long mins = totalSec / 60L;
		long secs = totalSec % 60L;
		long remMs = ms % 1000L;
		if (mins > 0) {
			return mins + ":" + String.format(Locale.ROOT, "%02d.%03d", secs, remMs);
		}
		return secs + "." + String.format(Locale.ROOT, "%03d", remMs) + "s";
	}

	public static String formatAgo(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - ms);
		long mins = ago / 60_000L;
		if (mins < 60L) {
			return mins + "m ago";
		}
		long hours = mins / 60L;
		if (hours < 48L) {
			return hours + "h ago";
		}
		return (hours / 24L) + "d ago";
	}

	public static String shortAttr(String name) {
		if (name == null) {
			return "";
		}
		String[] parts = name.split(" ");
		if (parts.length <= 2) {
			return name;
		}
		return parts[0] + " " + parts[parts.length - 1];
	}

	/** Native 16x16 fish-family icon (no fractional downscale). */
	public static void drawFishFamilyIcon(GuiGraphicsExtractor g, String id, int x, int y) {
		if (id != null && !id.isBlank() && SkyBlockIconRenderer.hasKnownIcon(id)) {
			SkyBlockIconRenderer.draw(g, id, x, y, 16);
			return;
		}
		ItemStack stack = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER)) {
			stack = new ItemStack(Items.TROPICAL_FISH);
		}
		SkyBlockIconRenderer.draw(g, stack, id, x, y, 16);
	}

	public static void drawSkyblockIcon(GuiGraphicsExtractor g, String id, int x, int y, int size) {
		ItemStack stack = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (id != null && !id.isBlank() && SkyBlockIconRenderer.hasKnownIcon(id)) {
			SkyBlockIconRenderer.draw(g, stack, id, x, y, size);
			return;
		}
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER)) {
			String upper = id == null ? "" : id.toUpperCase(Locale.ROOT);
			if (upper.contains("TREE")) {
				stack = new ItemStack(Items.OAK_SAPLING);
			} else {
				stack = new ItemStack(Items.TROPICAL_FISH);
			}
			// No known SkyBlock model/PNG: keep foraging-specific vanilla stand-ins.
			if (size == 16) {
				g.item(stack, x, y);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				float s = size / 16f;
				g.pose().scale(s, s);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}
			return;
		}
		SkyBlockIconRenderer.draw(g, stack, id, x, y, size);
	}

	public static String pretty(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.replace('-', '_').split("_");
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

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
		public static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines);
		}
	}
}

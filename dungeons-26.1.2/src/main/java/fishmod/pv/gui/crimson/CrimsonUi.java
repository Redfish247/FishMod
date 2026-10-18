/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared Crimson UI constants and helpers. */
public final class CrimsonUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int STAT_ROW = 12;
	public static final int SLOT = 18;
	public static final int SLOT_GAP = 3;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	public static final int FLIP_MS = 480;
	public static final int SEP_GAP = 10;
	public static final int PANEL_HOVER = 0x0AFFFFFF;
	/** Mages - light purple. */
	public static final int MAGE_COLOR = 0xFFD97FFF;
	/** Barbarians - light red. */
	public static final int BARB_COLOR = 0xFFFF7777;
	/** Kuudra - red. */
	public static final int KUUDRA_COLOR = 0xFFFF5555;
	/** Dojo - gold. */
	public static final int DOJO_COLOR = 0xFFFFAA00;
	/** Crimson essence shop header - red. */
	public static final int SHOP_HEADER_COLOR = 0xFFFF5555;
	public static final int ENABLED = 0xFF55FF55;
	public static final int DISABLED = 0xFF555555;

	private CrimsonUi() {
	}

	/**
	 * Draw an item at native 16×16 when possible (avoids fuzzy downscales).
	 * Only scales when the target box is smaller than 16px.
	 */
	public static void drawItemIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		if (size >= 16) {
			int pad = (size - 16) / 2;
			g.item(icon, x + pad, y + pad);
			return;
		}
		float scale = size / 16F;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	public static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	public static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value == null || value.isBlank() ? "-" : value, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	public static void drawHover(GuiGraphicsExtractor g, Font font, List<HoverZone> zones, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				if (zone.components != null && !zone.components.isEmpty()) {
					PvTooltip.drawComponents(g, font, zone.components, mx, my, screenW, screenH);
				} else if (zone.lines != null) {
					PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				}
				return;
			}
		}
	}

	public static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	public static String formatAgo(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - ms);
		return FormatUtil.prettySpan(ago) + " ago";
	}

	public static String prettyRingtone(String raw) {
		if (raw == null || raw.isBlank()) {
			return "-";
		}
		String[] parts = raw.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}

	public static String trim(Font font, String text, int maxW) {
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

	public static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
	}

	public static boolean visible(int y, int h, int clipTop, int clipBottom) {
		return y + h > clipTop && y < clipBottom;
	}

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines, List<Component> components) {
		public static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines, null);
		}

		public static HoverZone ofComponents(int x, int y, int w, int h, List<Component> components) {
			return new HoverZone(x, y, w, h, null, components);
		}
	}
}

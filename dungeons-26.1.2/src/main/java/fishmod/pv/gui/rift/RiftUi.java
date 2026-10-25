/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift;

import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Shared Rift UI helpers, constants, and hover/hit state. */
public final class RiftUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int STAT_ROW = 12;
	public static final int SEP_GAP = 10;
	public static final int BAR_LABEL_GAP = 2;
	public static final int BAR_AFTER = 4;
	/** Match Inventories tab slot sizing so icons sit flush. */
	public static final int SLOT = 18;
	public static final int SLOT_GAP = 2;
	public static final int COL_GAP = 4;
	public static final int ARMOR_GAP = 8;
	public static final int HOTBAR_GAP = SLOT_GAP;
	public static final int PAGE_BTN = 18;
	public static final int CHARM_ICON = 16;
	public static final int CHARM_ROW = 18;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	public static final int OBTAINED = 0xFF55FF55;
	public static final int NOT_OBTAINED = 0xFFFF5555;

	public static final int MOTES_COLOR = 0xFFC97FFF;
	public static final int ENIGMA_COLOR = 0xFF55FFFF;
	public static final int TIMECHARM_COLOR = 0xFFFFAA00;
	public static final int BURGER_COLOR = 0xFFFF8855;
	public static final int CAT_COLOR = 0xFFFF77AA;
	public static final int EYE_COLOR = 0xFFAA55FF;
	public static final int ZONE_COLOR = 0xFF88AADD;
	public static final int FLIP_MS = 480;
	public static final int PANEL_HOVER = 0x0AFFFFFF;

	public final List<HoverZone> zones = new ArrayList<>();
	public final List<RunnableHit> hits = new ArrayList<>();

	public InventorySnapshot.Slot hoveredSlot;
	public ItemStack hoveredStack = ItemStack.EMPTY;

	public void clear() {
		this.zones.clear();
		this.hits.clear();
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;
	}

	public boolean mouseClicked(double mx, double my) {
		for (RunnableHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		if (this.hoveredSlot != null) {
			List<Component> tip = SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack);
			if (tip != null && !tip.isEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mx, my, screenW, screenH);
				return;
			}
		}
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	public int drawLabeledBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, int mx, int my
	) {
		PvDraw.labeledBar(g, font, label, value, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank() && mx >= x && mx < x + w && my >= y && my < bottom) {
			this.zones.add(new HoverZone(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	public static void drawItemIcon(
		GuiGraphicsExtractor g, ItemStack stack, String skyblockId, int x, int y, int size
	) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int pad = Math.max(0, (size - 16) / 2);
		SkyBlockIconRenderer.draw(g, stack, skyblockId, x + pad, y + pad, Math.min(16, size));
	}

	public static int questRow(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w
	) {
		String right = value == null || value.isBlank() ? "-" : value;
		int gap = 8;
		int valueW = font.width(right);
		int labelMax = Math.max(8, w - valueW - gap);
		PvDraw.text(g, font, trim(font, label, labelMax), x, y, PvDraw.COLOR_TEXT);
		PvDraw.textRight(g, font, right, x + w, y, questValueColor(right));
		return y + STAT_ROW;
	}

	private static int questValueColor(String value) {
		String lower = value.toLowerCase(java.util.Locale.ROOT);
		if (lower.equals("claimed")
			|| lower.equals("complete")
			|| lower.equals("unlocked")
			|| lower.equals("reward claimed")
			|| lower.startsWith("3/3")
			|| lower.startsWith("7/7")
			|| lower.startsWith("8/8")) {
			return OBTAINED;
		}
		if (lower.equals("started") || lower.equals("wand claimed")) {
			return PvDraw.COLOR_GOLD;
		}
		return PvDraw.COLOR_TEXT;
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

	public static String prettyTier(String tier) {
		return InventoryDecoder.prettyWords(tier);
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

	public static String formatAgo(long epochMs) {
		long age = System.currentTimeMillis() - epochMs;
		if (age < 0L) {
			age = 0L;
		}
		long sec = age / 1000L;
		if (sec < 60) {
			return sec + "s ago";
		}
		long min = sec / 60L;
		if (min < 60) {
			return min + "m ago";
		}
		long hr = min / 60L;
		if (hr < 48) {
			return hr + "h ago";
		}
		long days = hr / 24L;
		if (days < 60) {
			return days + "d ago";
		}
		return (days / 30L) + "mo ago";
	}

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	public record RunnableHit(int x, int y, int w, int h, Runnable action) {
	}
}

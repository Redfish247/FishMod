/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import static fishmod.pv.gui.foraging.ForagingUi.BAR_AFTER;
import static fishmod.pv.gui.foraging.ForagingUi.BAR_HUNT;
import static fishmod.pv.gui.foraging.ForagingUi.DISABLED;
import static fishmod.pv.gui.foraging.ForagingUi.ENABLED;
import static fishmod.pv.gui.foraging.ForagingUi.GAP;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.PAD;
import static fishmod.pv.gui.foraging.ForagingUi.SEP_GAP;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT_GAP;
import static fishmod.pv.gui.foraging.ForagingUi.STAT_ROW;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.foraging.ForagingUi;
import fishmod.pv.gui.foraging.ForagingUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Hunting subtab: hunting and safari stats left, critter grid and toolkit right. */
public final class HuntingPage {
	private static final int TOOLKIT_MAX_PCT = 55;

	private final List<HoverZone> zones = new ArrayList<>();
	private final SafariSection safari = new SafariSection();
	private int leftScroll;
	private int leftMaxScroll;
	private int leftX;
	private int leftY;
	private int leftW;
	private int leftH;

	public void reset() {
		this.zones.clear();
		resetScroll();
	}

	public void resetScroll() {
		this.leftScroll = 0;
		this.safari.resetScroll();
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.safari.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (this.leftMaxScroll <= 0 || !ForagingUi.hit(mouseX, mouseY, this.leftX, this.leftY, this.leftW, this.leftH)) {
			return false;
		}
		int next = Math.max(0, Math.min(this.leftMaxScroll, this.leftScroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.leftScroll) {
			return false;
		}
		this.leftScroll = next;
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int leftW = Math.max(160, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		drawStatsLeft(g, font, snapshot, x, y, leftW, h);
		drawRight(g, font, snapshot, x + leftW + GAP, y, rightW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawStatsLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int lx = x + PAD;
		int lw = w - PAD * 2;
		int top = y + PAD;
		int viewH = Math.max(0, h - PAD * 2);
		this.leftX = x;
		this.leftY = top;
		this.leftW = w;
		this.leftH = viewH;
		this.leftScroll = Math.min(this.leftScroll, this.leftMaxScroll);

		List<HoverZone> raw = new ArrayList<>();
		g.enableScissor(x + 1, top, x + w - 1, top + viewH);
		int start = top - this.leftScroll;
		int ly = start;

		ly = ForagingUi.drawBar(g, font, "Hunting", String.valueOf(snapshot.huntingLevel()),
			snapshot.huntingFill(), snapshot.huntingMaxed(), BAR_HUNT,
			snapshot.huntingHover(), lx, ly, lw, raw) + BAR_AFTER + 2;
		if (snapshot.uniqueShards() > 0L) {
			ly = ForagingUi.statLine(g, font, "Unique shards", FormatUtil.commas(snapshot.uniqueShards()),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}
		ly = ForagingUi.statLine(g, font, "Fused", FormatUtil.commas(snapshot.fusedShards()),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		if (snapshot.peltCount() > 0L) {
			ly = ForagingUi.statLine(g, font, "Pelts", FormatUtil.commas(snapshot.peltCount()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}

		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		ly = this.safari.drawStats(g, font, snapshot.safari(), x, w, lx, ly, lw, raw);

		if (!snapshot.huntStats().isEmpty()) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Shard Drops", lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 3;
			for (Map.Entry<String, Long> e : snapshot.huntStats().entrySet()) {
				String label = ForagingUi.pretty(e.getKey().replace("shard_", "").replace("_hunts", ""));
				ly = ForagingUi.statLine(g, font, label, FormatUtil.commas(e.getValue()),
					lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
			}
		}
		g.disableScissor();

		this.leftMaxScroll = Math.max(0, ly - start - viewH);
		for (HoverZone zone : raw) {
			ForagingUi.addClippedHover(this.zones, zone.x(), zone.y(), zone.w(), zone.h(),
				x, top, w, viewH, zone.lines());
		}
		if (this.leftMaxScroll > 0) {
			int contentH = ly - start;
			int thumbH = Math.max(12, viewH * viewH / Math.max(1, contentH));
			int thumbY = top + (viewH - thumbH) * this.leftScroll / this.leftMaxScroll;
			PvDraw.fill(g, x + w - 4, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER);
		}
	}

	private void drawRight(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;
		List<ForagingSnapshot.ToolkitSlot> slots = snapshot.toolkitSlots();
		int toolkitH = toolkitHeight(font.lineHeight, groups(slots), toolkitCols(rw));
		toolkitH = Math.min(toolkitH, (h - PAD * 2) * TOOLKIT_MAX_PCT / 100);
		int toolkitTop = bottom - toolkitH;
		int gridBottom = toolkitTop - SEP_GAP;

		this.safari.drawCritterGrid(g, font, snapshot.safari(), rx, y + PAD, rw, gridBottom, mx, my, this.zones);
		PvDraw.fill(g, x + PAD + 4, gridBottom + SEP_GAP / 2, Math.max(0, w - (PAD + 4) * 2), 1, 0x33FFFFFF);
		drawToolkit(g, font, snapshot, slots, rx, toolkitTop, rw, bottom, mx, my);
	}

	private void drawToolkit(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, List<ForagingSnapshot.ToolkitSlot> slots,
		int rx, int ry, int rw, int bottom, int mx, int my
	) {
		PvDraw.text(g, font, "Hunting toolkit", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = ForagingUi.statLine(g, font, "Unlocked", snapshot.toolkitUnlocked() ? "Yes" : "No",
			rx, ry, rw, snapshot.toolkitUnlocked() ? ENABLED : DISABLED) + 3;

		if (slots.isEmpty()) {
			PvDraw.text(g, font, "No slots", rx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		String lastGroup = "";
		int cols = toolkitCols(rw);
		int col = 0;
		int rowY = ry;
		for (ForagingSnapshot.ToolkitSlot slot : slots) {
			if (!slot.group().equals(lastGroup)) {
				if (!lastGroup.isEmpty()) {
					rowY += SLOT + SLOT_GAP + 2;
					col = 0;
				}
				if (rowY + font.lineHeight + SLOT > bottom) {
					break;
				}
				PvDraw.text(g, font, ForagingUi.pretty(slot.group()), rx, rowY, PvDraw.COLOR_MUTED);
				rowY += font.lineHeight + 2;
				lastGroup = slot.group();
			}
			if (col >= cols) {
				col = 0;
				rowY += SLOT + SLOT_GAP;
			}
			if (rowY + SLOT > bottom) {
				break;
			}
			int bx = rx + col * (SLOT + SLOT_GAP);
			int by = rowY;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, slot.inUse()
				? PvDraw.COLOR_GOLD
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER));
			InventorySnapshot.Slot item = slot.item();
			boolean filled = item != null && !item.isEmpty();
			ItemStack stack = ItemStack.EMPTY;
			if (filled) {
				stack = SkyBlockItemFactory.toStack(item);
				if (stack == null || stack.isEmpty()) {
					stack = SkyBlockItemFactory.iconStack(item.id());
				}
				if (stack == null || stack.isEmpty()) {
					stack = new ItemStack(Items.TRIPWIRE_HOOK);
				}
				g.item(stack, bx + 1, by + 1);
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(ForagingUi.pretty(slot.group()) + " #" + (slot.index() + 1), PvDraw.COLOR_TEXT));
			if (filled) {
				String name = item.displayName() == null || item.displayName().isBlank()
					? ForagingUi.pretty(item.id()) : item.displayName();
				tip.add(PvTooltip.Line.of(name, PvDraw.COLOR_ACCENT));
			} else {
				tip.add(PvTooltip.Line.of("Empty", PvDraw.COLOR_MUTED));
			}
			if (slot.inUse()) {
				tip.add(PvTooltip.Line.of("In use", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, tip));
			col++;
		}
	}

	private static List<String> groups(List<ForagingSnapshot.ToolkitSlot> slots) {
		List<String> out = new ArrayList<>(slots.size());
		for (ForagingSnapshot.ToolkitSlot slot : slots) {
			out.add(slot.group());
		}
		return out;
	}

	static int toolkitCols(int rw) {
		return Math.max(3, Math.min(5, (rw + SLOT_GAP) / (SLOT + SLOT_GAP)));
	}

	/** Height {@link #drawToolkit} needs for these slot groups (one entry per slot), matching its row breaks. */
	static int toolkitHeight(int lineHeight, List<String> slotGroups, int cols) {
		int h = lineHeight + 3 + STAT_ROW + 3;
		if (slotGroups.isEmpty()) {
			return h + lineHeight;
		}
		String last = "";
		int col = 0;
		int rowY = h;
		for (String group : slotGroups) {
			if (!group.equals(last)) {
				if (!last.isEmpty()) {
					rowY += SLOT + SLOT_GAP + 2;
					col = 0;
				}
				rowY += lineHeight + 2;
				last = group;
			}
			if (col >= cols) {
				col = 0;
				rowY += SLOT + SLOT_GAP;
			}
			col++;
		}
		return rowY + SLOT;
	}
}

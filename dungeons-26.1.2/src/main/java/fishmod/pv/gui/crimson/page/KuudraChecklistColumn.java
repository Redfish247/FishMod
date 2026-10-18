/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page;

import static fishmod.pv.gui.crimson.CrimsonUi.DISABLED;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.crimson.CrimsonUi.PAD;
import static fishmod.pv.gui.crimson.CrimsonUi.trim;

import fishmod.pv.data.KuudraGearChecks;
import fishmod.pv.data.KuudraGearChecks.Check;
import fishmod.pv.data.KuudraGearChecks.CheckState;
import fishmod.pv.data.KuudraGearChecks.Entry;
import fishmod.pv.data.KuudraGearChecks.Group;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One full-height column of the Kuudra gear checklist (back side of the Kuudra page). */
final class KuudraChecklistColumn {
	static final int COLOR_OK = 0xFF55FF55;
	static final int COLOR_MISSING = 0xFFFF5555;
	static final int COLOR_WARN = 0xFFFFAA00;
	static final int COLOR_UPGRADE = 0xFFFFFF55;
	static final int COLOR_HIGHLIGHT = 0xFF55FFFF;
	private static final int TITLE_H = 16;
	private static final int GROUP_H = 14;
	private static final int ENTRY_H = 20;
	private static final int CHIP_H = 11;
	private static final int GROUP_GAP = 7;
	private static final int ENTRY_GAP = 3;
	private static final int ICON = 16;
	static final int SLOT_SIZE = 18;
	private static final int ENTRY_INDENT = 4;
	private static final int CHECK_INDENT = ENTRY_INDENT + SLOT_SIZE + 4;

	private enum Kind { GROUP, ENTRY, CHIPS, NOTE, GAP }

	enum Hover { NONE, ICON, ROW }

	private record Seg(String text, int color) {
	}

	private record Line(Kind kind, int indent, int h, List<Seg> segs, String right, int rightColor, Entry entry, Group group) {
	}

	private final Map<String, ItemStack> icons = new HashMap<>();
	private int scroll;
	private int maxScroll;
	private int areaX;
	private int areaY;
	private int areaW;
	private int areaH;
	private List<Group> cachedGroups;
	private int cachedWidth = -1;
	private List<Line> lines = List.of();

	void resetScroll() {
		this.scroll = 0;
	}

	boolean mouseScrolled(double mx, double my, double scrollY) {
		if (this.maxScroll <= 0 || mx < this.areaX || mx >= this.areaX + this.areaW
			|| my < this.areaY || my >= this.areaY + this.areaH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	void render(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, List<Group> groups,
		int mx, int my, List<HoverZone> zones
	) {
		PvDraw.innerPanel(g, x, y, w, h);
		int lx = x + PAD;
		int lw = w - PAD * 2 - 4;
		PvDraw.text(g, font, title, lx, y + PAD, PvDraw.COLOR_MUTED);
		int clipTop = y + PAD + TITLE_H - 4;
		int clipBottom = y + h - PAD;
		PvDraw.fill(g, lx, clipTop - 3, w - PAD * 2, 1, 0x33FFFFFF);
		this.areaX = x;
		this.areaY = clipTop;
		this.areaW = w;
		this.areaH = Math.max(0, clipBottom - clipTop);
		if (this.areaH <= 0) {
			return;
		}
		if (groups != this.cachedGroups || lw != this.cachedWidth) {
			this.lines = buildLines(font, groups, lw);
			this.cachedGroups = groups;
			this.cachedWidth = lw;
		}
		int contentH = 2;
		for (Line line : this.lines) {
			contentH += line.h();
		}
		this.maxScroll = Math.max(0, contentH - this.areaH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(x + 1, clipTop, x + w - 1, clipBottom);
		int ly = lineTop(clipTop, this.scroll, 0);
		for (Line line : this.lines) {
			if (line.kind() != Kind.GAP && ly + line.h() > clipTop && ly < clipBottom) {
				drawLine(g, font, line, lx, ly, lw, clipTop, clipBottom, mx, my, zones);
			}
			ly += line.h();
		}
		g.disableScissor();

		if (this.maxScroll > 0) {
			int thumbH = Math.max(10, this.areaH * this.areaH / Math.max(1, contentH));
			int thumbY = clipTop + (this.areaH - thumbH) * this.scroll / this.maxScroll;
			PvDraw.fill(g, x + w - 4, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER);
		}
	}

	private void drawLine(
		GuiGraphicsExtractor g, Font font, Line line, int lx, int ly, int lw,
		int clipTop, int clipBottom, int mx, int my, List<HoverZone> zones
	) {
		int textY = ly + (line.h() - font.lineHeight) / 2 + 1;
		if (line.kind() == Kind.GROUP) {
			PvDraw.fill(g, lx - 2, ly, lw + 4, line.h() - 1, 0x22FFFFFF);
		}
		Entry entry = line.kind() == Kind.ENTRY ? line.entry() : null;
		boolean preview = entry != null && hasPreview(entry);
		Hover hover = hoverAt(mx, my, lx, ly, lw, line.h(), clipTop, clipBottom, preview);
		int sx = slotX(lx);
		int sy = slotY(ly, line.h());
		if (entry != null && !entry.iconId().isEmpty()) {
			PvDraw.fill(g, sx, sy, SLOT_SIZE, SLOT_SIZE, ITEM_SLOT_BG);
			g.outline(sx, sy, SLOT_SIZE, SLOT_SIZE,
				hover == Hover.ICON ? PvDraw.COLOR_ACCENT : preview ? ITEM_SLOT_BORDER : DISABLED);
			ItemStack icon = icon(entry.iconId());
			if (!icon.isEmpty()) {
				int pad = (SLOT_SIZE - ICON) / 2;
				g.item(icon, sx + pad, sy + pad);
				if (!preview) {
					PvDraw.fill(g, sx + pad, sy + pad, ICON, ICON, 0x99000000);
				}
			}
		}
		int rightW = line.right() == null ? 0 : font.width(line.right()) + 6;
		int cursor = lx + line.indent();
		int limit = lx + lw - rightW;
		for (Seg seg : line.segs()) {
			if (cursor >= limit) {
				break;
			}
			String text = trim(font, seg.text(), limit - cursor);
			PvDraw.text(g, font, text, cursor, textY, seg.color());
			cursor += font.width(text);
		}
		if (line.right() != null) {
			PvDraw.textRight(g, font, line.right(), lx + lw, textY, line.rightColor());
		}
		if (hover == Hover.NONE) {
			return;
		}
		int y0 = Math.max(ly, clipTop);
		int y1 = Math.min(ly + line.h(), clipBottom);
		if (hover == Hover.ICON) {
			int iy0 = Math.max(sy, clipTop);
			int iy1 = Math.min(sy + SLOT_SIZE, clipBottom);
			zones.add(HoverZone.ofComponents(sx, iy0, SLOT_SIZE, iy1 - iy0, itemTip(entry)));
			return;
		}
		List<Component> tip = line.entry() != null ? rowTip(line.entry())
			: line.group() != null ? groupTip(line.group()) : null;
		if (tip != null) {
			zones.add(HoverZone.ofComponents(lx, y0, lw, y1 - y0, tip));
		}
	}

	static int lineTop(int clipTop, int scroll, int offset) {
		return clipTop + 2 - scroll + offset;
	}

	static int slotX(int lx) {
		return lx + ENTRY_INDENT - 1;
	}

	static int slotY(int ly, int lineH) {
		return ly + (lineH - SLOT_SIZE) / 2;
	}

	static boolean hasPreview(Entry entry) {
		return entry.stack() != null || entry.pet() != null;
	}

	/** Only an owned item's icon slot opens the full item tooltip; the rest of the row gets a short note. */
	static Hover hoverAt(int mx, int my, int lx, int ly, int lw, int lineH, int clipTop, int clipBottom, boolean preview) {
		int y0 = Math.max(ly, clipTop);
		int y1 = Math.min(ly + lineH, clipBottom);
		if (y1 <= y0 || my < y0 || my >= y1 || mx < lx || mx >= lx + lw) {
			return Hover.NONE;
		}
		int sx = slotX(lx);
		int sy = slotY(ly, lineH);
		if (preview && mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
			return Hover.ICON;
		}
		return Hover.ROW;
	}

	private ItemStack icon(String id) {
		ItemStack cached = this.icons.get(id);
		if (cached == null || cached.isEmpty()) {
			cached = SkyBlockItemFactory.iconStack(id);
			this.icons.put(id, cached);
		}
		return cached;
	}

	private static List<Line> buildLines(Font font, List<Group> groups, int lw) {
		List<Line> out = new ArrayList<>();
		if (groups == null || groups.isEmpty()) {
			out.add(new Line(Kind.NOTE, 0, CHIP_H + 2, List.of(new Seg("No gear data", PvDraw.COLOR_MUTED)), null, 0, null, null));
			return out;
		}
		for (int i = 0; i < groups.size(); i++) {
			Group group = groups.get(i);
			if (i > 0) {
				out.add(new Line(Kind.GAP, 0, GROUP_GAP, null, null, 0, null, null));
			}
			int statusColor = statusColor(group.status(), group.informational());
			out.add(new Line(Kind.GROUP, 2, GROUP_H, List.of(
				new Seg(mark(group.status()) + " ", statusColor),
				new Seg(group.title(), PvDraw.COLOR_WHITE)
			), summary(group), statusColor, null, group));
			boolean info = group.informational();
			addChecks(out, font, group.checks(), ENTRY_INDENT + 4, lw, null, group, info);
			if (group.entries().isEmpty() && group.checks().isEmpty()) {
				out.add(new Line(Kind.NOTE, ENTRY_INDENT + 4, CHIP_H + 1, List.of(new Seg(emptyNote(group), PvDraw.COLOR_MUTED)),
					null, 0, null, group));
			}
			for (int e = 0; e < group.entries().size(); e++) {
				Entry entry = group.entries().get(e);
				if (e > 0) {
					out.add(new Line(Kind.GAP, 0, ENTRY_GAP, null, null, 0, null, null));
				}
				int color = statusColor(entry.status(), info);
				List<Seg> segs = new ArrayList<>();
				segs.add(new Seg(mark(entry.status()) + " ", color));
				String right = null;
				if (entry.value() != null) {
					segs.add(new Seg(entry.name() + ": ", PvDraw.COLOR_MUTED));
					segs.add(new Seg(entry.value(), color));
				} else {
					segs.add(new Seg(entry.name(), color));
					right = entry.source();
				}
				out.add(new Line(Kind.ENTRY, CHECK_INDENT, ENTRY_H, segs, right, PvDraw.COLOR_MUTED, entry, group));
				addChecks(out, font, entry.checks(), CHECK_INDENT, lw, entry, group, info);
			}
		}
		return out;
	}

	/** Checks flow as whole chips, wrapping onto new rows instead of being cut. */
	private static void addChecks(
		List<Line> out, Font font, List<Check> checks, int indent, int lw, Entry entry, Group group, boolean info
	) {
		List<Seg> row = new ArrayList<>();
		int used = 0;
		int gap = font.width("   ");
		for (Check check : checks) {
			List<Seg> chip = chip(check, info);
			int chipW = 0;
			for (Seg seg : chip) {
				chipW += font.width(seg.text());
			}
			if (!row.isEmpty() && indent + used + gap + chipW > lw) {
				out.add(new Line(Kind.CHIPS, indent, CHIP_H, row, null, 0, entry, group));
				row = new ArrayList<>();
				used = 0;
			}
			if (!row.isEmpty()) {
				row.add(new Seg("   ", 0));
				used += gap;
			}
			row.addAll(chip);
			used += chipW;
		}
		if (!row.isEmpty()) {
			out.add(new Line(Kind.CHIPS, indent, CHIP_H, row, null, 0, entry, group));
		}
	}

	private static List<Seg> chip(Check check, boolean info) {
		List<Seg> segs = new ArrayList<>(3);
		if (!check.label().isEmpty()) {
			segs.add(new Seg(check.label() + " ", PvDraw.COLOR_MUTED));
		}
		int color = checkColor(check, info);
		boolean suffix = check.state() == CheckState.WARN && !check.label().isEmpty();
		segs.add(new Seg(suffix ? check.value() + " (warning)" : check.value(), color));
		if (check.upgradeTo() != null) {
			segs.add(new Seg(" \u2192 " + check.upgradeTo(), color));
		}
		return segs;
	}

	/** Green satisfied, yellow upgrade, red essential missing, grey unverifiable. Informational rows stay grey. */
	static int checkColor(Check check, boolean info) {
		return switch (check.state()) {
			case OK -> check.highlight() ? COLOR_HIGHLIGHT : COLOR_OK;
			case UPGRADE -> info ? PvDraw.COLOR_MUTED : COLOR_UPGRADE;
			case MISSING -> info ? PvDraw.COLOR_MUTED : COLOR_MISSING;
			case WARN -> COLOR_WARN;
			case INFO -> PvDraw.COLOR_TEXT;
			case UNKNOWN -> PvDraw.COLOR_MUTED;
		};
	}

	static final String MISSING_TEXT = KuudraGearChecks.MISSING_TEXT;

	private static String emptyNote(Group group) {
		return switch (group.status()) {
			case UNKNOWN -> "Unknown";
			case MISSING -> MISSING_TEXT;
			default -> "";
		};
	}

	private static String mark(KuudraGearChecks.Status status) {
		return switch (status) {
			case COMPLETE -> "\u2714";
			case INCOMPLETE -> "!";
			case MISSING -> "\u2718";
			case UNKNOWN -> "?";
		};
	}

	static int statusColor(KuudraGearChecks.Status status, boolean informational) {
		return switch (status) {
			case COMPLETE -> COLOR_OK;
			case INCOMPLETE -> informational ? PvDraw.COLOR_MUTED : COLOR_UPGRADE;
			case MISSING -> informational ? PvDraw.COLOR_MUTED : COLOR_MISSING;
			case UNKNOWN -> PvDraw.COLOR_MUTED;
		};
	}

	private static String summary(Group group) {
		String word = switch (group.status()) {
			case COMPLETE -> "Complete";
			case INCOMPLETE -> "Incomplete";
			case MISSING -> "Missing";
			case UNKNOWN -> "Unknown";
		};
		boolean countable = group.entries().stream().allMatch(e -> e.value() == null);
		return countable && group.owned() > 1 && group.status() != KuudraGearChecks.Status.UNKNOWN
			? group.owned() + "x  " + word : word;
	}

	/** Full item preview, only for owned items. */
	private static List<Component> itemTip(Entry entry) {
		List<String> lore = KuudraGearChecks.previewLore(entry);
		String title = KuudraGearChecks.previewTitle(entry);
		List<Component> tip = new ArrayList<>(lore.isEmpty()
			? List.of(SkyBlockItemFactory.legacyLine(title)) : KuudraPage.loreComponents(title, lore));
		if (entry.source() != null) {
			tip.add(Component.empty());
			tip.add(SkyBlockItemFactory.legacyLine("\u00a78Found in: " + entry.source()));
		}
		return tip;
	}

	/** Short reasons for a row's colour; null when there's nothing specific to say. */
	private static List<Component> rowTip(Entry entry) {
		List<String> lines = rowTipLines(entry);
		if (lines.isEmpty()) {
			return null;
		}
		List<Component> tip = new ArrayList<>();
		tip.add(SkyBlockItemFactory.legacyLine("\u00a7f" + entry.name() + (entry.value() == null ? "" : ": " + entry.value())));
		for (String line : lines) {
			tip.add(SkyBlockItemFactory.legacyLine(line));
		}
		return tip;
	}

	static List<String> rowTipLines(Entry entry) {
		List<String> out = new ArrayList<>();
		for (Check check : entry.checks()) {
			String text = check.label().isEmpty() ? check.value() : check.label() + " " + check.value();
			switch (check.state()) {
				case MISSING -> out.add("\u00a7cMissing: " + text);
				case UPGRADE -> out.add("\u00a7eUpgrade: " + text + (check.upgradeTo() == null ? "" : " \u2192 " + check.upgradeTo()));
				case WARN -> out.add("\u00a76Warning: " + text);
				default -> {
				}
			}
		}
		return out;
	}

	private static List<Component> groupTip(Group group) {
		String line = group.status() == KuudraGearChecks.Status.UNKNOWN ? "\u00a77Unknown: not available in the API"
			: group.informational() ? "\u00a77Optional, not a Kuudra requirement" : null;
		return line == null ? null : List.of(SkyBlockItemFactory.legacyLine("\u00a7f" + group.title()),
			SkyBlockItemFactory.legacyLine(line));
	}
}

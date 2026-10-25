/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons;

import fishmod.pv.data.DungeonChestHistory;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.TextColor;

/** Dungeons → Chests: recent runs from {@code dungeons.treasures} with every reward chest. */
public final class DungeonChestsPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int ROW = 12;
	private static final int SEP_GAP = 10;
	private static final int HEADER = 0xFFFFAA55;
	private static final int MASTER = 0xFFFF5555;
	private static final int NORMAL = 0xFF55FF55;
	private static final int KUUDRA = 0xFFFFAA00;
	private static final int ESSENCE = 0xFFFF55FF;
	private static final String[] TYPES = { "bedrock", "obsidian", "emerald", "diamond", "gold", "wood", "paid", "free" };

	private DungeonChestHistory history = DungeonChestHistory.empty();
	private final List<HoverZone> zones = new ArrayList<>();
	private List<Map.Entry<String, Integer>> notable = List.of();
	private final Map<String, String> notableRaw = new java.util.HashMap<>();

	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int scroll;
	private int maxScroll;

	public void apply(DungeonSnapshot data) {
		this.history = data == null ? DungeonChestHistory.empty() : data.chestHistory();
		this.scroll = 0;
		this.maxScroll = 0;
		Map<String, Integer> counts = new LinkedHashMap<>();
		this.notableRaw.clear();
		for (DungeonChestHistory.Run run : this.history.runs()) {
			DungeonChestHistory.Chest opened = run.opened();
			if (opened == null) {
				continue;
			}
			for (String reward : opened.rewards()) {
				if (!DungeonChestHistory.isEssence(reward) && !DungeonChestHistory.isBook(reward)) {
					String name = DungeonChestHistory.prettyReward(reward);
					counts.merge(name, 1, Integer::sum);
					this.notableRaw.putIfAbsent(name, reward);
				}
			}
		}
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort((a, b) -> b.getValue() - a.getValue());
		this.notable = sorted;
	}

	public boolean mouseScrolled(double mx, double my, double scrollY) {
		if (mx < this.listX || mx >= this.listX + this.listW || my < this.listY || my >= this.listY + this.listH
			|| this.maxScroll <= 0) {
			return false;
		}
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.round(scrollY * ROW)));
		return true;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		this.zones.clear();
		int leftW = Math.max(130, w / 3);
		int rightX = x + leftW + GAP;
		int rightW = w - leftW - GAP;
		drawSummary(g, font, x, y, leftW, h);
		drawRuns(g, font, rightX, y, rightW, h, mx, my);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				break;
			}
		}
	}

	private void drawSummary(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int bottom = py + ph - PAD;
		int cy = py + PAD;
		PvDraw.text(g, font, "Chest History", x, cy, HEADER);
		cy += font.lineHeight + 4;
		List<DungeonChestHistory.Run> runs = this.history.runs();
		if (runs.isEmpty()) {
			PvDraw.text(g, font, "No recent runs", x, cy, PvDraw.COLOR_MUTED);
			return;
		}
		Map<String, Integer> openedByType = new LinkedHashMap<>();
		int opened = 0;
		int rerolls = 0;
		int claimable = 0;
		long now = System.currentTimeMillis();
		for (DungeonChestHistory.Run run : runs) {
			DungeonChestHistory.Chest chest = run.opened();
			if (chest != null) {
				opened++;
				openedByType.merge(chest.type().toLowerCase(Locale.ROOT), 1, Integer::sum);
			} else if (!run.expired(now)) {
				claimable++;
			}
			for (DungeonChestHistory.Chest c : run.chests()) {
				rerolls += c.rerolls();
			}
		}
		cy = stat(g, font, "Runs", FormatUtil.commas(runs.size()), x, cy, w, PvDraw.COLOR_TEXT);
		cy = stat(g, font, "Chests Opened", FormatUtil.commas(opened), x, cy, w, PvDraw.COLOR_TEXT);
		cy = stat(g, font, "Claimable", FormatUtil.commas(claimable), x, cy, w, claimable > 0 ? NORMAL : PvDraw.COLOR_MUTED);
		cy = stat(g, font, "Rerolls", FormatUtil.commas(rerolls), x, cy, w, PvDraw.COLOR_TEXT);
		long oldest = runs.get(runs.size() - 1).completedMs();
		cy = stat(g, font, "Oldest Run", ago(oldest), x, cy, w, PvDraw.COLOR_MUTED);

		if (!openedByType.isEmpty() && cy + SEP_GAP + ROW <= bottom) {
			cy = separator(g, px, cy, pw);
			for (String type : TYPES) {
				Integer n = openedByType.get(type);
				if (n == null || cy + ROW > bottom) {
					continue;
				}
				PvDraw.text(g, font, typeLabel(type), x, cy, typeColor(type));
				PvDraw.textRight(g, font, FormatUtil.commas(n), x + w, cy, PvDraw.COLOR_TEXT);
				cy += ROW;
			}
		}

		if (!this.notable.isEmpty() && cy + SEP_GAP + ROW * 2 <= bottom) {
			cy = separator(g, px, cy, pw);
			PvDraw.text(g, font, "Drops", x, cy, PvDraw.COLOR_MUTED);
			cy += ROW;
			for (Map.Entry<String, Integer> e : this.notable) {
				if (cy + ROW > bottom) {
					break;
				}
				String count = e.getValue() + "x";
				PvDraw.text(g, font, trim(font, e.getKey(), w - font.width(count) - 6), x, cy,
					rewardColor(this.notableRaw.get(e.getKey())));
				PvDraw.textRight(g, font, count, x + w, cy, PvDraw.COLOR_TEXT);
				cy += ROW;
			}
		}
	}

	private void drawRuns(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph, int mx, int my) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int cy = py + PAD;
		List<DungeonChestHistory.Run> runs = this.history.runs();
		PvDraw.text(g, font, "Recent Runs", x, cy, HEADER);
		PvDraw.textRight(g, font, "Newest first", x + w, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 4;

		this.listX = x;
		this.listY = cy;
		this.listW = w;
		this.listH = Math.max(ROW, py + ph - PAD - cy);
		this.maxScroll = Math.max(0, runs.size() * ROW - this.listH);
		this.scroll = Math.min(this.scroll, this.maxScroll);
		if (runs.isEmpty()) {
			PvDraw.textCentered(g, font, "No recent runs",
				px + pw / 2, this.listY + this.listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		long now = System.currentTimeMillis();
		int floorW = font.width("M7");
		for (DungeonChestHistory.Run run : runs) {
			floorW = Math.max(floorW, font.width(run.floor()));
		}
		floorW += 6;
		int agoW = font.width("00d 00h ago") + 6;
		int typeW = font.width("Obsidian") + 6;
		g.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
		int rowY = this.listY - this.scroll;
		for (DungeonChestHistory.Run run : runs) {
			if (rowY + ROW < this.listY) {
				rowY += ROW;
				continue;
			}
			if (rowY > this.listY + this.listH) {
				break;
			}
			boolean hover = mx >= x && mx < x + w && my >= Math.max(rowY, this.listY)
				&& my < Math.min(rowY + ROW, this.listY + this.listH);
			if (hover) {
				PvDraw.fill(g, x - 2, rowY - 1, w + 4, ROW, 0x14FFFFFF);
			}
			PvDraw.text(g, font, run.floor(), x, rowY, floorColor(run.floor()));
			if (run.completedMs() > 0L && !run.expired(now)) {
				PvDraw.text(g, font, FormatUtil.prettySpan(run.expiresMs() - now) + " left", x + floorW, rowY, NORMAL);
			} else {
				PvDraw.text(g, font, ago(run.completedMs()), x + floorW, rowY, PvDraw.COLOR_MUTED);
			}
			DungeonChestHistory.Chest opened = run.opened();
			int cx = x + floorW + agoW;
			if (opened == null) {
				PvDraw.text(g, font, "Not opened", cx, rowY, PvDraw.COLOR_MUTED);
			} else {
				PvDraw.text(g, font, typeLabel(opened.type()), cx, rowY, typeColor(opened.type()));
				drawLoot(g, font, opened, cx + typeW, rowY, x + w - cx - typeW);
			}
			if (hover) {
				this.zones.add(new HoverZone(x, Math.max(rowY, this.listY), w, ROW, runTooltip(run)));
			}
			rowY += ROW;
		}
		g.disableScissor();
	}

	private static List<PvTooltip.Line> runTooltip(DungeonChestHistory.Run run) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(run.floor() + " Run", floorColor(run.floor())));
		tip.add(PvTooltip.Line.meta("Completed " + ago(run.completedMs())));
		long now = System.currentTimeMillis();
		if (run.completedMs() > 0L) {
			tip.add(run.expired(now)
				? PvTooltip.Line.meta("Expired from Croesus " + ago(run.expiresMs()))
				: PvTooltip.Line.of("Croesus expiry in " + FormatUtil.prettySpan(run.expiresMs() - now), NORMAL));
		}
		if (!run.party().isEmpty()) {
			tip.add(PvTooltip.Line.divider());
			for (String member : run.party()) {
				tip.add(PvTooltip.Line.text(legacySpans(member)));
			}
		}
		if (!run.chests().isEmpty()) {
			tip.add(PvTooltip.Line.divider());
		}
		for (DungeonChestHistory.Chest chest : run.chests()) {
			String title = typeLabel(chest.type()) + (chest.paid() ? " (opened)" : "");
			tip.add(PvTooltip.Line.of(title, typeColor(chest.type())));
			for (String reward : chest.rewards()) {
				tip.add(rewardLine(DungeonChestHistory.prettyReward(reward), DungeonChestHistory.rewardTier(reward)));
			}
		}
		return tip;
	}

	private static void drawLoot(GuiGraphicsExtractor g, Font font, DungeonChestHistory.Chest chest, int x, int y, int maxW) {
		if (chest.rewards().isEmpty()) {
			return;
		}
		List<String> items = new ArrayList<>();
		for (String reward : chest.rewards()) {
			if (!DungeonChestHistory.isEssence(reward)) {
				items.add(reward);
			}
		}
		if (items.isEmpty()) {
			PvDraw.text(g, font, trim(font, "Essence only", maxW), x, y, ESSENCE);
			return;
		}
		int cx = x;
		int end = x + maxW;
		for (int i = 0; i < items.size(); i++) {
			String text = DungeonChestHistory.prettyReward(items.get(i)) + (i < items.size() - 1 ? "," : "");
			int color = rewardColor(items.get(i));
			int remaining = end - cx;
			if (isBold(DungeonChestHistory.rewardTier(items.get(i)))) {
				if (PvDraw.widthBold(font, text) > remaining) {
					PvDraw.text(g, font, trim(font, text, remaining), cx, y, color);
					return;
				}
				PvDraw.textBold(g, font, text, cx, y, color);
				cx += PvDraw.widthBold(font, text) + font.width(" ");
				continue;
			}
			if (font.width(text) > remaining) {
				PvDraw.text(g, font, trim(font, text, remaining), cx, y, color);
				return;
			}
			PvDraw.text(g, font, text, cx, y, color);
			cx += font.width(text + " ");
		}
	}

	// Hypixel lore draws ultimate enchants bold (\u00a7d\u00a7l); regular books are not.
	static boolean isBold(String tier) {
		return "ULTIMATE".equals(tier);
	}

	static PvTooltip.Line rewardLine(String name, String tier) {
		int color = tierColor(tier);
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.of("  ", color),
			isBold(tier) ? PvTooltip.Span.bold(name, color) : PvTooltip.Span.of(name, color)
		));
	}

	private static int rewardColor(String raw) {
		return tierColor(DungeonChestHistory.rewardTier(raw));
	}

	private static int tierColor(String tier) {
		return switch (tier) {
			case "ESSENCE", "ULTIMATE", "MYTHIC" -> ESSENCE;
			case "BOOK", "RARE" -> 0xFF5555FF;
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			case "SUPREME" -> 0xFFAA0000;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	private static int floorColor(String floor) {
		if (DungeonChestHistory.isKuudra(floor)) {
			return KUUDRA;
		}
		return floor != null && floor.startsWith("M") ? MASTER : NORMAL;
	}

	private static String typeLabel(String type) {
		if (type == null || type.isBlank()) {
			return "Chest";
		}
		String t = type.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(t.charAt(0)) + t.substring(1);
	}

	private static int typeColor(String type) {
		return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
			case "wood" -> 0xFFC08A4A;
			case "gold" -> 0xFFFFAA00;
			case "diamond" -> 0xFF55FFFF;
			case "emerald" -> 0xFF55FF55;
			case "obsidian" -> 0xFFAA55FF;
			case "bedrock" -> 0xFFAAAAAA;
			case "paid" -> 0xFFFFAA00;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	private int stat(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int color) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value, x + w, y, color);
		return y + ROW;
	}

	private static int separator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, y + (SEP_GAP - 1) / 2, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static List<PvTooltip.Span> legacySpans(String text) {
		List<PvTooltip.Span> spans = new ArrayList<>();
		int color = PvDraw.COLOR_TEXT;
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				if (!buf.isEmpty()) {
					spans.add(PvTooltip.Span.of(buf.toString(), color));
					buf.setLength(0);
				}
				ChatFormatting f = ChatFormatting.getByCode(text.charAt(++i));
				TextColor tc = f == null ? null : TextColor.fromLegacyFormat(f);
				if (tc != null) {
					color = 0xFF000000 | tc.getValue();
				} else if (f == ChatFormatting.RESET) {
					color = PvDraw.COLOR_TEXT;
				}
			} else {
				buf.append(c);
			}
		}
		if (!buf.isEmpty()) {
			spans.add(PvTooltip.Span.of(buf.toString(), color));
		}
		return spans;
	}

	private static String ago(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		return FormatUtil.prettySpan(Math.max(0L, System.currentTimeMillis() - ms)) + " ago";
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null || maxW <= 0) {
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

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}

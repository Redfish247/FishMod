/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.fishing;

import fishmod.pv.data.BestiarySnapshot;
import fishmod.pv.data.FishingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.TrophyFishData;
import fishmod.pv.data.TrophySkulls;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.neu.NeuRepoCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fishing main page. Left: skill, items fished and sea creature totals. Right: sea creature kills
 * per bestiary family. Trophy fish and frogs live on the Trophies subtab.
 */
public final class FishingPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	private static final int STAT_ROW = 12;
	private static final int SEP_GAP = 10;
	private static final int BAR_FISH = 0xFF55AAFF;
	private static final int SLOT = 16;
	private static final int TIER_BORDER = 1;
	private static final int COLOR_MAXED = 0xFF7CFF9A;
	/** NEU bestiary category holding every sea creature family. */
	private static final String SEA_CREATURE_CATEGORY = "fishing";
	/** Soft violet for trophy names. */
	private static final int NAME_COLOR = 0xFFC9A6FF;
	private static final int TIER_DIAMOND = 0xFF55FFFF;
	private static final int TIER_GOLD = 0xFFFFE566;
	private static final int TIER_SILVER = 0xFFD0D0D8;
	private static final int TIER_BRONZE = 0xFFA05A2C;
	private static final int TIP_BG = 0xF0111116;
	private static final int TIP_BORDER = 0x28FFFFFF;
	private static final int TIP_TOTAL_LABEL = 0xFF9A9AA5;
	private static final int TIP_TOTAL_VALUE = 0xFFE8E8EE;
	private static final int TIP_COUNT = 0xFFC8C8D0;
	private static final int TIP_PAD_X = 12;
	private static final int TIP_PAD_Y = 10;
	private static final int TIP_ROW_GAP = 4;
	private static final int TIP_MIN_W = 160;

	private FishingSnapshot snapshot = FishingSnapshot.empty();
	private BestiarySnapshot bestiary = BestiarySnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();
	private BestiarySnapshot.CategoryProgress seaRowsFor;
	private List<BestiarySnapshot.FamilyProgress> seaRows;
	private int listScroll;
	private int listMaxScroll;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	public void apply(FishingSnapshot snapshot, BestiarySnapshot bestiary) {
		this.snapshot = snapshot == null ? FishingSnapshot.empty() : snapshot;
		this.bestiary = bestiary == null ? BestiarySnapshot.empty() : bestiary;
		this.zones.clear();
		this.listScroll = 0;
		TrophyFishData.ensureLoaded();
		TrophySkulls.ensureLoaded();
	}

	public FishingSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseScrolled(double mx, double my, double scrollY) {
		if (this.listMaxScroll <= 0
			|| mx < this.listX || mx >= this.listX + this.listW || my < this.listY || my >= this.listY + this.listH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.listMaxScroll, this.listScroll + (scrollY > 0 ? -STAT_ROW : STAT_ROW)));
		if (next == this.listScroll) {
			return false;
		}
		this.listScroll = next;
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		int leftW = Math.max(150, w * 42 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawOverview(g, font, x, y, leftW, h);
		drawSeaCreatureKills(g, font, x + leftW + GAP, y, rightW, h);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	private void drawOverview(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = drawBar(g, font, "Fishing", String.valueOf(this.snapshot.fishingLevel()),
			this.snapshot.fishingFill(), this.snapshot.fishingMaxed(), BAR_FISH,
			this.snapshot.fishingHover(), lx, ly, lw) + BAR_AFTER + 2;

		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Items fished", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = statLine(g, font, "Total", FormatUtil.commas(this.snapshot.itemsFishedTotal()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = statLine(g, font, "Normal", FormatUtil.commas(this.snapshot.itemsFishedNormal()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Treasure", FormatUtil.commas(this.snapshot.itemsFishedTreasure()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Large treasure", FormatUtil.commas(this.snapshot.itemsFishedLargeTreasure()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Outstanding", FormatUtil.commas(this.snapshot.itemsFishedOutstanding()),
			lx, ly, lw, PvDraw.COLOR_MUTED);

		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Sea creatures", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		int killsY = ly;
		ly = statLine(g, font, "Kills", FormatUtil.commas(this.snapshot.seaCreatureKills()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		this.zones.add(HoverZone.of(lx, killsY, lw, STAT_ROW, List.of(
			PvTooltip.Line.title("Sea creature kills", PvDraw.COLOR_TEXT),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Hypixel's sea creature kills stat.", PvDraw.COLOR_MUTED)
		)));
		if (this.snapshot.dolphinMilestone() >= 0L) {
			int rowY = ly;
			ly = statLine(g, font, "Dolphin milestone", FormatUtil.commas(this.snapshot.dolphinMilestone()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
			this.zones.add(HoverZone.of(lx, rowY, lw, STAT_ROW, List.of(
				PvTooltip.Line.title("Dolphin milestone", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Sea creatures killed toward the", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("Dolphin pet's milestone rewards.", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("Hypixel keeps this as a separate", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("counter from Kills, so the two", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("totals don't match.", PvDraw.COLOR_MUTED)
			)));
		}
		statLine(g, font, "Festival sharks", FormatUtil.commas(this.snapshot.festivalSharksKilled()),
			lx, ly, lw, TIER_GOLD);
	}

	private void drawSeaCreatureKills(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		BestiarySnapshot.CategoryProgress category = this.bestiary.category(SEA_CREATURE_CATEGORY);
		if (category != this.seaRowsFor || this.seaRows == null) {
			this.seaRows = seaCreatureRows(category);
			this.seaRowsFor = category;
		}
		List<BestiarySnapshot.FamilyProgress> rows = this.seaRows;
		int families = category == null ? 0 : category.families().size();
		PvDraw.text(g, font, "Sea creature kills", rx, ry, PvDraw.COLOR_MUTED);
		if (families > 0) {
			PvDraw.textRight(g, font, rows.size() + " / " + families + " killed", rx + rw, ry, PvDraw.COLOR_ACCENT);
		}
		this.zones.add(HoverZone.of(rx, ry, rw, font.lineHeight, List.of(
			PvTooltip.Line.title("Sea creature kills", PvDraw.COLOR_TEXT),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Kills per sea creature from the bestiary.", PvDraw.COLOR_MUTED),
			PvTooltip.Line.of("The total on the left comes from player", PvDraw.COLOR_MUTED),
			PvTooltip.Line.of("stats, so the two can differ.", PvDraw.COLOR_MUTED)
		)));
		ry += font.lineHeight + 4;

		this.listX = rx;
		this.listY = ry;
		this.listW = rw;
		this.listH = Math.max(0, bottom - ry);
		if (rows.isEmpty()) {
			this.listMaxScroll = 0;
			String empty = category == null ? "No bestiary data" : "No sea creature kills";
			PvDraw.textCentered(g, font, empty, rx + rw / 2, ry + this.listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}
		this.listMaxScroll = Math.max(0, rows.size() * STAT_ROW - this.listH);
		this.listScroll = Math.min(this.listScroll, this.listMaxScroll);
		int textW = this.listMaxScroll > 0 ? rw - 5 : rw;

		g.enableScissor(rx, ry, rx + rw + 3, ry + this.listH);
		for (int i = 0; i < rows.size(); i++) {
			int rowY = ry + i * STAT_ROW - this.listScroll;
			if (rowY + STAT_ROW <= ry || rowY >= ry + this.listH) {
				continue;
			}
			BestiarySnapshot.FamilyProgress fam = rows.get(i);
			String kills = FormatUtil.commas(fam.kills());
			int nameMax = Math.max(8, textW - font.width(kills) - 6);
			PvDraw.text(g, font, trim(font, fam.family().name(), nameMax), rx, rowY,
				fam.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, kills, rx + textW, rowY, PvDraw.COLOR_ACCENT);
			int top = Math.max(rowY, ry);
			int hz = Math.min(rowY + STAT_ROW, ry + this.listH) - top;
			if (hz > 0) {
				this.zones.add(HoverZone.of(rx, top, textW, hz, seaCreatureTip(fam)));
			}
		}
		g.disableScissor();

		if (this.listMaxScroll > 0) {
			int contentH = rows.size() * STAT_ROW;
			int thumbH = Math.max(12, this.listH * this.listH / Math.max(1, contentH));
			int thumbY = ry + (this.listH - thumbH) * this.listScroll / this.listMaxScroll;
			PvDraw.fill(g, rx + rw - 2, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER);
		}
	}

	private static List<PvTooltip.Line> seaCreatureTip(BestiarySnapshot.FamilyProgress fam) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(fam.family().name(), PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(fam.kills()), PvDraw.COLOR_ACCENT));
		tip.add(PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(fam.deaths()), PvDraw.COLOR_TEXT));
		if (fam.tiersMax() > 0) {
			String tier = fam.maxed() ? "Maxed" : fam.tier() + " / " + fam.tiersMax();
			tip.add(PvTooltip.Line.row("Bestiary tier", PvDraw.COLOR_MUTED, tier,
				fam.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT));
		}
		return tip;
	}

	/** Sea creature families with at least one kill, most kills first. */
	static List<BestiarySnapshot.FamilyProgress> seaCreatureRows(BestiarySnapshot.CategoryProgress category) {
		if (category == null) {
			return List.of();
		}
		List<BestiarySnapshot.FamilyProgress> out = new ArrayList<>();
		for (BestiarySnapshot.FamilyProgress fam : category.families()) {
			if (fam.kills() > 0L) {
				out.add(fam);
			}
		}
		out.sort(Comparator.comparingLong(BestiarySnapshot.FamilyProgress::kills).reversed()
			.thenComparing(fam -> fam.family().name(), String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	static void drawTierBorder(GuiGraphicsExtractor g, int bx, int by, int color) {
		int t = TIER_BORDER;
		PvDraw.fill(g, bx, by, SLOT, t, color);
		PvDraw.fill(g, bx, by + SLOT - t, SLOT, t, color);
		PvDraw.fill(g, bx, by + t, t, SLOT - t * 2, color);
		PvDraw.fill(g, bx + SLOT - t, by + t, t, SLOT - t * 2, color);
	}

	static List<FishingSnapshot.TrophyRow> sortedRows(List<FishingSnapshot.TrophyRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		List<FishingSnapshot.TrophyRow> out = new ArrayList<>(rows);
		// Each key descending; chained .reversed() flipped the earlier keys too.
		out.sort(Comparator
			.comparingInt((FishingSnapshot.TrophyRow r) -> -r.counts().bestRank())
			.thenComparingInt(r -> -r.counts().discoveredTier())
			.thenComparingInt(r -> -(r.countsExact() ? r.totalCaught() : 0))
			.thenComparing(r -> r.name() == null ? "" : r.name(), String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	static void drawTrophyStatTooltip(
		GuiGraphicsExtractor g, Font font, FishingSnapshot.TrophyRow row,
		int mouseX, int mouseY, int screenW, int screenH
	) {
		String title = row.name() == null ? "" : row.name();
		String totalLabel = "Total caught";
		String totalValue;
		if (row.countsExact()) {
			totalValue = FormatUtil.commas(row.totalCaught());
		} else if (row.anyDiscovered()) {
			totalValue = "Discovered";
		} else {
			totalValue = "Undiscovered";
		}

		TrophyFishData.Tier[] tiers = TrophyFishData.Tier.topToBottom();
		String[] tierLabels = new String[tiers.length];
		String[] tierValues = new String[tiers.length];
		for (int i = 0; i < tiers.length; i++) {
			TrophyFishData.Tier tier = tiers[i];
			tierLabels[i] = tierTitle(tier);
			int count = row.counts().of(tier);
			if (count <= 0) {
				tierValues[i] = "Undiscovered";
			} else if (row.countsExact()) {
				tierValues[i] = FormatUtil.commas(count);
			} else {
				tierValues[i] = "Caught";
			}
		}

		// Only show obtain/howto text when NEU item lore has it.
		String howto = trophyHowtoFromNeu(row);

		int contentW = Math.max(font.width(PvDraw.styled(title, NAME_COLOR, true)), font.width(totalLabel) + 10 + font.width(PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true)));
		for (int i = 0; i < tiers.length; i++) {
			contentW = Math.max(contentW, font.width(tierLabels[i]) + 10 + font.width(tierValues[i]));
		}
		if (howto != null && !howto.isBlank()) {
			contentW = Math.max(contentW, font.width(howto));
		}
		contentW = Math.max(TIP_MIN_W, contentW);
		int boxW = contentW + TIP_PAD_X * 2;

		int titleH = font.lineHeight;
		int dividerGap = 3;
		int totalH = font.lineHeight + 1;
		int tierH = font.lineHeight;
		int howtoH = (howto == null || howto.isBlank()) ? 0 : font.lineHeight + TIP_ROW_GAP;
		int boxH = TIP_PAD_Y * 2
			+ titleH
			+ dividerGap + 1 + dividerGap
			+ totalH
			+ TIP_ROW_GAP
			+ tiers.length * tierH
			+ Math.max(0, tiers.length - 1) * TIP_ROW_GAP
			+ howtoH;

		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + boxW > screenW - 4) {
			x = mouseX - boxW - 8;
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4;
		}
		if (x < 4) {
			x = 4;
		}
		if (y < 4) {
			y = 4;
		}

		PvDraw.roundedFill(g, x, y, boxW, boxH, 3, TIP_BG);
		PvDraw.roundedRing(g, x, y, boxW, boxH, 3, TIP_BORDER, PvDraw.C_ALL);

		int tx = x + TIP_PAD_X;
		int ty = y + TIP_PAD_Y;
		int right = x + boxW - TIP_PAD_X;

		PvDraw.text(g, font, PvDraw.styled(title, NAME_COLOR, true), tx, ty);
		ty += titleH + dividerGap;
		PvDraw.fill(g, tx, ty, contentW, 1, TIP_BORDER);
		ty += 1 + dividerGap;

		PvDraw.text(g, font, totalLabel, tx, ty, TIP_TOTAL_LABEL);
		var totalComp = PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true);
		PvDraw.text(g, font, totalComp, right - font.width(totalComp), ty);
		ty += totalH + TIP_ROW_GAP;

		for (int i = 0; i < tiers.length; i++) {
			PvDraw.text(g, font, tierLabels[i], tx, ty, tierColor(tiers[i]));
			PvDraw.textRight(g, font, tierValues[i], right, ty, TIP_COUNT);
			ty += tierH + TIP_ROW_GAP;
		}
		if (howto != null && !howto.isBlank()) {
			PvDraw.text(g, font, howto, tx, ty, PvDraw.COLOR_MUTED);
		}
	}

	/**
	 * First NEU lore line for the best/bronze trophy item when it looks like obtain text.
	 * Returns null when unavailable, so no howto copy is invented.
	 */
	private static String trophyHowtoFromNeu(FishingSnapshot.TrophyRow row) {
		if (row == null || row.id() == null || row.id().isBlank()) {
			return null;
		}
		TrophyFishData.Tier best = row.counts() == null ? null : row.counts().bestDiscovered();
		TrophyFishData.Tier tier = best == null ? TrophyFishData.Tier.BRONZE : best;
		String iconId = row.id() + "_" + tier.suffix();
		JsonObject neu = NeuRepoCache.get(iconId);
		if (neu == null || !neu.has("lore") || !neu.get("lore").isJsonArray()) {
			return null;
		}
		List<String> lore = new ArrayList<>();
		for (JsonElement el : neu.getAsJsonArray("lore")) {
			if (el != null && el.isJsonPrimitive()) {
				lore.add(el.getAsString());
			}
		}
		return TrophyFishData.howtoFromLore(lore);
	}

	static String tierTitle(TrophyFishData.Tier tier) {
		return switch (tier) {
			case DIAMOND -> "Diamond";
			case GOLD -> "Gold";
			case SILVER -> "Silver";
			case BRONZE -> "Bronze";
		};
	}

	static int tierColor(TrophyFishData.Tier tier) {
		return switch (tier) {
			case DIAMOND -> TIER_DIAMOND;
			case GOLD -> TIER_GOLD;
			case SILVER -> TIER_SILVER;
			case BRONZE -> TIER_BRONZE;
		};
	}

	static String prettyLast(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.split("/");
		String fish = parts[0].replace('_', ' ');
		StringBuilder sb = new StringBuilder();
		for (String p : fish.split(" ")) {
			if (p.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(p.charAt(0)));
			if (p.length() > 1) {
				sb.append(p.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		if (parts.length > 1 && !parts[1].isEmpty()) {
			sb.append(" · ").append(parts[1].substring(0, 1).toUpperCase(Locale.ROOT))
				.append(parts[1].substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			this.zones.add(HoverZone.of(x, y, w, bottom - y, fishmod.pv.data.Leveling.hoverTextLines(hover)));
		}
		return bottom;
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
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

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private static String fitValue(Font font, String label, String value, int w) {
		int max = Math.max(8, w - font.width(label) - 10);
		return trim(font, value, max);
	}

	static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) + ellipsisW > maxW) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
		static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines);
		}
	}
}

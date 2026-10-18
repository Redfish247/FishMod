/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.garden.GardenUi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static fishmod.pv.gui.garden.GardenUi.*;

/** Garden Jacob contests subpage. */
public final class JacobPage {
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int leftScroll;
	private int leftMaxScroll;
	private int leftScrollTop;
	private int leftScrollH;
	private int leftScrollX;
	private int leftScrollW;
	private boolean jacobExtrasFace;
	private boolean jacobFlipTarget;
	private long jacobFlipStartMs;
	private int jacobHitX;
	private int jacobHitY;
	private int jacobHitW;
	private int jacobHitH;
	private boolean jacobHovered;

	public void render(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		this.leftScrollX = lx;
		this.leftScrollW = lw;
		this.leftScrollTop = ly;
		this.leftScrollH = h - PAD * 2;

		int contentH = measureJacobLeft(snap, font);
		this.leftMaxScroll = Math.max(0, contentH - this.leftScrollH);
		this.leftScroll = Math.min(this.leftScroll, this.leftMaxScroll);

		g.enableScissor(lx, this.leftScrollTop, lx + lw + 3, this.leftScrollTop + this.leftScrollH);
		int cy = this.leftScrollTop - this.leftScroll;

		PvDraw.text(g, font, "Unspent medals", lx, cy, PvDraw.COLOR_MUTED);
		ui.zones.add(new GardenUi.HoverZone(lx, cy, lw, font.lineHeight, List.of(
			PvTooltip.Line.title("Unspent medals", PvDraw.COLOR_TEXT),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Medals held, not yet spent at Anita.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.meta("Not lifetime medal finishes")
		)));
		cy += font.lineHeight + 3;
		GardenSnapshot.MedalCounts m = snap.medals();
		cy = GardenUi.statLine(g, font, "Bronze", FormatUtil.commas(m.bronze()), lx, cy, lw, BRONZE) + 1;
		cy = GardenUi.statLine(g, font, "Silver", FormatUtil.commas(m.silver()), lx, cy, lw, SILVER) + 1;
		cy = GardenUi.statLine(g, font, "Gold", FormatUtil.commas(m.gold()), lx, cy, lw, GOLD) + 1;
		cy = GardenUi.statLine(g, font, "Total", FormatUtil.commas(m.total()), lx, cy, lw, PvDraw.COLOR_TEXT) + 4;

		if (!snap.uniqueBrackets().isEmpty()) {
			PvDraw.text(g, font, "Unique brackets", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			for (GardenSnapshot.BracketCount b : snap.uniqueBrackets()) {
				cy = GardenUi.statLine(g, font, b.bracket(), String.valueOf(b.crops()), lx, cy, lw, PvDraw.COLOR_TEXT) + 1;
			}
			cy += 3;
		}

		List<GardenSnapshot.CropMedal> cropMedals = snap.cropMedals();
		PvDraw.text(g, font, "Crop medals", lx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 3;
		if (cropMedals.isEmpty()) {
			PvDraw.text(g, font, "None yet", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 4;
		} else {
			int rowH = Math.max(STAT_ROW, ICON + 2);
			int orbsW = 5 * ORB + 4 * ORB_GAP;
			for (GardenSnapshot.CropMedal medal : cropMedals) {
				GardenUi.drawIcon(g, medal.iconId(), lx, cy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(medal.id()));
				int textX = lx + ICON + 4;
				int nameMax = Math.max(8, lw - ICON - 8 - orbsW);
				String shown = GardenUi.trim(font, medal.name(), nameMax);
				PvDraw.text(g, font, shown, textX, cy + (rowH - font.lineHeight) / 2, PvDraw.COLOR_TEXT);
				drawMedalOrbs(g, lx + lw - orbsW, cy + (rowH - ORB) / 2, medal.filled());
				String tipMedal = switch (medal.filled()) {
					case 1 -> "Bronze";
					case 2 -> "Silver";
					case 3 -> "Gold";
					case 4 -> "Platinum";
					case 5 -> "Diamond";
					default -> "None";
				};
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(medal.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of(
					"Highest unique: " + tipMedal,
					medal.filled() <= 0 ? PvDraw.COLOR_MUTED : medalOrbColor(medal.filled() - 1)
				));
				addPlacementLines(tip, snap, medal.id());
				ui.zones.add(new GardenUi.HoverZone(lx, cy, lw, rowH, tip));
				cy += rowH + 1;
			}
			cy += 3;
		}

		if (!snap.perks().isEmpty()) {
			PvDraw.text(g, font, "Perks", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			for (Map.Entry<String, Integer> e : snap.perks().entrySet()) {
				cy = GardenUi.statLine(g, font, perkName(e.getKey()), String.valueOf(e.getValue()),
					lx, cy, lw, PvDraw.COLOR_ACCENT) + 1;
			}
		}
		g.disableScissor();

		drawJacobContestPanel(snap, ui, g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawJacobContestPanel(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		this.jacobHitX = x;
		this.jacobHitY = y;
		this.jacobHitW = w;
		this.jacobHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		this.jacobHovered = hovered;
		float flipProgress = 0F;
		boolean animating = this.jacobFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.jacobFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.jacobExtrasFace = this.jacobFlipTarget;
				this.jacobFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showExtras = animating
			? (Math.cos(angle) < 0.0 ? this.jacobFlipTarget : this.jacobExtrasFace)
			: this.jacobExtrasFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		if (showExtras) {
			drawJacobExtrasFace(snap, ui, g, font, x, y, w, h);
		} else {
			drawJacobContestsFace(snap, ui, g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawJacobContestsFace(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		PvDraw.text(g, font, "Contest history", rx, ry, PvDraw.COLOR_MUTED);
		drawFlipHint(g, font, "Click for bests", rx, ry, rw);
		ry += font.lineHeight + 3;

		String credit = Component.translatable("betterpv.garden.elite_credit").getString();
		int creditH = Math.round(font.lineHeight * CREDIT_SCALE) + 4;
		PvDraw.textScaled(g, font, credit, rx, y + h - PAD - Math.round(font.lineHeight * CREDIT_SCALE), CREDIT_C, CREDIT_SCALE);

		if (snap.contestsLoading()) {
			PvDraw.text(g, font, "Loading contests...", rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}
		if (!snap.contestsError().isBlank() && snap.contests().isEmpty()) {
			PvDraw.text(g, font, GardenUi.trim(font, snap.contestsError(), rw), rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		List<GardenSnapshot.ContestEntry> contests = snap.contests();
		int rowH = Math.max(STAT_ROW, ICON + 2);
		int listTop = ry;
		int listH = y + h - PAD - creditH - listTop;
		this.scrollTop = listTop;
		this.scrollH = Math.max(0, listH);
		this.maxScroll = Math.max(0, contests.size() * rowH - listH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, listTop, rx + rw + 3, listTop + listH);
		int yy = listTop - this.scroll;
		for (GardenSnapshot.ContestEntry c : contests) {
			if (yy + rowH >= listTop && yy < listTop + listH) {
				GardenUi.drawIcon(g, c.iconId(), rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(c.crop()));
				int textX = rx + ICON + 4;
				int textW = rw - ICON - 4;
				String medalLabel = medalDisplay(c.medal());
				String right = FormatUtil.shortXp(c.collected());
				if (!medalLabel.isBlank()) {
					right = medalLabel + "  " + right;
				}
				GardenUi.drawPair(g, font, c.cropName(), right, textX, yy + (rowH - font.lineHeight) / 2, textW,
					PvDraw.COLOR_TEXT, medalColor(c.medal()));
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(c.cropName(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of("Collected: " + FormatUtil.commas(c.collected()), PvDraw.COLOR_MUTED));
				if (hasMedalLabel(c.medal())) {
					tip.add(PvTooltip.Line.of("Medal: " + GardenUi.title(c.medal()), medalColor(c.medal())));
				} else if ("unclaimable".equalsIgnoreCase(c.medal())) {
					tip.add(PvTooltip.Line.of("Medal: Unclaimable", PvDraw.COLOR_MUTED));
				}
				if (c.placement() > 0) {
					tip.add(PvTooltip.Line.of(
						"#" + c.placement() + (c.participants() > 0 ? " / " + c.participants() : ""),
						PvDraw.COLOR_MUTED
					));
				}
				if (c.timestampSeconds() > 0L) {
					tip.add(PvTooltip.Line.of(contestWhen(c.timestampSeconds()), PvDraw.COLOR_MUTED));
				}
				ui.zones.add(new GardenUi.HoverZone(rx, yy, rw, rowH, tip));
			}
			yy += rowH;
		}
		g.disableScissor();
	}

	private void drawJacobExtrasFace(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		List<String> uniqueGolds = snap.uniqueGoldCrops();
		String goldHeader = uniqueGolds.isEmpty() ? "Unique golds" : "Unique golds (" + uniqueGolds.size() + ")";
		PvDraw.text(g, font, goldHeader, rx, ry, PvDraw.COLOR_MUTED);
		ui.zones.add(new GardenUi.HoverZone(rx, ry, font.width(goldHeader), font.lineHeight, List.of(
			PvTooltip.Line.title("Unique golds", GOLD),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Crops with a gold medal earned at least once.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.meta("Not total gold finishes or unspent medals")
		)));
		drawFlipHint(g, font, "Click for history", rx, ry, rw);
		ry += font.lineHeight + 3;

		int goldBlockH;
		if (uniqueGolds.isEmpty()) {
			PvDraw.text(g, font, "None yet", rx, ry, PvDraw.COLOR_MUTED);
			goldBlockH = font.lineHeight + 4;
		} else {
			int cols = Math.max(4, Math.min(8, (rw + 2) / (ICON + 2)));
			int cell = ICON + 2;
			for (int i = 0; i < uniqueGolds.size(); i++) {
				String cropId = uniqueGolds.get(i);
				int col = i % cols;
				int row = i / cols;
				int bx = rx + col * cell;
				int by = ry + row * cell;
				String iconId = GardenData.cropIconId(cropId);
				GardenUi.drawIcon(g, iconId, bx, by, ICON, GardenData.cropPackModel(cropId));
				ui.zones.add(new GardenUi.HoverZone(bx, by, ICON, ICON, List.of(
					PvTooltip.Line.of(GardenData.prettyCrop(cropId), GOLD),
					PvTooltip.Line.of("Unique gold medal", PvDraw.COLOR_MUTED)
				)));
			}
			int rows = (uniqueGolds.size() + cols - 1) / cols;
			goldBlockH = rows * cell + 4;
		}
		ry += goldBlockH;

		PvDraw.text(g, font, "Personal bests", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		List<GardenSnapshot.PersonalBest> pbs = snap.personalBests();
		int rowH = Math.max(STAT_ROW, ICON + 2);
		this.scrollTop = ry;
		this.scrollH = Math.max(0, bottom - ry);
		this.maxScroll = Math.max(0, pbs.size() * rowH - this.scrollH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, this.scrollTop, rx + rw + 3, this.scrollTop + this.scrollH);
		int yy = this.scrollTop - this.scroll;
		for (GardenSnapshot.PersonalBest pb : pbs) {
			if (yy + rowH >= this.scrollTop && yy < this.scrollTop + this.scrollH) {
				String iconId = GardenData.cropIconId(pb.id());
				GardenUi.drawIcon(g, iconId, rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(pb.id()));
				int textX = rx + ICON + 4;
				int textW = rw - ICON - 4;
				GardenUi.drawPair(g, font, pb.name(), FormatUtil.commas(pb.amount()), textX,
					yy + (rowH - font.lineHeight) / 2, textW, PvDraw.COLOR_TEXT, PvDraw.COLOR_ACCENT);
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(pb.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of(FormatUtil.commas(pb.amount()) + " collected", PvDraw.COLOR_MUTED));
				addPlacementLines(tip, snap, pb.id());
				ui.zones.add(new GardenUi.HoverZone(rx, yy, rw, rowH, tip));
			}
			yy += rowH;
		}
		g.disableScissor();
	}

	private void drawFlipHint(GuiGraphicsExtractor g, Font font, String hint, int rx, int ry, int rw) {
		int hintW = font.width(hint);
		PvDraw.text(g, font, hint, rx + rw - hintW, ry, this.jacobHovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED);
	}

	private static void addPlacementLines(List<PvTooltip.Line> tip, GardenSnapshot snap, String cropId) {
		if (snap.contestsLoading()) {
			tip.add(PvTooltip.Line.meta("Loading contests..."));
			return;
		}
		GardenSnapshot.CropPlacement p = snap.placement(cropId);
		if (p == null) {
			tip.add(PvTooltip.Line.meta("No contests for this crop"));
			return;
		}
		tip.add(PvTooltip.Line.blank());
		if (p.best() > 0) {
			String value = "#" + FormatUtil.commas(p.best())
				+ (p.participants() > 0 ? " / " + FormatUtil.commas(p.participants()) : "");
			tip.add(PvTooltip.Line.row("Best placement", PvDraw.COLOR_MUTED, value, PvDraw.COLOR_ACCENT));
		} else {
			tip.add(PvTooltip.Line.row("Best placement", PvDraw.COLOR_MUTED, "None", PvDraw.COLOR_MUTED));
		}
		tip.add(PvTooltip.Line.row("Contests", PvDraw.COLOR_MUTED, FormatUtil.commas(p.contests()), PvDraw.COLOR_TEXT));
		if (p.unplaced() > 0) {
			tip.add(PvTooltip.Line.meta(FormatUtil.commas(p.unplaced()) + " unclaimed contests"));
		}
	}

	private int measureJacobLeft(GardenSnapshot snap, Font font) {
		int h = font.lineHeight + 3 + STAT_ROW * 4 + 4;
		if (!snap.uniqueBrackets().isEmpty()) {
			h += font.lineHeight + 2 + snap.uniqueBrackets().size() * (STAT_ROW + 1) + 3;
		}
		h += font.lineHeight + 3;
		List<GardenSnapshot.CropMedal> medals = snap.cropMedals();
		if (medals.isEmpty()) {
			h += font.lineHeight + 4;
		} else {
			int rowH = Math.max(STAT_ROW, ICON + 2);
			h += medals.size() * (rowH + 1) + 3;
		}
		if (!snap.perks().isEmpty()) {
			h += font.lineHeight + 2 + snap.perks().size() * (STAT_ROW + 1);
		}
		return h;
	}

	private static void drawMedalOrbs(GuiGraphicsExtractor g, int x, int y, int filled) {
		for (int i = 0; i < 5; i++) {
			int ox = x + i * (ORB + ORB_GAP);
			int color = i < filled ? medalOrbColor(i) : MEDAL_ORB_EMPTY;
			drawOrb(g, ox, y, ORB, color);
		}
	}

	private static int medalOrbColor(int index) {
		return switch (index) {
			case 0 -> BRONZE;
			case 1 -> SILVER;
			case 2 -> GOLD;
			case 3 -> PLATINUM;
			case 4 -> DIAMOND;
			default -> MEDAL_ORB_EMPTY;
		};
	}

	private static void drawOrb(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
		// Odd sizes center cleanly; even sizes read as knobbly squares.
		int r = size / 2;
		float mid = (size - 1) * 0.5F;
		float r2 = r * r + 0.25F;
		int baseR = (argb >>> 16) & 0xFF;
		int baseG = (argb >>> 8) & 0xFF;
		int baseB = argb & 0xFF;
		int baseA = (argb >>> 24) & 0xFF;
		for (int dy = 0; dy < size; dy++) {
			for (int dx = 0; dx < size; dx++) {
				float cx = dx - mid;
				float cy = dy - mid;
				float dist2 = cx * cx + cy * cy;
				if (dist2 > r2) {
					continue;
				}
				// Soft sphere shade so medals read as circles, not flat squares.
				float shade = 1.0F - (cy / Math.max(1F, r)) * 0.22F;
				shade = Math.max(0.72F, Math.min(1.18F, shade));
				if (cx * cx + (cy + r * 0.35F) * (cy + r * 0.35F) < r2 * 0.22F) {
					shade = Math.min(1.28F, shade + 0.18F);
				}
				int pr = clampByte(Math.round(baseR * shade));
				int pg = clampByte(Math.round(baseG * shade));
				int pb = clampByte(Math.round(baseB * shade));
				PvDraw.fill(g, x + dx, y + dy, 1, 1, (baseA << 24) | (pr << 16) | (pg << 8) | pb);
			}
		}
	}

	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private static String contestWhen(long timestampSeconds) {
		long sec = timestampSeconds;
		if (sec > 10_000_000_000L) {
			sec = sec / 1000L;
		}
		long ago = Math.max(0L, (System.currentTimeMillis() / 1000L) - sec);
		if (ago < 60) {
			return ago + "s ago";
		}
		if (ago < 3600) {
			return (ago / 60) + "m ago";
		}
		if (ago < 86_400) {
			return (ago / 3600) + "h ago";
		}
		if (ago < 86_400L * 45L) {
			return (ago / 86_400L) + "d ago";
		}
		return (ago / (86_400L * 30L)) + "mo ago";
	}

	private static String perkName(String id) {
		if (id == null) {
			return "?";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "double_drops" -> "Double Drops";
			case "farming_level_cap" -> "Farming Cap";
			case "personal_bests" -> "Personal Bests";
			default -> GardenUi.title(id.replace('_', ' '));
		};
	}

	private static boolean hasMedalLabel(String medal) {
		if (medal == null || medal.isBlank()) {
			return false;
		}
		String m = medal.toLowerCase(Locale.ROOT);
		return !"none".equals(m) && !"unclaimable".equals(m);
	}

	private static String medalDisplay(String medal) {
		if (!hasMedalLabel(medal)) {
			return "";
		}
		return GardenUi.title(medal);
	}

	private static int medalColor(String medal) {
		if (medal == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (medal.toLowerCase(Locale.ROOT)) {
			case "bronze" -> BRONZE;
			case "silver" -> SILVER;
			case "gold" -> GOLD;
			case "platinum" -> PLATINUM;
			case "diamond" -> DIAMOND;
			case "ghost" -> GHOST;
			default -> PvDraw.COLOR_MUTED;
		};
	}

	public void resetScroll() {
		this.scroll = 0;
		this.leftScroll = 0;
	}

	public void clearScrollExtents() {
		this.maxScroll = 0;
		this.leftMaxScroll = 0;
	}

	public void resetFlip() {
		this.jacobExtrasFace = false;
		this.jacobFlipTarget = false;
		this.jacobFlipStartMs = 0L;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		boolean overLeft = this.leftMaxScroll > 0
			&& mouseX >= this.leftScrollX && mouseX < this.leftScrollX + this.leftScrollW
			&& mouseY >= this.leftScrollTop && mouseY < this.leftScrollTop + this.leftScrollH;
		if (overLeft) {
			int next = Math.max(0, Math.min(this.leftMaxScroll, this.leftScroll + (scrollY > 0 ? -14 : 14)));
			if (next != this.leftScroll) {
				this.leftScroll = next;
				return true;
			}
			return false;
		}
		if (this.maxScroll <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx < this.jacobHitX || mx >= this.jacobHitX + this.jacobHitW
			|| my < this.jacobHitY || my >= this.jacobHitY + this.jacobHitH) {
			return false;
		}
		if (this.jacobFlipStartMs != 0L) {
			return true;
		}
		this.jacobFlipTarget = !this.jacobExtrasFace;
		this.jacobFlipStartMs = System.currentTimeMillis();
		return true;
	}
}

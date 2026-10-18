/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining.page;

import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.dungeons.EssencePerkTips;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.essence.EssenceIcons;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.mining.MiningUi;
import fishmod.pv.gui.mining.MiningUi.HoverZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Glacite tunnels subtab: mineshafts, fossils, corpses, milestones / fossil essence flip. */
public final class GlacitePage {
	private static final int BAR_CORPSE = 0xFF6B9BD1;
	private static final int CORPSE_LAPIS = 0xFF5555FF;
	private static final int CORPSE_UMBER = 0xFFC4A35A;
	private static final int CORPSE_TUNGSTEN = 0xFFB0B0B8;
	private static final int CORPSE_VANGUARD = 0xFF55FFFF;
	private static final int FOSSIL_COLOR = 0xFFE8D5A3;
	private static final int COLOR_MAXED = 0xFF7CFF9A;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int FLIP_MS = 480;

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	private boolean milestonesEssenceFace;
	private long milestonesFlipStartMs;
	private boolean milestonesFlipTarget;
	private int milestonesHitX;
	private int milestonesHitY;
	private int milestonesHitW;
	private int milestonesHitH;

	public void reset() {
		this.scroll = 0;
		this.milestonesEssenceFace = false;
		this.milestonesFlipStartMs = 0L;
		this.milestonesHitW = 0;
		this.milestonesHitH = 0;
	}

	public void onEnter() {
		this.scroll = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (true) {
			return false;
		}
		if (this.milestonesHitW <= 0 || this.milestonesHitH <= 0) {
			return false;
		}
		if (mx < this.milestonesHitX || mx >= this.milestonesHitX + this.milestonesHitW
			|| my < this.milestonesHitY || my >= this.milestonesHitY + this.milestonesHitH) {
			return false;
		}
		if (this.milestonesFlipStartMs != 0L) {
			return true;
		}
		this.milestonesFlipTarget = !this.milestonesEssenceFace;
		this.milestonesFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
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

	public void render(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - MiningUi.GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + MiningUi.PAD;
		int ly = y + MiningUi.PAD;
		int lw = leftW - MiningUi.PAD * 2;

		PvDraw.text(g, font, "Glacite", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		ly = MiningUi.statLine(g, font, "Mineshafts entered", FormatUtil.commas(snapshot.mineshaftsEntered()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);
		ly = MiningUi.statLine(g, font, "Fossil dust", FormatUtil.commas(snapshot.fossilDust()),
			lx, ly, lw, MiningUi.BAR_GLACITE) + 1;

		List<String> fossils = snapshot.fossilsDonated();
		String fossilCount = String.valueOf(fossils.size());
		int fossilTop = ly;
		ly = MiningUi.statLine(g, font, "Fossils donated", fossilCount, lx, ly, lw, PvDraw.COLOR_ACCENT);
		if (!fossils.isEmpty()) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Fossils donated", PvDraw.COLOR_MUTED));
			for (String fossil : fossils) {
				tip.add(PvTooltip.Line.of(MiningUi.title(fossil), MiningUi.PLACED));
			}
			zones.add(HoverZone.of(lx, fossilTop, lw, MiningUi.STAT_ROW, tip));
		}
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		MiningSnapshot.CorpseCounts counts = snapshot.corpses();
		PvDraw.text(g, font, "Corpses looted", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = MiningUi.coloredLabelStat(g, font, "Lapis", FormatUtil.commas(counts.lapis()),
			lx, ly, lw, CORPSE_LAPIS, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Umber", FormatUtil.commas(counts.umber()),
			lx, ly, lw, CORPSE_UMBER, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Tungsten", FormatUtil.commas(counts.tungsten()),
			lx, ly, lw, CORPSE_TUNGSTEN, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Vanguard", FormatUtil.commas(counts.vanguard()),
			lx, ly, lw, CORPSE_VANGUARD, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.statLine(g, font, "Total", FormatUtil.commas(counts.total()), lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		int current = snapshot.corpseMilestone();
		String tierLabel = current <= 0 ? "None" : ("Tier " + current + (current >= 7 ? " (max)" : ""));
		MiningUi.statLine(g, font, "Milestone", tierLabel, lx, ly, lw, PvDraw.COLOR_ACCENT);

		drawMilestonesPanel(g, font, snapshot, zones, x + leftW + MiningUi.GAP, y, rightW, h, mx, my);
	}

	private void drawMilestonesPanel(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		this.milestonesHitX = x;
		this.milestonesHitY = y;
		this.milestonesHitW = w;
		this.milestonesHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.milestonesFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.milestonesFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.milestonesEssenceFace = this.milestonesFlipTarget;
				this.milestonesFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.milestonesFlipTarget : this.milestonesEssenceFace)
			: this.milestonesEssenceFace;
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
		renderCorpseMilestones(g, font, snapshot, zones, x, y, w, h, mx, my);

		g.pose().popMatrix();
	}

	private void renderCorpseMilestones(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;

		PvDraw.text(g, font, "Corpse milestones", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		MiningSnapshot.CorpseCounts counts = snapshot.corpses();
		int current = snapshot.corpseMilestone();

		int gridTop = ry;
		int gridH = y + h - MiningUi.PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;

		List<MiningSnapshot.CorpseMilestone> tiers = MiningSnapshot.CORPSE_MILESTONES;
		// Two columns side by side, no scrolling.
		int cols = 2;
		int colGap = 12;
		int colW = (rw - colGap) / cols;
		int perCol = (tiers.size() + cols - 1) / cols;
		int rowH = Math.max(MiningUi.barRowH(font), gridH / Math.max(1, perCol));
		this.maxScroll = 0;
		this.scroll = 0;

		for (int i = 0; i < tiers.size(); i++) {
			MiningSnapshot.CorpseMilestone tier = tiers.get(i);
			MiningSnapshot.CorpseMilestone prev = i == 0 ? null : tiers.get(i - 1);
			boolean done = current >= tier.tier();
			float fill = done ? 1f : tier.fill(counts, prev);
			String value = done ? "Done" : requirementShort(tier, counts);
			List<PvTooltip.Line> hover = corpseHover(tier, counts, done);
			int col = i / Math.max(1, perCol);
			int row = i % Math.max(1, perCol);
			MiningUi.drawBar(g, font, "Tier " + tier.tier(), value, fill, done, BAR_CORPSE, hover,
				rx + col * (colW + colGap), gridTop + row * rowH, colW, mx, my, zones);
		}
	}

	private void drawFossilEssenceFace(
		GuiGraphicsExtractor g, Font font, DungeonSnapshot.EssenceShop shop, List<HoverZone> zones,
		int x, int y, int w, int h
	) {
		int pad = MiningUi.PAD;
		int cx = x + pad;
		int cy = y + pad;
		int innerW = w - pad * 2;
		int bottom = y + h - pad;

		int headerH = Math.max(16, font.lineHeight + 2);
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(cy, headerH, 16, font.lineHeight);
		g.item(fossilIcon(shop.iconId()), cx, headerAlign.iconY());
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int headerLabelX = cx + 16 + 4;
		int nameMax = Math.max(8, innerW - (headerLabelX - cx) - balW - 4);
		PvDraw.textBold(g, font, MiningUi.trim(font, shop.name() + " Essence", nameMax),
			headerLabelX, headerAlign.textY(), FOSSIL_COLOR);
		PvDraw.textBold(g, font, bal, cx + innerW - balW, headerAlign.textY(), FOSSIL_COLOR);

		int ly = cy + headerH + 6;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int colGap = 10;
		int colW = Math.max(80, (innerW - colGap) / 2);
		int perkRows = Math.max(1, (perks.size() + 1) / 2);
		int availPerkH = Math.max(font.lineHeight + 2, bottom - ly);
		int rowH = Math.max(16, Math.max(font.lineHeight + 2, Math.min(20, availPerkH / perkRows)));
		for (int i = 0; i < perks.size(); i++) {
			DungeonSnapshot.EssencePerk perk = perks.get(i);
			int col = i % 2;
			int row = i / 2;
			int px = cx + col * (colW + colGap);
			int py = ly + row * rowH;
			if (py + font.lineHeight > bottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(py, rowH, 16, font.lineHeight);
			g.item(fossilPerkIcon(perk.id()), px, rowAlign.iconY());
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			int labelX = px + 16 + 3;
			String left = MiningUi.trim(font, perk.name(), Math.max(8, colW - 16 - 3 - rightW - 4));
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, labelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, px + colW, rowAlign.textY(), valueColor);
			zones.add(HoverZone.of(px, py, colW, rowH, EssencePerkTips.tip(perk)));
		}
	}

	private static String requirementShort(MiningSnapshot.CorpseMilestone tier, MiningSnapshot.CorpseCounts counts) {
		List<String> parts = new ArrayList<>();
		if (tier.needLapis() > 0) {
			parts.add(counts.lapis() + "/" + tier.needLapis() + " L");
		}
		if (tier.needUmber() > 0) {
			parts.add(counts.umber() + "/" + tier.needUmber() + " U");
		}
		if (tier.needTungsten() > 0) {
			parts.add(counts.tungsten() + "/" + tier.needTungsten() + " T");
		}
		if (tier.needVanguard() > 0) {
			parts.add(counts.vanguard() + "/" + tier.needVanguard() + " V");
		}
		return parts.isEmpty() ? "-" : String.join(" ", parts);
	}

	private static List<PvTooltip.Line> corpseHover(
		MiningSnapshot.CorpseMilestone tier, MiningSnapshot.CorpseCounts counts, boolean done
	) {
		if (done) {
			return List.of(PvTooltip.Line.of("Tier " + tier.tier() + " complete", PvDraw.COLOR_ACCENT));
		}
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Tier " + tier.tier() + " needs", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		addCorpseNeedRow(tip, "Lapis", counts.lapis(), tier.needLapis(), CORPSE_LAPIS);
		addCorpseNeedRow(tip, "Umber", counts.umber(), tier.needUmber(), CORPSE_UMBER);
		addCorpseNeedRow(tip, "Tungsten", counts.tungsten(), tier.needTungsten(), CORPSE_TUNGSTEN);
		addCorpseNeedRow(tip, "Vanguard", counts.vanguard(), tier.needVanguard(), CORPSE_VANGUARD);
		return tip;
	}

	private static void addCorpseNeedRow(
		List<PvTooltip.Line> tip, String label, long have, long need, int color
	) {
		if (need <= 0L) {
			return;
		}
		boolean met = have >= need;
		tip.add(PvTooltip.Line.row(
			label, color,
			have + "/" + need,
			met ? 0xFF55FF55 : PvDraw.COLOR_TEXT
		));
	}

	private static ItemStack fossilIcon(String id) {
		ItemStack stack = SkyBlockItemFactory.iconStack(id == null ? "ESSENCE_FOSSIL" : id);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.BONE) : stack;
	}

	private static ItemStack fossilPerkIcon(String perkId) {
		return EssenceIcons.perk(perkId);
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining.page;

import fishmod.pv.data.ColeWeight;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.dungeons.EssencePerkTips;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.essence.EssenceIcons;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.mining.MiningUi;
import fishmod.pv.gui.mining.MiningUi.HoverZone;
import fishmod.pv.gui.overview.SkillStatRows;
import fishmod.pv.gui.overview.SummaryCards;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MiningOverviewPage {
	private static final int CRYSTAL_ICON = 14;
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int GOLD_COLOR = 0xFFFFAA00;
	private static final int DIAMOND_COLOR = 0xFF55FFFF;
	private static final int ESSENCE_HEADER_ICON = 16;
	private static final int ESSENCE_PERK_ICON = 10;
	private static final int COLOR_MAXED = 0xFF7CFF9A;
	private static final String[] SKILL_STATS = { "mining_speed", "mining_fortune", "pristine", "mining_spread" };
	private static final List<String> STAT_EXCLUSIONS = List.of(
		"Excludes held tool, set bonuses,",
		"temporary buffs and location bonuses."
	);

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	private boolean crystalsEssenceFace;
	private long crystalsFlipStartMs;
	private boolean crystalsFlipTarget;
	private int crystalsHitX;
	private int crystalsHitY;
	private int crystalsHitW;
	private int crystalsHitH;

	/** Profile apply: HEAD only zeroed shared scroll, not the crystals flip face. */
	public void resetScroll() {
		this.scroll = 0;
	}

	public void onEnter() {
		this.scroll = 0;
	}

	public void leaveOverview() {
		this.crystalsEssenceFace = false;
		this.crystalsFlipStartMs = 0L;
		this.crystalsHitW = 0;
		this.crystalsHitH = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.crystalsHitW <= 0 || this.crystalsHitH <= 0) {
			return false;
		}
		if (mx < this.crystalsHitX || mx >= this.crystalsHitX + this.crystalsHitW
			|| my < this.crystalsHitY || my >= this.crystalsHitY + this.crystalsHitH) {
			return false;
		}
		if (this.crystalsFlipStartMs != 0L) {
			return true;
		}
		this.crystalsFlipTarget = !this.crystalsEssenceFace;
		this.crystalsFlipStartMs = System.currentTimeMillis();
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
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, PlayerStatsSnapshot stats, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - MiningUi.GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + MiningUi.PAD;
		int ly = y + MiningUi.PAD;
		int lw = leftW - MiningUi.PAD * 2;

		ly = MiningUi.drawBar(g, font, "HOTM", String.valueOf(snapshot.hotmLevel()),
			snapshot.hotmFill(), snapshot.hotmMaxed(), MiningUi.BAR_HOTM,
			snapshot.hotmHover(), lx, ly, lw, mx, my, zones) + MiningUi.BAR_AFTER;
		ly = MiningUi.drawBar(g, font, "Mining", String.valueOf(snapshot.miningLevel()),
			snapshot.miningFill(), snapshot.miningMaxed(), MiningUi.BAR_MINING,
			snapshot.miningHover(), lx, ly, lw, mx, my, zones) + MiningUi.BAR_AFTER + 2;

		ColeWeight.Result cw = snapshot.coleWeight();
		String cwValue = FormatUtil.oneDecimal(cw.total());
		int cwTop = ly;
		ly = MiningUi.statLine(g, font, "ColeWeight", cwValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 2;
		zones.add(HoverZone.coleWeight(lx, cwTop, lw, MiningUi.STAT_ROW));

		ly = MiningUi.drawPowdersBlock(g, font, snapshot, lx, ly, lw, zones);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		ly = MiningUi.statLine(g, font, "Tokens spent", String.valueOf(snapshot.tokensSpent()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.statLine(g, font, "Ability", MiningUi.trim(font, snapshot.selectedAbilityName(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = MiningUi.statLine(g, font, "Sky Mall", MiningUi.trim(font, snapshot.skyMallEffect(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		if (snapshot.commissionMilestone() > 0) {
			ly = MiningUi.statLine(g, font, "Commissions", "Tier " + snapshot.commissionMilestone(),
				lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		}

		if (snapshot.miningFiestaOres() > 0L) {
			ly = MiningUi.statLine(g, font, "Fiesta ores",
				FormatUtil.commas(snapshot.miningFiestaOres()), lx, ly, lw, PvDraw.COLOR_GOLD);
		}

		int bottom = y + h - MiningUi.PAD;
		if (ly + MiningUi.STAT_ROW * 2 <= bottom) {
			ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);
			for (String id : SKILL_STATS) {
				if (ly + MiningUi.STAT_ROW > bottom) {
					break;
				}
				SummaryCards.Stat stat = SkillStatRows.row(stats, id, STAT_EXCLUSIONS);
				zones.add(HoverZone.of(lx, ly, lw, MiningUi.STAT_ROW, stat.tip()));
				ly = MiningUi.coloredLabelStat(g, font, stat.label(), stat.value(), lx, ly, lw,
					stat.labelColor(), stat.valueColor()) + 1;
			}
		}

		drawCrystalsPanel(g, font, snapshot, zones, x + leftW + MiningUi.GAP, y, rightW, h, mx, my);
	}

	private void drawCrystalsPanel(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		this.crystalsHitX = x;
		this.crystalsHitY = y;
		this.crystalsHitW = w;
		this.crystalsHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.crystalsFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.crystalsFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.crystalsEssenceFace = this.crystalsFlipTarget;
				this.crystalsFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.crystalsFlipTarget : this.crystalsEssenceFace)
			: this.crystalsEssenceFace;
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

		if (showEssence) {
			this.maxScroll = 0;
			drawMiningEssenceFace(g, font, snapshot, zones, x, y, w, h);
		} else {
			drawCrystalsFace(g, font, snapshot, zones, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawCrystalsFace(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;
		int bottom = y + h - MiningUi.PAD;
		PvDraw.text(g, font, "Crystals", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;
		List<MiningSnapshot.Crystal> crystals = snapshot.crystals();
		if (crystals.isEmpty()) {
			PvDraw.text(g, font, "No crystal data", rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		int cols = 2;
		int colGap = 10;
		int colW = Math.max(40, (rw - colGap * (cols - 1)) / cols);
		int cellH = Math.max(MiningUi.STAT_ROW, CRYSTAL_ICON) + 4;
		int rows = (crystals.size() + cols - 1) / cols;
		int gridH = rows * cellH;

		boolean hollows = snapshot.goblinKingQuests() > 0
			|| snapshot.jungleTempleOpen()
			|| snapshot.precursorTalked();
		int hollowsReserve = hollows
			? (snapshot.goblinKingQuests() > 0 ? MiningUi.STAT_ROW + 1 : 0)
				+ MiningUi.STAT_ROW + 1
				+ MiningUi.STAT_ROW
				+ 6
			: 0;

		this.scrollTop = ry;
		this.scrollH = Math.max(0, bottom - ry - MiningUi.STAT_ROW - 8 - hollowsReserve);
		this.maxScroll = Math.max(0, gridH - this.scrollH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, this.scrollTop, rx + rw + 3, this.scrollTop + this.scrollH);
		for (int i = 0; i < crystals.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cx = rx + col * (colW + colGap);
			int cy = ry + row * cellH - this.scroll;
			drawCrystalCell(g, font, crystals.get(i), cx, cy, colW, cellH);
		}
		g.disableScissor();

		int nucY = this.maxScroll <= 0
			? ry + gridH + 8
			: this.scrollTop + this.scrollH + 6;
		long derived = snapshot.nucleusRuns();
		long api = snapshot.apiNucleusRuns();
		MiningUi.statLine(g, font, "Nucleus runs", FormatUtil.commas(derived),
			rx, nucY, rw, PvDraw.COLOR_ACCENT);
		if (api > 0L || derived > 0L) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Nucleus runs", PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("From crystals", PvDraw.COLOR_MUTED, FormatUtil.commas(derived), PvDraw.COLOR_ACCENT));
			if (api > 0L) {
				tip.add(PvTooltip.Line.row("API", PvDraw.COLOR_MUTED, FormatUtil.commas(api), PvDraw.COLOR_GOLD));
			}
			zones.add(HoverZone.of(rx, nucY, rw, MiningUi.STAT_ROW, tip));
		}

		if (hollows) {
			int hy = nucY + MiningUi.STAT_ROW + 6;
			if (snapshot.goblinKingQuests() > 0) {
				hy = MiningUi.statLine(g, font, "Goblin King quests",
					String.valueOf(snapshot.goblinKingQuests()), rx, hy, rw, PvDraw.COLOR_TEXT) + 1;
			}
			hy = MiningUi.statLine(g, font, "Jungle Temple",
				snapshot.jungleTempleOpen() ? "Open" : "Closed",
				rx, hy, rw, snapshot.jungleTempleOpen() ? MiningUi.PLACED : PvDraw.COLOR_MUTED) + 1;
			MiningUi.statLine(g, font, "Precursor",
				snapshot.precursorTalked() ? "Yes" : "No",
				rx, hy, rw, snapshot.precursorTalked() ? MiningUi.PLACED : PvDraw.COLOR_MUTED);
		}
	}

	/** Gold / Diamond essence shops stacked full-width (names fit; rows fill each half). */
	private void drawMiningEssenceFace(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones, int x, int y, int w, int h
	) {
		int pad = 6;
		int cx = x + pad;
		int cy = y + pad;
		int innerW = w - pad * 2;
		int bottom = y + h - pad;
		int innerH = Math.max(0, bottom - cy);
		int sectionGap = 8;
		int halfH = Math.max(40, (innerH - sectionGap) / 2);

		int headerH = Math.max(ESSENCE_HEADER_ICON, font.lineHeight + 2);
		int goldTop = cy;
		int goldBottom = cy + halfH;
		int diamondTop = goldBottom + sectionGap;
		int diamondBottom = bottom;

		int goldPerks = Math.max(1, snapshot.goldShop().perks().size());
		int diamondPerks = Math.max(1, snapshot.diamondShop().perks().size());
		int goldBody = Math.max(0, goldBottom - goldTop - headerH - 4);
		int diamondBody = Math.max(0, diamondBottom - diamondTop - headerH - 4);
		int goldRowH = Math.max(font.lineHeight + 2, Math.max(ESSENCE_PERK_ICON + 3, goldBody / goldPerks));
		int diamondRowH = Math.max(font.lineHeight + 2, Math.max(ESSENCE_PERK_ICON + 3, diamondBody / diamondPerks));

		drawMiningEssenceColumn(
			g, font, snapshot.goldShop(), zones, cx, goldTop, innerW, headerH, goldRowH, goldBottom, GOLD_COLOR
		);

		int sepY = goldBottom + sectionGap / 2;
		PvDraw.fill(g, cx, sepY, innerW, 1, PvDraw.COLOR_DIVIDER);

		drawMiningEssenceColumn(
			g, font, snapshot.diamondShop(), zones, cx, diamondTop, innerW, headerH, diamondRowH, diamondBottom, DIAMOND_COLOR
		);
	}

	private void drawMiningEssenceColumn(
		GuiGraphicsExtractor g,
		Font font,
		DungeonSnapshot.EssenceShop shop,
		List<HoverZone> zones,
		int x,
		int y,
		int w,
		int headerH,
		int rowH,
		int bottom,
		int headerColor
	) {
		int headerIcon = ESSENCE_HEADER_ICON;
		int perkIcon = ESSENCE_PERK_ICON;
		int gap = 3;
		int headerLabelX = x + headerIcon + gap;
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(y, headerH, headerIcon, font.lineHeight);

		MiningUi.drawItemIcon(g, essenceIcon(shop.iconId()), x, headerAlign.iconY(), headerIcon);
		String name = shop.name();
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int nameMax = Math.max(8, w - (headerLabelX - x) - balW - 4);
		PvDraw.textBold(g, font, MiningUi.trim(font, name, nameMax), headerLabelX, headerAlign.textY(), headerColor);
		PvDraw.textBold(g, font, bal, x + w - balW, headerAlign.textY(), headerColor);

		int perkLabelX = x + perkIcon + gap;
		int ly = y + headerH + 4;
		for (DungeonSnapshot.EssencePerk perk : shop.perks()) {
			if (ly + font.lineHeight > bottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(ly, rowH, perkIcon, font.lineHeight);
			MiningUi.drawItemIcon(g, miningEssencePerkIcon(perk.id()), x, rowAlign.iconY(), perkIcon);
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			String left = MiningUi.trim(font, perk.name(), Math.max(8, w - (perkLabelX - x) - rightW - 4));
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, perkLabelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, rowAlign.textY(), valueColor);
			zones.add(HoverZone.of(x, ly, w, rowH, EssencePerkTips.tip(perk)));
			ly += rowH;
		}
	}

	private static ItemStack essenceIcon(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.PLAYER_HEAD) : stack;
	}

	private static ItemStack miningEssencePerkIcon(String perkId) {
		return EssenceIcons.perk(perkId);
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F
			? 4F * t * t * t
			: 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void drawCrystalCell(
		GuiGraphicsExtractor g, Font font, MiningSnapshot.Crystal c, int x, int y, int w, int cellH
	) {
		boolean collected = c.found();
		int nameColor = collected ? crystalColor(c.id()) : mixMuted(crystalColor(c.id()));
		int stateColor = c.placed() ? MiningUi.PLACED : (c.found() ? MiningUi.FOUND : MiningUi.MISSING);
		String state = prettyState(c.state());

		int iconY = y + Math.max(0, (cellH - CRYSTAL_ICON) / 2);
		drawCrystalIcon(g, c.id(), x, iconY, CRYSTAL_ICON);

		int textX = x + CRYSTAL_ICON + 4;
		int textW = Math.max(8, w - CRYSTAL_ICON - 4);
		int textY = y + Math.max(0, (cellH - font.lineHeight) / 2);
		String name = MiningUi.trim(font, c.name(), Math.max(8, textW - font.width(state) - 6));
		PvDraw.text(g, font, name, textX, textY, nameColor);
		PvDraw.textRight(g, font, state, textX + textW, textY, stateColor);
	}

	/** Flawless gemstone player-head cubes (classic NBT skulls). */
	private static void drawCrystalIcon(GuiGraphicsExtractor g, String crystalApiId, int x, int y, int size) {
		ItemStack icon = SkyBlockItemFactory.gemstoneHead(crystalApiId);
		if (icon == null || icon.isEmpty()) {
			icon = new ItemStack(Items.PLAYER_HEAD);
		}
		if (size == 16) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private static int crystalColor(String apiId) {
		String id = apiId == null ? "" : apiId.toLowerCase(Locale.ROOT);
		if (id.contains("jade")) {
			return 0xFF55FF55;
		}
		if (id.contains("amber")) {
			return 0xFFFFAA00;
		}
		if (id.contains("amethyst")) {
			return 0xFFCC66FF;
		}
		if (id.contains("sapphire")) {
			return 0xFF55FFFF;
		}
		if (id.contains("topaz")) {
			return 0xFFFFE55A;
		}
		if (id.contains("jasper")) {
			return 0xFFFF55FF;
		}
		if (id.contains("ruby")) {
			return 0xFFFF5555;
		}
		if (id.contains("aquamarine")) {
			return 0xFF5599FF;
		}
		if (id.contains("citrine")) {
			return 0xFFFF5555;
		}
		if (id.contains("peridot")) {
			return 0xFF88FF44;
		}
		if (id.contains("onyx")) {
			return 0xFFB0B0B8;
		}
		if (id.contains("opal")) {
			return 0xFFE8E8F0;
		}
		return PvDraw.COLOR_ACCENT;
	}

	private static int mixMuted(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		r = (r + 0x9A) / 2;
		g = (g + 0x9A) / 2;
		b = (b + 0xAC) / 2;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static String prettyState(String state) {
		if (state == null || state.isBlank()) {
			return "-";
		}
		return switch (state.toUpperCase(Locale.ROOT)) {
			case "PLACED" -> "Placed";
			case "FOUND" -> "Found";
			case "NOT_FOUND" -> "Missing";
			default -> MiningUi.title(state);
		};
	}
}

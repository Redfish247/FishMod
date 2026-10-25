/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import static fishmod.pv.gui.foraging.ForagingUi.BAR_AFTER;
import static fishmod.pv.gui.foraging.ForagingUi.BAR_FORAGE;
import static fishmod.pv.gui.foraging.ForagingUi.COLOR_MAXED;
import static fishmod.pv.gui.foraging.ForagingUi.ENABLED;
import static fishmod.pv.gui.foraging.ForagingUi.FLIP_MS;
import static fishmod.pv.gui.foraging.ForagingUi.FOREST_COLOR;
import static fishmod.pv.gui.foraging.ForagingUi.GAP;
import static fishmod.pv.gui.foraging.ForagingUi.ICON;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.PAD;
import static fishmod.pv.gui.foraging.ForagingUi.PANEL_HOVER;
import static fishmod.pv.gui.foraging.ForagingUi.SEP_GAP;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT_GAP;
import static fishmod.pv.gui.foraging.ForagingUi.STAT_ROW;

import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FishFamilyData;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.dungeons.EssencePerkTips;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.essence.EssenceIcons;
import fishmod.pv.gui.foraging.ForagingUi;
import fishmod.pv.gui.foraging.ForagingUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Foraging overview subtab (left stats + flip panel). */
public final class ForagingOverviewPage {
	private final List<HoverZone> zones = new ArrayList<>();

	private boolean rightEssenceFace;
	private long rightFlipStartMs;
	private boolean rightFlipTarget;
	private int rightHitX;
	private int rightHitY;
	private int rightHitW;
	private int rightHitH;

	public void reset() {
		this.zones.clear();
		this.rightEssenceFace = false;
		this.rightFlipStartMs = 0L;
		this.rightHitW = 0;
	}

	public void onLeave() {
		this.rightEssenceFace = false;
		this.rightFlipStartMs = 0L;
		this.rightHitW = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (true) {
			return false;
		}
		if (ForagingUi.hit(mx, my, this.rightHitX, this.rightHitY, this.rightHitW, this.rightHitH)) {
			if (this.rightFlipStartMs != 0L) {
				return true;
			}
			this.rightFlipTarget = !this.rightEssenceFace;
			this.rightFlipStartMs = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		drawOverviewLeft(g, font, snapshot, x, y, leftW, h, mx, my);
		drawRightFlipPanel(g, font, snapshot, x + leftW + GAP, y, rightW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawRightFlipPanel(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.rightHitX = x;
		this.rightHitY = y;
		this.rightHitW = w;
		this.rightHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.rightFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.rightFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.rightEssenceFace = this.rightFlipTarget;
				this.rightFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? ForagingUi.easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.rightFlipTarget : this.rightEssenceFace)
			: this.rightEssenceFace;
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
		drawContestsFace(g, font, snapshot, x, y, w, h);

		g.pose().popMatrix();
	}

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = ForagingUi.drawBar(g, font, "Foraging", String.valueOf(snapshot.foragingLevel()),
			snapshot.foragingFill(), snapshot.foragingMaxed(), BAR_FORAGE,
			snapshot.foragingHover(), lx, ly, lw, this.zones) + BAR_AFTER + 2;

		ly = ForagingUi.statLine(g, font, "Park race PB", ForagingUi.formatRaceMs(snapshot.raceBestMs()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);

		PvDraw.text(g, font, "Fish Family", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Set<String> discovered = new HashSet<>();
		for (String id : snapshot.fishFamily()) {
			if (id != null && !id.isBlank()) {
				discovered.add(id.toUpperCase(Locale.ROOT));
			}
		}
		List<String> catalog = new ArrayList<>(FishFamilyData.all());
		Set<String> known = new HashSet<>();
		for (String id : catalog) {
			known.add(id.toUpperCase(Locale.ROOT));
		}
		for (String id : discovered) {
			if (known.add(id)) {
				catalog.add(id);
			}
		}
		int cols = Math.max(1, Math.min(catalog.size(), Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP))));
		for (int i = 0; i < catalog.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + row * (SLOT + SLOT_GAP);
			String id = catalog.get(i);
			boolean found = discovered.contains(id.toUpperCase(Locale.ROOT));
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.roundedFill(g, bx, by, SLOT, SLOT, 3, ITEM_SLOT_BG);
			PvDraw.roundedRing(g, bx, by, SLOT, SLOT, 3, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER, PvDraw.C_ALL);
			if (found) {
				ForagingUi.drawFishFamilyIcon(g, id, bx + 1, by + 1);
			} else {
				g.item(new ItemStack(fishmod.compat.CI.GRAY_DYE), bx + 1, by + 1);
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, List.of(
				PvTooltip.Line.of(FishFamilyData.displayName(id), found ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED),
				PvTooltip.Line.of(found ? "Shown to Coral" : "Undiscovered", PvDraw.COLOR_MUTED)
			)));
		}
		int rows = (catalog.size() + cols - 1) / cols;
		ly += rows * SLOT + Math.max(0, rows - 1) * SLOT_GAP + 6;

		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		ly = ForagingUi.statLine(g, font, "Hina Chapter", String.valueOf(snapshot.hinaTier()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		if (snapshot.hasGalateaBeacon()) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			ly = ForagingUi.statLine(g, font, "Galatea Beacon",
				snapshot.galateaBeacon() + " / 10",
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}

		ForagingSnapshot.HoneyInfo honey = snapshot.honey();
		if (honey != null && honey.present()) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Honey", lx, ly, PvDraw.COLOR_MUTED);
			ly += ForagingUi.STAT_ROW;
			if (honey.smearedTrees() > 0) {
				int rowY = ly;
				ly = ForagingUi.statLine(g, font, "Smeared trees", String.valueOf(honey.smearedTrees()),
					lx, ly, lw, PvDraw.COLOR_ACCENT);
				if (!honey.treeNames().isEmpty()) {
					List<PvTooltip.Line> tip = new ArrayList<>();
					tip.add(PvTooltip.Line.title("Smeared trees", PvDraw.COLOR_ACCENT));
					tip.add(PvTooltip.Line.divider());
					int shown = 0;
					for (String name : honey.treeNames()) {
						if (shown >= 12) {
							tip.add(PvTooltip.Line.meta("+" + (honey.treeNames().size() - shown) + " more"));
							break;
						}
						tip.add(PvTooltip.Line.plain(name));
						shown++;
					}
					this.zones.add(HoverZone.of(lx, rowY, lw, ForagingUi.STAT_ROW, tip));
				}
			}
			if (honey.refills() > 0) {
				ly = ForagingUi.statLine(g, font, "Refills", String.valueOf(honey.refills()),
					lx, ly, lw, PvDraw.COLOR_GOLD);
			}
		}

		int bottom = y + h - PAD;
		// Header + talisman + one row of songs must fit, otherwise skip rather than spill off the panel.
		int needed = ForagingUi.SEP_GAP + font.lineHeight * 2 + 6 + ICON + 3;
		if (ly + needed <= bottom) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			drawMelodySection(g, font, snapshot, lx, ly, lw, bottom);
		}
	}


	private void drawMelodySection(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int lx, int ly, int lw, int bottom
	) {
		PvDraw.text(g, font, "Melodies Harp", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 2;
		ly = ForagingUi.statLine(g, font, "Talisman", snapshot.harpTalisman() ? "Claimed" : "Unclaimed",
			lx, ly, lw, snapshot.harpTalisman() ? ENABLED : PvDraw.COLOR_MUTED) + 2;

		List<ForagingSnapshot.HarpSong> songs = snapshot.harpSongs();
		if (songs.isEmpty()) {
			PvDraw.text(g, font, "No songs", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}

		int availH = Math.max(font.lineHeight, bottom - ly);
		int cellW = ICON + 4 + font.width("100%") + 2;
		int cols = Math.max(2, Math.min(songs.size(), Math.max(1, lw / Math.max(1, cellW + 2))));
		int rowsNeeded = (songs.size() + cols - 1) / cols;
		int cellH = Math.max(ICON + 2, Math.min(ICON + 4, availH / Math.max(1, rowsNeeded)));
		int gap = Math.max(1, Math.min(3, (availH - rowsNeeded * cellH) / Math.max(1, rowsNeeded)));

		ItemStack book = new ItemStack(Items.BOOK);
		String selected = snapshot.harpSelected();
		for (int i = 0; i < songs.size(); i++) {
			ForagingSnapshot.HarpSong song = songs.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (cellW + 2);
			int by = ly + row * (cellH + gap);
			if (by + ICON > bottom) {
				break;
			}
			double pct = song.best() <= 1.0 ? song.best() * 100.0 : song.best();
			boolean isSelected = selected != null && !selected.isBlank()
				&& selected.equalsIgnoreCase(song.id());
			g.item(book, bx, by);
			String pctText = Math.round(pct) + "%";
			PvDraw.text(g, font, pctText, bx + ICON + 2, by + Math.max(0, (ICON - font.lineHeight) / 2),
				isSelected ? ENABLED : PvDraw.COLOR_ACCENT);
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(song.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Best", PvDraw.COLOR_MUTED,
				FormatUtil.oneDecimal(pct) + "%", isSelected ? ENABLED : PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.row("Completions", PvDraw.COLOR_MUTED,
				String.valueOf(song.completions()), PvDraw.COLOR_MUTED));
			tip.add(PvTooltip.Line.row("Perfects", PvDraw.COLOR_MUTED,
				String.valueOf(song.perfects()), PvDraw.COLOR_GOLD));
			if (isSelected) {
				tip.add(PvTooltip.Line.meta("Selected"));
			}
			this.zones.add(HoverZone.of(bx, by, cellW, cellH, tip));
		}
	}

	private void drawContestsFace(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Tree gifts", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.TreeGift> gifts = snapshot.treeGifts();
		if (gifts.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.TreeGift gift : gifts) {
				String right = FormatUtil.commas(gift.count())
					+ (gift.milestoneTier() > 0 ? " · T" + gift.milestoneTier() : "");
				ry = ForagingUi.statLine(g, font, gift.name(), right, rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
			}
			ry += 2;
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Starlyn PBs", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.StarlynBest> bests = snapshot.starlynBests();
		if (bests.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.StarlynBest best : bests) {
				ry = ForagingUi.statLine(g, font, best.name(), FormatUtil.commas(best.amount()),
					rx, ry, rw, PvDraw.COLOR_GOLD) + 1;
			}
			ry += 2;
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.WhisperPool> pools = snapshot.whisperPools();
		if (pools.isEmpty()) {
			ForagingUi.statLine(g, font, "Total", "-", rx, ry, rw, PvDraw.COLOR_MUTED);
		} else {
			for (ForagingSnapshot.WhisperPool pool : pools) {
				int poolColor = whisperPoolColor(pool.id());
				this.zones.add(HoverZone.of(rx, ry, rw, STAT_ROW, HotfPage.whisperTip(pool, poolColor)));
				ry = ForagingUi.statLine(g, font, pool.label(), HotfPage.whisperValue(pool), rx, ry, rw, poolColor) + 1;
			}
		}
	}

	private static int whisperPoolColor(String id) {
		if (id == null) {
			return PvDraw.COLOR_ACCENT;
		}
		return switch (id.toLowerCase(java.util.Locale.ROOT)) {
			case "forest" -> 0xFF55C878;
			case "desert" -> 0xFFE8A838;
			default -> PvDraw.COLOR_ACCENT;
		};
	}

	private static ItemStack essenceIcon(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.FLOWERING_AZALEA_LEAVES) : stack;
	}

	private static ItemStack forestPerkIcon(String perkId) {
		return EssenceIcons.perk(perkId);
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift.page;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.rift.RiftUi;
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import static fishmod.pv.gui.rift.RiftUi.*;

/** Rift overview: progress bars + timecharms / zone unlocks. */
public final class RiftOverviewPage {
	private static final int REGION_GAP = 6;

	private int questScroll;
	private int questMaxScroll;
	private int questViewX;
	private int questViewY;
	private int questViewW;
	private int questViewH;

	private int leftHitX;
	private int leftHitY;
	private int leftHitW;
	private int leftHitH;
	private boolean leftQuestFace;
	private boolean leftFlipTarget;
	private long leftFlipStartMs;

	public void resetScroll() {
		this.questScroll = 0;
		this.leftQuestFace = false;
		this.leftFlipTarget = false;
		this.leftFlipStartMs = 0L;
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx < this.leftHitX || mx >= this.leftHitX + this.leftHitW
			|| my < this.leftHitY || my >= this.leftHitY + this.leftHitH) {
			return false;
		}
		if (this.leftFlipStartMs != 0L) {
			return true;
		}
		this.leftFlipTarget = !this.leftQuestFace;
		this.leftFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!showingQuestFace() || this.questMaxScroll <= 0) {
			return false;
		}
		if (mouseX < this.questViewX || mouseX >= this.questViewX + this.questViewW
			|| mouseY < this.questViewY || mouseY >= this.questViewY + this.questViewH) {
			return false;
		}
		int next = Math.max(0, Math.min(
			this.questMaxScroll,
			this.questScroll - (int) Math.round(scrollY * 12)
		));
		if (next == this.questScroll) {
			return false;
		}
		this.questScroll = next;
		return true;
	}

	public void render(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 48 / 100);
		int leftW = w - rightW - GAP;
		drawOverviewLeftFlip(snapshot, ui, g, font, x, y, leftW, h, mx, my);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawOverviewRight(snapshot, ui, g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private boolean showingQuestFace() {
		if (this.leftFlipStartMs == 0L) {
			return this.leftQuestFace;
		}
		float progress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
		float angle = easeInOutCubic(progress) * (float) Math.PI;
		return Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftQuestFace;
	}

	private void drawOverviewLeftFlip(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.leftHitX = x;
		this.leftHitY = y;
		this.leftHitW = w;
		this.leftHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.leftFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.leftQuestFace = this.leftFlipTarget;
				this.leftFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showQuests = animating
			? (Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftQuestFace)
			: this.leftQuestFace;
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

		if (showQuests) {
			drawQuestFace(snapshot, g, font, x, y, w, h);
		} else {
			this.questMaxScroll = 0;
			this.questViewH = 0;
			drawStatsFace(snapshot, ui, g, font, x, y, w, h, mx, my);
		}

		g.pose().popMatrix();
	}

	private void drawStatsFace(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = RiftUi.statLine(g, font, "Motes", FormatUtil.commas(snapshot.motesPurse()),
			lx, ly, lw, MOTES_COLOR) + 2;
		ly = RiftUi.statLine(g, font, "Lifetime motes", FormatUtil.commas(snapshot.lifetimeMotes()),
			lx, ly, lw, PvDraw.COLOR_MUTED);
		if (snapshot.visits() > 0) {
			ly = RiftUi.statLine(g, font, "Visits", FormatUtil.commas(snapshot.visits()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		if (snapshot.lastFreeAccessMs() > 0L) {
			ly = RiftUi.statLine(g, font, "Last free entry", FormatUtil.ago(snapshot.lastFreeAccessMs()),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);

		RiftSnapshot.VampireProgress vamp = snapshot.vampire();
		ly = ui.drawLabeledBar(g, font, "Vampire", "T" + vamp.level(),
			vamp.fill(), vamp.maxed(), PvDraw.COLOR_BAR_FILL_SLAYER, vamp.hover(),
			lx, ly, lw, mx, my) + BAR_AFTER;

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);

		String enigmaTip = "Found " + snapshot.enigmaFound() + " / " + RiftSnapshot.ENIGMA_MAX
			+ (snapshot.enigmaCloakBought() ? " · Cloak owned" : " · Cloak not bought");
		ly = ui.drawLabeledBar(g, font, "Enigma souls",
			snapshot.enigmaFound() + "/" + RiftSnapshot.ENIGMA_MAX,
			snapshot.enigmaFill(), snapshot.enigmaFound() >= RiftSnapshot.ENIGMA_MAX,
			ENIGMA_COLOR, enigmaTip, lx, ly, lw, mx, my) + BAR_AFTER;

		String charmTip = snapshot.timecharmsSecured() + " / " + RiftSnapshot.TIMECHARM_MAX
			+ " secured in the gallery";
		ly = ui.drawLabeledBar(g, font, "Timecharms",
			snapshot.timecharmsSecured() + "/" + RiftSnapshot.TIMECHARM_MAX,
			snapshot.timecharmFill(), snapshot.timecharmsSecured() >= RiftSnapshot.TIMECHARM_MAX,
			TIMECHARM_COLOR, charmTip, lx, ly, lw, mx, my) + BAR_AFTER;

		ly = ui.drawLabeledBar(g, font, "McGrubber's burgers",
			snapshot.burgers() + "/" + RiftSnapshot.BURGER_MAX,
			snapshot.burgerFill(), snapshot.burgers() >= RiftSnapshot.BURGER_MAX,
			BURGER_COLOR, "Grubber stacks from Castle burgers", lx, ly, lw, mx, my) + BAR_AFTER;

		String catTip = snapshot.montezumaUnlocked()
			? "Montezuma unlocked"
				+ (snapshot.montezumaTier().isBlank() ? "" : " · " + RiftUi.prettyTier(snapshot.montezumaTier()))
			: "Montezuma not unlocked";
		ly = ui.drawLabeledBar(g, font, "Montezuma's Souls",
			snapshot.catsFound() + "/" + RiftSnapshot.MONTEZUMA_CATS_MAX,
			snapshot.catsFill(), snapshot.catsFound() >= RiftSnapshot.MONTEZUMA_CATS_MAX,
			CAT_COLOR, catTip, lx, ly, lw, mx, my) + BAR_AFTER;

		ui.drawLabeledBar(g, font, "Porhtal eyes",
			snapshot.eyesKilled() + "/" + RiftSnapshot.EYES_MAX,
			snapshot.eyesFill(), snapshot.eyesKilled() >= RiftSnapshot.EYES_MAX,
			EYE_COLOR, "Rogue eyes calmed for Porhtal", lx, ly, lw, mx, my);
	}

	private void drawQuestFace(
		RiftSnapshot snapshot, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Quest progress", lx, ly, ZONE_COLOR);
		ly += STAT_ROW + 2;

		List<RiftSnapshot.QuestRegion> quests = snapshot.questRegions();
		if (quests.isEmpty()) {
			this.questMaxScroll = 0;
			this.questViewH = 0;
			PvDraw.text(g, font, "No tracked quests", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}

		int contentH = questContentHeight(quests);
		this.questViewX = lx;
		this.questViewY = ly;
		this.questViewW = lw;
		this.questViewH = Math.max(STAT_ROW, bottom - ly);
		this.questMaxScroll = Math.max(0, contentH - this.questViewH);
		this.questScroll = Math.max(0, Math.min(this.questScroll, this.questMaxScroll));

		g.enableScissor(lx, ly, lx + lw + 3, ly + this.questViewH);
		int qy = ly - this.questScroll;
		boolean firstRegion = true;
		for (RiftSnapshot.QuestRegion region : quests) {
			if (!region.present()) {
				continue;
			}
			if (!firstRegion) {
				qy += REGION_GAP;
			}
			firstRegion = false;
			if (qy + STAT_ROW > ly && qy < ly + this.questViewH) {
				PvDraw.text(g, font, RiftUi.trim(font, region.title(), lw), lx, qy, PvDraw.COLOR_ACCENT);
			}
			qy += STAT_ROW;
			for (RiftSnapshot.QuestLine line : region.lines()) {
				if (qy + font.lineHeight > ly && qy < ly + this.questViewH) {
					RiftUi.questRow(g, font, line.label(), line.value(), lx, qy, lw);
				}
				qy += STAT_ROW;
			}
		}
		g.disableScissor();
	}

	private void drawOverviewRight(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Timecharms", lx, ly, TIMECHARM_COLOR);
		ly += STAT_ROW + 2;
		for (RiftSnapshot.Timecharm charm : snapshot.timecharms()) {
			if (ly + CHARM_ROW > bottom) {
				break;
			}
			ly = drawTimecharmRow(ui, g, font, charm, lx, ly, lw, mx, my);
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);
		if (ly + STAT_ROW > bottom) {
			return;
		}

		PvDraw.text(g, font, "Zone unlocks", lx, ly, ZONE_COLOR);
		ly += STAT_ROW;
		List<String> zones = snapshot.purchasedBoundaries();
		if (zones.isEmpty()) {
			PvDraw.text(g, font, "None purchased", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}
		PvDraw.text(g, font, zones.size() + " boundaries", lx, ly, PvDraw.COLOR_MUTED);
		ly += STAT_ROW + 2;
		for (String zone : zones) {
			if (ly + STAT_ROW > bottom) {
				break;
			}
			String label = InventoryDecoder.prettyWords(zone);
			PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
			ui.zones.add(new RiftUi.HoverZone(lx, ly, lw, STAT_ROW, List.of(
				PvTooltip.Line.title(label, PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(zone)
			)));
			ly += STAT_ROW;
		}

		if (!snapshot.foundCats().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = RiftUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Montezuma's Souls", lx, ly, CAT_COLOR);
			ly += STAT_ROW;
			for (String cat : snapshot.foundCats()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(cat);
				PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}

		if (!snapshot.killedEyes().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = RiftUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Killed eyes", lx, ly, EYE_COLOR);
			ly += STAT_ROW;
			for (String eye : snapshot.killedEyes()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(eye);
				PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}
	}

	private int drawTimecharmRow(
		RiftUi ui, GuiGraphicsExtractor g, Font font, RiftSnapshot.Timecharm charm,
		int x, int y, int w, int mx, int my
	) {
		ItemStack icon = SkyBlockItemFactory.toStack(new InventorySnapshot.Slot(
			charm.itemId(), 1, List.of(), charm.name(), null, null, null
		));

		PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(y, CHARM_ROW, CHARM_ICON, font.lineHeight);
		RiftUi.drawItemIcon(g, icon, charm.itemId(), x, rowAlign.iconY(), CHARM_ICON);

		int textX = x + CHARM_ICON + 4;
		String status = charm.secured() ? "Obtained" : "Not obtained";
		int statusColor = charm.secured() ? OBTAINED : NOT_OBTAINED;
		int statusW = font.width(status);
		int nameMax = Math.max(20, w - CHARM_ICON - 4 - statusW - 6);
		String name = RiftUi.trim(font, charm.name(), nameMax);
		PvDraw.text(g, font, name, textX, rowAlign.textY(), charm.color());
		PvDraw.textRight(g, font, status, x + w, rowAlign.textY(), statusColor);

		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(charm.name(), charm.color()));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row(
			"Status", PvDraw.COLOR_MUTED,
			status, statusColor
		));
		if (charm.visitsToGet() > 0) {
			tip.add(PvTooltip.Line.row(
				"Visits to get", PvDraw.COLOR_MUTED,
				String.valueOf(charm.visitsToGet()), PvDraw.COLOR_TEXT
			));
		}
		if (charm.secured() && charm.securedAtMs() > 0L) {
			tip.add(PvTooltip.Line.meta("Secured " + RiftUi.formatAgo(charm.securedAtMs())));
		}
		ui.zones.add(new RiftUi.HoverZone(x, y, w, CHARM_ROW, tip));
		return y + CHARM_ROW + 1;
	}

	private static int questContentHeight(List<RiftSnapshot.QuestRegion> quests) {
		int h = 0;
		boolean first = true;
		for (RiftSnapshot.QuestRegion region : quests) {
			if (!region.present()) {
				continue;
			}
			if (!first) {
				h += REGION_GAP;
			}
			first = false;
			h += STAT_ROW;
			h += region.lines().size() * STAT_ROW;
		}
		return h;
	}

	private static float easeInOutCubic(float t) {
		float x = Math.max(0F, Math.min(1F, t));
		return x < 0.5F
			? 4F * x * x * x
			: 1F - (float) Math.pow(-2F * x + 2F, 3) / 2F;
	}
}

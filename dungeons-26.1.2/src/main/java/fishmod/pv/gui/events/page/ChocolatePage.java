/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events.page;

import fishmod.pv.data.ChocolateEmployees;
import fishmod.pv.data.ChocolateFactoryData;
import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.HoppityRabbitsData;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.events.EventsUi;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.events.EventsUi.*;

/** Chocolate Factory subpage (owns rabbit-list scroll). */
public final class ChocolatePage {
	private record FactionHit(String id, int x, int y, int w, int h) {
	}

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private final Set<String> expandedFactions = new HashSet<>();
	private final List<FactionHit> factionHits = new ArrayList<>();
	private int rightHitX;
	private int rightHitY;
	private int rightHitW;
	private int rightHitH;
	private boolean rabbitsFace;
	private boolean flipTarget;
	private long flipStartMs;

	public void resetScroll() {
		this.scroll = 0;
		this.expandedFactions.clear();
		this.rabbitsFace = false;
		this.flipTarget = false;
		this.flipStartMs = 0L;
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx < this.rightHitX || mx >= this.rightHitX + this.rightHitW
			|| my < this.rightHitY || my >= this.rightHitY + this.rightHitH) {
			return false;
		}
		if (this.flipStartMs != 0L) {
			return true;
		}
		if (this.rabbitsFace) {
			for (FactionHit hit : this.factionHits) {
				if (mx >= hit.x() && mx < hit.x() + hit.w() && my >= hit.y() && my < hit.y() + hit.h()) {
					if (!this.expandedFactions.remove(hit.id())) {
						this.expandedFactions.add(hit.id());
					}
					return true;
				}
			}
		}
		this.flipTarget = !this.rabbitsFace;
		this.flipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int contentX, int contentW) {
		if (!this.rabbitsFace || this.flipStartMs != 0L || this.maxScroll <= 0 || this.scrollH <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		if (mouseX < contentX || mouseX >= contentX + contentW) {
			return false;
		}
		int step = STAT_ROW * 3;
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -step : step)));
		if (next != this.scroll) {
			this.scroll = next;
			return true;
		}
		return false;
	}

	public void render(
		EventsSnapshot snapshot,
		EventsUi ui,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mx,
		int my
	) {
		EventsSnapshot.Chocolate choc = snapshot.chocolate();
		this.rightHitW = 0;
		this.factionHits.clear();
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		int lx = x;
		int rx = x + leftW + GAP;
		PvDraw.innerPanel(g, lx, y, leftW, h);

		int bottom = y + h - PAD;
		int cx = lx + PAD;
		int cy = y + PAD;
		int cw = leftW - PAD * 2;

		cy += PvDraw.sectionHeader(g, font, "Chocolate", cx, cy, cw);
		if (!choc.present()) {
			PvDraw.text(g, font, "No chocolate data", cx, cy, PvDraw.COLOR_MUTED);
			PvDraw.innerPanel(g, rx, y, rightW, h);
			this.maxScroll = 0;
			return;
		}

		cy = ui.tipStat(g, font, "Current", FormatUtil.shortCoins(choc.chocolate()), COLOR_CHOCOLATE, cx, cy, cw, mx, my,
			tipTitle("Chocolate", COLOR_CHOCOLATE,
				PvTooltip.Line.row("Purse", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolate()), COLOR_CHOCOLATE),
				PvTooltip.Line.row("All-time", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalChocolate()), PvDraw.COLOR_GOLD)));
		cy = ui.tipStat(g, font, "All-time", FormatUtil.shortCoins(choc.totalChocolate()), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
			tipTitle("All-time chocolate", PvDraw.COLOR_GOLD,
				PvTooltip.Line.meta(FormatUtil.commas(choc.totalChocolate()) + " chocolate")));
		ChocolateFactoryData.Prestige prestige = choc.prestige();
		String prestigeValue = prestige.maxed()
			? FormatUtil.shortCoins(prestige.earned())
			: FormatUtil.shortCoins(prestige.earned()) + " / " + FormatUtil.shortCoins(prestige.threshold());
		cy = ui.tipStat(g, font, "This prestige", prestigeValue, prestige.ready() ? COLOR_COMPLETE : PvDraw.COLOR_TEXT,
			cx, cy, cw, mx, my, prestigeTip(prestige));

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Factory", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Level", FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Factory level", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT)));
			cy = ui.tipStat(g, font, "Click upgrades", FormatUtil.commas(choc.clickUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			cy = ui.tipStat(g, font, "Multiplier", FormatUtil.commas(choc.multiplierUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Rabbit rarity", FormatUtil.commas(choc.rabbitRarityUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Barn capacity", FormatUtil.commas(choc.barnCapacityLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
		}

		if (cy + SEP_GAP + font.lineHeight + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Time Tower", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Level", FormatUtil.commas(choc.timeTowerLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				int chargeColor = choc.timeTowerCharges() >= 3 ? COLOR_COMPLETE : PvDraw.COLOR_ACCENT;
				cy = ui.tipStat(g, font, "Charges", choc.timeTowerCharges() + " / 3", chargeColor, cx, cy, cw, mx, my,
					tipTitle("Time Tower charges", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.row("Charges", PvDraw.COLOR_MUTED, choc.timeTowerCharges() + " / 3", chargeColor)));
			}
			if (choc.timeTowerActivationMs() > 0L && cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Last active", formatAgo(choc.timeTowerActivationMs()), PvDraw.COLOR_MUTED,
					cx, cy, cw, mx, my,
					tipTitle("Time Tower", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.meta("Activated " + formatAgo(choc.timeTowerActivationMs()))));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Shop", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Cocoa fortune", FormatUtil.commas(choc.cocoaFortuneUpgrades()), PvDraw.COLOR_TEXT,
				cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Spent", FormatUtil.shortCoins(choc.chocolateSpent()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my,
					tipTitle("Chocolate shop", COLOR_CHOCOLATE,
						PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED,
							FormatUtil.commas(choc.chocolateSpent()), COLOR_CHOCOLATE)));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Rabbits & eggs", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Unique", FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Rabbits", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Unique", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT)));
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Copies", FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Breakfast eggs", FormatUtil.shortCoins(choc.breakfastEggs()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Lunch eggs", FormatUtil.shortCoins(choc.lunchEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Dinner eggs", FormatUtil.shortCoins(choc.dinnerEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				ChocolateFactoryData.Hitmen hitmen = choc.hitmen();
				cy = ui.tipStat(g, font, "Hitmen slots", hitmen.slots() + " / " + hitmen.maxSlots(), PvDraw.COLOR_TEXT,
					cx, cy, cw, mx, my, hitmenTip(choc));
			}
			if (cy + STAT_ROW <= bottom) {
				ui.tipStat(g, font, "Missed eggs", FormatUtil.commas(choc.missedEggs()),
					choc.missedEggs() > 0 ? 0xFFFF8888 : PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					choc.missedEggs() > 0
						? tipTitle("Missed eggs", 0xFFFF8888,
						PvTooltip.Line.meta("Uncollected eggs from rabbit hitmen"))
						: null);
			}
		}

		drawRightFlip(choc, ui, g, font, rx, y, rightW, h, mx, my);
	}

	private void drawRightFlip(
		EventsSnapshot.Chocolate choc, EventsUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int h, int mx, int my
	) {
		this.rightHitX = x;
		this.rightHitY = y;
		this.rightHitW = w;
		this.rightHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.flipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.flipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.rabbitsFace = this.flipTarget;
				this.flipStartMs = 0L;
				this.scroll = 0;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showRabbits = animating
			? (Math.cos(angle) < 0.0 ? this.flipTarget : this.rabbitsFace)
			: this.rabbitsFace;
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
		if (showRabbits) {
			drawRabbitsFace(choc, ui, g, font, x, y, w, h, mx, my);
		} else {
			drawEmployeesFace(choc, ui, g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawEmployeesFace(
		EventsSnapshot.Chocolate choc, EventsUi ui, GuiGraphicsExtractor g, Font font, int rx, int y, int rightW, int h
	) {
		this.maxScroll = 0;
		this.scrollH = 0;
		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		int bottom = y + h - PAD;
		ry += PvDraw.sectionHeader(g, font, "Employees", rcx, ry, rcw);
		List<EventsSnapshot.Employee> employees = choc.employees();
		if (employees.isEmpty()) {
			PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED);
		}
		for (EventsSnapshot.Employee emp : employees) {
			if (ry + RABBIT_ROW > bottom) {
				break;
			}
			drawEmployeeRow(ui, g, font, emp, rcx, ry, rcw);
			ry += RABBIT_ROW;
		}
	}

	private void drawRabbitsFace(
		EventsSnapshot.Chocolate choc, EventsUi ui, GuiGraphicsExtractor g, Font font,
		int rx, int y, int rightW, int h, int mx, int my
	) {
		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		int bottom = y + h - PAD;
		ry += PvDraw.sectionHeader(g, font, "Rabbits", rcx, ry, rcw);
		if (ry + STAT_ROW <= bottom) {
			List<EventsSnapshot.Rabbit> rabbits = choc.topRabbits();
			List<ChocolateFactoryData.Faction> factions = ChocolateFactoryData.factions();
			int rows = 3 + (factions.isEmpty() ? 0 : factions.size() + 1) + 1 + Math.max(1, rabbits.size());
			for (ChocolateFactoryData.Faction faction : factions) {
				if (this.expandedFactions.contains(faction.id())) {
					rows += faction.size();
				}
			}
			int viewH = Math.max(0, bottom - ry);
			int contentH = rows * STAT_ROW;
			this.scrollTop = ry;
			this.scrollH = viewH;
			this.maxScroll = Math.max(0, contentH - viewH);
			this.scroll = Math.min(this.scroll, this.maxScroll);
			g.enableScissor(rcx, ry, rcx + rcw, ry + viewH);
			int gy = ry - this.scroll;
			gy = drawHoppityRows(ui, g, font, choc, rcx, gy, rcw, ry, viewH);
			if (!factions.isEmpty()) {
				PvDraw.text(g, font, "Factions", rcx, gy, PvDraw.COLOR_MUTED);
				gy += STAT_ROW;
				for (ChocolateFactoryData.Faction faction : factions) {
					int rowY = gy;
					gy = drawFactionRow(ui, g, font, choc, faction, rcx, gy, rcw, ry, viewH, mx, my);
					if (rowY + STAT_ROW > ry && rowY < ry + viewH) {
						this.factionHits.add(new FactionHit(faction.id(), rcx, Math.max(rowY, ry), rcw,
							Math.min(rowY + STAT_ROW, ry + viewH) - Math.max(rowY, ry)));
					}
					if (this.expandedFactions.contains(faction.id())) {
						gy = drawFactionRabbits(g, font, choc, faction, rcx, gy, rcw);
					}
				}
			}
			PvDraw.text(g, font, "Top rabbits", rcx, gy, PvDraw.COLOR_MUTED);
			gy += STAT_ROW;
			if (rabbits.isEmpty()) {
				PvDraw.text(g, font, "None", rcx, gy, PvDraw.COLOR_MUTED);
			}
			for (EventsSnapshot.Rabbit rabbit : rabbits) {
				String rarity = rabbit.rarity() == null || rabbit.rarity().isBlank()
					? HoppityRabbitsData.rarityOf(rabbit.id())
					: rabbit.rarity();
				int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
				EventsUi.statLine(g, font, trim(font, rabbit.name(), Math.max(24, rcw - font.width("×" + rabbit.count()) - 8)),
					"×" + rabbit.count(), rcx, gy, rcw, rarityColor);
				ChocolateFactoryData.Faction faction = ChocolateFactoryData.factionOf(rabbit.id());
				ui.addClippedHover(rcx, gy, rcw, STAT_ROW, rcx, ry, rcw, viewH, tipTitle(rabbit.name(), rarityColor,
					PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(rabbit.count()), PvDraw.COLOR_GOLD),
					faction == null ? null
						: PvTooltip.Line.row("Faction", PvDraw.COLOR_MUTED, faction.name(), PvDraw.COLOR_ACCENT)));
				gy += STAT_ROW;
			}
			g.disableScissor();
		} else {
			this.maxScroll = 0;
		}
	}

	private static int drawHoppityRows(
		EventsUi ui, GuiGraphicsExtractor g, Font font, EventsSnapshot.Chocolate choc,
		int x, int gy, int w, int clipY, int clipH
	) {
		ChocolateFactoryData.Faction selected = ChocolateFactoryData.faction(choc.selectedFaction());
		String factionValue = selected == null
			? "None"
			: selected.name() + (choc.factionLevel() > 0 ? ", Lvl " + choc.factionLevel() : "");
		List<PvTooltip.Line> factionTip = tipTitle("Rabbit faction", PvDraw.COLOR_ACCENT,
			PvTooltip.Line.row("Selected", PvDraw.COLOR_MUTED, selected == null ? "None" : selected.name(), PvDraw.COLOR_ACCENT),
			PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.factionLevel()), PvDraw.COLOR_TEXT),
			selected == null ? null
				: PvTooltip.Line.row("Rabbits found", PvDraw.COLOR_MUTED,
				selected.found(choc.ownedRabbits()) + " / " + selected.size(), PvDraw.COLOR_GOLD));
		gy = scrollStat(ui, g, font, "Faction", PvDraw.COLOR_MUTED, factionValue,
			selected == null ? PvDraw.COLOR_MUTED : PvDraw.COLOR_ACCENT, x, gy, w, clipY, clipH, factionTip);

		long ready = choc.missedEggs();
		ChocolateFactoryData.Hitmen hitmen = choc.hitmen();
		gy = scrollStat(ui, g, font, "Hitmen", PvDraw.COLOR_MUTED,
			FormatUtil.commas(ready) + " ready, " + hitmen.slots() + "/" + hitmen.maxSlots() + " slots",
			ready > 0 ? COLOR_COMPLETE : PvDraw.COLOR_TEXT, x, gy, w, clipY, clipH, hitmenTip(choc));

		if (!choc.chocobitsPresent()) {
			return scrollStat(ui, g, font, "Chocobits", PvDraw.COLOR_MUTED, "No data", PvDraw.COLOR_MUTED,
				x, gy, w, clipY, clipH, tipTitle("Chocobits", COLOR_CHOCOLATE,
					PvTooltip.Line.meta("No Chocobit data in the API")));
		}
		int year = ChocolateFactoryData.currentSkyBlockYear(System.currentTimeMillis());
		String bitsValue = EventsSnapshot.Chocobit.active(choc.chocobits(), year) + " active, "
			+ FormatUtil.commas(choc.chocobitsFound()) + " found";
		return scrollStat(ui, g, font, "Chocobits", PvDraw.COLOR_MUTED, bitsValue, COLOR_CHOCOLATE,
			x, gy, w, clipY, clipH, chocobitsTip(choc));
	}

	private int drawFactionRow(
		EventsUi ui, GuiGraphicsExtractor g, Font font, EventsSnapshot.Chocolate choc, ChocolateFactoryData.Faction faction,
		int x, int gy, int w, int clipY, int clipH, int mx, int my
	) {
		boolean selected = faction.id().equals(choc.selectedFaction());
		boolean expanded = this.expandedFactions.contains(faction.id());
		boolean hover = mx >= x && mx < x + w && my >= gy && my < gy + STAT_ROW && my >= clipY && my < clipY + clipH;
		int found = faction.found(choc.ownedRabbits());
		int size = faction.size();
		List<PvTooltip.Line> tip = tipTitle(faction.name() + " faction", PvDraw.COLOR_ACCENT,
			PvTooltip.Line.row("Found", PvDraw.COLOR_MUTED, found + " / " + size, found >= size ? COLOR_COMPLETE : PvDraw.COLOR_GOLD),
			selected ? PvTooltip.Line.meta("Selected faction") : null,
			PvTooltip.Line.action(expanded ? "Click to collapse" : "Click to show rabbits"));
		if (hover) {
			PvDraw.fill(g, x - 2, gy - 1, w + 4, STAT_ROW, 0x22FFFFFF);
		}
		int labelColor = selected ? PvDraw.COLOR_ACCENT : hover ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED;
		return scrollStat(ui, g, font, (expanded ? "- " : "+ ") + faction.name(), labelColor,
			found + " / " + size, found >= size ? COLOR_COMPLETE : PvDraw.COLOR_TEXT, x, gy, w, clipY, clipH, tip);
	}

	private static int drawFactionRabbits(
		GuiGraphicsExtractor g, Font font, EventsSnapshot.Chocolate choc, ChocolateFactoryData.Faction faction,
		int x, int gy, int w
	) {
		int indent = 10;
		for (var group : faction.rabbitsByRarity().entrySet()) {
			int rarityColor = SkyBlockItemFactory.tierArgb(group.getKey());
			for (String id : group.getValue()) {
				Integer count = choc.rabbitCounts().get(id);
				boolean owned = count != null && count > 0;
				String value = owned ? "×" + count : "Missing";
				int leftMax = Math.max(8, w - indent - font.width(value) - 6);
				PvDraw.text(g, font, trim(font, prettyModifier(id), leftMax), x + indent, gy, owned ? rarityColor : PvDraw.COLOR_MUTED);
				PvDraw.textRight(g, font, value, x + w, gy, owned ? PvDraw.COLOR_TEXT : 0xFF666670);
				gy += STAT_ROW;
			}
		}
		return gy;
	}

	private static int scrollStat(
		EventsUi ui, GuiGraphicsExtractor g, Font font, String label, int labelColor, String value, int valueColor,
		int x, int gy, int w, int clipY, int clipH, List<PvTooltip.Line> tip
	) {
		int leftMax = Math.max(8, w - font.width(value) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, gy, labelColor);
		PvDraw.textRight(g, font, value, x + w, gy, valueColor);
		ui.addClippedHover(x, gy, w, STAT_ROW, x, clipY, w, clipH, tip);
		return gy + STAT_ROW;
	}

	private static List<PvTooltip.Line> prestigeTip(ChocolateFactoryData.Prestige prestige) {
		List<PvTooltip.Line> tip = tipTitle("This prestige", PvDraw.COLOR_TEXT,
			PvTooltip.Line.row("Prestige", PvDraw.COLOR_MUTED, FormatUtil.commas(prestige.level()), PvDraw.COLOR_ACCENT),
			PvTooltip.Line.row("Earned", PvDraw.COLOR_MUTED, FormatUtil.commas(prestige.earned()), PvDraw.COLOR_TEXT));
		if (prestige.maxed()) {
			tip.add(PvTooltip.Line.row("Next prestige", PvDraw.COLOR_MUTED, "Max", COLOR_COMPLETE));
			return tip;
		}
		tip.add(PvTooltip.Line.row("Prestige " + (prestige.level() + 1), PvDraw.COLOR_MUTED,
			FormatUtil.commas(prestige.threshold()), COLOR_CHOCOLATE));
		tip.add(PvTooltip.Line.row("Needed", PvDraw.COLOR_MUTED,
			prestige.ready() ? "Ready" : FormatUtil.commas(prestige.needed()),
			prestige.ready() ? COLOR_COMPLETE : PvDraw.COLOR_GOLD));
		return tip;
	}

	private static List<PvTooltip.Line> hitmenTip(EventsSnapshot.Chocolate choc) {
		ChocolateFactoryData.Hitmen hitmen = choc.hitmen();
		List<PvTooltip.Line> tip = tipTitle("Rabbit Hitmen", PvDraw.COLOR_ACCENT,
			PvTooltip.Line.row("Eggs ready", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.missedEggs()),
				choc.missedEggs() > 0 ? COLOR_COMPLETE : PvDraw.COLOR_TEXT),
			PvTooltip.Line.row("Slots", PvDraw.COLOR_MUTED, hitmen.slots() + " / " + hitmen.maxSlots(),
				hitmen.maxed() ? COLOR_COMPLETE : PvDraw.COLOR_TEXT),
			PvTooltip.Line.row("Coins paid", PvDraw.COLOR_MUTED,
				FormatUtil.shortCoins(hitmen.paid()) + " / " + FormatUtil.shortCoins(hitmen.total())
					+ " (" + FormatUtil.percent(hitmen.fraction()) + ")",
				hitmen.maxed() ? COLOR_COMPLETE : PvDraw.COLOR_GOLD));
		if (!hitmen.maxed() && hitmen.nextSlotCost() > 0L) {
			tip.add(PvTooltip.Line.row("Next slot", PvDraw.COLOR_MUTED,
				FormatUtil.commas(hitmen.nextSlotCost()) + " coins", PvDraw.COLOR_GOLD));
		}
		tip.add(PvTooltip.Line.meta("Uncollected eggs waiting for hitmen"));
		return tip;
	}

	private static List<PvTooltip.Line> chocobitsTip(EventsSnapshot.Chocolate choc) {
		List<EventsSnapshot.Chocobit> bits = choc.chocobits();
		int year = ChocolateFactoryData.currentSkyBlockYear(System.currentTimeMillis());
		int active = EventsSnapshot.Chocobit.active(bits, year);
		List<PvTooltip.Line> tip = tipTitle("Chocobits", COLOR_CHOCOLATE,
			PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, FormatUtil.commas(active), COLOR_CHOCOLATE),
			bits.size() > active
				? PvTooltip.Line.row("Expired", PvDraw.COLOR_MUTED, FormatUtil.commas(bits.size() - active), 0xFFFF8888)
				: null,
			PvTooltip.Line.row("Total found", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocobitsFound()), PvDraw.COLOR_TEXT));
		if (bits.isEmpty()) {
			return tip;
		}
		tip.add(PvTooltip.Line.divider());
		int shown = 0;
		for (EventsSnapshot.Chocobit bit : bits) {
			if (shown == 12) {
				tip.add(PvTooltip.Line.meta("+" + (bits.size() - shown) + " more"));
				break;
			}
			boolean expired = bit.expired(year);
			String obtained = bit.ownedYear() > 0 ? "Year " + bit.ownedYear() : "Year unknown";
			String expiry = bit.expiryYear() <= 0 ? ", expiry unknown"
				: expired ? ", expired Year " + bit.expiryYear()
				: ", expires Year " + bit.expiryYear();
			tip.add(PvTooltip.Line.row("#" + bit.id(), PvDraw.COLOR_MUTED, obtained + expiry,
				expired ? 0xFFFF8888 : PvDraw.COLOR_TEXT));
			shown++;
		}
		tip.add(PvTooltip.Line.meta("Current year: " + year));
		return tip;
	}

	private void drawEmployeeRow(
		EventsUi ui, GuiGraphicsExtractor g, Font font, EventsSnapshot.Employee emp, int x, int y, int w
	) {
		String rarity = ChocolateEmployees.rarityOf(emp.id());
		int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
		int slotBg = raritySlotBackground(rarityColor);
		PvDraw.roundedFill(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, 3, slotBg);
		PvDraw.roundedRing(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, 3, SLOT_BORDER, PvDraw.C_ALL);

		ItemStack icon = employeeIcon(emp.id());
		PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(y + 1, RABBIT_SLOT, 16, font.lineHeight);
		g.item(icon, x, rowAlign.iconY());

		String name = ChocolateEmployees.displayName(emp.id(), emp.name());
		String level = "Lvl " + emp.level();
		int levelColor = ChocolateFactoryData.employeeLevelColor(emp.level());
		int textX = x + RABBIT_SLOT + 4;
		int textW = Math.max(8, w - RABBIT_SLOT - 4);
		int leftMax = Math.max(8, textW - font.width(level) - 6);
		PvDraw.text(g, font, trim(font, name, leftMax), textX, rowAlign.textY(), rarityColor);
		PvDraw.textRight(g, font, level, x + w, rowAlign.textY(), levelColor);

		ui.addClippedHover(x, y, w, RABBIT_ROW, ui.contentX, ui.contentY, ui.contentW, ui.contentH,
			tipTitle(name, rarityColor,
				PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
				PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(emp.level()), levelColor)));
	}

	// Textured heads are rebuilt from scratch per call; employees are a small fixed set.
	private static final java.util.Map<String, ItemStack> EMPLOYEE_HEADS = new java.util.concurrent.ConcurrentHashMap<>();

	private static ItemStack employeeIcon(String employeeId) {
		String value = ChocolateEmployees.skullValue(employeeId);
		if (value != null && !value.isBlank()) {
			ItemStack head = EMPLOYEE_HEADS.computeIfAbsent(value, SkyBlockItemFactory::texturedHead);
			if (head != null && !head.isEmpty()) {
				return head;
			}
		}
		ItemStack fallback = SkyBlockItemFactory.iconStack("CHOCO_RABBIT_PERSONALITY");
		if (fallback != null && !fallback.isEmpty()) {
			return fallback;
		}
		return new ItemStack(Items.RABBIT_FOOT);
	}

	/** Soft rarity tint like pets/museum slots. */
	private static int raritySlotBackground(int rarityArgb) {
		int r = (rarityArgb >> 16) & 0xFF;
		int g = (rarityArgb >> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		int mixR = (r * 70 + 16 * 186) / 256;
		int mixG = (g * 70 + 16 * 186) / 256;
		int mixB = (b * 70 + 24 * 186) / 256;
		return 0xFF000000 | (mixR << 16) | (mixG << 8) | mixB;
	}
}

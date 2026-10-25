/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import fishmod.pv.data.AttributeShardsData;
import fishmod.pv.data.FarmingFortuneEstimate;
import fishmod.pv.data.FarmingFortuneEstimate.Garden;
import fishmod.pv.data.FarmingFortuneEstimate.Kind;
import fishmod.pv.data.FarmingFortuneEstimate.Line;
import fishmod.pv.data.FarmingFortuneSummary;
import fishmod.pv.data.FarmingFortuneTemporary;
import fishmod.pv.data.FarmingFortuneTemporary.Source;
import fishmod.pv.data.FarmingFortuneTemporary.Status;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.PvTooltip.Span;
import fishmod.pv.gui.garden.GardenUi;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.garden.GardenUi.*;
import static fishmod.pv.gui.garden.page.FarmingFortuneLayout.*;

/** Farming Fortune breakdown, opened from the Garden overview row. Each tile is a short total; details live in tooltips. */
public final class FarmingFortunePage {
	private static final int BACK = 14;
	private static final int MAX_OTHER_PETS = 3;
	private static final int UNCONFIRMED_C = 0xFFBFA36A;
	private static final int ACTIVE_BORDER = 0xFF3C7A3C;
	private static final int ULTIMATE_C = 0xFFFF55FF;
	private static final String SHARD_PREFIX = "ATTRIBUTE_SHARD_";
	private static final Set<String> PLAIN_SOURCES = Set.of("PESTHUNTER_PHILLIP_NPC", "OVERDRIVE_GARDEN_CHIP");

	private interface Icon {
		void draw(GuiGraphicsExtractor g, int x, int y);
	}

	private boolean open;
	private String crop = FarmingFortuneEstimate.CROPS.get(0);
	private int scroll;
	private int maxScroll;
	private int bodyTop;
	private int bodyH;
	private int backX;
	private int backY;
	private final List<Rect> cropHits = new ArrayList<>();
	private int clipTop;
	private int clipBottom;

	public boolean isOpen() {
		return this.open;
	}

	public void open() {
		this.open = true;
		this.scroll = 0;
	}

	public void close() {
		this.open = false;
	}

	String selectedCrop() {
		return this.crop;
	}

	public boolean mouseClicked(double mx, double my) {
		if (!this.open) {
			return false;
		}
		if (mx >= this.backX && mx < this.backX + BACK && my >= this.backY && my < this.backY + BACK) {
			close();
			return true;
		}
		for (int i = 0; i < this.cropHits.size(); i++) {
			if (this.cropHits.get(i).contains((int) Math.floor(mx), (int) Math.floor(my))) {
				this.crop = FarmingFortuneEstimate.CROPS.get(i);
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mouseY, double scrollY) {
		if (!this.open || this.maxScroll <= 0 || mouseY < this.bodyTop || mouseY >= this.bodyTop + this.bodyH) {
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
		GardenSnapshot snap, PlayerStatsSnapshot stats, GardenUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.cropHits.clear();
		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int cw = w - PAD * 2;
		int cy = y + PAD;

		this.backX = cx;
		this.backY = cy;
		boolean backHover = mx >= cx && mx < cx + BACK && my >= cy && my < cy + BACK;
		PvDraw.roundedFill(g, cx, cy, BACK, BACK, 3, backHover ? 0xFF3A3A4A : 0x24FFFFFF);
		PvDraw.roundedRing(g, cx, cy, BACK, BACK, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, "<", cx + BACK / 2, cy + (BACK - font.lineHeight) / 2 + 1, PvDraw.COLOR_TEXT);
		if (backHover) {
			ui.zones.add(new HoverZone(cx, cy, BACK, BACK, List.of(PvTooltip.Line.of("Back to overview", PvDraw.COLOR_TEXT))));
		}
		PvDraw.textBold(g, font, "Farming Fortune", cx + BACK + 6, cy + 3, PvDraw.COLOR_GOLD);
		String tag = "Estimate";
		PvDraw.textRight(g, font, tag, cx + cw, cy + 3, PvDraw.COLOR_MUTED);
		ui.zones.add(new HoverZone(cx + cw - font.width(tag), cy, font.width(tag), BACK, List.of(
			PvTooltip.Line.title("Estimate", PvDraw.COLOR_TEXT),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Uses the best farming setup found on", PvDraw.COLOR_TEXT),
			PvTooltip.Line.of("the profile. The API only has what was", PvDraw.COLOR_TEXT),
			PvTooltip.Line.of("last saved, so this is not a live stat.", PvDraw.COLOR_TEXT)
		)));
		cy += BACK + 6;

		FarmingFortuneEstimate est = stats == null ? null : stats.farmingFortune();
		if (est == null) {
			PvDraw.textCentered(g, font, "Inventories still loading...", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}
		Garden garden = Garden.of(snap);
		List<Source> temporary = est.temporary(this.crop, garden, System.currentTimeMillis());
		int heroBottom = drawHero(est, garden, temporary, ui, g, font, cx, cy, cw, mx, my);

		this.bodyTop = heroBottom + 8;
		this.bodyH = Math.max(20, y + h - PAD - this.bodyTop);
		this.clipTop = this.bodyTop;
		this.clipBottom = this.bodyTop + this.bodyH;

		g.enableScissor(x + 1, this.bodyTop, x + w - 1, this.bodyTop + this.bodyH);
		int top = this.bodyTop - this.scroll;
		int columns = columnCount(cw);
		List<Rect> cols = columns(cx, cw, columns);
		List<List<Section>> plan = sections(columns);
		int bottom = top;
		for (int i = 0; i < columns; i++) {
			Rect col = cols.get(i);
			int yy = top;
			for (Section s : plan.get(i)) {
				yy = drawSection(s, est, garden, snap, ui, g, font, col.x(), yy, col.w(), mx, my) + SECTION_GAP;
			}
			bottom = Math.max(bottom, yy - SECTION_GAP);
		}
		bottom = drawTemporary(est, garden, temporary, ui, g, font, cx, bottom + SECTION_GAP + 2, cw, mx, my);
		g.disableScissor();
		this.maxScroll = Math.max(0, bottom - top - this.bodyH);
		this.scroll = Math.min(this.scroll, this.maxScroll);
	}

	private int drawHero(
		FarmingFortuneEstimate est, Garden garden, List<Source> temporary, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int mx, int my
	) {
		double general = est.general(garden);
		double total = est.forCrop(this.crop, garden);
		double active = FarmingFortuneTemporary.activeTotal(temporary);
		String big = FarmingFortuneTip.format(total);
		int bigW = font.width(big) * 2;
		String label = this.crop + " Fortune";
		String sub = "General " + FarmingFortuneTip.format(general);
		int heroW = bigW + 6 + Math.max(font.width(label), font.width(sub));
		List<Rect> slots = cropSlots(x, y, w, heroW, FarmingFortuneEstimate.CROPS.size());
		int textRoom = Math.max(8, (cropsBesideHero(w, heroW, slots.size()) ? heroW : w) - bigW - 6);

		PvDraw.textScaled(g, font, big, x, y + 3, PvDraw.COLOR_GOLD, 2F);
		PvDraw.text(g, font, GardenUi.trim(font, label, textRoom), x + bigW + 6, y + 2, PvDraw.COLOR_TEXT);
		PvDraw.text(g, font, GardenUi.trim(font, sub, textRoom), x + bigW + 6, y + 12, PvDraw.COLOR_MUTED);

		FarmingFortuneEstimate.PetPick cropPet = est.bestPet(this.crop, garden);
		List<PvTooltip.Line> summary = new ArrayList<>(List.of(
			PvTooltip.Line.title(label, PvDraw.COLOR_GOLD),
			PvTooltip.Line.divider(),
			summaryRow("Permanent", est.permanentTotal(garden)),
			summaryRow("Armour", est.armourTotal()),
			summaryRow("Equipment", est.equipmentTotal()),
			summaryRow(cropPet == null ? "Pet" : cropPet.pet().displayName(), est.petTotal(this.crop, garden)),
			summaryRow(this.crop, est.cropBonus(this.crop, garden)),
			PvTooltip.Line.divider(),
			PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, FarmingFortuneTip.format(total), PvDraw.COLOR_GOLD)
		));
		if (active > 0D) {
			summary.add(PvTooltip.Line.row("Active buffs", PvDraw.COLOR_MUTED, plus(active), COMPLETED_C));
			summary.add(PvTooltip.Line.row("With buffs", PvDraw.COLOR_TEXT, FarmingFortuneTip.format(total + active), COMPLETED_C));
		}
		summary.add(PvTooltip.Line.blank());
		summary.add(PvTooltip.Line.meta("General " + FarmingFortuneTip.format(general) + " applies to every crop."));
		ui.zones.add(new HoverZone(x, y, Math.min(heroW, w), HERO_H, summary));

		for (int i = 0; i < slots.size(); i++) {
			Rect r = slots.get(i);
			String name = FarmingFortuneEstimate.CROPS.get(i);
			this.cropHits.add(r);
			boolean selected = name.equals(this.crop);
			boolean hovered = r.contains(mx, my);
			PvDraw.roundedFill(g, r.x(), r.y(), r.w(), r.h(), 3, ITEM_SLOT_BG);
			PvDraw.roundedRing(g, r.x(), r.y(), r.w(), r.h(), 3, selected ? PvDraw.COLOR_ACCENT : hovered ? ITEM_SLOT_HOVER : ITEM_SLOT_BORDER, PvDraw.C_ALL);
			String id = cropId(name);
			GardenUi.drawIcon(g, GardenData.cropIconId(id), r.x() + 1, r.y() + 1, ICON, GardenData.cropPackModel(id));
			if (hovered) {
				ui.zones.add(new HoverZone(r.x(), r.y(), r.w(), r.h(), List.of(
					PvTooltip.Line.title(name, PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Fortune", PvDraw.COLOR_MUTED, FarmingFortuneTip.format(est.forCrop(name, garden)), PvDraw.COLOR_GOLD),
					PvTooltip.Line.meta(selected ? "Selected" : "Click to select")
				)));
			}
		}
		return heroBottom(y, slots);
	}

	private int drawSection(
		Section section, FarmingFortuneEstimate est, Garden garden, GardenSnapshot snap, GardenUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my
	) {
		return switch (section) {
			case CROP -> drawCrop(est, garden, snap, ui, g, font, x, y, w, mx, my);
			case GEAR -> drawGear(est, ui, g, font, x, y, w, mx, my);
			case PET -> drawPet(est, garden, ui, g, font, x, y, w, mx, my);
			case PERMANENT -> drawPermanent(est, garden, ui, g, font, x, y, w, mx, my);
		};
	}

	private int drawCrop(
		FarmingFortuneEstimate est, Garden garden, GardenSnapshot snap, GardenUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my
	) {
		y = header(g, font, this.crop, plus(est.cropBonus(this.crop, garden)), x, y, w);
		List<Line> lines = est.cropLines(this.crop, garden);
		y = toolTile(est.tool(this.crop, garden), lines.get(0), ui, g, font, x, y, w, mx, my);
		for (Line line : lines.subList(1, lines.size())) {
			Rect r = new Rect(x, y, w, TILE_H);
			switch (line.label()) {
				case "Crop upgrade" -> {
					String id = cropId(this.crop);
					tile(g, font, ui, r, (gg, ix, iy) -> GardenUi.drawIcon(gg, GardenData.cropIconId(id), ix, iy, ICON, GardenData.cropPackModel(id)),
						"Crop upgrade", PvDraw.COLOR_TEXT, line.note(), line, () -> upgradeTip(line, snap), mx, my);
				}
				case "Personal best" -> {
					long pb = est.personalBest(this.crop);
					String detail = !est.personalBestsUnlocked() ? "Perk not unlocked" : pb > 0L ? FormatUtil.shortXp(pb) + " crops" : "No score yet";
					tile(g, font, ui, r, vanilla(Items.GOLD_INGOT), "Personal best", PvDraw.COLOR_TEXT, detail, line,
						() -> personalBestTip(est, line, pb), mx, my);
				}
				case "Gear crop fortune" -> tile(g, font, ui, r, vanilla(Items.GOLDEN_BOOTS), "Gear bonus", PvDraw.COLOR_TEXT,
					"Armour and equipment", line, () -> gearCropTip(est, line), mx, my);
				default -> {
					InventoryDecoder.Stack acc = est.fermento();
					tile(g, font, ui, r, acc == null ? null : stack(acc), line.label(), nameColor(acc), "Crop accessory", line,
						() -> List.of(
							PvTooltip.Line.title(line.label(), nameColor(acc)),
							PvTooltip.Line.divider(),
							valueRow(this.crop + " Fortune", line, null)
						), mx, my);
				}
			}
			y += TILE_H + TILE_GAP;
		}
		return y - TILE_GAP;
	}

	private int toolTile(
		FarmingFortuneEstimate.Tool tool, Line toolLine, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int mx, int my
	) {
		Rect r = new Rect(x, y, w, TILE_H);
		if (tool == null) {
			String explanation = FarmingFortuneSummary.missingToolExplanation(this.crop, toolLine);
			boolean unknown = toolLine.kind() == Kind.UNAVAILABLE;
			tile(g, font, ui, r, null, "Tool", PvDraw.COLOR_MUTED, FarmingFortuneSummary.missingToolDetail(toolLine), unknown ? null : toolLine,
				() -> List.of(
					PvTooltip.Line.title(this.crop + " tool", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.of(explanation, PvDraw.COLOR_TEXT),
					PvTooltip.Line.meta(unknown ? "Not counted, the player may still own one." : "Counted as +0.")
				), mx, my);
			return y + TILE_H + TILE_GAP;
		}
		InventoryDecoder.Stack stack = tool.stack();
		int color = nameColor(stack);
		tile(g, font, ui, r, stack(stack), FarmingFortuneSummary.shortToolName(stack), color,
			tool.fromToolkit() ? "Farming Toolkit" : "Inventory", toolLine, () -> {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.title(FarmingFortuneTip.name(stack), color));
				tip.add(PvTooltip.Line.divider());
				for (Line part : tool.parts()) {
					tip.add(valueRow(part.label(), part, part.note()));
				}
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(tool.total()), PvDraw.COLOR_GOLD));
				return tip;
			}, mx, my);
		return y + TILE_H + TILE_GAP;
	}

	private int drawGear(FarmingFortuneEstimate est, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my) {
		y = header(g, font, "Gear", plus(est.armourTotal() + est.equipmentTotal()), x, y, w);
		List<FarmingFortuneEstimate.Piece> armour = est.armour();
		String armourDetail;
		if (!est.setBonuses().isEmpty()) {
			FarmingFortuneEstimate.SetBonus set = est.setBonuses().get(0);
			armourDetail = set.name() + " " + set.pieces() + "/4";
		} else {
			armourDetail = pieces(armour.size(), "No farming armour");
		}
		Line armourLine = new Line("Armour", est.armourTotal(), Kind.COUNTED, "");
		tile(g, font, ui, new Rect(x, y, w, TILE_H), armour.isEmpty() ? null : stack(armour.get(0).stack()), "Armour", PvDraw.COLOR_TEXT,
			armourDetail, armourLine, () -> {
				List<PvTooltip.Line> sets = new ArrayList<>();
				for (FarmingFortuneEstimate.SetBonus s : est.setBonuses()) {
					sets.add(PvTooltip.Line.row(
						List.of(Span.of(s.name() + " set bonus", PvDraw.COLOR_TEXT)),
						List.of(Span.of(plus(s.fortune()), PvDraw.COLOR_GOLD)),
						List.of(Span.of(s.pieces() + " pieces", PvDraw.COLOR_MUTED))));
				}
				return piecesTip("Armour", armour, sets, est.armourTotal());
			}, mx, my);
		y += TILE_H + TILE_GAP;
		List<FarmingFortuneEstimate.Piece> equipment = est.equipment();
		Line equipmentLine = new Line("Equipment", est.equipmentTotal(), Kind.COUNTED, "");
		tile(g, font, ui, new Rect(x, y, w, TILE_H), equipment.isEmpty() ? null : stack(equipment.get(0).stack()), "Equipment",
			PvDraw.COLOR_TEXT, pieces(equipment.size(), "No farming equipment"), equipmentLine,
			() -> piecesTip("Equipment", equipment, List.of(), est.equipmentTotal()), mx, my);
		return y + TILE_H;
	}

	private int drawPet(
		FarmingFortuneEstimate est, Garden garden, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int mx, int my
	) {
		y = header(g, font, "Pet", plus(est.petTotal(this.crop, garden)), x, y, w);
		List<FarmingFortuneEstimate.PetPick> pets = est.pets(this.crop, garden);
		Rect r = new Rect(x, y, w, TILE_H);
		if (pets.isEmpty()) {
			tile(g, font, ui, r, null, "Pet", PvDraw.COLOR_MUTED, "No farming pet", new Line("Pet", 0D, Kind.COUNTED, ""),
				() -> List.of(
					PvTooltip.Line.title("Pet", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.of("No pets that give farming fortune.", PvDraw.COLOR_TEXT)
				), mx, my);
			return y + TILE_H;
		}
		FarmingFortuneEstimate.PetPick best = pets.get(0);
		int color = SkyBlockItemFactory.tierArgb(best.pet().tier());
		String detail = "Level " + best.pet().level() + (best.item() == null ? "" : ", " + best.item().label());
		tile(g, font, ui, r, stack(best.pet().neuId()), best.pet().displayName(), color, detail,
			new Line("Pet", best.total(), Kind.COUNTED, ""), () -> petTip(best, pets, color), mx, my);
		return y + TILE_H;
	}

	private int drawPermanent(
		FarmingFortuneEstimate est, Garden garden, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int mx, int my
	) {
		y = header(g, font, "Permanent", plus(est.permanentTotal(garden)), x, y, w);
		for (FarmingFortuneSummary.Group group : FarmingFortuneSummary.permanentGroups(est, garden)) {
			String detail = FarmingFortuneSummary.detail(group);
			Line line = new Line(group.category().label(), group.total(), Kind.COUNTED, "");
			tile(g, font, ui, new Rect(x, y, w, TILE_H), categoryIcon(group, est), group.category().label(), PvDraw.COLOR_TEXT,
				detail.isEmpty() ? "None" : detail, line, () -> groupTip(group, est), mx, my);
			y += TILE_H + TILE_GAP;
		}
		return y - TILE_GAP;
	}

	private int drawTemporary(
		FarmingFortuneEstimate est, Garden garden, List<Source> temporary, GardenUi ui, GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int mx, int my
	) {
		if (temporary.isEmpty()) {
			return y - SECTION_GAP - 2;
		}
		double active = FarmingFortuneTemporary.activeTotal(temporary);
		double possible = FarmingFortuneTemporary.possibleTotal(temporary);
		header(g, font, "Temporary", active > 0D ? plus(active) + " active" : "None active", x, y, w,
			active > 0D ? COMPLETED_C : PvDraw.COLOR_MUTED);
		addZone(ui, x, y, w, HEADER_H, mx, my, () -> {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Temporary", PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(active > 0D
				? PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, plus(active), COMPLETED_C)
				: PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, "None", PvDraw.COLOR_MUTED));
			if (possible > 0D) {
				tip.add(PvTooltip.Line.row("Unconfirmed", PvDraw.COLOR_MUTED, "up to " + plus(possible), UNCONFIRMED_C));
			}
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.meta("Not in the totals above."));
			return tip;
		});
		y += HEADER_H;
		FarmingFortuneEstimate.PetPick pet = est.bestPet(this.crop, garden);
		int[] widths = new int[temporary.size()];
		for (int i = 0; i < temporary.size(); i++) {
			widths[i] = CROP_SLOT + 4 + font.width(plus(temporary.get(i).value())) + 4;
		}
		List<Rect> chips = chips(x, y, w, widths);
		int bottom = y;
		for (int i = 0; i < temporary.size(); i++) {
			Source s = temporary.get(i);
			Rect r = chips.get(i);
			int status = statusColor(s.status());
			int name = sourceColor(s, pet);
			boolean hovered = visible(my) && r.contains(mx, my);
			PvDraw.roundedFill(g, r.x(), r.y(), r.w(), r.h(), 3, hovered ? 0x30FFFFFF : PvDraw.CELL);
			PvDraw.roundedRing(g, r.x(), r.y(), r.w(), r.h(), 3, hovered ? ITEM_SLOT_HOVER : s.status() == Status.ACTIVE ? ACTIVE_BORDER : ITEM_SLOT_BORDER, PvDraw.C_ALL);
			sourceIcon(s).draw(g, r.x() + 1, r.y() + 1);
			if (s.status() == Status.INACTIVE) {
				PvDraw.fill(g, r.x() + 1, r.y() + 1, ICON, ICON, 0x99101018);
			}
			String text = GardenUi.trim(font, plus(s.value()), Math.max(8, r.w() - CROP_SLOT - 6));
			PvDraw.text(g, font, text, r.x() + CROP_SLOT + 3, r.y() + (r.h() - font.lineHeight) / 2 + 1, status);
			addZone(ui, r.x(), r.y(), r.w(), r.h(), mx, my, () -> temporaryTip(s, name, status));
			bottom = Math.max(bottom, r.bottom());
		}
		return bottom;
	}

	private List<PvTooltip.Line> temporaryTip(Source s, int nameColor, int statusColor) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(s.label(), nameColor));
		tip.add(PvTooltip.Line.divider());
		String stat = s.crop() ? this.crop + " Fortune" : "Farming Fortune";
		if (!s.parts().isEmpty()) {
			for (Line part : s.parts()) {
				tip.add(valueRow(part.label(), part, part.note()));
			}
			tip.add(PvTooltip.Line.divider());
		}
		tip.add(PvTooltip.Line.row(stat, s.parts().isEmpty() ? PvDraw.COLOR_MUTED : PvDraw.COLOR_TEXT, plus(s.value()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.row(
			List.of(Span.of("Status", PvDraw.COLOR_MUTED)),
			List.of(Span.of(statusText(s.status()), statusColor)),
			s.state() == null || s.state().isEmpty() ? List.of() : List.of(Span.of(s.state(), PvDraw.COLOR_MUTED))));
		tip.add(PvTooltip.Line.blank());
		tip.add(PvTooltip.Line.of(s.condition(), PvDraw.COLOR_TEXT));
		return tip;
	}

	static String statusText(Status status) {
		return switch (status) {
			case ACTIVE -> "Active";
			case UNKNOWN -> "Unconfirmed";
			case INACTIVE -> "Inactive";
		};
	}

	private static int statusColor(Status status) {
		return switch (status) {
			case ACTIVE -> COMPLETED_C;
			case UNKNOWN -> UNCONFIRMED_C;
			case INACTIVE -> PvDraw.COLOR_MUTED;
		};
	}

	// Rarity colour only for real items; Phillip and chips have no single rarity, Crop Fever uses the ultimate enchant pink.
	private static int sourceColor(Source s, FarmingFortuneEstimate.PetPick pet) {
		String id = s.iconId();
		if (id == null || PLAIN_SOURCES.contains(id)) {
			return PvDraw.COLOR_TEXT;
		}
		if (id.startsWith("ULTIMATE_")) {
			return ULTIMATE_C;
		}
		if (pet != null && id.equals(pet.pet().neuId())) {
			return SkyBlockItemFactory.tierArgb(pet.pet().tier());
		}
		String key = shardKeyOf(id);
		if (key != null) {
			return shardRarityColor(key);
		}
		return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(id));
	}

	private Icon sourceIcon(Source s) {
		String key = shardKeyOf(s.iconId());
		return key != null ? shardIcon(key) : stack(s.iconId());
	}

	static String shardKeyOf(String id) {
		if (id == null || !id.startsWith(SHARD_PREFIX)) {
			return null;
		}
		int semi = id.indexOf(';');
		return id.substring(SHARD_PREFIX.length(), semi < 0 ? id.length() : semi).toLowerCase(Locale.ROOT);
	}

	// One row: 18px icon slot, title and value on top, muted detail underneath. A null line draws no value.
	private void tile(
		GuiGraphicsExtractor g, Font font, GardenUi ui, Rect r, Icon icon, String title, int titleColor,
		String detail, Line line, Supplier<List<PvTooltip.Line>> tip, int mx, int my
	) {
		boolean hovered = visible(my) && r.contains(mx, my);
		if (hovered) {
			PvDraw.fill(g, r.x(), r.y(), r.w(), r.h(), PANEL_HOVER);
		}
		int slotY = r.y() + (r.h() - CROP_SLOT) / 2;
		PvDraw.roundedFill(g, r.x(), slotY, CROP_SLOT, CROP_SLOT, 3, ITEM_SLOT_BG);
		PvDraw.roundedRing(g, r.x(), slotY, CROP_SLOT, CROP_SLOT, 3, hovered ? ITEM_SLOT_HOVER : ITEM_SLOT_BORDER, PvDraw.C_ALL);
		if (icon != null) {
			icon.draw(g, r.x() + 1, slotY + 1);
		}
		int tx = r.x() + CROP_SLOT + 4;
		int valueW = 0;
		if (line != null) {
			String value = valueText(line);
			valueW = font.width(value) + 4;
			PvDraw.textRight(g, font, value, r.right(), r.y() + 1, valueColor(line));
		}
		PvDraw.text(g, font, GardenUi.trim(font, title, Math.max(8, r.right() - valueW - tx)), tx, r.y() + 1, titleColor);
		if (detail != null && !detail.isEmpty()) {
			PvDraw.text(g, font, GardenUi.trim(font, detail, Math.max(8, r.right() - tx)), tx, r.y() + 11, PvDraw.COLOR_MUTED);
		}
		addZone(ui, r.x(), r.y(), r.w(), r.h(), mx, my, tip);
	}

	private static int header(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w) {
		return header(g, font, label, value, x, y, w, PvDraw.COLOR_GOLD);
	}

	private static int header(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor) {
		int valueW = font.width(value);
		String shown = GardenUi.trim(font, label, Math.max(8, w - valueW - 8));
		PvDraw.text(g, font, shown, x, y, PvDraw.COLOR_MUTED);
		int lineX = x + font.width(shown) + 4;
		int lineEnd = x + w - valueW - 4;
		if (lineEnd > lineX) {
			PvDraw.fill(g, lineX, y + font.lineHeight / 2 - 1, lineEnd - lineX, 1, PvDraw.COLOR_DIVIDER);
		}
		PvDraw.textRight(g, font, value, x + w, y, valueColor);
		return y + HEADER_H;
	}

	private List<PvTooltip.Line> upgradeTip(Line line, GardenSnapshot snap) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(this.crop + " upgrade", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		tip.add(valueRow("Fortune", line, line.note()));
		GardenSnapshot.CropRow row = null;
		if (snap != null && snap.islandLoaded()) {
			for (GardenSnapshot.CropRow r : snap.crops()) {
				if (r.name().equals(this.crop)) {
					row = r;
				}
			}
		}
		if (row != null) {
			tip.add(PvTooltip.Line.row("Milestone", PvDraw.COLOR_MUTED,
				row.milestone() + (row.milestoneMaxed() ? " (MAX)" : ""), row.milestoneMaxed() ? COMPLETED_C : PvDraw.COLOR_TEXT));
		}
		if (line.kind() == Kind.UNAVAILABLE) {
			tip.add(PvTooltip.Line.meta("Garden not loaded, so not counted yet."));
		}
		return tip;
	}

	private List<PvTooltip.Line> personalBestTip(FarmingFortuneEstimate est, Line line, long pb) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(this.crop + " personal best", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		tip.add(valueRow("Fortune", line, null));
		if (est.personalBestsUnlocked()) {
			tip.add(PvTooltip.Line.row("Best contest", PvDraw.COLOR_MUTED, pb > 0L ? FormatUtil.commas(pb) : "-", PvDraw.COLOR_TEXT));
		} else {
			tip.add(PvTooltip.Line.meta("Needs the Personal Bests perk from Jacob."));
		}
		return tip;
	}

	private List<PvTooltip.Line> gearCropTip(FarmingFortuneEstimate est, Line line) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(this.crop + " gear bonus", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		List<FarmingFortuneEstimate.Piece> all = new ArrayList<>(est.armour());
		all.addAll(est.equipment());
		for (FarmingFortuneEstimate.Piece p : all) {
			double v = p.cropFortune().getOrDefault(this.crop, 0D);
			if (v > 0D) {
				tip.add(pieceRow(p.stack(), plus(v), null));
			}
		}
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(line.value()), PvDraw.COLOR_GOLD));
		return tip;
	}

	private List<PvTooltip.Line> piecesTip(
		String title, List<FarmingFortuneEstimate.Piece> pieces, List<PvTooltip.Line> extra, double total
	) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(title, PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		if (pieces.isEmpty()) {
			tip.add(PvTooltip.Line.of("None found in inventories.", PvDraw.COLOR_MUTED));
		}
		for (FarmingFortuneEstimate.Piece p : pieces) {
			String meta = p.pieceBonus() > 0D ? "incl. " + plus(p.pieceBonus()) + " piece bonus" : null;
			tip.add(pieceRow(p.stack(), plus(p.total()), meta));
		}
		tip.addAll(extra);
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(total), PvDraw.COLOR_GOLD));
		return tip;
	}

	private List<PvTooltip.Line> petTip(FarmingFortuneEstimate.PetPick best, List<FarmingFortuneEstimate.PetPick> pets, int color) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("[Lvl " + best.pet().level() + "] " + best.pet().displayName(), color));
		tip.add(PvTooltip.Line.divider());
		List<Line> lines = new ArrayList<>(best.lines());
		if (best.item() != null) {
			lines.add(best.item());
		}
		for (Line line : lines) {
			String meta = line.kind() == Kind.COUNTED ? null : line.note();
			tip.add(valueRow(line.label(), line, meta == null || meta.isEmpty() ? null : meta));
		}
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(best.total()), PvDraw.COLOR_GOLD));
		if (pets.size() > 1) {
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.meta("Other pets"));
			for (int i = 1; i < Math.min(pets.size(), MAX_OTHER_PETS + 1); i++) {
				FarmingFortuneEstimate.PetPick p = pets.get(i);
				tip.add(withIcon(PvTooltip.Line.row(p.pet().displayName(), SkyBlockItemFactory.tierArgb(p.pet().tier()),
					plus(p.total()), PvDraw.COLOR_MUTED), p.pet().neuId()));
			}
		}
		return tip;
	}

	private List<PvTooltip.Line> groupTip(FarmingFortuneSummary.Group group, FarmingFortuneEstimate est) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(group.category().label(), PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		for (Line line : group.lines()) {
			String shard = FarmingFortuneSummary.shardKey(line.label());
			if ("Accessories".equals(line.label())) {
				for (FarmingFortuneEstimate.Accessory a : est.accessories()) {
					tip.add(pieceRow(a.stack(), plus(a.fortune()), null));
				}
			} else if ("Helianthus Relic".equals(line.label()) && est.fermento() != null) {
				tip.add(pieceRow(est.fermento(), valueText(line), null));
			} else if (shard != null) {
				tip.add(withIcon(PvTooltip.Line.row(
					List.of(Span.of(line.label(), shardColor(line.label()))),
					List.of(Span.of(valueText(line), valueColor(line))),
					line.note() == null || line.note().isEmpty() ? List.of() : List.of(Span.of(line.note(), PvDraw.COLOR_MUTED))),
					shardIconId(shard)));
			} else {
				tip.add(valueRow(line.label(), line, line.note()));
			}
		}
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(group.total()), PvDraw.COLOR_GOLD));
		for (Line line : group.lines()) {
			if (line.kind() == Kind.UNAVAILABLE) {
				tip.add(PvTooltip.Line.meta("Sources marked - are not counted yet."));
				break;
			}
		}
		return tip;
	}

	private PvTooltip.Line pieceRow(InventoryDecoder.Stack stack, String value, String meta) {
		PvTooltip.Line row = PvTooltip.Line.row(
			List.of(Span.of(FarmingFortuneTip.name(stack), nameColor(stack))),
			List.of(Span.of(value, PvDraw.COLOR_GOLD)),
			meta == null ? List.of() : List.of(Span.of(meta, PvDraw.COLOR_MUTED)));
		return withIcon(row, stack.id());
	}

	// By id, not a bare stack: newer gear (Blossom, Peony, vacuums) is paper in NEU and only gets its
	// texture from the item model the icon renderer looks up.
	static PvTooltip.Line withIcon(PvTooltip.Line row, String id) {
		return id == null || id.isBlank() ? row : row.withIcon(null, id);
	}

	private static PvTooltip.Line valueRow(String label, Line line, String meta) {
		return PvTooltip.Line.row(
			List.of(Span.of(label, line.kind() == Kind.UNAVAILABLE ? PvDraw.COLOR_MUTED : PvDraw.COLOR_TEXT)),
			List.of(Span.of(valueText(line), valueColor(line))),
			meta == null || meta.isEmpty() ? List.of() : List.of(Span.of(meta, PvDraw.COLOR_MUTED)));
	}

	private static PvTooltip.Line summaryRow(String label, double value) {
		return PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, plus(value), PvDraw.COLOR_GOLD);
	}

	private static String valueText(Line line) {
		return line.kind() == Kind.UNAVAILABLE ? "-" : plus(line.value());
	}

	private static int valueColor(Line line) {
		return line.kind() == Kind.COUNTED && line.value() > 0D ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED;
	}

	private static String plus(double value) {
		return "+" + FarmingFortuneTip.format(value);
	}

	private static String pieces(int count, String none) {
		return count == 0 ? none : count == 1 ? "1 piece" : count + " pieces";
	}

	private static int nameColor(InventoryDecoder.Stack stack) {
		return stack == null || stack.displayName() == null
			? PvDraw.COLOR_TEXT
			: SkyBlockItemFactory.tierArgbFromFormattedName(stack.displayName());
	}

	private Icon categoryIcon(FarmingFortuneSummary.Group group, FarmingFortuneEstimate est) {
		return switch (group.category()) {
			case SKILLS -> vanilla(Items.GOLDEN_HOE);
			case GARDEN -> vanilla(Items.GRASS_BLOCK);
			case SHARDS -> {
				for (Line line : group.lines()) {
					String key = FarmingFortuneSummary.shardKey(line.label());
					if (key != null) {
						yield shardIcon(key);
					}
				}
				yield vanilla(Items.PRISMARINE_SHARD);
			}
			case ACCESSORIES -> {
				InventoryDecoder.Stack fermento = est.fermento();
				if (fermento != null && "HELIANTHUS_RELIC".equalsIgnoreCase(fermento.id())) {
					yield stack(fermento);
				}
				yield est.accessories().isEmpty() ? vanilla(Items.EMERALD) : stack(est.accessories().get(0).stack());
			}
		};
	}

	private Icon stack(InventoryDecoder.Stack stack) {
		return stack(stack.id());
	}

	private Icon stack(String id) {
		return (g, x, y) -> SkyBlockIconRenderer.draw(g, iconStack(id), id, x, y, ICON);
	}

	private static String shardIconId(String key) {
		AttributeShardsData.Def def = AttributeShardsData.def(key);
		return def != null ? def.iconId() : SHARD_PREFIX + key.toUpperCase(Locale.ROOT) + ";1";
	}

	// The paired day and night row only takes a rarity colour when both shards share it.
	private static int shardColor(String label) {
		if ("Firefly and Lunar Moth shards".equals(label)) {
			int day = shardRarityColor("solar_power");
			return day == shardRarityColor("lunar_power") ? day : PvDraw.COLOR_TEXT;
		}
		String key = FarmingFortuneSummary.shardKey(label);
		return key == null ? PvDraw.COLOR_TEXT : shardRarityColor(key);
	}

	private static int shardRarityColor(String key) {
		AttributeShardsData.Def def = AttributeShardsData.def(key);
		return def == null ? PvDraw.COLOR_TEXT : SkyBlockItemFactory.tierArgb(def.rarity());
	}

	private static Icon shardIcon(String key) {
		return (g, x, y) -> {
			AttributeShardsData.Def def = AttributeShardsData.def(key);
			ItemStack icon = def == null ? null : SkyBlockItemFactory.iconStack(def.iconId());
			if (icon == null || icon.isEmpty() || icon.is(Items.BARRIER)) {
				GardenUi.drawStack(g, new ItemStack(Items.PRISMARINE_SHARD), x, y, ICON);
				return;
			}
			SkyBlockIconRenderer.draw(g, icon, def.iconId(), x, y, ICON);
		};
	}

	private static Icon vanilla(Item item) {
		return (g, x, y) -> GardenUi.drawStack(g, new ItemStack(item), x, y, ICON);
	}

	private static ItemStack iconStack(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		ItemStack stack = SkyBlockItemFactory.iconStack(id);
		return stack.isEmpty() ? null : stack;
	}

	private boolean visible(int my) {
		return my >= this.clipTop && my < this.clipBottom;
	}

	// Tooltips are only built for the hovered zone, clipped to the scroll area.
	private void addZone(GardenUi ui, int x, int y, int w, int h, int mx, int my, Supplier<List<PvTooltip.Line>> tip) {
		int y0 = Math.max(y, this.clipTop);
		int y1 = Math.min(y + h, this.clipBottom);
		if (y1 > y0 && mx >= x && mx < x + w && my >= y0 && my < y1) {
			ui.zones.add(new HoverZone(x, y0, w, y1 - y0, tip.get()));
		}
	}

	static String cropId(String crop) {
		return crop.toUpperCase(Locale.ROOT).replace(' ', '_');
	}
}

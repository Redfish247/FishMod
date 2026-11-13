/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.AttributeShardsData
import fishmod.pv.data.FarmingFortuneEstimate
import fishmod.pv.data.FarmingFortuneEstimate.Garden
import fishmod.pv.data.FarmingFortuneEstimate.Kind
import fishmod.pv.data.FarmingFortuneEstimate.Line
import fishmod.pv.data.FarmingFortuneSummary
import fishmod.pv.data.FarmingFortuneTemporary
import fishmod.pv.data.FarmingFortuneTemporary.Source
import fishmod.pv.data.FarmingFortuneTemporary.Status
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.PvTooltip.Span
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.COMPLETED_C
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.ITEM_SLOT_BG
import fishmod.pv.gui.garden.GardenUi.Companion.ITEM_SLOT_BORDER
import fishmod.pv.gui.garden.GardenUi.Companion.ITEM_SLOT_HOVER
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.PANEL_HOVER
import fishmod.pv.gui.garden.GardenUi.HoverZone
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.CROP_SLOT
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.HEADER_H
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.HERO_H
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.Rect
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.SECTION_GAP
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.Section
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.TILE_GAP
import fishmod.pv.gui.garden.page.FarmingFortuneLayout.TILE_H
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale
import kotlin.math.floor
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class FarmingFortunePage {
	private fun interface Icon {
		fun draw(g: GuiGraphicsExtractor, x: Int, y: Int)
	}

	private var open = false
	private var crop: String = FarmingFortuneEstimate.CROPS[0]
	private var scroll = 0
	private var maxScroll = 0
	private var bodyTop = 0
	private var bodyH = 0
	private var backX = 0
	private var backY = 0
	private val cropHits = ArrayList<Rect>()
	private var clipTop = 0
	private var clipBottom = 0

	fun isOpen(): Boolean = open

	fun open() {
		open = true
		scroll = 0
	}

	fun close() {
		open = false
	}

	fun selectedCrop(): String = crop

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (!open) {
			return false
		}
		if (mx >= backX && mx < backX + BACK && my >= backY && my < backY + BACK) {
			close()
			return true
		}
		for ((i, hit) in cropHits.withIndex()) {
			if (hit.contains(floor(mx).toInt(), floor(my).toInt())) {
				crop = FarmingFortuneEstimate.CROPS[i]
				return true
			}
		}
		return false
	}

	fun mouseScrolled(mouseY: Double, scrollY: Double): Boolean {
		if (!open || maxScroll <= 0 || mouseY < bodyTop || mouseY >= bodyTop + bodyH) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll + (if (scrollY > 0) -14 else 14)))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	fun render(
		snap: GardenSnapshot, stats: PlayerStatsSnapshot?, ui: GardenUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int
	) {
		cropHits.clear()
		PvDraw.innerPanel(g, x, y, w, h)
		val cx = x + PAD
		val cw = w - PAD * 2
		var cy = y + PAD

		backX = cx
		backY = cy
		val backHover = mx >= cx && mx < cx + BACK && my >= cy && my < cy + BACK
		PvDraw.roundedFill(g, cx, cy, BACK, BACK, 3, if (backHover) 0xFF3A3A4A.toInt() else 0x24FFFFFF)
		PvDraw.roundedRing(g, cx, cy, BACK, BACK, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(g, font, "<", cx + BACK / 2, cy + (BACK - font.lineHeight) / 2 + 1, PvDraw.COLOR_TEXT)
		if (backHover) {
			ui.zones.add(HoverZone(cx, cy, BACK, BACK, listOf(PvTooltip.Line.of("Back to overview", PvDraw.COLOR_TEXT))))
		}
		PvDraw.textBold(g, font, "Farming Fortune", cx + BACK + 6, cy + 3, PvDraw.COLOR_GOLD)
		val tag = "Estimate"
		PvDraw.textRight(g, font, tag, cx + cw, cy + 3, PvDraw.COLOR_MUTED)
		ui.zones.add(HoverZone(
			cx + cw - font.width(tag), cy, font.width(tag), BACK, listOf(
				PvTooltip.Line.title("Estimate", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Uses the best farming setup found on", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("the profile. The API only has what was", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("last saved, so this is not a live stat.", PvDraw.COLOR_TEXT)
			)
		))
		cy += BACK + 6

		val est = stats?.farmingFortune
		if (est == null) {
			PvDraw.textCentered(g, font, "Inventories still loading...", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			maxScroll = 0
			return
		}
		val garden = Garden.of(snap)
		val temporary = est.temporary(crop, garden, System.currentTimeMillis())
		val heroBottom = drawHero(est, garden, temporary, ui, g, font, cx, cy, cw, mx, my)

		bodyTop = heroBottom + 8
		bodyH = maxOf(20, y + h - PAD - bodyTop)
		clipTop = bodyTop
		clipBottom = bodyTop + bodyH

		g.enableScissor(x + 1, bodyTop, x + w - 1, bodyTop + bodyH)
		val top = bodyTop - scroll
		val columns = FarmingFortuneLayout.columnCount(cw)
		val cols = FarmingFortuneLayout.columns(cx, cw, columns)
		val plan = FarmingFortuneLayout.sections(columns)
		var bottom = top
		for (i in 0 until columns) {
			val col = cols[i]
			var yy = top
			for (s in plan[i]) {
				yy = drawSection(s, est, garden, snap, ui, g, font, col.x, yy, col.w, mx, my) + SECTION_GAP
			}
			bottom = maxOf(bottom, yy - SECTION_GAP)
		}
		bottom = drawTemporary(est, garden, temporary, ui, g, font, cx, bottom + SECTION_GAP + 2, cw, mx, my)
		g.disableScissor()
		maxScroll = maxOf(0, bottom - top - bodyH)
		scroll = minOf(scroll, maxScroll)
	}

	private fun drawHero(
		est: FarmingFortuneEstimate, garden: Garden, temporary: List<Source>, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, y: Int, w: Int, mx: Int, my: Int
	): Int {
		val general = est.general(garden)
		val total = est.forCrop(crop, garden)
		val active = FarmingFortuneTemporary.activeTotal(temporary)
		val big = FarmingFortuneTip.format(total)
		val bigW = font.width(big) * 2
		val label = "$crop Fortune"
		val sub = "General " + FarmingFortuneTip.format(general)
		val heroW = bigW + 6 + maxOf(font.width(label), font.width(sub))
		val slots = FarmingFortuneLayout.cropSlots(x, y, w, heroW, FarmingFortuneEstimate.CROPS.size)
		val textRoom = maxOf(8, (if (FarmingFortuneLayout.cropsBesideHero(w, heroW, slots.size)) heroW else w) - bigW - 6)

		PvDraw.textScaled(g, font, big, x, y + 3, PvDraw.COLOR_GOLD, 2F)
		PvDraw.text(g, font, GardenUi.trim(font, label, textRoom), x + bigW + 6, y + 2, PvDraw.COLOR_TEXT)
		PvDraw.text(g, font, GardenUi.trim(font, sub, textRoom), x + bigW + 6, y + 12, PvDraw.COLOR_MUTED)

		val cropPet = est.bestPet(crop, garden)
		val summary = mutableListOf(
			PvTooltip.Line.title(label, PvDraw.COLOR_GOLD),
			PvTooltip.Line.divider(),
			summaryRow("Permanent", est.permanentTotal(garden)),
			summaryRow("Armour", est.armourTotal()),
			summaryRow("Equipment", est.equipmentTotal()),
			summaryRow(cropPet?.pet?.displayName ?: "Pet", est.petTotal(crop, garden)),
			summaryRow(crop, est.cropBonus(crop, garden)),
			PvTooltip.Line.divider(),
			PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, FarmingFortuneTip.format(total), PvDraw.COLOR_GOLD)
		)
		if (active > 0.0) {
			summary.add(PvTooltip.Line.row("Active buffs", PvDraw.COLOR_MUTED, plus(active), COMPLETED_C))
			summary.add(PvTooltip.Line.row("With buffs", PvDraw.COLOR_TEXT, FarmingFortuneTip.format(total + active), COMPLETED_C))
		}
		summary.add(PvTooltip.Line.blank())
		summary.add(PvTooltip.Line.meta("General " + FarmingFortuneTip.format(general) + " applies to every crop."))
		ui.zones.add(HoverZone(x, y, minOf(heroW, w), HERO_H, summary))

		for ((i, r) in slots.withIndex()) {
			val name = FarmingFortuneEstimate.CROPS[i]
			cropHits.add(r)
			val selected = name == crop
			val hovered = r.contains(mx, my)
			PvDraw.roundedFill(g, r.x, r.y, r.w, r.h, 3, ITEM_SLOT_BG)
			val ring = when {
				selected -> PvDraw.COLOR_ACCENT
				hovered -> ITEM_SLOT_HOVER
				else -> ITEM_SLOT_BORDER
			}
			PvDraw.roundedRing(g, r.x, r.y, r.w, r.h, 3, ring, PvDraw.C_ALL)
			val id = cropId(name)
			GardenUi.drawIcon(g, GardenData.cropIconId(id), r.x + 1, r.y + 1, ICON, GardenData.cropPackModel(id))
			if (hovered) {
				ui.zones.add(HoverZone(
					r.x, r.y, r.w, r.h, listOf(
						PvTooltip.Line.title(name, PvDraw.COLOR_TEXT),
						PvTooltip.Line.divider(),
						PvTooltip.Line.row("Fortune", PvDraw.COLOR_MUTED, FarmingFortuneTip.format(est.forCrop(name, garden)), PvDraw.COLOR_GOLD),
						PvTooltip.Line.meta(if (selected) "Selected" else "Click to select")
					)
				))
			}
		}
		return FarmingFortuneLayout.heroBottom(y, slots)
	}

	private fun drawSection(
		section: Section, est: FarmingFortuneEstimate, garden: Garden, snap: GardenSnapshot, ui: GardenUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mx: Int, my: Int
	): Int = when (section) {
		Section.CROP -> drawCrop(est, garden, snap, ui, g, font, x, y, w, mx, my)
		Section.GEAR -> drawGear(est, ui, g, font, x, y, w, mx, my)
		Section.PET -> drawPet(est, garden, ui, g, font, x, y, w, mx, my)
		Section.PERMANENT -> drawPermanent(est, garden, ui, g, font, x, y, w, mx, my)
	}

	private fun drawCrop(
		est: FarmingFortuneEstimate, garden: Garden, snap: GardenSnapshot, ui: GardenUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, startY: Int, w: Int, mx: Int, my: Int
	): Int {
		var y = header(g, font, crop, plus(est.cropBonus(crop, garden)), x, startY, w)
		val lines = est.cropLines(crop, garden)
		y = toolTile(est.tool(crop, garden), lines[0], ui, g, font, x, y, w, mx, my)
		for (line in lines.subList(1, lines.size)) {
			val r = Rect(x, y, w, TILE_H)
			when (line.label) {
				"Crop upgrade" -> {
					val id = cropId(crop)
					tile(
						g, font, ui, r,
						{ gg, ix, iy -> GardenUi.drawIcon(gg, GardenData.cropIconId(id), ix, iy, ICON, GardenData.cropPackModel(id)) },
						"Crop upgrade", PvDraw.COLOR_TEXT, line.note, line, { upgradeTip(line, snap) }, mx, my
					)
				}
				"Personal best" -> {
					val pb = est.personalBest(crop)
					val detail = when {
						!est.personalBestsUnlocked() -> "Perk not unlocked"
						pb > 0L -> FormatUtil.shortXp(pb.toDouble()) + " crops"
						else -> "No score yet"
					}
					tile(
						g, font, ui, r, vanilla(Items.GOLD_INGOT), "Personal best", PvDraw.COLOR_TEXT, detail, line,
						{ personalBestTip(est, line, pb) }, mx, my
					)
				}
				"Gear crop fortune" -> tile(
					g, font, ui, r, vanilla(Items.GOLDEN_BOOTS), "Gear bonus", PvDraw.COLOR_TEXT,
					"Armour and equipment", line, { gearCropTip(est, line) }, mx, my
				)
				else -> {
					val acc = est.fermento
					tile(
						g, font, ui, r, acc?.let { stack(it) }, line.label, nameColor(acc), "Crop accessory", line,
						{
							listOf(
								PvTooltip.Line.title(line.label, nameColor(acc)),
								PvTooltip.Line.divider(),
								valueRow("$crop Fortune", line, null)
							)
						}, mx, my
					)
				}
			}
			y += TILE_H + TILE_GAP
		}
		return y - TILE_GAP
	}

	private fun toolTile(
		tool: FarmingFortuneEstimate.Tool?, toolLine: Line, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, y: Int, w: Int, mx: Int, my: Int
	): Int {
		val r = Rect(x, y, w, TILE_H)
		if (tool == null) {
			val explanation = FarmingFortuneSummary.missingToolExplanation(crop, toolLine)
			val unknown = toolLine.kind == Kind.UNAVAILABLE
			tile(
				g, font, ui, r, null, "Tool", PvDraw.COLOR_MUTED, FarmingFortuneSummary.missingToolDetail(toolLine),
				if (unknown) null else toolLine,
				{
					listOf(
						PvTooltip.Line.title("$crop tool", PvDraw.COLOR_TEXT),
						PvTooltip.Line.divider(),
						PvTooltip.Line.of(explanation, PvDraw.COLOR_TEXT),
						PvTooltip.Line.meta(if (unknown) "Not counted, the player may still own one." else "Counted as +0.")
					)
				}, mx, my
			)
			return y + TILE_H + TILE_GAP
		}
		val stack = tool.stack
		val color = nameColor(stack)
		tile(
			g, font, ui, r, stack(stack), FarmingFortuneSummary.shortToolName(stack), color,
			if (tool.fromToolkit) "Farming Toolkit" else "Inventory", toolLine,
			{
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.title(FarmingFortuneTip.name(stack), color))
				tip.add(PvTooltip.Line.divider())
				for (part in tool.parts) {
					tip.add(valueRow(part.label, part, part.note))
				}
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(tool.total()), PvDraw.COLOR_GOLD))
				tip
			}, mx, my
		)
		return y + TILE_H + TILE_GAP
	}

	private fun drawGear(est: FarmingFortuneEstimate, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, startY: Int, w: Int, mx: Int, my: Int): Int {
		var y = header(g, font, "Gear", plus(est.armourTotal() + est.equipmentTotal()), x, startY, w)
		val armour = est.armour
		val armourDetail = if (est.setBonuses.isNotEmpty()) {
			val set = est.setBonuses[0]
			set.name + " " + set.pieces + "/4"
		} else {
			pieces(armour.size, "No farming armour")
		}
		val armourLine = Line("Armour", est.armourTotal(), Kind.COUNTED, "")
		tile(
			g, font, ui, Rect(x, y, w, TILE_H), if (armour.isEmpty()) null else stack(armour[0].stack), "Armour", PvDraw.COLOR_TEXT,
			armourDetail, armourLine,
			{
				val sets = est.setBonuses.map { s ->
					PvTooltip.Line.row(
						listOf(Span.of(s.name + " set bonus", PvDraw.COLOR_TEXT)),
						listOf(Span.of(plus(s.fortune), PvDraw.COLOR_GOLD)),
						listOf(Span.of(s.pieces.toString() + " pieces", PvDraw.COLOR_MUTED))
					)
				}
				piecesTip("Armour", armour, sets, est.armourTotal())
			}, mx, my
		)
		y += TILE_H + TILE_GAP
		val equipment = est.equipment
		val equipmentLine = Line("Equipment", est.equipmentTotal(), Kind.COUNTED, "")
		tile(
			g, font, ui, Rect(x, y, w, TILE_H), if (equipment.isEmpty()) null else stack(equipment[0].stack), "Equipment",
			PvDraw.COLOR_TEXT, pieces(equipment.size, "No farming equipment"), equipmentLine,
			{ piecesTip("Equipment", equipment, listOf(), est.equipmentTotal()) }, mx, my
		)
		return y + TILE_H
	}

	private fun drawPet(
		est: FarmingFortuneEstimate, garden: Garden, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, startY: Int, w: Int, mx: Int, my: Int
	): Int {
		val y = header(g, font, "Pet", plus(est.petTotal(crop, garden)), x, startY, w)
		val pets = est.pets(crop, garden)
		val r = Rect(x, y, w, TILE_H)
		if (pets.isEmpty()) {
			tile(
				g, font, ui, r, null, "Pet", PvDraw.COLOR_MUTED, "No farming pet", Line("Pet", 0.0, Kind.COUNTED, ""),
				{
					listOf(
						PvTooltip.Line.title("Pet", PvDraw.COLOR_TEXT),
						PvTooltip.Line.divider(),
						PvTooltip.Line.of("No pets that give farming fortune.", PvDraw.COLOR_TEXT)
					)
				}, mx, my
			)
			return y + TILE_H
		}
		val best = pets[0]
		val color = SkyBlockItemFactory.tierArgb(best.pet.tier)
		val detail = "Level " + best.pet.level + (best.item?.let { ", " + it.label } ?: "")
		tile(
			g, font, ui, r, stack(best.pet.neuId), best.pet.displayName, color, detail,
			Line("Pet", best.total(), Kind.COUNTED, ""), { petTip(best, pets, color) }, mx, my
		)
		return y + TILE_H
	}

	private fun drawPermanent(
		est: FarmingFortuneEstimate, garden: Garden, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, startY: Int, w: Int, mx: Int, my: Int
	): Int {
		var y = header(g, font, "Permanent", plus(est.permanentTotal(garden)), x, startY, w)
		for (group in FarmingFortuneSummary.permanentGroups(est, garden)) {
			val detail = FarmingFortuneSummary.detail(group)
			val line = Line(group.category.label, group.total, Kind.COUNTED, "")
			tile(
				g, font, ui, Rect(x, y, w, TILE_H), categoryIcon(group, est), group.category.label, PvDraw.COLOR_TEXT,
				detail.ifEmpty { "None" }, line, { groupTip(group, est) }, mx, my
			)
			y += TILE_H + TILE_GAP
		}
		return y - TILE_GAP
	}

	private fun drawTemporary(
		est: FarmingFortuneEstimate, garden: Garden, temporary: List<Source>, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, startY: Int, w: Int, mx: Int, my: Int
	): Int {
		if (temporary.isEmpty()) {
			return startY - SECTION_GAP - 2
		}
		var y = startY
		val active = FarmingFortuneTemporary.activeTotal(temporary)
		val possible = FarmingFortuneTemporary.possibleTotal(temporary)
		header(
			g, font, "Temporary", if (active > 0.0) plus(active) + " active" else "None active", x, y, w,
			if (active > 0.0) COMPLETED_C else PvDraw.COLOR_MUTED
		)
		addZone(ui, x, y, w, HEADER_H, mx, my) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Temporary", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			tip.add(
				if (active > 0.0) PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, plus(active), COMPLETED_C)
				else PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, "None", PvDraw.COLOR_MUTED)
			)
			if (possible > 0.0) {
				tip.add(PvTooltip.Line.row("Unconfirmed", PvDraw.COLOR_MUTED, "up to " + plus(possible), UNCONFIRMED_C))
			}
			tip.add(PvTooltip.Line.blank())
			tip.add(PvTooltip.Line.meta("Not in the totals above."))
			tip
		}
		y += HEADER_H
		val pet = est.bestPet(crop, garden)
		val widths = IntArray(temporary.size) { CROP_SLOT + 4 + font.width(plus(temporary[it].value)) + 4 }
		val chips = FarmingFortuneLayout.chips(x, y, w, widths)
		var bottom = y
		for ((i, s) in temporary.withIndex()) {
			val r = chips[i]
			val status = statusColor(s.status)
			val name = sourceColor(s, pet)
			val hovered = visible(my) && r.contains(mx, my)
			PvDraw.roundedFill(g, r.x, r.y, r.w, r.h, 3, if (hovered) 0x30FFFFFF else PvDraw.CELL)
			val ring = when {
				hovered -> ITEM_SLOT_HOVER
				s.status == Status.ACTIVE -> ACTIVE_BORDER
				else -> ITEM_SLOT_BORDER
			}
			PvDraw.roundedRing(g, r.x, r.y, r.w, r.h, 3, ring, PvDraw.C_ALL)
			sourceIcon(s).draw(g, r.x + 1, r.y + 1)
			if (s.status == Status.INACTIVE) {
				PvDraw.fill(g, r.x + 1, r.y + 1, ICON, ICON, 0x99101018.toInt())
			}
			val text = GardenUi.trim(font, plus(s.value), maxOf(8, r.w - CROP_SLOT - 6))
			PvDraw.text(g, font, text, r.x + CROP_SLOT + 3, r.y + (r.h - font.lineHeight) / 2 + 1, status)
			addZone(ui, r.x, r.y, r.w, r.h, mx, my) { temporaryTip(s, name, status) }
			bottom = maxOf(bottom, r.bottom())
		}
		return bottom
	}

	private fun temporaryTip(s: Source, nameColor: Int, statusColor: Int): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title(s.label, nameColor))
		tip.add(PvTooltip.Line.divider())
		val stat = if (s.crop) "$crop Fortune" else "Farming Fortune"
		if (s.parts.isNotEmpty()) {
			for (part in s.parts) {
				tip.add(valueRow(part.label, part, part.note))
			}
			tip.add(PvTooltip.Line.divider())
		}
		tip.add(PvTooltip.Line.row(stat, if (s.parts.isEmpty()) PvDraw.COLOR_MUTED else PvDraw.COLOR_TEXT, plus(s.value), PvDraw.COLOR_GOLD))
		tip.add(PvTooltip.Line.row(
			listOf(Span.of("Status", PvDraw.COLOR_MUTED)),
			listOf(Span.of(statusText(s.status), statusColor)),
			if (s.state.isEmpty()) listOf() else listOf(Span.of(s.state, PvDraw.COLOR_MUTED))
		))
		tip.add(PvTooltip.Line.blank())
		tip.add(PvTooltip.Line.of(s.condition, PvDraw.COLOR_TEXT))
		return tip
	}

	private fun sourceIcon(s: Source): Icon {
		val key = shardKeyOf(s.iconId)
		return if (key != null) shardIcon(key) else stack(s.iconId)
	}

	private fun tile(
		g: GuiGraphicsExtractor, font: Font, ui: GardenUi, r: Rect, icon: Icon?, title: String, titleColor: Int,
		detail: String?, line: Line?, tip: () -> List<PvTooltip.Line>, mx: Int, my: Int
	) {
		val hovered = visible(my) && r.contains(mx, my)
		if (hovered) {
			PvDraw.fill(g, r.x, r.y, r.w, r.h, PANEL_HOVER)
		}
		val slotY = r.y + (r.h - CROP_SLOT) / 2
		PvDraw.roundedFill(g, r.x, slotY, CROP_SLOT, CROP_SLOT, 3, ITEM_SLOT_BG)
		PvDraw.roundedRing(g, r.x, slotY, CROP_SLOT, CROP_SLOT, 3, if (hovered) ITEM_SLOT_HOVER else ITEM_SLOT_BORDER, PvDraw.C_ALL)
		icon?.draw(g, r.x + 1, slotY + 1)
		val tx = r.x + CROP_SLOT + 4
		var valueW = 0
		if (line != null) {
			val value = valueText(line)
			valueW = font.width(value) + 4
			PvDraw.textRight(g, font, value, r.right(), r.y + 1, valueColor(line))
		}
		PvDraw.text(g, font, GardenUi.trim(font, title, maxOf(8, r.right() - valueW - tx)), tx, r.y + 1, titleColor)
		if (!detail.isNullOrEmpty()) {
			PvDraw.text(g, font, GardenUi.trim(font, detail, maxOf(8, r.right() - tx)), tx, r.y + 11, PvDraw.COLOR_MUTED)
		}
		addZone(ui, r.x, r.y, r.w, r.h, mx, my, tip)
	}

	private fun upgradeTip(line: Line, snap: GardenSnapshot?): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("$crop upgrade", PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		tip.add(valueRow("Fortune", line, line.note))
		val row = if (snap != null && snap.islandLoaded) snap.crops.lastOrNull { it.name == crop } else null
		if (row != null) {
			tip.add(PvTooltip.Line.row(
				"Milestone", PvDraw.COLOR_MUTED,
				row.milestone.toString() + (if (row.milestoneMaxed) " (MAX)" else ""),
				if (row.milestoneMaxed) COMPLETED_C else PvDraw.COLOR_TEXT
			))
		}
		if (line.kind == Kind.UNAVAILABLE) {
			tip.add(PvTooltip.Line.meta("Garden not loaded, so not counted yet."))
		}
		return tip
	}

	private fun personalBestTip(est: FarmingFortuneEstimate, line: Line, pb: Long): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("$crop personal best", PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		tip.add(valueRow("Fortune", line, null))
		if (est.personalBestsUnlocked()) {
			tip.add(PvTooltip.Line.row("Best contest", PvDraw.COLOR_MUTED, if (pb > 0L) FormatUtil.commas(pb) else "-", PvDraw.COLOR_TEXT))
		} else {
			tip.add(PvTooltip.Line.meta("Needs the Personal Bests perk from Jacob."))
		}
		return tip
	}

	private fun gearCropTip(est: FarmingFortuneEstimate, line: Line): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("$crop gear bonus", PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		for (p in est.armour + est.equipment) {
			val v = p.cropFortune[crop] ?: 0.0
			if (v > 0.0) {
				tip.add(pieceRow(p.stack, plus(v), null))
			}
		}
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(line.value), PvDraw.COLOR_GOLD))
		return tip
	}

	private fun piecesTip(
		title: String, pieces: List<FarmingFortuneEstimate.Piece>, extra: List<PvTooltip.Line>, total: Double
	): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title(title, PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		if (pieces.isEmpty()) {
			tip.add(PvTooltip.Line.of("None found in inventories.", PvDraw.COLOR_MUTED))
		}
		for (p in pieces) {
			val meta = if (p.pieceBonus > 0.0) "incl. " + plus(p.pieceBonus) + " piece bonus" else null
			tip.add(pieceRow(p.stack, plus(p.total()), meta))
		}
		tip.addAll(extra)
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(total), PvDraw.COLOR_GOLD))
		return tip
	}

	private fun petTip(best: FarmingFortuneEstimate.PetPick, pets: List<FarmingFortuneEstimate.PetPick>, color: Int): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("[Lvl " + best.pet.level + "] " + best.pet.displayName, color))
		tip.add(PvTooltip.Line.divider())
		val lines = ArrayList(best.lines)
		best.item?.let { lines.add(it) }
		for (line in lines) {
			val meta = if (line.kind == Kind.COUNTED) null else line.note
			tip.add(valueRow(line.label, line, if (meta.isNullOrEmpty()) null else meta))
		}
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(best.total()), PvDraw.COLOR_GOLD))
		if (pets.size > 1) {
			tip.add(PvTooltip.Line.blank())
			tip.add(PvTooltip.Line.meta("Other pets"))
			for (i in 1 until minOf(pets.size, MAX_OTHER_PETS + 1)) {
				val p = pets[i]
				tip.add(withIcon(
					PvTooltip.Line.row(p.pet.displayName, SkyBlockItemFactory.tierArgb(p.pet.tier), plus(p.total()), PvDraw.COLOR_MUTED),
					p.pet.neuId
				))
			}
		}
		return tip
	}

	private fun groupTip(group: FarmingFortuneSummary.Group, est: FarmingFortuneEstimate): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title(group.category.label, PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		val fermento = est.fermento
		for (line in group.lines) {
			val shard = FarmingFortuneSummary.shardKey(line.label)
			if (line.label == "Accessories") {
				for (a in est.accessories) {
					tip.add(pieceRow(a.stack, plus(a.fortune), null))
				}
			} else if (line.label == "Helianthus Relic" && fermento != null) {
				tip.add(pieceRow(fermento, valueText(line), null))
			} else if (shard != null) {
				tip.add(withIcon(
					PvTooltip.Line.row(
						listOf(Span.of(line.label, shardColor(line.label))),
						listOf(Span.of(valueText(line), valueColor(line))),
						if (line.note.isEmpty()) listOf() else listOf(Span.of(line.note, PvDraw.COLOR_MUTED))
					),
					shardIconId(shard)
				))
			} else {
				tip.add(valueRow(line.label, line, line.note))
			}
		}
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_TEXT, plus(group.total), PvDraw.COLOR_GOLD))
		if (group.lines.any { it.kind == Kind.UNAVAILABLE }) {
			tip.add(PvTooltip.Line.meta("Sources marked - are not counted yet."))
		}
		return tip
	}

	private fun pieceRow(stack: InventoryDecoder.Stack, value: String, meta: String?): PvTooltip.Line {
		val row = PvTooltip.Line.row(
			listOf(Span.of(FarmingFortuneTip.name(stack), nameColor(stack))),
			listOf(Span.of(value, PvDraw.COLOR_GOLD)),
			if (meta == null) listOf() else listOf(Span.of(meta, PvDraw.COLOR_MUTED))
		)
		return withIcon(row, stack.id)
	}

	private fun categoryIcon(group: FarmingFortuneSummary.Group, est: FarmingFortuneEstimate): Icon = when (group.category) {
		FarmingFortuneSummary.Category.SKILLS -> vanilla(Items.GOLDEN_HOE)
		FarmingFortuneSummary.Category.GARDEN -> vanilla(Items.GRASS_BLOCK)
		FarmingFortuneSummary.Category.SHARDS -> {
			val key = group.lines.firstNotNullOfOrNull { FarmingFortuneSummary.shardKey(it.label) }
			if (key != null) shardIcon(key) else vanilla(Items.PRISMARINE_SHARD)
		}
		FarmingFortuneSummary.Category.ACCESSORIES -> {
			val fermento = est.fermento
			when {
				fermento != null && "HELIANTHUS_RELIC".equals(fermento.id, ignoreCase = true) -> stack(fermento)
				est.accessories.isEmpty() -> vanilla(Items.EMERALD)
				else -> stack(est.accessories[0].stack)
			}
		}
	}

	private fun stack(stack: InventoryDecoder.Stack): Icon = stack(stack.id)

	private fun stack(id: String?): Icon = Icon { g, x, y -> SkyBlockIconRenderer.draw(g, iconStack(id), id, x, y, ICON) }

	private fun visible(my: Int): Boolean = my >= clipTop && my < clipBottom

	private fun addZone(ui: GardenUi, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int, tip: () -> List<PvTooltip.Line>) {
		val y0 = maxOf(y, clipTop)
		val y1 = minOf(y + h, clipBottom)
		if (y1 > y0 && mx >= x && mx < x + w && my >= y0 && my < y1) {
			ui.zones.add(HoverZone(x, y0, w, y1 - y0, tip()))
		}
	}

	companion object {
		private const val BACK = 14
		private const val MAX_OTHER_PETS = 3
		private const val UNCONFIRMED_C = 0xFFBFA36A.toInt()
		private const val ACTIVE_BORDER = 0xFF3C7A3C.toInt()
		private const val ULTIMATE_C = 0xFFFF55FF.toInt()
		private const val SHARD_PREFIX = "ATTRIBUTE_SHARD_"
		private val PLAIN_SOURCES = setOf("PESTHUNTER_PHILLIP_NPC", "OVERDRIVE_GARDEN_CHIP")

		@JvmStatic
		fun statusText(status: Status): String = when (status) {
			Status.ACTIVE -> "Active"
			Status.UNKNOWN -> "Unconfirmed"
			Status.INACTIVE -> "Inactive"
		}

		private fun statusColor(status: Status): Int = when (status) {
			Status.ACTIVE -> COMPLETED_C
			Status.UNKNOWN -> UNCONFIRMED_C
			Status.INACTIVE -> PvDraw.COLOR_MUTED
		}

		private fun sourceColor(s: Source, pet: FarmingFortuneEstimate.PetPick?): Int {
			val id = s.iconId
			if (id in PLAIN_SOURCES) {
				return PvDraw.COLOR_TEXT
			}
			if (id.startsWith("ULTIMATE_")) {
				return ULTIMATE_C
			}
			if (pet != null && id == pet.pet.neuId) {
				return SkyBlockItemFactory.tierArgb(pet.pet.tier)
			}
			val key = shardKeyOf(id)
			if (key != null) {
				return shardRarityColor(key)
			}
			return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(id))
		}

		@JvmStatic
		fun shardKeyOf(id: String?): String? {
			if (id == null || !id.startsWith(SHARD_PREFIX)) {
				return null
			}
			val semi = id.indexOf(';')
			return id.substring(SHARD_PREFIX.length, if (semi < 0) id.length else semi).lowercase(Locale.ROOT)
		}

		private fun header(g: GuiGraphicsExtractor, font: Font, label: String, value: String, x: Int, y: Int, w: Int, valueColor: Int = PvDraw.COLOR_GOLD): Int {
			val valueW = font.width(value)
			val shown = GardenUi.trim(font, label, maxOf(8, w - valueW - 8))
			PvDraw.text(g, font, shown, x, y, PvDraw.COLOR_MUTED)
			val lineX = x + font.width(shown) + 4
			val lineEnd = x + w - valueW - 4
			if (lineEnd > lineX) {
				PvDraw.fill(g, lineX, y + font.lineHeight / 2 - 1, lineEnd - lineX, 1, PvDraw.COLOR_DIVIDER)
			}
			PvDraw.textRight(g, font, value, x + w, y, valueColor)
			return y + HEADER_H
		}

		@JvmStatic
		fun withIcon(row: PvTooltip.Line, id: String?): PvTooltip.Line = if (id.isNullOrBlank()) row else row.withIcon(null, id)

		private fun valueRow(label: String, line: Line, meta: String?): PvTooltip.Line =
			PvTooltip.Line.row(
				listOf(Span.of(label, if (line.kind == Kind.UNAVAILABLE) PvDraw.COLOR_MUTED else PvDraw.COLOR_TEXT)),
				listOf(Span.of(valueText(line), valueColor(line))),
				if (meta.isNullOrEmpty()) listOf() else listOf(Span.of(meta, PvDraw.COLOR_MUTED))
			)

		private fun summaryRow(label: String, value: Double): PvTooltip.Line =
			PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, plus(value), PvDraw.COLOR_GOLD)

		private fun valueText(line: Line): String = if (line.kind == Kind.UNAVAILABLE) "-" else plus(line.value)

		private fun valueColor(line: Line): Int =
			if (line.kind == Kind.COUNTED && line.value > 0.0) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED

		private fun plus(value: Double): String = "+" + FarmingFortuneTip.format(value)

		private fun pieces(count: Int, none: String): String = when (count) {
			0 -> none
			1 -> "1 piece"
			else -> "$count pieces"
		}

		private fun nameColor(stack: InventoryDecoder.Stack?): Int {
			val name = stack?.displayName ?: return PvDraw.COLOR_TEXT
			return SkyBlockItemFactory.tierArgbFromFormattedName(name)
		}

		private fun shardIconId(key: String): String =
			AttributeShardsData.def(key)?.iconId() ?: (SHARD_PREFIX + key.uppercase(Locale.ROOT) + ";1")

		private fun shardColor(label: String): Int {
			if (label == "Firefly and Lunar Moth shards") {
				val day = shardRarityColor("solar_power")
				return if (day == shardRarityColor("lunar_power")) day else PvDraw.COLOR_TEXT
			}
			val key = FarmingFortuneSummary.shardKey(label)
			return if (key == null) PvDraw.COLOR_TEXT else shardRarityColor(key)
		}

		private fun shardRarityColor(key: String): Int {
			val def = AttributeShardsData.def(key) ?: return PvDraw.COLOR_TEXT
			return SkyBlockItemFactory.tierArgb(def.rarity)
		}

		private fun shardIcon(key: String): Icon = Icon { g, x, y ->
			val def = AttributeShardsData.def(key)
			val icon = if (def == null) null else SkyBlockItemFactory.iconStack(def.iconId())
			if (def == null || icon == null || icon.isEmpty || icon.`is`(Items.BARRIER)) {
				GardenUi.drawStack(g, ItemStack(Items.PRISMARINE_SHARD), x, y, ICON)
			} else {
				SkyBlockIconRenderer.draw(g, icon, def.iconId(), x, y, ICON)
			}
		}

		private fun vanilla(item: Item): Icon = Icon { g, x, y -> GardenUi.drawStack(g, ItemStack(item), x, y, ICON) }

		private fun iconStack(id: String?): ItemStack? {
			if (id.isNullOrBlank()) {
				return null
			}
			val stack = SkyBlockItemFactory.iconStack(id)
			return if (stack.isEmpty) null else stack
		}

		@JvmStatic
		fun cropId(crop: String): String = crop.uppercase(Locale.ROOT).replace(' ', '_')
	}
}

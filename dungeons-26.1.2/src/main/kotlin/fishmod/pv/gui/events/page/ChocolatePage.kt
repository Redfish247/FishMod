/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events.page

import fishmod.pv.data.ChocolateEmployees
import fishmod.pv.data.ChocolateFactoryData
import fishmod.pv.data.EventsSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.HoppityRabbitsData
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.events.EventsUi
import fishmod.pv.gui.events.EventsUi.Companion.COLOR_CHOCOLATE
import fishmod.pv.gui.events.EventsUi.Companion.COLOR_COMPLETE
import fishmod.pv.gui.events.EventsUi.Companion.FLIP_MS
import fishmod.pv.gui.events.EventsUi.Companion.GAP
import fishmod.pv.gui.events.EventsUi.Companion.PAD
import fishmod.pv.gui.events.EventsUi.Companion.PANEL_HOVER
import fishmod.pv.gui.events.EventsUi.Companion.RABBIT_ROW
import fishmod.pv.gui.events.EventsUi.Companion.RABBIT_SLOT
import fishmod.pv.gui.events.EventsUi.Companion.SEP_GAP
import fishmod.pv.gui.events.EventsUi.Companion.SLOT_BORDER
import fishmod.pv.gui.events.EventsUi.Companion.STAT_ROW
import fishmod.pv.gui.events.EventsUi.Companion.easeInOutCubic
import fishmod.pv.gui.events.EventsUi.Companion.formatAgo
import fishmod.pv.gui.events.EventsUi.Companion.prettyModifier
import fishmod.pv.gui.events.EventsUi.Companion.sectionSeparator
import fishmod.pv.gui.events.EventsUi.Companion.tipTitle
import fishmod.pv.gui.events.EventsUi.Companion.trim
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Chocolate Factory subpage (owns rabbit-list scroll). */
class ChocolatePage {
	private data class FactionHit(val id: String, val x: Int, val y: Int, val w: Int, val h: Int)

	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0
	private val expandedFactions = HashSet<String>()
	private val factionHits = ArrayList<FactionHit>()
	private var rightHitX = 0
	private var rightHitY = 0
	private var rightHitW = 0
	private var rightHitH = 0
	private var rabbitsFace = false
	private var flipTarget = false
	private var flipStartMs = 0L

	fun resetScroll() {
		scroll = 0
		expandedFactions.clear()
		rabbitsFace = false
		flipTarget = false
		flipStartMs = 0L
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx < rightHitX || mx >= rightHitX + rightHitW || my < rightHitY || my >= rightHitY + rightHitH) {
			return false
		}
		if (flipStartMs != 0L) {
			return true
		}
		if (rabbitsFace) {
			for (hit in factionHits) {
				if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
					if (!expandedFactions.remove(hit.id)) {
						expandedFactions.add(hit.id)
					}
					return true
				}
			}
		}
		flipTarget = !rabbitsFace
		flipStartMs = System.currentTimeMillis()
		return true
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, contentX: Int, contentW: Int): Boolean {
		if (!rabbitsFace || flipStartMs != 0L || maxScroll <= 0 || scrollH <= 0) {
			return false
		}
		if (mouseY < scrollTop || mouseY >= scrollTop + scrollH) {
			return false
		}
		if (mouseX < contentX || mouseX >= contentX + contentW) {
			return false
		}
		val step = STAT_ROW * 3
		val next = maxOf(0, minOf(maxScroll, scroll + (if (scrollY > 0) -step else step)))
		if (next != scroll) {
			scroll = next
			return true
		}
		return false
	}

	fun render(
		snapshot: EventsSnapshot,
		ui: EventsUi,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mx: Int,
		my: Int
	) {
		val choc = snapshot.chocolate
		rightHitW = 0
		factionHits.clear()
		val rightW = maxOf(200, w * 52 / 100)
		val leftW = w - rightW - GAP
		val lx = x
		val rx = x + leftW + GAP
		PvDraw.innerPanel(g, lx, y, leftW, h)

		val bottom = y + h - PAD
		val cx = lx + PAD
		var cy = y + PAD
		val cw = leftW - PAD * 2

		cy += PvDraw.sectionHeader(g, font, "Chocolate", cx, cy, cw)
		if (!choc.present()) {
			PvDraw.text(g, font, "No chocolate data", cx, cy, PvDraw.COLOR_MUTED)
			PvDraw.innerPanel(g, rx, y, rightW, h)
			maxScroll = 0
			return
		}

		fun commas(v: Int) = FormatUtil.commas(v.toLong())

		cy = ui.tipStat(
			g, font, "Current", FormatUtil.shortCoins(choc.chocolate.toDouble()), COLOR_CHOCOLATE, cx, cy, cw, mx, my,
			tipTitle(
				"Chocolate", COLOR_CHOCOLATE,
				PvTooltip.Line.row("Purse", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolate), COLOR_CHOCOLATE),
				PvTooltip.Line.row("All-time", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalChocolate), PvDraw.COLOR_GOLD)
			)
		)
		cy = ui.tipStat(
			g, font, "All-time", FormatUtil.shortCoins(choc.totalChocolate.toDouble()), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
			tipTitle("All-time chocolate", PvDraw.COLOR_GOLD, PvTooltip.Line.meta(FormatUtil.commas(choc.totalChocolate) + " chocolate"))
		)
		val prestige = choc.prestige()
		val prestigeValue = if (prestige.maxed()) {
			FormatUtil.shortCoins(prestige.earned.toDouble())
		} else {
			FormatUtil.shortCoins(prestige.earned.toDouble()) + " / " + FormatUtil.shortCoins(prestige.threshold.toDouble())
		}
		cy = ui.tipStat(
			g, font, "This prestige", prestigeValue, if (prestige.ready()) COLOR_COMPLETE else PvDraw.COLOR_TEXT,
			cx, cy, cw, mx, my, prestigeTip(prestige)
		)

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Factory", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 3
			cy = ui.tipStat(
				g, font, "Level", commas(choc.chocolateLevel), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle(
					"Factory level", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, commas(choc.chocolateLevel), PvDraw.COLOR_ACCENT)
				)
			)
			cy = ui.tipStat(g, font, "Click upgrades", commas(choc.clickUpgrades), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			cy = ui.tipStat(g, font, "Multiplier", commas(choc.multiplierUpgrades), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Rabbit rarity", commas(choc.rabbitRarityUpgrades), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Barn capacity", commas(choc.barnCapacityLevel), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			}
		}

		if (cy + SEP_GAP + font.lineHeight + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Time Tower", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 3
			cy = ui.tipStat(g, font, "Level", commas(choc.timeTowerLevel), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			if (cy + STAT_ROW <= bottom) {
				val chargeColor = if (choc.timeTowerCharges >= 3) COLOR_COMPLETE else PvDraw.COLOR_ACCENT
				cy = ui.tipStat(
					g, font, "Charges", "${choc.timeTowerCharges} / 3", chargeColor, cx, cy, cw, mx, my,
					tipTitle(
						"Time Tower charges", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.row("Charges", PvDraw.COLOR_MUTED, "${choc.timeTowerCharges} / 3", chargeColor)
					)
				)
			}
			if (choc.timeTowerActivationMs > 0L && cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(
					g, font, "Last active", formatAgo(choc.timeTowerActivationMs), PvDraw.COLOR_MUTED,
					cx, cy, cw, mx, my,
					tipTitle("Time Tower", PvDraw.COLOR_ACCENT, PvTooltip.Line.meta("Activated " + formatAgo(choc.timeTowerActivationMs)))
				)
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Shop", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 3
			cy = ui.tipStat(g, font, "Cocoa fortune", commas(choc.cocoaFortuneUpgrades), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(
					g, font, "Spent", FormatUtil.shortCoins(choc.chocolateSpent.toDouble()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my,
					tipTitle(
						"Chocolate shop", COLOR_CHOCOLATE,
						PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolateSpent), COLOR_CHOCOLATE)
					)
				)
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Rabbits & eggs", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 3
			cy = ui.tipStat(
				g, font, "Unique", commas(choc.uniqueRabbits), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle(
					"Rabbits", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Unique", PvDraw.COLOR_MUTED, commas(choc.uniqueRabbits), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, commas(choc.totalRabbitDuplicates), PvDraw.COLOR_TEXT)
				)
			)
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Copies", commas(choc.totalRabbitDuplicates), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null)
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(
					g, font, "Breakfast eggs", FormatUtil.shortCoins(choc.breakfastEggs.toDouble()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my, null
				)
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Lunch eggs", FormatUtil.shortCoins(choc.lunchEggs.toDouble()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null)
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Dinner eggs", FormatUtil.shortCoins(choc.dinnerEggs.toDouble()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null)
			}
			if (cy + STAT_ROW <= bottom) {
				val hitmen = choc.hitmen()
				cy = ui.tipStat(
					g, font, "Hitmen slots", "${hitmen.slots} / ${hitmen.maxSlots}", PvDraw.COLOR_TEXT,
					cx, cy, cw, mx, my, hitmenTip(choc)
				)
			}
			if (cy + STAT_ROW <= bottom) {
				val missed = choc.missedEggs > 0
				ui.tipStat(
					g, font, "Missed eggs", FormatUtil.commas(choc.missedEggs),
					if (missed) 0xFFFF8888.toInt() else PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					if (missed) tipTitle("Missed eggs", 0xFFFF8888.toInt(), PvTooltip.Line.meta("Uncollected eggs from rabbit hitmen")) else null
				)
			}
		}

		drawRightFlip(choc, ui, g, font, rx, y, rightW, h, mx, my)
	}

	private fun drawRightFlip(
		choc: EventsSnapshot.Chocolate, ui: EventsUi, g: GuiGraphicsExtractor, font: Font,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int
	) {
		rightHitX = x
		rightHitY = y
		rightHitW = w
		rightHitH = h

		val hovered = mx >= x && mx < x + w && my >= y && my < y + h
		var flipProgress = 0f
		var animating = flipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1f, (System.currentTimeMillis() - flipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1f) {
				rabbitsFace = flipTarget
				flipStartMs = 0L
				scroll = 0
				animating = false
				flipProgress = 0f
			}
		}
		val eased = if (animating) easeInOutCubic(flipProgress) else 0f
		val angle = eased * Math.PI.toFloat()
		val showRabbits = if (animating) {
			if (cos(angle.toDouble()) < 0.0) flipTarget else rabbitsFace
		} else {
			rabbitsFace
		}
		var scaleX = 1f
		var scaleY = 1f
		if (animating) {
			scaleX = maxOf(0.04f, abs(cos(angle.toDouble()).toFloat()))
			scaleY = 1f - (1f - scaleX) * 0.06f
		}

		val cxFlip = x + w / 2f
		val cyFlip = y + h / 2f
		g.pose().pushMatrix()
		g.pose().translate(cxFlip, cyFlip)
		g.pose().scale(scaleX, scaleY)
		g.pose().translate(-cxFlip, -cyFlip)

		PvDraw.innerPanel(g, x, y, w, h)
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}
		if (showRabbits) {
			drawRabbitsFace(choc, ui, g, font, x, y, w, h, mx, my)
		} else {
			drawEmployeesFace(choc, ui, g, font, x, y, w, h)
		}

		g.pose().popMatrix()
	}

	private fun drawEmployeesFace(
		choc: EventsSnapshot.Chocolate, ui: EventsUi, g: GuiGraphicsExtractor, font: Font, rx: Int, y: Int, rightW: Int, h: Int
	) {
		maxScroll = 0
		scrollH = 0
		val rcx = rx + PAD
		var ry = y + PAD
		val rcw = rightW - PAD * 2
		val bottom = y + h - PAD
		ry += PvDraw.sectionHeader(g, font, "Employees", rcx, ry, rcw)
		val employees = choc.employees
		if (employees.isEmpty()) {
			PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED)
		}
		for (emp in employees) {
			if (ry + RABBIT_ROW > bottom) {
				break
			}
			drawEmployeeRow(ui, g, font, emp, rcx, ry, rcw)
			ry += RABBIT_ROW
		}
	}

	private fun drawRabbitsFace(
		choc: EventsSnapshot.Chocolate, ui: EventsUi, g: GuiGraphicsExtractor, font: Font,
		rx: Int, y: Int, rightW: Int, h: Int, mx: Int, my: Int
	) {
		val rcx = rx + PAD
		var ry = y + PAD
		val rcw = rightW - PAD * 2
		val bottom = y + h - PAD
		ry += PvDraw.sectionHeader(g, font, "Rabbits", rcx, ry, rcw)
		if (ry + STAT_ROW > bottom) {
			maxScroll = 0
			return
		}
		val rabbits = choc.topRabbits
		val factions = ChocolateFactoryData.factions()
		var rows = 3 + (if (factions.isEmpty()) 0 else factions.size + 1) + 1 + maxOf(1, rabbits.size)
		for (faction in factions) {
			if (expandedFactions.contains(faction.id)) {
				rows += faction.size()
			}
		}
		val viewH = maxOf(0, bottom - ry)
		val contentH = rows * STAT_ROW
		scrollTop = ry
		scrollH = viewH
		maxScroll = maxOf(0, contentH - viewH)
		scroll = minOf(scroll, maxScroll)
		g.enableScissor(rcx, ry, rcx + rcw, ry + viewH)
		var gy = ry - scroll
		gy = drawHoppityRows(ui, g, font, choc, rcx, gy, rcw, ry, viewH)
		if (factions.isNotEmpty()) {
			PvDraw.text(g, font, "Factions", rcx, gy, PvDraw.COLOR_MUTED)
			gy += STAT_ROW
			for (faction in factions) {
				val rowY = gy
				gy = drawFactionRow(ui, g, font, choc, faction, rcx, gy, rcw, ry, viewH, mx, my)
				if (rowY + STAT_ROW > ry && rowY < ry + viewH) {
					factionHits.add(
						FactionHit(faction.id, rcx, maxOf(rowY, ry), rcw, minOf(rowY + STAT_ROW, ry + viewH) - maxOf(rowY, ry))
					)
				}
				if (expandedFactions.contains(faction.id)) {
					gy = drawFactionRabbits(g, font, choc, faction, rcx, gy, rcw)
				}
			}
		}
		PvDraw.text(g, font, "Top rabbits", rcx, gy, PvDraw.COLOR_MUTED)
		gy += STAT_ROW
		if (rabbits.isEmpty()) {
			PvDraw.text(g, font, "None", rcx, gy, PvDraw.COLOR_MUTED)
		}
		for (rabbit in rabbits) {
			val rarity = rabbit.rarity.ifBlank { HoppityRabbitsData.rarityOf(rabbit.id) }
			val rarityColor = SkyBlockItemFactory.tierArgb(rarity)
			EventsUi.statLine(
				g, font, trim(font, rabbit.name, maxOf(24, rcw - font.width("×" + rabbit.count) - 8)),
				"×" + rabbit.count, rcx, gy, rcw, rarityColor
			)
			val faction = ChocolateFactoryData.factionOf(rabbit.id)
			ui.addClippedHover(
				rcx, gy, rcw, STAT_ROW, rcx, ry, rcw, viewH,
				tipTitle(
					rabbit.name, rarityColor,
					PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(rabbit.count.toLong()), PvDraw.COLOR_GOLD),
					faction?.let { PvTooltip.Line.row("Faction", PvDraw.COLOR_MUTED, it.name, PvDraw.COLOR_ACCENT) }
				)
			)
			gy += STAT_ROW
		}
		g.disableScissor()
	}

	private fun drawFactionRow(
		ui: EventsUi, g: GuiGraphicsExtractor, font: Font, choc: EventsSnapshot.Chocolate, faction: ChocolateFactoryData.Faction,
		x: Int, gy: Int, w: Int, clipY: Int, clipH: Int, mx: Int, my: Int
	): Int {
		val selected = faction.id == choc.selectedFaction
		val expanded = expandedFactions.contains(faction.id)
		val hover = mx >= x && mx < x + w && my >= gy && my < gy + STAT_ROW && my >= clipY && my < clipY + clipH
		val found = faction.found(choc.ownedRabbits())
		val size = faction.size()
		val tip = tipTitle(
			faction.name + " faction", PvDraw.COLOR_ACCENT,
			PvTooltip.Line.row("Found", PvDraw.COLOR_MUTED, "$found / $size", if (found >= size) COLOR_COMPLETE else PvDraw.COLOR_GOLD),
			if (selected) PvTooltip.Line.meta("Selected faction") else null,
			PvTooltip.Line.action(if (expanded) "Click to collapse" else "Click to show rabbits")
		)
		if (hover) {
			PvDraw.fill(g, x - 2, gy - 1, w + 4, STAT_ROW, 0x22FFFFFF)
		}
		val labelColor = if (selected) PvDraw.COLOR_ACCENT else if (hover) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
		return scrollStat(
			ui, g, font, (if (expanded) "- " else "+ ") + faction.name, labelColor,
			"$found / $size", if (found >= size) COLOR_COMPLETE else PvDraw.COLOR_TEXT, x, gy, w, clipY, clipH, tip
		)
	}

	private fun drawEmployeeRow(
		ui: EventsUi, g: GuiGraphicsExtractor, font: Font, emp: EventsSnapshot.Employee, x: Int, y: Int, w: Int
	) {
		val rarity = ChocolateEmployees.rarityOf(emp.id)
		val rarityColor = SkyBlockItemFactory.tierArgb(rarity)
		PvDraw.roundedFill(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, 3, raritySlotBackground(rarityColor))
		PvDraw.roundedRing(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, 3, SLOT_BORDER, PvDraw.C_ALL)

		val rowAlign = PvDraw.IconTextAlign.of(y + 1, RABBIT_SLOT, 16, font.lineHeight)
		g.item(employeeIcon(emp.id), x, rowAlign.iconY)

		val name = ChocolateEmployees.displayName(emp.id, emp.name)
		val level = "Lvl " + emp.level
		val levelColor = ChocolateFactoryData.employeeLevelColor(emp.level)
		val textX = x + RABBIT_SLOT + 4
		val textW = maxOf(8, w - RABBIT_SLOT - 4)
		val leftMax = maxOf(8, textW - font.width(level) - 6)
		PvDraw.text(g, font, trim(font, name, leftMax), textX, rowAlign.textY, rarityColor)
		PvDraw.textRight(g, font, level, x + w, rowAlign.textY, levelColor)

		ui.addClippedHover(
			x, y, w, RABBIT_ROW, ui.contentX, ui.contentY, ui.contentW, ui.contentH,
			tipTitle(
				name, rarityColor,
				PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
				PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(emp.level.toLong()), levelColor)
			)
		)
	}

	companion object {
		// Textured heads are rebuilt from scratch per call; employees are a small fixed set.
		private val EMPLOYEE_HEADS = ConcurrentHashMap<String, ItemStack>()

		private fun drawHoppityRows(
			ui: EventsUi, g: GuiGraphicsExtractor, font: Font, choc: EventsSnapshot.Chocolate,
			x: Int, startY: Int, w: Int, clipY: Int, clipH: Int
		): Int {
			var gy = startY
			val selected = ChocolateFactoryData.faction(choc.selectedFaction)
			val factionValue = if (selected == null) {
				"None"
			} else {
				selected.name + (if (choc.factionLevel > 0) ", Lvl " + choc.factionLevel else "")
			}
			val factionTip = tipTitle(
				"Rabbit faction", PvDraw.COLOR_ACCENT,
				PvTooltip.Line.row("Selected", PvDraw.COLOR_MUTED, selected?.name ?: "None", PvDraw.COLOR_ACCENT),
				PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.factionLevel.toLong()), PvDraw.COLOR_TEXT),
				selected?.let {
					PvTooltip.Line.row("Rabbits found", PvDraw.COLOR_MUTED, "${it.found(choc.ownedRabbits())} / ${it.size()}", PvDraw.COLOR_GOLD)
				}
			)
			gy = scrollStat(
				ui, g, font, "Faction", PvDraw.COLOR_MUTED, factionValue,
				if (selected == null) PvDraw.COLOR_MUTED else PvDraw.COLOR_ACCENT, x, gy, w, clipY, clipH, factionTip
			)

			val ready = choc.missedEggs
			val hitmen = choc.hitmen()
			gy = scrollStat(
				ui, g, font, "Hitmen", PvDraw.COLOR_MUTED,
				FormatUtil.commas(ready) + " ready, " + hitmen.slots + "/" + hitmen.maxSlots + " slots",
				if (ready > 0) COLOR_COMPLETE else PvDraw.COLOR_TEXT, x, gy, w, clipY, clipH, hitmenTip(choc)
			)

			if (!choc.chocobitsPresent) {
				return scrollStat(
					ui, g, font, "Chocobits", PvDraw.COLOR_MUTED, "No data", PvDraw.COLOR_MUTED,
					x, gy, w, clipY, clipH, tipTitle("Chocobits", COLOR_CHOCOLATE, PvTooltip.Line.meta("No Chocobit data in the API"))
				)
			}
			val year = ChocolateFactoryData.currentSkyBlockYear(System.currentTimeMillis())
			val bitsValue = EventsSnapshot.Chocobit.active(choc.chocobits, year).toString() + " active, " +
				FormatUtil.commas(choc.chocobitsFound.toLong()) + " found"
			return scrollStat(ui, g, font, "Chocobits", PvDraw.COLOR_MUTED, bitsValue, COLOR_CHOCOLATE, x, gy, w, clipY, clipH, chocobitsTip(choc))
		}

		private fun drawFactionRabbits(
			g: GuiGraphicsExtractor, font: Font, choc: EventsSnapshot.Chocolate, faction: ChocolateFactoryData.Faction,
			x: Int, startY: Int, w: Int
		): Int {
			var gy = startY
			val indent = 10
			for ((rarityKey, ids) in faction.rabbitsByRarity) {
				val rarityColor = SkyBlockItemFactory.tierArgb(rarityKey)
				for (id in ids) {
					val count = choc.rabbitCounts[id]
					val owned = count != null && count > 0
					val value = if (owned) "×$count" else "Missing"
					val leftMax = maxOf(8, w - indent - font.width(value) - 6)
					PvDraw.text(g, font, trim(font, prettyModifier(id), leftMax), x + indent, gy, if (owned) rarityColor else PvDraw.COLOR_MUTED)
					PvDraw.textRight(g, font, value, x + w, gy, if (owned) PvDraw.COLOR_TEXT else 0xFF666670.toInt())
					gy += STAT_ROW
				}
			}
			return gy
		}

		private fun scrollStat(
			ui: EventsUi, g: GuiGraphicsExtractor, font: Font, label: String, labelColor: Int, value: String, valueColor: Int,
			x: Int, gy: Int, w: Int, clipY: Int, clipH: Int, tip: List<PvTooltip.Line>
		): Int {
			val leftMax = maxOf(8, w - font.width(value) - 6)
			PvDraw.text(g, font, trim(font, label, leftMax), x, gy, labelColor)
			PvDraw.textRight(g, font, value, x + w, gy, valueColor)
			ui.addClippedHover(x, gy, w, STAT_ROW, x, clipY, w, clipH, tip)
			return gy + STAT_ROW
		}

		private fun prestigeTip(prestige: ChocolateFactoryData.Prestige): List<PvTooltip.Line> {
			val tip = tipTitle(
				"This prestige", PvDraw.COLOR_TEXT,
				PvTooltip.Line.row("Prestige", PvDraw.COLOR_MUTED, FormatUtil.commas(prestige.level.toLong()), PvDraw.COLOR_ACCENT),
				PvTooltip.Line.row("Earned", PvDraw.COLOR_MUTED, FormatUtil.commas(prestige.earned), PvDraw.COLOR_TEXT)
			)
			if (prestige.maxed()) {
				tip.add(PvTooltip.Line.row("Next prestige", PvDraw.COLOR_MUTED, "Max", COLOR_COMPLETE))
				return tip
			}
			tip.add(
				PvTooltip.Line.row(
					"Prestige " + (prestige.level + 1), PvDraw.COLOR_MUTED,
					FormatUtil.commas(prestige.threshold), COLOR_CHOCOLATE
				)
			)
			tip.add(
				PvTooltip.Line.row(
					"Needed", PvDraw.COLOR_MUTED,
					if (prestige.ready()) "Ready" else FormatUtil.commas(prestige.needed()),
					if (prestige.ready()) COLOR_COMPLETE else PvDraw.COLOR_GOLD
				)
			)
			return tip
		}

		private fun hitmenTip(choc: EventsSnapshot.Chocolate): List<PvTooltip.Line> {
			val hitmen = choc.hitmen()
			val tip = tipTitle(
				"Rabbit Hitmen", PvDraw.COLOR_ACCENT,
				PvTooltip.Line.row(
					"Eggs ready", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.missedEggs),
					if (choc.missedEggs > 0) COLOR_COMPLETE else PvDraw.COLOR_TEXT
				),
				PvTooltip.Line.row(
					"Slots", PvDraw.COLOR_MUTED, "${hitmen.slots} / ${hitmen.maxSlots}",
					if (hitmen.maxed()) COLOR_COMPLETE else PvDraw.COLOR_TEXT
				),
				PvTooltip.Line.row(
					"Coins paid", PvDraw.COLOR_MUTED,
					FormatUtil.shortCoins(hitmen.paid.toDouble()) + " / " + FormatUtil.shortCoins(hitmen.total.toDouble()) +
						" (" + FormatUtil.percent(hitmen.fraction()) + ")",
					if (hitmen.maxed()) COLOR_COMPLETE else PvDraw.COLOR_GOLD
				)
			)
			if (!hitmen.maxed() && hitmen.nextSlotCost > 0L) {
				tip.add(PvTooltip.Line.row("Next slot", PvDraw.COLOR_MUTED, FormatUtil.commas(hitmen.nextSlotCost) + " coins", PvDraw.COLOR_GOLD))
			}
			tip.add(PvTooltip.Line.meta("Uncollected eggs waiting for hitmen"))
			return tip
		}

		private fun chocobitsTip(choc: EventsSnapshot.Chocolate): List<PvTooltip.Line> {
			val bits = choc.chocobits
			val year = ChocolateFactoryData.currentSkyBlockYear(System.currentTimeMillis())
			val active = EventsSnapshot.Chocobit.active(bits, year)
			val tip = tipTitle(
				"Chocobits", COLOR_CHOCOLATE,
				PvTooltip.Line.row("Active", PvDraw.COLOR_MUTED, FormatUtil.commas(active.toLong()), COLOR_CHOCOLATE),
				if (bits.size > active) {
					PvTooltip.Line.row("Expired", PvDraw.COLOR_MUTED, FormatUtil.commas((bits.size - active).toLong()), 0xFFFF8888.toInt())
				} else {
					null
				},
				PvTooltip.Line.row("Total found", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocobitsFound.toLong()), PvDraw.COLOR_TEXT)
			)
			if (bits.isEmpty()) {
				return tip
			}
			tip.add(PvTooltip.Line.divider())
			for ((shown, bit) in bits.withIndex()) {
				if (shown == 12) {
					tip.add(PvTooltip.Line.meta("+" + (bits.size - shown) + " more"))
					break
				}
				val expired = bit.expired(year)
				val obtained = if (bit.ownedYear > 0) "Year " + bit.ownedYear else "Year unknown"
				val expiry = when {
					bit.expiryYear <= 0 -> ", expiry unknown"
					expired -> ", expired Year " + bit.expiryYear
					else -> ", expires Year " + bit.expiryYear
				}
				tip.add(
					PvTooltip.Line.row(
						"#" + bit.id, PvDraw.COLOR_MUTED, obtained + expiry,
						if (expired) 0xFFFF8888.toInt() else PvDraw.COLOR_TEXT
					)
				)
			}
			tip.add(PvTooltip.Line.meta("Current year: $year"))
			return tip
		}

		private fun employeeIcon(employeeId: String): ItemStack {
			val value = ChocolateEmployees.skullValue(employeeId)
			if (!value.isNullOrBlank()) {
				val head = EMPLOYEE_HEADS.computeIfAbsent(value) { SkyBlockItemFactory.texturedHead(it) }
				if (!head.isEmpty) {
					return head
				}
			}
			val fallback = SkyBlockItemFactory.iconStack("CHOCO_RABBIT_PERSONALITY")
			if (!fallback.isEmpty) {
				return fallback
			}
			return ItemStack(Items.RABBIT_FOOT)
		}

		/** Soft rarity tint like pets/museum slots. */
		private fun raritySlotBackground(rarityArgb: Int): Int {
			val r = (rarityArgb shr 16) and 0xFF
			val g = (rarityArgb shr 8) and 0xFF
			val b = rarityArgb and 0xFF
			val mixR = (r * 70 + 16 * 186) / 256
			val mixG = (g * 70 + 16 * 186) / 256
			val mixB = (b * 70 + 24 * 186) / 256
			return 0xFF000000.toInt() or (mixR shl 16) or (mixG shl 8) or mixB
		}
	}
}

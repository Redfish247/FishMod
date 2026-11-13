/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page

import fishmod.pv.data.KuudraGearChecks
import fishmod.pv.data.KuudraGearChecks.Check
import fishmod.pv.data.KuudraGearChecks.CheckState
import fishmod.pv.data.KuudraGearChecks.Entry
import fishmod.pv.data.KuudraGearChecks.Group
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.crimson.CrimsonUi.DISABLED
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER
import fishmod.pv.gui.crimson.CrimsonUi.PAD
import fishmod.pv.gui.crimson.CrimsonUi.trim
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

internal class KuudraChecklistColumn {
	private enum class Kind { GROUP, ENTRY, CHIPS, NOTE, GAP }

	enum class Hover { NONE, ICON, ROW }

	private class Seg(val text: String, val color: Int)

	private class Line(
		val kind: Kind, val indent: Int, val h: Int, val segs: List<Seg>, val right: String?, val rightColor: Int,
		val entry: Entry?, val group: Group?,
	)

	private val icons = HashMap<String, ItemStack>()
	private var scroll = 0
	private var maxScroll = 0
	private var areaX = 0
	private var areaY = 0
	private var areaW = 0
	private var areaH = 0
	private var cachedGroups: List<Group>? = null
	private var cachedWidth = -1
	private var lines: List<Line> = listOf()

	fun resetScroll() {
		scroll = 0
	}

	fun mouseScrolled(mx: Double, my: Double, scrollY: Double): Boolean {
		if (maxScroll <= 0 || mx < areaX || mx >= areaX + areaW || my < areaY || my >= areaY + areaH) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll + if (scrollY > 0) -14 else 14))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, title: String, groups: List<Group>?,
		mx: Int, my: Int, zones: MutableList<HoverZone>,
	) {
		PvDraw.innerPanel(g, x, y, w, h)
		val lx = x + PAD
		val lw = w - PAD * 2 - 4
		PvDraw.text(g, font, title, lx, y + PAD, PvDraw.COLOR_MUTED)
		val clipTop = y + PAD + TITLE_H - 4
		val clipBottom = y + h - PAD
		PvDraw.fill(g, lx, clipTop - 3, w - PAD * 2, 1, 0x33FFFFFF)
		areaX = x
		areaY = clipTop
		areaW = w
		areaH = maxOf(0, clipBottom - clipTop)
		if (areaH <= 0) {
			return
		}
		if (groups !== cachedGroups || lw != cachedWidth) {
			lines = buildLines(font, groups, lw)
			cachedGroups = groups
			cachedWidth = lw
		}
		val contentH = 2 + lines.sumOf { it.h }
		maxScroll = maxOf(0, contentH - areaH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(x + 1, clipTop, x + w - 1, clipBottom)
		var ly = lineTop(clipTop, scroll, 0)
		for (line in lines) {
			if (line.kind != Kind.GAP && ly + line.h > clipTop && ly < clipBottom) {
				drawLine(g, font, line, lx, ly, lw, clipTop, clipBottom, mx, my, zones)
			}
			ly += line.h
		}
		g.disableScissor()

		if (maxScroll > 0) {
			val thumbH = maxOf(10, areaH * areaH / maxOf(1, contentH))
			val thumbY = clipTop + (areaH - thumbH) * scroll / maxScroll
			PvDraw.fill(g, x + w - 4, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER)
		}
	}

	private fun drawLine(
		g: GuiGraphicsExtractor, font: Font, line: Line, lx: Int, ly: Int, lw: Int,
		clipTop: Int, clipBottom: Int, mx: Int, my: Int, zones: MutableList<HoverZone>,
	) {
		val textY = ly + (line.h - font.lineHeight) / 2 + 1
		if (line.kind == Kind.GROUP) {
			PvDraw.fill(g, lx - 2, ly, lw + 4, line.h - 1, 0x22FFFFFF)
		}
		val entry = if (line.kind == Kind.ENTRY) line.entry else null
		val preview = entry != null && hasPreview(entry)
		val hover = hoverAt(mx, my, lx, ly, lw, line.h, clipTop, clipBottom, preview)
		val sx = slotX(lx)
		val sy = slotY(ly, line.h)
		if (entry != null && entry.iconId.isNotEmpty()) {
			PvDraw.fill(g, sx, sy, SLOT_SIZE, SLOT_SIZE, ITEM_SLOT_BG)
			g.outline(
				sx, sy, SLOT_SIZE, SLOT_SIZE,
				if (hover == Hover.ICON) PvDraw.COLOR_ACCENT else if (preview) ITEM_SLOT_BORDER else DISABLED,
			)
			val icon = icon(entry.iconId)
			if (!icon.isEmpty) {
				val pad = (SLOT_SIZE - ICON) / 2
				g.item(icon, sx + pad, sy + pad)
				if (!preview) {
					PvDraw.fill(g, sx + pad, sy + pad, ICON, ICON, 0x99000000.toInt())
				}
			}
		}
		val rightW = if (line.right == null) 0 else font.width(line.right) + 6
		var cursor = lx + line.indent
		val limit = lx + lw - rightW
		for (seg in line.segs) {
			if (cursor >= limit) {
				break
			}
			val text = trim(font, seg.text, limit - cursor)
			PvDraw.text(g, font, text, cursor, textY, seg.color)
			cursor += font.width(text)
		}
		if (line.right != null) {
			PvDraw.textRight(g, font, line.right, lx + lw, textY, line.rightColor)
		}
		if (hover == Hover.NONE) {
			return
		}
		val y0 = maxOf(ly, clipTop)
		val y1 = minOf(ly + line.h, clipBottom)
		if (hover == Hover.ICON && entry != null) {
			val iy0 = maxOf(sy, clipTop)
			val iy1 = minOf(sy + SLOT_SIZE, clipBottom)
			zones.add(HoverZone.ofComponents(sx, iy0, SLOT_SIZE, iy1 - iy0, itemTip(entry)))
			return
		}
		val tip = when {
			line.entry != null -> rowTip(line.entry)
			line.group != null -> groupTip(line.group)
			else -> null
		}
		if (tip != null) {
			zones.add(HoverZone.ofComponents(lx, y0, lw, y1 - y0, tip))
		}
	}

	private fun icon(id: String): ItemStack {
		val cached = icons[id]
		if (cached != null && !cached.isEmpty) {
			return cached
		}
		val stack: ItemStack = SkyBlockItemFactory.iconStack(id)
		icons[id] = stack
		return stack
	}

	companion object {
		const val COLOR_OK = 0xFF55FF55.toInt()
		const val COLOR_MISSING = 0xFFFF5555.toInt()
		const val COLOR_WARN = 0xFFFFAA00.toInt()
		const val COLOR_UPGRADE = 0xFFFFFF55.toInt()
		const val COLOR_HIGHLIGHT = 0xFF55FFFF.toInt()
		private const val TITLE_H = 16
		private const val GROUP_H = 14
		private const val ENTRY_H = 20
		private const val CHIP_H = 11
		private const val GROUP_GAP = 7
		private const val ENTRY_GAP = 3
		private const val ICON = 16
		const val SLOT_SIZE = 18
		private const val ENTRY_INDENT = 4
		private const val CHECK_INDENT = ENTRY_INDENT + SLOT_SIZE + 4
		const val MISSING_TEXT = KuudraGearChecks.MISSING_TEXT

		fun lineTop(clipTop: Int, scroll: Int, offset: Int): Int = clipTop + 2 - scroll + offset

		fun slotX(lx: Int): Int = lx + ENTRY_INDENT - 1

		fun slotY(ly: Int, lineH: Int): Int = ly + (lineH - SLOT_SIZE) / 2

		fun hasPreview(entry: Entry): Boolean = entry.stack != null || entry.pet != null

		fun hoverAt(mx: Int, my: Int, lx: Int, ly: Int, lw: Int, lineH: Int, clipTop: Int, clipBottom: Int, preview: Boolean): Hover {
			val y0 = maxOf(ly, clipTop)
			val y1 = minOf(ly + lineH, clipBottom)
			if (y1 <= y0 || my < y0 || my >= y1 || mx < lx || mx >= lx + lw) {
				return Hover.NONE
			}
			val sx = slotX(lx)
			val sy = slotY(ly, lineH)
			if (preview && mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
				return Hover.ICON
			}
			return Hover.ROW
		}

		private fun buildLines(font: Font, groups: List<Group>?, lw: Int): List<Line> {
			val out = ArrayList<Line>()
			if (groups.isNullOrEmpty()) {
				out.add(Line(Kind.NOTE, 0, CHIP_H + 2, listOf(Seg("No gear data", PvDraw.COLOR_MUTED)), null, 0, null, null))
				return out
			}
			for ((i, group) in groups.withIndex()) {
				if (i > 0) {
					out.add(Line(Kind.GAP, 0, GROUP_GAP, listOf(), null, 0, null, null))
				}
				val statusColor = statusColor(group.status, group.informational)
				out.add(Line(
					Kind.GROUP, 2, GROUP_H,
					listOf(Seg(mark(group.status) + " ", statusColor), Seg(group.title, PvDraw.COLOR_WHITE)),
					summary(group), statusColor, null, group,
				))
				val info = group.informational
				addChecks(out, font, group.checks, ENTRY_INDENT + 4, lw, null, group, info)
				if (group.entries.isEmpty() && group.checks.isEmpty()) {
					out.add(Line(
						Kind.NOTE, ENTRY_INDENT + 4, CHIP_H + 1, listOf(Seg(emptyNote(group), PvDraw.COLOR_MUTED)),
						null, 0, null, group,
					))
				}
				for ((e, entry) in group.entries.withIndex()) {
					if (e > 0) {
						out.add(Line(Kind.GAP, 0, ENTRY_GAP, listOf(), null, 0, null, null))
					}
					val color = statusColor(entry.status, info)
					val segs = ArrayList<Seg>()
					segs.add(Seg(mark(entry.status) + " ", color))
					var right: String? = null
					val value = entry.value
					if (value != null) {
						segs.add(Seg(entry.name + ": ", PvDraw.COLOR_MUTED))
						segs.add(Seg(value, color))
					} else {
						segs.add(Seg(entry.name, color))
						right = entry.source
					}
					out.add(Line(Kind.ENTRY, CHECK_INDENT, ENTRY_H, segs, right, PvDraw.COLOR_MUTED, entry, group))
					addChecks(out, font, entry.checks, CHECK_INDENT, lw, entry, group, info)
				}
			}
			return out
		}

		/** Checks flow as whole chips, wrapping onto new rows instead of being cut. */
		private fun addChecks(
			out: MutableList<Line>, font: Font, checks: List<Check>, indent: Int, lw: Int, entry: Entry?, group: Group, info: Boolean,
		) {
			var row = ArrayList<Seg>()
			var used = 0
			val gap = font.width("   ")
			for (check in checks) {
				val chip = chip(check, info)
				val chipW = chip.sumOf { font.width(it.text) }
				if (row.isNotEmpty() && indent + used + gap + chipW > lw) {
					out.add(Line(Kind.CHIPS, indent, CHIP_H, row, null, 0, entry, group))
					row = ArrayList()
					used = 0
				}
				if (row.isNotEmpty()) {
					row.add(Seg("   ", 0))
					used += gap
				}
				row.addAll(chip)
				used += chipW
			}
			if (row.isNotEmpty()) {
				out.add(Line(Kind.CHIPS, indent, CHIP_H, row, null, 0, entry, group))
			}
		}

		private fun chip(check: Check, info: Boolean): List<Seg> {
			val segs = ArrayList<Seg>(3)
			if (check.label.isNotEmpty()) {
				segs.add(Seg(check.label + " ", PvDraw.COLOR_MUTED))
			}
			val color = checkColor(check, info)
			val suffix = check.state == CheckState.WARN && check.label.isNotEmpty()
			segs.add(Seg(if (suffix) check.value + " (warning)" else check.value, color))
			check.upgradeTo?.let { segs.add(Seg(" → $it", color)) }
			return segs
		}

		fun checkColor(check: Check, info: Boolean): Int = when (check.state) {
			CheckState.OK -> if (check.highlight) COLOR_HIGHLIGHT else COLOR_OK
			CheckState.UPGRADE -> if (info) PvDraw.COLOR_MUTED else COLOR_UPGRADE
			CheckState.MISSING -> if (info) PvDraw.COLOR_MUTED else COLOR_MISSING
			CheckState.WARN -> COLOR_WARN
			CheckState.INFO -> PvDraw.COLOR_TEXT
			CheckState.UNKNOWN -> PvDraw.COLOR_MUTED
		}

		private fun emptyNote(group: Group): String = when (group.status) {
			KuudraGearChecks.Status.UNKNOWN -> "Unknown"
			KuudraGearChecks.Status.MISSING -> MISSING_TEXT
			else -> ""
		}

		private fun mark(status: KuudraGearChecks.Status): String = when (status) {
			KuudraGearChecks.Status.COMPLETE -> "✔"
			KuudraGearChecks.Status.INCOMPLETE -> "!"
			KuudraGearChecks.Status.MISSING -> "✘"
			KuudraGearChecks.Status.UNKNOWN -> "?"
		}

		fun statusColor(status: KuudraGearChecks.Status, informational: Boolean): Int = when (status) {
			KuudraGearChecks.Status.COMPLETE -> COLOR_OK
			KuudraGearChecks.Status.INCOMPLETE -> if (informational) PvDraw.COLOR_MUTED else COLOR_UPGRADE
			KuudraGearChecks.Status.MISSING -> if (informational) PvDraw.COLOR_MUTED else COLOR_MISSING
			KuudraGearChecks.Status.UNKNOWN -> PvDraw.COLOR_MUTED
		}

		private fun summary(group: Group): String {
			val word = when (group.status) {
				KuudraGearChecks.Status.COMPLETE -> "Complete"
				KuudraGearChecks.Status.INCOMPLETE -> "Incomplete"
				KuudraGearChecks.Status.MISSING -> "Missing"
				KuudraGearChecks.Status.UNKNOWN -> "Unknown"
			}
			val countable = group.entries.all { it.value == null }
			return if (countable && group.owned > 1 && group.status != KuudraGearChecks.Status.UNKNOWN) "${group.owned}x  $word" else word
		}

		private fun itemTip(entry: Entry): List<Component> {
			val lore = KuudraGearChecks.previewLore(entry)
			val title = KuudraGearChecks.previewTitle(entry)
			val tip = ArrayList<Component>(
				if (lore.isEmpty()) listOf(SkyBlockItemFactory.legacyLine(title)) else KuudraPage.loreComponents(title, lore),
			)
			entry.source?.let {
				tip.add(Component.empty())
				tip.add(SkyBlockItemFactory.legacyLine("§8Found in: $it"))
			}
			return tip
		}

		private fun rowTip(entry: Entry): List<Component>? {
			val lines = rowTipLines(entry)
			if (lines.isEmpty()) {
				return null
			}
			val tip = ArrayList<Component>()
			tip.add(SkyBlockItemFactory.legacyLine("§f" + entry.name + (if (entry.value == null) "" else ": " + entry.value)))
			for (line in lines) {
				tip.add(SkyBlockItemFactory.legacyLine(line))
			}
			return tip
		}

		fun rowTipLines(entry: Entry): List<String> {
			val out = ArrayList<String>()
			for (check in entry.checks) {
				val text = if (check.label.isEmpty()) check.value else check.label + " " + check.value
				when (check.state) {
					CheckState.MISSING -> out.add("§cMissing: $text")
					CheckState.UPGRADE -> out.add("§eUpgrade: " + text + (if (check.upgradeTo == null) "" else " → " + check.upgradeTo))
					CheckState.WARN -> out.add("§6Warning: $text")
					else -> {}
				}
			}
			return out
		}

		private fun groupTip(group: Group): List<Component>? {
			val line = when {
				group.status == KuudraGearChecks.Status.UNKNOWN -> "§7Unknown: not available in the API"
				group.informational -> "§7Optional, not a Kuudra requirement"
				else -> return null
			}
			return listOf(SkyBlockItemFactory.legacyLine("§f" + group.title), SkyBlockItemFactory.legacyLine(line))
		}
	}
}

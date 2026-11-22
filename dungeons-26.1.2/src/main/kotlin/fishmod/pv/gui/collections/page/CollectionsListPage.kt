/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page

import fishmod.pv.data.BossCollections
import fishmod.pv.data.CollectionSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.HypixelRanks
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.collections.CollectionCategoryIcons
import fishmod.pv.gui.collections.CollectionsUi
import fishmod.pv.gui.collections.CollectionsUi.COLL_ICON_STEPS
import fishmod.pv.gui.collections.CollectionsUi.MINION_LOCKED
import fishmod.pv.gui.collections.CollectionsUi.MINION_UNLOCKED
import fishmod.pv.gui.collections.CollectionsUi.PAD
import fishmod.pv.price.HypixelCollectionsCache
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class CollectionsListPage {
	private var selectedCat = 0
	private var selectedItem = 0
	private var gridScroll = 0
	private var gridMaxScroll = 0
	private var gridTop = 0
	private var gridH = 0
	private val itemHits = ArrayList<ItemHit>()
	private val detail = CollectionDetailView()
	private val board = IntArray(4)
	private var snapshot: CollectionSnapshot? = null
	private var hoverTip: List<PvTooltip.Line> = listOf()

	fun reset(@Suppress("UNUSED_PARAMETER") snapshot: CollectionSnapshot?) {
		selectedCat = 0
		selectedItem = 0
		gridScroll = 0
		hoverTip = listOf()
		detail.close()
	}

	fun render(
		snapshot: CollectionSnapshot,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int,
		@Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		itemHits.clear()
		hoverTip = listOf()
		this.snapshot = snapshot
		board[0] = x
		board[1] = y
		board[2] = w
		board[3] = h
		val popup = detail.isOpen()
		renderCollections(snapshot, g, font, x, y, w, h, if (popup) -1 else mouseX, if (popup) -1 else mouseY)
		if (popup) {
			hoverTip = listOf()
		}
	}

	fun renderOverlay(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		val snap = snapshot
		if (detail.isOpen() && snap != null) {
			detail.render(snap, g, font, screenW, screenH, board[0], board[1], board[2], board[3], mouseX, mouseY)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (hoverTip.isNotEmpty()) {
			PvTooltip.drawStyled(g, font, hoverTip, mouseX, mouseY, screenW, screenH)
		}
	}

	fun keyPressed(key: Int): Boolean = detail.keyPressed(key)

	fun closeDetail() {
		detail.close()
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (detail.isOpen()) {
			return detail.mouseClicked(mx, my)
		}
		for (hit in itemHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				selectedCat = hit.cat
				selectedItem = hit.item
				detail.open(hit.entry)
				return true
			}
		}
		return false
	}

	fun setCategory(index: Int) {
		if (index != selectedCat) {
			selectedCat = maxOf(0, index)
			selectedItem = 0
			detail.close()
		}
	}

	fun category(): Int = selectedCat

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean = detail.mouseScrolled(mouseX, mouseY, scrollY)

	private fun renderCollections(
		snapshot: CollectionSnapshot,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		PvDraw.innerPanel(g, x, y, w, h)

		val categories = snapshot.categories
		if (categories.isEmpty()) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("fishmod.pv.collections.loading").string,
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED,
			)
			return
		}
		clampSelection(categories)

		gridTop = y + PAD
		gridH = maxOf(20, h - PAD * 2)
		drawAllCategories(snapshot, g, font, categories, x + PAD, gridTop, w - PAD * 2, gridH, mouseX, mouseY)
	}

	private fun drawAllCategories(
		snapshot: CollectionSnapshot,
		g: GuiGraphicsExtractor,
		font: Font,
		allCategories: List<HypixelCollectionsCache.Category>,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		if (allCategories.isEmpty()) {
			return
		}
		val cat = maxOf(0, minOf(selectedCat, allCategories.size - 1))
		val categories = listOf(allCategories[cat])
		val n = 1

		val layout = layoutBoard(categories, w, h)
		val slot = layout.slot
		val icon = layout.icon
		val gap = layout.gap
		val cellH = layout.cellH()
		val header = slot
		val headerH = header + layout.rowGap
		val rowPitch = cellH + layout.rowGap
		val contentH = headerH + layout.maxRows * cellH + maxOf(0, layout.maxRows - 1) * layout.rowGap

		gridMaxScroll = 0
		gridScroll = 0

		val startX = x + maxOf(0, (w - layout.neededW) / 2)
		val startY = y + maxOf(0, (h - contentH) / 2)
		val labelScale = if (icon >= 14) 1.0f else if (icon >= 12) 0.8f else 0.7f

		g.enableScissor(x, y, x + w + 3, y + h)
		var cursorX = startX
		for (c in 0 until n) {
			val category = categories[c]
			val items = category.items
			val cols = layout.catCols[c]
			val blockW = cols * slot + maxOf(0, cols - 1) * gap
			val headerX = cursorX + maxOf(0, (blockW - header) / 2)

			PvDraw.roundedFill(g, headerX, startY, header, header, 3, 0x50386CC8)
			PvDraw.roundedRing(g, headerX, startY, header, header, 3, PvDraw.COLOR_ACCENT, PvDraw.C_ALL)
			val headerIcon = CollectionCategoryIcons.icon(category.id)
			if (!headerIcon.isEmpty) {
				CollectionsUi.drawVanillaIcon(g, headerIcon, headerX + (header - icon) / 2, startY + (header - icon) / 2, icon)
			}

			for ((i, item) in items.withIndex()) {
				val col = i % cols
				val row = i / cols
				val sx = cursorX + col * (slot + gap)
				val sy = startY + headerH + row * rowPitch
				val tier = snapshot.displayTier(item)
				val selected = i == selectedItem
				val hover = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + cellH
				PvDraw.roundedFill(g, sx, sy, slot, slot, 3, if (selected) 0x50386CC8 else if (hover) 0x24FFFFFF else PvDraw.CELL)
				PvDraw.roundedRing(
					g, sx, sy, slot, slot, 3,
					if (selected) PvDraw.COLOR_ACCENT else if (hover) 0xFF4A4A5A.toInt() else 0x30FFFFFF,
					PvDraw.C_ALL,
				)
				CollectionsUi.drawIcon(g, CollectionsUi.resolveIconId(item.id), sx + (slot - icon) / 2, sy + (slot - icon) / 2, icon)
				val maxed = tier >= item.maxTiers && item.maxTiers > 0
				val label = tier.toString()
				val labelColor = if (maxed) PvDraw.COLOR_GOLD else if (tier > 0) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
				val labelY = sy + slot + layout.labelGap
				if (labelScale >= 0.99f) {
					PvDraw.textCentered(g, font, label, sx + slot / 2, labelY, labelColor)
				} else {
					val tw = maxOf(1, (font.width(label) * labelScale).roundToInt())
					PvDraw.textScaled(g, font, label, sx + (slot - tw) / 2, labelY, labelColor, labelScale)
				}
				itemHits.add(ItemHit(sx, sy, slot, cellH, cat, i, item))
				if (hover) {
					hoverTip = collectionHover(snapshot, item)
				}
			}

			cursorX += blockW
			if (c < n - 1) {
				val lineX = cursorX + layout.catSep / 2
				PvDraw.fill(g, lineX, startY, 1, contentH, PvDraw.COLOR_DIVIDER)
				cursorX += layout.catSep
			}
		}
		g.disableScissor()
	}

	private fun collectionHover(snapshot: CollectionSnapshot, item: HypixelCollectionsCache.Item): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		val amount = snapshot.progressAmount(item)
		val yours = snapshot.viewedAmount(item.id)
		val tier = snapshot.displayTier(item)
		val maxed = tier >= item.maxTiers && item.maxTiers > 0
		val boss = BossCollections.isBossId(item.id)

		lines.add(PvTooltip.Line.bold(item.name, PvDraw.COLOR_GOLD))
		lines.add(PvTooltip.Line.blank())
		if (boss) {
			lines.add(PvTooltip.Line(listOf(
				PvTooltip.Span.of("Kills: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount.toDouble()), PvDraw.COLOR_TEXT),
			)))
		} else {
			lines.add(PvTooltip.Line(listOf(
				PvTooltip.Span.of("Coop total: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount.toDouble()), PvDraw.COLOR_GOLD),
			)))
			if (snapshot.members.size > 1) {
				lines.add(PvTooltip.Line(listOf(
					PvTooltip.Span.of("You: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.bold(FormatUtil.shortXp(yours.toDouble()), PvDraw.COLOR_ACCENT),
				)))
			}
		}
		lines.add(PvTooltip.Line.blank())
		lines.add(PvTooltip.Line(listOf(
			PvTooltip.Span.of("Tier: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(
				"$tier / ${item.maxTiers}",
				if (maxed) MINION_UNLOCKED else if (tier > 0) PvDraw.COLOR_TEXT else MINION_LOCKED,
			),
		)))
		val next = item.nextTier(amount)
		if (maxed || next == null) {
			lines.add(PvTooltip.Line.bold("Maxed", MINION_UNLOCKED))
			val legacy = snapshot.legacyTier(item)
			if (legacy > 0) {
				lines.add(PvTooltip.Line.meta(
					"Old tier " + CollectionDetailView.roman(legacy) + " from before the " + item.maxTiers + "-tier rework",
				))
			}
		} else {
			val pct = (item.progressToNext(amount) * 100f).roundToInt()
			lines.add(PvTooltip.Line(listOf(
				PvTooltip.Span.of("Next: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(
					FormatUtil.shortXp(amount.toDouble()) + " / " + FormatUtil.shortXp(next.amountRequired.toDouble()),
					PvDraw.COLOR_TEXT,
				),
				PvTooltip.Span.of(" ($pct%)", PvDraw.COLOR_ACCENT),
			)))
			val maxTier = item.tiers.lastOrNull()
			if (maxTier != null && maxTier.amountRequired > next.amountRequired) {
				lines.add(PvTooltip.Line(listOf(
					PvTooltip.Span.of("Max: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(
						FormatUtil.shortXp(amount.toDouble()) + " / " + FormatUtil.shortXp(maxTier.amountRequired.toDouble()),
						PvDraw.COLOR_TEXT,
					),
				)))
			}
		}
		if (!boss && snapshot.members.size > 1) {
			lines.add(PvTooltip.Line.blank())
			lines.add(PvTooltip.Line.bold(
				Component.translatable("fishmod.pv.collections.coop").string,
				PvDraw.COLOR_ACCENT,
			))
			for (member in snapshot.membersByAmount(item.id)) {
				val viewed = member.uuid == snapshot.viewedUuid
				val spans = ArrayList(memberNameSpans(snapshot, member))
				spans.add(PvTooltip.Span.of(": ", PvDraw.COLOR_MUTED))
				spans.add(PvTooltip.Span.bold(
					FormatUtil.shortXp(member.amount(item.id).toDouble()),
					if (viewed) PvDraw.COLOR_ACCENT else PvDraw.COLOR_GOLD,
				))
				lines.add(PvTooltip.Line(spans))
			}
		}
		if (item.tiers.isNotEmpty()) {
			lines.add(PvTooltip.Line.blank())
			lines.add(PvTooltip.Line.meta("Click to view tiers"))
		}
		return lines
	}

	private fun memberNameSpans(snapshot: CollectionSnapshot, member: CollectionSnapshot.Member): List<PvTooltip.Span> {
		val name = snapshot.displayName(member)
		val rankPlayer = snapshot.playerRank(member.uuid)
		if (rankPlayer != null) {
			return HypixelRanks.nameSpans(name, rankPlayer)
		}

		val viewed = member.uuid == snapshot.viewedUuid
		return listOf(PvTooltip.Span.bold(name, if (viewed) PvDraw.COLOR_ACCENT else PvDraw.COLOR_TEXT))
	}

	private class BoardLayout(
		val icon: Int,
		val slot: Int,
		val gap: Int,
		val rowGap: Int,
		val labelGap: Int,
		val labelH: Int,
		val catSep: Int,
		val catCols: IntArray,
		val neededW: Int,
		val maxRows: Int,
	) {
		fun cellH(): Int = slot + labelGap + labelH

		fun contentH(): Int {
			val headerH = slot + rowGap
			return headerH + maxRows * cellH() + maxOf(0, maxRows - 1) * rowGap
		}
	}

	private fun clampSelection(categories: List<HypixelCollectionsCache.Category>) {
		selectedCat = maxOf(0, minOf(selectedCat, categories.size - 1))
		val items = categories[selectedCat].items
		if (items.isEmpty()) {
			selectedItem = 0
			return
		}
		selectedItem = maxOf(0, minOf(selectedItem, items.size - 1))
	}

	private class ItemHit(
		val x: Int, val y: Int, val w: Int, val h: Int, val cat: Int, val item: Int, val entry: HypixelCollectionsCache.Item,
	)

	private companion object {
		fun layoutBoard(categories: List<HypixelCollectionsCache.Category>, w: Int, h: Int): BoardLayout {
			var best: BoardLayout? = null
			for (icon in COLL_ICON_STEPS) {
				val packs = if (icon >= 14) {
					arrayOf(intArrayOf(4, 5, 4, 2, 9, 8), intArrayOf(2, 4, 3, 2, 8, 6), intArrayOf(2, 3, 2, 1, 8, 6))
				} else {
					arrayOf(intArrayOf(2, 4, 3, 1, 8, 6), intArrayOf(2, 3, 2, 1, 7, 5), intArrayOf(2, 2, 2, 1, 7, 4))
				}
				for (p in packs) {
					val candidate = packBoard(categories, w, h, icon, icon + p[0], p[1], p[2], p[3], p[4], p[5])
					if (candidate.neededW <= w && candidate.contentH() <= h) {
						return candidate
					}
					best = candidate
				}
			}
			return requireNotNull(best)
		}

		/** Build column counts for a fixed cell size; prefer fitting height without scroll. */
		fun packBoard(
			categories: List<HypixelCollectionsCache.Category>,
			w: Int,
			h: Int,
			icon: Int,
			slot: Int,
			gap: Int,
			rowGap: Int,
			labelGap: Int,
			labelH: Int,
			catSep: Int,
		): BoardLayout {
			val n = categories.size
			val cellH = slot + labelGap + labelH
			val headerH = slot + rowGap
			val availRows = maxOf(1, (h - headerH + rowGap) / (cellH + rowGap))

			val catCols = IntArray(n) { c -> maxOf(1, (categories[c].items.size + availRows - 1) / availRows) }

			while (boardWidth(catCols, slot, gap, catSep) > w) {
				var best = -1
				var bestCols = 1
				for (c in 0 until n) {
					if (catCols[c] > bestCols) {
						bestCols = catCols[c]
						best = c
					}
				}
				if (best < 0 || bestCols <= 1) {
					break
				}
				catCols[best]--
			}

			while (true) {
				if (w - boardWidth(catCols, slot, gap, catSep) < slot + gap) {
					break
				}
				var pick = -1
				var pickRows = 0
				for (c in 0 until n) {
					val items = categories[c].items.size
					val cols = catCols[c]
					val rows = (items + cols - 1) / cols
					val newRows = (items + cols) / (cols + 1)
					if (newRows < rows && rows > pickRows) {
						pickRows = rows
						pick = c
					}
				}
				if (pick < 0) {
					break
				}
				catCols[pick]++
				if (boardWidth(catCols, slot, gap, catSep) > w) {
					catCols[pick]--
					break
				}
			}

			var maxRows = 1
			for (c in 0 until n) {
				val items = categories[c].items.size
				maxRows = maxOf(maxRows, (items + catCols[c] - 1) / catCols[c])
			}
			return BoardLayout(icon, slot, gap, rowGap, labelGap, labelH, catSep, catCols, boardWidth(catCols, slot, gap, catSep), maxRows)
		}

		fun boardWidth(catCols: IntArray, slot: Int, gap: Int, sep: Int): Int {
			var neededW = 0
			for (c in catCols.indices) {
				val cols = catCols[c]
				neededW += cols * slot + maxOf(0, cols - 1) * gap
				if (c < catCols.size - 1) {
					neededW += sep
				}
			}
			return neededW
		}
	}
}

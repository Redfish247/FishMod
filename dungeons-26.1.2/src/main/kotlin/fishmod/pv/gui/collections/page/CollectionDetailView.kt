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
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.collections.CollectionsUi
import fishmod.pv.gui.collections.CollectionsUi.ITEM_ICON
import fishmod.pv.gui.collections.CollectionsUi.MINION_UNLOCKED
import fishmod.pv.gui.collections.CollectionsUi.SLOT
import fishmod.pv.gui.collections.page.CollectionPopupLayout.Companion.CELL
import fishmod.pv.gui.collections.page.CollectionPopupLayout.Companion.ROW_H
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelCollectionsCache
import fishmod.pv.price.HypixelItemsCache
import java.util.function.Predicate
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

internal class CollectionDetailView {
	enum class TierState { UNLOCKED, NEXT, UPCOMING }

	private var item: HypixelCollectionsCache.Item? = null
	private var openStartMs = 0L
	private var selected = -1
	private var selectedReward = -1
	private var page = 0
	private var perPage = 1
	private var trackGap = TRACK_GAP
	private var scroll = 0
	private var maxScroll = 0
	private var layout: CollectionPopupLayout? = null
	private var content: CollectionPopupLayout.Content? = null
	private var contentFinal = false
	private var panelX = 0
	private var panelY = 0
	private var panelW = 0
	private var panelH = 0
	private var closeX = 0
	private var closeY = 0
	private val list = IntArray(4)
	private val track = IntArray(4)
	private var prevArrow: IntArray? = null
	private var nextArrow: IntArray? = null
	private val slots = ArrayList<IntArray>()
	private val rewardRows = ArrayList<IntArray>()
	private var hoverTip: List<PvTooltip.Line> = listOf()
	private var hoverItemId: String? = null
	private var hoverItemHint: String? = null
	private var cachedTipId: String? = null
	private var cachedTip: List<Component> = listOf()

	fun isOpen(): Boolean = item != null

	fun open(item: HypixelCollectionsCache.Item) {
		this.item = item
		openStartMs = System.currentTimeMillis()
		selected = -1
		selectedReward = -1
		page = 0
		perPage = 0
		scroll = 0
		maxScroll = 0
		content = null
		contentFinal = false
		slots.clear()
		rewardRows.clear()
	}

	fun close() {
		item = null
		hoverTip = listOf()
		hoverItemId = null
		slots.clear()
		rewardRows.clear()
	}

	fun selected(): Int = selected

	fun selectedReward(): Int = selectedReward

	fun page(): Int = page

	fun visibleSlots(): List<IntArray> = slots

	fun pageCount(): Int = item?.let { pageCount(it.tiers.size, perPage) } ?: 0

	fun keyPressed(key: Int): Boolean {
		if (item != null && key == fishmod.compat.GLFW.GLFW_KEY_ESCAPE) {
			close()
			return true
		}
		return false
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (item == null) {
			return false
		}
		if (mx >= closeX && mx < closeX + CLOSE && my >= closeY && my < closeY + CLOSE) {
			close()
			return true
		}
		if (hit(prevArrow, mx, my)) {
			setPage(page - 1)
			return true
		}
		if (hit(nextArrow, mx, my)) {
			setPage(page + 1)
			return true
		}
		for (slot in slots) {
			if (hit(slot, mx, my)) {
				selectTier(slot[4])
				return true
			}
		}
		if (hit(list, mx, my)) {
			for (row in rewardRows) {
				if (hit(row, mx, my)) {
					selectedReward = row[4]
					return true
				}
			}
		}
		if (mx < panelX || mx >= panelX + panelW || my < panelY || my >= panelY + panelH) {
			close()
		}
		return true
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (item == null) {
			return false
		}
		if (hit(list, mouseX, mouseY)) {
			CollectionsUi.scrollBy(scrollY, scroll, maxScroll, ROW_H) { scroll = it }
		} else if (hit(track, mouseX, mouseY) && scrollY != 0.0) {
			setPage(page + if (scrollY < 0) 1 else -1)
		}
		return true
	}

	private fun selectTier(index: Int) {
		if (index != selected) {
			selected = index
			selectedReward = -1
			scroll = 0
		}
	}

	private fun setPage(next: Int) {
		val tiers = item?.tiers ?: return
		val clamped = maxOf(0, minOf(pageCount() - 1, next))
		if (clamped != page) {
			page = clamped
			selectTier(minOf(tiers.size - 1, page * perPage))
		}
	}

	fun layoutPanel(px: Int, py: Int, pw: Int, ph: Int, lineH: Int, content: CollectionPopupLayout.Content) {
		val l = CollectionPopupLayout.compute(px, py, pw, ph, lineH, content)
		layout = l
		panelX = l.x
		panelY = l.y
		panelW = l.w
		panelH = l.h
		closeX = l.innerX + l.innerW - CLOSE
		closeY = l.headerY + (CollectionPopupLayout.HEADER_H - CLOSE) / 2
	}

	fun layoutTrack(x: Int, y: Int, w: Int, displayTier: Int) {
		val item = item ?: return
		val tiers = item.tiers
		if (selected < 0 || selected >= tiers.size) {
			selected = defaultSelection(item, displayTier)
		}
		val fit = perPage(tiers.size, w)
		if (fit != perPage) {
			perPage = fit
			page = selected / fit
		}
		val pages = pageCount(tiers.size, fit)
		page = maxOf(0, minOf(pages - 1, page))
		slots.clear()
		prevArrow = null
		nextArrow = null
		track[0] = x
		track[1] = y
		track[2] = w
		track[3] = SLOT + 12
		val from = page * fit
		val count = minOf(fit, tiers.size - from)
		var startX = x
		trackGap = TRACK_GAP
		if (pages > 1) {
			prevArrow = intArrayOf(x, y, ARROW, SLOT)
			nextArrow = intArrayOf(x + w - ARROW, y, ARROW, SLOT)
			startX = x + ARROW + 4
		} else {
			trackGap = trackGap(count, w)
		}
		for (i in 0 until count) {
			slots.add(intArrayOf(startX + i * (SLOT + trackGap), y, SLOT, SLOT, from + i))
		}
	}

	fun render(
		snapshot: CollectionSnapshot, g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int,
		boardX: Int, boardY: Int, boardW: Int, boardH: Int, mx: Int, my: Int,
	) {
		val item = item ?: return
		hoverTip = listOf()
		hoverItemId = null
		hoverItemHint = null
		layoutPanel(boardX, boardY, boardW, boardH, font.lineHeight, content(snapshot, font, item))

		PvDraw.fill(g, 0, 0, screenW, screenH, BACKDROP)
		val scale = openScale()
		val pcx = panelX + panelW / 2f
		val pcy = panelY + panelH / 2f
		g.pose().pushMatrix()
		if (scale < 0.999f) {
			g.pose().translate(pcx, pcy)
			g.pose().scale(scale, scale)
			g.pose().translate(-pcx, -pcy)
		}
		PvDraw.roundedFill(g, panelX, panelY, panelW, panelH, 3, POPUP_BG)
		PvDraw.roundedRing(g, panelX, panelY, panelW, panelH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		drawContent(snapshot, g, font, item, mx, my)
		g.pose().popMatrix()

		g.nextStratum()
		val hoverId = hoverItemId
		if (hoverId != null) {
			val tip = ArrayList(itemTip(hoverId))
			hoverItemHint?.let {
				tip.add(Component.empty())
				tip.add(SkyBlockItemFactory.legacyLine("§8$it"))
			}
			PvTooltip.drawComponents(g, font, tip, mx, my, screenW, screenH)
		} else if (hoverTip.isNotEmpty()) {
			PvTooltip.drawStyled(g, font, hoverTip, mx, my, screenW, screenH)
		}
	}

	private fun content(snapshot: CollectionSnapshot, font: Font, item: HypixelCollectionsCache.Item): CollectionPopupLayout.Content {
		content?.let { if (contentFinal) return it }
		var maxRewards = 0
		var recipes = false
		var rewardW = 0
		var titleW = 0
		for (t in item.tiers) {
			maxRewards = maxOf(maxRewards, t.unlocks.size)
			for (unlock in t.unlocks) {
				val r = CollectionRewards.resolve(unlock)
				rewardW = maxOf(rewardW, rewardRowW(font, r))
				if (r.isItem() && CollectionRewards.recipe(r.itemId) != null) {
					recipes = true
					titleW = maxOf(titleW, font.width(r.label))
				}
			}
		}
		val amount = snapshot.progressAmount(item)
		val tier = snapshot.displayTier(item)
		val headerW = 20 + PvDraw.widthBold(font, item.name) + 8 + font.width(tierText(item, tier)) + 6 + CLOSE
		val statsW = statsLeftW(snapshot, font, item, amount) + 12 +
			font.width(nextText(item, amount, tier, snapshot.legacyTier(item)))
		val measured = CollectionPopupLayout.Content(item.tiers.size, maxRewards, recipes, headerW, statsW, rewardW, titleW)
		content = measured
		contentFinal = NeuRepoCache.isReady() && HypixelItemsCache.allItems().isNotEmpty()
		return measured
	}

	private fun drawContent(
		snapshot: CollectionSnapshot, g: GuiGraphicsExtractor, font: Font, item: HypixelCollectionsCache.Item, mx: Int, my: Int,
	) {
		val l = layout ?: return
		val x = l.innerX
		val w = l.innerW
		val amount = snapshot.progressAmount(item)
		val tier = snapshot.displayTier(item)
		val maxed = isMaxed(item, tier)

		val iconId = CollectionsUi.resolveIconId(item.id)
		val headTextY = PvDraw.IconTextAlign.of(l.headerY, CollectionPopupLayout.HEADER_H, ITEM_ICON, font.lineHeight).textY
		CollectionsUi.drawIcon(g, iconId, x, l.headerY, ITEM_ICON)
		val closeHover = mx >= closeX && mx < closeX + CLOSE && my >= closeY && my < closeY + CLOSE
		PvDraw.roundedFill(g, closeX, closeY, CLOSE, CLOSE, 3, if (closeHover) 0xFF3A3A4A.toInt() else 0x24FFFFFF)
		PvDraw.roundedRing(g, closeX, closeY, CLOSE, CLOSE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(g, font, "x", closeX + CLOSE / 2, closeY + 2, PvDraw.COLOR_MUTED)
		if (closeHover) {
			hoverTip = listOf(PvTooltip.Line.of("Close", PvDraw.COLOR_TEXT))
		}
		val tierText = tierText(item, tier)
		val tierRight = closeX - 6
		PvDraw.textRight(g, font, tierText, tierRight, headTextY, if (maxed) MINION_UNLOCKED else PvDraw.COLOR_TEXT)
		val nameW = tierRight - font.width(tierText) - 8 - (x + 20)
		val name = CollectionsUi.trim(font, item.name, nameW)
		PvDraw.textBold(g, font, name, x + 20, headTextY, PvDraw.COLOR_GOLD)
		if (name != item.name && mx >= x && mx < x + 20 + nameW && my >= l.headerY &&
			my < l.headerY + CollectionPopupLayout.HEADER_H
		) {
			hoverTip = listOf(PvTooltip.Line.of(item.name, PvDraw.COLOR_GOLD))
		}

		val label = amountLabel(snapshot, item)
		PvDraw.text(g, font, label, x, l.statsY, PvDraw.COLOR_MUTED)
		var sx = x + font.width(label) + 4
		val total = FormatUtil.commas(amount)
		PvDraw.text(g, font, total, sx, l.statsY, PvDraw.COLOR_GOLD)
		if (isCoop(snapshot, item)) {
			sx += font.width(total) + 10
			PvDraw.text(g, font, "You", sx, l.statsY, PvDraw.COLOR_MUTED)
			PvDraw.text(
				g, font, FormatUtil.commas(snapshot.viewedAmount(item.id)), sx + font.width("You") + 4,
				l.statsY, PvDraw.COLOR_ACCENT,
			)
		}
		val legacy = snapshot.legacyTier(item)
		val nextText = nextText(item, amount, tier, legacy)
		val nextColor = if (maxed) MINION_UNLOCKED else PvDraw.COLOR_MUTED
		val nextW = minOf(w, font.width(nextText))
		val nextX = if (l.nextInline()) x + w - nextW else x
		val nextY = if (l.nextInline()) l.statsY else l.nextY
		PvDraw.text(g, font, CollectionsUi.trim(font, nextText, w), nextX, nextY, nextColor)
		if (legacy > 0 && mx >= nextX && mx < nextX + nextW && my >= nextY && my < nextY + font.lineHeight) {
			hoverTip = legacyTip(item, legacy)
		}
		PvDraw.progressBar(
			g, x, l.barY, w, PvDraw.BAR_HEIGHT, if (maxed) 1f else item.progressToNext(amount),
			PvDraw.COLOR_BAR_FILL, maxed,
		)

		val tiers = item.tiers
		if (tiers.isEmpty()) {
			PvDraw.text(g, font, "No tier data", x, l.trackY, PvDraw.COLOR_MUTED)
			slots.clear()
			rewardRows.clear()
			list[2] = 0
			track[2] = 0
			maxScroll = 0
			return
		}
		layoutTrack(x, l.trackY, w, tier)
		if (l.hasTiersHeader()) {
			val pages = pageCount(tiers.size, perPage)
			if (pages > 1) {
				val pageText = "Page ${page + 1} / $pages"
				PvDraw.sectionHeader(g, font, "Tiers", x, l.tiersY, w - font.width(pageText) - 4)
				PvDraw.textRight(g, font, pageText, x + w, l.tiersY, PvDraw.COLOR_MUTED)
			} else {
				PvDraw.sectionHeader(g, font, "Tiers", x, l.tiersY, w)
			}
		}
		drawTrack(g, font, item, amount, tier, iconId, mx, my)

		val rewards = drawSelected(g, font, item, amount, tier, mx, my)
		PvDraw.fill(
			g, l.recipeX - CollectionPopupLayout.COL_GAP / 2, l.columnsY, 1,
			maxOf(0, l.bottom - l.columnsY), SLOT_BORDER,
		)
		drawRecipe(g, font, rewards, mx, my)
	}

	private fun drawTrack(
		g: GuiGraphicsExtractor, font: Font, item: HypixelCollectionsCache.Item, amount: Long, tier: Int, iconId: String,
		mx: Int, my: Int,
	) {
		val tiers = item.tiers
		for (arrow in arrayOf(prevArrow, nextArrow)) {
			if (arrow == null) {
				continue
			}
			val prev = arrow === prevArrow
			val enabled = if (prev) page > 0 else page < pageCount(tiers.size, perPage) - 1
			val hover = enabled && hit(arrow, mx.toDouble(), my.toDouble())
			PvDraw.roundedFill(g, arrow[0], arrow[1], arrow[2], arrow[3], 3, if (hover) 0xFF3A3A4A.toInt() else 0x24FFFFFF)
			PvDraw.roundedRing(g, arrow[0], arrow[1], arrow[2], arrow[3], 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
			PvDraw.textCentered(
				g, font, if (prev) "<" else ">", arrow[0] + arrow[2] / 2,
				arrow[1] + (arrow[3] - font.lineHeight) / 2 + 1, if (enabled) PvDraw.COLOR_TEXT else SLOT_BORDER,
			)
			if (hover) {
				hoverTip = listOf(PvTooltip.Line.of(if (prev) "Previous tiers" else "More tiers", PvDraw.COLOR_TEXT))
			}
		}
		for ((i, s) in slots.withIndex()) {
			val t = tiers[s[4]]
			val state = tierState(item, t, amount, tier)
			if (i + 1 < slots.size) {
				val after = tierState(item, tiers[s[4] + 1], amount, tier)
				PvDraw.fill(
					g, s[0] + SLOT, s[1] + SLOT / 2 - 1, trackGap, 2,
					if (after == TierState.UNLOCKED) MINION_UNLOCKED else SLOT_BORDER,
				)
			}
			val hover = hit(s, mx.toDouble(), my.toDouble())
			val chosen = s[4] == selected
			PvDraw.fill(g, s[0], s[1], SLOT, SLOT, SLOT_BG)
			CollectionsUi.drawIcon(g, iconId, s[0] + (SLOT - ITEM_ICON) / 2, s[1] + (SLOT - ITEM_ICON) / 2, ITEM_ICON)
			if (state == TierState.UPCOMING) {
				PvDraw.fill(g, s[0] + 1, s[1] + 1, SLOT - 2, SLOT - 2, LOCKED_SHADE)
			}
			val border = if (chosen) PvDraw.COLOR_ACCENT else if (hover) PvDraw.COLOR_TEXT else slotBorder(state)
			g.outline(s[0], s[1], SLOT, SLOT, border)
			if (state == TierState.NEXT) {
				val fill = item.progressToNext(amount)
				PvDraw.fill(g, s[0] + 1, s[1] + SLOT - 3, SLOT - 2, 2, PvDraw.COLOR_BAR_BG)
				PvDraw.fill(
					g, s[0] + 1, s[1] + SLOT - 3, Math.round((SLOT - 2) * fill.coerceIn(0f, 1f)), 2, PvDraw.COLOR_BAR_FILL,
				)
			}
			PvDraw.textCentered(g, font, roman(t.tier), s[0] + SLOT / 2, s[1] + SLOT + 2, stateColor(state))
			if (hover) {
				val tip = ArrayList(tierTip(item, t, amount, tier))
				tip.add(PvTooltip.Line.meta(if (chosen) "Shown below" else "Click to show rewards"))
				hoverTip = tip
			}
		}
	}

	private fun drawSelected(
		g: GuiGraphicsExtractor, font: Font, item: HypixelCollectionsCache.Item, amount: Long, tier: Int, mx: Int, my: Int,
	): List<CollectionRewards.Reward> {
		val l = layout ?: return listOf()
		val x = l.innerX
		val w = l.innerW
		rewardRows.clear()
		list[2] = 0
		if (selected < 0 || selected >= item.tiers.size) {
			maxScroll = 0
			return listOf()
		}
		val t = item.tiers[selected]
		val state = tierState(item, t, amount, tier)
		val color = stateColor(state)
		val stateText = when (state) {
			TierState.UNLOCKED -> "Unlocked"
			TierState.NEXT -> "In progress"
			TierState.UPCOMING -> "Locked"
		}
		PvDraw.textRight(g, font, stateText, x + w, l.selectedY, color)
		PvDraw.textBold(
			g, font, CollectionsUi.trim(font, item.name + " " + roman(t.tier), w - font.width(stateText) - 8),
			x, l.selectedY, color,
		)

		val required = maxOf(1L, t.amountRequired)
		val unlocked = state == TierState.UNLOCKED
		val fill = if (unlocked) 1f else (amount / required.toDouble()).coerceIn(0.0, 1.0).toFloat()
		val progress = if (unlocked) {
			FormatUtil.commas(t.amountRequired) + " / " + FormatUtil.commas(t.amountRequired)
		} else {
			FormatUtil.commas(amount) + " / " + FormatUtil.commas(t.amountRequired) + " (" + Math.round(fill * 100f) + "%)"
		}
		val progressW = font.width(progress)
		PvDraw.textRight(g, font, progress, x + w, l.selectedBarY, if (unlocked) MINION_UNLOCKED else PvDraw.COLOR_TEXT)
		val barW = w - progressW - 8
		if (barW > 12) {
			PvDraw.progressBar(
				g, x, l.selectedBarY + (font.lineHeight - PvDraw.BAR_HEIGHT) / 2, barW,
				PvDraw.BAR_HEIGHT, fill, PvDraw.COLOR_BAR_FILL,
			)
		}

		val rw = l.rewardsW
		PvDraw.sectionHeader(g, font, "Rewards", x, l.columnsY, rw)
		val y = l.columnsBodyY(font.lineHeight)
		val bottom = l.bottom
		val rewards = t.unlocks.map { CollectionRewards.resolve(it) }
		if (selectedReward < 0 || selectedReward >= rewards.size) {
			selectedReward = defaultReward(rewards) { CollectionRewards.recipe(it) != null }
		}
		val listH = maxOf(0, bottom - y)
		list[0] = x - 2
		list[1] = y
		list[2] = rw + 4
		list[3] = listH
		maxScroll = maxOf(0, rewards.size * ROW_H - listH)
		scroll = maxOf(0, minOf(scroll, maxScroll))
		if (rewards.isEmpty()) {
			PvDraw.text(g, font, "Rewards not in the API", x, y + 2, PvDraw.COLOR_MUTED)
			return rewards
		}
		val inList = hit(list, mx.toDouble(), my.toDouble())
		g.enableScissor(list[0], y, list[0] + list[2], y + listH)
		var rowY = y - scroll
		for ((i, r) in rewards.withIndex()) {
			if (rowY + ROW_H <= y || rowY >= y + listH) {
				rowY += ROW_H
				continue
			}
			val row = intArrayOf(x - 2, rowY, rw + 4, ROW_H, i)
			rewardRows.add(row)
			val hover = inList && hit(row, mx.toDouble(), my.toDouble())
			val chosen = r.isItem() && i == selectedReward
			if (chosen || hover) {
				PvDraw.fill(g, row[0], row[1], row[2], row[3], if (chosen) ROW_CHOSEN else ROW_HOVER)
			}
			if (chosen) {
				g.outline(row[0], row[1], row[2], row[3], PvDraw.COLOR_ACCENT)
			}
			val align = PvDraw.IconTextAlign.of(rowY, ROW_H, ITEM_ICON, font.lineHeight)
			if (r.hasIcon()) {
				CollectionsUi.drawIcon(g, r.iconId, x, align.iconY, ITEM_ICON)
			}
			val textW = rw - 20 - 2
			val suffix = r.suffix.isNotEmpty() && font.width(r.label) + 6 + font.width(r.suffix) <= textW
			if (suffix) {
				PvDraw.textRight(g, font, r.suffix, x + rw - 2, align.textY, PvDraw.COLOR_MUTED)
			}
			val label = CollectionsUi.trim(font, r.label, textW)
			PvDraw.text(g, font, label, x + 20, align.textY, CollectionRewards.color(r))
			if (hover) {
				if (r.isItem()) {
					hoverItemId = r.itemId
					hoverItemHint = if (chosen) null else "Click to preview the recipe"
				} else if (label != r.text) {
					hoverTip = listOf(PvTooltip.Line.of(r.text, CollectionRewards.color(r)))
				}
			}
			rowY += ROW_H
		}
		g.disableScissor()
		return rewards
	}

	private fun drawRecipe(g: GuiGraphicsExtractor, font: Font, rewards: List<CollectionRewards.Reward>, mx: Int, my: Int) {
		val l = layout ?: return
		val x = l.recipeX
		val w = l.recipeW
		val bottom = l.bottom
		val reward = rewards.getOrNull(selectedReward)
		val recipe = reward?.let { CollectionRewards.recipe(it.itemId) }
		PvDraw.sectionHeader(g, font, if (recipe != null && recipe.forge) "Forge recipe" else "Recipe", x, l.columnsY, w)
		var y = l.columnsBodyY(font.lineHeight)
		if (reward == null || !reward.isItem()) {
			PvDraw.text(g, font, "No item reward", x, y + 2, PvDraw.COLOR_MUTED)
			return
		}
		val room = bottom - y
		val titled = recipe == null || room >= CollectionPopupLayout.recipeBodyH(font.lineHeight, true) || room < CELL * 3
		if (titled) {
			val title = CollectionsUi.trim(font, reward.label, w)
			PvDraw.text(g, font, title, x, y + 2, CollectionRewards.color(reward))
			if (title != reward.label && mx >= x && mx < x + w && my >= y && my < y + font.lineHeight + 2) {
				hoverItemId = reward.itemId
			}
			y += font.lineHeight + 4
		}
		if (recipe == null) {
			PvDraw.text(g, font, "No recipe data", x, y + 2, PvDraw.COLOR_MUTED)
			return
		}
		if (y + CELL * 3 > bottom) {
			return
		}
		for (i in 0 until 9) {
			val cx = x + (i % 3) * CELL
			val cy = y + (i / 3) * CELL
			drawCell(g, font, recipe.ids[i], recipe.counts[i], cx, cy, mx, my)
		}
		val arrowX = x + CELL * 3
		PvDraw.textCentered(g, font, ">", arrowX + 8, y + CELL + (CELL - font.lineHeight) / 2 + 1, PvDraw.COLOR_MUTED)
		drawCell(g, font, reward.itemId, recipe.outputCount, arrowX + 16, y + CELL, mx, my)
	}

	private fun drawCell(g: GuiGraphicsExtractor, font: Font, id: String?, count: Int, x: Int, y: Int, mx: Int, my: Int) {
		PvDraw.roundedFill(g, x, y, CELL, CELL, 3, SLOT_BG)
		PvDraw.roundedRing(g, x, y, CELL, CELL, 3, SLOT_BORDER, PvDraw.C_ALL)
		if (id == null) {
			return
		}
		CollectionsUi.drawIcon(g, id, x + 1, y + 1, ITEM_ICON)
		if (count > 1) {
			val text = if (count > 999) FormatUtil.shortXp(count.toDouble()) else count.toString()
			PvDraw.textRight(g, font, text, x + CELL, y + CELL - font.lineHeight + 1, PvDraw.COLOR_TEXT)
		}
		if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
			g.outline(x, y, CELL, CELL, PvDraw.COLOR_TEXT)
			hoverItemId = id
			hoverItemHint = if (count > 1) "x" + FormatUtil.commas(count.toLong()) else null
		}
	}

	private fun itemTip(id: String): List<Component> {
		if (id != cachedTipId) {
			val slot = petSlot(id) ?: InventorySnapshot.Slot(id, 1, listOf(), null, null, null, null)
			cachedTip = SkyBlockItemFactory.tooltipLines(slot, SkyBlockItemFactory.toStack(slot), false)
			cachedTipId = id
		}
		return cachedTip
	}

	private fun openScale(): Float {
		val elapsed = System.currentTimeMillis() - openStartMs
		if (elapsed >= OPEN_MS) {
			return 1.0f
		}
		val t = (elapsed / OPEN_MS.toFloat()).coerceIn(0.0f, 1.0f)
		val eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
		return OPEN_SCALE_START + (1.0f - OPEN_SCALE_START) * eased
	}

	companion object {
		private const val OPEN_MS = 220
		private const val OPEN_SCALE_START = 0.12f
		private const val CLOSE = 12
		private const val TRACK_GAP = 6
		private const val MAX_TRACK_GAP = 40
		private const val ARROW = 12
		private const val SLOT_BG = 0x1CFFFFFF
		private const val SLOT_BORDER = 0x30FFFFFF
		private const val LOCKED_SHADE = 0x88000000.toInt()
		private const val ROW_HOVER = 0x24FFFFFF
		private const val ROW_CHOSEN = 0x1CFFFFFF
		// Same navy as PvDraw.COLOR_PANEL, nearly opaque so the board doesn't read through text and icons.
		private const val POPUP_BG = 0xF7101018.toInt()
		private const val BACKDROP = 0xB0000000.toInt()

		private fun hit(r: IntArray?, mx: Double, my: Double): Boolean =
			r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]

		fun perPage(tierCount: Int, width: Int): Int {
			if (tierCount <= 0) {
				return 1
			}
			if (tierCount * SLOT + (tierCount - 1) * TRACK_GAP <= width) {
				return tierCount
			}
			val room = width - (ARROW + 4) * 2
			return maxOf(1, (room + TRACK_GAP) / (SLOT + TRACK_GAP))
		}

		fun pageCount(tierCount: Int, perPage: Int): Int =
			if (perPage <= 0) 1 else maxOf(1, (tierCount + perPage - 1) / perPage)

		fun trackGap(count: Int, width: Int): Int {
			if (count <= 1) {
				return TRACK_GAP
			}
			return maxOf(TRACK_GAP, minOf(MAX_TRACK_GAP, (width - count * SLOT) / (count - 1)))
		}

		fun defaultSelection(item: HypixelCollectionsCache.Item, displayTier: Int): Int {
			val i = item.tiers.indexOfFirst { it.tier > displayTier }
			return if (i >= 0) i else maxOf(0, item.tiers.size - 1)
		}

		fun defaultReward(rewards: List<CollectionRewards.Reward>, hasRecipe: Predicate<String?>): Int {
			var firstItem = -1
			for ((i, r) in rewards.withIndex()) {
				if (!r.isItem()) {
					continue
				}
				if (hasRecipe.test(r.itemId)) {
					return i
				}
				if (firstItem < 0) {
					firstItem = i
				}
			}
			return firstItem
		}

		private fun rewardRowW(font: Font, r: CollectionRewards.Reward): Int =
			20 + font.width(r.label) + (if (r.suffix.isEmpty()) 0 else 6 + font.width(r.suffix)) + 4

		fun legacyTip(item: HypixelCollectionsCache.Item, legacyTier: Int): List<PvTooltip.Line> = listOf(
			PvTooltip.Line.title(item.name + " " + roman(legacyTier), PvDraw.COLOR_GOLD),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("This profile still lists tier " + roman(legacyTier) + " from before", PvDraw.COLOR_TEXT),
			PvTooltip.Line.of("Hypixel cut the collection to " + item.maxTiers + " tiers.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.meta("Tiers above " + roman(item.maxTiers) + " no longer exist."),
		)

		fun tierText(item: HypixelCollectionsCache.Item, tier: Int): String = "Tier $tier / ${item.maxTiers}"

		private fun isMaxed(item: HypixelCollectionsCache.Item, tier: Int): Boolean = item.maxTiers > 0 && tier >= item.maxTiers

		fun nextText(item: HypixelCollectionsCache.Item, amount: Long, tier: Int, legacyTier: Int): String {
			val next = if (isMaxed(item, tier)) null else item.nextTier(amount)
			if (next == null) {
				return if (legacyTier > 0) "Maxed (old tier " + roman(legacyTier) + ")" else "Maxed"
			}
			return "Next tier: " + FormatUtil.shortXp(amount.toDouble()) + " / " + FormatUtil.shortXp(next.amountRequired.toDouble()) +
				" (" + Math.round(item.progressToNext(amount) * 100f) + "%)"
		}

		private fun isCoop(snapshot: CollectionSnapshot, item: HypixelCollectionsCache.Item): Boolean =
			!BossCollections.isBossId(item.id) && snapshot.members.size > 1

		private fun amountLabel(snapshot: CollectionSnapshot, item: HypixelCollectionsCache.Item): String =
			if (BossCollections.isBossId(item.id)) "Kills" else if (isCoop(snapshot, item)) "Coop total" else "Collected"

		private fun statsLeftW(snapshot: CollectionSnapshot, font: Font, item: HypixelCollectionsCache.Item, amount: Long): Int {
			var w = font.width(amountLabel(snapshot, item)) + 4 + font.width(FormatUtil.commas(amount))
			if (isCoop(snapshot, item)) {
				w += 10 + font.width("You") + 4 + font.width(FormatUtil.commas(snapshot.viewedAmount(item.id)))
			}
			return w
		}

		private fun petSlot(id: String): InventorySnapshot.Slot? {
			val semi = id.indexOf(';')
			if (semi <= 0 || !CollectionRewards.isPet(id)) {
				return null
			}
			val tier: String = SkyBlockItemFactory.resolveTier(id)
			return if (tier.isBlank()) null else SkyBlockItemFactory.auctionPetSlot("PET_" + id.substring(0, semi), "[Lvl 1]", tier)
		}

		private fun slotBorder(state: TierState): Int = when (state) {
			TierState.UNLOCKED -> MINION_UNLOCKED
			TierState.NEXT -> PvDraw.COLOR_GOLD
			TierState.UPCOMING -> SLOT_BORDER
		}

		private fun stateColor(state: TierState): Int = when (state) {
			TierState.UNLOCKED -> MINION_UNLOCKED
			TierState.NEXT -> PvDraw.COLOR_GOLD
			TierState.UPCOMING -> PvDraw.COLOR_MUTED
		}

		fun tierState(
			item: HypixelCollectionsCache.Item, tier: HypixelCollectionsCache.Tier, @Suppress("UNUSED_PARAMETER") amount: Long, displayTier: Int,
		): TierState {
			if (tier.tier <= displayTier) {
				return TierState.UNLOCKED
			}
			val firstLocked = item.tiers.firstOrNull { it.tier > displayTier } ?: return TierState.UPCOMING
			return if (firstLocked.tier == tier.tier) TierState.NEXT else TierState.UPCOMING
		}

		fun rewardSummary(tier: HypixelCollectionsCache.Tier): String {
			val main = ArrayList<String>()
			var xp: String? = null
			for (unlock in tier.unlocks) {
				if (isSkyBlockXp(unlock)) {
					xp = unlock
				} else {
					main.add(unlock)
				}
			}
			if (main.isEmpty()) {
				return xp ?: "-"
			}
			return if (main.size == 1) main[0] else main[0] + " +" + (main.size - 1) + " more"
		}

		fun tierTip(
			item: HypixelCollectionsCache.Item,
			tier: HypixelCollectionsCache.Tier,
			amount: Long,
			displayTier: Int,
		): List<PvTooltip.Line> {
			val state = tierState(item, tier, amount, displayTier)
			val lines = ArrayList<PvTooltip.Line>()
			lines.add(PvTooltip.Line.title(item.name + " " + roman(tier.tier), stateColor(state)))
			lines.add(PvTooltip.Line.divider())
			lines.add(PvTooltip.Line.row("Required", PvDraw.COLOR_MUTED, FormatUtil.commas(tier.amountRequired), PvDraw.COLOR_TEXT))
			if (state == TierState.UNLOCKED) {
				lines.add(PvTooltip.Line.of("Unlocked", MINION_UNLOCKED))
			} else {
				val required = maxOf(1L, tier.amountRequired)
				val pct = minOf(100L, maxOf(0L, amount * 100L / required)).toInt()
				lines.add(PvTooltip.Line.row(
					"Progress", PvDraw.COLOR_MUTED,
					FormatUtil.commas(amount) + " / " + FormatUtil.commas(tier.amountRequired) + " (" + pct + "%)",
					PvDraw.COLOR_TEXT,
				))
				lines.add(PvTooltip.Line.row(
					"Remaining", PvDraw.COLOR_MUTED,
					FormatUtil.commas(maxOf(0L, tier.amountRequired - amount)), PvDraw.COLOR_TEXT,
				))
			}
			lines.add(PvTooltip.Line.blank())
			lines.add(PvTooltip.Line.of("Rewards", PvDraw.COLOR_MUTED))
			if (tier.unlocks.isEmpty()) {
				lines.add(PvTooltip.Line.meta("Rewards not in the API"))
			} else {
				for (unlock in tier.unlocks) {
					val reward = CollectionRewards.resolve(unlock)
					lines.add(PvTooltip.Line.of(reward.text, CollectionRewards.color(reward)))
				}
			}
			return lines
		}

		private fun isSkyBlockXp(unlock: String?): Boolean = unlock != null && unlock.endsWith("SkyBlock XP")

		fun roman(n: Int): String {
			if (n <= 0) {
				return n.toString()
			}
			val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
			val numerals = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
			val sb = StringBuilder()
			var left = n
			for (i in values.indices) {
				while (left >= values[i]) {
					sb.append(numerals[i])
					left -= values[i]
				}
			}
			return sb.toString()
		}
	}
}

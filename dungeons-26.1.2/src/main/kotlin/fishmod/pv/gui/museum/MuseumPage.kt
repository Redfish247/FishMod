/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.museum

import com.google.gson.JsonObject
import fishmod.compat.CI
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.MinionCosts
import fishmod.pv.data.MuseumCatalog
import fishmod.pv.gui.PvAhs
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.MuseumSort
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import fishmod.pv.price.HypixelItemsCache
import java.util.IdentityHashMap
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class MuseumPage {
	enum class LoadState {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	@JvmRecord
	data class Slot(
		val donationId: String,
		val stack: InventoryDecoder.Stack?,
		val missing: Boolean,
		val coveredBy: String?,
		val donatedTimeMs: Long
	) {
		constructor(donationId: String, stack: InventoryDecoder.Stack?, missing: Boolean) :
			this(donationId, stack, missing, null, 0L)

		constructor(donationId: String, stack: InventoryDecoder.Stack?, missing: Boolean, coveredBy: String?) :
			this(donationId, stack, missing, coveredBy, 0L)

		fun coveredByHigherTier(): Boolean = !coveredBy.isNullOrBlank()
	}

	/** Per-slot draw data resolved once instead of every frame. */
	private class SlotView(val iconId: String, val rarityArgb: Int)

	private class CheapRow(val donationId: String, val iconId: String, val name: String, val nameColor: Int, val price: Double)

	private class SortHit(val x: Int, val y: Int, val w: Int, val h: Int, val option: MuseumSort)

	private var state = LoadState.IDLE
	private var error = ""
	private var museumMember = JsonObject()
	private var sort = MuseumSort.ALL

	private var slots: List<Slot> = listOf()
	private val iconByIndex = IdentityHashMap<Slot, ItemStack>()
	private val missingIconById = HashMap<String, ItemStack>()
	private val viewBySlot = IdentityHashMap<Slot, SlotView>()
	private val weakSlots = ArrayList<Slot>()
	private val weakMissingIds = ArrayList<String>()
	private var nextWeakRetryMs = 0L
	private var missingView: List<Slot> = listOf()
	private var cheapest: List<CheapRow> = listOf()
	private var hoverCheapName: String? = null
	private var costToMax = 0.0
	private var donatedValue = 0.0
	private var donatedCount = 0
	private var totalCount = 0
	private var appraisal: Boolean? = null
	private var cacheDirty = true

	private var gridScroll = 0
	private var gridMaxScroll = 0
	private var listScroll = 0
	private var listMaxScroll = 0

	private var leftX = 0
	private var leftY = 0
	private var leftW = 0
	private var leftH = 0
	private var rightX = 0
	private var rightY = 0
	private var rightW = 0
	private var rightH = 0
	private var gridX = 0
	private var gridY = 0
	private var gridW = 0
	private var gridH = 0
	private var listX = 0
	private var listY = 0
	private var listW = 0
	private var listH = 0
	private var missingOnly = false
	private val sortHits = ArrayList<SortHit>()
	private var missingHitX = 0
	private var missingHitY = 0
	private var missingHitW = 0
	private var missingHitH = 0
	private var refreshHitX = 0
	private var refreshHitY = 0
	private var refreshHitW = 0
	private var refreshHitH = 0

	private var hoverKey = ""
	private var hoverTip: List<Component> = listOf()

	fun setSort(sort: MuseumSort?) {
		val next = sort ?: MuseumSort.ALL
		if (next != this.sort) {
			this.sort = next
			gridScroll = 0
			listScroll = 0
			cacheDirty = true
		}
	}

	fun sort(): MuseumSort = sort

	fun state(): LoadState = state

	fun applyLoading() {
		state = LoadState.LOADING
		error = ""
		appraisal = null
		cacheDirty = true
	}

	fun applyError(message: String?) {
		state = LoadState.ERROR
		error = if (message.isNullOrBlank()) "Museum unavailable" else message
		museumMember = JsonObject()
		appraisal = null
		cacheDirty = true
	}

	fun applyMuseum(member: JsonObject?) {
		val next = member ?: JsonObject()
		// Same cached member instance: skip dirty/scroll reset so we do not re-decode every frame.
		if (state == LoadState.READY && museumMember === next) {
			return
		}
		state = LoadState.READY
		error = ""
		museumMember = next
		appraisal = readAppraisal(next)
		gridScroll = 0
		listScroll = 0
		cacheDirty = true
	}

	fun reset() {
		state = LoadState.IDLE
		error = ""
		museumMember = JsonObject()
		appraisal = null
		gridScroll = 0
		listScroll = 0
		slots = listOf()
		iconByIndex.clear()
		missingIconById.clear()
		viewBySlot.clear()
		weakSlots.clear()
		weakMissingIds.clear()
		cheapest = listOf()
		costToMax = 0.0
		donatedValue = 0.0
		donatedCount = 0
		totalCount = 0
		cacheDirty = true
		hoverKey = ""
		hoverTip = listOf()
	}

	fun clickCheapest(): Boolean {
		val name = hoverCheapName ?: return false
		return PvAhs.search(name)
	}

	fun clickMissingOnly(mx: Double, my: Double): Boolean {
		for (hit in sortHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				setSort(hit.option)
				return true
			}
		}
		if (missingHitW > 0
			&& mx >= missingHitX && mx < missingHitX + missingHitW
			&& my >= missingHitY && my < missingHitY + missingHitH
		) {
			missingOnly = !missingOnly
			gridScroll = 0
			return true
		}
		return false
	}

	fun clickRefresh(mx: Double, my: Double): Boolean =
		mx >= refreshHitX && mx < refreshHitX + refreshHitW
			&& my >= refreshHitY && my < refreshHitY + refreshHitH
			&& refreshHitW > 0
			&& state != LoadState.LOADING

	fun mouseScrolled(mx: Double, my: Double, scrollY: Double): Boolean {
		if (state != LoadState.READY) {
			return false
		}
		val overRight = mx >= rightX && mx < rightX + rightW && my >= rightY && my < rightY + rightH
		val overLeft = mx >= leftX && mx < leftX + leftW && my >= leftY && my < leftY + leftH
		if (overRight && listMaxScroll > 0) {
			val delta = if (scrollY > 0) -LIST_SCROLL_STEP else LIST_SCROLL_STEP
			val next = maxOf(0, minOf(listMaxScroll, listScroll + delta))
			if (next != listScroll) {
				listScroll = next
				return true
			}
			return false
		}
		if (overLeft && gridMaxScroll > 0) {
			val delta = if (scrollY > 0) -SCROLL_STEP else SCROLL_STEP
			val next = maxOf(0, minOf(gridMaxScroll, gridScroll + delta))
			if (next != gridScroll) {
				gridScroll = next
				return true
			}
		}
		return false
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mx: Int,
		my: Int,
		screenW: Int,
		screenH: Int
	) {
		ensureCache()

		var leftW = maxOf(160, Math.round(w * 0.70).toInt())
		val rightW = maxOf(110, w - leftW - GAP)
		leftW = w - rightW - GAP
		val rx = x + leftW + GAP

		this.leftX = x
		this.leftY = y
		this.leftW = leftW
		this.leftH = h
		this.rightX = rx
		this.rightY = y
		this.rightW = rightW
		this.rightH = h

		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, rx, y, rightW, h)

		var nextHover = drawLeft(g, font, x, y, leftW, h, mx, my)
		val rightHover = drawRight(g, font, rx, y, rightW, h, mx, my)
		if (!rightHover.isNullOrBlank()) {
			nextHover = rightHover
		}

		if (nextHover.isNullOrBlank()) {
			hoverKey = ""
			hoverTip = listOf()
		} else if (nextHover != hoverKey) {
			hoverKey = nextHover
			val slot = slotByKey(nextHover)
			hoverTip = if (slot == null) listOf() else tooltipFor(slot)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mx: Int, my: Int, screenW: Int, screenH: Int) {
		if (hoverTip.isNotEmpty()) {
			PvTooltip.drawComponents(g, font, hoverTip, mx, my, screenW, screenH)
		}
	}

	private fun drawLeft(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int): String? {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		val bottom = y + h - PAD

		PvDraw.text(g, font, "Museum", lx, ly, PvDraw.COLOR_MUTED)
		val refreshEnabled = state != LoadState.LOADING
		val refreshLabel = if (refreshEnabled) "Refresh" else "Loading..."
		val btnW = maxOf(56, font.width(refreshLabel) + 10)
		val btnH = font.lineHeight + 6
		val btnX = lx + lw - btnW
		val btnY = ly - 1
		refreshHitX = btnX
		refreshHitY = btnY
		refreshHitW = btnW
		refreshHitH = btnH
		val hoverRefresh = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH
		PvDraw.fill(g, btnX, btnY, btnW, btnH, ITEM_SLOT_BG)
		g.outline(btnX, btnY, btnW, btnH, if (hoverRefresh && refreshEnabled) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER)
		PvDraw.textCentered(
			g, font, refreshLabel, btnX + btnW / 2, btnY + (btnH - font.lineHeight) / 2,
			if (refreshEnabled) ENABLED else DISABLED
		)

		val missingLabel = "Show missing only"
		val mW = font.width(missingLabel) + 10
		val mX = btnX - 6 - mW
		missingHitX = mX
		missingHitY = btnY
		missingHitW = mW
		missingHitH = btnH
		val hoverMissing = mx >= mX && mx < mX + mW && my >= btnY && my < btnY + btnH
		PvDraw.roundedFill(g, mX, btnY, mW, btnH, 3, if (missingOnly) 0x50386CC8 else ITEM_SLOT_BG)
		val ring = when {
			missingOnly -> PvDraw.COLOR_ACCENT
			hoverMissing -> 0x60FFFFFF
			else -> PvDraw.COLOR_BORDER
		}
		PvDraw.roundedRing(g, mX, btnY, mW, btnH, 3, ring, PvDraw.C_ALL)
		PvDraw.textCentered(
			g, font, missingLabel, mX + mW / 2, btnY + (btnH - font.lineHeight) / 2,
			if (missingOnly) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
		)

		ly += font.lineHeight + 6

		sortHits.clear()
		var sx = lx
		var sy = ly
		val pillH = font.lineHeight + 6
		for (option in MuseumSort.entries) {
			val label = option.label().string
			val pw = font.width(label) + 10
			if (sx + pw > lx + lw && sx > lx) {
				sx = lx
				sy += pillH + 3
			}
			val on = option == sort
			val hov = mx >= sx && mx < sx + pw && my >= sy && my < sy + pillH
			val fill = when {
				on -> 0x70386CC8
				hov -> 0x30FFFFFF
				else -> PvDraw.CELL
			}
			PvDraw.roundedFill(g, sx, sy, pw, pillH, 4, fill)
			PvDraw.textCentered(
				g, font, label, sx + pw / 2, sy + (pillH - font.lineHeight) / 2,
				if (on) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
			)
			sortHits.add(SortHit(sx, sy, pw, pillH, option))
			sx += pw + 3
		}
		ly = sy + pillH + 6

		val stateMessage = when (state) {
			LoadState.LOADING -> "Loading museum..."
			LoadState.ERROR -> error
			LoadState.IDLE -> "Museum not loaded"
			LoadState.READY -> null
		}
		if (stateMessage != null) {
			PvDraw.textCentered(g, font, stateMessage, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			gridMaxScroll = 0
			return null
		}
		retryWeakIcons()
		val view = if (missingOnly) missingView else slots
		if (view.isEmpty()) {
			PvDraw.textCentered(
				g, font, if (HypixelItemsCache.allItems().isEmpty()) "Loading item data..." else "No museum slots",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED
			)
			gridMaxScroll = 0
			return null
		}

		val cols = maxOf(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP))
		val rows = (view.size + cols - 1) / cols
		val contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP
		gridX = x
		gridY = ly
		gridW = w
		gridH = maxOf(0, bottom - ly)
		gridMaxScroll = maxOf(0, contentH - gridH)
		gridScroll = minOf(gridScroll, gridMaxScroll)

		val rowPitch = SLOT + SLOT_GAP
		val firstRow = maxOf(0, gridScroll / rowPitch)
		val lastRow = minOf(rows - 1, (gridScroll + gridH) / rowPitch + 1)
		val firstIndex = firstRow * cols
		val lastIndex = minOf(view.size - 1, (lastRow + 1) * cols - 1)

		var hoveredKey: String? = null
		hoverCheapName = null
		g.enableScissor(lx, gridY, lx + lw + 3, gridY + gridH)
		for (i in firstIndex..lastIndex) {
			val slot = view[i]
			val bx = lx + (i % cols) * rowPitch
			val by = ly + (i / cols) * rowPitch - gridScroll
			if (by + SLOT < gridY || by > gridY + gridH) {
				continue
			}
			val hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT && my >= gridY && my < gridY + gridH
			val sv = slotView(slot)
			PvDraw.fill(g, bx, by, SLOT, SLOT, raritySlotBackground(sv.rarityArgb, slot.missing, hovered))
			if (hovered) {
				g.outline(bx, by, SLOT, SLOT, PvDraw.COLOR_ACCENT)
			}
			val pad = maxOf(0, (SLOT - ICON) / 2)
			val icon = if (slot.missing) missingIcon(slot.donationId, sv.iconId) else iconAt(slot)
			drawSkyblockIcon(g, sv.iconId, icon, bx + pad, by + pad, ICON)
			if (hovered) {
				hoveredKey = slot.donationId
			}
		}
		g.disableScissor()
		return hoveredKey
	}

	private fun drawRight(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int): String? {
		if (state != LoadState.READY) {
			listMaxScroll = 0
			return null
		}
		val lx = x + PAD
		var ly = y + PAD
		val lw = maxOf(8, w - PAD * 2)
		val bottom = y + h - PAD

		val type = sort.label().string
		val count = FormatUtil.commas(donatedCount.toLong()) + "/" + FormatUtil.commas(totalCount.toLong())
		val complete = donatedCount >= totalCount && totalCount > 0
		PvDraw.text(g, font, type, lx, ly, PvDraw.COLOR_TEXT)
		PvDraw.text(g, font, count, lx + lw - font.width(count), ly, if (complete) ENABLED else PvDraw.COLOR_GOLD)
		ly += font.lineHeight + 3

		val costValue = FormatUtil.shortCoins(costToMax)
		PvDraw.text(g, font, "Cost:", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, costValue, lx + lw - font.width(costValue), ly, PvDraw.COLOR_GOLD)
		ly += font.lineHeight + 2

		val totalValue = FormatUtil.shortCoins(donatedValue + costToMax)
		PvDraw.text(g, font, "Total:", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, totalValue, lx + lw - font.width(totalValue), ly, PvDraw.COLOR_GOLD)
		ly += font.lineHeight + 2

		val missingValue = FormatUtil.commas((totalCount - donatedCount).toLong())
		PvDraw.text(g, font, "Missing:", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.text(
			g, font, missingValue, lx + lw - font.width(missingValue), ly,
			if (totalCount - donatedCount > 0) 0xFFFF5555.toInt() else ENABLED
		)
		ly += font.lineHeight + 2

		val app = appraisal
		if (app != null) {
			val appValue = if (app) "Yes" else "No"
			PvDraw.text(g, font, "Appraisal:", lx, ly, PvDraw.COLOR_MUTED)
			PvDraw.text(g, font, appValue, lx + lw - font.width(appValue), ly, if (app) ENABLED else PvDraw.COLOR_MUTED)
			ly += font.lineHeight + 2
		}
		ly += 4

		PvDraw.text(g, font, "Cheapest missing", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 4

		listX = lx
		listY = ly
		listW = lw
		listH = maxOf(0, bottom - ly)

		if (cheapest.isEmpty()) {
			listMaxScroll = 0
			PvDraw.text(g, font, if (complete) "Complete" else "None", lx, ly, PvDraw.COLOR_MUTED)
			return null
		}

		val rowH = maxOf(LIST_ICON, font.lineHeight) + LIST_ROW_GAP
		val contentH = cheapest.size * rowH
		listMaxScroll = maxOf(0, contentH - listH)
		listScroll = minOf(listScroll, listMaxScroll)

		var hoveredKey: String? = null
		g.enableScissor(lx, listY, lx + lw + 3, listY + listH)
		for ((i, row) in cheapest.withIndex()) {
			val by = listY + i * rowH - listScroll
			if (by + rowH < listY || by > listY + listH) {
				continue
			}
			val hovered = mx >= lx && mx < lx + lw && my >= by && my < by + rowH - LIST_ROW_GAP && my >= listY && my < listY + listH

			val icon = missingIcon(row.donationId, row.iconId)
			val iconY = by + maxOf(0, (maxOf(LIST_ICON, font.lineHeight) - LIST_ICON) / 2)
			drawSkyblockIcon(g, row.iconId, icon, lx, iconY, LIST_ICON)

			val price = if (row.price > 0) FormatUtil.shortCoins(row.price) else "-"
			val priceW = font.width(price)
			val textX = lx + LIST_ICON + 3
			val nameMax = maxOf(8, lw - LIST_ICON - 3 - priceW - 4)
			val name = trimToWidth(font, row.name, nameMax)
			val textY = by + maxOf(0, (LIST_ICON - font.lineHeight) / 2)
			PvDraw.text(g, font, name, textX, textY, row.nameColor)
			PvDraw.text(g, font, price, lx + lw - priceW, textY, PvDraw.COLOR_GOLD)
			if (hovered) {
				hoveredKey = row.donationId
				hoverCheapName = row.name
			}
		}
		g.disableScissor()
		return hoveredKey
	}

	private fun ensureCache() {
		if (!cacheDirty) {
			return
		}
		if (state != LoadState.READY) {
			slots = listOf()
			iconByIndex.clear()
			missingIconById.clear()
			cheapest = listOf()
			costToMax = 0.0
			donatedValue = 0.0
			donatedCount = 0
			totalCount = 0
			cacheDirty = false
			return
		}
		rebuildCache()
		cacheDirty = HypixelItemsCache.allItems().isEmpty()
		if (cacheDirty) {
			HypixelItemsCache.requestIfEmpty()
		}
	}

	private fun rebuildCache() {
		MuseumCatalog.ensureBuilt()
		val built = buildSlots()
		slots = built
		missingView = built.filter { it.missing }
		NeuRepoCache.prefetch(built.filter { it.missing && !isSpecialKey(it.donationId) }.map { it.donationId })
		iconByIndex.clear()
		missingIconById.clear()
		viewBySlot.clear()
		weakSlots.clear()
		weakMissingIds.clear()
		hoverKey = ""
		hoverTip = listOf()
		totalCount = built.size
		var donated = 0
		var cost = 0.0
		var owned = 0.0
		val cheap = ArrayList<CheapRow>()

		for (slot in built) {
			if (slot.missing) {
				val price = MuseumCatalog.marketPrice(slot.donationId)
				cost += price
				val iconId = MuseumCatalog.iconId(slot.donationId)
				val color = SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(iconId))
				cheap.add(CheapRow(slot.donationId, iconId, MuseumCatalog.displayName(slot.donationId), color, price))
			} else {
				donated++
				val valueId = MuseumCatalog.valueId(slot.donationId, slot.coveredBy, slot.stack)
				if (valueId != null) {
					owned += MuseumCatalog.marketPrice(valueId)
				}
			}
		}
		cheap.sortWith(
			compareBy<CheapRow> { if (it.price > 0) 0 else 1 }
				.thenBy { it.price }
				.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
		)
		donatedCount = donated
		costToMax = cost
		donatedValue = owned
		cheapest = cheap.toList()

		val warm = ArrayList<String>(built.size + cheap.size)
		for (slot in built) {
			val id = resolveIconId(slot)
			if (id.isNotBlank()) {
				warm.add(id)
			}
		}
		for (row in cheap) {
			warm.add(row.iconId)
		}
		SkyBlockItemFactory.prefetchIds(warm)
	}

	private fun missingIcon(donationId: String, iconId: String): ItemStack {
		missingIconById[donationId]?.let { return it }
		var icon = SkyBlockItemFactory.iconStack(iconId)
		if (icon.isEmpty) {
			icon = MISSING_ICON
		}
		missingIconById[donationId] = icon
		if (isWeakIcon(icon) && !SkyBlockIconRenderer.hasKnownIcon(iconId)) {
			weakMissingIds.add(donationId)
		}
		return icon
	}

	private fun slotView(slot: Slot): SlotView =
		viewBySlot.getOrPut(slot) {
			val iconId = if (slot.missing) MuseumCatalog.iconId(slot.donationId) else resolveIconId(slot)
			SlotView(iconId, SkyBlockItemFactory.tierArgb(slotTier(slot)))
		}

	private fun retryWeakIcons() {
		val now = System.currentTimeMillis()
		if (now < nextWeakRetryMs) {
			return
		}
		nextWeakRetryMs = now + 1000L
		for (slot in weakSlots) {
			iconByIndex.remove(slot)
		}
		for (id in weakMissingIds) {
			missingIconById.remove(id)
		}
		weakSlots.clear()
		weakMissingIds.clear()
	}

	/** Lightweight icon only - never builds full lore stacks for the grid unless needed for skulls. */
	private fun iconAt(slot: Slot): ItemStack {
		iconByIndex[slot]?.let { return it }
		if (slot.missing) {
			iconByIndex[slot] = MISSING_ICON
			return MISSING_ICON
		}
		val iconId = resolveIconId(slot)
		val stack = slot.stack
		var icon: ItemStack = if (!slot.coveredByHigherTier() && stack != null
			&& (!stack.skullValue.isNullOrBlank() || stack.dyeColor != null)
		) {
			SkyBlockItemFactory.toStack(toUiSlot(slot))
		} else if (iconId.isNotBlank() && !isSpecialKey(iconId)) {
			var found = SkyBlockItemFactory.iconStack(iconId)
			if (isWeakIcon(found)) {
				val fallback = MuseumCatalog.iconId(slot.donationId)
				if (fallback.isNotBlank() && !fallback.equals(iconId, ignoreCase = true) && !isSpecialKey(fallback)) {
					val alt = SkyBlockItemFactory.iconStack(fallback)
					if (!isWeakIcon(alt)) {
						found = alt
					}
				}
			}
			found
		} else {
			ItemStack.EMPTY
		}
		if (icon.isEmpty) {
			icon = ItemStack(Items.BARRIER)
		}
		iconByIndex[slot] = icon
		// Don't freeze paper before pack/NEU textures arrive.
		if (isWeakIcon(icon) && !SkyBlockIconRenderer.hasKnownIcon(iconId)) {
			weakSlots.add(slot)
		}
		return icon
	}

	private fun slotByKey(donationId: String?): Slot? {
		if (donationId == null) {
			return null
		}
		return slots.firstOrNull { it.donationId == donationId }
	}

	private fun tooltipFor(slot: Slot): List<Component> {
		val ui = toUiSlot(slot)
		val rendered = SkyBlockItemFactory.toStack(ui)
		val tip = ArrayList(SkyBlockItemFactory.tooltipLines(ui, rendered, false))
		if (slot.coveredByHigherTier()) {
			tip.add(Component.empty())
			tip.add(SkyBlockItemFactory.legacyLine("§7Donated via §a" + MuseumCatalog.displayName(slot.coveredBy)))
		}
		if (!slot.missing && slot.donatedTimeMs > 0L) {
			tip.add(Component.empty())
			val date = FormatUtil.prettyDate(slot.donatedTimeMs)
			val age = FormatUtil.ago(slot.donatedTimeMs)
			if (date.isNotBlank() && age.isNotBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f$date §8($age)"))
			} else if (age.isNotBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f$age"))
			} else if (date.isNotBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f$date"))
			}
		}
		if (!slot.missing) {
			val valueId = MuseumCatalog.valueId(slot.donationId, slot.coveredBy, slot.stack)
			val value = if (valueId == null) 0.0 else MuseumCatalog.marketPrice(valueId)
			if (value > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Value: §6" + FormatUtil.shortCoins(value)))
			}
		}
		if (slot.missing) {
			tip.add(Component.empty())
			tip.add(SkyBlockItemFactory.legacyLine("§7Missing donation"))
			val price = MuseumCatalog.marketPrice(slot.donationId)
			if (price > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Price: §6" + FormatUtil.shortCoins(price)))
			}
			val xp = MuseumCatalog.donationXp(slot.donationId)
			if (xp > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Museum XP: §e" + FormatUtil.commas(xp.toLong())))
			}
			addRecipeLines(tip, slot.donationId)
		}
		return tip
	}

	private fun buildSlots(): List<Slot> {
		val donated = MuseumCatalog.resolveDonations(InventoryDecoder.parseMuseumById(museumMember))
		if (sort.isAll()) {
			val out = ArrayList<Slot>()
			for (category in MuseumSort.categories()) {
				out.addAll(buildSlotsForCategory(category, donated))
			}
			return missingLast(out)
		}
		return missingLast(buildSlotsForCategory(sort, donated))
	}

	private fun buildSlotsForCategory(category: MuseumSort, donated: Map<String, MuseumCatalog.ResolvedDonation>): List<Slot> {
		val out = ArrayList<Slot>()
		if (category == MuseumSort.SPECIAL) {
			for ((key, res) in donated) {
				if (key.uppercase(Locale.ROOT).startsWith("SPECIAL:")) {
					out.add(donatedSlot(key, res.stack, null))
				} else if (res.direct() && !MuseumCatalog.isCataloged(key)) {
					out.add(donatedSlot(key, res.stack, null))
				}
			}
		}
		for (id in MuseumCatalog.donationIds(category)) {
			val res = donated[id]
			if (res == null) {
				out.add(Slot(id, null, true, null, 0L))
			} else {
				out.add(donatedSlot(id, res.stack, res.coveredBy))
			}
		}
		return out
	}

	private fun donatedSlot(id: String, stack: InventoryDecoder.Stack?, coveredBy: String?): Slot =
		Slot(id, stack, false, coveredBy, donatedTimeMs(id))

	private fun donatedTimeMs(donationId: String?): Long {
		if (donationId.isNullOrBlank()) {
			return 0L
		}
		if (isSpecialKey(donationId)) {
			return readDonatedTime(specialEntry(donationId))
		}
		if (!museumMember.has("items") || !museumMember.get("items").isJsonObject) {
			return 0L
		}
		val items = museumMember.getAsJsonObject("items")
		var entry: JsonObject? = null
		if (items.has(donationId) && items.get(donationId).isJsonObject) {
			entry = items.getAsJsonObject(donationId)
		} else {
			val upper = donationId.uppercase(Locale.ROOT)
			for ((key, value) in items.entrySet()) {
				if (key != null && key.uppercase(Locale.ROOT) == upper && value.isJsonObject) {
					entry = value.asJsonObject
					break
				}
			}
		}
		return readDonatedTime(entry)
	}

	private fun specialEntry(key: String): JsonObject? {
		if (!museumMember.has("special") || !museumMember.get("special").isJsonArray) {
			return null
		}
		val parts = key.split(":")
		if (parts.size < 2) {
			return null
		}
		val index = parts[1].toIntOrNull() ?: return null
		val special = museumMember.getAsJsonArray("special")
		if (index < 0 || index >= special.size() || !special.get(index).isJsonObject) {
			return null
		}
		return special.get(index).asJsonObject
	}

	companion object {
		private const val GAP = 8
		private const val PAD = 6
		private const val SLOT = 22
		private const val ICON = 20
		private const val SLOT_GAP = 2
		private const val LIST_ICON = 19
		private const val LIST_ROW_GAP = 3
		private const val ITEM_SLOT_BG = 0x1CFFFFFF
		private const val ITEM_SLOT_BORDER = 0x00FFFFFF
		private const val ENABLED = 0xFF55FF55.toInt()
		private const val DISABLED = 0xFF555555.toInt()
		private const val SCROLL_STEP = (SLOT + SLOT_GAP) * 3
		private const val LIST_SCROLL_STEP = LIST_ICON + LIST_ROW_GAP + 2
		private val MISSING_ICON = ItemStack(CI.GRAY_DYE)
		private val TIER_ORDER = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE")

		private fun drawSkyblockIcon(g: GuiGraphicsExtractor, iconId: String, fallback: ItemStack?, x: Int, y: Int, size: Int) {
			SkyBlockIconRenderer.draw(g, fallback ?: ItemStack.EMPTY, iconId, x, y, size)
		}

		private fun resolveIconId(slot: Slot): String {
			val stack = slot.stack
			if (!slot.missing && !slot.coveredByHigherTier() && stack != null) {
				val id = stack.id
				if (id.isNotBlank() && !isSpecialKey(id)) {
					return id
				}
			}
			val catalog = MuseumCatalog.iconId(slot.donationId)
			if (catalog.isNotBlank() && !isSpecialKey(catalog)) {
				return catalog
			}
			if (!slot.missing && stack != null) {
				return stack.id
			}
			return slot.donationId
		}

		private fun isSpecialKey(id: String?): Boolean = id != null && id.uppercase(Locale.ROOT).startsWith("SPECIAL:")

		private fun slotTier(slot: Slot): String {
			val id = resolveIconId(slot)
			val tier = SkyBlockItemFactory.resolveTier(id)
			if (tier.isNotBlank()) {
				return bumpTier(tier, rarityUpgrades(slot))
			}
			if (!slot.donationId.equals(id, ignoreCase = true)) {
				return bumpTier(SkyBlockItemFactory.resolveTier(slot.donationId), rarityUpgrades(slot))
			}
			return ""
		}

		private fun rarityUpgrades(slot: Slot): Int {
			val stack = slot.stack
			if (slot.missing || stack == null) {
				return 0
			}
			return NbtAttrs.intValue(stack.extraAttributes, "rarity_upgrades", 0)
		}

		private fun bumpTier(tier: String?, upgrades: Int): String {
			if (tier.isNullOrBlank() || upgrades <= 0) {
				return tier ?: ""
			}
			val key = SkyBlockItemFactory.normalizeTier(tier)
			val idx = TIER_ORDER.indexOf(key)
			if (idx < 0) {
				return key
			}
			return TIER_ORDER[minOf(TIER_ORDER.size - 1, idx + upgrades)]
		}

		private fun raritySlotBackground(rarityArgb: Int, missing: Boolean, hovered: Boolean): Int {
			val r = (rarityArgb ushr 16) and 0xFF
			val green = (rarityArgb ushr 8) and 0xFF
			val b = rarityArgb and 0xFF
			if (r >= 240 && green >= 240 && b >= 240) {
				return if (hovered) PvDraw.CELL else ITEM_SLOT_BG
			}
			val tint = if (hovered) 0.40f else if (missing) 0.20f else 0.28f
			val outR = Math.round(16 + r * tint).coerceIn(0, 255)
			val outG = Math.round(16 + green * tint).coerceIn(0, 255)
			val outB = Math.round(16 + b * tint).coerceIn(0, 255)
			return 0xFF000000.toInt() or (outR shl 16) or (outG shl 8) or outB
		}

		private fun isWeakIcon(icon: ItemStack?): Boolean =
			icon == null || icon.isEmpty || icon.`is`(Items.PAPER) || icon.`is`(Items.BARRIER)

		private fun addRecipeLines(tip: MutableList<Component>, donationId: String?) {
			val item = if (donationId == null || isSpecialKey(donationId)) null else NeuRepoCache.get(donationId)
			if (item == null) {
				return
			}
			val ingredients = MinionCosts.ingredients(MinionCosts.recipe(item), null)
			tip.add(Component.empty())
			if (ingredients.isEmpty()) {
				tip.add(SkyBlockItemFactory.legacyLine("§8No crafting recipe"))
				return
			}
			tip.add(SkyBlockItemFactory.legacyLine("§7Recipe:"))
			for ((id, amount) in ingredients) {
				tip.add(SkyBlockItemFactory.legacyLine(
					"§8" + FormatUtil.commas(amount.toLong()) + "x §f" + SkyBlockItemFactory.plainDisplayName(id)
				))
			}
		}

		private fun toUiSlot(slot: Slot): InventorySnapshot.Slot {
			val s = slot.stack
			if (!slot.missing && !slot.coveredByHigherTier() && s != null) {
				return InventorySnapshot.Slot(
					s.id, maxOf(1, s.count), s.lore, s.displayName, s.dyeColor, s.skullValue, s.skullSignature
				)
			}
			val id = MuseumCatalog.iconId(slot.donationId)
			return InventorySnapshot.Slot(id, 1, listOf(), null, null, null, null)
		}

		private fun missingLast(slots: List<Slot>): List<Slot> = slots.filter { !it.missing } + slots.filter { it.missing }

		private fun readAppraisal(museumMember: JsonObject?): Boolean? {
			if (museumMember == null || !museumMember.has("appraisal") || !museumMember.get("appraisal").isJsonPrimitive) {
				return null
			}
			return try {
				museumMember.get("appraisal").asBoolean
			} catch (_: Exception) {
				null
			}
		}

		private fun readDonatedTime(entry: JsonObject?): Long {
			if (entry == null || !entry.has("donated_time") || !entry.get("donated_time").isJsonPrimitive) {
				return 0L
			}
			return try {
				maxOf(0L, entry.get("donated_time").asLong)
			} catch (_: Exception) {
				0L
			}
		}

		private fun trimToWidth(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val ellipsisW = font.width(ellipsis)
			if (ellipsisW >= maxW) {
				return ""
			}
			var end = text.length
			while (end > 0 && font.width(text.substring(0, end)) + ellipsisW > maxW) {
				end--
			}
			return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
		}
	}
}

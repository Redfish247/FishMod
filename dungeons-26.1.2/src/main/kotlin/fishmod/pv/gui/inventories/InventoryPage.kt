/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.mojang.blaze3d.platform.InputConstants
import fishmod.compat.CI
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.MissingAccessories
import fishmod.pv.gui.PvAhs
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockStats
import fishmod.pv.gui.nav.InventoryPane
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.ItemWorth
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelItemsCache
import fishmod.pv.price.ItemPricer
import java.util.EnumMap
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Left grid renderer + page controls for Inventories. Right pane buttons stay in ProfileViewerScreen. */
class InventoryPage {
	private var snapshot: InventorySnapshot = InventorySnapshot.empty()
	private var pane = InventoryPane.INVENTORY
	/** When on Sacks: `null` = sack menu; otherwise index into [InventorySnapshot.sacks]. */
	private var openSackIndex: Int? = null
	private val pageIndex = EnumMap<InventoryPane, Int>(InventoryPane::class.java)
	/** Cards per loadout page, recomputed from the preview area each frame. */
	private var loadoutsPerPage = 2
	private var searchQuery = ""
	private val searchPanes: MutableSet<InventoryPane> = EnumSet.noneOf(InventoryPane::class.java)
	private val searchPages = EnumMap<InventoryPane, MutableSet<Int>>(InventoryPane::class.java)
	private val hits = ArrayList<SlotHit>()
	private val pageHits = ArrayList<RunnableHit>()
	/** Render/tooltip stacks keyed by slot identity; rebuilt when the snapshot changes. */
	private val stackCache = IdentityHashMap<InventorySnapshot.Slot, ItemStack>()
	private var hoveredSlot: InventorySnapshot.Slot? = null
	private var hoveredStack: ItemStack = ItemStack.EMPTY
	private var gemScroll = 0
	private var gemMaxScroll = 0
	private var gemHoverTip: List<Component>? = null
	private var gemHoverX = 0
	private var gemHoverY = 0

	/** Accessory bag panel flip: bag face ↔ unlocked powers, toggled by the Powers button. */
	private var accessoryPowersFace = false
	private var accessoryFlipTarget = false
	private var accessoryFlipStartMs = 0L
	private var accessoryFlipX = 0
	private var accessoryFlipY = 0
	private var accessoryFlipW = 0
	private var accessoryFlipH = 0
	private var accessoryPowersScroll = 0
	private var accessoryPowersMaxScroll = 0
	private var accessoryTuningsX = 0
	private var accessoryTuningsY = 0
	private var accessoryTuningsW = 0
	private var accessoryTuningsH = 0
	private var accessoryTuningsHover = false
	/** Accessory bag panel swapped to the missing accessories list via the top-right toggle. */
	private var accessoryMissingView = false
	private var missingResult: MissingAccessories.Result? = null
	private var missingHoverName: String? = null
	private var missingPricedAtCompute = false
	private var missingScroll = 0
	private var missingMaxScroll = 0
	private var missingHoverTip: List<Component>? = null
	private var loadoutHoverTip: List<Component>? = null
	private var sackValueHoverTip: List<Component>? = null
	/** Sack menu icons keyed by page so the stack cache doesn't grow every frame. */
	private val sackMenuSlots = IdentityHashMap<InventorySnapshot.Page, InventorySnapshot.Slot>()
	private val valueOverlay = ItemValueOverlay()
	private var sackValuesCache: List<SackValue>? = null
	private var sackValuesAtMs = 0L

	fun apply(snapshot: InventorySnapshot?) {
		this.snapshot = snapshot ?: InventorySnapshot.empty()
		sackValuesCache = null
		pageIndex.clear()
		openSackIndex = null
		gemScroll = 0
		stackCache.clear()
		sackMenuSlots.clear()
		valueOverlay.close()
		resetAccessoryFlip()
		missingResult = null
		if (!pane.visibleOn(this.snapshot)) {
			pane = InventoryPane.INVENTORY
		}
		rebuildSearchIndex()
		SkyBlockItemFactory.prefetch(this.snapshot)
	}

	fun setPane(pane: InventoryPane?) {
		if (pane != null) {
			if (this.pane != pane) {
				resetAccessoryFlip()
				valueOverlay.close()
			}
			this.pane = pane
			openSackIndex = null
		}
	}

	private fun resetAccessoryFlip() {
		accessoryPowersFace = false
		accessoryFlipTarget = false
		accessoryFlipStartMs = 0L
		accessoryPowersScroll = 0
		accessoryPowersMaxScroll = 0
		accessoryFlipW = 0
		accessoryFlipH = 0
		accessoryTuningsW = 0
		accessoryTuningsH = 0
		accessoryTuningsHover = false
		accessoryMissingView = false
		missingScroll = 0
		missingMaxScroll = 0
		missingHoverTip = null
	}

	fun pane(): InventoryPane = pane

	fun visiblePanes(): List<InventoryPane> =
		InventoryPane.entries.filter { !HIDDEN_PANES.contains(it) && it.visibleOn(snapshot) }

	fun setSearchQuery(query: String?) {
		val next = query?.trim()?.lowercase(Locale.ROOT) ?: ""
		if (next == searchQuery) {
			return
		}
		searchQuery = next
		rebuildSearchIndex()
	}

	/** True when this pane contains at least one search match. */
	fun searchHighlightsPane(pane: InventoryPane?): Boolean = pane != null && searchPanes.contains(pane)

	private fun searchMatches(pane: InventoryPane): Set<Int> = searchPages[pane] ?: setOf()

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int
	) {
		hits.clear()
		pageHits.clear()
		hoveredSlot = null
		hoveredStack = ItemStack.EMPTY
		missingHoverName = null
		gemHoverTip = null
		accessoryTuningsHover = false
		missingHoverTip = null
		loadoutHoverTip = null
		sackValueHoverTip = null

		if (pane == InventoryPane.ACCESSORY_BAG) {
			renderAccessoryBagPanel(g, font, x, y, w, h, mouseX, mouseY)
			return
		}

		PvDraw.innerPanel(g, x, y, w, h)

		if (pane == InventoryPane.SACKS) {
			renderSacks(g, font, x, y, w, h, mouseX, mouseY)
			return
		}

		val loadouts = pane == InventoryPane.LOADOUTS
		val pages = if (loadouts) listOf() else pagesFor(pane)
		val loadoutList = snapshot.loadouts
		val headerY = y + 6
		if (loadouts) {
			val gridH = y + h - (PAGE_BTN + 10) - (headerY + font.lineHeight + 4 + PREVIEW_TOP_PAD)
			val perPage = loadoutGrid(w - 16, gridH, font)[2]
			if (perPage != loadoutsPerPage) {
				loadoutsPerPage = perPage
				rebuildSearchIndex()
			}
		}
		val pageCount = if (loadouts) loadoutPageCount() else maxOf(1, pages.size)
		val page = clampPage(pane, pageCount)
		val multi = pageCount > 1

		val title = titleFor(loadouts, pages, page)
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT)

		val unavailable = if (loadouts) null else unavailableReason(pane)
		if (unavailable != null) {
			PvDraw.textCentered(g, font, unavailable, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}

		val metaTop = headerY + font.lineHeight + 4
		val previewTop = metaTop + PREVIEW_TOP_PAD
		val gridBottom = y + h - (if (multi) PAGE_BTN + 10 else 6)
		val previewH = maxOf(SLOT, gridBottom - previewTop)
		val previewX = x + 8
		val previewW = w - 16

		if (loadouts) {
			drawLoadoutsPage(g, font, previewX, previewTop, previewW, previewH, loadoutList, page, mouseX, mouseY)
		} else if (pane == InventoryPane.INVENTORY) {
			val current = if (pages.isEmpty()) InventorySnapshot.emptyPage("Inventory", 9) else pages[minOf(page, pages.size - 1)]
			drawPlayerInventory(g, font, previewX, previewTop, previewW, previewH, current.slots, mouseX, mouseY)
		} else {
			val current = if (pages.isEmpty()) {
				InventorySnapshot.emptyPage(pane.label().string, 9)
			} else {
				pages[minOf(page, pages.size - 1)]
			}
			val markers = pane == InventoryPane.WARDROBE || pane == InventoryPane.EQUIPMENT_WARDROBE
			drawGrid(
				g,
				font,
				previewX,
				previewTop,
				previewW,
				previewH,
				current.columns,
				current.slots,
				markers,
				if (markers) current.equippedColumn else -1,
				mouseX,
				mouseY
			)
		}

		if (multi) {
			val matches = searchMatches(pane)
			drawPager(
				g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount, mouseX, mouseY,
				matches.any { it < page }, matches.any { it > page }, null
			)
		}
	}

	/** Why a pane has nothing real to show (so it isn't drawn as a false empty grid), or null. */
	private fun unavailableReason(pane: InventoryPane): String? = when (pane) {
		InventoryPane.PERSONAL_VAULT -> when {
			!snapshot.inventoryApi -> Component.translatable("fishmod.pv.inv.state.api_off").string
			!snapshot.personalVaultPresent -> Component.translatable("fishmod.pv.inv.state.vault_locked").string
			else -> null
		}
		InventoryPane.TIME_POCKET -> when {
			!snapshot.inventoryApi -> Component.translatable("fishmod.pv.inv.state.api_off").string
			!snapshot.timePocketPresent -> Component.translatable("fishmod.pv.inv.state.not_in_api").string
			else -> null
		}
		else -> null
	}

	/** Sack menu (NEU sack skulls) or a single opened sack’s contents. */
	private fun renderSacks(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
		val sackPages = snapshot.sacks
		val openIndex = openSackIndex
		val inSack = openIndex != null && openIndex >= 0 && openIndex < sackPages.size

		val headerY = y + 6
		val title = if (inSack && openIndex != null) sackPages[openIndex].title else "Sacks"
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT)

		val previewTop = headerY + font.lineHeight + 4 + PREVIEW_TOP_PAD
		val footer = inSack || sackMenuPageCount(sackPages) > 1
		val gridBottom = y + h - (if (footer) PAGE_BTN + 10 else 6)
		val previewH = maxOf(SLOT, gridBottom - previewTop)
		val previewX = x + 8
		val previewW = w - 16

		if (inSack && openIndex != null) {
			val current = sackPages[openIndex]
			if ("Gemstone".equals(current.title, ignoreCase = true)) {
				drawGemstoneMatrix(g, font, previewX, previewTop, previewW, previewH, current.slots, mouseX, mouseY)
			} else {
				drawGrid(
					g, font, previewX, previewTop, previewW, previewH,
					current.columns, current.slots, false, -1, mouseX, mouseY
				)
			}
			drawSackValueHeader(g, font, x + w - 8, headerY, current, sackValues()[openIndex], mouseX, mouseY)
			val backX = x + 8
			val backY = y + h - PAGE_BTN - 6
			drawPageButton(g, font, backX, backY, "<", mouseX, mouseY, true, false) { openSackIndex = null }
			PvDraw.textCentered(
				g, font, "Sacks",
				x + w / 2,
				backY + (PAGE_BTN - font.lineHeight) / 2,
				PvDraw.COLOR_MUTED
			)
			return
		}

		val menuPages = sackMenuPageCount(sackPages)
		val page = clampPage(pane, menuPages)
		drawSackMenu(g, font, previewX, previewTop, previewW, previewH, sackPages, page, mouseX, mouseY)
		if (menuPages > 1) {
			val perPage = sackMenuSlotsPerPage()
			val matches = searchMatches(pane)
			drawPager(
				g, font, x, y + h - PAGE_BTN - 6, w, page, menuPages, mouseX, mouseY,
				matches.any { it / perPage < page }, matches.any { it / perPage > page }, null
			)
		}
	}

	private fun sackMenuPageCount(sackPages: List<InventorySnapshot.Page>): Int {
		val total = maxOf(0, sackPages.size)
		val perPage = sackMenuSlotsPerPage()
		return maxOf(1, (total + perPage - 1) / perPage)
	}

	// Match drawGrid capacity for a typical preview (~6 rows × 9 cols).
	private fun sackMenuSlotsPerPage(): Int = 9 * 6

	private fun drawSackMenu(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		sackPages: List<InventorySnapshot.Page>,
		page: Int,
		mouseX: Int,
		mouseY: Int
	) {
		if (sackPages.isEmpty()) {
			val key = if (snapshot.inventoryApi) "fishmod.pv.inv.state.no_sacks" else "fishmod.pv.inv.state.api_off"
			PvDraw.textCentered(g, font, Component.translatable(key).string, x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}
		val cols = 9
		val perPage = sackMenuSlotsPerPage()
		val start = page * perPage
		val end = minOf(sackPages.size, start + perPage)
		val menuSlots = (start until end).map { sackMenuSlot(sackPages[it]) }
		drawGrid(g, font, x, y, w, h, cols, menuSlots, false, -1, mouseX, mouseY)

		// Click targets: open the corresponding sack (drawGrid only adds tooltip hits).
		var rows = maxOf(1, (menuSlots.size + cols - 1) / cols)
		val maxRows = maxOf(1, (h - GRID_PAD + SLOT_GAP) / (SLOT + SLOT_GAP))
		rows = minOf(rows, maxRows)
		val gridW = cols * SLOT + (cols - 1) * SLOT_GAP
		val gridH = rows * SLOT + (rows - 1) * SLOT_GAP
		val startX = x + maxOf(GRID_PAD, (w - gridW) / 2)
		val startY = y + maxOf(GRID_PAD, (h - gridH) / 2)
		val shown = minOf(menuSlots.size, cols * rows)
		for (i in 0 until shown) {
			val sackIndex = start + i
			val col = i % cols
			val row = i / cols
			val sx = startX + col * (SLOT + SLOT_GAP)
			val sy = startY + row * (SLOT + SLOT_GAP)
			if (searchMatches(pane).contains(sackIndex)) {
				g.outline(sx, sy, SLOT, SLOT, SEARCH_HIGHLIGHT)
			}
			pageHits.add(RunnableHit(sx, sy, SLOT, SLOT) { openSackIndex = sackIndex })
		}
	}

	private fun sackValues(): List<SackValue> {
		// Pricing every sack each frame is costly; refresh once a second so late prices still land.
		val now = System.currentTimeMillis()
		val cached = sackValuesCache
		if (cached != null && now - sackValuesAtMs <= 1000L) {
			return cached
		}
		val fresh = SackValue.compute(snapshot.sacks, { ItemPricer.materialPrice(it) }, { SkyBlockItemFactory.plainDisplayName(it) })
		sackValuesCache = fresh
		sackValuesAtMs = now
		return fresh
	}

	private fun drawSackValueHeader(
		g: GuiGraphicsExtractor,
		font: Font,
		right: Int,
		y: Int,
		page: InventorySnapshot.Page,
		value: SackValue?,
		mouseX: Int,
		mouseY: Int
	) {
		if (value == null || !value.known()) {
			return
		}
		val label = "Sack value: "
		val coins = FormatUtil.shortCoins(Math.round(value.total).toDouble())
		val w = font.width(label) + font.width(coins)
		val left = right - w
		val hover = mouseX >= left && mouseX < right && mouseY >= y - 2 && mouseY < y + font.lineHeight + 2
		PvDraw.text(g, font, label, left, y, if (hover) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, coins, left + font.width(label), y, PvDraw.COLOR_GOLD)
		if (hover) {
			sackValueHoverTip = ItemTooltipEnricher.sackValueLines(value, true)
		}
		val icon = sackMenuSlot(page)
		pageHits.add(RunnableHit(left, y - 2, w, font.lineHeight + 4) {
			valueOverlay.open(page.title + " Sack", cachedStack(icon), icon.id, value.breakdown(), "Sack value")
		})
	}

	private fun sackMenuSlot(page: InventorySnapshot.Page): InventorySnapshot.Slot =
		sackMenuSlots.getOrPut(page) { buildSackMenuSlot(page) }

	private fun buildSackMenuSlot(page: InventorySnapshot.Page?): InventorySnapshot.Slot {
		val title = if (page == null || page.title.isBlank()) "Sack" else page.title
		val itemId = NeuRepoCache.sackItemId(title)
		if (!itemId.isNullOrBlank()) {
			return InventorySnapshot.Slot(itemId, 1, listOf(), title, null, null, null)
		}
		val icon = page?.slots?.firstOrNull { it != null && !it.isEmpty() }?.id ?: "CHEST"
		return InventorySnapshot.Slot(
			icon, 1, listOf(
				"§7Sack items that aren't in any",
				"§7known sack list."
			), "§f" + title, null, null, null
		)
	}

	/** Drawn after the right button panel so tips are never covered by it. */
	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (valueOverlay.isOpen()) {
			return
		}
		for (tip in listOf(gemHoverTip, missingHoverTip, loadoutHoverTip)) {
			if (!tip.isNullOrEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH)
				return
			}
		}
		if (accessoryTuningsHover) {
			val tip = accessoryTuningsTooltip()
			if (tip.isNotEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH)
				return
			}
		}
		val sackTip = sackValueHoverTip
		if (!sackTip.isNullOrEmpty()) {
			PvTooltip.drawComponents(g, font, sackTip, mouseX, mouseY, screenW, screenH)
			return
		}
		val slot = hoveredSlot
		if (slot != null && !hoveredStack.isEmpty) {
			val sackIndex = sackMenuIndex(slot)
			val tip: List<Component>
			if (sackIndex >= 0) {
				val lines = ArrayList(SkyBlockItemFactory.tooltipLines(slot, hoveredStack, false))
				ItemTooltipEnricher.appendSackValue(lines, sackValues()[sackIndex])
				tip = lines
			} else {
				tip = SkyBlockItemFactory.tooltipLines(slot, hoveredStack)
			}
			PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH)
		}
	}

	private fun sackMenuIndex(slot: InventorySnapshot.Slot): Int {
		if (pane != InventoryPane.SACKS || openSackIndex != null) {
			return -1
		}
		val pages = snapshot.sacks
		for (i in pages.indices) {
			if (sackMenuSlots[pages[i]] === slot) {
				return i
			}
		}
		return -1
	}

	fun renderOverlay(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		valueOverlay.render(g, font, screenW, screenH, mouseX, mouseY)
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (valueOverlay.isOpen()) {
			return valueOverlay.mouseClicked(mx, my)
		}
		val missingName = missingHoverName
		if (missingName != null && PvAhs.search(missingName)) {
			return true
		}
		for (hit in pageHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run()
				return true
			}
		}
		for (hit in hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				openItemValue(hit.slot, hit.stack)
				return true
			}
		}
		return false
	}

	private fun toggleAccessoryPowers() {
		if (accessoryFlipStartMs != 0L || !canFlipAccessoryPowers()) {
			return
		}
		accessoryFlipTarget = !accessoryPowersFace
		accessoryFlipStartMs = System.currentTimeMillis()
	}

	private fun openItemValue(slot: InventorySnapshot.Slot?, stack: ItemStack) {
		if (slot == null || slot.isEmpty()) {
			return
		}
		val id = slot.id ?: return
		val worth = InventoryDecoder.Stack(
			id,
			slot.count,
			slot.extraAttributes ?: CompoundTag(),
			slot.lore ?: listOf(),
			slot.soulbound,
			slot.displayName,
			slot.dyeColor,
			slot.skullValue,
			slot.skullSignature
		)
		val breakdown = ItemWorth.breakdown(worth)
		val displayName = slot.displayName
		val title = if (!displayName.isNullOrBlank()) {
			displayName.replace(FORMAT_CODE, "").trim()
		} else {
			SkyBlockItemFactory.plainDisplayName(id)
		}
		valueOverlay.open(title, stack, id, breakdown)
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (valueOverlay.isOpen()) {
			return valueOverlay.mouseScrolled(scrollY)
		}
		val overFlipPanel = mouseX >= accessoryFlipX && mouseX < accessoryFlipX + accessoryFlipW &&
			mouseY >= accessoryFlipY && mouseY < accessoryFlipY + accessoryFlipH
		if (pane == InventoryPane.ACCESSORY_BAG && accessoryMissingView) {
			if (missingMaxScroll <= 0 || !overFlipPanel) {
				return false
			}
			val before = missingScroll
			missingScroll = maxOf(0, minOf(missingMaxScroll, missingScroll + (if (scrollY > 0) -MISSING_ROW else MISSING_ROW)))
			return missingScroll != before
		}
		if (pane == InventoryPane.ACCESSORY_BAG && showingAccessoryPowersFace()) {
			if (accessoryPowersMaxScroll <= 0 || !overFlipPanel) {
				return false
			}
			val before = accessoryPowersScroll
			accessoryPowersScroll = maxOf(
				0,
				minOf(accessoryPowersMaxScroll, accessoryPowersScroll + (if (scrollY > 0) -POWERS_ROW else POWERS_ROW))
			)
			return accessoryPowersScroll != before
		}
		if (pane == InventoryPane.SACKS && openSackIndex != null) {
			if (gemMaxScroll > 0 && isOpenGemstoneSack()) {
				val before = gemScroll
				if (scrollY > 0) {
					gemScroll = maxOf(0, gemScroll - (SLOT + SLOT_GAP))
				} else if (scrollY < 0) {
					gemScroll = minOf(gemMaxScroll, gemScroll + (SLOT + SLOT_GAP))
				}
				return gemScroll != before
			}
			return false
		}
		val size = when (pane) {
			InventoryPane.LOADOUTS -> loadoutPageCount()
			InventoryPane.SACKS -> sackMenuPageCount(snapshot.sacks)
			else -> maxOf(1, pagesFor(pane).size)
		}
		if (size <= 1) {
			return false
		}
		val page = clampPage(pane, size)
		if (scrollY > 0 && page > 0) {
			pageIndex[pane] = page - 1
			return true
		}
		if (scrollY < 0 && page < size - 1) {
			pageIndex[pane] = page + 1
			return true
		}
		return false
	}

	private fun titleFor(loadouts: Boolean, pages: List<InventorySnapshot.Page>, page: Int): String {
		if (loadouts) {
			return "Loadouts"
		}
		if (pages.isEmpty()) {
			return pane.label().string
		}
		return pages[minOf(page, pages.size - 1)].title
	}

	private fun tuningsLabel(color: Int): Component =
		Component.literal("Tunings").setStyle(
			Style.EMPTY
				.withColor(TextColor.fromRgb(color and 0xFFFFFF))
				.withUnderlined(true)
		)

	private fun drawAccessoryMeta(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		info: InventorySnapshot.AccessoryInfo?,
		mouseX: Int,
		mouseY: Int
	) {
		accessoryTuningsW = 0
		accessoryTuningsH = 0
		accessoryTuningsHover = false
		if (info == null) {
			return
		}

		val line1 = Component.empty()
		line1.append(PvDraw.styled("MP ", PvDraw.COLOR_MUTED, false))
		line1.append(PvDraw.styled(FormatUtil.commas(info.magicalPower.toLong()), 0xFF55FFFF.toInt(), true))
		line1.append(PvDraw.styled("  ·  Power ", PvDraw.COLOR_MUTED, false))
		line1.append(SkyBlockStats.powerName(info.selectedPower))
		if (info.bagUpgrades > 0) {
			line1.append(PvDraw.styled(" · Upgrades ", PvDraw.COLOR_MUTED, false))
			line1.append(PvDraw.styled(info.bagUpgrades.toString(), PvDraw.COLOR_GOLD, true))
		}
		PvDraw.text(g, font, trimComponent(font, line1, w), x, y)

		val tuningsY = y + font.lineHeight + 2
		var tunings = tuningsLabel(PvDraw.COLOR_MUTED)
		var tuningsW = font.width(tunings)
		val hover = mouseX >= x && mouseX < x + tuningsW && mouseY >= tuningsY && mouseY < tuningsY + font.lineHeight
		if (hover) {
			tunings = tuningsLabel(PvDraw.COLOR_ACCENT)
			tuningsW = font.width(tunings)
		}
		accessoryTuningsX = x
		accessoryTuningsY = tuningsY
		accessoryTuningsW = tuningsW
		accessoryTuningsH = font.lineHeight
		accessoryTuningsHover = hover
		PvDraw.text(g, font, tunings, x, tuningsY)
	}

	private fun accessoryTuningsTooltip(): List<Component> {
		val info: InventorySnapshot.AccessoryInfo? = snapshot.accessoryInfo
		val lines = ArrayList<Component>()
		lines.add(PvDraw.styled("Tunings", PvDraw.COLOR_TEXT, true))
		if (info == null || info.tunings.isEmpty()) {
			lines.add(PvDraw.styled("No saved tunings", PvDraw.COLOR_MUTED, false))
			return lines
		}
		for (template in info.tunings) {
			val line = Component.empty()
			line.append(PvDraw.styled("#" + template.slot + " ", PvDraw.COLOR_MUTED, false))
			line.append(SkyBlockStats.tuningStats(template.stats))
			lines.add(line)
		}
		return lines
	}

	/** Whole left inventory panel flips: border, content, and pager. */
	private fun renderAccessoryBagPanel(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val pages = pagesFor(InventoryPane.ACCESSORY_BAG)
		val pageCount = maxOf(1, pages.size)
		val page = clampPage(pane, pageCount)
		val multi = pageCount > 1 && !showingAccessoryPowersFace() && !accessoryMissingView

		accessoryFlipX = x
		accessoryFlipY = y
		accessoryFlipW = w
		accessoryFlipH = h

		var flipProgress = 0f
		var animating = accessoryFlipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1f, (System.currentTimeMillis() - accessoryFlipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1f) {
				accessoryPowersFace = accessoryFlipTarget
				accessoryFlipStartMs = 0L
				animating = false
				flipProgress = 0f
				if (!accessoryPowersFace) {
					accessoryPowersScroll = 0
				}
			}
		}
		val eased = if (animating) easeInOutCubic(flipProgress) else 0f
		val angle = eased * Math.PI.toFloat()
		val showPowers = if (animating) {
			if (cos(angle.toDouble()) < 0.0) accessoryFlipTarget else accessoryPowersFace
		} else {
			accessoryPowersFace
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

		val contentX = x + 8
		val contentY = y + 6
		val contentW = w - 16
		val contentBottom = y + h - (if (multi) PAGE_BTN + 10 else 6)
		val contentH = maxOf(SLOT, contentBottom - contentY)

		if (showPowers) {
			accessoryTuningsW = 0
			accessoryTuningsH = 0
			accessoryTuningsHover = false
			val backW = drawPanelButton(
				g, font, "Accessory Bag", contentX + contentW, contentY,
				mouseX, mouseY, !animating
			) { toggleAccessoryPowers() }
			drawAccessoryPowersFace(g, font, contentX, contentY, contentW, contentH, backW)
		} else if (accessoryMissingView) {
			val toggleW = drawAccessoryViewToggle(g, font, contentX + contentW, contentY, mouseX, mouseY, !animating)
			accessoryTuningsW = 0
			accessoryTuningsH = 0
			accessoryTuningsHover = false
			drawAccessoryMissingFace(g, font, contentX, contentY, contentW, contentH, toggleW, mouseX, mouseY)
		} else {
			var toggleW = drawAccessoryViewToggle(g, font, contentX + contentW, contentY, mouseX, mouseY, !animating)
			if (canFlipAccessoryPowers()) {
				toggleW += 4 + drawPanelButton(
					g, font, "Powers", contentX + contentW - toggleW - 4, contentY,
					mouseX, mouseY, !animating
				) { toggleAccessoryPowers() }
			}
			val title = titleFor(false, pages, page)
			PvDraw.text(g, font, trimToWidth(font, title, contentW - toggleW - 6), contentX, contentY, PvDraw.COLOR_TEXT)
			val metaTop = contentY + font.lineHeight + 4
			drawAccessoryMeta(g, font, contentX, metaTop, contentW, snapshot.accessoryInfo, mouseX, mouseY)
			val previewTop = metaTop + font.lineHeight + 2 + font.lineHeight + 6
			val previewH = maxOf(SLOT, contentY + contentH - previewTop)
			val current = if (pages.isEmpty()) {
				InventorySnapshot.emptyPage("Accessory Bag", 9)
			} else {
				pages[minOf(page, pages.size - 1)]
			}
			drawGrid(
				g, font, contentX, previewTop, contentW, previewH,
				current.columns, current.slots, false, -1, mouseX, mouseY, false
			)
			if (multi) {
				val matches = searchMatches(pane)
				drawPager(
					g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount,
					mouseX, mouseY, matches.any { it < page }, matches.any { it > page }, null
				)
			}
		}

		g.pose().popMatrix()
	}

	private fun drawAccessoryPowersFace(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, buttonW: Int) {
		val info: InventorySnapshot.AccessoryInfo? = snapshot.accessoryInfo
		val powers: List<String?> = info?.unlockedPowers ?: listOf()
		val selected = info?.selectedPower ?: ""

		val title = "Unlocked powers"
		PvDraw.text(g, font, trimToWidth(font, title, w - buttonW - 6), x, y, PvDraw.COLOR_MUTED)
		val count = FormatUtil.commas(powers.size.toLong())
		val countX = x + font.width(title) + 4
		if (countX + font.width(count) <= x + w - buttonW - 6) {
			PvDraw.text(g, font, count, countX, y, PvDraw.COLOR_ACCENT)
		}

		val listTop = y + font.lineHeight + 4
		val listBottom = y + h
		val listH = maxOf(0, listBottom - listTop)
		val colGap = 10
		val colW = maxOf(40, (w - colGap) / 2)
		val rows = (powers.size + 1) / 2
		val contentH = rows * POWERS_ROW
		accessoryPowersMaxScroll = maxOf(0, contentH - listH)
		accessoryPowersScroll = minOf(accessoryPowersScroll, accessoryPowersMaxScroll)

		g.enableScissor(x, listTop, x + w + 3, listTop + listH)
		for (i in powers.indices) {
			val col = i % 2
			val row = i / 2
			val px = x + col * (colW + colGap)
			val py = listTop + row * POWERS_ROW - accessoryPowersScroll
			if (py + font.lineHeight < listTop || py > listTop + listH) {
				continue
			}
			val power = powers[i]
			val active = selected.isNotBlank() && selected.equals(power ?: "", ignoreCase = true)
			val name = SkyBlockStats.powerName(power)
			PvDraw.text(g, font, trimComponent(font, name, colW - (if (active) font.width(" ●") + 2 else 0)), px, py)
			if (active) {
				PvDraw.textRight(g, font, "●", px + colW, py, 0xFF55FF55.toInt())
			}
		}
		g.disableScissor()

		if (powers.isEmpty()) {
			PvDraw.textCentered(g, font, "None unlocked", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
		}
	}

	/** Top-right "Missing" / "Accessory Bag" toggle. Returns its width so titles can avoid it. */
	private fun drawAccessoryViewToggle(
		g: GuiGraphicsExtractor, font: Font, rightX: Int, y: Int, mouseX: Int, mouseY: Int, clickable: Boolean
	): Int {
		val label = if (accessoryMissingView) "Accessory Bag" else "Missing"
		return drawPanelButton(g, font, label, rightX, y, mouseX, mouseY, clickable) { toggleAccessoryMissingView() }
	}

	private fun drawPanelButton(
		g: GuiGraphicsExtractor, font: Font, label: String, rightX: Int, y: Int, mouseX: Int, mouseY: Int,
		clickable: Boolean, action: Runnable
	): Int {
		val bw = font.width(label) + 10
		val bh = font.lineHeight + 4
		val bx = rightX - bw
		val by = y - 2
		val hovered = clickable && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh
		PvDraw.roundedFill(g, bx, by, bw, bh, 3, if (hovered) 0x50386CC8 else PvDraw.CELL)
		PvDraw.roundedRing(g, bx, by, bw, bh, 3, if (hovered) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(g, font, label, bx + bw / 2, by + 2, PvDraw.COLOR_TEXT)
		if (clickable) {
			pageHits.add(RunnableHit(bx, by, bw, bh, action))
		}
		return bw
	}

	private fun toggleAccessoryMissingView() {
		accessoryMissingView = !accessoryMissingView
		missingScroll = 0
		missingHoverTip = null
	}

	private fun missingAccessories(): MissingAccessories.Result? {
		val pricesReady = ItemPricer.isReady()
		val existing = missingResult
		if (existing != null && (missingPricedAtCompute || !pricesReady)) {
			return existing
		}
		if (HypixelItemsCache.allItems().isEmpty()) {
			HypixelItemsCache.requestIfEmpty()
			return null
		}
		val owned = ArrayList<String>()
		for (bagPage in pagesFor(InventoryPane.ACCESSORY_BAG)) {
			for (slot in bagPage.slots) {
				val id = slot?.id
				if (slot != null && !slot.isEmpty() && id != null) {
					owned.add(id)
				}
			}
		}
		val result = MissingAccessories.compute(owned)
		missingResult = result
		missingPricedAtCompute = pricesReady
		SkyBlockItemFactory.prefetchIds(result.entries.map { it.id })
		return result
	}

	private fun drawAccessoryMissingFace(
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, toggleW: Int, mouseX: Int, mouseY: Int
	) {
		PvDraw.text(g, font, trimToWidth(font, "Missing Accessories", w - toggleW - 6), x, y, PvDraw.COLOR_TEXT)
		val result = missingAccessories()
		if (result == null) {
			PvDraw.textCentered(g, font, "Loading item data...", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}

		val metaY = y + font.lineHeight + 4
		val cost = Component.empty()
		cost.append(PvDraw.styled("Cost to max ", PvDraw.COLOR_MUTED, false))
		cost.append(PvDraw.styled(FormatUtil.shortCoins(result.costToMax), PvDraw.COLOR_GOLD, true))
		cost.append(PvDraw.styled("  ·  ", PvDraw.COLOR_MUTED, false))
		cost.append(PvDraw.styled("+" + FormatUtil.commas(result.mpToMax.toLong()) + " MP", 0xFF55FFFF.toInt(), true))
		PvDraw.text(g, font, trimComponent(font, cost, w), x, metaY)

		val countY = metaY + font.lineHeight + 2
		var count = FormatUtil.commas(result.entries.size.toLong()) + " missing"
		if (result.unpricedTop > 0) {
			count += "  ·  " + FormatUtil.commas(result.unpricedTop.toLong()) + " unpriced"
		}
		PvDraw.text(g, font, trimToWidth(font, count, w), x, countY, PvDraw.COLOR_MUTED)

		val listTop = countY + font.lineHeight + 6
		val listH = maxOf(0, y + h - listTop)
		val entries = result.entries
		missingMaxScroll = maxOf(0, entries.size * MISSING_ROW - listH)
		missingScroll = minOf(missingScroll, missingMaxScroll)
		if (entries.isEmpty()) {
			PvDraw.textCentered(g, font, "Nothing missing", x + w / 2, listTop + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}

		val overList = mouseX >= x && mouseX < x + w && mouseY >= listTop && mouseY < listTop + listH
		g.enableScissor(x, listTop, x + w + 3, listTop + listH)
		for ((i, entry) in entries.withIndex()) {
			val rowY = listTop + i * MISSING_ROW - missingScroll
			if (rowY + MISSING_ROW <= listTop || rowY >= listTop + listH) {
				continue
			}
			val hovered = overList && mouseY >= rowY && mouseY < rowY + MISSING_ROW
			if (hovered) {
				PvDraw.fill(g, x, rowY, w, MISSING_ROW, PANEL_HOVER)
				missingHoverTip = missingTooltip(entry)
				missingHoverName = entry.name
			}
			val align = PvDraw.IconTextAlign.of(rowY, MISSING_ROW, 16, font.lineHeight)
			SkyBlockIconRenderer.draw(g, entry.id, x + 1, align.iconY)

			val perMp = when {
				entry.priced() -> FormatUtil.shortCoins(entry.coinsPerMp()) + "/MP"
				entry.soulbound -> "Soulbound"
				else -> "No price"
			}
			val perMpW = font.width(perMp)
			PvDraw.textRight(g, font, perMp, x + w - 2, align.textY, if (entry.priced()) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED)
			val total = if (entry.priced()) "(" + FormatUtil.shortCoins(entry.netCost()) + ")" else "+" + entry.mpGain + " MP"
			val totalW = font.width(total) + 6
			PvDraw.textRight(g, font, total, x + w - 2 - perMpW - 6, align.textY, PvDraw.COLOR_MUTED)
			val nameX = x + 20
			val nameMax = maxOf(20, x + w - 2 - perMpW - totalW - 6 - nameX)
			PvDraw.text(
				g, font, trimToWidth(font, entry.name, nameMax), nameX, align.textY,
				SkyBlockItemFactory.tierArgb(entry.tier)
			)
		}
		g.disableScissor()
	}

	private fun canFlipAccessoryPowers(): Boolean {
		val info: InventorySnapshot.AccessoryInfo? = snapshot.accessoryInfo
		return info != null && info.unlockedPowers.isNotEmpty()
	}

	private fun showingAccessoryPowersFace(): Boolean {
		if (accessoryFlipStartMs != 0L) {
			val progress = minOf(1f, (System.currentTimeMillis() - accessoryFlipStartMs) / FLIP_MS.toFloat())
			val angle = easeInOutCubic(progress) * Math.PI.toFloat()
			return if (cos(angle.toDouble()) < 0.0) accessoryFlipTarget else accessoryPowersFace
		}
		return accessoryPowersFace
	}

	private fun drawLoadoutsPage(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		all: List<InventorySnapshot.Loadout>,
		page: Int,
		mouseX: Int,
		mouseY: Int
	) {
		if (all.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}
		val grid = loadoutGrid(w, h, font)
		val cols = grid[0]
		val compact = grid[3] == 1
		val single = grid[3] == 2
		val perPage = loadoutsPerPage
		val start = page * perPage
		val end = minOf(all.size, start + perPage)
		val cardW = (w - LOADOUT_GAP * (cols - 1)) / cols
		val cardH = when {
			single -> loadoutRowHeight(font)
			compact -> loadoutCompactHeight(font)
			else -> loadoutCardHeight(font)
		}
		for (i in start until end) {
			val col = (i - start) % cols
			val row = (i - start) / cols
			val cx = x + col * (cardW + LOADOUT_GAP)
			val cy = y + row * (cardH + LOADOUT_ROW_GAP)
			if (row > 0 && col == 0) {
				PvDraw.fill(g, x, cy - LOADOUT_ROW_GAP / 2, w, 1, PvDraw.COLOR_DIVIDER)
			}
			if (single) {
				drawLoadoutRowCard(g, font, cx, cy, cardW, all[i], mouseX, mouseY)
			} else if (compact) {
				drawLoadoutCompactCard(g, font, cx, cy, cardW, all[i], mouseX, mouseY)
			} else {
				drawLoadoutCard(g, font, cx, cy, cardW, cardH, all[i], mouseX, mouseY)
			}
		}
	}

	private fun loadoutPageCount(): Int {
		val perPage = maxOf(1, loadoutsPerPage)
		return maxOf(1, (maxOf(1, snapshot.loadouts.size) + perPage - 1) / perPage)
	}

	/** Loadout name, shortened to fit beside the tree presets label. */
	private fun loadoutTitle(font: Font, loadout: InventorySnapshot.Loadout, w: Int, treesW: Int): Component {
		val nameW = maxOf(20, w - GRID_PAD * 2 - (if (treesW > 0) treesW + 6 else 0))
		val name = SkyBlockStats.loadoutName(loadout.name)
		if (font.width(name) > nameW) {
			return SkyBlockStats.loadoutName(trimToWidth(font, loadout.name, nameW - font.width("..")))
		}
		return name
	}

	private fun isEmptySlot(slot: InventorySnapshot.Slot?): Boolean = slot == null || slot.isEmpty()

	/** Two slot rows (equipment, armor) with pet and power beside; details move to the header hover. */
	private fun drawLoadoutCompactCard(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		loadout: InventorySnapshot.Loadout,
		mouseX: Int,
		mouseY: Int
	) {
		val startY = y + GRID_PAD
		val left = x + GRID_PAD
		val trees = loadoutTreesLabel(loadout)
		val treesW = if (trees.isEmpty()) 0 else font.width(trees)
		PvDraw.text(g, font, loadoutTitle(font, loadout, w, treesW), left, startY)
		if (treesW > 0) {
			PvDraw.text(g, font, trees, x + w - GRID_PAD - treesW, startY, PvDraw.COLOR_MUTED)
		}
		if (mouseX >= x && mouseX < x + w && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
			loadoutHoverTip = loadoutSummaryTooltip(loadout)
		}

		val eqY = startY + font.lineHeight + 4
		val armorY = eqY + SLOT + SLOT_GAP
		for (i in 0 until 4) {
			val sx = left + i * (SLOT + SLOT_GAP)
			drawSlot(g, font, sx, eqY, loadout.equipment.getOrNull(i), mouseX, mouseY)
			drawSlot(g, font, sx, armorY, loadout.armor.getOrNull(i), mouseX, mouseY)
		}

		val sideX = left + 4 * SLOT + 3 * SLOT_GAP + META_GAP
		val sideW = maxOf(0, x + w - GRID_PAD - sideX)
		drawSlot(g, font, sideX, eqY, loadout.pet, mouseX, mouseY)
		if (isEmptySlot(loadout.pet)) {
			g.item(ItemStack(Items.BONE), sideX + 1, eqY + 1)
		}
		if (sideW > 8) {
			PvDraw.text(
				g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone), sideW),
				sideX, armorY + (SLOT - font.lineHeight) / 2
			)
		}
	}

	/** Single-row loadout: name on top, equipment + armor + pet in one line so three fit on a page. */
	private fun drawLoadoutRowCard(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		loadout: InventorySnapshot.Loadout,
		mouseX: Int,
		mouseY: Int
	) {
		val startY = y + GRID_PAD
		val left = x + GRID_PAD
		val trees = loadoutTreesLabel(loadout)
		val treesW = if (trees.isEmpty()) 0 else font.width(trees)
		PvDraw.text(g, font, loadoutTitle(font, loadout, w, treesW), left, startY)
		if (treesW > 0) {
			PvDraw.text(g, font, trees, x + w - GRID_PAD - treesW, startY, PvDraw.COLOR_MUTED)
		}
		if (mouseX >= x && mouseX < x + w && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
			loadoutHoverTip = loadoutSummaryTooltip(loadout)
		}
		val rowY = startY + font.lineHeight + 4
		var sx = left
		for (i in 0 until 4) {
			drawSlot(g, font, sx, rowY, loadout.equipment.getOrNull(i), mouseX, mouseY)
			sx += SLOT + SLOT_GAP
		}
		sx += META_GAP
		for (i in 0 until 4) {
			drawSlot(g, font, sx, rowY, loadout.armor.getOrNull(i), mouseX, mouseY)
			sx += SLOT + SLOT_GAP
		}
		sx += META_GAP
		drawSlot(g, font, sx, rowY, loadout.pet, mouseX, mouseY)
		if (isEmptySlot(loadout.pet)) {
			g.item(ItemStack(Items.BONE), sx + 5, rowY + 5)
		}
		sx += SLOT + SLOT_GAP + META_GAP
		val sideW = maxOf(0, x + w - GRID_PAD - sx)
		if (sideW > 8) {
			PvDraw.text(
				g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone), sideW),
				sx, rowY + (SLOT - font.lineHeight) / 2
			)
		}
	}

	/** Equipment column left of armor (both top→bottom), metadata under/ beside. */
	private fun drawLoadoutCard(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		loadout: InventorySnapshot.Loadout,
		mouseX: Int,
		mouseY: Int
	) {
		val gearW = SLOT + COL_GAP + SLOT
		val startY = y + GRID_PAD
		val trees = loadoutTreesLabel(loadout)
		val treesW = if (trees.isEmpty()) 0 else font.width(trees)
		PvDraw.text(g, font, loadoutTitle(font, loadout, w, treesW), x + GRID_PAD, startY)
		if (treesW > 0) {
			val treesX = x + w - GRID_PAD - treesW
			PvDraw.text(g, font, trees, treesX, startY, PvDraw.COLOR_MUTED)
			if (mouseX >= treesX && mouseX < treesX + treesW && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
				loadoutHoverTip = loadoutTreesTooltip(loadout)
			}
		}
		val gearTop = startY + font.lineHeight + 4
		val eqX = x + GRID_PAD
		val armorX = eqX + SLOT + COL_GAP
		for (i in 0 until 4) {
			val sy = gearTop + i * (SLOT + SLOT_GAP)
			drawSlot(g, font, eqX, sy, loadout.equipment.getOrNull(i), mouseX, mouseY)
			drawSlot(g, font, armorX, sy, loadout.armor.getOrNull(i), mouseX, mouseY)
		}

		val metaX = eqX + gearW + META_GAP
		val metaW = maxOf(40, w - (metaX - x) - GRID_PAD)
		var my = gearTop
		PvDraw.text(g, font, "Power", metaX, my, PvDraw.COLOR_MUTED)
		my += font.lineHeight + 1
		PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone), metaW), metaX, my)
		my += font.lineHeight + 4

		PvDraw.text(g, font, "Tuning", metaX, my, PvDraw.COLOR_MUTED)
		my += font.lineHeight + 1
		val tune = SkyBlockStats.tuningStats(loadout.tuning)
		if (font.width(tune) <= metaW) {
			PvDraw.text(g, font, tune, metaX, my)
		} else {
			g.enableScissor(metaX, my, metaX + metaW, my + font.lineHeight + 1)
			PvDraw.text(g, font, tune, metaX, my)
			g.disableScissor()
		}
		my += font.lineHeight + 4

		PvDraw.text(g, font, "Pet", metaX, my, PvDraw.COLOR_MUTED)
		my += font.lineHeight + 2
		drawSlot(g, font, metaX, my, loadout.pet, mouseX, mouseY)
		if (isEmptySlot(loadout.pet)) {
			g.item(ItemStack(Items.BONE), metaX + 1, my + 1)
		}
		val petText = loadout.petLabel.ifBlank { "-" }
		PvDraw.text(
			g,
			font,
			trimToWidth(font, petText, maxOf(20, metaW - SLOT - 4)),
			metaX + SLOT + 4,
			my + (SLOT - font.lineHeight) / 2,
			loadoutPetColor(loadout)
		)
	}

	private fun drawPlayerInventory(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		slots: List<InventorySnapshot.Slot?>,
		mouseX: Int,
		mouseY: Int
	) {
		// Layout: [equipment×4][armor×4][inv×36], with legacy fallback without equipment.
		val hasEquipment = slots.size >= 44
		val armorOff = if (hasEquipment) 4 else 0
		val invOff = armorOff + 4
		val equipment = Array(4) { if (hasEquipment) slots.getOrNull(it) else null }
		val armor = Array(4) { slots.getOrNull(armorOff + it) }
		val hotbar = Array(9) { slots.getOrNull(invOff + it) }
		val main = Array(27) { slots.getOrNull(invOff + 9 + it) }

		val armorH = 4 * SLOT + 3 * SLOT_GAP
		val mainH = 3 * SLOT + 2 * SLOT_GAP
		// Hotbar sits under the main 3×9 grid (not under the taller armor column).
		val totalH = maxOf(armorH, mainH + HOTBAR_GAP + SLOT)
		val mainW = 9 * SLOT + 8 * SLOT_GAP
		val gearW = SLOT + COL_GAP + SLOT
		val totalW = gearW + ARMOR_GAP + mainW
		val startX = x + maxOf(0, (w - totalW) / 2)
		val startY = y + maxOf(0, (h - totalH) / 2)

		val eqX = startX
		val armorX = eqX + SLOT + COL_GAP
		val bodyX = startX + gearW + ARMOR_GAP
		val bodyY = startY
		val hotbarY = bodyY + mainH + HOTBAR_GAP

		for (i in 0 until 4) {
			val sy = startY + i * (SLOT + SLOT_GAP)
			drawSlot(g, font, eqX, sy, equipment[i], mouseX, mouseY)
			drawSlot(g, font, armorX, sy, armor[i], mouseX, mouseY)
		}
		for (i in 0 until 27) {
			val col = i % 9
			val row = i / 9
			drawSlot(g, font, bodyX + col * (SLOT + SLOT_GAP), bodyY + row * (SLOT + SLOT_GAP), main[i], mouseX, mouseY)
		}
		for (i in 0 until 9) {
			drawSlot(g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY)
		}

		if (slots.all { isEmptySlot(it) }) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + minOf(h / 2, totalH / 2) - font.lineHeight / 2, PvDraw.COLOR_MUTED)
		}
	}

	private fun drawPager(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		page: Int,
		total: Int,
		mouseX: Int,
		mouseY: Int,
		highlightPrev: Boolean,
		highlightNext: Boolean,
		centerLabel: String?
	) {
		val prevX = x + 8
		val nextX = x + w - 8 - PAGE_BTN
		drawPageButton(g, font, prevX, y, "<", mouseX, mouseY, page > 0, highlightPrev) { stepPage(total, -1) }
		drawPageButton(g, font, nextX, y, ">", mouseX, mouseY, page < total - 1, highlightNext) { stepPage(total, 1) }
		var label = if (!centerLabel.isNullOrBlank()) centerLabel else "${page + 1} / $total"
		val labelMax = maxOf(40, nextX - prevX - PAGE_BTN - 16)
		if (font.width(label) > labelMax) {
			label = trimToWidth(font, label, labelMax)
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED)
	}

	private fun drawPageButton(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		label: String,
		mouseX: Int,
		mouseY: Int,
		enabled: Boolean,
		searchHighlight: Boolean,
		action: Runnable
	) {
		val hovered = enabled && mouseX >= x && mouseX < x + PAGE_BTN && mouseY >= y && mouseY < y + PAGE_BTN
		val bg = when {
			!enabled -> PvDraw.CELL
			searchHighlight -> 0xFF3A2A10.toInt()
			hovered -> 0x50386CC8
			else -> PvDraw.CELL
		}
		val border = if (searchHighlight) SEARCH_HIGHLIGHT else if (hovered) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER
		PvDraw.roundedFill(g, x, y, PAGE_BTN, PAGE_BTN, 3, bg)
		PvDraw.roundedRing(g, x, y, PAGE_BTN, PAGE_BTN, 3, border, PvDraw.C_ALL)
		PvDraw.textCentered(
			g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2,
			if (enabled) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
		)
		if (enabled) {
			pageHits.add(RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action))
		}
	}

	private fun isOpenGemstoneSack(): Boolean {
		val index = openSackIndex ?: return false
		val sacks = snapshot.sacks
		if (index < 0 || index >= sacks.size) {
			return false
		}
		return "Gemstone".equals(sacks[index].title, ignoreCase = true)
	}

	/**
	 * One slot per gem family; hover tip lists Rough→Perfect counts (no scroll matrix).
	 */
	private fun drawGemstoneMatrix(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		slots: List<InventorySnapshot.Slot?>?,
		mouseX: Int,
		mouseY: Int
	) {
		gemMaxScroll = 0
		gemScroll = 0
		val counts = LinkedHashMap<String, IntArray>()
		val bestIcon = LinkedHashMap<String, InventorySnapshot.Slot>()
		for (slot in slots ?: listOf()) {
			if (slot == null) {
				continue
			}
			val id = slot.id ?: continue
			val upper = id.uppercase(Locale.ROOT)
			var gem: String? = null
			var tierIdx = -1
			for (t in GEM_TIERS.indices) {
				val prefix = GEM_TIERS[t] + "_"
				if (upper.startsWith(prefix) && upper.endsWith("_GEM")) {
					gem = upper.substring(prefix.length, upper.length - "_GEM".length)
					tierIdx = t
					break
				}
			}
			if (gem == null || tierIdx < 0) {
				continue
			}
			val row = counts.getOrPut(gem) { IntArray(GEM_TIERS.size) }
			row[tierIdx] = maxOf(0, slot.count)
			val prev = bestIcon[gem]
			if (prev == null || tierIdx > bestTierIndex(prev.id)) {
				bestIcon[gem] = slot
			}
		}
		if (counts.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}

		val cols = minOf(6, maxOf(3, w / (SLOT + SLOT_GAP)))
		val rows = (counts.size + cols - 1) / cols
		val gridW = cols * SLOT + (cols - 1) * SLOT_GAP
		val gridH = rows * SLOT + (rows - 1) * SLOT_GAP
		val startX = x + maxOf(GRID_PAD, (w - gridW) / 2)
		val startY = y + maxOf(GRID_PAD, (h - gridH) / 2)
		for ((i, entry) in counts.entries.withIndex()) {
			val col = i % cols
			val row = i / cols
			val sx = startX + col * (SLOT + SLOT_GAP)
			val sy = startY + row * (SLOT + SLOT_GAP)
			val icon = bestIcon[entry.key]
				?: InventorySnapshot.Slot("FINE_" + entry.key + "_GEM", 1, listOf(), null, null, null, null)
			drawSlot(g, font, sx, sy, icon, mouseX, mouseY)
			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
				val tip = ArrayList<Component>()
				tip.add(
					Component.literal(InventoryDecoder.prettyWords(entry.key))
						.withStyle(Style.EMPTY.withColor(gemColour(entry.key)))
				)
				val c = entry.value
				for (t in GEM_TIERS.indices) {
					tip.add(
						Component.literal(GEM_TIER_LABELS[t] + ": ")
							.withStyle(Style.EMPTY.withColor(gemTierColour(t)))
							.append(
								Component.literal(FormatUtil.commas(c[t].toLong()))
									.withStyle(Style.EMPTY.withColor(0xFFE8E8F0.toInt()))
							)
					)
				}
				// Prefer showing this tip over the generic item tip.
				hoveredSlot = null
				hoveredStack = ItemStack.EMPTY
				gemHoverTip = tip
				gemHoverX = mouseX
				gemHoverY = mouseY
			}
		}
	}

	private fun drawGrid(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		columns: Int,
		slots: List<InventorySnapshot.Slot?>,
		statusRow: Boolean,
		equippedColumn: Int,
		mouseX: Int,
		mouseY: Int,
		emptyLabel: Boolean = true
	) {
		val cols = maxOf(1, columns)
		var rows = maxOf(1, (slots.size + cols - 1) / cols)
		val statusReserve = if (statusRow) SLOT + SLOT_GAP else 0
		val maxRows = maxOf(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP))
		rows = minOf(rows, maxRows)
		val gridW = cols * SLOT + (cols - 1) * SLOT_GAP
		val gridH = rows * SLOT + (rows - 1) * SLOT_GAP + statusReserve
		val startX = x + maxOf(GRID_PAD, (w - gridW) / 2)
		val startY = y + maxOf(GRID_PAD, (h - gridH) / 2)
		val maxSlots = cols * rows

		val shown = minOf(slots.size, maxSlots)
		for (i in 0 until shown) {
			val col = i % cols
			val row = i / cols
			drawSlot(g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP), slots[i], mouseX, mouseY)
		}

		if (statusRow) {
			val dyeY = startY + rows * (SLOT + SLOT_GAP)
			for (col in 0 until cols) {
				val dyeX = startX + col * (SLOT + SLOT_GAP)
				drawSlot(g, font, dyeX, dyeY, null, mouseX, mouseY)
				val dye = ItemStack(if (col == equippedColumn) CI.LIME_DYE else CI.LIGHT_GRAY_DYE)
				g.item(dye, dyeX + 1, dyeY + 1)
			}
		}

		if (emptyLabel && slots.all { isEmptySlot(it) }) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED)
		}
	}

	private fun drawSlot(
		g: GuiGraphicsExtractor,
		font: Font,
		sx: Int,
		sy: Int,
		slot: InventorySnapshot.Slot?,
		mouseX: Int,
		mouseY: Int
	) {
		val hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT
		val match = slotMatches(slot)
		PvDraw.roundedFill(g, sx, sy, SLOT, SLOT, 4, if (match) 0x70604810 else if (hovered) 0x30FFFFFF else PvDraw.CELL)
		PvDraw.roundedRing(
			g, sx, sy, SLOT, SLOT, 4,
			if (match) SEARCH_HIGHLIGHT else if (hovered) PvDraw.COLOR_ACCENT else 0x00FFFFFF, PvDraw.C_ALL
		)

		if (slot != null && !slot.isEmpty()) {
			val stack = cachedStack(slot)
			SkyBlockIconRenderer.draw(g, stack, slot.id, sx + 2, sy + 2, SLOT - 4)
			val openIndex = openSackIndex
			val hideCount = pane == InventoryPane.SACKS && openIndex != null && openIndex >= 0
			if (!hideCount && slot.count > 1) {
				val count = if (slot.count <= 64) slot.count.toString() else FormatUtil.shortXp(slot.count.toDouble())
				val scale = if (font.width(count) > SLOT - 4) 0.5f else 1f
				val textH = maxOf(1, Math.round(font.lineHeight * scale))
				val textW = maxOf(1, Math.round(font.width(count) * scale))
				val tx = sx + SLOT - 1 - textW
				val ty = sy + SLOT - 1 - textH
				if (scale >= 0.99f) {
					PvDraw.text(g, font, count, tx, ty, PvDraw.COLOR_TEXT)
				} else {
					PvDraw.textScaled(g, font, count, tx, ty, PvDraw.COLOR_TEXT, scale)
				}
			}
			hits.add(SlotHit(sx, sy, SLOT, SLOT, slot, stack))
			if (hovered) {
				hoveredSlot = slot
				hoveredStack = stack
			}
		}
	}

	private fun cachedStack(slot: InventorySnapshot.Slot): ItemStack =
		stackCache.getOrPut(slot) { SkyBlockItemFactory.toStack(slot) }

	private fun rebuildSearchIndex() {
		searchPanes.clear()
		searchPages.clear()
		if (searchQuery.isEmpty()) {
			return
		}
		for (pane in InventoryPane.entries) {
			if (!pane.visibleOn(snapshot)) {
				continue
			}
			if (pane == InventoryPane.LOADOUTS) {
				for ((i, loadout) in snapshot.loadouts.withIndex()) {
					if (loadoutMatches(loadout)) {
						searchPanes.add(pane)
						searchPages.getOrPut(pane) { HashSet() }.add(i / loadoutsPerPage)
					}
				}
				continue
			}
			for ((i, page) in pagesFor(pane).withIndex()) {
				if (pageMatches(page)) {
					searchPanes.add(pane)
					searchPages.getOrPut(pane) { HashSet() }.add(i)
				}
			}
		}
	}

	private fun pageMatches(page: InventorySnapshot.Page?): Boolean = page != null && page.slots.any { slotMatches(it) }

	private fun loadoutMatches(loadout: InventorySnapshot.Loadout?): Boolean {
		if (loadout == null) {
			return false
		}
		return loadout.equipment.any { slotMatches(it) } || loadout.armor.any { slotMatches(it) } || slotMatches(loadout.pet)
	}

	private fun slotMatches(slot: InventorySnapshot.Slot?): Boolean {
		if (searchQuery.isEmpty() || slot == null || slot.isEmpty()) {
			return false
		}
		val id = slot.id?.lowercase(Locale.ROOT) ?: ""
		val name = plainText(slot.displayName)
		if (id.contains(searchQuery) || name.contains(searchQuery)) {
			return true
		}
		val customName = cachedStack(slot).get(DataComponents.CUSTOM_NAME)
		if (customName != null && plainText(customName.string).contains(searchQuery)) {
			return true
		}
		return slot.lore?.any { it != null && plainText(it).contains(searchQuery) } == true
	}

	private fun pagesFor(pane: InventoryPane): List<InventorySnapshot.Page> = when (pane) {
		InventoryPane.INVENTORY -> listOf(snapshot.inventory)
		InventoryPane.ENDER_CHEST -> snapshot.enderChest
		InventoryPane.BACKPACKS -> snapshot.backpacks
		InventoryPane.WARDROBE -> snapshot.wardrobe
		InventoryPane.EQUIPMENT_WARDROBE -> snapshot.equipmentWardrobe
		InventoryPane.LOADOUTS -> listOf()
		InventoryPane.SACKS -> snapshot.sacks
		InventoryPane.FISHING_BAG -> listOf(snapshot.fishingBag)
		InventoryPane.POTION_BAG -> listOf(snapshot.potionBag)
		InventoryPane.QUIVER -> listOf(snapshot.quiver)
		InventoryPane.ACCESSORY_BAG -> snapshot.accessoryBag
		InventoryPane.TIME_POCKET -> listOf(snapshot.timePocket)
		InventoryPane.PERSONAL_VAULT -> listOf(snapshot.personalVault)
		InventoryPane.CARNIVAL_MASKS -> listOf(snapshot.carnivalMasks)
		InventoryPane.CANDY_BAG -> listOf(snapshot.candyBag)
	}

	/** Ctrl+click steps 5 pages; normal click steps 1. */
	private fun stepPage(total: Int, direction: Int) {
		val size = maxOf(1, total)
		val page = clampPage(pane, size)
		val step = if (controlDown()) 5 else 1
		pageIndex[pane] = maxOf(0, minOf(size - 1, page + direction * step))
	}

	private fun clampPage(pane: InventoryPane, size: Int): Int {
		if (size <= 0) {
			return 0
		}
		val page = (pageIndex[pane] ?: 0).coerceIn(0, size - 1)
		pageIndex[pane] = page
		return page
	}

	private class SlotHit(val x: Int, val y: Int, val w: Int, val h: Int, val slot: InventorySnapshot.Slot, val stack: ItemStack)

	private class RunnableHit(val x: Int, val y: Int, val w: Int, val h: Int, val action: Runnable)

	companion object {
		private const val SLOT = 26
		private const val SLOT_GAP = 3
		private const val PAGE_BTN = 18
		private const val ARMOR_GAP = 8
		/** Same spacing as between inventory rows so the hotbar sits flush under the 3×9 grid. */
		private const val HOTBAR_GAP = SLOT_GAP
		private const val GRID_PAD = 4
		/** Small gap under the title for non-accessory-bag previews. */
		private const val PREVIEW_TOP_PAD = 6
		private const val COL_GAP = 4
		private const val META_GAP = 6
		private const val LOADOUT_GAP = 14
		private const val LOADOUT_MIN_W = 170
		private const val LOADOUT_ROW_GAP = 8
		private const val SEARCH_HIGHLIGHT = 0xFF55FF55.toInt()
		private const val PANEL_HOVER = 0x0AFFFFFF
		private const val FLIP_MS = 480
		private const val POWERS_ROW = 12
		private const val MISSING_ROW = 18
		private val FORMAT_CODE = Regex("§.")
		private val GEM_TIERS = arrayOf("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT")
		private val GEM_TIER_LABELS = arrayOf("Rough", "Flawed", "Fine", "Flawless", "Perfect")

		/** Panes not shown in the storage side column. */
		private val HIDDEN_PANES: Set<InventoryPane> = EnumSet.of(
			InventoryPane.POTION_BAG, InventoryPane.CARNIVAL_MASKS, InventoryPane.CANDY_BAG,
			InventoryPane.TIME_POCKET, InventoryPane.QUIVER
		)

		private fun missingTooltip(entry: MissingAccessories.Entry): List<Component> {
			val lines = ArrayList<Component>()
			val tierColor = SkyBlockItemFactory.tierArgb(entry.tier)
			lines.add(PvDraw.styled(entry.name, tierColor, true))
			lines.add(PvDraw.styled(entry.tier.replace('_', ' '), tierColor, false))
			lines.add(Component.empty())
			val price = Component.empty()
			price.append(PvDraw.styled("Price ", PvDraw.COLOR_MUTED, false))
			price.append(
				if (entry.priced()) {
					PvDraw.styled(FormatUtil.commas(Math.round(entry.price)), PvDraw.COLOR_GOLD, false)
				} else {
					PvDraw.styled(if (entry.soulbound) "Soulbound" else "No price", PvDraw.COLOR_MUTED, false)
				}
			)
			lines.add(price)
			if (entry.soldId != null) {
				val sold = Component.empty()
				sold.append(PvDraw.styled("After selling ", PvDraw.COLOR_MUTED, false))
				sold.append(PvDraw.styled(entry.soldName ?: "", PvDraw.COLOR_TEXT, false))
				if (entry.soldPrice > 0.0) {
					sold.append(PvDraw.styled(" +" + FormatUtil.shortCoins(entry.soldPrice), PvDraw.COLOR_GOLD, false))
				}
				lines.add(sold)
				if (entry.priced()) {
					val net = Component.empty()
					net.append(PvDraw.styled("Net cost ", PvDraw.COLOR_MUTED, false))
					net.append(PvDraw.styled(FormatUtil.commas(Math.round(entry.netCost())), PvDraw.COLOR_GOLD, false))
					lines.add(net)
				}
			}
			val mp = Component.empty()
			mp.append(PvDraw.styled("Magical Power ", PvDraw.COLOR_MUTED, false))
			mp.append(PvDraw.styled("+" + entry.mpGain, 0xFF55FFFF.toInt(), false))
			lines.add(mp)
			if (entry.priced() && entry.mpGain > 0) {
				val perMp = Component.empty()
				perMp.append(PvDraw.styled("Coins per MP ", PvDraw.COLOR_MUTED, false))
				perMp.append(PvDraw.styled(FormatUtil.shortCoins(entry.coinsPerMp()), PvDraw.COLOR_GOLD, false))
				lines.add(perMp)
			}
			return lines
		}

		private fun easeInOutCubic(t: Float): Float =
			if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f

		/**
		 * {cols, rows, perPage, compact}. Callers pass a height that already excludes the pager row.
		 * Two rows of full cards need ~206px, more than the usual panel gives, so fall back to compact cards there.
		 */
		private fun loadoutGrid(w: Int, h: Int, font: Font): IntArray {
			// Tall cards side by side: three across when they fit, one row per page.
			val cols = maxOf(2, minOf(3, (w + LOADOUT_GAP) / (150 + LOADOUT_GAP)))
			val rows = maxOf(1, (h + LOADOUT_ROW_GAP) / (loadoutCardHeight(font) + LOADOUT_ROW_GAP))
			if (h < loadoutCardHeight(font)) {
				val compactRows = maxOf(1, (h + LOADOUT_ROW_GAP) / (loadoutCompactHeight(font) + LOADOUT_ROW_GAP))
				return intArrayOf(cols, compactRows, cols * compactRows, 1)
			}
			return intArrayOf(cols, rows, cols * rows, 0)
		}

		private fun loadoutRowHeight(font: Font): Int = GRID_PAD + font.lineHeight + 4 + SLOT + GRID_PAD

		private fun loadoutCardHeight(font: Font): Int = GRID_PAD + font.lineHeight + 4 + 4 * SLOT + 3 * SLOT_GAP + GRID_PAD

		private fun loadoutCompactHeight(font: Font): Int = GRID_PAD + font.lineHeight + 4 + 2 * SLOT + SLOT_GAP + GRID_PAD

		private fun loadoutTreesLabel(loadout: InventorySnapshot.Loadout): String {
			val parts = ArrayList<String>(2)
			loadout.hotmPreset?.let { parts.add("HOTM $it") }
			loadout.hotfPreset?.let { parts.add("HOTF $it") }
			return parts.joinToString("  ")
		}

		private fun loadoutTreesTooltip(loadout: InventorySnapshot.Loadout): List<Component> {
			val lines = ArrayList<Component>()
			lines.add(PvDraw.styled("Skill Tree Presets", PvDraw.COLOR_TEXT, true))
			loadout.hotmPreset?.let { lines.add(treePresetLine("HOTM ", it)) }
			loadout.hotfPreset?.let { lines.add(treePresetLine("HOTF ", it)) }
			lines.add(Component.empty())
			lines.add(PvDraw.styled("HOTM/HOTF tabs show the active preset", PvDraw.COLOR_MUTED, false))
			return lines
		}

		private fun loadoutSummaryTooltip(loadout: InventorySnapshot.Loadout): List<Component> {
			val lines = ArrayList<Component>()
			lines.add(SkyBlockStats.loadoutName(loadout.name))
			lines.add(labelledLine("Power ", SkyBlockStats.powerName(loadout.powerStone)))
			lines.add(labelledLine("Tuning ", SkyBlockStats.tuningStats(loadout.tuning)))
			lines.add(labelledLine("Pet ", PvDraw.styled(loadout.petLabel.ifBlank { "-" }, loadoutPetColor(loadout), false)))
			loadout.hotmPreset?.let { lines.add(treePresetLine("HOTM ", it)) }
			loadout.hotfPreset?.let { lines.add(treePresetLine("HOTF ", it)) }
			if (loadout.hotmPreset == null && loadout.hotfPreset == null) {
				lines.add(PvDraw.styled("No HOTM/HOTF preset", PvDraw.COLOR_MUTED, false))
			}
			return lines
		}

		private fun labelledLine(label: String, value: Component): Component {
			val line = Component.empty()
			line.append(PvDraw.styled(label, PvDraw.COLOR_MUTED, false))
			line.append(value)
			return line
		}

		private fun loadoutPetColor(loadout: InventorySnapshot.Loadout): Int {
			val pet = loadout.pet
			if (pet != null && !pet.isEmpty()) {
				val tier = SkyBlockItemFactory.resolveTier(pet.id)
				if (tier.isNotBlank()) {
					return SkyBlockItemFactory.tierArgb(tier)
				}
			}
			return PvDraw.COLOR_TEXT
		}

		private fun treePresetLine(label: String, preset: Int): Component {
			val line = Component.empty()
			line.append(PvDraw.styled(label, PvDraw.COLOR_MUTED, false))
			line.append(PvDraw.styled("Preset $preset", PvDraw.COLOR_TEXT, false))
			return line
		}

		private fun bestTierIndex(id: String?): Int {
			if (id == null) {
				return -1
			}
			val upper = id.uppercase(Locale.ROOT)
			return GEM_TIERS.indices.lastOrNull { upper.startsWith(GEM_TIERS[it] + "_") } ?: -1
		}

		/** SkyBlock gem name colours (Rough→Perfect tip header). */
		private fun gemColour(gem: String?): Int {
			if (gem == null) {
				return 0xFFE8E8F0.toInt()
			}
			return when (gem.uppercase(Locale.ROOT)) {
				"RUBY" -> 0xFFFF5555
				"AMBER" -> 0xFFFFAA00
				"TOPAZ" -> 0xFFFFFF55
				"JADE" -> 0xFF55FF55
				"SAPPHIRE" -> 0xFF5555FF
				"AMETHYST" -> 0xFFAA00AA
				"JASPER" -> 0xFFFF55FF
				"OPAL" -> 0xFFFCFCFC
				"AQUAMARINE" -> 0xFF55FFFF
				"CITRINE" -> 0xFFE5A84B
				"ONYX" -> 0xFF555555
				"PERIDOT" -> 0xFF00AA00
				else -> 0xFFE8E8F0
			}.toInt()
		}

		/** Rough / Flawed / Fine / Flawless / Perfect label colours. */
		private fun gemTierColour(tierIdx: Int): Int = when (tierIdx) {
			0 -> 0xFFAAAAAA // Rough
			1 -> 0xFF55FF55 // Flawed
			2 -> 0xFF5555FF // Fine
			3 -> 0xFFAA00AA // Flawless
			4 -> 0xFFFFAA00 // Perfect
			else -> 0xFFE8E8F0
		}.toInt()

		private fun plainText(value: String?): String {
			if (value.isNullOrEmpty()) {
				return ""
			}
			return value.replace(FORMAT_CODE, "").lowercase(Locale.ROOT)
		}

		private fun trimToWidth(font: Font, value: String?, maxW: Int): String {
			if (value == null) {
				return ""
			}
			if (maxW <= 0 || font.width(value) <= maxW) {
				return value
			}
			val ellipsis = "..."
			val ellipsisW = font.width(ellipsis)
			if (maxW <= ellipsisW) {
				return ellipsis
			}
			val sb = StringBuilder(value)
			while (sb.isNotEmpty() && font.width(sb.toString()) + ellipsisW > maxW) {
				sb.setLength(sb.length - 1)
			}
			return sb.toString() + ellipsis
		}

		private fun trimComponent(font: Font, value: Component?, maxW: Int): Component {
			if (value == null) {
				return Component.empty()
			}
			if (maxW <= 0 || font.width(value) <= maxW) {
				return value
			}
			return PvDraw.styled(trimToWidth(font, value.string, maxW), PvDraw.COLOR_TEXT, false)
		}

		private fun controlDown(): Boolean {
			val mc: Minecraft? = Minecraft.getInstance()
			val window = mc?.window ?: return false
			return fishmod.compat.GLFW.keyDown(window, InputConstants.KEY_LCONTROL) ||
				fishmod.compat.GLFW.keyDown(window, InputConstants.KEY_RCONTROL)
		}
	}
}

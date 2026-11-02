/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift.page

import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.rift.RiftUi
import fishmod.pv.gui.rift.RiftUi.Companion.ARMOR_GAP
import fishmod.pv.gui.rift.RiftUi.Companion.COL_GAP
import fishmod.pv.gui.rift.RiftUi.Companion.GAP
import fishmod.pv.gui.rift.RiftUi.Companion.HOTBAR_GAP
import fishmod.pv.gui.rift.RiftUi.Companion.ITEM_SLOT_BG
import fishmod.pv.gui.rift.RiftUi.Companion.ITEM_SLOT_BORDER
import fishmod.pv.gui.rift.RiftUi.Companion.PAD
import fishmod.pv.gui.rift.RiftUi.Companion.PAGE_BTN
import fishmod.pv.gui.rift.RiftUi.Companion.SLOT
import fishmod.pv.gui.rift.RiftUi.Companion.SLOT_GAP
import kotlin.math.sign
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Rift inventory: player inventory + ender chest pager. */
class RiftInventoryPage {
	private var enderPage = 0

	fun resetPage() {
		enderPage = 0
	}

	fun mouseScrolled(
		@Suppress("UNUSED_PARAMETER") mouseX: Double, @Suppress("UNUSED_PARAMETER") mouseY: Double,
		scrollY: Double, snapshot: RiftSnapshot,
	): Boolean {
		val total = maxOf(1, snapshot.enderPages.size)
		if (total <= 1 || scrollY == 0.0) {
			return false
		}
		val next = (enderPage - scrollY.sign.toInt()).coerceIn(0, total - 1)
		if (next == enderPage) {
			return false
		}
		enderPage = next
		return true
	}

	fun render(
		snapshot: RiftSnapshot, ui: RiftUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val leftW = (w - GAP) / 2
		val rightW = w - leftW - GAP
		val leftX = x
		val rightX = x + leftW + GAP

		PvDraw.innerPanel(g, leftX, y, leftW, h)
		PvDraw.innerPanel(g, rightX, y, rightW, h)

		val titleY = y + PAD
		PvDraw.text(g, font, "Inventory", leftX + PAD, titleY, PvDraw.COLOR_TEXT)

		val contentTop = titleY + font.lineHeight + PREVIEW_TOP_PAD
		drawPlayerInventory(
			ui, g, font, leftX + PAD, contentTop, leftW - PAD * 2, h - (contentTop - y) - PAD,
			snapshot.inventory.slots, mx, my,
		)

		val pages = snapshot.enderPages
		val total = maxOf(1, pages.size)
		enderPage = maxOf(0, minOf(enderPage, total - 1))
		val page = pages[enderPage]

		PvDraw.text(g, font, "Ender Chest", rightX + PAD, titleY, PvDraw.COLOR_TEXT)
		val pagerY = y + h - PAD - PAGE_BTN
		val gridBottom = pagerY - 4
		drawGrid(
			ui, g, font, rightX + PAD, contentTop, rightW - PAD * 2, maxOf(SLOT, gridBottom - contentTop),
			page.columns, page.slots, mx, my,
		)
		drawPager(ui, g, font, rightX + PAD, pagerY, rightW - PAD * 2, enderPage, total, mx, my)
	}

	private fun drawPager(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, page: Int, total: Int, mx: Int, my: Int,
	) {
		val prevX = x
		val nextX = x + w - PAGE_BTN
		drawPageButton(ui, g, font, prevX, y, "<", mx, my, page > 0) { enderPage = maxOf(0, page - 1) }
		drawPageButton(ui, g, font, nextX, y, ">", mx, my, page < total - 1) { enderPage = minOf(total - 1, page + 1) }
		var label = "${page + 1} / $total"
		val labelMax = maxOf(40, nextX - prevX - PAGE_BTN - 12)
		if (font.width(label) > labelMax) {
			label = RiftUi.trim(font, label, labelMax)
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED)
	}

	private fun drawPageButton(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, label: String,
		mx: Int, my: Int, enabled: Boolean, action: Runnable,
	) {
		val hovered = enabled && mx >= x && mx < x + PAGE_BTN && my >= y && my < y + PAGE_BTN
		PvDraw.roundedFill(g, x, y, PAGE_BTN, PAGE_BTN, 3, if (hovered) 0x50386CC8 else PvDraw.CELL)
		PvDraw.roundedRing(g, x, y, PAGE_BTN, PAGE_BTN, 3, if (hovered) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(
			g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2,
			if (enabled) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED,
		)
		if (enabled) {
			ui.hits.add(RiftUi.RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action))
		}
	}

	private fun drawPlayerInventory(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int,
		slots: List<InventorySnapshot.Slot?>, mouseX: Int, mouseY: Int,
	) {
		val hasEquipment = slots.size >= 44
		val armorOff = if (hasEquipment) 4 else 0
		val invOff = armorOff + 4
		val equipment = Array(4) { i -> if (hasEquipment) slots.getOrNull(i) else null }
		val armor = Array(4) { i -> slots.getOrNull(armorOff + i) }
		val hotbar = Array(9) { i -> slots.getOrNull(invOff + i) }
		val main = Array(27) { i -> slots.getOrNull(invOff + 9 + i) }

		val armorH = 4 * SLOT + 3 * SLOT_GAP
		val mainH = 3 * SLOT + 2 * SLOT_GAP
		val totalH = maxOf(armorH, mainH + HOTBAR_GAP + SLOT)
		val mainW = 9 * SLOT + 8 * SLOT_GAP
		val gearW = SLOT + COL_GAP + SLOT
		val totalW = gearW + ARMOR_GAP + mainW
		val startX = x + maxOf(0, (w - totalW) / 2)
		val startY = y + maxOf(0, (h - totalH) / 2)

		val eqX = startX
		val armorX = eqX + SLOT + COL_GAP
		val bodyX = startX + gearW + ARMOR_GAP
		val hotbarY = startY + mainH + HOTBAR_GAP

		for (i in 0 until 4) {
			val sy = startY + i * (SLOT + SLOT_GAP)
			drawSlot(ui, g, font, eqX, sy, equipment[i], mouseX, mouseY)
			drawSlot(ui, g, font, armorX, sy, armor[i], mouseX, mouseY)
		}
		for (i in 0 until 27) {
			drawSlot(ui, g, font, bodyX + (i % 9) * (SLOT + SLOT_GAP), startY + (i / 9) * (SLOT + SLOT_GAP), main[i], mouseX, mouseY)
		}
		for (i in 0 until 9) {
			drawSlot(ui, g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY)
		}

		if (slots.all { it == null || it.isEmpty() }) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + minOf(h / 2, totalH / 2) - font.lineHeight / 2, PvDraw.COLOR_MUTED)
		}
	}

	private fun drawGrid(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int,
		columns: Int, slots: List<InventorySnapshot.Slot?>, mouseX: Int, mouseY: Int,
	) {
		val cols = maxOf(1, columns)
		val maxRows = maxOf(1, (h + SLOT_GAP) / (SLOT + SLOT_GAP))
		val rows = minOf(maxOf(1, (slots.size + cols - 1) / cols), maxRows)
		val gridW = cols * SLOT + (cols - 1) * SLOT_GAP
		val gridH = rows * SLOT + (rows - 1) * SLOT_GAP
		val startX = x + maxOf(0, (w - gridW) / 2)
		val startY = y + maxOf(0, (h - gridH) / 2)
		val shown = minOf(slots.size, cols * rows)
		for (i in 0 until shown) {
			drawSlot(ui, g, font, startX + (i % cols) * (SLOT + SLOT_GAP), startY + (i / cols) * (SLOT + SLOT_GAP), slots[i], mouseX, mouseY)
		}
		if (slots.all { it == null || it.isEmpty() }) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED)
		}
	}

	private fun drawSlot(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, sx: Int, sy: Int, slot: InventorySnapshot.Slot?, mouseX: Int, mouseY: Int,
	) {
		val hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT
		PvDraw.roundedFill(g, sx, sy, SLOT, SLOT, 3, if (hovered) 0x30FFFFFF else ITEM_SLOT_BG)
		PvDraw.roundedRing(g, sx, sy, SLOT, SLOT, 3, if (hovered) PvDraw.COLOR_ACCENT else ITEM_SLOT_BORDER, PvDraw.C_ALL)
		if (slot == null || slot.isEmpty()) {
			return
		}
		val stack = SkyBlockItemFactory.toStack(slot)
		RiftUi.drawItemIcon(g, stack, slot.id, sx + 1, sy + 1, 16)
		if (slot.count in 2..64) {
			PvDraw.textRight(g, font, slot.count.toString(), sx + SLOT - 1, sy + SLOT - font.lineHeight, PvDraw.COLOR_TEXT)
		}
		if (hovered) {
			ui.hoveredSlot = slot
			ui.hoveredStack = stack
		}
	}

	private companion object {
		const val PREVIEW_TOP_PAD = 6
	}
}

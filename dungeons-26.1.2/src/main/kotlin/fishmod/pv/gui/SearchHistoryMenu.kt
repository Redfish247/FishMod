/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.api.SearchHistory
import java.util.function.Consumer
import java.util.function.Supplier
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Recent searches dropdown under the player search box. */
class SearchHistoryMenu(
	private val history: Supplier<SearchHistory>,
	private val opener: Consumer<String>
) {
	@JvmRecord
	data class Layout(val x: Int, val y: Int, val w: Int, val h: Int, val rowH: Int, val rows: Int) {
		fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h

		fun rowY(index: Int): Int = y + 2 + index * rowH
	}

	private var icon: ItemStack? = null
	private var open = false
	private var lastFocused = false
	private var query = ""
	private var anchorX = 0
	private var anchorY = 0
	private var anchorW = 0
	private var anchorH = 0
	private var viewMinX = 0
	private var viewMinY = 0
	private var viewMaxX = Int.MAX_VALUE / 2
	private var viewMaxY = Int.MAX_VALUE / 2
	private var layout: Layout? = null

	fun isOpen(): Boolean = open

	/** Open and actually showing something; an empty or fully filtered history never draws. */
	fun isVisible(): Boolean = open && entries().isNotEmpty()

	fun open() {
		open = true
	}

	fun close() {
		open = false
	}

	fun entries(): List<SearchHistory.Entry> {
		val matches = history.get().filter(query)
		return if (matches.size > SearchHistory.MAX) matches.subList(0, SearchHistory.MAX) else matches
	}

	/**
	 * Follows the search box each frame: gaining focus or typing opens the dropdown, losing focus closes it.
	 * Returns true when this call made it appear, so the caller can close other menus.
	 */
	fun syncSearch(focused: Boolean, value: String?): Boolean {
		val next = value ?: ""
		val wasVisible = isVisible()
		if (focused && (!lastFocused || next != query)) {
			open = true
		} else if (!focused && lastFocused) {
			open = false
		}
		lastFocused = focused
		query = next
		return !wasVisible && isVisible()
	}

	fun setAnchor(x: Int, y: Int, w: Int, h: Int) {
		anchorX = x
		anchorY = y
		anchorW = w
		anchorH = h
	}

	fun setViewport(minX: Int, minY: Int, maxX: Int, maxY: Int) {
		viewMinX = minX
		viewMinY = minY
		viewMaxX = maxX
		viewMaxY = maxY
	}

	fun layout(): Layout? = layout

	fun layoutDropdown(lineHeight: Int) {
		val rows = entries().size
		layout = if (rows == 0) null else dropdownLayout(
			anchorX, anchorY, anchorW, anchorH, rows, lineHeight,
			viewMinX, viewMinY, viewMaxX, viewMaxY
		)
	}

	fun anchorContains(mx: Double, my: Double): Boolean =
		anchorW > 0 &&
			mx >= anchorX && mx < anchorX + anchorW &&
			my >= anchorY && my < anchorY + anchorH

	fun blocksMouse(mx: Double, my: Double): Boolean = isVisible() && layout?.contains(mx, my) == true

	fun rowAt(mx: Double, my: Double): Int {
		if (!blocksMouse(mx, my)) {
			return -1
		}
		val l = layout ?: return -1
		val count = minOf(l.rows, entries().size)
		for (i in 0 until count) {
			val rowY = l.rowY(i)
			if (my >= rowY && my < rowY + l.rowH) {
				return i
			}
		}
		return -1
	}

	/** Consumes clicks on the dropdown; outside clicks close it and carry on to whatever is underneath. */
	fun mouseClicked(mx: Double, my: Double, button: Int): Boolean {
		if (!isVisible()) {
			return false
		}
		if (blocksMouse(mx, my)) {
			val row = rowAt(mx, my)
			if (row >= 0 && button == 0) {
				val name = entries()[row].name
				open = false
				opener.accept(name)
			}
			return true
		}
		if (!anchorContains(mx, my)) {
			open = false
		}
		return false
	}

	fun mouseScrolled(mx: Double, my: Double): Boolean = blocksMouse(mx, my)

	fun keyPressed(key: Int): Boolean {
		if (key == 256 && isVisible()) {
			open = false
			return true
		}
		return false
	}

	fun render(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (!isVisible()) {
			return
		}
		layoutDropdown(font.lineHeight)
		val l = layout ?: return
		val shown = entries()
		PvDraw.popupSurface(g, l.x, l.y, l.w, l.h)
		val icon = icon ?: ItemStack(Items.CLOCK).also { icon = it }
		val hoverRow = rowAt(mouseX.toDouble(), mouseY.toDouble())
		val nameX = l.x + PAD + ICON + ICON_GAP
		val nameW = maxOf(0, l.x + l.w - PAD - nameX)
		for (i in 0 until minOf(l.rows, shown.size)) {
			val rowY = l.rowY(i)
			if (i == hoverRow) {
				PvDraw.fill(g, l.x + 1, rowY, l.w - 2, l.rowH, 0x33FFFFFF)
			}
			ItemIcons.drawItemIcon(g, icon, l.x + PAD, rowY + (l.rowH - ICON) / 2, ICON)
			val name = font.plainSubstrByWidth(shown[i].name, nameW)
			PvDraw.text(g, font, name, nameX, rowY + ROW_PAD / 2, if (i == hoverRow) PvDraw.COLOR_ACCENT else PvDraw.COLOR_TEXT)
		}
	}

	companion object {
		const val PAD = 4
		const val ICON = 10
		const val ICON_GAP = 4
		const val ROW_PAD = 4
		const val EDGE = 2

		/** Matches the search box width, opens under it, flips above when there is no room, and stays on screen. */
		@JvmStatic
		fun dropdownLayout(
			anchorX: Int, anchorY: Int, anchorW: Int, anchorH: Int, rows: Int, lineHeight: Int,
			minX: Int, minY: Int, maxX: Int, maxY: Int
		): Layout {
			val shown = maxOf(1, minOf(SearchHistory.MAX, rows))
			val rowH = lineHeight + ROW_PAD
			val w = anchorW
			val h = shown * rowH + 4
			val x = maxOf(minX + EDGE, minOf(anchorX, maxX - EDGE - w))
			var y = anchorY + anchorH + 1
			if (y + h > maxY - EDGE) {
				y = anchorY - 1 - h
			}
			y = maxOf(minY + EDGE, y)
			return Layout(x, y, w, h, rowH, shown)
		}
	}
}

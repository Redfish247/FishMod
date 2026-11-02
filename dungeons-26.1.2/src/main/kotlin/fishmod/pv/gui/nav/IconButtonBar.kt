/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.nav

import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

class IconButtonBar {
	private val hits = ArrayList<Hit>()

	fun clearHits() {
		hits.clear()
	}

	fun click(mouseX: Double, mouseY: Double): Boolean {
		for (hit in hits) {
			if (hit.contains(mouseX, mouseY)) {
				hit.onClick.run()
				return true
			}
		}
		return false
	}

	fun drawInnerButton(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		icon: ItemStack,
		label: Component,
		selected: Boolean,
		hovered: Boolean,
		onClick: Runnable
	) {
		val top = if (selected) 0x70386CC8 else if (hovered) 0x40FFFFFF else PvDraw.CELL
		val edge = if (selected) 0xB05B8CFF.toInt() else if (hovered) 0x00FFFFFF else PvDraw.GLASS_EDGE
		PvDraw.glassSurface(g, x, y, TAB, TAB, 5, top, PvDraw.GLASS_INNER_BOTTOM, PvDraw.C_ALL, edge)
		g.item(icon, x + 5, y + 5)
		hits.add(Hit(x, y, TAB, TAB, onClick))
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y)
		}
	}

	fun drawTopFrameTabs(
		g: GuiGraphicsExtractor,
		font: Font,
		panelX: Int,
		panelY: Int,
		mouseX: Int,
		mouseY: Int,
		entries: List<Entry>,
		selectedKey: Any?
	) {
		// Tab body sits fully above the panel; selected tab opens a 1px seam into the border.
		val tabY = panelY - TAB
		var cx = panelX + 4
		for (entry in entries) {
			val selected = entry.key == selectedKey
			val hovered = mouseX >= cx && mouseX < cx + TAB && mouseY >= tabY && mouseY < panelY + 1
			drawTopTab(g, font, cx, tabY, panelY, entry.icon, entry.label, selected, hovered, entry.onClick)
			cx += TAB + GAP
		}
	}

	fun drawLeftFrameTabs(
		g: GuiGraphicsExtractor,
		font: Font,
		panelX: Int,
		panelY: Int,
		panelH: Int,
		mouseX: Int,
		mouseY: Int,
		entries: List<Entry>,
		selectedKey: Any?
	) {
		val tabX = panelX - TAB
		var cy = panelY
		var tabSize = TAB
		var gap = GAP
		if (panelH > 0 && entries.isNotEmpty()) {
			val n = entries.size
			var need = n * TAB + (n - 1) * GAP
			if (need > panelH) {
				gap = maxOf(0, (panelH - n * TAB) / maxOf(1, n - 1))
				need = n * TAB + (n - 1) * gap
			}
			if (need > panelH) {
				// Even with zero gap, shrink tabs so the stack ends at panel bottom.
				tabSize = maxOf(16, panelH / n)
				gap = maxOf(0, (panelH - n * tabSize) / maxOf(1, n - 1))
			}
		}
		for (entry in entries) {
			val selected = entry.key == selectedKey
			val hovered = mouseX >= tabX && mouseX < panelX + 1 && mouseY >= cy && mouseY < cy + tabSize
			drawLeftTab(
				g, font, tabX, cy, panelX, tabSize,
				entry.icon, entry.texture, entry.textureSize, entry.label, selected, hovered, entry.onClick
			)
			cy += tabSize + gap
		}
	}

	fun drawLeftFrameTabs(
		g: GuiGraphicsExtractor,
		font: Font,
		panelX: Int,
		panelY: Int,
		mouseX: Int,
		mouseY: Int,
		entries: List<Entry>,
		selectedKey: Any?
	) {
		drawLeftFrameTabs(g, font, panelX, panelY, 0, mouseX, mouseY, entries, selectedKey)
	}

	private fun drawTopTab(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		panelY: Int,
		icon: ItemStack,
		label: Component,
		selected: Boolean,
		hovered: Boolean,
		onClick: Runnable
	) {
		var h = panelY - y + (if (selected) 1 else 0)
		if (h < TAB) {
			h = TAB
		}
		val top = if (selected) PvDraw.GLASS_TOP else if (hovered) 0x50FFFFFF else PvDraw.GLASS_BOTTOM
		val bottom = if (selected) PvDraw.GLASS_TOP else if (hovered) 0x30FFFFFF else PvDraw.GLASS_BOTTOM
		val edge = if (selected) 0x00FFFFFF else if (hovered) 0x00FFFFFF else PvDraw.GLASS_EDGE
		PvDraw.glassSurface(g, x, y, TAB, h, 6, top, bottom, PvDraw.C_TL or PvDraw.C_TR, edge)
		if (selected) {
			// Open the panel's top edge under the selected tab so they read as one surface.
			PvDraw.fill(g, x + 1, panelY, TAB - 2, 1, PvDraw.GLASS_TOP)
			PvDraw.fill(g, x + 6, y + 1, TAB - 12, 1, PvDraw.COLOR_ACCENT)
		}

		g.item(icon, x + (TAB - 16) / 2, y + (TAB - 16) / 2)
		hits.add(Hit(x, y, TAB, maxOf(h, TAB), onClick))
		// Tooltips only - avoid building tip Components until hovered.
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y)
		}
	}

	private fun drawLeftTab(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		panelX: Int,
		tabH: Int,
		icon: ItemStack,
		texture: Identifier?,
		textureSize: Int,
		label: Component,
		selected: Boolean,
		hovered: Boolean,
		onClick: Runnable
	) {
		val h = maxOf(16, tabH)
		var w = panelX - x + (if (selected) 1 else 0)
		if (w < TAB) {
			w = TAB
		}
		val top = if (selected) PvDraw.GLASS_TOP else if (hovered) 0x50FFFFFF else PvDraw.GLASS_BOTTOM
		val bottom = if (selected) PvDraw.GLASS_TOP else if (hovered) 0x30FFFFFF else PvDraw.GLASS_BOTTOM
		val edge = if (selected) 0x00FFFFFF else if (hovered) 0x00FFFFFF else PvDraw.GLASS_EDGE
		PvDraw.glassSurface(g, x, y, w, h, 6, top, bottom, PvDraw.C_TL or PvDraw.C_BL, edge)
		if (selected) {
			PvDraw.fill(g, panelX, y + 1, 1, maxOf(1, h - 2), PvDraw.GLASS_TOP)
			PvDraw.fill(g, x + 1, y + 6, 1, maxOf(1, h - 12), PvDraw.COLOR_ACCENT)
		}

		val iconDraw = minOf(16, maxOf(10, h - 4))
		val ix = x + (TAB - iconDraw) / 2
		val iy = y + (h - iconDraw) / 2
		if (texture != null) {
			val tex = maxOf(1, textureSize)
			g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0f, 0f, iconDraw, iconDraw, tex, tex, tex, tex)
		} else if (iconDraw >= 16) {
			g.item(icon, ix, iy)
		} else {
			// Item renders are fixed 16×16 — center and accept slight clip into pad when tabs shrink.
			g.item(icon, x + (TAB - 16) / 2, y + (h - 16) / 2)
		}
		hits.add(Hit(x, y, maxOf(w, TAB), h, onClick))
		if (hovered) {
			tooltipLeft(g, font, label, x, y + h / 2)
		}
	}

	fun addHit(x: Int, y: Int, w: Int, h: Int, onClick: Runnable) {
		hits.add(Hit(x, y, w, h, onClick))
	}

	fun maybeTooltip(g: GuiGraphicsExtractor, font: Font, label: Component, hovered: Boolean, x: Int, y: Int) {
		if (!hovered) {
			return
		}
		val screen = screenSize()
		PvTooltip.drawComponents(g, font, listOf(label), x, y, screen[0], screen[1])
	}

	@JvmRecord
	data class Entry @JvmOverloads constructor(
		val key: Any,
		val icon: ItemStack,
		val label: Component,
		val onClick: Runnable,
		val texture: Identifier? = null,
		val textureSize: Int = 16
	)

	private data class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val onClick: Runnable) {
		fun contains(mx: Double, my: Double): Boolean =
			mx >= x && mx < x + w && my >= y && my < y + h
	}

	companion object {
		const val TAB = 26
		const val GAP = 1
		const val SEAM = 3

		const val BTN = TAB

		private var virtualW = 0
		private var virtualH = 0

		private fun tooltipAbove(g: GuiGraphicsExtractor, font: Font, label: Component, cx: Int, topY: Int) {
			val screen = screenSize()
			PvTooltip.drawCenteredAbove(g, font, label, cx, topY, screen[0], screen[1])
		}

		private fun tooltipLeft(g: GuiGraphicsExtractor, font: Font, label: Component, leftX: Int, cy: Int) {
			val screen = screenSize()
			PvTooltip.drawCenteredLeft(g, font, label, leftX, cy, screen[0], screen[1])
		}

		/** Set by the viewer each frame: screen size in the scaled viewport's coordinates. */
		@JvmStatic
		fun setVirtualScreen(w: Int, h: Int) {
			virtualW = w
			virtualH = h
		}

		@JvmStatic
		fun virtualHeight(): Int = virtualH

		private fun screenSize(): IntArray {
			if (virtualW > 0 && virtualH > 0) {
				return intArrayOf(virtualW, virtualH)
			}
			val window = Minecraft.getInstance().window
			return intArrayOf(window.guiScaledWidth, window.guiScaledHeight)
		}
	}
}

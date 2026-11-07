/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import java.util.EnumMap
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.ToIntFunction
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** "Socials" footer label and its external profile links popup. */
class SocialsMenu(
	private val target: Supplier<SocialLinks.Target?>,
	private val opener: Consumer<String>
) {
	@JvmRecord
	data class Layout(val x: Int, val y: Int, val w: Int, val h: Int, val rowH: Int, val nameW: Int) {
		fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h

		fun rowY(index: Int): Int = y + 2 + index * rowH
	}

	private val icons = EnumMap<SocialLinks.Site, ItemStack>(SocialLinks.Site::class.java)
	private var headUuid: UUID? = null
	private var open = false
	private var labelX = 0
	private var labelY = 0
	private var labelW = 0
	private var labelH = 0
	private var viewMinX = 0
	private var viewMinY = 0
	private var viewMaxX = Int.MAX_VALUE / 2
	private var viewMaxY = Int.MAX_VALUE / 2
	private var layout: Layout? = null

	fun isOpen(): Boolean = open

	fun close() {
		open = false
	}

	fun setLabel(x: Int, y: Int, w: Int, h: Int) {
		labelX = x
		labelY = y
		labelW = w
		labelH = h
	}

	fun layout(): Layout? = layout

	fun setViewport(minX: Int, minY: Int, maxX: Int, maxY: Int) {
		viewMinX = minX
		viewMinY = minY
		viewMaxX = maxX
		viewMaxY = maxY
	}

	fun layoutPopup(width: ToIntFunction<String>, lineHeight: Int) {
		layout = popupLayout(
			labelX, labelY, labelH, width, lineHeight,
			viewMinX, viewMinY, viewMaxX, viewMaxY
		)
	}

	fun labelContains(mx: Double, my: Double): Boolean =
		labelW > 0 &&
			mx >= labelX && mx < labelX + labelW &&
			my >= labelY && my < labelY + labelH

	/** True while the open popup (or its label) owns the mouse, so nothing underneath reacts. */
	fun blocksMouse(mx: Double, my: Double): Boolean =
		open && (labelContains(mx, my) || layout?.contains(mx, my) == true)

	fun rowAt(mx: Double, my: Double): Int {
		val l = layout
		if (!open || l == null || !l.contains(mx, my)) {
			return -1
		}
		for (i in SITES.indices) {
			val rowY = l.rowY(i)
			if (my >= rowY && my < rowY + l.rowH) {
				return i
			}
		}
		return -1
	}

	/** Consumes every click while open, so outside clicks only close the popup. */
	fun mouseClicked(mx: Double, my: Double, button: Int): Boolean {
		if (labelContains(mx, my)) {
			if (button == 0) {
				open = !open
			}
			return true
		}
		if (!open) {
			return false
		}
		val row = rowAt(mx, my)
		if (row >= 0) {
			if (button != 0) {
				return true
			}
			val url = SocialLinks.url(SITES[row], target.get()) ?: return true
			open = false
			opener.accept(url)
			return true
		}
		if (layout?.contains(mx, my) != true) {
			open = false
		}
		return true
	}

	fun mouseScrolled(mx: Double, my: Double): Boolean = blocksMouse(mx, my)

	fun keyPressed(key: Int): Boolean {
		if (open && key == fishmod.compat.GLFW.GLFW_KEY_ESCAPE) {
			open = false
			return true
		}
		return false
	}

	fun renderLabel(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val arrow = if (open) " ▲" else " ▼"
		val textW = font.width(LABEL)
		setLabel(x, y, textW + font.width(arrow), font.lineHeight)
		val hover = labelContains(mouseX.toDouble(), mouseY.toDouble())
		val color = if (hover || open) PvDraw.COLOR_ACCENT else PvDraw.COLOR_MUTED
		PvDraw.text(g, font, LABEL, x, y, color)
		PvDraw.text(g, font, arrow, x + textW, y, color)
	}

	fun renderPopup(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (!open) {
			return
		}
		layoutPopup({ font.width(it) }, font.lineHeight)
		val l = layout ?: return
		PvDraw.popupSurface(g, l.x, l.y, l.w, l.h)
		val viewed = target.get()
		val hoverRow = rowAt(mouseX.toDouble(), mouseY.toDouble())
		val nameX = l.x + PAD + ICON + ICON_GAP
		SITES.forEachIndexed { i, site ->
			val rowY = l.rowY(i)
			val available = SocialLinks.url(site, viewed) != null
			if (available && i == hoverRow) {
				PvDraw.fill(g, l.x + 1, rowY, l.w - 2, l.rowH, 0x33FFFFFF)
			}
			ItemIcons.drawItemIcon(g, icon(site, viewed), l.x + PAD, rowY + (l.rowH - ICON) / 2, ICON)
			val nameColor = when {
				!available -> PvDraw.COLOR_MUTED
				i == hoverRow -> PvDraw.COLOR_ACCENT
				else -> PvDraw.COLOR_TEXT
			}
			PvDraw.text(g, font, site.label(), nameX, rowY + ROW_PAD / 2, nameColor)
		}
	}

	private fun icon(site: SocialLinks.Site, viewed: SocialLinks.Target?): ItemStack? {
		val uuid = viewed?.uuid
		if (site == SocialLinks.Site.SKYCRYPT && uuid != headUuid) {
			icons.remove(site)
			headUuid = uuid
		}
		return icons.computeIfAbsent(site) {
			when (it) {
				SocialLinks.Site.PLANCKE -> ItemStack(Items.BOOK)
				SocialLinks.Site.SKYCRYPT -> ProfileSelectorController.coopMemberHead(uuid)
				SocialLinks.Site.ELITEBOT -> ItemStack(Items.WHEAT)
				SocialLinks.Site.COFLNET -> ItemStack(Items.GOLD_INGOT)
				SocialLinks.Site.NAMEMC -> ItemStack(Items.NAME_TAG)
			}
		}
	}

	companion object {
		const val LABEL = "Socials"
		const val LABEL_GAP = 10
		const val PAD = 6
		const val ICON = 10
		const val ICON_GAP = 4
		const val ROW_PAD = 4
		const val EDGE = 2
		private val SITES = SocialLinks.Site.entries.toTypedArray()

		/** Opens under the label, flips above it when there is no room, and stays inside the visible area. */
		@JvmStatic
		fun popupLayout(
			labelX: Int, labelY: Int, labelH: Int, width: ToIntFunction<String>, lineHeight: Int,
			minX: Int, minY: Int, maxX: Int, maxY: Int
		): Layout {
			val nameW = SITES.maxOf { width.applyAsInt(it.label()) }
			val rowH = lineHeight + ROW_PAD
			val w = PAD + ICON + ICON_GAP + nameW + PAD
			val h = SITES.size * rowH + 4
			val x = maxOf(minX + EDGE, minOf(labelX, maxX - EDGE - w))
			var y = labelY + labelH + 2
			if (y + h > maxY - EDGE) {
				y = labelY - 2 - h
			}
			y = maxOf(minY + EDGE, y)
			return Layout(x, y, w, h, rowH, nameW)
		}
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift

import fishmod.pv.data.InventorySnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

/** Shared Rift UI helpers, constants, and hover/hit state. */
class RiftUi {
	@JvmField
	val zones = ArrayList<HoverZone>()

	@JvmField
	val hits = ArrayList<RunnableHit>()

	@JvmField
	var hoveredSlot: InventorySnapshot.Slot? = null

	@JvmField
	var hoveredStack: ItemStack = ItemStack.EMPTY

	fun clear() {
		zones.clear()
		hits.clear()
		hoveredSlot = null
		hoveredStack = ItemStack.EMPTY
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		val hit = hits.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h } ?: return false
		hit.action.run()
		return true
	}

	fun drawHover(g: GuiGraphicsExtractor, font: Font, mx: Int, my: Int, screenW: Int, screenH: Int) {
		val slot = hoveredSlot
		if (slot != null) {
			val tip = SkyBlockItemFactory.tooltipLines(slot, hoveredStack)
			if (tip.isNotEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mx, my, screenW, screenH)
				return
			}
		}
		val zone = zones.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH)
	}

	fun drawLabeledBar(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String, fill: Float, maxed: Boolean,
		color: Int, hover: String?, x: Int, y: Int, w: Int, mx: Int, my: Int,
	): Int {
		PvDraw.labeledBar(g, font, label, value, fill, x, y, w, color, maxed)
		val bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT
		if (!hover.isNullOrBlank() && mx >= x && mx < x + w && my >= y && my < bottom) {
			zones.add(HoverZone(x, y, w, bottom - y, listOf(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))))
		}
		return bottom
	}

	@JvmRecord
	data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	@JvmRecord
	data class RunnableHit(val x: Int, val y: Int, val w: Int, val h: Int, val action: Runnable)

	companion object {
		const val PAD = 6
		const val GAP = 6
		const val STAT_ROW = 12
		const val SEP_GAP = 10
		const val BAR_LABEL_GAP = 2
		const val BAR_AFTER = 4

		/** Match Inventories tab slot sizing so icons sit flush. */
		const val SLOT = 18
		const val SLOT_GAP = 2
		const val COL_GAP = 4
		const val ARMOR_GAP = 8
		const val HOTBAR_GAP = SLOT_GAP
		const val PAGE_BTN = 18
		const val CHARM_ICON = 16
		const val CHARM_ROW = 18
		const val ITEM_SLOT_BG = 0x1CFFFFFF
		const val ITEM_SLOT_BORDER = 0x00FFFFFF
		const val OBTAINED = 0xFF55FF55.toInt()
		const val NOT_OBTAINED = 0xFFFF5555.toInt()

		const val MOTES_COLOR = 0xFFC97FFF.toInt()
		const val ENIGMA_COLOR = 0xFF55FFFF.toInt()
		const val TIMECHARM_COLOR = 0xFFFFAA00.toInt()
		const val BURGER_COLOR = 0xFFFF8855.toInt()
		const val CAT_COLOR = 0xFFFF77AA.toInt()
		const val EYE_COLOR = 0xFFAA55FF.toInt()
		const val ZONE_COLOR = 0xFF88AADD.toInt()
		const val FLIP_MS = 480
		const val PANEL_HOVER = 0x0AFFFFFF

		@JvmStatic
		fun drawItemIcon(g: GuiGraphicsExtractor, stack: ItemStack?, skyblockId: String?, x: Int, y: Int, size: Int) {
			if (stack == null || stack.isEmpty) {
				return
			}
			val pad = maxOf(0, (size - 16) / 2)
			SkyBlockIconRenderer.draw(g, stack, skyblockId, x + pad, y + pad, minOf(16, size))
		}

		@JvmStatic
		fun questRow(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int): Int {
			val right = if (value.isNullOrBlank()) "-" else value
			val gap = 8
			val labelMax = maxOf(8, w - font.width(right) - gap)
			PvDraw.text(g, font, trim(font, label, labelMax), x, y, PvDraw.COLOR_TEXT)
			PvDraw.textRight(g, font, right, x + w, y, questValueColor(right))
			return y + STAT_ROW
		}

		private fun questValueColor(value: String): Int {
			val lower = value.lowercase(Locale.ROOT)
			if (lower == "claimed" || lower == "complete" || lower == "unlocked" || lower == "reward claimed" ||
				lower.startsWith("3/3") || lower.startsWith("7/7") || lower.startsWith("8/8")
			) {
				return OBTAINED
			}
			if (lower == "started" || lower == "wand claimed") {
				return PvDraw.COLOR_GOLD
			}
			return PvDraw.COLOR_TEXT
		}

		@JvmStatic
		fun sectionSeparator(g: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") font: Font, panelX: Int, y: Int, panelW: Int): Int {
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, y + (SEP_GAP - 1) / 2, lineW, 1, 0x33FFFFFF)
			}
			return y + SEP_GAP
		}

		@JvmStatic
		fun statLine(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int {
			PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, if (value.isNullOrBlank()) "-" else value, x + w, y, valueColor)
			return y + STAT_ROW
		}

		@JvmStatic
		fun prettyTier(tier: String?): String = InventoryDecoder.prettyWords(tier)

		@JvmStatic
		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (maxW <= 0 || font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "…"
			val budget = maxW - font.width(ellipsis)
			if (budget <= 0) {
				return ellipsis
			}
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) > budget) {
					break
				}
				sb.append(c)
			}
			return "$sb$ellipsis"
		}

		@JvmStatic
		fun formatAgo(epochMs: Long): String {
			val sec = maxOf(0L, System.currentTimeMillis() - epochMs) / 1000L
			if (sec < 60) {
				return "${sec}s ago"
			}
			val min = sec / 60L
			if (min < 60) {
				return "${min}m ago"
			}
			val hr = min / 60L
			if (hr < 48) {
				return "${hr}h ago"
			}
			val days = hr / 24L
			if (days < 60) {
				return "${days}d ago"
			}
			return "${days / 30L}mo ago"
		}
	}
}

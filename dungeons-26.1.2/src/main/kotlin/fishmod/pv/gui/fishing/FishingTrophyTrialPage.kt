/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.fishing

import fishmod.compat.CI
import fishmod.pv.data.FishingSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.TrophyFishData
import fishmod.pv.data.TrophySkulls
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class FishingTrophyTrialPage {
	private data class Zone(val x: Int, val y: Int, val w: Int, val h: Int, val row: FishingSnapshot.TrophyRow)

	private val zones = ArrayList<Zone>()
	private var rowsFor: FishingSnapshot? = null
	private var rows: List<FishingSnapshot.TrophyRow> = listOf()

	fun render(
		g: GuiGraphicsExtractor, font: Font, snapshot: FishingSnapshot?,
		x: Int, y: Int, w: Int, h: Int
	) {
		zones.clear()
		val data = snapshot ?: FishingSnapshot.empty()
		if (data !== rowsFor) {
			rows = FishingPage.sortedRows(data.trophyFish) + FishingPage.sortedRows(data.trophyFrogs)
			rowsFor = data
		}
		val rows = this.rows

		PvDraw.innerPanel(g, x, y, w, h)
		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No data", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}

		val innerX = x + PAD
		val innerW = w - PAD * 2
		var top = y + PAD
		PvDraw.text(g, font, "Trophy fish", innerX, top, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, FormatUtil.commas(data.trophyFishTotal) + " caught", innerX + innerW, top, PvDraw.COLOR_ACCENT)
		top += font.lineHeight + 4

		val headerH = font.lineHeight + 3
		val avail = y + h - PAD - top - headerH
		// Two columns when one can't fit every row, else the last frog (Puddle Jumper) was clipped
		val blocks = if (innerW >= 330 || (innerW >= 240 && avail < rows.size * ROW_H_MIN)) 2 else 1
		val perBlock = (rows.size + blocks - 1) / blocks
		val blockW = (innerW - BLOCK_GAP * (blocks - 1)) / blocks
		val rowH = maxOf(ROW_H_MIN, minOf(ROW_H_MAX, avail / maxOf(1, perBlock)))

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1)
		for (b in 0 until blocks) {
			val bx = innerX + b * (blockW + BLOCK_GAP)
			drawHeader(g, font, bx, top, blockW)
			var ry = top + headerH
			for (i in b * perBlock until minOf(rows.size, (b + 1) * perBlock)) {
				drawRow(g, font, rows[i], bx, ry, blockW, rowH, (i - b * perBlock) % 2 == 1)
				ry += rowH
			}
		}
		g.disableScissor()
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		FishingPage.drawTrophyStatTooltip(g, font, zone.row, mouseX, mouseY, screenW, screenH)
	}

	private fun drawRow(
		g: GuiGraphicsExtractor, font: Font, row: FishingSnapshot.TrophyRow, x: Int, y: Int, w: Int, h: Int, stripe: Boolean
	) {
		if (stripe) {
			PvDraw.fill(g, x, y, w, h, 0x14FFFFFF)
		}
		val iconY = y + (h - ICON) / 2
		val best = row.counts?.bestDiscovered()
		var skull = ItemStack.EMPTY
		if (best != null) {
			val skullId = row.id + "_" + best.suffix()
			skull = SKULLS[skullId] ?: SkyBlockItemFactory.trophySkullStack(skullId).also {
				if (!TrophySkulls.value(skullId).isNullOrBlank()) {
					SKULLS[skullId] = it
				}
			}
		}
		if (skull.isEmpty) {
			skull = if (best == null) UNDISCOVERED_ICON else DISCOVERED_ICON
		}
		val scale = ICON / 16f
		g.pose().pushMatrix()
		g.pose().translate((x + 1).toFloat(), iconY.toFloat())
		g.pose().scale(scale, scale)
		g.item(skull, 0, 0)
		g.pose().popMatrix()

		val textY = y + (h - font.lineHeight) / 2
		val nameX = x + ICON + 5
		val nameMax = maxOf(8, w - TIERS.size * COUNT_W - (nameX - x))
		PvDraw.text(
			g, font, FishingPage.trim(font, row.name, nameMax), nameX, textY,
			if (row.anyDiscovered()) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
		)

		for (i in TIERS.indices) {
			val right = x + w - (TIERS.size - 1 - i) * COUNT_W
			val count = row.counts?.of(TIERS[i]) ?: 0
			if (count > 0) {
				PvDraw.textRight(g, font, FormatUtil.commas(count.toLong()), right, textY, FishingPage.tierColor(TIERS[i]))
			} else {
				PvDraw.textRight(g, font, "-", right, textY, PvDraw.COLOR_BAR_BG)
			}
		}
		zones.add(Zone(x, y, w, h, row))
	}

	companion object {
		private const val PAD = 6
		private const val ICON = 12
		private const val BLOCK_GAP = 10
		private const val ROW_H_MAX = 14
		private const val ROW_H_MIN = 9
		private const val COUNT_W = 26
		private val TIERS = arrayOf(
			TrophyFishData.Tier.BRONZE,
			TrophyFishData.Tier.SILVER,
			TrophyFishData.Tier.GOLD,
			TrophyFishData.Tier.DIAMOND
		)

		private val SKULLS = ConcurrentHashMap<String, ItemStack>()
		private val UNDISCOVERED_ICON = ItemStack(CI.GRAY_DYE)
		private val DISCOVERED_ICON = ItemStack(Items.TROPICAL_FISH)

		private fun drawHeader(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int) {
			for (i in TIERS.indices) {
				val right = x + w - (TIERS.size - 1 - i) * COUNT_W
				val title = when (TIERS[i]) {
					TrophyFishData.Tier.BRONZE -> "Brz"
					TrophyFishData.Tier.SILVER -> "Slv"
					TrophyFishData.Tier.GOLD -> "Gld"
					TrophyFishData.Tier.DIAMOND -> "Dia"
				}
				PvDraw.textRight(g, font, title, right, y, FishingPage.tierColor(TIERS[i]))
			}
			PvDraw.fill(g, x, y + font.lineHeight + 1, w, 1, 0x33FFFFFF)
		}
	}
}

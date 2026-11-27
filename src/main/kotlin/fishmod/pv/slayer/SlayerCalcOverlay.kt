/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.gui.PvDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import kotlin.math.max
import kotlin.math.min

class SlayerCalcOverlay {
	private var open = false
	private var openStartMs = 0L
	private var slayer: ProfileSnapshot.SlayerEntry? = null
	private var mods: SlayerMayorMods = SlayerMayorMods.none()
	private var levelText = ""
	private var replaceOnType = false
	private var fieldFocus = false
	private var accent = 0

	private var panelX = 0
	private var panelY = 0
	private var panelW = 0
	private var panelH = 0
	private var closeX = 0
	private var closeY = 0
	private var fieldX = 0
	private var fieldY = 0
	private var fieldW = 0

	fun isOpen(): Boolean = open

	fun open(slayer: ProfileSnapshot.SlayerEntry?, mods: SlayerMayorMods?, accent: Int) {
		this.slayer = slayer
		this.mods = mods ?: SlayerMayorMods.none()
		this.accent = if (accent == 0) PvDraw.COLOR_ACCENT else accent
		open = slayer != null
		openStartMs = System.currentTimeMillis()
		val max = SlayerXpCalculator.maxLevel(slayer?.id ?: "")
		val next = min(max, max(1, (slayer?.tier ?: 0) + 1))
		levelText = next.toString()
		replaceOnType = true
		fieldFocus = true
	}

	fun close() {
		open = false
		slayer = null
		fieldFocus = false
		replaceOnType = false
	}

	fun render(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		if (!open) return
		val result = currentResult() ?: return
		val lineH = font.lineHeight + 3
		val bossH = max(16, font.lineHeight) + 6
		val modLabels = result.mods.labels()
		val modsH = if (modLabels.isEmpty()) 0 else modLabels.size * (font.lineHeight + 2) + 4
		val contentH = PAD +
			font.lineHeight + 8 +
			bossH +
			FIELD_H + 8 +
			lineH +
			modsH +
			lineH +
			result.tiers.size * lineH +
			PAD
		panelW = min(300, max(220, screenW / 3))
		panelH = min(contentH, screenH - 36)
		panelX = (screenW - panelW) / 2
		panelY = (screenH - panelH) / 2

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000.toInt())

		val scale = openScale()
		val cx = panelX + panelW / 2f
		val cy = panelY + panelH / 2f
		g.pose().pushMatrix()
		if (scale < 0.999f) {
			g.pose().translate(cx, cy)
			g.pose().scale(scale, scale)
			g.pose().translate(-cx, -cy)
		}

		PvDraw.overlayPanel(g, panelX, panelY, panelW, panelH)

		val x = panelX + PAD
		var y = panelY + PAD
		val innerW = panelW - PAD * 2

		PvDraw.textBold(g, font, "Slayer Calculator", x, y, accent)
		closeX = panelX + panelW - PAD - CLOSE_SIZE
		closeY = panelY + PAD
		val closeHover = mouseX >= closeX && mouseX < closeX + CLOSE_SIZE &&
			mouseY >= closeY && mouseY < closeY + CLOSE_SIZE
		PvDraw.fill(g, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, if (closeHover) 0xFF3A3A4A.toInt() else 0xFF222230.toInt())
		g.outline(closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, PvDraw.COLOR_BORDER)
		PvDraw.textCentered(g, font, "x", closeX + CLOSE_SIZE / 2, closeY + 1, PvDraw.COLOR_MUTED)
		y += font.lineHeight + 8

		val itemY = y + max(0, (max(16, font.lineHeight) - 16) / 2)
		g.item(ItemStack(result.item), x, itemY)
		val bossTextY = y + max(0, (max(16, font.lineHeight) - font.lineHeight) / 2)
		PvDraw.text(g, font, "Slayer Boss:", x + 18, bossTextY, COLOR_LABEL)
		val bossX = x + 18 + font.width("Slayer Boss: ")
		PvDraw.text(
			g, font,
			PvDraw.styled(trim(font, result.bossName, x + innerW - bossX), accent, true),
			bossX, bossTextY
		)
		y += bossH

		PvDraw.text(g, font, "Target level", x, y, COLOR_LABEL)
		fieldW = 36
		fieldX = x + innerW - fieldW
		fieldY = y - 2
		val fieldHover = mouseX >= fieldX && mouseX < fieldX + fieldW &&
			mouseY >= fieldY && mouseY < fieldY + FIELD_H
		PvDraw.fill(g, fieldX, fieldY, fieldW, FIELD_H, 0xFF101018.toInt())
		g.outline(fieldX, fieldY, fieldW, FIELD_H, if (fieldFocus || fieldHover) accent else PvDraw.COLOR_BORDER)
		val shown = levelText.ifBlank { "-" }
		PvDraw.textCentered(g, font, shown, fieldX + fieldW / 2, fieldY + 2, COLOR_VALUE)
		y += FIELD_H + 8

		PvDraw.text(g, font, "XP to reach:", x, y, COLOR_LABEL)
		val xpColor = if (result.xpNeeded <= 0.5f) 0xFF6DFF8A.toInt() else COLOR_COIN
		PvDraw.textRight(g, font, FormatUtil.commas(Math.round(result.xpNeeded).toLong()), x + innerW, y, xpColor)
		y += lineH

		if (modLabels.isNotEmpty()) {
			for (label in modLabels) {
				PvDraw.text(g, font, PvDraw.styled(trim(font, label, innerW), PvDraw.COLOR_GOLD, false), x, y)
				y += font.lineHeight + 2
			}
			y += 4
		}

		PvDraw.text(g, font, "Bosses needed:", x, y, COLOR_HEADER)
		y += lineH
		for (tier in result.tiers) {
			val left = "Tier " + roman(tier.tier) + ": " + FormatUtil.commas(tier.bosses)
			val coins = "(" + FormatUtil.commas(tier.coins) + " coins)"
			val coinW = font.width(coins)
			PvDraw.text(g, font, PvDraw.styled(trim(font, left, innerW - coinW - 6), tier.color, true), x, y)
			PvDraw.textRight(g, font, coins, x + innerW, y, COLOR_COIN)
			y += lineH
		}

		g.pose().popMatrix()
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (!open) return false
		if (mx >= closeX && mx < closeX + CLOSE_SIZE && my >= closeY && my < closeY + CLOSE_SIZE) {
			close()
			return true
		}
		if (mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + FIELD_H) {
			fieldFocus = true
			replaceOnType = levelText.isNotEmpty()
			return true
		}
		if (mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH) {
			fieldFocus = false
			return true
		}
		close()
		return true
	}

	fun keyPressed(key: Int): Boolean {
		if (!open) return false
		if (key == fishmod.compat.GLFW.GLFW_KEY_ESCAPE) {
			close()
			return true
		}
		if (!fieldFocus) return false
		if (key == fishmod.compat.GLFW.GLFW_KEY_BACKSPACE) {
			if (replaceOnType) {
				levelText = ""
				replaceOnType = false
				return true
			}
			if (levelText.isNotEmpty()) levelText = levelText.substring(0, levelText.length - 1)
			return true
		}
		return false
	}

	fun charTyped(ch: Char): Boolean {
		if (!open || !fieldFocus) return false
		if (ch < '0' || ch > '9') return true
		if (replaceOnType) {
			levelText = ""
			replaceOnType = false
		}
		if (levelText.length >= 2) return true
		levelText += ch
		clampLevelText()
		return true
	}

	private fun clampLevelText() {
		if (levelText.isBlank()) return
		val value = levelText.trim().toIntOrNull() ?: return
		if (value > 9) levelText = "9"
	}

	private fun currentResult(): SlayerXpCalculator.Result? {
		val slayer = slayer ?: return null
		val max = min(9, SlayerXpCalculator.maxLevel(slayer.id))
		val target = parseLevel(levelText, min(max, slayer.tier + 1), max)
		return SlayerXpCalculator.calculate(slayer.id, slayer.name, slayer.xp, slayer.tier, target, mods)
	}

	private fun openScale(): Float {
		val elapsed = System.currentTimeMillis() - openStartMs
		if (elapsed >= OPEN_MS) return 1.0f
		val t = (elapsed / OPEN_MS.toFloat()).coerceIn(0.0f, 1.0f)
		val eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
		return OPEN_SCALE_START + (1.0f - OPEN_SCALE_START) * eased
	}

	private companion object {
		const val PAD = 8
		const val CLOSE_SIZE = 12
		const val FIELD_H = 14
		const val OPEN_MS = 220
		const val OPEN_SCALE_START = 0.12f
		const val COLOR_LABEL = 0xFF9A9AAC.toInt()
		const val COLOR_VALUE = 0xFFE8E8F0.toInt()
		const val COLOR_COIN = 0xFFFFAA00.toInt()
		const val COLOR_HEADER = 0xFF5B8CFF.toInt()

		fun parseLevel(text: String?, fallback: Int, max: Int): Int {
			val parsed = text?.trim()?.toIntOrNull() ?: fallback
			return max(1, min(max, parsed))
		}

		fun roman(n: Int): String = when (n) {
			1 -> "I"
			2 -> "II"
			3 -> "III"
			4 -> "IV"
			5 -> "V"
			else -> n.toString()
		}

		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) return ""
			if (maxW <= 0 || font.width(text) <= maxW) return text
			val ellipsis = "…"
			val budget = maxW - font.width(ellipsis)
			if (budget <= 0) return ellipsis
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) > budget) break
				sb.append(c)
			}
			return "$sb$ellipsis"
		}
	}
}

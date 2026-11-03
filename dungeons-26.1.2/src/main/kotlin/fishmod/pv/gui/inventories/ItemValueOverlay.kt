/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.networth.ItemWorth
import java.util.EnumMap
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

/** Click-to-open item coin breakdown for the Inventories tab. */
class ItemValueOverlay {
	private var opened = false
	private var breakdown: ItemWorth.Breakdown? = null
	private var title = "Item value"
	private var totalLabel = "Estimated value"
	private var icon: ItemStack = ItemStack.EMPTY
	private var skyblockId = ""
	private var panelX = 0
	private var panelY = 0
	private var panelW = 0
	private var panelH = 0
	private var closeX = 0
	private var closeY = 0
	private var scroll = 0
	private var maxScroll = 0

	fun isOpen(): Boolean = opened

	fun open(title: String?, icon: ItemStack?, breakdown: ItemWorth.Breakdown?) {
		open(title, icon, null, breakdown)
	}

	fun open(title: String?, icon: ItemStack?, skyblockId: String?, breakdown: ItemWorth.Breakdown?) {
		open(title, icon, skyblockId, breakdown, "Estimated value")
	}

	fun open(title: String?, icon: ItemStack?, skyblockId: String?, breakdown: ItemWorth.Breakdown?, totalLabel: String?) {
		this.totalLabel = if (totalLabel.isNullOrBlank()) "Estimated value" else totalLabel
		this.title = if (title.isNullOrBlank()) "Item value" else title
		this.icon = icon ?: ItemStack.EMPTY
		this.skyblockId = skyblockId ?: ""
		this.breakdown = breakdown ?: ItemWorth.Breakdown.empty()
		opened = true
		scroll = 0
	}

	fun close() {
		opened = false
		breakdown = null
		icon = ItemStack.EMPTY
		skyblockId = ""
		scroll = 0
		maxScroll = 0
	}

	fun mouseScrolled(delta: Double): Boolean {
		if (!opened || maxScroll <= 0) {
			return false
		}
		val before = scroll
		scroll = maxOf(0, minOf(maxScroll, scroll - Math.round(delta * 12).toInt()))
		return scroll != before
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		screenW: Int,
		screenH: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val breakdown = this.breakdown
		if (!opened || breakdown == null) {
			return
		}

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000.toInt())

		val rows = buildRows(breakdown, totalLabel, font)
		var contentH = PAD + 16 + 6
		for (row in rows) {
			contentH += row.height
		}
		contentH += PAD

		panelW = minOf(300, maxOf(210, screenW / 3))
		panelH = minOf(maxOf(contentH, 80), screenH - 40)
		panelX = (screenW - panelW) / 2
		panelY = (screenH - panelH) / 2
		maxScroll = maxOf(0, contentH - panelH)
		scroll = minOf(scroll, maxScroll)

		PvDraw.overlayPanel(g, panelX, panelY, panelW, panelH)

		var titleX = panelX + PAD
		if (!icon.isEmpty || skyblockId.isNotBlank()) {
			// Pass skyblock id so Hypixel models / official PNGs match the inventory slot.
			SkyBlockIconRenderer.draw(g, icon, skyblockId, titleX, panelY + PAD, 16)
			titleX += 20
		}
		PvDraw.textBold(
			g, font, trim(font, title, panelW - PAD * 2 - CLOSE_SIZE - 24),
			titleX, panelY + PAD + (16 - font.lineHeight) / 2, titleColor(breakdown)
		)

		closeX = panelX + panelW - PAD - CLOSE_SIZE
		closeY = panelY + PAD
		val closeHover = mouseX >= closeX && mouseX < closeX + CLOSE_SIZE && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE
		PvDraw.roundedFill(g, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 3, if (closeHover) 0xFF3A3A4A.toInt() else 0x24FFFFFF)
		PvDraw.roundedRing(g, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(g, font, "x", closeX + CLOSE_SIZE / 2, closeY + 1, COLOR_MUTED)

		val bodyTop = panelY + PAD + 16 + 6
		val bodyBottom = panelY + panelH - PAD
		g.enableScissor(panelX + 1, bodyTop, panelX + panelW - 1, bodyBottom)
		var y = bodyTop - scroll
		val innerW = panelW - PAD * 2
		for (row in rows) {
			if (y + row.height > bodyTop && y < bodyBottom) {
				drawRow(g, font, row, panelX + PAD, y, innerW)
			}
			y += row.height
		}
		g.disableScissor()
	}

	/** @return true if the click was consumed. */
	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (!opened) {
			return false
		}
		if (mx >= closeX && mx < closeX + CLOSE_SIZE && my >= closeY && my < closeY + CLOSE_SIZE) {
			close()
			return true
		}
		if (mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH) {
			return true
		}
		close()
		return true
	}

	private enum class Kind {
		TOTAL, SECTION, ITEM, UPGRADE, UPGRADE_SPLIT, DETAIL, NOTE, SPACER, DIVIDER
	}

	private data class Row(
		val kind: Kind,
		val left: String,
		val detail: String,
		val right: String,
		val leftColor: Int,
		val detailColor: Int,
		val height: Int
	) {
		companion object {
			fun total(left: String, right: String, color: Int, height: Int) = Row(Kind.TOTAL, left, "", right, color, 0, height)

			fun section(left: String, right: String, color: Int, height: Int) = Row(Kind.SECTION, left, "", right, color, 0, height)

			fun item(left: String, right: String, color: Int, height: Int) = Row(Kind.ITEM, left, "", right, color, 0, height)

			fun upgrade(left: String, right: String, color: Int, height: Int) = Row(Kind.UPGRADE, left, "", right, color, 0, height)

			fun upgradeSplit(label: String, detail: String, right: String, labelColor: Int, detailColor: Int, height: Int) =
				Row(Kind.UPGRADE_SPLIT, label, detail, right, labelColor, detailColor, height)

			fun detail(left: String, right: String, color: Int, height: Int) = Row(Kind.DETAIL, left, "", right, color, 0, height)

			fun note(left: String, height: Int) = Row(Kind.NOTE, left, "", "", COLOR_MUTED, 0, height)

			fun text(left: String, color: Int, height: Int) = Row(Kind.NOTE, left, "", "", color, 0, height)

			fun spacer(height: Int) = Row(Kind.SPACER, "", "", "", 0, 0, height)

			fun divider(height: Int) = Row(Kind.DIVIDER, "", "", "", 0, 0, height)
		}
	}

	companion object {
		private const val PAD = 8
		private const val CLOSE_SIZE = 12

		/** Strong green for estimated value / total labels. */
		private const val COLOR_TOTAL = 0xFF55FF55.toInt()
		/** Consistent coin prices. */
		private const val COLOR_PRICE = PvDraw.COLOR_GOLD
		/** Upgrade rows and upgrade section header. */
		private const val COLOR_UPGRADE = 0xFFFFAA55.toInt()
		/** Gemstone section + rows. */
		private const val COLOR_GEM = 0xFF55FFFF.toInt()
		/** Enchantment section + rows. */
		private const val COLOR_ENCHANT = 0xFFC88CFF.toInt()
		/** Unlock / apply / slot cost style rows. */
		private const val COLOR_COST = 0xFFFF7744.toInt()
		/** Soft structural labels. */
		private val COLOR_LABEL = PvDraw.COLOR_TEXT
		private val COLOR_MUTED = PvDraw.COLOR_MUTED

		private fun coins(value: Double): String = FormatUtil.shortCoins(Math.round(value).toDouble())

		private fun buildRows(breakdown: ItemWorth.Breakdown, totalLabel: String, font: Font): List<Row> {
			val rows = ArrayList<Row>()
			val lineH = font.lineHeight + 2

			if (breakdown.parts.isEmpty()) {
				rows.add(Row.text("No priced components", COLOR_MUTED, lineH))
				return rows
			}

			rows.add(Row.total(totalLabel, coins(breakdown.total) + " coins", headerColor(breakdown), lineH + 4))
			rows.add(Row.spacer(4))
			rows.add(Row.divider(6))

			val bySection = EnumMap<ItemWorth.Section, MutableList<ItemWorth.Part>>(ItemWorth.Section::class.java)
			for (part in breakdown.parts) {
				bySection.getOrPut(part.section) { ArrayList() }.add(part)
			}

			var firstSection = true
			for (section in ItemWorth.Section.entries) {
				val parts = bySection[section]
				if (parts.isNullOrEmpty()) {
					continue
				}
				val sectionTotal = parts.filter { it.role != ItemWorth.Role.NOTE }.sumOf { it.amount }

				if (!firstSection) {
					rows.add(Row.spacer(6))
				}
				firstSection = false

				sortSectionParts(section, parts)

				val sectionPrice = if (sectionTotal > 0) coins(sectionTotal) else ""
				rows.add(Row.section(section.title, sectionPrice, sectionHeaderColor(section), lineH + 2))

				for (part in parts) {
					rows.add(rowForPart(part, lineH))
				}
			}
			return rows
		}

		private fun headerColor(breakdown: ItemWorth.Breakdown?): Int {
			if (breakdown == null) {
				return COLOR_TOTAL
			}
			for (part in breakdown.parts) {
				if (part.role == ItemWorth.Role.ITEM && part.accentColor != 0) {
					return part.accentColor
				}
			}
			return COLOR_TOTAL
		}

		private fun titleColor(breakdown: ItemWorth.Breakdown?): Int = headerColor(breakdown)

		/** Enchantments: highest value first. Everything else: stable logical order. */
		private fun sortSectionParts(section: ItemWorth.Section, parts: MutableList<ItemWorth.Part>) {
			if (section == ItemWorth.Section.ENCHANTMENTS) {
				parts.sortWith { a, b ->
					val aNote = a.role == ItemWorth.Role.NOTE
					val bNote = b.role == ItemWorth.Role.NOTE
					if (aNote != bNote) {
						if (aNote) 1 else -1
					} else {
						b.amount.compareTo(a.amount)
					}
				}
				return
			}
			if (section == ItemWorth.Section.UPGRADES) {
				parts.sortBy { upgradeRank(it.label) }
			}
		}

		private fun upgradeRank(label: String?): Int {
			if (label.isNullOrBlank()) {
				return 900
			}
			return when (label) {
				"HPB's" -> 0
				"Fuming" -> 1
				"Recombobulator" -> 2
				"Art of War" -> 3
				"Art of Peace" -> 4
				"Stars" -> 5
				"Master Stars" -> 6
				"Reforge" -> 7
				"Etherwarp" -> 8
				"Transmission Tuner" -> 9
				"Wood Singularity" -> 10
				"Divan Powder Coating" -> 11
				"Farming for Dummies" -> 12
				"Jalapeno Book" -> 13
				"Mana Disintegrator" -> 14
				"Polarvoid Book" -> 15
				else -> 800
			}
		}

		private fun sectionHeaderColor(section: ItemWorth.Section): Int = when (section) {
			ItemWorth.Section.UPGRADES -> COLOR_UPGRADE
			ItemWorth.Section.GEMSTONES -> COLOR_GEM
			ItemWorth.Section.ENCHANTMENTS -> COLOR_ENCHANT
			ItemWorth.Section.CONTENTS, ItemWorth.Section.OTHER -> COLOR_MUTED
			ItemWorth.Section.BASE -> COLOR_MUTED
		}

		private fun rowLabelColor(part: ItemWorth.Part): Int {
			if (part.role == ItemWorth.Role.NOTE) {
				return COLOR_MUTED
			}
			if (part.role == ItemWorth.Role.ITEM && part.accentColor != 0) {
				return part.accentColor
			}
			if (isCostLike(part)) {
				return COLOR_COST
			}
			return when (part.section) {
				ItemWorth.Section.UPGRADES -> COLOR_UPGRADE
				ItemWorth.Section.GEMSTONES -> COLOR_GEM
				ItemWorth.Section.ENCHANTMENTS -> COLOR_ENCHANT
				ItemWorth.Section.CONTENTS, ItemWorth.Section.OTHER -> COLOR_LABEL
				ItemWorth.Section.BASE -> COLOR_LABEL
			}
		}

		private fun isCostLike(part: ItemWorth.Part): Boolean {
			val blob = (part.label + " " + part.detail).lowercase(Locale.ROOT)
			return blob.contains("cost") || blob.contains("unlock") || blob.contains("apply")
		}

		private fun rowForPart(part: ItemWorth.Part, lineH: Int): Row {
			if (part.role == ItemWorth.Role.NOTE || part.amount <= 0) {
				return Row.note("  " + part.label, lineH)
			}
			val price = coins(part.amount)
			val color = rowLabelColor(part)
			if (part.role == ItemWorth.Role.ITEM) {
				return Row.item(part.label, price, color, lineH)
			}
			if (part.role == ItemWorth.Role.UPGRADE) {
				if (part.detail.isNotBlank()) {
					// "Reforge" soft + stone/progress warm; keeps upgrade rows scannable.
					return Row.upgradeSplit(part.label, part.detail, price, COLOR_LABEL, color, lineH)
				}
				return Row.upgrade(part.label, price, color, lineH)
			}
			return Row.detail("  " + formatDetailLabel(part), price, color, lineH)
		}

		private fun formatDetailLabel(part: ItemWorth.Part): String =
			if (part.detail.isBlank()) part.label else part.label + ": " + part.detail

		private fun drawRow(g: GuiGraphicsExtractor, font: Font, row: Row, x: Int, y: Int, w: Int) {
			val textY = y + maxOf(0, (row.height - font.lineHeight) / 2)
			when (row.kind) {
				Kind.SPACER -> {
				}
				Kind.DIVIDER -> PvDraw.fill(g, x, y + row.height / 2, w, 1, PvDraw.COLOR_DIVIDER)
				Kind.SECTION -> {
					val ruleY = textY + font.lineHeight / 2
					val labelW = font.width(row.left)
					val valueW = if (row.right.isEmpty()) 0 else font.width(row.right)
					val gap = 6
					val ruleStart = x + labelW + gap
					val ruleEnd = x + w - (if (valueW > 0) valueW + gap else 0)
					PvDraw.text(g, font, row.left, x, textY, row.leftColor)
					if (ruleEnd > ruleStart) {
						PvDraw.fill(g, ruleStart, ruleY, ruleEnd - ruleStart, 1, PvDraw.COLOR_DIVIDER)
					}
					if (row.right.isNotEmpty()) {
						PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE)
					}
				}
				Kind.TOTAL -> {
					PvDraw.textBold(g, font, row.left, x, textY, row.leftColor)
					PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE)
				}
				Kind.NOTE -> PvDraw.text(g, font, trim(font, row.left, w), x, textY, COLOR_MUTED)
				Kind.UPGRADE_SPLIT -> {
					val valueW = font.width(row.right)
					val maxLeft = maxOf(40, w - valueW - 8)
					val prefixW = font.width(row.left + " ")
					PvDraw.text(g, font, row.left, x, textY, row.leftColor)
					val shownDetail = trim(font, row.detail, maxOf(8, maxLeft - prefixW))
					PvDraw.text(g, font, shownDetail, x + prefixW, textY, row.detailColor)
					PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE)
				}
				Kind.ITEM, Kind.UPGRADE, Kind.DETAIL -> {
					val valueW = font.width(row.right)
					PvDraw.text(g, font, trim(font, row.left, maxOf(40, w - valueW - 8)), x, textY, row.leftColor)
					PvDraw.textRight(g, font, row.right, x + w, textY, COLOR_PRICE)
				}
			}
		}

		private fun trim(font: Font, value: String?, maxW: Int): String {
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
	}
}

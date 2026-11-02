/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page

import fishmod.compat.CI
import fishmod.pv.data.CrimsonKuudraCard
import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.crimson.CrimsonUi.DISABLED
import fishmod.pv.gui.crimson.CrimsonUi.ENABLED
import fishmod.pv.gui.crimson.CrimsonUi.GAP
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER
import fishmod.pv.gui.crimson.CrimsonUi.KUUDRA_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.PAD
import fishmod.pv.gui.crimson.CrimsonUi.SEP_GAP
import fishmod.pv.gui.crimson.CrimsonUi.SLOT
import fishmod.pv.gui.crimson.CrimsonUi.SLOT_GAP
import fishmod.pv.gui.crimson.CrimsonUi.STAT_ROW
import fishmod.pv.gui.crimson.CrimsonUi.drawHover
import fishmod.pv.gui.crimson.CrimsonUi.sectionSeparator
import fishmod.pv.gui.crimson.CrimsonUi.statLine
import fishmod.pv.gui.crimson.CrimsonUi.stripFormatting
import fishmod.pv.gui.crimson.CrimsonUi.trim
import fishmod.pv.gui.crimson.CrimsonUi.visible
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.IdentityHashMap
import java.util.Locale
import java.util.function.ToIntFunction
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor

/** Crimson Kuudra subtab: tier stats + scrollable Kuudra card. */
class KuudraPage {
	private val zones = ArrayList<HoverZone>()

	private var kuudraScroll = 0
	private var kuudraMaxScroll = 0
	private var kuudraX = 0
	private var kuudraY = 0
	private var kuudraW = 0
	private var kuudraH = 0

	// Per-card caches so lore parsing and dyed icon copies don't run every frame.
	private var cachedCard: CrimsonKuudraCard? = null
	private val loreCache = IdentityHashMap<List<String>, List<Component>>()
	private val armorIcons = IdentityHashMap<CrimsonKuudraCard.ArmorPiece, ItemStack>()

	private val itemsColumn = KuudraChecklistColumn()
	private val armourColumn = KuudraChecklistColumn()
	private var showingChecklist = false
	private var buttonX = 0
	private var buttonY = 0
	private var buttonW = 0
	private var buttonH = 0

	fun resetScroll() {
		kuudraScroll = 0
		itemsColumn.resetScroll()
		armourColumn.resetScroll()
	}

	fun mouseClicked(mouseX: Double, mouseY: Double): Boolean {
		if (buttonW <= 0) {
			return false
		}
		if (mouseX >= buttonX && mouseX < buttonX + buttonW && mouseY >= buttonY && mouseY < buttonY + buttonH) {
			showingChecklist = !showingChecklist
			return true
		}
		return false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (showingChecklist) {
			return itemsColumn.mouseScrolled(mouseX, mouseY, scrollY) || armourColumn.mouseScrolled(mouseX, mouseY, scrollY)
		}
		if (kuudraMaxScroll > 0 &&
			mouseX >= kuudraX && mouseX < kuudraX + kuudraW &&
			mouseY >= kuudraY && mouseY < kuudraY + kuudraH
		) {
			val delta = if (scrollY > 0) -14 else 14
			val next = maxOf(0, minOf(kuudraMaxScroll, kuudraScroll + delta))
			if (next != kuudraScroll) {
				kuudraScroll = next
				return true
			}
		}
		return false
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int, @Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		zones.clear()
		if (showingChecklist) {
			drawChecklist(g, font, snapshot.kuudraCard, x, y, w, h, mouseX, mouseY)
			return
		}
		val leftW = maxOf(140, w * 40 / 100)
		val rightW = w - leftW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)
		drawKuudraTiers(g, font, snapshot, x, y, leftW, h, mouseX, mouseY)
		drawKuudraCard(g, font, snapshot, x + leftW + GAP, y, rightW, h, mouseX, mouseY)
	}

	private fun drawChecklist(
		g: GuiGraphicsExtractor, font: Font, card: CrimsonKuudraCard, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val headerH = 22
		PvDraw.innerPanel(g, x, y, w, headerH)
		val title = "Kuudra Gear Checklist"
		val textY = y + (headerH - font.lineHeight) / 2 + 1
		PvDraw.text(g, font, title, x + PAD, textY, KUUDRA_COLOR)
		val back = "← Back to Overview"
		val bw = font.width(back) + 14
		val bx = x + w - PAD - bw
		drawViewButton(g, font, back, bx, y + 3, bw, headerH - 6, mx, my)
		var legendX = x + PAD + font.width(title) + LEGEND_GAP
		for (part in headerLegend(bx - LEGEND_GAP - legendX) { font.width(it) }) {
			PvDraw.text(g, font, part.text, legendX, textY, part.color)
			legendX += font.width(part.text)
		}
		val top = y + headerH + GAP
		val colH = h - headerH - GAP
		val colW = (w - GAP) / 2
		itemsColumn.render(g, font, x, top, colW, colH, "Important Items", card.gear.items, mx, my, zones)
		armourColumn.render(g, font, x + colW + GAP, top, w - colW - GAP, colH, "Armour", card.gear.armour, mx, my, zones)
	}

	private fun drawViewButton(g: GuiGraphicsExtractor, font: Font, label: String, bx: Int, by: Int, bw: Int, bh: Int, mx: Int, my: Int) {
		buttonX = bx
		buttonY = by
		buttonW = bw
		buttonH = bh
		val hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh
		PvDraw.roundedFill(g, bx, by, bw, bh, 3, if (hovered) 0x40FF5555 else 0x26FF5555)
		PvDraw.roundedRing(g, bx, by, bw, bh, 3, if (hovered) KUUDRA_COLOR else 0xFF8A3A3A.toInt(), PvDraw.C_ALL)
		PvDraw.textCentered(g, font, label, bx + bw / 2, by + (bh - font.lineHeight) / 2 + 1, PvDraw.COLOR_WHITE)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		drawHover(g, font, zones, mouseX, mouseY, screenW, screenH)
	}

	private fun drawKuudraTiers(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		val buttonH = font.lineHeight + 9
		val buttonY = y + h - PAD - buttonH
		drawViewButton(g, font, "Gear Checklist →", lx, buttonY, lw, buttonH, mx, my)
		val tiersBottom = buttonY - 4

		PvDraw.text(g, font, "Kuudra", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 4
		ly = sectionSeparator(g, font, x, ly, w)

		val tiers = CrimsonSnapshot.KuudraTier.entries
		val tierGap = tierGap(tiersBottom - ly, tiers.size)
		for (tier in tiers) {
			if (ly + STAT_ROW * 2 > tiersBottom) {
				break
			}
			val stats = snapshot.kuudra(tier)
			PvDraw.text(g, font, tier.label + ":", lx, ly, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, FormatUtil.commas(stats.completions.toLong()), lx + lw, ly, KUUDRA_COLOR)
			ly += STAT_ROW
			PvDraw.text(g, font, "Highest wave:", lx, ly, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, if (stats.highestWave > 0) stats.highestWave.toString() else "-", lx + lw, ly, PvDraw.COLOR_TEXT)
			ly += STAT_ROW + tierGap
		}
	}

	private fun drawKuudraCard(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val card = snapshot.kuudraCard
		if (card !== cachedCard) {
			cachedCard = card
			loreCache.clear()
			armorIcons.clear()
		}
		val lx = x + PAD
		val contentTop = y + PAD
		val lw = w - PAD * 2
		val bottom = y + h - PAD

		val contentH = measureKuudraCard(font, card)
		kuudraX = x
		kuudraY = contentTop
		kuudraW = w
		kuudraH = maxOf(0, bottom - contentTop)
		kuudraMaxScroll = maxOf(0, contentH - kuudraH)
		kuudraScroll = minOf(kuudraScroll, kuudraMaxScroll)

		g.enableScissor(lx, kuudraY, lx + lw + 3, kuudraY + kuudraH)
		val ly = contentTop - kuudraScroll
		drawKuudraCardBody(g, font, card, lx, ly, lw, mx, my, kuudraY, kuudraY + kuudraH)
		g.disableScissor()
	}

	private fun measureKuudraCard(font: Font, card: CrimsonKuudraCard): Int =
		font.lineHeight + 2 + STAT_ROW * 3 + SEP_GAP + font.lineHeight + 3 +
			card.importantItems.size * STAT_ROW + SEP_GAP + font.lineHeight + 3 + SLOT + 4

	private fun drawKuudraCardBody(
		g: GuiGraphicsExtractor, font: Font, card: CrimsonKuudraCard,
		lx: Int, startY: Int, lw: Int, mx: Int, my: Int, clipTop: Int, clipBottom: Int,
	) {
		var ly = startY
		val scoreText = FormatUtil.shortXp(card.kuudraScore) + " [Level: " + FormatUtil.commas(card.kuudraLevel.toLong()) + "]"
		PvDraw.text(g, font, "Kuudra Score:", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, scoreText, lx + lw, ly, KUUDRA_COLOR)
		if (visible(ly, font.lineHeight, clipTop, clipBottom)) {
			val tip = card.scoreHoverLines().map { PvTooltip.Line.of(it, PvDraw.COLOR_MUTED) }
			zones.add(HoverZone.of(lx, maxOf(ly, clipTop), lw, minOf(ly + font.lineHeight, clipBottom) - maxOf(ly, clipTop), tip))
		}
		ly += font.lineHeight + 2

		val mp = FormatUtil.commas(card.magicalPower.toLong()) + " [" + card.selectedPowerLabel() + "]"
		ly = statLine(g, font, "Magical Power", trim(font, mp, lw - font.width("Magical Power") - 8), lx, ly, lw, PvDraw.COLOR_ACCENT)

		val skills = "Cata " + card.cataLevel + ", Combat " + card.combatLevel + ", Foraging " + card.foragingLevel + ", SB " + card.skyBlockLevel
		ly = statLine(g, font, "Skills", trim(font, skills, lw - font.width("Skills") - 8), lx, ly, lw, PvDraw.COLOR_TEXT)

		val vanq = String.format(Locale.ROOT, "%.3f%%", card.vanquisherChancePct)
		val vanqY = ly
		ly = statLine(g, font, "Vanquisher Chance", vanq, lx, ly, lw, ENABLED)
		if (visible(vanqY, STAT_ROW, clipTop, clipBottom)) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.of("Vanquisher Chance", PvDraw.COLOR_TEXT))
			for (line in card.vanquisherHover) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED))
			}
			zones.add(HoverZone.of(lx, maxOf(vanqY, clipTop), lw, minOf(vanqY + STAT_ROW, clipBottom) - maxOf(vanqY, clipTop), tip))
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2)
		PvDraw.text(g, font, "Important Items", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3

		for (item in card.importantItems) {
			if (visible(ly, STAT_ROW, clipTop, clipBottom)) {
				val color = if (item.owned) ENABLED else DISABLED
				val mark = if (item.owned) "✔ " else "✘ "
				val detail = if (item.details.isEmpty()) "" else " [" + item.details.joinToString(" · ") + "]"
				PvDraw.text(g, font, trim(font, mark + item.label + detail, lw), lx, ly, color)
				addLoreHoverZone(
					lx, maxOf(ly, clipTop), lw, minOf(ly + STAT_ROW, clipBottom) - maxOf(ly, clipTop),
					item.label, item.displayName, item.owned, item.details, item.lore,
				)
			}
			ly += STAT_ROW
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2)
		val colGap = 10
		val colW = maxOf(SLOT, (lw - colGap) / 2)
		val rightColX = lx + colW + colGap
		PvDraw.text(g, font, "Mage Armor", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, "Archer Armor", rightColX, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3
		drawArmorRow(g, card.mageArmor, lx, ly, mx, my, clipTop, clipBottom)
		drawArmorRow(g, card.archerArmor, rightColX, ly, mx, my, clipTop, clipBottom)
	}

	private fun drawArmorRow(
		g: GuiGraphicsExtractor, pieces: List<CrimsonKuudraCard.ArmorPiece>,
		lx: Int, ly: Int, mx: Int, my: Int, clipTop: Int, clipBottom: Int,
	) {
		for ((i, piece) in pieces.withIndex()) {
			val bx = lx + i * (SLOT + SLOT_GAP)
			val by = ly
			val hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT && my >= clipTop && my < clipBottom
			if (!visible(by, SLOT, clipTop, clipBottom)) {
				continue
			}
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG)
			g.outline(bx, by, SLOT, SLOT, if (hovered) PvDraw.COLOR_ACCENT else if (piece.owned) ITEM_SLOT_BORDER else DISABLED)
			var icon = armorIcons[piece]
			if (icon == null) {
				icon = armorIconStack(piece)
				// Paper is the still-loading placeholder; retry until the real icon arrives.
				if (!icon.isEmpty && !icon.`is`(Items.PAPER)) {
					armorIcons[piece] = icon
				}
			}
			val iconPad = maxOf(0, (SLOT - 16) / 2)
			if (!icon.isEmpty) {
				g.item(icon, bx + iconPad, by + iconPad)
				if (!piece.owned) {
					PvDraw.fill(g, bx + 1, by + 1, SLOT - 2, SLOT - 2, 0x88000000.toInt())
				}
			} else {
				g.item(EMPTY_ARMOR_ICON, bx + iconPad, by + iconPad)
			}
			val dye = piece.dyeColor
			if (piece.owned && dye != null) {
				PvDraw.fill(g, bx + SLOT - 4, by + 1, 3, 3, 0xFF000000.toInt() or (dye and 0xFFFFFF))
			}
			val y0 = maxOf(by, clipTop)
			val y1 = minOf(by + SLOT, clipBottom)
			if (y1 > y0) {
				addLoreHoverZone(
					bx, y0, SLOT, y1 - y0,
					piece.label.ifBlank { piece.slot }, piece.displayName, piece.owned, piece.details, piece.lore,
				)
			}
		}
	}

	/** Full item lore (when present) via legacy-formatted components, else a plain fallback tip. */
	private fun addLoreHoverZone(
		x: Int, y: Int, w: Int, h: Int,
		label: String, displayName: String?, owned: Boolean, details: List<String>, lore: List<String>?,
	) {
		if (!lore.isNullOrEmpty()) {
			val title = if (!displayName.isNullOrBlank()) displayName else label
			val tip = loreCache.getOrPut(lore) { loreComponents(title, lore) }
			zones.add(HoverZone.ofComponents(x, y, w, h, tip))
			return
		}
		val tip = ArrayList<PvTooltip.Line>()
		val title = if (!displayName.isNullOrBlank()) stripFormatting(displayName) else label
		tip.add(PvTooltip.Line.of(title, PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.of(if (owned) "Owned" else "Missing", if (owned) ENABLED else DISABLED))
		for (d in details) {
			tip.add(PvTooltip.Line.of(d, PvDraw.COLOR_MUTED))
		}
		zones.add(HoverZone.of(x, y, w, h, tip))
	}

	@JvmRecord
	data class LegendPart(val text: String, val color: Int)

	companion object {
		private const val LEGEND_GAP = 12
		private val EMPTY_ARMOR_ICON = ItemStack(CI.GRAY_DYE)

		private val LEGENDS: List<List<LegendPart>> = listOf(
			legend("✔ Good", "! Upgrade", "✘ Missing", "   "),
			legend("✔ Good", "! Upgrade", "✘ Missing", "  "),
			legend("✔ OK", "! Upg", "✘ Miss", " "),
			legend("✔", "!", "✘", " "),
		)

		private fun legend(good: String, upgrade: String, missing: String, gap: String): List<LegendPart> = listOf(
			LegendPart(good, KuudraChecklistColumn.COLOR_OK), LegendPart(gap, 0),
			LegendPart(upgrade, KuudraChecklistColumn.COLOR_UPGRADE), LegendPart(gap, 0),
			LegendPart(missing, KuudraChecklistColumn.COLOR_MISSING),
		)

		/** Widest legend that fits beside the title, shortening labels instead of clipping; empty if nothing fits. */
		@JvmStatic
		fun headerLegend(available: Int, width: ToIntFunction<String>): List<LegendPart> =
			LEGENDS.firstOrNull { parts -> parts.sumOf { width.applyAsInt(it.text) } <= available } ?: listOf()

		/** Gap between tier blocks, shrinking so every tier still fits above the checklist button. */
		@JvmStatic
		fun tierGap(available: Int, tiers: Int): Int = maxOf(0, minOf(6, available / maxOf(1, tiers) - STAT_ROW * 2))

		private fun armorIconStack(piece: CrimsonKuudraCard.ArmorPiece): ItemStack {
			val icon: ItemStack = SkyBlockItemFactory.iconStack(piece.iconId)
			if (icon.isEmpty) {
				return icon
			}
			val copy = icon.copy()
			val dye = piece.dyeColor
			if (dye != null) {
				val item = copy.item
				if (item === Items.LEATHER_HELMET || item === Items.LEATHER_CHESTPLATE ||
					item === Items.LEATHER_LEGGINGS || item === Items.LEATHER_BOOTS ||
					item === Items.LEATHER_HORSE_ARMOR
				) {
					copy.set(DataComponents.DYED_COLOR, DyedItemColor(dye and 0xFFFFFF))
				}
			}
			return copy
		}

		@JvmStatic
		fun loreComponents(title: String?, lore: List<String?>): List<Component> {
			val tip = ArrayList<Component>()
			tip.add(SkyBlockItemFactory.legacyLine(title ?: ""))
			val titlePlain = stripFormatting(title)
			var start = 0
			if (lore.isNotEmpty()) {
				val firstPlain = stripFormatting(lore[0])
				val n = minOf(titlePlain.length, firstPlain.length)
				if (titlePlain.isNotBlank() && firstPlain.isNotBlank() &&
					(firstPlain.equals(titlePlain, ignoreCase = true) ||
						titlePlain.regionMatches(0, firstPlain, 0, n, ignoreCase = true) ||
						firstPlain.regionMatches(0, titlePlain, 0, n, ignoreCase = true))
				) {
					start = 1
				}
			}
			for (i in start until lore.size) {
				val loreLine = lore[i]
				tip.add(if (loreLine.isNullOrBlank()) Component.empty() else SkyBlockItemFactory.legacyLine(loreLine))
			}
			return tip
		}
	}
}

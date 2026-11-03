/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.pets

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.PetListRules
import fishmod.pv.data.PetLoreResolver
import fishmod.pv.data.PetSnapshot
import fishmod.pv.gui.PvAhs
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Left ~56% compact pet inventory + right ~44% selected-pet details. */
class PetsPage {
	private var snapshot: PetSnapshot = PetSnapshot.empty()
	private var missingPets: List<PetLoreResolver.PetType> = listOf()
	private var selected = -1
	private var scroll = 0
	private var gridY = 0
	private var gridH = 0
	private var maxScroll = 0
	private var cols = 1
	private var slotSize = SLOT_SIZE
	private val hits = ArrayList<SlotHit>()
	private var xpBarX = 0
	private var xpBarY = 0
	private var xpBarW = 0
	private var xpBarH = 0
	private var xpHover: String? = null
	private var maxXpBarX = 0
	private var maxXpBarY = 0
	private var maxXpBarW = 0
	private var maxXpBarH = 0
	private var maxXpHover: String? = null
	private var hoverPetTip: List<Component>? = null
	private var hoverMissingName: String? = null
	private var autopetTip: List<PvTooltip.Line>? = null

	private enum class SortMode(val label: String) {
		XP("XP"),
		LEVEL("Lvl"),
		NAME("Name"),
		NETWORTH("NW"),
		RARITY("Rarity");

		fun next(): SortMode = entries[(ordinal + 1) % entries.size]
	}

	private var sortMode = SortMode.RARITY
	private var sorted: List<PetSnapshot.Entry> = listOf()
	private var sortHitX = 0
	private var sortHitY = 0
	private var sortHitW = 0
	private var sortHitH = 0

	fun apply(snapshot: PetSnapshot?) {
		this.snapshot = snapshot ?: PetSnapshot.empty()
		scroll = 0
		SkyBlockItemFactory.prefetchPets(this.snapshot)
		val owned = HashSet<String>()
		for (pet in this.snapshot.pets) {
			owned.add(pet.type.uppercase(Locale.ROOT))
		}
		missingPets = PetLoreResolver.petTypes().filter { !owned.contains(it.type) && PetListRules.showAsMissing(it.type, owned) }
		sorted = sortPets(PetListRules.visibleOwned(this.snapshot.pets), sortMode)
		selected = if (sorted.isEmpty()) -1 else 0
	}

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
		xpHover = null
		xpBarW = 0
		maxXpHover = null
		maxXpBarW = 0
		hoverPetTip = null
		hoverMissingName = null
		autopetTip = null
		// Compact pets grid (~56%) + wider details inspector (~44%).
		var leftW = maxOf(140, Math.round(w * 0.70).toInt())
		val rightW = maxOf(120, w - leftW - GAP)
		leftW = w - rightW - GAP
		val rightX = x + leftW + GAP

		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, rightX, y, rightW, h)

		drawGrid(g, font, x, y, leftW, h, mouseX, mouseY)
		drawStats(g, font, rightX, y, rightW, h)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val xp = xpHover
		val maxXp = maxXpHover
		val autopet = autopetTip
		val petTip = hoverPetTip
		if (xp != null
			&& mouseX >= xpBarX && mouseX < xpBarX + xpBarW
			&& mouseY >= xpBarY && mouseY < xpBarY + xpBarH
		) {
			PvTooltip.draw(g, font, listOf(xp), mouseX, mouseY, screenW, screenH)
		} else if (maxXp != null
			&& mouseX >= maxXpBarX && mouseX < maxXpBarX + maxXpBarW
			&& mouseY >= maxXpBarY && mouseY < maxXpBarY + maxXpBarH
		) {
			PvTooltip.draw(g, font, listOf(maxXp), mouseX, mouseY, screenW, screenH)
		} else if (autopet != null) {
			PvTooltip.drawStyled(g, font, autopet, mouseX, mouseY, screenW, screenH)
		} else if (petTip != null) {
			PvTooltip.drawComponents(g, font, petTip, mouseX, mouseY, screenW, screenH)
		}
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx >= sortHitX && mx < sortHitX + sortHitW
			&& my >= sortHitY && my < sortHitY + sortHitH
		) {
			val current = if (selected >= 0 && selected < sorted.size) sorted[selected] else null
			sortMode = sortMode.next()
			sorted = sortPets(sorted, sortMode)
			selected = if (current == null) selected else sorted.indexOf(current)
			return true
		}
		val missingName = hoverMissingName
		if (missingName != null && PvAhs.search(missingName)) {
			return true
		}
		for (hit in hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				selected = hit.index
				return true
			}
		}
		return false
	}

	fun mouseScrolled(scrollY: Double): Boolean {
		if (maxScroll <= 0) {
			return false
		}
		val before = scroll
		val step = slotSize + SLOT_GAP
		if (scrollY > 0) {
			scroll = maxOf(0, scroll - step)
		} else if (scrollY < 0) {
			scroll = minOf(maxScroll, scroll + step)
		}
		return scroll != before
	}

	private fun drawGrid(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
		PvDraw.text(g, font, "Pets", x + PAD, y + 6, PvDraw.COLOR_TEXT)
		val sortLabel = "Sort: " + sortMode.label
		val sortX = x + w - PAD - font.width(sortLabel)
		val sortHover = mouseX >= sortX && mouseX < x + w - PAD
			&& mouseY >= y + 6 && mouseY < y + 6 + font.lineHeight
		PvDraw.text(g, font, sortLabel, sortX, y + 6, if (sortHover) PvDraw.COLOR_ACCENT else PvDraw.COLOR_MUTED)
		sortHitX = sortX
		sortHitY = y + 6
		sortHitW = font.width(sortLabel)
		sortHitH = font.lineHeight
		val summaryY = y + 6 + font.lineHeight + 2
		val showSummary = snapshot.highestPetScore > 0
			|| snapshot.sacrificedTypes.isNotEmpty()
			|| snapshot.autopetRuleCount > 0
			|| snapshot.autopetRulesLimit > 0
		if (showSummary) {
			val scorePart = "Score " + FormatUtil.commas(snapshot.highestPetScore.toLong())
			val autopetPart = "Autopet " + snapshot.autopetRuleCount +
				"/" + maxOf(snapshot.autopetRulesLimit, snapshot.autopetRuleCount)
			val x0 = x + PAD
			val y0 = summaryY
			PvDraw.text(g, font, scorePart, x0, y0, PvDraw.COLOR_MUTED)
			val autopetX = x0 + font.width("$scorePart · ")
			PvDraw.text(g, font, " · $autopetPart", autopetX - font.width(" · "), y0, PvDraw.COLOR_MUTED)

			if (mouseY >= y0 && mouseY < y0 + font.lineHeight) {
				if (mouseX >= autopetX && mouseX < x + w - PAD) {
					autopetTip = autopetTooltip()
				}
			}
		}
		// Fixed top-left origin - never shift up / left / right when slot size changes.
		val gridX = x + PAD
		gridY = if (showSummary) summaryY + font.lineHeight + 4 else y + 6 + font.lineHeight + 6
		val gridW = w - PAD * 2
		gridH = maxOf(0, y + h - PAD - gridY)

		val pets = sorted
		if (pets.isEmpty()) {
			PvDraw.textCentered(g, font, "No pets", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			maxScroll = 0
			return
		}

		slotSize = SLOT_SIZE
		cols = maxOf(1, (gridW + SLOT_GAP) / (slotSize + SLOT_GAP))
		val total = pets.size + missingPets.size
		val rows = (total + cols - 1) / cols
		val contentH = rows * slotSize + maxOf(0, rows - 1) * SLOT_GAP
		maxScroll = maxOf(0, contentH - gridH)
		scroll = maxOf(0, minOf(scroll, maxScroll))

		g.enableScissor(gridX, gridY, gridX + gridW + 3, gridY + gridH)
		for (i in 0 until total) {
			val col = i % cols
			val row = i / cols
			val sx = gridX + col * (slotSize + SLOT_GAP)
			val sy = gridY + row * (slotSize + SLOT_GAP) - scroll
			if (sy + slotSize < gridY || sy > gridY + gridH) {
				continue
			}
			if (i < pets.size) {
				drawSlot(g, font, pets[i], i, sx, sy, mouseX, mouseY)
			} else {
				drawMissingSlot(g, missingPets[i - pets.size], sx, sy, mouseX, mouseY)
			}
		}
		g.disableScissor()
	}

	private fun autopetTooltip(): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("Autopet", PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		tip.add(
			PvTooltip.Line.row(
				"Rules", PvDraw.COLOR_MUTED,
				snapshot.autopetRuleCount.toString() + "/" + maxOf(snapshot.autopetRulesLimit, snapshot.autopetRuleCount),
				PvDraw.COLOR_ACCENT
			)
		)
		if (snapshot.autopetRules.isEmpty()) {
			tip.add(PvTooltip.Line.meta("No rules set"))
		} else {
			var shown = 0
			for (rule in snapshot.autopetRules) {
				if (shown >= 8) {
					tip.add(PvTooltip.Line.blank())
					tip.add(PvTooltip.Line.meta("+" + (snapshot.autopetRules.size - shown) + " more"))
					break
				}
				tip.add(PvTooltip.Line.blank())
				val pet = rule.petName.ifBlank { "Unknown pet" }
				val petColor = if (rule.disabled) PvDraw.COLOR_MUTED else PvDraw.COLOR_GOLD
				tip.add(PvTooltip.Line.title(pet, petColor))
				val sub = StringBuilder(rule.trigger)
				if (rule.detail.isNotBlank()) {
					sub.append(" · ").append(rule.detail)
				}
				if (rule.disabled) {
					sub.append(" · Off")
				}
				tip.add(PvTooltip.Line.meta(sub.toString()))
				shown++
			}
		}
		return tip
	}

	private fun drawSlot(
		g: GuiGraphicsExtractor,
		font: Font,
		pet: PetSnapshot.Entry,
		index: Int,
		sx: Int,
		sy: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val slot = slotSize
		val isSelected = index == selected
		val inGrid = mouseY >= gridY && mouseY < gridY + gridH && mouseX >= sx && mouseX < sx + slot
		val hovered = inGrid && mouseY >= sy && mouseY < sy + slot
		val rarity = SkyBlockItemFactory.tierArgb(pet.tier)
		val bg = raritySlotBackground(rarity, pet.active, isSelected, hovered)
		val border = when {
			isSelected -> SELECTED_BORDER
			pet.active -> ACTIVE_BORDER
			hovered -> PvDraw.COLOR_ACCENT
			else -> 0x00FFFFFF
		}
		PvDraw.roundedFill(g, sx, sy, slot, slot, 3, bg)
		PvDraw.roundedRing(g, sx, sy, slot, slot, 3, border, PvDraw.C_ALL)

		val icon = petIcon(pet)
		// Native 16px only - fractional upscale softens skull / pet textures.
		val draw = ITEM_ICON
		val ix = sx + (slot - draw) / 2
		// Nudge icon up 1px so level "100" sits in the bottom band without crowding.
		val iy = sy + maxOf(1, (slot - draw) / 2 - 1)
		SkyBlockIconRenderer.draw(g, icon, pet.neuId, ix, iy, draw)

		val level = pet.level.toString()
		val levelScale = 0.6f
		val levelW = Math.round(font.width(level) * levelScale)
		val levelY = sy + slot - Math.round(font.lineHeight * levelScale)
		val levelRight = sx + slot - 1
		PvDraw.textScaled(g, font, level, levelRight - levelW + 1, levelY, 0xFFFFFFFF.toInt(), levelScale)

		val hitTop = maxOf(sy, gridY)
		val hitBottom = minOf(sy + slot, gridY + gridH)
		if (hitBottom > hitTop) {
			hits.add(SlotHit(sx, hitTop, slot, hitBottom - hitTop, index))
		}
		if (hovered) {
			val petSlot = InventorySnapshot.Slot(
				pet.neuId,
				1,
				PetLoreResolver.loreFor(pet),
				PetLoreResolver.displayNameFor(pet),
				null,
				null,
				null
			)
			val petStack = SkyBlockItemFactory.toStack(petSlot)
			hoverPetTip = SkyBlockItemFactory.tooltipLines(petSlot, petStack)
		}
	}

	private fun drawMissingSlot(g: GuiGraphicsExtractor, pet: PetLoreResolver.PetType, sx: Int, sy: Int, mouseX: Int, mouseY: Int) {
		val slot = slotSize
		val hovered = mouseY >= gridY && mouseY < gridY + gridH &&
			mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot
		PvDraw.roundedFill(g, sx, sy, slot, slot, 3, MISSING_BG)
		PvDraw.roundedRing(g, sx, sy, slot, slot, 3, MISSING_BORDER, PvDraw.C_ALL)
		val ix = sx + (slot - ITEM_ICON) / 2
		val iy = sy + (slot - ITEM_ICON) / 2
		SkyBlockIconRenderer.draw(g, SkyBlockItemFactory.iconStack(pet.neuId), pet.neuId, ix, iy, ITEM_ICON)
		if (hovered) {
			hoverMissingName = pet.name
			val rarity = SkyBlockItemFactory.tierArgb(pet.tier) and 0xFFFFFF
			hoverPetTip = listOf(
				Component.literal(pet.name).withColor(rarity),
				Component.literal("Missing").withColor(0xFF5555),
				Component.literal("Click to search /ahs").withColor(0xAAAAAA)
			)
		}
	}

	private fun drawStats(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		PvDraw.text(g, font, "Details", x + PAD, y + 6, PvDraw.COLOR_TEXT)
		var cy = y + 6 + font.lineHeight + 8
		val contentW = w - PAD * 2
		val cx = x + PAD

		if (selected < 0 || selected >= sorted.size) {
			PvDraw.textCentered(g, font, "Select a pet", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}
		val pet = sorted[selected]

		val icon = petIcon(pet)
		SkyBlockIconRenderer.draw(g, icon, pet.neuId, cx, cy, ITEM_ICON)
		PvDraw.text(
			g,
			font,
			trim(font, pet.displayName, contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			SkyBlockItemFactory.tierArgb(pet.tier)
		)
		cy += ITEM_ICON + 8

		cy += row(g, font, "Tier", InventoryDecoder.prettyWords(pet.tier), cx, cy, contentW, SkyBlockItemFactory.tierArgb(pet.tier))
		cy += row(g, font, "Level", "${pet.level} / ${pet.maxLevel}", cx, cy, contentW, PvDraw.COLOR_TEXT)

		val maxed = pet.level >= pet.maxLevel
		val nextProgress = if (maxed) 1f else pet.progressToNext
		val maxProgress = if (pet.xpMax <= 0) {
			if (maxed) 1f else 0f
		} else {
			maxOf(0.0, minOf(1.0, pet.exp / pet.xpMax)).toFloat()
		}

		PvDraw.text(g, font, "Next level", cx, cy, PvDraw.COLOR_MUTED)
		val nextLabelY = cy
		cy += font.lineHeight + 2
		PvDraw.progressBar(g, cx, cy, contentW, PvDraw.BAR_HEIGHT, nextProgress, PvDraw.COLOR_BAR_FILL, maxed)
		xpBarX = cx
		xpBarY = nextLabelY
		xpBarW = contentW
		xpBarH = (cy + PvDraw.BAR_HEIGHT) - nextLabelY
		xpHover = if (maxed) {
			val overflow = maxOf(0.0, pet.exp - pet.xpMax)
			"Max level · Overflow: " + FormatUtil.shortXp(overflow)
		} else {
			FormatUtil.shortXp(pet.xpIntoLevel) + " / " + FormatUtil.shortXp(pet.xpToNext)
		}
		cy += PvDraw.BAR_HEIGHT + 6

		PvDraw.text(g, font, "To max", cx, cy, PvDraw.COLOR_MUTED)
		val maxLabelY = cy
		cy += font.lineHeight + 2
		PvDraw.progressBar(g, cx, cy, contentW, PvDraw.BAR_HEIGHT, maxProgress, PvDraw.COLOR_ACCENT, maxed)
		maxXpBarX = cx
		maxXpBarY = maxLabelY
		maxXpBarW = contentW
		maxXpBarH = (cy + PvDraw.BAR_HEIGHT) - maxLabelY
		maxXpHover = FormatUtil.shortXp(minOf(pet.exp, pet.xpMax)) + " / " + FormatUtil.shortXp(pet.xpMax)
		cy += PvDraw.BAR_HEIGHT + 8

		if (pet.candyUsed > 0) {
			cy += row(g, font, "Candy", pet.candyUsed.toString(), cx, cy, contentW, PvDraw.COLOR_TEXT)
		}

		PvDraw.text(g, font, "Held item", cx, cy, PvDraw.COLOR_MUTED)
		cy += font.lineHeight + 2
		if (pet.hasHeldItem()) {
			val held = SkyBlockItemFactory.iconStack(pet.heldItem)
			SkyBlockIconRenderer.draw(g, held, pet.heldItem, cx, cy, ITEM_ICON)
			val heldName = SkyBlockItemFactory.plainDisplayName(pet.heldItem)
			val heldColor = SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(pet.heldItem))
			PvDraw.text(
				g,
				font,
				trim(font, heldName, contentW - ITEM_ICON - 6),
				cx + ITEM_ICON + 4,
				cy + (ITEM_ICON - font.lineHeight) / 2,
				heldColor
			)
			cy += ITEM_ICON + 6
		} else {
			PvDraw.text(g, font, "None", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 6
		}

		if (pet.hasSkin()) {
			cy += row(g, font, "Skin", InventoryDecoder.prettyWords(pet.skin), cx, cy, contentW, PvDraw.COLOR_TEXT)
		}

		row(
			g,
			font,
			"Networth",
			if (pet.networth > 0) FormatUtil.shortCoins(pet.networth) else "-",
			cx,
			cy,
			contentW,
			PvDraw.COLOR_GOLD
		)
	}

	private data class SlotHit(val x: Int, val y: Int, val w: Int, val h: Int, val index: Int)

	companion object {
		private const val GAP = 8
		private const val PAD = 8
		/** Fixed Minecraft-inventory-style cell; 16px icons fill this without upscaling. */
		private const val SLOT_SIZE = 22
		private const val SLOT_GAP = 2
		private const val ITEM_ICON = 16
		private val SELECTED_BORDER = PvDraw.COLOR_ACCENT
		private const val ACTIVE_BORDER = 0xFF55FF55.toInt()

		private const val MISSING_BG = 0xFF3A1010.toInt()
		private const val MISSING_BORDER = 0xFFCC3333.toInt()

		private fun sortPets(source: List<PetSnapshot.Entry>, mode: SortMode): List<PetSnapshot.Entry> {
			val byName = compareBy<PetSnapshot.Entry> { it.displayName.lowercase(Locale.ROOT) }
			val byXp = compareByDescending<PetSnapshot.Entry> { it.exp }
			val comparator = when (mode) {
				SortMode.NAME -> byName
				SortMode.NETWORTH -> compareByDescending<PetSnapshot.Entry> { it.networth }.then(byName)
				SortMode.RARITY -> compareByDescending<PetSnapshot.Entry> { it.tierIndex }.then(byXp).then(byName)
				SortMode.LEVEL -> compareByDescending<PetSnapshot.Entry> { it.level }.then(byName)
				SortMode.XP -> byXp.then(byName)
			}
			return source.sortedWith(comparator)
		}

		/** Dark rarity-tinted slot fill (keeps icons readable). */
		private fun raritySlotBackground(rarityArgb: Int, active: Boolean, selected: Boolean, hovered: Boolean): Int {
			val r = (rarityArgb ushr 16) and 0xFF
			val green = (rarityArgb ushr 8) and 0xFF
			val b = rarityArgb and 0xFF
			val tint = if (hovered) 0.38f else if (selected) 0.32f else 0.26f
			var outR = Math.round(16 + r * tint)
			var outG = Math.round(16 + green * tint)
			val outB = Math.round(16 + b * tint)
			if (active) {
				outG = minOf(255, outG + 18)
				outR = maxOf(0, outR - 4)
			}
			return 0xFF000000.toInt() or (clampByte(outR) shl 16) or (clampByte(outG) shl 8) or clampByte(outB)
		}

		private fun clampByte(value: Int): Int = maxOf(0, minOf(255, value))

		private fun petIcon(pet: PetSnapshot.Entry): ItemStack {
			if (pet.hasSkin()) {
				val skin = SkyBlockItemFactory.iconStack("PET_SKIN_" + pet.skin.uppercase(Locale.ROOT))
				if (!skin.isEmpty && !skin.`is`(Items.PAPER)) {
					return skin
				}
			}
			return SkyBlockItemFactory.iconStack(pet.neuId)
		}

		private fun row(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int {
			PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, trim(font, value, maxOf(20, w - font.width(label) - 8)), x + w, y, valueColor)
			return font.lineHeight + 4
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

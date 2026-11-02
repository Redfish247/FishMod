/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page

import fishmod.pv.data.AbiphoneCosts
import fishmod.pv.data.AbiphoneNpcs
import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.crimson.CrimsonUi.DISABLED
import fishmod.pv.gui.crimson.CrimsonUi.DOJO_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.ENABLED
import fishmod.pv.gui.crimson.CrimsonUi.GAP
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER
import fishmod.pv.gui.crimson.CrimsonUi.PAD
import fishmod.pv.gui.crimson.CrimsonUi.drawHover
import fishmod.pv.gui.crimson.CrimsonUi.prettyRingtone
import fishmod.pv.gui.crimson.CrimsonUi.sectionSeparator
import fishmod.pv.gui.crimson.CrimsonUi.statLine
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.ItemPricer
import java.util.IdentityHashMap
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Crimson Abiphone subtab: contact grid + stats. */
class AbiphonePage {
	private val zones = ArrayList<HoverZone>()

	private var abiphoneScroll = 0
	private var abiphoneMaxScroll = 0
	private var abiphoneX = 0
	private var abiphoneY = 0
	private var abiphoneW = 0
	private var abiphoneH = 0
	private var cheapestFirst = false
	private var sortX = 0
	private var sortY = 0
	private var sortW = 0
	private var sortH = 0

	private var undiscoveredFor: CrimsonSnapshot? = null
	private var undiscoveredFinal = false
	private var undiscoveredAtMs = 0L
	private var undiscoveredCache: List<AbiphoneNpcs.Undiscovered> = listOf()
	private var cheapestCache: List<AbiphoneNpcs.Undiscovered> = listOf()
	private var costCache: Map<AbiphoneNpcs.Undiscovered, AbiphoneCosts.Estimate> = mapOf()

	fun resetScroll() {
		abiphoneScroll = 0
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (sortW > 0 && mx >= sortX && mx < sortX + sortW && my >= sortY && my < sortY + sortH) {
			cheapestFirst = !cheapestFirst
			abiphoneScroll = 0
			return true
		}
		return false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (abiphoneMaxScroll > 0 &&
			mouseX >= abiphoneX && mouseX < abiphoneX + abiphoneW &&
			mouseY >= abiphoneY && mouseY < abiphoneY + abiphoneH
		) {
			val delta = if (scrollY > 0) -14 else 14
			val next = maxOf(0, minOf(abiphoneMaxScroll, abiphoneScroll + delta))
			if (next != abiphoneScroll) {
				abiphoneScroll = next
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
		val statsW = maxOf(150, minOf(220, w * 32 / 100))
		val iconsW = w - statsW - GAP
		PvDraw.innerPanel(g, x, y, iconsW, h)
		PvDraw.innerPanel(g, x + iconsW + GAP, y, statsW, h)

		drawAbiphoneStats(g, font, snapshot, x + iconsW + GAP, y, statsW)
		drawAbiphoneIcons(g, font, snapshot, x, y, iconsW, h, mouseX, mouseY)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		drawHover(g, font, zones, mouseX, mouseY, screenW, screenH)
	}

	private fun drawAbiphoneStats(g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot, x: Int, y: Int, w: Int) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2

		PvDraw.text(g, font, "Abiphone", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, "${snapshot.abiphoneActive} / ${AbiphoneNpcs.TOTAL_CONTACTS}", lx + lw, ly, PvDraw.COLOR_ACCENT)
		ly += font.lineHeight + 4
		ly = sectionSeparator(g, font, x, ly, w)

		if (snapshot.abiphoneRingtone.isNotBlank()) {
			ly = statLine(g, font, "Ringtone", prettyRingtone(snapshot.abiphoneRingtone), lx, ly, lw, PvDraw.COLOR_TEXT)
		}
		if (snapshot.trioContactAddons > 0) {
			ly = statLine(g, font, "Trio addons", snapshot.trioContactAddons.toString(), lx, ly, lw, PvDraw.COLOR_GOLD)
		}
		if (snapshot.operatorChipRepaired > 0) {
			ly = statLine(g, font, "Operator chip", "Repaired " + snapshot.operatorChipRepaired, lx, ly, lw, PvDraw.COLOR_ACCENT)
		}
		ly = statLine(g, font, "Quests done", "${snapshot.abiphoneQuestsDone()}/${AbiphoneNpcs.TOTAL_CONTACTS}", lx, ly, lw, ENABLED)
		if (snapshot.abiphoneDndCount() > 0) {
			ly = statLine(g, font, "DND contacts", snapshot.abiphoneDndCount().toString(), lx, ly, lw, PvDraw.COLOR_MUTED)
		}
		if (snapshot.snakeBestScore > 0 || snapshot.tttLosses > 0 || snapshot.tttDraws > 0) {
			val games = "Snake " + snapshot.snakeBestScore + " · TTT L" + snapshot.tttLosses + " D" + snapshot.tttDraws
			ly = statLine(g, font, "Games", games, lx, ly, lw, DOJO_COLOR)
		}
		if (snapshot.abiphoneSort.isNotBlank()) {
			ly = statLine(g, font, "Sort", prettyRingtone(snapshot.abiphoneSort), lx, ly, lw, PvDraw.COLOR_MUTED)
		}
		ly = sectionSeparator(g, font, x, ly, w)
		PvDraw.text(g, font, "Last Called", lx, ly, PvDraw.COLOR_MUTED)
	}

	private fun drawAbiphoneIcons(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		val bottom = y + h - PAD

		PvDraw.text(g, font, "Contacts", lx, ly, PvDraw.COLOR_MUTED)
		val contacts = snapshot.abiphoneContacts
		var undiscovered = undiscovered(snapshot)
		sortW = 0
		if (undiscovered.isNotEmpty()) {
			val sortLabel = if (cheapestFirst) "Missing: Cheapest" else "Missing: Name"
			sortW = font.width(sortLabel)
			sortH = font.lineHeight
			sortX = lx + lw - sortW
			sortY = ly
			val sortHovered = mx >= sortX && mx < sortX + sortW && my >= sortY && my < sortY + sortH
			PvDraw.text(g, font, sortLabel, sortX, sortY, if (sortHovered || cheapestFirst) PvDraw.COLOR_ACCENT else PvDraw.COLOR_MUTED)
			zones.add(HoverZone.of(sortX, sortY, sortW, sortH, listOf(
				PvTooltip.Line.of("Click to change sort", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Name: A to Z", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("Cheapest: lowest cost first, unpriced last", PvDraw.COLOR_MUTED),
			)))
			if (cheapestFirst) {
				undiscovered = cheapestCache
			}
		}
		ly += font.lineHeight + 4
		if (contacts.isEmpty() && undiscovered.isEmpty()) {
			PvDraw.textCentered(g, font, "No contacts", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			abiphoneMaxScroll = 0
			abiphoneW = 0
			abiphoneH = 0
			return
		}

		val gridH = maxOf(1, bottom - ly)
		// Largest slot that fits every contact without scrolling; undiscovered start on a fresh row.
		var slot = 18
		var gap = 2
		var cols = 1
		for (trySlot in 28 downTo 18) {
			val tryGap = if (trySlot >= 24) 4 else if (trySlot >= 21) 3 else 2
			val tryCols = maxOf(1, (lw + tryGap) / (trySlot + tryGap))
			val needH = gridRows(contacts.size, undiscovered.size, tryCols) * (trySlot + tryGap) - tryGap
			slot = trySlot
			gap = tryGap
			cols = tryCols
			if (needH <= gridH) {
				break
			}
		}
		// Stretch gaps so the grid uses the full width.
		val usedW = cols * slot
		val freeW = maxOf(0, lw - usedW)
		val gapX = maxOf(gap, if (cols > 1) freeW / (cols - 1) else 0)
		val contentH = gridRows(contacts.size, undiscovered.size, cols) * (slot + gap) - gap
		abiphoneX = x
		abiphoneY = ly
		abiphoneW = w
		abiphoneH = maxOf(0, bottom - ly)
		abiphoneMaxScroll = maxOf(0, contentH - abiphoneH)
		abiphoneScroll = minOf(abiphoneScroll, abiphoneMaxScroll)

		g.enableScissor(lx, abiphoneY, lx + lw + 3, abiphoneY + abiphoneH)
		for ((i, contact) in contacts.withIndex()) {
			val col = i % cols
			val row = i / cols
			val bx = lx + col * (slot + gapX)
			val by = ly + row * (slot + gap) - abiphoneScroll
			val hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot &&
				my >= abiphoneY && my < abiphoneY + abiphoneH
			PvDraw.fill(g, bx, by, slot, slot, ITEM_SLOT_BG)
			g.outline(bx, by, slot, slot, if (hovered) PvDraw.COLOR_ACCENT else if (contact.active) ITEM_SLOT_BORDER else DISABLED)

			val iconPad = maxOf(0, (slot - 16) / 2)
			drawContactIcon(g, contact.id, bx + iconPad, by + iconPad)
			if (!contact.active) {
				PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000)
			}

			val y0 = maxOf(by, abiphoneY)
			val y1 = minOf(by + slot, abiphoneY + abiphoneH)
			if (y1 > y0) {
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(contact.name, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of(
					if (contact.active) "Active contact" else "Inactive",
					if (contact.active) ENABLED else PvDraw.COLOR_MUTED,
				))
				if (contact.completedQuest) {
					tip.add(PvTooltip.Line.of("Quest complete", ENABLED))
				} else if (contact.talkedTo) {
					tip.add(PvTooltip.Line.of("Talked to", PvDraw.COLOR_MUTED))
				}
				if (contact.incomingCalls > 0) {
					tip.add(PvTooltip.Line.of("Incoming calls: " + FormatUtil.commas(contact.incomingCalls.toLong()), PvDraw.COLOR_MUTED))
				}
				if (contact.dnd) {
					tip.add(PvTooltip.Line.of("Do Not Disturb", PvDraw.COLOR_GOLD))
				}
				zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip))
			}
		}
		val firstRow = (contacts.size + cols - 1) / cols
		for ((i, contact) in undiscovered.withIndex()) {
			val bx = lx + (i % cols) * (slot + gapX)
			val by = ly + (firstRow + i / cols) * (slot + gap) - abiphoneScroll
			val hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot &&
				my >= abiphoneY && my < abiphoneY + abiphoneH
			PvDraw.roundedFill(g, bx, by, slot, slot, 3, UNDISCOVERED_BG)
			PvDraw.roundedRing(g, bx, by, slot, slot, 3, if (hovered) PvDraw.COLOR_ACCENT else UNDISCOVERED_BORDER, PvDraw.C_ALL)
			val iconPad = maxOf(0, (slot - 16) / 2)
			drawNpcIcon(g, contact.neuId, bx + iconPad, by + iconPad)
			PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000)

			val y0 = maxOf(by, abiphoneY)
			val y1 = minOf(by + slot, abiphoneY + abiphoneH)
			if (y1 > y0) {
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(contact.name, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of("Undiscovered", UNDISCOVERED_TEXT))
				for (req in contact.requirement) {
					tip.add(PvTooltip.Line.of(req, PvDraw.COLOR_GOLD))
				}
				val coins = (costCache[contact] ?: AbiphoneCosts.Estimate.UNKNOWN).coins
				tip.add(PvTooltip.Line.blank())
				if (coins == null) {
					tip.add(PvTooltip.Line.meta("Cost unknown"))
				} else if (coins <= 0) {
					tip.add(PvTooltip.Line.of("No coin cost", ENABLED))
				} else {
					tip.add(PvTooltip.Line.of("Est. cost: " + FormatUtil.compactCoins(coins), PvDraw.COLOR_GOLD))
				}
				zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip))
			}
		}
		g.disableScissor()
	}

	private fun undiscovered(snapshot: CrimsonSnapshot): List<AbiphoneNpcs.Undiscovered> {
		// Keep recomputing until the NEU repo and prices are ready; both may still be loading on first draw.
		val now = System.currentTimeMillis()
		val retry = !undiscoveredFinal && now - undiscoveredAtMs >= 1000L
		if (undiscoveredFor !== snapshot || retry) {
			undiscoveredAtMs = now
			val ids = snapshot.abiphoneContacts.map { it.id }
			val missing = AbiphoneNpcs.undiscovered(ids)
			// NEU lists extra entries; the real count of contacts still missing caps the red slots.
			val allowed = maxOf(0, AbiphoneNpcs.TOTAL_CONTACTS - snapshot.abiphoneActive)
			undiscoveredCache = if (missing.size > allowed) missing.subList(0, allowed).toList() else missing
			val costs = IdentityHashMap<AbiphoneNpcs.Undiscovered, AbiphoneCosts.Estimate>()
			for (contact in undiscoveredCache) {
				costs[contact] = AbiphoneCosts.estimate(contact.requirement, { NeuRepoCache.idForDisplayName(it) }, { ItemPricer.knownPrice(it) })
			}
			costCache = costs
			cheapestCache = undiscoveredCache.sortedWith(
				compareBy<AbiphoneNpcs.Undiscovered> { !costs.getValue(it).known() }
					.thenComparingDouble { costs.getValue(it).coins ?: 0.0 }
					.thenComparing(AbiphoneNpcs.Undiscovered::name, String.CASE_INSENSITIVE_ORDER),
			)
			undiscoveredFinal = NeuRepoCache.isReady() && ItemPricer.isReady()
			undiscoveredFor = snapshot
		}
		return undiscoveredCache
	}

	private companion object {
		const val UNDISCOVERED_BG = 0xFF3A1010.toInt()
		const val UNDISCOVERED_BORDER = 0xFFCC3333.toInt()
		const val UNDISCOVERED_TEXT = 0xFFFF5555.toInt()

		val FALLBACK_ICON = ItemStack(Items.VILLAGER_SPAWN_EGG)

		fun gridRows(discovered: Int, undiscovered: Int, cols: Int): Int =
			(discovered + cols - 1) / cols + (undiscovered + cols - 1) / cols

		/** Prefer the custom NEU skin texture, then a vanilla stack; never show blank paper. */
		fun drawContactIcon(g: GuiGraphicsExtractor, contactId: String, x: Int, y: Int) {
			drawNpcIcon(g, AbiphoneNpcs.neuId(contactId), x, y)
		}

		fun drawNpcIcon(g: GuiGraphicsExtractor, neuId: String, x: Int, y: Int) {
			var icon: ItemStack = if (neuId.isBlank()) ItemStack.EMPTY else SkyBlockItemFactory.iconStack(neuId)
			if (neuId.isNotBlank() && SkyBlockIconRenderer.hasKnownIcon(neuId)) {
				SkyBlockIconRenderer.draw(g, icon, neuId, x, y, 16)
				return
			}
			if (icon.isEmpty || icon.`is`(Items.PAPER) || isUntexturedHead(icon)) {
				icon = FALLBACK_ICON
			}
			g.item(icon, x, y)
		}

		fun isUntexturedHead(icon: ItemStack?): Boolean {
			if (icon == null || icon.isEmpty || !icon.`is`(Items.PLAYER_HEAD)) {
				return false
			}
			val profile = icon.get(DataComponents.PROFILE) ?: return true
			try {
				val props = profile.partialProfile().properties().get("textures")
				if (props == null || props.isEmpty()) {
					return true
				}
				for (prop in props) {
					if (prop != null && prop.value() != null && prop.value().isNotBlank()) {
						return false
					}
				}
			} catch (ignored: Exception) {
				return true
			}
			return true
		}
	}
}

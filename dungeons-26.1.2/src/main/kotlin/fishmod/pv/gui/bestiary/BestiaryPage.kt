/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.bestiary

import fishmod.pv.data.BestiaryData
import fishmod.pv.data.BestiarySnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MobNames
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Bestiary left panel: family grid for the selected island/category.
 * Right island buttons live in `ProfileViewerScreen` (inventory split layout).
 */
class BestiaryPage {
	private var snapshot: BestiarySnapshot = BestiarySnapshot.empty()
	private var categoryId = ""
	private var searchQuery = ""
	private var scroll = 0
	private var maxScroll = 0
	private var gridX = 0
	private var gridY = 0
	private var gridW = 0
	private var gridH = 0

	private val iconCache = HashMap<String, ItemStack>()
	private var hoverKey = ""
	private var hoverTip: List<Component> = listOf()
	private var tipFamily: BestiarySnapshot.FamilyProgress? = null
	private var tipCache: List<Component> = listOf()

	fun apply(snapshot: BestiarySnapshot?) {
		this.snapshot = snapshot ?: BestiarySnapshot.empty()
		iconCache.clear()
		scroll = 0
		if (categoryId.isBlank()) {
			val cats = BestiaryData.categories()
			cats.firstOrNull { it.families.isNotEmpty() }?.let { categoryId = it.id }
			if (categoryId.isBlank() && cats.isNotEmpty()) {
				categoryId = cats[0].id
			}
		}
	}

	fun totalUnlockedTiers(): Int = snapshot.totalUnlockedTiers

	fun totalMaxTiers(): Int = snapshot.totalMaxTiers

	fun claimedMilestone(): Int = snapshot.claimedMilestone

	fun milestone(): Int = snapshot.milestone()

	fun lastKilledMob(): String = snapshot.lastKilledMob

	fun setCategory(categoryId: String?) {
		val next = categoryId ?: ""
		if (next != this.categoryId) {
			this.categoryId = next
			scroll = 0
			hoverKey = ""
			hoverTip = listOf()
		}
	}

	fun categoryId(): String = categoryId

	fun setSearchQuery(query: String?) {
		searchQuery = query?.trim()?.lowercase(Locale.ROOT) ?: ""
	}

	fun searchHighlightsCategory(categoryId: String?): Boolean {
		if (searchQuery.isBlank() || categoryId == null) {
			return false
		}
		val progress = snapshot.category(categoryId) ?: return false
		if (progress.families.any { matchesSearch(it) }) {
			return true
		}
		return progress.category.name.lowercase(Locale.ROOT).contains(searchQuery)
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
		hoverKey = ""
		hoverTip = listOf()
		BestiaryData.ensureLoaded()
		var progress = snapshot.category(categoryId)
		if (progress == null && BestiaryData.categories().isNotEmpty()) {
			progress = snapshot.category(BestiaryData.categories()[0].id)
			if (progress != null) {
				categoryId = progress.category.id
			}
		}

		val lx = x + PAD
		var ly = y + PAD
		val lw = maxOf(8, w - PAD * 2)

		// Four stat boxes across the top: category, tiers, milestone, last kill.
		val boxGap = 4
		val boxW = (lw - boxGap * 3) / 4
		val boxH = font.lineHeight * 2 + 12
		val title = progress?.category?.name ?: "Bestiary"
		val famDone = progress?.familiesUnlocked ?: 0
		val famTotal = progress?.families?.size ?: 0
		val tiers = progress?.unlockedTiers ?: 0
		val tiersMax = progress?.maxTiers ?: 0
		for (i in 0 until 4) {
			val bx = lx + i * (boxW + boxGap)
			PvDraw.roundedFill(g, bx, ly, boxW, boxH, 3, PvDraw.CELL)
			PvDraw.roundedRing(g, bx, ly, boxW, boxH, 3, 0x30FFFFFF, PvDraw.C_ALL)
		}
		val tx = 5
		val line1 = ly + 3
		val line2 = line1 + font.lineHeight + 2
		val hdrBarY = ly + boxH - 5

		val b0 = lx
		PvDraw.text(g, font, fit(font, title, boxW - tx * 2), b0 + tx, line1, PvDraw.COLOR_TEXT)
		PvDraw.text(g, font, "$famDone/$famTotal families", b0 + tx, line2, PvDraw.COLOR_MUTED)
		val famPct = if (famTotal <= 0) 0f else famDone / famTotal.toFloat()
		PvDraw.progressBar(g, b0 + tx, hdrBarY, boxW - tx * 2, 3, famPct, 0xFF55FF55.toInt(), famDone >= famTotal && famTotal > 0)

		val b1 = lx + (boxW + boxGap)
		PvDraw.text(g, font, "Tiers", b1 + tx, line1, PvDraw.COLOR_MUTED)
		PvDraw.text(
			g, font, FormatUtil.commas(tiers.toLong()) + " / " + FormatUtil.commas(tiersMax.toLong()),
			b1 + tx, line2, PvDraw.COLOR_TEXT
		)
		val tierPct = if (tiersMax <= 0) 0f else tiers / tiersMax.toFloat()
		PvDraw.progressBar(g, b1 + tx, hdrBarY, boxW - tx * 2, 3, tierPct, PvDraw.COLOR_ACCENT, tiers >= tiersMax && tiersMax > 0)

		val b2 = lx + 2 * (boxW + boxGap)
		PvDraw.text(g, font, "Milestone", b2 + tx, line1, PvDraw.COLOR_MUTED)
		PvDraw.text(
			g, font, FormatUtil.commas(snapshot.milestone().toLong()) + "  +" +
				FormatUtil.commas(snapshot.claimedMilestone.toLong()) + " HP", b2 + tx, line2, PvDraw.COLOR_GOLD
		)

		val b3 = lx + 3 * (boxW + boxGap)
		PvDraw.text(g, font, "Last kill", b3 + tx, line1, PvDraw.COLOR_MUTED)
		val lastKill = if (snapshot.lastKilledMob.isBlank()) "-" else prettyMobId(snapshot.lastKilledMob)
		PvDraw.text(g, font, fit(font, lastKill, boxW - tx * 2), b3 + tx, line2, PvDraw.COLOR_TEXT)
		ly += boxH + 6

		val families = progress?.families ?: listOf()
		gridX = lx
		gridY = ly
		gridW = lw
		gridH = maxOf(0, y + h - PAD - ly)

		if (families.isEmpty()) {
			maxScroll = 0
			PvDraw.textCentered(
				g, font, "No families",
				x + w / 2, gridY + maxOf(0, gridH / 2 - font.lineHeight / 2), PvDraw.COLOR_MUTED
			)
			return
		}

		val cols = maxOf(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP))
		val rows = (families.size + cols - 1) / cols
		val contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP
		maxScroll = maxOf(0, contentH - gridH)
		scroll = minOf(scroll, maxScroll)

		val linked = findLinkedFamily(families, cols, lx, ly, mouseX, mouseY)
		val linkedId = linked?.family()?.id ?: ""

		val rowPitch = SLOT + SLOT_GAP
		val firstRow = maxOf(0, scroll / rowPitch)
		val lastRow = minOf(rows - 1, (scroll + gridH) / rowPitch + 1)
		val first = firstRow * cols
		val last = minOf(families.size - 1, (lastRow + 1) * cols - 1)

		g.enableScissor(lx, gridY, lx + lw + 3, gridY + gridH)
		for (i in first..last) {
			val fam = families[i]
			val col = i % cols
			val row = i / cols
			val bx = lx + col * rowPitch
			val by = ly + row * rowPitch - scroll
			if (by + SLOT < gridY || by > gridY + gridH) {
				continue
			}
			val linkedHover = linkedId.isNotBlank() && linkedId == fam.family().id
			val searchHit = matchesSearch(fam)
			PvDraw.fill(g, bx, by, SLOT, SLOT, slotColor(fam))
			val border = if (searchHit) 0xFF55FF55.toInt() else if (linkedHover) PvDraw.COLOR_ACCENT else 0x00FFFFFF
			g.outline(bx, by, SLOT, SLOT, border)
			val icon = familyIcon(fam)
			if (!icon.isEmpty) {
				val iconScale = (SLOT - 6) / 16f
				g.pose().pushMatrix()
				g.pose().translate((bx + 3).toFloat(), (by + 3).toFloat())
				g.pose().scale(iconScale, iconScale)
				g.item(icon, 0, 0)
				g.pose().popMatrix()
			}
		}
		g.disableScissor()

		if (linked != null) {
			// Rebuild the tooltip only when the hovered family changes.
			if (linked !== tipFamily) {
				tipFamily = linked
				tipCache = tooltipFor(linked)
			}
			hoverKey = linked.family().id
			hoverTip = tipCache
		}
	}

	/** Family under the cursor on the icon grid. */
	private fun findLinkedFamily(
		families: List<BestiarySnapshot.FamilyProgress>,
		cols: Int,
		gridLeft: Int,
		gridTop: Int,
		mouseX: Int,
		mouseY: Int
	): BestiarySnapshot.FamilyProgress? {
		val rowPitch = SLOT + SLOT_GAP
		val overGrid = mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH
		if (!overGrid) {
			return null
		}
		for (i in families.indices) {
			val col = i % cols
			val row = i / cols
			val bx = gridLeft + col * rowPitch
			val by = gridTop + row * rowPitch - scroll
			if (by + SLOT < gridY || by > gridY + gridH) {
				continue
			}
			if (mouseX >= bx && mouseX < bx + SLOT && mouseY >= by && mouseY < by + SLOT) {
				return families[i]
			}
		}
		return null
	}

	private fun tooltipFor(fam: BestiarySnapshot.FamilyProgress): List<Component> {
		val family = fam.family()
		val tip = ArrayList<Component>()
		tip.add(SkyBlockItemFactory.legacyLine("§a" + family.name))
		tip.add(
			SkyBlockItemFactory.legacyLine(
				"§7Tier: §e" + fam.tier + "§7/§e" + fam.tiersMax + (if (fam.maxed) " §a(MAX)" else "")
			)
		)
		if (fam.maxed || fam.nextNeed <= 0L) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Kills: §aMAX"))
		} else {
			tip.add(
				SkyBlockItemFactory.legacyLine(
					"§7Kills: §f" + FormatUtil.commas(fam.intoTier) + "§7/§f" + FormatUtil.commas(fam.nextNeed)
				)
			)
		}
		tip.add(SkyBlockItemFactory.legacyLine("§7Total kills: §f" + FormatUtil.commas(fam.kills)))
		val cap = family.cap.toLong()
		if (fam.maxed && cap > 0 && fam.kills > cap) {
			tip.add(SkyBlockItemFactory.legacyLine("§8+" + FormatUtil.commas(fam.kills - cap) + " past final milestone"))
		}
		if (fam.deaths > 0) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Deaths to family: §c" + FormatUtil.commas(fam.deaths)))
		}
		if (family.mobIds.isNotEmpty()) {
			tip.add(Component.empty())
			tip.add(SkyBlockItemFactory.legacyLine("§7Mobs:"))
			var shown = 0
			for (mob in family.mobIds) {
				if (shown >= 8) {
					tip.add(SkyBlockItemFactory.legacyLine("§8…" + (family.mobIds.size - shown) + " more"))
					break
				}
				tip.add(SkyBlockItemFactory.legacyLine("§8• §7" + prettyMobId(mob)))
				shown++
			}
		}
		return tip
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (gridW <= 0 || gridH <= 0) {
			return false
		}
		if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) {
			return false
		}
		if (maxScroll <= 0) {
			return true
		}
		val delta = if (scrollY > 0) -SCROLL_STEP else SCROLL_STEP
		scroll = maxOf(0, minOf(maxScroll, scroll + delta))
		return true
	}

	private fun matchesSearch(fam: BestiarySnapshot.FamilyProgress?): Boolean {
		if (searchQuery.isBlank() || fam == null) {
			return false
		}
		val family = fam.family()
		if (family.name.lowercase(Locale.ROOT).contains(searchQuery)) {
			return true
		}
		if (prettyMobId(family.name).lowercase(Locale.ROOT).contains(searchQuery)) {
			return true
		}
		for (mob in family.mobIds) {
			if (mob.lowercase(Locale.ROOT).contains(searchQuery)) {
				return true
			}
			if (prettyMobId(mob).lowercase(Locale.ROOT).contains(searchQuery)) {
				return true
			}
		}
		return false
	}

	fun categoryIcon(categoryId: String?): ItemStack {
		val key = "cat:" + (categoryId ?: "")
		iconCache[key]?.let { return it }
		val cat = BestiaryData.category(categoryId)
		var icon = ItemStack.EMPTY
		if (cat != null) {
			if (cat.textureValue.isNotBlank()) {
				icon = SkyBlockItemFactory.texturedHead(cat.textureValue)
			}
			if (icon.isEmpty && cat.itemIcon.isNotBlank()) {
				icon = vanillaItem(cat.itemIcon)
			}
		}
		if (icon.isEmpty) {
			icon = ItemStack(Items.MAP)
		}
		iconCache[key] = icon
		return icon
	}

	private fun familyIcon(fam: BestiarySnapshot.FamilyProgress): ItemStack {
		val family = fam.family()
		val id = family.id
		iconCache[id]?.let { return it }
		var icon = ItemStack.EMPTY
		if (family.textureValue.isNotBlank()) {
			icon = SkyBlockItemFactory.texturedHead(family.textureValue)
		}
		if (icon.isEmpty && family.itemIcon.isNotBlank()) {
			icon = vanillaItem(family.itemIcon)
		}
		if (icon.isEmpty) {
			icon = ItemStack(Items.SPAWNER)
		}
		iconCache[id] = icon
		return icon
	}

	/** Draw after island buttons / frame tabs so tips paint on top. */
	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (hoverTip.isNotEmpty()) {
			PvTooltip.drawComponents(g, font, hoverTip, mouseX, mouseY, screenW, screenH)
		}
	}

	companion object {
		private const val PAD = 6
		private const val SLOT = 32
		private const val SLOT_GAP = 4
		private const val SCROLL_STEP = (SLOT + SLOT_GAP) * 3

		private val FORMAT_CODE = Regex("§[0-9a-fk-or]")
		private val LEVEL_SUFFIX = Regex("(?i)_\\d+$")

		private fun fit(font: Font, text: String, maxW: Int): String {
			if (font.width(text) <= maxW) {
				return text
			}
			var shown = text
			while (shown.length > 1 && font.width("$shown...") > maxW) {
				shown = shown.substring(0, shown.length - 1)
			}
			return "$shown..."
		}

		/** Slot background: green when maxed, yellow when started, gray when untouched. */
		private fun slotColor(fam: BestiarySnapshot.FamilyProgress?): Int {
			if (fam == null || fam.tier <= 0) {
				return 0xFF2A2A32.toInt()
			}
			if (fam.maxed || (fam.tiersMax > 0 && fam.tier >= fam.tiersMax)) {
				return 0xFF1E5A2A.toInt()
			}
			return 0xFF8A4A10.toInt()
		}

		/** `bogged_10` / `magma_cube_rider_90` → readable title case, level suffix dropped. */
		@JvmStatic
		fun prettyMobId(id: String?): String {
			if (id.isNullOrBlank()) {
				return ""
			}
			var cleaned = FORMAT_CODE.replace(id.trim(), "")
			cleaned = LEVEL_SUFFIX.replace(cleaned, "")
			return MobNames.pretty(cleaned)
		}

		private fun vanillaItem(name: String): ItemStack {
			val itemId = Identifier.tryParse(name.lowercase(Locale.ROOT).replace(' ', '_')) ?: return ItemStack.EMPTY
			return BuiltInRegistries.ITEM.getOptional(itemId).map { ItemStack(it) }.orElse(ItemStack.EMPTY)
		}
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page

import fishmod.pv.data.CollectionSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MinionCosts
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.collections.CollectionsUi
import fishmod.pv.gui.collections.CollectionsUi.GAP
import fishmod.pv.gui.collections.CollectionsUi.ITEM_ICON
import fishmod.pv.gui.collections.CollectionsUi.MINION_CROSS
import fishmod.pv.gui.collections.CollectionsUi.MINION_LOCKED
import fishmod.pv.gui.collections.CollectionsUi.MINION_TICK
import fishmod.pv.gui.collections.CollectionsUi.MINION_UNLOCKED
import fishmod.pv.gui.collections.CollectionsUi.PAD
import fishmod.pv.gui.collections.CollectionsUi.SLOT
import fishmod.pv.gui.collections.CollectionsUi.SLOT_GAP
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelCollectionsCache
import fishmod.pv.price.ItemPricer
import java.util.EnumMap
import java.util.function.Function
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class MinionsPage {
	private var selectedMinion = 0
	private var minionScroll = 0
	private var minionMaxScroll = 0
	private var tierScroll = 0
	private var tierMaxScroll = 0
	private var minionTop = 0
	private var minionH = 0
	private var tierListTop = 0
	private var tierListH = 0
	private var cheapestMode = false
	private var toggleX = 0
	private var toggleY = 0
	private var toggleW = 0
	private var toggleH = 0
	private val minionHits = ArrayList<SlotHit>()
	private var hoverTip: List<PvTooltip.Line> = listOf()

	private var costsFor: CollectionSnapshot? = null
	private var costsFinal = false
	private var costsAt = 0L
	private val missingById = HashMap<String, List<MinionCosts.TierCost>>()
	private val indexById = HashMap<String, Int>()
	private var cheapest: List<MinionCosts.MissingTier> = listOf()
	private var groups: Map<MinionCosts.Category, List<Int>> = mapOf()

	fun reset(snapshot: CollectionSnapshot?) {
		selectedMinion = if (snapshot == null || snapshot.minions.isEmpty()) -1 else 0
		minionScroll = 0
		tierScroll = 0
		costsFor = null
		hoverTip = listOf()
	}

	fun render(
		snapshot: CollectionSnapshot,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		minionHits.clear()
		hoverTip = listOf()
		ensureCosts(snapshot)
		renderMinions(snapshot, g, font, x, y, w, h, mouseX, mouseY)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (hoverTip.isNotEmpty()) {
			PvTooltip.drawStyled(g, font, hoverTip, mouseX, mouseY, screenW, screenH)
		}
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx >= toggleX && mx < toggleX + toggleW && my >= toggleY && my < toggleY + toggleH) {
			cheapestMode = !cheapestMode
			minionScroll = 0
			return true
		}
		for (hit in minionHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				selectedMinion = hit.index
				tierScroll = 0
				return true
			}
		}
		return false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		val step = SLOT + SLOT_GAP
		if (mouseY >= minionTop && mouseY < minionTop + minionH) {
			return CollectionsUi.scrollBy(scrollY, minionScroll, minionMaxScroll, step) { minionScroll = it }
		}
		if (mouseY >= tierListTop && mouseY < tierListTop + tierListH) {
			return CollectionsUi.scrollBy(scrollY, tierScroll, tierMaxScroll, 12) { tierScroll = it }
		}
		return false
	}

	private fun ensureCosts(snapshot: CollectionSnapshot?) {
		val now = System.currentTimeMillis()
		if (snapshot === costsFor && (costsFinal || now - costsAt < COST_RETRY_MS)) {
			return
		}
		costsFor = snapshot
		costsAt = now
		costsFinal = ItemPricer.isReady() && NeuRepoCache.isReady() && HypixelCollectionsCache.isReady()
		missingById.clear()
		indexById.clear()
		val minions = snapshot?.minions ?: listOf()
		val byCategory = EnumMap<MinionCosts.Category, MutableList<Int>>(MinionCosts.Category::class.java)
		for ((i, minion) in minions.withIndex()) {
			byCategory.getOrPut(MinionCosts.categoryOf(minion.id)) { ArrayList() }.add(i)
			missingById[minion.id] = MinionCosts.missingTiers(minion)
			indexById[minion.id] = i
		}
		groups = byCategory
		cheapest = MinionCosts.cheapestNext(minions)
	}

	private fun renderMinions(
		snapshot: CollectionSnapshot,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		val rightW = maxOf(96, minOf(118, Math.round(w * 0.24).toInt()))
		val leftW = w - rightW - GAP
		val rightX = x + leftW + GAP

		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, rightX, y, rightW, h)

		val minions = snapshot.minions
		PvDraw.text(g, font, Component.translatable("fishmod.pv.collections.minions").string, x + PAD, y + PAD, PvDraw.COLOR_TEXT)
		minionTop = y + PAD + font.lineHeight + 4
		minionH = maxOf(20, y + h - PAD - minionTop)

		if (minions.isEmpty()) {
			toggleW = 0
			PvDraw.textCentered(
				g, font,
				Component.translatable("fishmod.pv.collections.minions_empty").string,
				x + leftW / 2, y + h / 2, PvDraw.COLOR_MUTED,
			)
			minionMaxScroll = 0
			selectedMinion = -1
			drawMinionDetail(g, font, null, rightX, y, rightW, h, mouseX, mouseY)
			return
		}
		selectedMinion = maxOf(0, minOf(selectedMinion, minions.size - 1))

		val toggle = Component.translatable("fishmod.pv.collections.minions_cheapest").string
		toggleW = font.width(toggle)
		toggleH = font.lineHeight
		toggleX = x + leftW - PAD - toggleW
		toggleY = y + PAD
		val toggleHover = mouseX >= toggleX && mouseX < toggleX + toggleW && mouseY >= toggleY && mouseY < toggleY + toggleH
		PvDraw.text(
			g, font, toggle, toggleX, toggleY,
			if (cheapestMode) PvDraw.COLOR_ACCENT else if (toggleHover) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED,
		)

		val listX = x + PAD
		val listW = leftW - PAD * 2
		if (cheapestMode) {
			renderCheapest(g, font, listX, listW, mouseX, mouseY)
		} else {
			renderGrid(minions, g, font, listX, listW, mouseX, mouseY)
		}

		drawMinionDetail(g, font, minions[selectedMinion], rightX, y, rightW, h, mouseX, mouseY)
	}

	private fun renderGrid(
		minions: List<CollectionSnapshot.MinionEntry>,
		g: GuiGraphicsExtractor,
		font: Font,
		listX: Int,
		listW: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		val slot = SLOT
		val minGap = 5
		val maxGap = 14
		val cols = maxOf(1, (listW + minGap) / (slot + minGap))
		val gapX = if (cols <= 1) 0 else maxOf(minGap, minOf(maxGap, (listW - cols * slot) / (cols - 1)))
		val gapY = minGap
		val gridW = cols * slot + maxOf(0, cols - 1) * gapX
		val startX = listX + maxOf(0, (listW - gridW) / 2)
		val headerH = font.lineHeight + 3
		val groupGap = 4

		var contentH = 0
		for (members in groups.values) {
			val rows = (members.size + cols - 1) / cols
			contentH += headerH + rows * slot + maxOf(0, rows - 1) * gapY + groupGap
		}
		contentH = maxOf(0, contentH - groupGap)
		minionMaxScroll = maxOf(0, contentH - minionH)
		minionScroll = maxOf(0, minOf(minionScroll, minionMaxScroll))
		val inList = mouseY >= minionTop && mouseY < minionTop + minionH

		g.enableScissor(listX, minionTop, listX + listW, minionTop + minionH)
		var cy = minionTop - minionScroll
		for ((category, members) in groups) {
			val maxed = members.count { isMaxed(minions[it]) }
			val label = category.label() + " " + maxed + "/" + members.size
			PvDraw.sectionHeader(g, font, label, listX, cy, listW)
			cy += headerH
			for ((n, index) in members.withIndex()) {
				val sx = startX + (n % cols) * (slot + gapX)
				val sy = cy + (n / cols) * (slot + gapY)
				if (sy + slot < minionTop || sy > minionTop + minionH) {
					continue
				}
				val minion = minions[index]
				val selected = index == selectedMinion
				val hover = inList && mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot
				PvDraw.roundedFill(g, sx, sy, slot, slot, 3, if (selected) 0x50386CC8 else if (hover) 0x24FFFFFF else PvDraw.CELL)
				PvDraw.roundedRing(
					g, sx, sy, slot, slot, 3,
					if (selected) PvDraw.COLOR_ACCENT else if (hover) 0xFF4A4A5A.toInt() else 0x30FFFFFF,
					PvDraw.C_ALL,
				)
				CollectionsUi.drawIcon(g, minion.iconId(), sx + (slot - ITEM_ICON) / 2, sy + (slot - ITEM_ICON) / 2 - 1)
				PvDraw.textRight(
					g, font,
					minion.maxCrafted().toString(),
					sx + slot - 1, sy + slot - font.lineHeight + 1,
					if (isMaxed(minion)) PvDraw.COLOR_GOLD else MINION_LOCKED,
				)
				if (inList) {
					minionHits.add(SlotHit(sx, sy, slot, slot, index))
				}
				if (hover) {
					hoverTip = minionTip(minion)
				}
			}
			val rows = (members.size + cols - 1) / cols
			cy += rows * slot + maxOf(0, rows - 1) * gapY + groupGap
		}
		g.disableScissor()
	}

	private fun renderCheapest(
		g: GuiGraphicsExtractor,
		font: Font,
		listX: Int,
		listW: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		if (cheapest.isEmpty()) {
			minionMaxScroll = 0
			PvDraw.textCentered(
				g, font,
				Component.translatable("fishmod.pv.collections.minions_all_crafted").string,
				listX + listW / 2, minionTop + minionH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED,
			)
			return
		}
		val rowH = maxOf(ITEM_ICON, font.lineHeight) + 2
		minionMaxScroll = maxOf(0, cheapest.size * rowH - minionH)
		minionScroll = maxOf(0, minOf(minionScroll, minionMaxScroll))
		val inList = mouseY >= minionTop && mouseY < minionTop + minionH

		g.enableScissor(listX, minionTop, listX + listW, minionTop + minionH)
		var rowY = minionTop - minionScroll
		for (row in cheapest) {
			if (rowY + rowH >= minionTop && rowY <= minionTop + minionH) {
				val index = indexById[row.minionId]
				val selected = index != null && index == selectedMinion
				val hover = inList && mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + rowH
				if (selected || hover) {
					PvDraw.fill(g, listX, rowY, listW, rowH, if (selected) 0x50386CC8 else 0x24FFFFFF)
				}
				val align = PvDraw.IconTextAlign.of(rowY, rowH, ITEM_ICON, font.lineHeight)
				val tierId = row.minionId + "_" + row.cost.tier
				CollectionsUi.drawIcon(g, tierId, listX + 1, align.iconY)
				val cost = costText(row.cost)
				val costW = font.width(cost)
				PvDraw.textRight(g, font, cost, listX + listW - 2, align.textY, costColor(row.cost))
				val nameX = listX + ITEM_ICON + 5
				PvDraw.text(
					g, font, CollectionsUi.trim(font, tierName(row.minionId, row.cost.tier), listX + listW - 2 - costW - 6 - nameX),
					nameX, align.textY, PvDraw.COLOR_TEXT,
				)
				if (inList && index != null) {
					minionHits.add(SlotHit(listX, rowY, listW, rowH, index))
				}
				if (hover) {
					hoverTip = tierTip(row.minionId, row.cost)
				}
			}
			rowY += rowH
		}
		g.disableScissor()
	}

	private fun drawMinionDetail(
		g: GuiGraphicsExtractor,
		font: Font,
		minion: CollectionSnapshot.MinionEntry?,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		val cx = x + PAD
		var cy = y + PAD
		val contentW = w - PAD * 2
		if (minion == null) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("fishmod.pv.collections.select_minion").string,
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED,
			)
			tierListTop = cy
			tierListH = 0
			tierMaxScroll = 0
			return
		}

		CollectionsUi.drawIcon(g, minion.iconId(), cx, cy)
		PvDraw.text(
			g, font,
			CollectionsUi.trim(font, minion.displayName, contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT,
		)
		cy += ITEM_ICON + 4
		val maxed = isMaxed(minion)
		PvDraw.text(g, font, "T" + minion.maxCrafted() + " / " + minion.tierCap, cx, cy, if (maxed) PvDraw.COLOR_GOLD else MINION_LOCKED)
		cy += font.lineHeight + 2
		PvDraw.text(
			g, font,
			Component.translatable("fishmod.pv.collections.minions_shared").string,
			cx, cy,
			PvDraw.COLOR_MUTED,
		)
		cy += font.lineHeight + 3

		val missing = missingById[minion.id] ?: listOf()
		if (missing.isNotEmpty()) {
			val remainingLabel = Component.translatable("fishmod.pv.collections.minions_remaining").string
			val remaining = totalText(missing)
			PvDraw.text(g, font, CollectionsUi.trim(font, remainingLabel, contentW - font.width(remaining) - 4), cx, cy, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, remaining, cx + contentW, cy, if (totalComplete(missing)) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED)
			if (mouseX >= cx && mouseX < cx + contentW && mouseY >= cy && mouseY < cy + font.lineHeight) {
				hoverTip = remainingTip(minion, missing)
			}
			cy += font.lineHeight + 3
		}

		PvDraw.text(g, font, Component.translatable("fishmod.pv.collections.tiers").string, cx, cy, PvDraw.COLOR_MUTED)
		cy += font.lineHeight + 2
		tierListTop = cy
		tierListH = maxOf(20, y + h - PAD - tierListTop)

		val costByTier = missing.associateBy { it.tier }
		val tiers = maxOf(1, minion.tierCap)
		val naturalRow = font.lineHeight + 1
		val fitRow = maxOf(8, tierListH / tiers)
		val rowH = minOf(naturalRow, fitRow)
		val tierScale = if (rowH < font.lineHeight) rowH / font.lineHeight.toFloat() else 1.0f
		tierMaxScroll = maxOf(0, tiers * rowH - tierListH)
		tierScroll = maxOf(0, minOf(tierScroll, tierMaxScroll))
		val inTiers = mouseX >= cx && mouseX < cx + contentW && mouseY >= tierListTop && mouseY < tierListTop + tierListH

		g.enableScissor(cx, tierListTop, cx + contentW, tierListTop + tierListH)
		var rowY = tierListTop - tierScroll
		for (t in 1..tiers) {
			if (rowY + rowH >= tierListTop && rowY <= tierListTop + tierListH) {
				val crafted = minion.crafted(t)
				val color = if (crafted) MINION_UNLOCKED else MINION_LOCKED
				val label = "T$t"
				val cost = if (crafted) null else costByTier[t]
				if (tierScale >= 0.99f) {
					PvDraw.text(g, font, label, cx, rowY, color)
					if (cost != null) {
						PvDraw.textRight(g, font, costText(cost), cx + contentW, rowY, costColor(cost))
					} else {
						val mark = PvDraw.styled(if (crafted) MINION_TICK else MINION_CROSS, color, true)
						PvDraw.text(g, font, mark, cx + contentW - PvDraw.width(font, mark), rowY)
					}
				} else {
					PvDraw.textScaled(g, font, label, cx, rowY, color, tierScale)
					if (cost != null) {
						val text = costText(cost)
						val tw = maxOf(1, (font.width(text) * tierScale).roundToInt())
						PvDraw.textScaled(g, font, text, cx + contentW - tw, rowY, costColor(cost), tierScale)
					} else {
						val mark = if (crafted) MINION_TICK else MINION_CROSS
						val tw = maxOf(1, (PvDraw.widthBold(font, mark) * tierScale).roundToInt())
						PvDraw.textScaled(g, font, mark, cx + contentW - tw, rowY, color, tierScale)
					}
				}
				if (cost != null && inTiers && mouseY >= rowY && mouseY < rowY + rowH) {
					hoverTip = tierTip(minion.id, cost)
				}
			}
			rowY += rowH
		}
		g.disableScissor()
	}

	private fun minionTip(minion: CollectionSnapshot.MinionEntry): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.bold(minion.displayName, PvDraw.COLOR_GOLD))
		lines.add(PvTooltip.Line.row(
			"Tier", PvDraw.COLOR_MUTED,
			minion.maxCrafted().toString() + " / " + minion.tierCap, if (isMaxed(minion)) MINION_UNLOCKED else PvDraw.COLOR_TEXT,
		))
		val missing = missingById[minion.id] ?: listOf()
		if (missing.isNotEmpty()) {
			lines.add(PvTooltip.Line.row(
				Component.translatable("fishmod.pv.collections.minions_remaining").string, PvDraw.COLOR_MUTED,
				totalText(missing), if (totalComplete(missing)) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED,
			))
		}
		return lines
	}

	private fun remainingTip(minion: CollectionSnapshot.MinionEntry, missing: List<MinionCosts.TierCost>): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.bold(minion.displayName, PvDraw.COLOR_GOLD))
		for (cost in missing) {
			lines.add(PvTooltip.Line.row("T" + cost.tier, PvDraw.COLOR_MUTED, costText(cost), costColor(cost)))
		}
		lines.add(PvTooltip.Line.blank())
		lines.add(PvTooltip.Line.of(Component.translatable("fishmod.pv.collections.minions_cost_note").string, PvDraw.COLOR_MUTED))
		return lines
	}

	private class SlotHit(val x: Int, val y: Int, val w: Int, val h: Int, val index: Int)

	companion object {
		private const val COST_RETRY_MS = 2_000L

		private fun tierTip(minionId: String, cost: MinionCosts.TierCost): List<PvTooltip.Line> = tierTipLines(
			tierName(minionId, cost.tier),
			cost,
			MinionCosts.ingredientsFor(minionId, cost.tier),
			{ itemName(it) },
			{ itemColor(it) },
			{ itemIcon(it) },
			{ id -> if (cost.unpriced.contains(id)) null else ItemPricer.knownPrice(id) },
			Component.translatable("fishmod.pv.collections.minions_unpriced").string,
			Component.translatable("fishmod.pv.collections.minions_no_recipe").string,
		)

		@JvmStatic
		fun tierTipLines(
			title: String,
			cost: MinionCosts.TierCost,
			ingredients: Map<String, Int>,
			names: Function<String, String>,
			colors: Function<String, Int?>,
			icons: Function<String, ItemStack?>,
			prices: Function<String, Double?>,
			unpricedLabel: String,
			noRecipeLabel: String,
		): List<PvTooltip.Line> {
			val lines = ArrayList<PvTooltip.Line>()
			lines.add(PvTooltip.Line.bold(title, PvDraw.COLOR_GOLD))
			if (cost.noRecipe) {
				lines.add(PvTooltip.Line.of(noRecipeLabel, PvDraw.COLOR_MUTED))
				return lines
			}
			lines.add(PvTooltip.Line.blank())
			for ((id, count) in ingredients) {
				val color = colors.apply(id)
				val left = listOf(
					PvTooltip.Span.of("${count}x ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(names.apply(id), color ?: PvDraw.COLOR_TEXT),
				)
				val unit = prices.apply(id)
				val row = PvTooltip.Line.row(
					left,
					listOf(
						if (unit == null || unit <= 0) {
							PvTooltip.Span.of(unpricedLabel, PvDraw.COLOR_MUTED)
						} else {
							PvTooltip.Span.of(FormatUtil.shortCoins(unit * count), PvDraw.COLOR_GOLD)
						},
					),
				)
				val icon = icons.apply(id)
				lines.add(if (icon == null) row else row.withIcon(icon))
			}
			lines.add(PvTooltip.Line.blank())
			val unknown = !cost.complete() && cost.coins <= 0
			lines.add(PvTooltip.Line.row(
				listOf(PvTooltip.Span.of("Craft cost:", PvDraw.COLOR_MUTED)),
				listOf(PvTooltip.Span.bold(if (unknown) "Unknown" else costText(cost), costColor(cost))),
			))
			if (!cost.complete() && !unknown) {
				lines.add(PvTooltip.Line.meta("Excludes materials with no price"))
			}
			return lines
		}

		private fun costText(cost: MinionCosts.TierCost): String {
			if (cost.noRecipe) {
				return "?"
			}
			return FormatUtil.shortCoins(cost.coins) + if (cost.complete()) "" else "+"
		}

		private fun costColor(cost: MinionCosts.TierCost): Int = if (cost.complete()) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED

		private fun totalText(missing: List<MinionCosts.TierCost>): String {
			val priced = missing.filter { !it.noRecipe }
			if (priced.isEmpty()) {
				return "?"
			}
			return FormatUtil.shortCoins(priced.sumOf { it.coins }) + if (totalComplete(missing)) "" else "+"
		}

		private fun totalComplete(missing: List<MinionCosts.TierCost>): Boolean = missing.all { it.complete() }

		private fun tierName(minionId: String, tier: Int): String {
			val name: String? = SkyBlockItemFactory.plainDisplayName(minionId + "_" + tier)
			return if (name.isNullOrBlank()) "$minionId $tier" else name
		}

		private fun itemColor(id: String): Int =
			SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(id.replace(':', '-')))

		private fun itemIcon(id: String): ItemStack? {
			val icon: ItemStack = SkyBlockItemFactory.iconStack(id.replace(':', '-'))
			return if (icon.isEmpty || icon.`is`(Items.PAPER)) null else icon
		}

		private fun itemName(id: String): String {
			val name: String? = SkyBlockItemFactory.plainDisplayName(id.replace(':', '-'))
			return if (name.isNullOrBlank()) id else name
		}

		private fun isMaxed(minion: CollectionSnapshot.MinionEntry): Boolean =
			minion.maxCrafted() >= minion.tierCap && minion.tierCap > 0
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections

import fishmod.pv.data.BossCollections
import fishmod.pv.data.CollectionIds
import fishmod.pv.data.CollectionSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.collections.page.CollectionsListPage
import fishmod.pv.gui.collections.page.MinionsPage
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.PvSubTab
import fishmod.pv.price.HypixelCollectionsCache
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class CollectionsPage {
	private var snapshot: CollectionSnapshot = CollectionSnapshot.empty()
	private val list = CollectionsListPage()
	private val minions = MinionsPage()
	private var lastSub: PvSubTab? = null
	private var showMinions = false
	private val stripHits = ArrayList<IntArray>()
	private var stripTip: Component? = null
	private var stripTipX = 0
	private var stripTipY = 0

	private val currentSub: PvSubTab
		get() = if (showMinions) PvSubTab.COLLECTIONS_MINIONS else PvSubTab.COLLECTIONS_LIST

	fun apply(snapshot: CollectionSnapshot?) {
		this.snapshot = snapshot ?: CollectionSnapshot.empty()
		list.reset(this.snapshot)
		minions.reset(this.snapshot)
		prefetchIcons()
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		@Suppress("UNUSED_PARAMETER") sub: PvSubTab?,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int,
	) {
		snapshot.requestMemberRanks()
		val stripH = drawStrip(g, x, y, w, mouseX, mouseY)
		val top = y + stripH + 4
		val height = h - (stripH + 4)
		val current = currentSub
		if (current != lastSub) {
			list.closeDetail()
		}
		lastSub = current
		if (current == PvSubTab.COLLECTIONS_MINIONS) {
			minions.render(snapshot, g, font, x, top, w, height, mouseX, mouseY)
		} else {
			list.render(snapshot, g, font, x, top, w, height, mouseX, mouseY, screenW, screenH)
		}
	}

	fun categories(): List<HypixelCollectionsCache.Category> = snapshot.categories

	fun setCategory(index: Int) {
		list.setCategory(index)
	}

	fun category(): Int = list.category()

	private fun drawStrip(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int): Int {
		stripHits.clear()
		stripTip = null
		val cats = snapshot.categories
		val btn = 18
		val gap = 2
		val count = cats.size + 1
		val perRow = maxOf(1, (w + gap) / (btn + gap))
		val rows = (count + perRow - 1) / perRow
		for (i in 0 until count) {
			val isMinions = i == cats.size
			val bx = x + (i % perRow) * (btn + gap)
			val by = y + (i / perRow) * (btn + gap)
			val selected = if (isMinions) showMinions else !showMinions && i == list.category()
			val hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn
			PvDraw.roundedFill(g, bx, by, btn, btn, 3, if (selected) 0x50386CC8 else if (hovered) 0x24FFFFFF else PvDraw.CELL)
			PvDraw.roundedRing(g, bx, by, btn, btn, 3, if (selected) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER, PvDraw.C_ALL)
			val icon = if (isMinions) PvSubTab.COLLECTIONS_MINIONS.icon() else CollectionCategoryIcons.icon(cats[i].id)
			if (!icon.isEmpty) {
				g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2)
			}
			stripHits.add(intArrayOf(bx, by, btn, i))
			if (hovered) {
				stripTip = if (isMinions) PvSubTab.COLLECTIONS_MINIONS.label() else Component.literal(cats[i].name)
				stripTipX = bx + btn / 2
				stripTipY = by + btn + 14
			}
		}
		return rows * btn + maxOf(0, rows - 1) * gap
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		stripTip?.let {
			PvTooltip.drawComponents(g, font, listOf(it), stripTipX, stripTipY, screenW, screenH)
			return
		}
		if (lastSub == PvSubTab.COLLECTIONS_MINIONS) {
			minions.renderTooltip(g, font, mouseX, mouseY, screenW, screenH)
		} else {
			list.renderTooltip(g, font, mouseX, mouseY, screenW, screenH)
		}
	}

	fun renderOverlay(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		if (lastSub != PvSubTab.COLLECTIONS_MINIONS) {
			list.renderOverlay(g, font, screenW, screenH, mouseX, mouseY)
		}
	}

	fun keyPressed(key: Int, @Suppress("UNUSED_PARAMETER") sub: PvSubTab?): Boolean =
		currentSub != PvSubTab.COLLECTIONS_MINIONS && list.keyPressed(key)

	fun mouseClicked(mx: Double, my: Double, @Suppress("UNUSED_PARAMETER") sub: PvSubTab?): Boolean {
		for (hit in stripHits) {
			if (mx >= hit[0] && mx < hit[0] + hit[2] && my >= hit[1] && my < hit[1] + hit[2]) {
				if (hit[3] == snapshot.categories.size) {
					showMinions = true
				} else {
					showMinions = false
					list.setCategory(hit[3])
				}
				return true
			}
		}
		if (currentSub == PvSubTab.COLLECTIONS_MINIONS) {
			return minions.mouseClicked(mx, my)
		}
		return list.mouseClicked(mx, my)
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, @Suppress("UNUSED_PARAMETER") sub: PvSubTab?): Boolean {
		if (currentSub == PvSubTab.COLLECTIONS_MINIONS) {
			return minions.mouseScrolled(mouseX, mouseY, scrollY)
		}
		return list.mouseScrolled(mouseX, mouseY, scrollY)
	}

	private fun prefetchIcons() {
		val ids = ArrayList<String>()
		for (category in snapshot.categories) {
			for (item in category.items) {
				ids.add(CollectionsUi.resolveIconId(item.id))
				ids.add(CollectionIds.iconId(item.id))
				ids.addAll(CollectionIds.lookupKeys(item.id))
			}
		}
		for (boss in BossCollections.bosses()) {
			ids.add(boss.iconId)
		}
		for (minion in snapshot.minions) {
			ids.add(minion.iconId())
			ids.add(minion.id + "_1")
			if (minion.maxCrafted() < minion.tierCap) {
				ids.add(minion.id + "_" + (minion.maxCrafted() + 1))
			}
		}
		SkyBlockItemFactory.prefetchIds(ids)
	}
}

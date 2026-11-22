/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift

import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.PvSubTab
import fishmod.pv.gui.rift.page.RiftInventoryPage
import fishmod.pv.gui.rift.page.RiftOverviewPage
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class RiftPage {
	private var snapshot: RiftSnapshot = RiftSnapshot.empty()
	private val ui = RiftUi()
	private val overview = RiftOverviewPage()
	private val inventory = RiftInventoryPage()

	fun apply(snapshot: RiftSnapshot?) {
		this.snapshot = snapshot ?: RiftSnapshot.empty()
		inventory.resetPage()
		overview.resetScroll()
		ui.clear()
		SkyBlockItemFactory.prefetch(toWarmSnapshot(this.snapshot))
		SkyBlockItemFactory.prefetchIds(this.snapshot.timecharms.map { it.itemId })
	}

	fun snapshot(): RiftSnapshot = snapshot

	fun mouseClicked(mx: Double, my: Double, sub: PvSubTab?): Boolean {
		if (sub != PvSubTab.RIFT_INVENTORY && overview.mouseClicked(mx, my)) {
			return true
		}
		return ui.mouseClicked(mx, my)
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, sub: PvSubTab?): Boolean {
		if (sub != PvSubTab.RIFT_INVENTORY) {
			return overview.mouseScrolled(mouseX, mouseY, scrollY)
		}
		return inventory.mouseScrolled(mouseX, mouseY, scrollY, snapshot)
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, sub: PvSubTab?,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int, @Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		ui.clear()
		if (sub == PvSubTab.RIFT_INVENTORY) {
			inventory.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
		} else {
			overview.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		ui.drawHover(g, font, mouseX, mouseY, screenW, screenH)
	}

	private companion object {
		fun toWarmSnapshot(rift: RiftSnapshot): InventorySnapshot = InventorySnapshot(
			rift.inventory,
			rift.enderPages,
			listOf(),
			listOf(),
			listOf(),
			listOf(),
			listOf(),
			InventorySnapshot.emptyPage("Fishing Bag", 9),
			InventorySnapshot.emptyPage("Potion Bag", 9),
			InventorySnapshot.emptyPage("Quiver", 9),
			listOf(),
			InventorySnapshot.AccessoryInfo.empty(),
			InventorySnapshot.emptyPage("Time Pocket", 9),
			InventorySnapshot.emptyPage("Personal Vault", 9),
			InventorySnapshot.emptyPage("Carnival Masks", 9),
			false,
			InventorySnapshot.emptyPage("Candy Bag", 9),
			false,
		)
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift;

import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.nav.PvSubTab;
import fishmod.pv.gui.rift.page.RiftInventoryPage;
import fishmod.pv.gui.rift.page.RiftOverviewPage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Rift: Overview progress + side-by-side Inventory / Ender Chest. */
public final class RiftPage {
	private RiftSnapshot snapshot = RiftSnapshot.empty();
	private final RiftUi ui = new RiftUi();
	private final RiftOverviewPage overview = new RiftOverviewPage();
	private final RiftInventoryPage inventory = new RiftInventoryPage();

	public void apply(RiftSnapshot snapshot) {
		this.snapshot = snapshot == null ? RiftSnapshot.empty() : snapshot;
		this.inventory.resetPage();
		this.overview.resetScroll();
		this.ui.clear();
		SkyBlockItemFactory.prefetch(toWarmSnapshot(this.snapshot));
		List<String> charmIds = new ArrayList<>();
		for (RiftSnapshot.Timecharm charm : this.snapshot.timecharms()) {
			charmIds.add(charm.itemId());
		}
		SkyBlockItemFactory.prefetchIds(charmIds);
	}

	public RiftSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my, PvSubTab sub) {
		if (sub != PvSubTab.RIFT_INVENTORY && this.overview.mouseClicked(mx, my)) {
			return true;
		}
		return this.ui.mouseClicked(mx, my);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub != PvSubTab.RIFT_INVENTORY) {
			return this.overview.mouseScrolled(mouseX, mouseY, scrollY);
		}
		return this.inventory.mouseScrolled(mouseX, mouseY, scrollY, this.snapshot);
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.ui.clear();

		if (sub == PvSubTab.RIFT_INVENTORY) {
			this.inventory.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
		} else {
			this.overview.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		this.ui.drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	private static InventorySnapshot toWarmSnapshot(RiftSnapshot rift) {
		return new InventorySnapshot(
			rift.inventory(),
			rift.enderPages(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			InventorySnapshot.emptyPage("Fishing Bag", 9),
			InventorySnapshot.emptyPage("Potion Bag", 9),
			InventorySnapshot.emptyPage("Quiver", 9),
			List.of(),
			InventorySnapshot.AccessoryInfo.empty(),
			InventorySnapshot.emptyPage("Time Pocket", 9),
			InventorySnapshot.emptyPage("Personal Vault", 9),
			InventorySnapshot.emptyPage("Carnival Masks", 9),
			false,
			InventorySnapshot.emptyPage("Candy Bag", 9),
			false
		);
	}
}

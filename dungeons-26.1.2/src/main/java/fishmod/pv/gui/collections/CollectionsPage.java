/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections;

import fishmod.pv.data.BossCollections;
import fishmod.pv.data.CollectionIds;
import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.gui.collections.page.CollectionsListPage;
import fishmod.pv.gui.collections.page.MinionsPage;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.nav.PvSubTab;
import fishmod.pv.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import fishmod.pv.gui.PvDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Collections: full-width multi-column board; amount/tier/coop on hover.
 * Minions: icon grid + tier checklist.
 */
public final class CollectionsPage {
	private CollectionSnapshot snapshot = CollectionSnapshot.empty();
	private final CollectionsListPage list = new CollectionsListPage();
	private final MinionsPage minions = new MinionsPage();
	private PvSubTab lastSub;
	private boolean showMinions;
	private final List<int[]> stripHits = new ArrayList<>();
	private Component stripTip;
	private int stripTipX;
	private int stripTipY;

	public void apply(CollectionSnapshot snapshot) {
		this.snapshot = snapshot == null ? CollectionSnapshot.empty() : snapshot;
		this.list.reset(this.snapshot);
		this.minions.reset(this.snapshot);
		prefetchIcons();
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.snapshot.requestMemberRanks();
		int stripH = drawStrip(g, x, y, w, mouseX, mouseY);
		y += stripH + 4;
		h -= stripH + 4;
		sub = this.showMinions ? PvSubTab.COLLECTIONS_MINIONS : PvSubTab.COLLECTIONS_LIST;
		if (sub != this.lastSub) {
			this.list.closeDetail();
		}
		this.lastSub = sub;
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			this.minions.render(this.snapshot, g, font, x, y, w, h, mouseX, mouseY);
		} else {
			this.list.render(this.snapshot, g, font, x, y, w, h, mouseX, mouseY, screenW, screenH);
		}
	}

	public List<HypixelCollectionsCache.Category> categories() {
		return this.snapshot.categories();
	}

	public void setCategory(int index) {
		this.list.setCategory(index);
	}

	public int category() {
		return this.list.category();
	}

	/** Category tabs across the top, same style as the Bestiary family tabs. */
	private int drawStrip(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
		this.stripHits.clear();
		this.stripTip = null;
		List<HypixelCollectionsCache.Category> cats = this.snapshot.categories();
		int btn = 18;
		int gap = 2;
		int count = cats.size() + 1;
		int perRow = Math.max(1, (w + gap) / (btn + gap));
		int rows = (count + perRow - 1) / perRow;
		for (int i = 0; i < count; i++) {
			boolean minions = i == cats.size();
			int bx = x + (i % perRow) * (btn + gap);
			int by = y + (i / perRow) * (btn + gap);
			boolean selected = minions ? this.showMinions : !this.showMinions && i == this.list.category();
			boolean hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn;
			PvDraw.roundedFill(g, bx, by, btn, btn, 3, selected ? 0x50386CC8 : hovered ? 0x24FFFFFF : PvDraw.CELL);
			PvDraw.roundedRing(g, bx, by, btn, btn, 3, selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
			ItemStack icon = minions
				? PvSubTab.COLLECTIONS_MINIONS.icon()
				: CollectionCategoryIcons.icon(cats.get(i).id());
			if (!icon.isEmpty()) {
				g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2);
			}
			this.stripHits.add(new int[] { bx, by, btn, i });
			if (hovered) {
				this.stripTip = minions ? PvSubTab.COLLECTIONS_MINIONS.label() : Component.literal(cats.get(i).name());
				this.stripTipX = bx + btn / 2;
				this.stripTipY = by + btn + 14;
			}
		}
		return rows * btn + Math.max(0, rows - 1) * gap;
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.stripTip != null) {
			fishmod.pv.gui.PvTooltip.drawComponents(
				g, font, List.of(this.stripTip), this.stripTipX, this.stripTipY, screenW, screenH);
			return;
		}
		if (this.lastSub == PvSubTab.COLLECTIONS_MINIONS) {
			this.minions.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
		} else {
			this.list.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
		}
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		if (this.lastSub != PvSubTab.COLLECTIONS_MINIONS) {
			this.list.renderOverlay(g, font, screenW, screenH, mouseX, mouseY);
		}
	}

	public boolean keyPressed(int key, PvSubTab sub) {
		sub = this.showMinions ? PvSubTab.COLLECTIONS_MINIONS : PvSubTab.COLLECTIONS_LIST;
		return sub != PvSubTab.COLLECTIONS_MINIONS && this.list.keyPressed(key);
	}

	public boolean mouseClicked(double mx, double my, PvSubTab sub) {
		for (int[] hit : this.stripHits) {
			if (mx >= hit[0] && mx < hit[0] + hit[2] && my >= hit[1] && my < hit[1] + hit[2]) {
				if (hit[3] == this.snapshot.categories().size()) {
					this.showMinions = true;
				} else {
					this.showMinions = false;
					this.list.setCategory(hit[3]);
				}
				return true;
			}
		}
		sub = this.showMinions ? PvSubTab.COLLECTIONS_MINIONS : PvSubTab.COLLECTIONS_LIST;
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			return this.minions.mouseClicked(mx, my);
		}
		return this.list.mouseClicked(mx, my);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		sub = this.showMinions ? PvSubTab.COLLECTIONS_MINIONS : PvSubTab.COLLECTIONS_LIST;
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			return this.minions.mouseScrolled(mouseX, mouseY, scrollY);
		}
		return this.list.mouseScrolled(mouseX, mouseY, scrollY);
	}

	private void prefetchIcons() {
		List<String> ids = new ArrayList<>();
		for (HypixelCollectionsCache.Category category : this.snapshot.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				ids.add(CollectionsUi.resolveIconId(item.id()));
				ids.add(CollectionIds.iconId(item.id()));
				ids.addAll(CollectionIds.lookupKeys(item.id()));
			}
		}
		for (BossCollections.BossDef boss : BossCollections.bosses()) {
			ids.add(boss.iconId());
		}
		for (CollectionSnapshot.MinionEntry minion : this.snapshot.minions()) {
			ids.add(minion.iconId());
			ids.add(minion.id() + "_1");
			if (minion.maxCrafted() < minion.tierCap()) {
				ids.add(minion.id() + "_" + (minion.maxCrafted() + 1));
			}
		}
		SkyBlockItemFactory.prefetchIds(ids);
	}
}

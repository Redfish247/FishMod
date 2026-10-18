/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.MissingAccessories;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.SkyBlockStats;
import fishmod.pv.gui.nav.InventoryPane;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.ItemWorth;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelItemsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Left grid renderer + page controls for Inventories. Right pane buttons stay in ProfileViewerScreen. */
public final class InventoryPage {
	private static final int SLOT = 26;
	private static final int SLOT_GAP = 3;
	private static final int PAGE_BTN = 18;
	private static final int ARMOR_GAP = 8;
	/** Same spacing as between inventory rows so the hotbar sits flush under the 3×9 grid. */
	private static final int HOTBAR_GAP = SLOT_GAP;
	private static final int GRID_PAD = 4;
	/** Small gap under the title for non-accessory-bag previews. */
	private static final int PREVIEW_TOP_PAD = 6;
	private static final int COL_GAP = 4;
	private static final int META_GAP = 6;
	private static final int LOADOUT_GAP = 14;
	private static final int LOADOUT_MIN_W = 170;
	private static final int LOADOUT_ROW_GAP = 8;
	private static final int SEARCH_HIGHLIGHT = 0xFF55FF55;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int FLIP_MS = 480;
	private static final int POWERS_ROW = 12;
	private static final int MISSING_ROW = 18;

	private InventorySnapshot snapshot = InventorySnapshot.empty();
	/** Panes not shown in the storage side column. */
	private static final Set<InventoryPane> HIDDEN_PANES = EnumSet.of(
		InventoryPane.POTION_BAG, InventoryPane.CARNIVAL_MASKS, InventoryPane.CANDY_BAG,
		InventoryPane.TIME_POCKET, InventoryPane.QUIVER
	);
	private InventoryPane pane = InventoryPane.INVENTORY;
	/** When on Sacks: {@code null} = sack menu; otherwise index into {@link InventorySnapshot#sacks()}. */
	private Integer openSackIndex;
	private final Map<InventoryPane, Integer> pageIndex = new EnumMap<>(InventoryPane.class);
	/** Cards per loadout page, recomputed from the preview area each frame. */
	private int loadoutsPerPage = 2;
	private String searchQuery = "";
	private final Set<InventoryPane> searchPanes = EnumSet.noneOf(InventoryPane.class);
	private final Map<InventoryPane, Set<Integer>> searchPages = new EnumMap<>(InventoryPane.class);
	private final List<SlotHit> hits = new ArrayList<>();
	private final List<RunnableHit> pageHits = new ArrayList<>();
	/** Render/tooltip stacks keyed by slot identity; rebuilt when the snapshot changes. */
	private final Map<InventorySnapshot.Slot, ItemStack> stackCache = new IdentityHashMap<>();
	private InventorySnapshot.Slot hoveredSlot;
	private ItemStack hoveredStack = ItemStack.EMPTY;
	private int gemScroll;
	private int gemMaxScroll;
	private int gemScrollTop;
	private int gemScrollH;
	private List<Component> gemHoverTip;
	private int gemHoverX;
	private int gemHoverY;

	/** Accessory bag panel flip: bag face ↔ unlocked powers, toggled by the Powers button. */
	private boolean accessoryPowersFace;
	private boolean accessoryFlipTarget;
	private long accessoryFlipStartMs;
	private int accessoryFlipX;
	private int accessoryFlipY;
	private int accessoryFlipW;
	private int accessoryFlipH;
	private int accessoryPowersScroll;
	private int accessoryPowersMaxScroll;
	private int accessoryTuningsX;
	private int accessoryTuningsY;
	private int accessoryTuningsW;
	private int accessoryTuningsH;
	private boolean accessoryTuningsHover;
	/** Accessory bag panel swapped to the missing accessories list via the top-right toggle. */
	private boolean accessoryMissingView;
	private MissingAccessories.Result missingResult;
	private boolean missingPricedAtCompute;
	private int missingScroll;
	private int missingMaxScroll;
	private List<Component> missingHoverTip;
	private List<Component> loadoutHoverTip;
	private List<Component> sackValueHoverTip;
	/** Sack menu icons keyed by page so the stack cache doesn't grow every frame. */
	private final Map<InventorySnapshot.Page, InventorySnapshot.Slot> sackMenuSlots = new IdentityHashMap<>();
	private final ItemValueOverlay valueOverlay = new ItemValueOverlay();

	public void apply(InventorySnapshot snapshot) {
		this.snapshot = snapshot == null ? InventorySnapshot.empty() : snapshot;
		this.pageIndex.clear();
		this.openSackIndex = null;
		this.gemScroll = 0;
		this.stackCache.clear();
		this.sackMenuSlots.clear();
		this.valueOverlay.close();
		resetAccessoryFlip();
		this.missingResult = null;
		if (!this.pane.visibleOn(this.snapshot)) {
			this.pane = InventoryPane.INVENTORY;
		}
		rebuildSearchIndex();
		SkyBlockItemFactory.prefetch(this.snapshot);
	}

	public void setPane(InventoryPane pane) {
		if (pane != null) {
			if (this.pane != pane) {
				resetAccessoryFlip();
				this.valueOverlay.close();
			}
			this.pane = pane;
			this.openSackIndex = null;
		}
	}

	private void resetAccessoryFlip() {
		this.accessoryPowersFace = false;
		this.accessoryFlipTarget = false;
		this.accessoryFlipStartMs = 0L;
		this.accessoryPowersScroll = 0;
		this.accessoryPowersMaxScroll = 0;
		this.accessoryFlipW = 0;
		this.accessoryFlipH = 0;
		this.accessoryTuningsW = 0;
		this.accessoryTuningsH = 0;
		this.accessoryTuningsHover = false;
		this.accessoryMissingView = false;
		this.missingScroll = 0;
		this.missingMaxScroll = 0;
		this.missingHoverTip = null;
	}

	public InventoryPane pane() {
		return this.pane;
	}

	public List<InventoryPane> visiblePanes() {
		List<InventoryPane> out = new ArrayList<>();
		for (InventoryPane pane : InventoryPane.values()) {
			if (HIDDEN_PANES.contains(pane)) {
				continue;
			}
			if (pane.visibleOn(this.snapshot)) {
				out.add(pane);
			}
		}
		return out;
	}

	public void setSearchQuery(String query) {
		String next = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (next.equals(this.searchQuery)) {
			return;
		}
		this.searchQuery = next;
		rebuildSearchIndex();
	}

	/** True when this pane contains at least one search match. */
	public boolean searchHighlightsPane(InventoryPane pane) {
		return pane != null && this.searchPanes.contains(pane);
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.hits.clear();
		this.pageHits.clear();
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;
		this.gemHoverTip = null;
		this.accessoryTuningsHover = false;
		this.missingHoverTip = null;
		this.loadoutHoverTip = null;
		this.sackValueHoverTip = null;

		if (this.pane == InventoryPane.ACCESSORY_BAG) {
			renderAccessoryBagPanel(g, font, x, y, w, h, mouseX, mouseY);
			return;
		}

		PvDraw.innerPanel(g, x, y, w, h);

		if (this.pane == InventoryPane.SACKS) {
			renderSacks(g, font, x, y, w, h, mouseX, mouseY);
			return;
		}

		boolean loadouts = this.pane == InventoryPane.LOADOUTS;
		List<InventorySnapshot.Page> pages = loadouts ? List.of() : pagesFor(this.pane);
		List<InventorySnapshot.Loadout> loadoutList = this.snapshot.loadouts();
		int headerY = y + 6;
		if (loadouts) {
			int gridH = y + h - (PAGE_BTN + 10) - (headerY + font.lineHeight + 4 + PREVIEW_TOP_PAD);
			int perPage = loadoutGrid(w - 16, gridH, font)[2];
			if (perPage != this.loadoutsPerPage) {
				this.loadoutsPerPage = perPage;
				rebuildSearchIndex();
			}
		}
		int pageCount = loadouts ? loadoutPageCount() : Math.max(1, pages.size());
		int page = clampPage(this.pane, pageCount);
		boolean multi = pageCount > 1;

		String title = titleFor(loadouts, loadoutList, pages, page);
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT);

		String unavailable = loadouts ? null : unavailableReason(this.pane);
		if (unavailable != null) {
			PvDraw.textCentered(g, font, unavailable, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int metaTop = headerY + font.lineHeight + 4;
		int previewTop = metaTop + PREVIEW_TOP_PAD;
		int gridBottom = y + h - (multi ? PAGE_BTN + 10 : 6);
		int previewH = Math.max(SLOT, gridBottom - previewTop);
		int previewX = x + 8;
		int previewW = w - 16;

		if (loadouts) {
			drawLoadoutsPage(g, font, previewX, previewTop, previewW, previewH, loadoutList, page, mouseX, mouseY);
		} else if (this.pane == InventoryPane.INVENTORY) {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage("Inventory", 9)
				: pages.get(Math.min(page, pages.size() - 1));
			drawPlayerInventory(g, font, previewX, previewTop, previewW, previewH, current.slots(), mouseX, mouseY);
		} else {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage(this.pane.label().getString(), 9)
				: pages.get(Math.min(page, pages.size() - 1));
			boolean markers = this.pane == InventoryPane.WARDROBE || this.pane == InventoryPane.EQUIPMENT_WARDROBE;
			drawGrid(
				g,
				font,
				previewX,
				previewTop,
				previewW,
				previewH,
				current.columns(),
				current.slots(),
				markers,
				markers ? current.equippedColumn() : -1,
				mouseX,
				mouseY
			);
		}

		if (multi) {
			Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
			boolean highlightPrev = matches.stream().anyMatch(p -> p < page);
			boolean highlightNext = matches.stream().anyMatch(p -> p > page);
			drawPager(g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount, mouseX, mouseY, highlightPrev, highlightNext, null);
		}
	}

	/** Why a pane has nothing real to show (so it isn't drawn as a false empty grid), or null. */
	private String unavailableReason(InventoryPane pane) {
		return switch (pane) {
			case PERSONAL_VAULT -> !this.snapshot.inventoryApi()
				? Component.translatable("betterpv.inv.state.api_off").getString()
				: !this.snapshot.personalVaultPresent()
					? Component.translatable("betterpv.inv.state.vault_locked").getString()
					: null;
			case TIME_POCKET -> !this.snapshot.inventoryApi()
				? Component.translatable("betterpv.inv.state.api_off").getString()
				: !this.snapshot.timePocketPresent()
					? Component.translatable("betterpv.inv.state.not_in_api").getString()
					: null;
			default -> null;
		};
	}

	/** Sack menu (NEU sack skulls) or a single opened sack’s contents. */
	private void renderSacks(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		List<InventorySnapshot.Page> sackPages = this.snapshot.sacks();
		boolean inSack = this.openSackIndex != null
			&& this.openSackIndex >= 0
			&& this.openSackIndex < sackPages.size();

		int headerY = y + 6;
		String title = inSack ? sackPages.get(this.openSackIndex).title() : "Sacks";
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT);

		int previewTop = headerY + font.lineHeight + 4 + PREVIEW_TOP_PAD;
		boolean footer = inSack || sackMenuPageCount(sackPages) > 1;
		int gridBottom = y + h - (footer ? PAGE_BTN + 10 : 6);
		int previewH = Math.max(SLOT, gridBottom - previewTop);
		int previewX = x + 8;
		int previewW = w - 16;

		if (inSack) {
			InventorySnapshot.Page current = sackPages.get(this.openSackIndex);
			if ("Gemstone".equalsIgnoreCase(current.title())) {
				drawGemstoneMatrix(g, font, previewX, previewTop, previewW, previewH, current.slots(), mouseX, mouseY);
			} else {
				drawGrid(
					g, font, previewX, previewTop, previewW, previewH,
					current.columns(), current.slots(), false, -1, mouseX, mouseY
				);
			}
			drawSackValueHeader(g, font, x + w - 8, headerY, current, sackValues().get(this.openSackIndex), mouseX, mouseY);
			int backX = x + 8;
			int backY = y + h - PAGE_BTN - 6;
			drawPageButton(g, font, backX, backY, "<", mouseX, mouseY, true, false, () -> this.openSackIndex = null);
			PvDraw.textCentered(
				g, font, "Sacks",
				x + w / 2,
				backY + (PAGE_BTN - font.lineHeight) / 2,
				PvDraw.COLOR_MUTED
			);
			return;
		}

		int menuPages = sackMenuPageCount(sackPages);
		int page = clampPage(this.pane, menuPages);
		drawSackMenu(g, font, previewX, previewTop, previewW, previewH, sackPages, page, mouseX, mouseY);
		if (menuPages > 1) {
			int perPage = sackMenuSlotsPerPage();
			Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
			boolean highlightPrev = matches.stream().anyMatch(sackIdx -> sackIdx / perPage < page);
			boolean highlightNext = matches.stream().anyMatch(sackIdx -> sackIdx / perPage > page);
			drawPager(g, font, x, y + h - PAGE_BTN - 6, w, page, menuPages, mouseX, mouseY, highlightPrev, highlightNext, null);
		}
	}

	private int sackMenuPageCount(List<InventorySnapshot.Page> sackPages) {
		int total = Math.max(0, sackPages.size());
		int perPage = sackMenuSlotsPerPage();
		return Math.max(1, (total + perPage - 1) / perPage);
	}

	private int sackMenuSlotsPerPage() {
		// Match drawGrid capacity for a typical preview (~6 rows × 9 cols).
		return 9 * 6;
	}

	private void drawSackMenu(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Page> sackPages,
		int page,
		int mouseX,
		int mouseY
	) {
		if (sackPages.isEmpty()) {
			String key = this.snapshot.inventoryApi() ? "betterpv.inv.state.no_sacks" : "betterpv.inv.state.api_off";
			PvDraw.textCentered(g, font, Component.translatable(key).getString(), x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int cols = 9;
		int perPage = sackMenuSlotsPerPage();
		int start = page * perPage;
		int end = Math.min(sackPages.size(), start + perPage);
		List<InventorySnapshot.Slot> menuSlots = new ArrayList<>(end - start);
		for (int i = start; i < end; i++) {
			menuSlots.add(sackMenuSlot(sackPages.get(i)));
		}
		drawGrid(g, font, x, y, w, h, cols, menuSlots, false, -1, mouseX, mouseY);

		// Click targets: open the corresponding sack (drawGrid only adds tooltip hits).
		int rows = Math.max(1, (menuSlots.size() + cols - 1) / cols);
		int statusReserve = 0;
		int maxRows = Math.max(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int shown = Math.min(menuSlots.size(), cols * rows);
		for (int i = 0; i < shown; i++) {
			int sackIndex = start + i;
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (SLOT + SLOT_GAP);
			int sy = startY + row * (SLOT + SLOT_GAP);
			boolean searchHit = this.searchPages.getOrDefault(this.pane, Set.of()).contains(sackIndex);
			if (searchHit) {
				g.outline(sx, sy, SLOT, SLOT, SEARCH_HIGHLIGHT);
			}
			this.pageHits.add(new RunnableHit(sx, sy, SLOT, SLOT, () -> this.openSackIndex = sackIndex));
		}
	}

	private List<SackValue> sackValues() {
		return SackValue.compute(this.snapshot.sacks(), ItemPricer::materialPrice, SkyBlockItemFactory::plainDisplayName);
	}

	private void drawSackValueHeader(
		GuiGraphicsExtractor g,
		Font font,
		int right,
		int y,
		InventorySnapshot.Page page,
		SackValue value,
		int mouseX,
		int mouseY
	) {
		if (value == null || !value.known()) {
			return;
		}
		String label = "Sack value: ";
		String coins = FormatUtil.shortCoins(Math.round(value.total()));
		int w = font.width(label) + font.width(coins);
		int left = right - w;
		boolean hover = mouseX >= left && mouseX < right && mouseY >= y - 2 && mouseY < y + font.lineHeight + 2;
		PvDraw.text(g, font, label, left, y, hover ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, coins, left + font.width(label), y, PvDraw.COLOR_GOLD);
		if (hover) {
			this.sackValueHoverTip = ItemTooltipEnricher.sackValueLines(value, true);
		}
		InventorySnapshot.Slot icon = sackMenuSlot(page);
		this.pageHits.add(new RunnableHit(left, y - 2, w, font.lineHeight + 4, () -> this.valueOverlay.open(
			page.title() + " Sack", cachedStack(icon), icon.id(), value.breakdown(), "Sack value"
		)));
	}

	private InventorySnapshot.Slot sackMenuSlot(InventorySnapshot.Page page) {
		return this.sackMenuSlots.computeIfAbsent(page, this::buildSackMenuSlot);
	}

	private InventorySnapshot.Slot buildSackMenuSlot(InventorySnapshot.Page page) {
		String title = page == null || page.title() == null || page.title().isBlank() ? "Sack" : page.title();
		String itemId = NeuRepoCache.sackItemId(title);
		if (itemId != null && !itemId.isBlank()) {
			return new InventorySnapshot.Slot(itemId, 1, List.of(), title, null, null, null);
		}
		String icon = "CHEST";
		for (InventorySnapshot.Slot slot : page == null ? List.<InventorySnapshot.Slot>of() : page.slots()) {
			if (slot != null && !slot.isEmpty()) {
				icon = slot.id();
				break;
			}
		}
		return new InventorySnapshot.Slot(icon, 1, List.of(
			"\u00a77Sack items that aren't in any",
			"\u00a77known sack list."
		), "\u00a7f" + title, null, null, null);
	}

	/** Drawn after the right button panel so tips are never covered by it. */
	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.valueOverlay.isOpen()) {
			return;
		}
		if (this.gemHoverTip != null && !this.gemHoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.gemHoverTip, mouseX, mouseY, screenW, screenH);
			return;
		}
		if (this.missingHoverTip != null && !this.missingHoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.missingHoverTip, mouseX, mouseY, screenW, screenH);
			return;
		}
		if (this.loadoutHoverTip != null && !this.loadoutHoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.loadoutHoverTip, mouseX, mouseY, screenW, screenH);
			return;
		}
		if (this.accessoryTuningsHover) {
			List<Component> tip = accessoryTuningsTooltip();
			if (tip != null && !tip.isEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
				return;
			}
		}
		if (this.sackValueHoverTip != null && !this.sackValueHoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.sackValueHoverTip, mouseX, mouseY, screenW, screenH);
			return;
		}
		if (this.hoveredSlot != null && !this.hoveredStack.isEmpty()) {
			int sackIndex = sackMenuIndex(this.hoveredSlot);
			List<Component> tip;
			if (sackIndex >= 0) {
				tip = new ArrayList<>(SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack, false));
				ItemTooltipEnricher.appendSackValue(tip, sackValues().get(sackIndex));
			} else {
				tip = SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack);
			}
			PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	private int sackMenuIndex(InventorySnapshot.Slot slot) {
		if (this.pane != InventoryPane.SACKS || this.openSackIndex != null) {
			return -1;
		}
		List<InventorySnapshot.Page> pages = this.snapshot.sacks();
		for (int i = 0; i < pages.size(); i++) {
			if (this.sackMenuSlots.get(pages.get(i)) == slot) {
				return i;
			}
		}
		return -1;
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		this.valueOverlay.render(g, font, screenW, screenH, mouseX, mouseY);
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.valueOverlay.isOpen()) {
			return this.valueOverlay.mouseClicked(mx, my);
		}
		for (RunnableHit hit : this.pageHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run();
				return true;
			}
		}
		for (SlotHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				openItemValue(hit.slot(), hit.stack());
				return true;
			}
		}
		return false;
	}

	private void toggleAccessoryPowers() {
		if (this.accessoryFlipStartMs != 0L || !canFlipAccessoryPowers()) {
			return;
		}
		this.accessoryFlipTarget = !this.accessoryPowersFace;
		this.accessoryFlipStartMs = System.currentTimeMillis();
	}

	private void openItemValue(InventorySnapshot.Slot slot, ItemStack stack) {
		if (slot == null || slot.isEmpty()) {
			return;
		}
		InventoryDecoder.Stack worth = new InventoryDecoder.Stack(
			slot.id(),
			slot.count(),
			slot.extraAttributes() == null ? new net.minecraft.nbt.CompoundTag() : slot.extraAttributes(),
			slot.lore() == null ? List.of() : slot.lore(),
			slot.soulbound(),
			slot.displayName(),
			slot.dyeColor(),
			slot.skullValue(),
			slot.skullSignature()
		);
		ItemWorth.Breakdown breakdown = ItemWorth.breakdown(worth);
		String title = slot.displayName() != null && !slot.displayName().isBlank()
			? slot.displayName().replaceAll("§.", "").trim()
			: SkyBlockItemFactory.plainDisplayName(slot.id());
		this.valueOverlay.open(title, stack, slot.id(), breakdown);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.valueOverlay.isOpen()) {
			return this.valueOverlay.mouseScrolled(scrollY);
		}
		if (this.pane == InventoryPane.ACCESSORY_BAG && this.accessoryMissingView) {
			if (this.missingMaxScroll <= 0
				|| mouseX < this.accessoryFlipX || mouseX >= this.accessoryFlipX + this.accessoryFlipW
				|| mouseY < this.accessoryFlipY || mouseY >= this.accessoryFlipY + this.accessoryFlipH) {
				return false;
			}
			int before = this.missingScroll;
			this.missingScroll = Math.max(0, Math.min(this.missingMaxScroll,
				this.missingScroll + (scrollY > 0 ? -MISSING_ROW : MISSING_ROW)));
			return this.missingScroll != before;
		}
		if (this.pane == InventoryPane.ACCESSORY_BAG && showingAccessoryPowersFace()) {
			if (this.accessoryPowersMaxScroll <= 0) {
				return false;
			}
			if (mouseX < this.accessoryFlipX || mouseX >= this.accessoryFlipX + this.accessoryFlipW
				|| mouseY < this.accessoryFlipY || mouseY >= this.accessoryFlipY + this.accessoryFlipH) {
				return false;
			}
			int before = this.accessoryPowersScroll;
			this.accessoryPowersScroll = Math.max(0, Math.min(this.accessoryPowersMaxScroll,
				this.accessoryPowersScroll + (scrollY > 0 ? -POWERS_ROW : POWERS_ROW)));
			return this.accessoryPowersScroll != before;
		}
		if (this.pane == InventoryPane.SACKS && this.openSackIndex != null) {
			if (this.gemMaxScroll > 0 && isOpenGemstoneSack()) {
				int before = this.gemScroll;
				if (scrollY > 0) {
					this.gemScroll = Math.max(0, this.gemScroll - (SLOT + SLOT_GAP));
				} else if (scrollY < 0) {
					this.gemScroll = Math.min(this.gemMaxScroll, this.gemScroll + (SLOT + SLOT_GAP));
				}
				return this.gemScroll != before;
			}
			return false;
		}
		int size;
		if (this.pane == InventoryPane.LOADOUTS) {
			size = loadoutPageCount();
		} else if (this.pane == InventoryPane.SACKS) {
			size = sackMenuPageCount(this.snapshot.sacks());
		} else {
			size = Math.max(1, pagesFor(this.pane).size());
		}
		if (size <= 1) {
			return false;
		}
		int page = clampPage(this.pane, size);
		if (scrollY > 0 && page > 0) {
			this.pageIndex.put(this.pane, page - 1);
			return true;
		}
		if (scrollY < 0 && page < size - 1) {
			this.pageIndex.put(this.pane, page + 1);
			return true;
		}
		return false;
	}

	private String titleFor(
		boolean loadouts,
		List<InventorySnapshot.Loadout> loadoutList,
		List<InventorySnapshot.Page> pages,
		int page
	) {
		if (loadouts) {
			return "Loadouts";
		}
		if (pages.isEmpty()) {
			return this.pane.label().getString();
		}
		return pages.get(Math.min(page, pages.size() - 1)).title();
	}

	private void drawAccessoryMeta(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		InventorySnapshot.AccessoryInfo info,
		int mouseX,
		int mouseY
	) {
		this.accessoryTuningsW = 0;
		this.accessoryTuningsH = 0;
		this.accessoryTuningsHover = false;
		if (info == null) {
			return;
		}

		MutableComponent line1 = Component.empty();
		line1.append(PvDraw.styled("MP ", PvDraw.COLOR_MUTED, false));
		line1.append(PvDraw.styled(FormatUtil.commas(info.magicalPower()), 0xFF55FFFF, true));
		line1.append(PvDraw.styled("  ·  Power ", PvDraw.COLOR_MUTED, false));
		line1.append(SkyBlockStats.powerName(info.selectedPower()));
		if (info.bagUpgrades() > 0) {
			line1.append(PvDraw.styled(" · Upgrades ", PvDraw.COLOR_MUTED, false));
			line1.append(PvDraw.styled(String.valueOf(info.bagUpgrades()), PvDraw.COLOR_GOLD, true));
		}
		PvDraw.text(g, font, trimComponent(font, line1, w), x, y);

		int tuningsY = y + font.lineHeight + 2;
		int color = PvDraw.COLOR_MUTED;
		Component tunings = Component.literal("Tunings").setStyle(
			Style.EMPTY
				.withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0xFFFFFF))
				.withUnderlined(true)
		);
		int tuningsW = font.width(tunings);
		boolean hover = mouseX >= x && mouseX < x + tuningsW
			&& mouseY >= tuningsY && mouseY < tuningsY + font.lineHeight;
		if (hover) {
			tunings = Component.literal("Tunings").setStyle(
				Style.EMPTY
					.withColor(net.minecraft.network.chat.TextColor.fromRgb(PvDraw.COLOR_ACCENT & 0xFFFFFF))
					.withUnderlined(true)
			);
			tuningsW = font.width(tunings);
		}
		this.accessoryTuningsX = x;
		this.accessoryTuningsY = tuningsY;
		this.accessoryTuningsW = tuningsW;
		this.accessoryTuningsH = font.lineHeight;
		this.accessoryTuningsHover = hover;
		PvDraw.text(g, font, tunings, x, tuningsY);
	}

	private List<Component> accessoryTuningsTooltip() {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		List<Component> lines = new ArrayList<>();
		lines.add(PvDraw.styled("Tunings", PvDraw.COLOR_TEXT, true));
		if (info == null || info.tunings().isEmpty()) {
			lines.add(PvDraw.styled("No saved tunings", PvDraw.COLOR_MUTED, false));
			return lines;
		}
		for (InventorySnapshot.TuningTemplate template : info.tunings()) {
			MutableComponent line = Component.empty();
			line.append(PvDraw.styled("#" + template.slot() + " ", PvDraw.COLOR_MUTED, false));
			line.append(SkyBlockStats.tuningStats(template.stats()));
			lines.add(line);
		}
		return lines;
	}

	/** Whole left inventory panel flips: border, content, and pager. */
	private void renderAccessoryBagPanel(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		List<InventorySnapshot.Page> pages = pagesFor(InventoryPane.ACCESSORY_BAG);
		int pageCount = Math.max(1, pages.size());
		int page = clampPage(this.pane, pageCount);
		boolean multi = pageCount > 1 && !showingAccessoryPowersFace() && !this.accessoryMissingView;

		this.accessoryFlipX = x;
		this.accessoryFlipY = y;
		this.accessoryFlipW = w;
		this.accessoryFlipH = h;

		float flipProgress = 0F;
		boolean animating = this.accessoryFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.accessoryFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.accessoryPowersFace = this.accessoryFlipTarget;
				this.accessoryFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
				if (!this.accessoryPowersFace) {
					this.accessoryPowersScroll = 0;
				}
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showPowers = animating
			? (Math.cos(angle) < 0.0 ? this.accessoryFlipTarget : this.accessoryPowersFace)
			: this.accessoryPowersFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);

		int contentX = x + 8;
		int contentY = y + 6;
		int contentW = w - 16;
		int contentBottom = y + h - (multi ? PAGE_BTN + 10 : 6);
		int contentH = Math.max(SLOT, contentBottom - contentY);

		if (showPowers) {
			this.accessoryTuningsW = 0;
			this.accessoryTuningsH = 0;
			this.accessoryTuningsHover = false;
			int backW = drawPanelButton(g, font, "Accessory Bag", contentX + contentW, contentY,
				mouseX, mouseY, !animating, this::toggleAccessoryPowers);
			drawAccessoryPowersFace(g, font, contentX, contentY, contentW, contentH, backW);
		} else if (this.accessoryMissingView) {
			int toggleW = drawAccessoryViewToggle(g, font, contentX + contentW, contentY, mouseX, mouseY, !animating);
			this.accessoryTuningsW = 0;
			this.accessoryTuningsH = 0;
			this.accessoryTuningsHover = false;
			drawAccessoryMissingFace(g, font, contentX, contentY, contentW, contentH, toggleW, mouseX, mouseY);
		} else {
			int toggleW = drawAccessoryViewToggle(g, font, contentX + contentW, contentY, mouseX, mouseY, !animating);
			if (canFlipAccessoryPowers()) {
				toggleW += 4 + drawPanelButton(g, font, "Powers", contentX + contentW - toggleW - 4, contentY,
					mouseX, mouseY, !animating, this::toggleAccessoryPowers);
			}
			String title = titleFor(false, List.of(), pages, page);
			PvDraw.text(g, font, trimToWidth(font, title, contentW - toggleW - 6), contentX, contentY, PvDraw.COLOR_TEXT);
			int metaTop = contentY + font.lineHeight + 4;
			drawAccessoryMeta(g, font, contentX, metaTop, contentW, this.snapshot.accessoryInfo(), mouseX, mouseY);
			int previewTop = metaTop + font.lineHeight + 2 + font.lineHeight + 6;
			int previewH = Math.max(SLOT, contentY + contentH - previewTop);
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage("Accessory Bag", 9)
				: pages.get(Math.min(page, pages.size() - 1));
			drawGrid(
				g, font, contentX, previewTop, contentW, previewH,
				current.columns(), current.slots(), false, -1, mouseX, mouseY, false
			);
			if (multi) {
				Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
				boolean highlightPrev = matches.stream().anyMatch(p -> p < page);
				boolean highlightNext = matches.stream().anyMatch(p -> p > page);
				drawPager(
					g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount,
					mouseX, mouseY, highlightPrev, highlightNext, null
				);
			}
		}

		g.pose().popMatrix();
	}

	private void drawAccessoryPowersFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int buttonW) {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		List<String> powers = info == null ? List.of() : info.unlockedPowers();
		String selected = info == null ? "" : info.selectedPower();

		String title = "Unlocked powers";
		PvDraw.text(g, font, trimToWidth(font, title, w - buttonW - 6), x, y, PvDraw.COLOR_MUTED);
		String count = FormatUtil.commas(powers.size());
		int countX = x + font.width(title) + 4;
		if (countX + font.width(count) <= x + w - buttonW - 6) {
			PvDraw.text(g, font, count, countX, y, PvDraw.COLOR_ACCENT);
		}

		int listTop = y + font.lineHeight + 4;
		int listBottom = y + h;
		int listH = Math.max(0, listBottom - listTop);
		int colGap = 10;
		int colW = Math.max(40, (w - colGap) / 2);
		int rows = (powers.size() + 1) / 2;
		int contentH = rows * POWERS_ROW;
		this.accessoryPowersMaxScroll = Math.max(0, contentH - listH);
		this.accessoryPowersScroll = Math.min(this.accessoryPowersScroll, this.accessoryPowersMaxScroll);

		g.enableScissor(x, listTop, x + w + 3, listTop + listH);
		for (int i = 0; i < powers.size(); i++) {
			int col = i % 2;
			int row = i / 2;
			int px = x + col * (colW + colGap);
			int py = listTop + row * POWERS_ROW - this.accessoryPowersScroll;
			if (py + font.lineHeight < listTop || py > listTop + listH) {
				continue;
			}
			String power = powers.get(i);
			boolean active = selected != null && !selected.isBlank()
				&& selected.equalsIgnoreCase(power == null ? "" : power);
			Component name = SkyBlockStats.powerName(power);
			PvDraw.text(g, font, trimComponent(font, name, colW - (active ? font.width(" ●") + 2 : 0)), px, py);
			if (active) {
				PvDraw.textRight(g, font, "●", px + colW, py, 0xFF55FF55);
			}
		}
		g.disableScissor();

		if (powers.isEmpty()) {
			PvDraw.textCentered(g, font, "None unlocked", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
		}
	}

	/** Top-right "Missing" / "Accessory Bag" toggle. Returns its width so titles can avoid it. */
	private int drawAccessoryViewToggle(
		GuiGraphicsExtractor g, Font font, int rightX, int y, int mouseX, int mouseY, boolean clickable
	) {
		String label = this.accessoryMissingView ? "Accessory Bag" : "Missing";
		return drawPanelButton(g, font, label, rightX, y, mouseX, mouseY, clickable, this::toggleAccessoryMissingView);
	}

	private int drawPanelButton(
		GuiGraphicsExtractor g, Font font, String label, int rightX, int y, int mouseX, int mouseY,
		boolean clickable, Runnable action
	) {
		int bw = font.width(label) + 10;
		int bh = font.lineHeight + 4;
		int bx = rightX - bw;
		int by = y - 2;
		boolean hovered = clickable && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
		PvDraw.roundedFill(g, bx, by, bw, bh, 3, hovered ? 0x50386CC8 : PvDraw.CELL);
		PvDraw.roundedRing(g, bx, by, bw, bh, 3, hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, label, bx + bw / 2, by + 2, PvDraw.COLOR_TEXT);
		if (clickable) {
			this.pageHits.add(new RunnableHit(bx, by, bw, bh, action));
		}
		return bw;
	}

	private void toggleAccessoryMissingView() {
		this.accessoryMissingView = !this.accessoryMissingView;
		this.missingScroll = 0;
		this.missingHoverTip = null;
	}

	private MissingAccessories.Result missingAccessories() {
		boolean pricesReady = ItemPricer.isReady();
		if (this.missingResult != null && (this.missingPricedAtCompute || !pricesReady)) {
			return this.missingResult;
		}
		if (HypixelItemsCache.allItems().isEmpty()) {
			HypixelItemsCache.requestIfEmpty();
			return null;
		}
		List<String> owned = new ArrayList<>();
		for (InventorySnapshot.Page bagPage : pagesFor(InventoryPane.ACCESSORY_BAG)) {
			for (InventorySnapshot.Slot slot : bagPage.slots()) {
				if (slot != null && !slot.isEmpty() && slot.id() != null) {
					owned.add(slot.id());
				}
			}
		}
		this.missingResult = MissingAccessories.compute(owned);
		this.missingPricedAtCompute = pricesReady;
		List<String> ids = new ArrayList<>(this.missingResult.entries().size());
		for (MissingAccessories.Entry entry : this.missingResult.entries()) {
			ids.add(entry.id());
		}
		SkyBlockItemFactory.prefetchIds(ids);
		return this.missingResult;
	}

	private void drawAccessoryMissingFace(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int toggleW, int mouseX, int mouseY
	) {
		PvDraw.text(g, font, trimToWidth(font, "Missing Accessories", w - toggleW - 6), x, y, PvDraw.COLOR_TEXT);
		MissingAccessories.Result result = missingAccessories();
		if (result == null) {
			PvDraw.textCentered(g, font, "Loading item data...", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int metaY = y + font.lineHeight + 4;
		MutableComponent cost = Component.empty();
		cost.append(PvDraw.styled("Cost to max ", PvDraw.COLOR_MUTED, false));
		cost.append(PvDraw.styled(FormatUtil.shortCoins(result.costToMax()), PvDraw.COLOR_GOLD, true));
		cost.append(PvDraw.styled("  ·  ", PvDraw.COLOR_MUTED, false));
		cost.append(PvDraw.styled("+" + FormatUtil.commas(result.mpToMax()) + " MP", 0xFF55FFFF, true));
		PvDraw.text(g, font, trimComponent(font, cost, w), x, metaY);

		int countY = metaY + font.lineHeight + 2;
		String count = FormatUtil.commas(result.entries().size()) + " missing";
		if (result.unpricedTop() > 0) {
			count += "  ·  " + FormatUtil.commas(result.unpricedTop()) + " unpriced";
		}
		PvDraw.text(g, font, trimToWidth(font, count, w), x, countY, PvDraw.COLOR_MUTED);

		int listTop = countY + font.lineHeight + 6;
		int listH = Math.max(0, y + h - listTop);
		List<MissingAccessories.Entry> entries = result.entries();
		this.missingMaxScroll = Math.max(0, entries.size() * MISSING_ROW - listH);
		this.missingScroll = Math.min(this.missingScroll, this.missingMaxScroll);
		if (entries.isEmpty()) {
			PvDraw.textCentered(g, font, "Nothing missing", x + w / 2, listTop + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		boolean overList = mouseX >= x && mouseX < x + w && mouseY >= listTop && mouseY < listTop + listH;
		g.enableScissor(x, listTop, x + w + 3, listTop + listH);
		for (int i = 0; i < entries.size(); i++) {
			int rowY = listTop + i * MISSING_ROW - this.missingScroll;
			if (rowY + MISSING_ROW <= listTop || rowY >= listTop + listH) {
				continue;
			}
			MissingAccessories.Entry entry = entries.get(i);
			boolean hovered = overList && mouseY >= rowY && mouseY < rowY + MISSING_ROW;
			if (hovered) {
				PvDraw.fill(g, x, rowY, w, MISSING_ROW, PANEL_HOVER);
				this.missingHoverTip = missingTooltip(entry);
			}
			PvDraw.IconTextAlign align = PvDraw.IconTextAlign.of(rowY, MISSING_ROW, 16, font.lineHeight);
			SkyBlockIconRenderer.draw(g, entry.id(), x + 1, align.iconY());

			String perMp = entry.priced()
				? FormatUtil.shortCoins(entry.coinsPerMp()) + "/MP"
				: entry.soulbound() ? "Soulbound" : "No price";
			int perMpW = font.width(perMp);
			PvDraw.textRight(g, font, perMp, x + w - 2, align.textY(), entry.priced() ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED);
			String total = entry.priced() ? "(" + FormatUtil.shortCoins(entry.netCost()) + ")" : "+" + entry.mpGain() + " MP";
			int totalW = font.width(total) + 6;
			PvDraw.textRight(g, font, total, x + w - 2 - perMpW - 6, align.textY(), PvDraw.COLOR_MUTED);
			int nameX = x + 20;
			int nameMax = Math.max(20, x + w - 2 - perMpW - totalW - 6 - nameX);
			PvDraw.text(g, font, trimToWidth(font, entry.name(), nameMax), nameX, align.textY(),
				SkyBlockItemFactory.tierArgb(entry.tier()));
		}
		g.disableScissor();
	}

	private static List<Component> missingTooltip(MissingAccessories.Entry entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(PvDraw.styled(entry.name(), SkyBlockItemFactory.tierArgb(entry.tier()), true));
		lines.add(PvDraw.styled(entry.tier().replace('_', ' '), SkyBlockItemFactory.tierArgb(entry.tier()), false));
		lines.add(Component.empty());
		MutableComponent price = Component.empty();
		price.append(PvDraw.styled("Price ", PvDraw.COLOR_MUTED, false));
		price.append(entry.priced()
			? PvDraw.styled(FormatUtil.commas(Math.round(entry.price())), PvDraw.COLOR_GOLD, false)
			: PvDraw.styled(entry.soulbound() ? "Soulbound" : "No price", PvDraw.COLOR_MUTED, false));
		lines.add(price);
		if (entry.soldId() != null) {
			MutableComponent sold = Component.empty();
			sold.append(PvDraw.styled("After selling ", PvDraw.COLOR_MUTED, false));
			sold.append(PvDraw.styled(entry.soldName(), PvDraw.COLOR_TEXT, false));
			if (entry.soldPrice() > 0D) {
				sold.append(PvDraw.styled(" +" + FormatUtil.shortCoins(entry.soldPrice()), PvDraw.COLOR_GOLD, false));
			}
			lines.add(sold);
			if (entry.priced()) {
				MutableComponent net = Component.empty();
				net.append(PvDraw.styled("Net cost ", PvDraw.COLOR_MUTED, false));
				net.append(PvDraw.styled(FormatUtil.commas(Math.round(entry.netCost())), PvDraw.COLOR_GOLD, false));
				lines.add(net);
			}
		}
		MutableComponent mp = Component.empty();
		mp.append(PvDraw.styled("Magical Power ", PvDraw.COLOR_MUTED, false));
		mp.append(PvDraw.styled("+" + entry.mpGain(), 0xFF55FFFF, false));
		lines.add(mp);
		if (entry.priced() && entry.mpGain() > 0) {
			MutableComponent perMp = Component.empty();
			perMp.append(PvDraw.styled("Coins per MP ", PvDraw.COLOR_MUTED, false));
			perMp.append(PvDraw.styled(FormatUtil.shortCoins(entry.coinsPerMp()), PvDraw.COLOR_GOLD, false));
			lines.add(perMp);
		}
		return lines;
	}

	private boolean canFlipAccessoryPowers() {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		return info != null && !info.unlockedPowers().isEmpty();
	}

	private boolean showingAccessoryPowersFace() {
		if (this.accessoryFlipStartMs != 0L) {
			float progress = Math.min(1F, (System.currentTimeMillis() - this.accessoryFlipStartMs) / (float) FLIP_MS);
			float angle = easeInOutCubic(progress) * (float) Math.PI;
			return Math.cos(angle) < 0.0 ? this.accessoryFlipTarget : this.accessoryPowersFace;
		}
		return this.accessoryPowersFace;
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void drawLoadoutsPage(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Loadout> all,
		int page,
		int mouseX,
		int mouseY
	) {
		if (all.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int[] grid = loadoutGrid(w, h, font);
		int cols = grid[0];
		boolean compact = grid[3] == 1;
		boolean single = grid[3] == 2;
		int perPage = this.loadoutsPerPage;
		int start = page * perPage;
		int end = Math.min(all.size(), start + perPage);
		int cardW = (w - LOADOUT_GAP * (cols - 1)) / cols;
		int cardH = single ? loadoutRowHeight(font) : compact ? loadoutCompactHeight(font) : loadoutCardHeight(font);
		for (int i = start; i < end; i++) {
			int col = (i - start) % cols;
			int row = (i - start) / cols;
			int cx = x + col * (cardW + LOADOUT_GAP);
			int cy = y + row * (cardH + LOADOUT_ROW_GAP);
			if (row > 0 && col == 0) {
				PvDraw.fill(g, x, cy - LOADOUT_ROW_GAP / 2, w, 1, PvDraw.COLOR_DIVIDER);
			}
			if (single) {
				drawLoadoutRowCard(g, font, cx, cy, cardW, all.get(i), mouseX, mouseY);
			} else if (compact) {
				drawLoadoutCompactCard(g, font, cx, cy, cardW, all.get(i), mouseX, mouseY);
			} else {
				drawLoadoutCard(g, font, cx, cy, cardW, cardH, all.get(i), mouseX, mouseY);
			}
		}
	}

	/**
	 * {cols, rows, perPage, compact}. Callers pass a height that already excludes the pager row.
	 * Two rows of full cards need ~206px, more than the usual panel gives, so fall back to compact cards there.
	 */
	private static int[] loadoutGrid(int w, int h, Font font) {
		// Tall cards side by side: three across when they fit, one row per page.
		int cols = Math.max(2, Math.min(3, (w + LOADOUT_GAP) / (150 + LOADOUT_GAP)));
		int rows = Math.max(1, (h + LOADOUT_ROW_GAP) / (loadoutCardHeight(font) + LOADOUT_ROW_GAP));
		if (h < loadoutCardHeight(font)) {
			int compactRows = Math.max(1, (h + LOADOUT_ROW_GAP) / (loadoutCompactHeight(font) + LOADOUT_ROW_GAP));
			return new int[] {cols, compactRows, cols * compactRows, 1};
		}
		return new int[] {cols, rows, cols * rows, 0};
	}

	private static int loadoutRowHeight(Font font) {
		return GRID_PAD + font.lineHeight + 4 + SLOT + GRID_PAD;
	}

	private static int loadoutCardHeight(Font font) {
		return GRID_PAD + font.lineHeight + 4 + 4 * SLOT + 3 * SLOT_GAP + GRID_PAD;
	}

	private static int loadoutCompactHeight(Font font) {
		return GRID_PAD + font.lineHeight + 4 + 2 * SLOT + SLOT_GAP + GRID_PAD;
	}

	private int loadoutPageCount() {
		int perPage = Math.max(1, this.loadoutsPerPage);
		return Math.max(1, (Math.max(1, this.snapshot.loadouts().size()) + perPage - 1) / perPage);
	}

	private static String loadoutTreesLabel(InventorySnapshot.Loadout loadout) {
		List<String> parts = new ArrayList<>(2);
		if (loadout.hotmPreset() != null) {
			parts.add("HOTM " + loadout.hotmPreset());
		}
		if (loadout.hotfPreset() != null) {
			parts.add("HOTF " + loadout.hotfPreset());
		}
		return String.join("  ", parts);
	}

	private static List<Component> loadoutTreesTooltip(InventorySnapshot.Loadout loadout) {
		List<Component> lines = new ArrayList<>();
		lines.add(PvDraw.styled("Skill Tree Presets", PvDraw.COLOR_TEXT, true));
		if (loadout.hotmPreset() != null) {
			lines.add(treePresetLine("HOTM ", loadout.hotmPreset()));
		}
		if (loadout.hotfPreset() != null) {
			lines.add(treePresetLine("HOTF ", loadout.hotfPreset()));
		}
		lines.add(Component.empty());
		lines.add(PvDraw.styled("HOTM/HOTF tabs show the active preset", PvDraw.COLOR_MUTED, false));
		return lines;
	}

	private static List<Component> loadoutSummaryTooltip(InventorySnapshot.Loadout loadout) {
		List<Component> lines = new ArrayList<>();
		lines.add(SkyBlockStats.loadoutName(loadout.name()));
		lines.add(labelledLine("Power ", SkyBlockStats.powerName(loadout.powerStone())));
		lines.add(labelledLine("Tuning ", SkyBlockStats.tuningStats(loadout.tuning())));
		lines.add(labelledLine("Pet ", PvDraw.styled(loadout.petLabel().isBlank() ? "-" : loadout.petLabel(), loadoutPetColor(loadout), false)));
		if (loadout.hotmPreset() != null) {
			lines.add(treePresetLine("HOTM ", loadout.hotmPreset()));
		}
		if (loadout.hotfPreset() != null) {
			lines.add(treePresetLine("HOTF ", loadout.hotfPreset()));
		}
		if (loadout.hotmPreset() == null && loadout.hotfPreset() == null) {
			lines.add(PvDraw.styled("No HOTM/HOTF preset", PvDraw.COLOR_MUTED, false));
		}
		return lines;
	}

	private static Component labelledLine(String label, Component value) {
		MutableComponent line = Component.empty();
		line.append(PvDraw.styled(label, PvDraw.COLOR_MUTED, false));
		line.append(value);
		return line;
	}

	private static int loadoutPetColor(InventorySnapshot.Loadout loadout) {
		if (loadout.pet() != null && !loadout.pet().isEmpty()) {
			String tier = SkyBlockItemFactory.resolveTier(loadout.pet().id());
			if (!tier.isBlank()) {
				return SkyBlockItemFactory.tierArgb(tier);
			}
		}
		return PvDraw.COLOR_TEXT;
	}

	/** Two slot rows (equipment, armor) with pet and power beside; details move to the header hover. */
	private void drawLoadoutCompactCard(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		InventorySnapshot.Loadout loadout,
		int mouseX,
		int mouseY
	) {
		int startY = y + GRID_PAD;
		int left = x + GRID_PAD;
		String trees = loadoutTreesLabel(loadout);
		int treesW = trees.isEmpty() ? 0 : font.width(trees);
		int nameW = Math.max(20, w - GRID_PAD * 2 - (treesW > 0 ? treesW + 6 : 0));
		Component name = SkyBlockStats.loadoutName(loadout.name());
		if (font.width(name) > nameW) {
			name = SkyBlockStats.loadoutName(trimToWidth(font, loadout.name(), nameW - font.width("..")));
		}
		PvDraw.text(g, font, name, left, startY);
		if (treesW > 0) {
			PvDraw.text(g, font, trees, x + w - GRID_PAD - treesW, startY, PvDraw.COLOR_MUTED);
		}
		if (mouseX >= x && mouseX < x + w && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
			this.loadoutHoverTip = loadoutSummaryTooltip(loadout);
		}

		int eqY = startY + font.lineHeight + 4;
		int armorY = eqY + SLOT + SLOT_GAP;
		for (int i = 0; i < 4; i++) {
			int sx = left + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, sx, eqY, i < loadout.equipment().size() ? loadout.equipment().get(i) : null, mouseX, mouseY);
			drawSlot(g, font, sx, armorY, i < loadout.armor().size() ? loadout.armor().get(i) : null, mouseX, mouseY);
		}

		int sideX = left + 4 * SLOT + 3 * SLOT_GAP + META_GAP;
		int sideW = Math.max(0, x + w - GRID_PAD - sideX);
		drawSlot(g, font, sideX, eqY, loadout.pet(), mouseX, mouseY);
		if (loadout.pet() == null || loadout.pet().isEmpty()) {
			g.item(new ItemStack(Items.BONE), sideX + 1, eqY + 1);
		}
		if (sideW > 8) {
			PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone()), sideW),
				sideX, armorY + (SLOT - font.lineHeight) / 2);
		}
	}

	/** Single-row loadout: name on top, equipment + armor + pet in one line so three fit on a page. */
	private void drawLoadoutRowCard(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		InventorySnapshot.Loadout loadout,
		int mouseX,
		int mouseY
	) {
		int startY = y + GRID_PAD;
		int left = x + GRID_PAD;
		String trees = loadoutTreesLabel(loadout);
		int treesW = trees.isEmpty() ? 0 : font.width(trees);
		int nameW = Math.max(20, w - GRID_PAD * 2 - (treesW > 0 ? treesW + 6 : 0));
		Component name = SkyBlockStats.loadoutName(loadout.name());
		if (font.width(name) > nameW) {
			name = SkyBlockStats.loadoutName(trimToWidth(font, loadout.name(), nameW - font.width("..")));
		}
		PvDraw.text(g, font, name, left, startY);
		if (treesW > 0) {
			PvDraw.text(g, font, trees, x + w - GRID_PAD - treesW, startY, PvDraw.COLOR_MUTED);
		}
		if (mouseX >= x && mouseX < x + w && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
			this.loadoutHoverTip = loadoutSummaryTooltip(loadout);
		}
		int rowY = startY + font.lineHeight + 4;
		int sx = left;
		for (int i = 0; i < 4; i++) {
			drawSlot(g, font, sx, rowY, i < loadout.equipment().size() ? loadout.equipment().get(i) : null, mouseX, mouseY);
			sx += SLOT + SLOT_GAP;
		}
		sx += META_GAP;
		for (int i = 0; i < 4; i++) {
			drawSlot(g, font, sx, rowY, i < loadout.armor().size() ? loadout.armor().get(i) : null, mouseX, mouseY);
			sx += SLOT + SLOT_GAP;
		}
		sx += META_GAP;
		drawSlot(g, font, sx, rowY, loadout.pet(), mouseX, mouseY);
		if (loadout.pet() == null || loadout.pet().isEmpty()) {
			g.item(new ItemStack(Items.BONE), sx + 5, rowY + 5);
		}
		sx += SLOT + SLOT_GAP + META_GAP;
		int sideW = Math.max(0, x + w - GRID_PAD - sx);
		if (sideW > 8) {
			PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone()), sideW),
				sx, rowY + (SLOT - font.lineHeight) / 2);
		}
	}

	private static Component treePresetLine(String label, int preset) {
		MutableComponent line = Component.empty();
		line.append(PvDraw.styled(label, PvDraw.COLOR_MUTED, false));
		line.append(PvDraw.styled("Preset " + preset, PvDraw.COLOR_TEXT, false));
		return line;
	}

	/** Equipment column left of armor (both top→bottom), metadata under/ beside. */
	private void drawLoadoutCard(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		InventorySnapshot.Loadout loadout,
		int mouseX,
		int mouseY
	) {
		int gearW = SLOT + COL_GAP + SLOT;
		int startY = y + GRID_PAD;
		String trees = loadoutTreesLabel(loadout);
		int treesW = trees.isEmpty() ? 0 : font.width(trees);
		int nameW = Math.max(20, w - GRID_PAD * 2 - (treesW > 0 ? treesW + 6 : 0));
		Component name = SkyBlockStats.loadoutName(loadout.name());
		if (font.width(name) > nameW) {
			name = SkyBlockStats.loadoutName(trimToWidth(font, loadout.name(), nameW - font.width("..")));
		}
		PvDraw.text(g, font, name, x + GRID_PAD, startY);
		if (treesW > 0) {
			int treesX = x + w - GRID_PAD - treesW;
			PvDraw.text(g, font, trees, treesX, startY, PvDraw.COLOR_MUTED);
			if (mouseX >= treesX && mouseX < treesX + treesW && mouseY >= startY - 1 && mouseY < startY + font.lineHeight) {
				this.loadoutHoverTip = loadoutTreesTooltip(loadout);
			}
		}
		int gearTop = startY + font.lineHeight + 4;
		int eqX = x + GRID_PAD;
		int armorX = eqX + SLOT + COL_GAP;
		for (int i = 0; i < 4; i++) {
			int sy = gearTop + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, i < loadout.equipment().size() ? loadout.equipment().get(i) : null, mouseX, mouseY);
			drawSlot(g, font, armorX, sy, i < loadout.armor().size() ? loadout.armor().get(i) : null, mouseX, mouseY);
		}

		int metaX = eqX + gearW + META_GAP;
		int metaW = Math.max(40, w - (metaX - x) - GRID_PAD);
		int my = gearTop;
		PvDraw.text(g, font, "Power", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone()), metaW), metaX, my);
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Tuning", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		Component tune = SkyBlockStats.tuningStats(loadout.tuning());
		if (font.width(tune) <= metaW) {
			PvDraw.text(g, font, tune, metaX, my);
		} else {
			g.enableScissor(metaX, my, metaX + metaW, my + font.lineHeight + 1);
			PvDraw.text(g, font, tune, metaX, my);
			g.disableScissor();
		}
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Pet", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 2;
		drawSlot(g, font, metaX, my, loadout.pet(), mouseX, mouseY);
		if (loadout.pet() == null || loadout.pet().isEmpty()) {
			g.item(new ItemStack(Items.BONE), metaX + 1, my + 1);
		}
		String petText = loadout.petLabel().isBlank() ? "-" : loadout.petLabel();
		int petColor = PvDraw.COLOR_TEXT;
		if (loadout.pet() != null && !loadout.pet().isEmpty()) {
			String tier = SkyBlockItemFactory.resolveTier(loadout.pet().id());
			if (!tier.isBlank()) {
				petColor = SkyBlockItemFactory.tierArgb(tier);
			}
		}
		PvDraw.text(
			g,
			font,
			trimToWidth(font, petText, Math.max(20, metaW - SLOT - 4)),
			metaX + SLOT + 4,
			my + (SLOT - font.lineHeight) / 2,
			petColor
		);
	}

	private void drawPlayerInventory(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Slot> slots,
		int mouseX,
		int mouseY
	) {
		InventorySnapshot.Slot[] equipment = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] armor = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] hotbar = new InventorySnapshot.Slot[9];
		InventorySnapshot.Slot[] main = new InventorySnapshot.Slot[27];
		// Layout: [equipment×4][armor×4][inv×36], with legacy fallback without equipment.
		boolean hasEquipment = slots.size() >= 44;
		int armorOff = hasEquipment ? 4 : 0;
		int invOff = armorOff + 4;
		for (int i = 0; i < 4; i++) {
			equipment[i] = hasEquipment && i < slots.size() ? slots.get(i) : null;
			armor[i] = armorOff + i < slots.size() ? slots.get(armorOff + i) : null;
		}
		for (int i = 0; i < 36; i++) {
			InventorySnapshot.Slot slot = invOff + i < slots.size() ? slots.get(invOff + i) : null;
			if (i < 9) {
				hotbar[i] = slot;
			} else {
				main[i - 9] = slot;
			}
		}

		int armorH = 4 * SLOT + 3 * SLOT_GAP;
		int mainH = 3 * SLOT + 2 * SLOT_GAP;
		// Hotbar sits under the main 3×9 grid (not under the taller armor column).
		int totalH = Math.max(armorH, mainH + HOTBAR_GAP + SLOT);
		int mainW = 9 * SLOT + 8 * SLOT_GAP;
		int gearW = SLOT + COL_GAP + SLOT;
		int totalW = gearW + ARMOR_GAP + mainW;
		int startX = x + Math.max(0, (w - totalW) / 2);
		int startY = y + Math.max(0, (h - totalH) / 2);

		int eqX = startX;
		int armorX = eqX + SLOT + COL_GAP;
		int bodyX = startX + gearW + ARMOR_GAP;
		int bodyY = startY;
		int hotbarY = bodyY + mainH + HOTBAR_GAP;

		for (int i = 0; i < 4; i++) {
			int sy = startY + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, equipment[i], mouseX, mouseY);
			drawSlot(g, font, armorX, sy, armor[i], mouseX, mouseY);
		}
		for (int i = 0; i < 27; i++) {
			int col = i % 9;
			int row = i / 9;
			drawSlot(g, font, bodyX + col * (SLOT + SLOT_GAP), bodyY + row * (SLOT + SLOT_GAP), main[i], mouseX, mouseY);
		}
		for (int i = 0; i < 9; i++) {
			drawSlot(g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY);
		}

		boolean empty = true;
		for (InventorySnapshot.Slot s : slots) {
			if (s != null && !s.isEmpty()) {
				empty = false;
				break;
			}
		}
		if (empty) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + Math.min(h / 2, totalH / 2) - font.lineHeight / 2, PvDraw.COLOR_MUTED);
		}
	}

	private void drawPager(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int page,
		int total,
		int mouseX,
		int mouseY,
		boolean highlightPrev,
		boolean highlightNext,
		String centerLabel
	) {
		int prevX = x + 8;
		int nextX = x + w - 8 - PAGE_BTN;
		drawPageButton(g, font, prevX, y, "<", mouseX, mouseY, page > 0, highlightPrev, () -> stepPage(total, -1));
		drawPageButton(g, font, nextX, y, ">", mouseX, mouseY, page < total - 1, highlightNext, () -> stepPage(total, 1));
		String label = centerLabel != null && !centerLabel.isBlank()
			? centerLabel
			: (page + 1) + " / " + total;
		int labelMax = Math.max(40, nextX - prevX - PAGE_BTN - 16);
		if (font.width(label) > labelMax) {
			label = trimToWidth(font, label, labelMax);
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
	}

	private void drawPageButton(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		String label,
		int mouseX,
		int mouseY,
		boolean enabled,
		boolean searchHighlight,
		Runnable action
	) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + PAGE_BTN && mouseY >= y && mouseY < y + PAGE_BTN;
		int bg = !enabled ? PvDraw.CELL : searchHighlight ? 0xFF3A2A10 : hovered ? 0x50386CC8 : PvDraw.CELL;
		int border = searchHighlight ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.roundedFill(g, x, y, PAGE_BTN, PAGE_BTN, 3, bg);
		PvDraw.roundedRing(g, x, y, PAGE_BTN, PAGE_BTN, 3, border, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2, enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) {
			this.pageHits.add(new RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action));
		}
	}

	private boolean isOpenGemstoneSack() {
		if (this.openSackIndex == null) {
			return false;
		}
		List<InventorySnapshot.Page> sacks = this.snapshot.sacks();
		if (this.openSackIndex < 0 || this.openSackIndex >= sacks.size()) {
			return false;
		}
		return "Gemstone".equalsIgnoreCase(sacks.get(this.openSackIndex).title());
	}

	/**
	 * One slot per gem family; hover tip lists Rough→Perfect counts (no scroll matrix).
	 */
	private void drawGemstoneMatrix(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Slot> slots,
		int mouseX,
		int mouseY
	) {
		this.gemMaxScroll = 0;
		this.gemScroll = 0;
		String[] tiers = {"ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT"};
		String[] tierLabels = {"Rough", "Flawed", "Fine", "Flawless", "Perfect"};
		java.util.LinkedHashMap<String, int[]> counts = new java.util.LinkedHashMap<>();
		java.util.LinkedHashMap<String, InventorySnapshot.Slot> bestIcon = new java.util.LinkedHashMap<>();
		if (slots != null) {
			for (InventorySnapshot.Slot slot : slots) {
				if (slot == null || slot.id() == null) {
					continue;
				}
				String upper = slot.id().toUpperCase(Locale.ROOT);
				String gem = null;
				int tierIdx = -1;
				for (int t = 0; t < tiers.length; t++) {
					String prefix = tiers[t] + "_";
					if (upper.startsWith(prefix) && upper.endsWith("_GEM")) {
						gem = upper.substring(prefix.length(), upper.length() - "_GEM".length());
						tierIdx = t;
						break;
					}
				}
				if (gem == null || tierIdx < 0) {
					continue;
				}
				int[] row = counts.computeIfAbsent(gem, ignored -> new int[tiers.length]);
				row[tierIdx] = Math.max(0, slot.count());
				InventorySnapshot.Slot prev = bestIcon.get(gem);
				if (prev == null || tierIdx > bestTierIndex(prev.id(), tiers)) {
					bestIcon.put(gem, slot);
				}
			}
		}
		if (counts.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.min(6, Math.max(3, w / (SLOT + SLOT_GAP)));
		int rows = (counts.size() + cols - 1) / cols;
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int i = 0;
		for (var entry : counts.entrySet()) {
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (SLOT + SLOT_GAP);
			int sy = startY + row * (SLOT + SLOT_GAP);
			InventorySnapshot.Slot icon = bestIcon.get(entry.getKey());
			if (icon == null) {
				icon = new InventorySnapshot.Slot(
					"FINE_" + entry.getKey() + "_GEM", 1, List.of(), null, null, null, null
				);
			}
			drawSlot(g, font, sx, sy, icon, mouseX, mouseY);
			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(InventoryDecoder.prettyWords(entry.getKey()))
					.withStyle(Style.EMPTY.withColor(gemColour(entry.getKey()))));
				int[] c = entry.getValue();
				for (int t = 0; t < tiers.length; t++) {
					tip.add(Component.literal(tierLabels[t] + ": ")
						.withStyle(Style.EMPTY.withColor(gemTierColour(t)))
						.append(Component.literal(FormatUtil.commas(c[t]))
							.withStyle(Style.EMPTY.withColor(0xFFE8E8F0))));
				}
				// Prefer showing this tip over the generic item tip.
				this.hoveredSlot = null;
				this.hoveredStack = ItemStack.EMPTY;
				this.gemHoverTip = tip;
				this.gemHoverX = mouseX;
				this.gemHoverY = mouseY;
			}
			i++;
		}
	}

	private static int bestTierIndex(String id, String[] tiers) {
		if (id == null) {
			return -1;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		for (int t = tiers.length - 1; t >= 0; t--) {
			if (upper.startsWith(tiers[t] + "_")) {
				return t;
			}
		}
		return -1;
	}

	/** SkyBlock gem name colours (Rough→Perfect tip header). */
	private static int gemColour(String gem) {
		if (gem == null) {
			return 0xFFE8E8F0;
		}
		return switch (gem.toUpperCase(Locale.ROOT)) {
			case "RUBY" -> 0xFFFF5555;
			case "AMBER" -> 0xFFFFAA00;
			case "TOPAZ" -> 0xFFFFFF55;
			case "JADE" -> 0xFF55FF55;
			case "SAPPHIRE" -> 0xFF5555FF;
			case "AMETHYST" -> 0xFFAA00AA;
			case "JASPER" -> 0xFFFF55FF;
			case "OPAL" -> 0xFFFCFCFC;
			case "AQUAMARINE" -> 0xFF55FFFF;
			case "CITRINE" -> 0xFFE5A84B;
			case "ONYX" -> 0xFF555555;
			case "PERIDOT" -> 0xFF00AA00;
			default -> 0xFFE8E8F0;
		};
	}

	/** Rough / Flawed / Fine / Flawless / Perfect label colours. */
	private static int gemTierColour(int tierIdx) {
		return switch (tierIdx) {
			case 0 -> 0xFFAAAAAA; // Rough
			case 1 -> 0xFF55FF55; // Flawed
			case 2 -> 0xFF5555FF; // Fine
			case 3 -> 0xFFAA00AA; // Flawless
			case 4 -> 0xFFFFAA00; // Perfect
			default -> 0xFFE8E8F0;
		};
	}

	private void drawGrid(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int columns,
		List<InventorySnapshot.Slot> slots,
		boolean statusRow,
		int equippedColumn,
		int mouseX,
		int mouseY
	) {
		drawGrid(g, font, x, y, w, h, columns, slots, statusRow, equippedColumn, mouseX, mouseY, true);
	}

	private void drawGrid(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int columns,
		List<InventorySnapshot.Slot> slots,
		boolean statusRow,
		int equippedColumn,
		int mouseX,
		int mouseY,
		boolean emptyLabel
	) {
		int cols = Math.max(1, columns);
		int rows = Math.max(1, (slots.size() + cols - 1) / cols);
		int statusReserve = statusRow ? SLOT + SLOT_GAP : 0;
		int maxRows = Math.max(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP + statusReserve;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int maxSlots = cols * rows;

		int shown = Math.min(slots.size(), maxSlots);
		for (int i = 0; i < shown; i++) {
			InventorySnapshot.Slot slotData = slots.get(i);
			int col = i % cols;
			int row = i / cols;
			drawSlot(g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP), slotData, mouseX, mouseY);
		}

		if (statusRow) {
			int dyeY = startY + rows * (SLOT + SLOT_GAP);
			for (int col = 0; col < cols; col++) {
				int dyeX = startX + col * (SLOT + SLOT_GAP);
				drawSlot(g, font, dyeX, dyeY, null, mouseX, mouseY);
				ItemStack dye = new ItemStack(
					col == equippedColumn ? Items.LIME_DYE : Items.LIGHT_GRAY_DYE
				);
				g.item(dye, dyeX + 1, dyeY + 1);
			}
		}

		if (emptyLabel && (slots.isEmpty() || slots.stream().allMatch(s -> s == null || s.isEmpty()))) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED);
		}
	}

	private void drawSlot(
		GuiGraphicsExtractor g,
		Font font,
		int sx,
		int sy,
		InventorySnapshot.Slot slot,
		int mouseX,
		int mouseY
	) {
		boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
		boolean match = slotMatches(slot);
		PvDraw.roundedFill(g, sx, sy, SLOT, SLOT, 4, match ? 0x70604810 : hovered ? 0x30FFFFFF : PvDraw.CELL);
		PvDraw.roundedRing(g, sx, sy, SLOT, SLOT, 4,
			match ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : 0x00FFFFFF, PvDraw.C_ALL);

		if (slot != null && !slot.isEmpty()) {
			ItemStack stack = cachedStack(slot);
			SkyBlockIconRenderer.draw(g, stack, slot.id(), sx + 2, sy + 2, SLOT - 4);
			boolean hideCount = this.pane == InventoryPane.SACKS
				&& this.openSackIndex != null
				&& this.openSackIndex >= 0;
			if (!hideCount && slot.count() > 1) {
				String count = slot.count() <= 64
					? String.valueOf(slot.count())
					: FormatUtil.shortXp(slot.count());
				float scale = font.width(count) > SLOT - 4 ? 0.5f : 1f;
				int textH = Math.max(1, Math.round(font.lineHeight * scale));
				int textW = Math.max(1, Math.round(font.width(count) * scale));
				int tx = sx + SLOT - 1 - textW;
				int ty = sy + SLOT - 1 - textH;
				if (scale >= 0.99f) {
					PvDraw.text(g, font, count, tx, ty, PvDraw.COLOR_TEXT);
				} else {
					PvDraw.textScaled(g, font, count, tx, ty, PvDraw.COLOR_TEXT, scale);
				}
			}
			this.hits.add(new SlotHit(sx, sy, SLOT, SLOT, slot, stack));
			if (hovered) {
				this.hoveredSlot = slot;
				this.hoveredStack = stack;
			}
		}
	}

	private ItemStack cachedStack(InventorySnapshot.Slot slot) {
		ItemStack cached = this.stackCache.get(slot);
		if (cached != null) {
			return cached;
		}
		ItemStack built = SkyBlockItemFactory.toStack(slot);
		this.stackCache.put(slot, built);
		return built;
	}

	private void rebuildSearchIndex() {
		this.searchPanes.clear();
		this.searchPages.clear();
		if (this.searchQuery.isEmpty()) {
			return;
		}
		for (InventoryPane pane : InventoryPane.values()) {
			if (!pane.visibleOn(this.snapshot)) {
				continue;
			}
			if (pane == InventoryPane.LOADOUTS) {
				List<InventorySnapshot.Loadout> loadouts = this.snapshot.loadouts();
				for (int i = 0; i < loadouts.size(); i++) {
					if (loadoutMatches(loadouts.get(i))) {
						int page = i / this.loadoutsPerPage;
						this.searchPanes.add(pane);
						this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(page);
					}
				}
				continue;
			}
			List<InventorySnapshot.Page> pages = pagesFor(pane);
			for (int i = 0; i < pages.size(); i++) {
				if (pageMatches(pages.get(i))) {
					this.searchPanes.add(pane);
					this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(i);
				}
			}
		}
	}

	private boolean pageMatches(InventorySnapshot.Page page) {
		if (page == null || page.slots() == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : page.slots()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return false;
	}

	private boolean loadoutMatches(InventorySnapshot.Loadout loadout) {
		if (loadout == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return slotMatches(loadout.pet());
	}

	private boolean slotMatches(InventorySnapshot.Slot slot) {
		if (this.searchQuery.isEmpty() || slot == null || slot.isEmpty()) {
			return false;
		}
		String id = slot.id() == null ? "" : slot.id().toLowerCase(Locale.ROOT);
		String name = plainText(slot.displayName());
		if (id.contains(this.searchQuery) || name.contains(this.searchQuery)) {
			return true;
		}
		ItemStack preview = cachedStack(slot);
		var customName = preview.get(DataComponents.CUSTOM_NAME);
		if (customName != null && plainText(customName.getString()).contains(this.searchQuery)) {
			return true;
		}
		if (slot.lore() != null) {
			for (String line : slot.lore()) {
				if (line != null && plainText(line).contains(this.searchQuery)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String plainText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.replaceAll("§.", "").toLowerCase(Locale.ROOT);
	}

	private static String trimToWidth(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private static Component trimComponent(Font font, Component value, int maxW) {
		if (value == null) {
			return Component.empty();
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String plain = trimToWidth(font, value.getString(), maxW);
		return PvDraw.styled(plain, PvDraw.COLOR_TEXT, false);
	}

	private List<InventorySnapshot.Page> pagesFor(InventoryPane pane) {
		return switch (pane) {
			case INVENTORY -> List.of(this.snapshot.inventory());
			case ENDER_CHEST -> this.snapshot.enderChest();
			case BACKPACKS -> this.snapshot.backpacks();
			case WARDROBE -> this.snapshot.wardrobe();
			case EQUIPMENT_WARDROBE -> this.snapshot.equipmentWardrobe();
			case LOADOUTS -> List.of();
			case SACKS -> this.snapshot.sacks();
			case FISHING_BAG -> List.of(this.snapshot.fishingBag());
			case POTION_BAG -> List.of(this.snapshot.potionBag());
			case QUIVER -> List.of(this.snapshot.quiver());
			case ACCESSORY_BAG -> this.snapshot.accessoryBag();
			case TIME_POCKET -> List.of(this.snapshot.timePocket());
			case PERSONAL_VAULT -> List.of(this.snapshot.personalVault());
			case CARNIVAL_MASKS -> List.of(this.snapshot.carnivalMasks());
			case CANDY_BAG -> List.of(this.snapshot.candyBag());
		};
	}

	/** Ctrl+click steps 5 pages; normal click steps 1. */
	private void stepPage(int total, int direction) {
		int size = Math.max(1, total);
		int page = clampPage(this.pane, size);
		int step = controlDown() ? 5 : 1;
		int next = Math.max(0, Math.min(size - 1, page + direction * step));
		this.pageIndex.put(this.pane, next);
	}

	private static boolean controlDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL)
			|| InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL);
	}

	private int clampPage(InventoryPane pane, int size) {
		if (size <= 0) {
			return 0;
		}
		int page = this.pageIndex.getOrDefault(pane, 0);
		if (page < 0) page = 0;
		if (page >= size) page = size - 1;
		this.pageIndex.put(pane, page);
		return page;
	}

	private record SlotHit(int x, int y, int w, int h, InventorySnapshot.Slot slot, ItemStack stack) {
	}

	private record RunnableHit(int x, int y, int w, int h, Runnable action) {
	}
}

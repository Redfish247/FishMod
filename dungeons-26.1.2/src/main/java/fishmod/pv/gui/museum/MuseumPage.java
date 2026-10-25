/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.museum;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.MinionCosts;
import fishmod.pv.data.MuseumCatalog;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.nav.MuseumSort;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import fishmod.pv.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Museum tab: left ~70% scrollable slot grid, right ~30% progress + cheapest missing.
 */
public final class MuseumPage {
	private static final int GAP = 8;
	private static final int PAD = 6;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	private static final int LIST_ICON = 16;
	private static final int LIST_ROW_GAP = 3;
	private static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	private static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	private static final int ENABLED = 0xFF55FF55;
	private static final int DISABLED = 0xFF555555;
	private static final int SCROLL_STEP = (SLOT + SLOT_GAP) * 3;
	private static final int LIST_SCROLL_STEP = LIST_ICON + LIST_ROW_GAP + 2;
	private static final ItemStack MISSING_ICON = new ItemStack(fishmod.compat.CI.GRAY_DYE);

	public enum LoadState {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	public record Slot(
		String donationId,
		InventoryDecoder.Stack stack,
		boolean missing,
		String coveredBy,
		long donatedTimeMs
	) {
		public Slot(String donationId, InventoryDecoder.Stack stack, boolean missing) {
			this(donationId, stack, missing, null, 0L);
		}

		public Slot(String donationId, InventoryDecoder.Stack stack, boolean missing, String coveredBy) {
			this(donationId, stack, missing, coveredBy, 0L);
		}

		public boolean coveredByHigherTier() {
			return coveredBy != null && !coveredBy.isBlank();
		}
	}

	private record CheapRow(String donationId, String iconId, String name, int nameColor, double price) {
	}

	private LoadState state = LoadState.IDLE;
	private String error = "";
	private JsonObject museumMember = new JsonObject();
	private MuseumSort sort = MuseumSort.ALL;

	private List<Slot> slots = List.of();
	// Keyed by slot identity: grid indices shift when "missing only" filters the view.
	private final Map<Slot, ItemStack> iconByIndex = new java.util.IdentityHashMap<>();
	private final Map<String, ItemStack> missingIconById = new HashMap<>();
	private List<CheapRow> cheapest = List.of();
	private double costToMax;
	private double donatedValue;
	private int donatedCount;
	private int totalCount;
	/** {@code null} when museum member has no {@code appraisal} field. */
	private Boolean appraisal;
	private boolean cacheDirty = true;

	private int gridScroll;
	private int gridMaxScroll;
	private int listScroll;
	private int listMaxScroll;

	private int leftX;
	private int leftY;
	private int leftW;
	private int leftH;
	private int rightX;
	private int rightY;
	private int rightW;
	private int rightH;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private boolean missingOnly;
	private final List<Object[]> sortHits = new ArrayList<>();
	private int missingHitX;
	private int missingHitY;
	private int missingHitW;
	private int missingHitH;
	private int refreshHitX;
	private int refreshHitY;
	private int refreshHitW;
	private int refreshHitH;

	private String hoverKey = "";
	private List<Component> hoverTip = List.of();

	public void setSort(MuseumSort sort) {
		MuseumSort next = sort == null ? MuseumSort.ALL : sort;
		if (next != this.sort) {
			this.sort = next;
			this.gridScroll = 0;
			this.listScroll = 0;
			this.cacheDirty = true;
		}
	}

	public MuseumSort sort() {
		return this.sort;
	}

	public LoadState state() {
		return this.state;
	}

	public void applyLoading() {
		this.state = LoadState.LOADING;
		this.error = "";
		this.appraisal = null;
		this.cacheDirty = true;
	}

	public void applyError(String message) {
		this.state = LoadState.ERROR;
		this.error = message == null || message.isBlank() ? "Museum unavailable" : message;
		this.museumMember = new JsonObject();
		this.appraisal = null;
		this.cacheDirty = true;
	}

	public void applyMuseum(JsonObject member) {
		JsonObject next = member == null ? new JsonObject() : member;
		// Same cached member instance: skip dirty/scroll reset so we do not re-decode every frame.
		if (this.state == LoadState.READY && this.museumMember == next) {
			return;
		}
		this.state = LoadState.READY;
		this.error = "";
		this.museumMember = next;
		this.appraisal = readAppraisal(next);
		this.gridScroll = 0;
		this.listScroll = 0;
		this.cacheDirty = true;
	}

	public void reset() {
		this.state = LoadState.IDLE;
		this.error = "";
		this.museumMember = new JsonObject();
		this.appraisal = null;
		this.gridScroll = 0;
		this.listScroll = 0;
		this.slots = List.of();
		this.iconByIndex.clear();
		this.missingIconById.clear();
		this.cheapest = List.of();
		this.costToMax = 0;
		this.donatedValue = 0;
		this.donatedCount = 0;
		this.totalCount = 0;
		this.cacheDirty = true;
		this.hoverKey = "";
		this.hoverTip = List.of();
	}

	/** Toggles the "Missing only" filter; true when the click landed on its button. */
	public boolean clickMissingOnly(double mx, double my) {
		for (Object[] hit : this.sortHits) {
			int hx = (Integer) hit[0];
			int hy = (Integer) hit[1];
			int hw = (Integer) hit[2];
			int hh = (Integer) hit[3];
			if (mx >= hx && mx < hx + hw && my >= hy && my < hy + hh) {
				setSort((MuseumSort) hit[4]);
				return true;
			}
		}
		if (this.missingHitW > 0
			&& mx >= this.missingHitX && mx < this.missingHitX + this.missingHitW
			&& my >= this.missingHitY && my < this.missingHitY + this.missingHitH) {
			this.missingOnly = !this.missingOnly;
			this.gridScroll = 0;
			return true;
		}
		return false;
	}

	public boolean clickRefresh(double mx, double my) {
		return mx >= this.refreshHitX && mx < this.refreshHitX + this.refreshHitW
			&& my >= this.refreshHitY && my < this.refreshHitY + this.refreshHitH
			&& this.refreshHitW > 0
			&& this.state != LoadState.LOADING;
	}

	/** Scroll left grid or right missing list depending on cursor position. */
	public boolean mouseScrolled(double mx, double my, double scrollY) {
		if (this.state != LoadState.READY) {
			return false;
		}
		boolean overRight = mx >= this.rightX && mx < this.rightX + this.rightW
			&& my >= this.rightY && my < this.rightY + this.rightH;
		boolean overLeft = mx >= this.leftX && mx < this.leftX + this.leftW
			&& my >= this.leftY && my < this.leftY + this.leftH;
		if (overRight && this.listMaxScroll > 0) {
			int delta = scrollY > 0 ? -LIST_SCROLL_STEP : LIST_SCROLL_STEP;
			int next = Math.max(0, Math.min(this.listMaxScroll, this.listScroll + delta));
			if (next != this.listScroll) {
				this.listScroll = next;
				return true;
			}
			return false;
		}
		if (overLeft && this.gridMaxScroll > 0) {
			int delta = scrollY > 0 ? -SCROLL_STEP : SCROLL_STEP;
			int next = Math.max(0, Math.min(this.gridMaxScroll, this.gridScroll + delta));
			if (next != this.gridScroll) {
				this.gridScroll = next;
				return true;
			}
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mx,
		int my,
		int screenW,
		int screenH
	) {
		ensureCache();

		int leftW = Math.max(160, (int) Math.round(w * 0.70));
		int rightW = Math.max(110, w - leftW - GAP);
		leftW = w - rightW - GAP;
		int rx = x + leftW + GAP;

		this.leftX = x;
		this.leftY = y;
		this.leftW = leftW;
		this.leftH = h;
		this.rightX = rx;
		this.rightY = y;
		this.rightW = rightW;
		this.rightH = h;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rx, y, rightW, h);

		String nextHover = drawLeft(g, font, x, y, leftW, h, mx, my);
		String rightHover = drawRight(g, font, rx, y, rightW, h, mx, my);
		if (rightHover != null && !rightHover.isBlank()) {
			nextHover = rightHover;
		}

		if (nextHover == null || nextHover.isBlank()) {
			this.hoverKey = "";
			this.hoverTip = List.of();
		} else if (!nextHover.equals(this.hoverKey)) {
			this.hoverKey = nextHover;
			Slot slot = slotByKey(nextHover);
			this.hoverTip = slot == null ? List.of() : tooltipFor(slot);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.hoverTip, mx, my, screenW, screenH);
		}
	}

	private String drawLeft(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Museum", lx, ly, PvDraw.COLOR_MUTED);
		boolean refreshEnabled = this.state != LoadState.LOADING;
		String refreshLabel = refreshEnabled ? "Refresh" : "Loading...";
		int btnW = Math.max(56, font.width(refreshLabel) + 10);
		int btnH = font.lineHeight + 6;
		int btnX = lx + lw - btnW;
		int btnY = ly - 1;
		this.refreshHitX = btnX;
		this.refreshHitY = btnY;
		this.refreshHitW = btnW;
		this.refreshHitH = btnH;
		boolean hoverRefresh = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
		PvDraw.fill(g, btnX, btnY, btnW, btnH, ITEM_SLOT_BG);
		g.outline(btnX, btnY, btnW, btnH,
			hoverRefresh && refreshEnabled ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		PvDraw.textCentered(g, font, refreshLabel, btnX + btnW / 2, btnY + (btnH - font.lineHeight) / 2,
			refreshEnabled ? ENABLED : DISABLED);

		String missingLabel = "Show missing only";
		int mW = font.width(missingLabel) + 10;
		int mX = btnX - 6 - mW;
		this.missingHitX = mX;
		this.missingHitY = btnY;
		this.missingHitW = mW;
		this.missingHitH = btnH;
		boolean hoverMissing = mx >= mX && mx < mX + mW && my >= btnY && my < btnY + btnH;
		PvDraw.roundedFill(g, mX, btnY, mW, btnH, 3, this.missingOnly ? 0x50386CC8 : ITEM_SLOT_BG);
		PvDraw.roundedRing(g, mX, btnY, mW, btnH, 3, this.missingOnly ? PvDraw.COLOR_ACCENT : hoverMissing ? 0x60FFFFFF : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, missingLabel, mX + mW / 2, btnY + (btnH - font.lineHeight) / 2,
			this.missingOnly ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);

		ly += font.lineHeight + 6;

		// Category filter as labelled pills (the old side icons were generic vanilla items).
		this.sortHits.clear();
		int sx = lx;
		int sy = ly;
		int pillH = font.lineHeight + 6;
		for (MuseumSort option : MuseumSort.values()) {
			String label = option.label().getString();
			int pw = font.width(label) + 10;
			if (sx + pw > lx + lw && sx > lx) {
				sx = lx;
				sy += pillH + 3;
			}
			boolean on = option == this.sort;
			boolean hov = mx >= sx && mx < sx + pw && my >= sy && my < sy + pillH;
			PvDraw.roundedFill(g, sx, sy, pw, pillH, 4, on ? 0x70386CC8 : hov ? 0x30FFFFFF : PvDraw.CELL);
			PvDraw.textCentered(g, font, label, sx + pw / 2, sy + (pillH - font.lineHeight) / 2,
				on ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
			this.sortHits.add(new Object[] {sx, sy, pw, pillH, option});
			sx += pw + 3;
		}
		ly = sy + pillH + 6;

		if (this.state == LoadState.LOADING) {
			PvDraw.textCentered(g, font, "Loading museum...",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.gridMaxScroll = 0;
			return null;
		}
		if (this.state == LoadState.ERROR) {
			PvDraw.textCentered(g, font, this.error,
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.gridMaxScroll = 0;
			return null;
		}
		if (this.state == LoadState.IDLE) {
			PvDraw.textCentered(g, font, "Museum not loaded",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.gridMaxScroll = 0;
			return null;
		}
		List<Slot> view = this.slots;
		if (this.missingOnly) {
			List<Slot> only = new ArrayList<>();
			for (Slot slot : this.slots) {
				if (slot.missing()) {
					only.add(slot);
				}
			}
			view = only;
		}
		if (view.isEmpty()) {
			PvDraw.textCentered(g, font, HypixelItemsCache.allItems().isEmpty() ? "Loading item data..." : "No museum slots",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.gridMaxScroll = 0;
			return null;
		}

		int cols = Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP));
		int rows = (view.size() + cols - 1) / cols;
		int contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP;
		this.gridX = x;
		this.gridY = ly;
		this.gridW = w;
		this.gridH = Math.max(0, bottom - ly);
		this.gridMaxScroll = Math.max(0, contentH - this.gridH);
		this.gridScroll = Math.min(this.gridScroll, this.gridMaxScroll);

		int rowPitch = SLOT + SLOT_GAP;
		int firstRow = Math.max(0, this.gridScroll / rowPitch);
		int lastRow = Math.min(rows - 1, (this.gridScroll + this.gridH) / rowPitch + 1);
		int firstIndex = firstRow * cols;
		int lastIndex = Math.min(view.size() - 1, (lastRow + 1) * cols - 1);

		String hoveredKey = null;
		g.enableScissor(lx, this.gridY, lx + lw + 3, this.gridY + this.gridH);
		for (int i = firstIndex; i <= lastIndex; i++) {
			Slot slot = view.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * rowPitch;
			int by = ly + row * rowPitch - this.gridScroll;
			if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
				continue;
			}
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= this.gridY && my < this.gridY + this.gridH;
			int rarity = SkyBlockItemFactory.tierArgb(slotTier(slot));
			PvDraw.roundedFill(g, bx, by, SLOT, SLOT, 3, raritySlotBackground(rarity, slot.missing(), hovered));
			PvDraw.roundedRing(g, bx, by, SLOT, SLOT, 3, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER, PvDraw.C_ALL);
			int pad = Math.max(0, (SLOT - 16) / 2);
			ItemStack icon = iconAt(slot);
			if (slot.missing()) {
				if (!icon.isEmpty()) {
					g.item(icon, bx + pad, by + pad);
				}
			} else {
				drawSkyblockIcon(g, resolveIconId(slot), icon, bx + pad, by + pad, 16);
			}
			if (hovered) {
				hoveredKey = slot.donationId();
			}
		}
		g.disableScissor();
		return hoveredKey;
	}

	private String drawRight(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		if (this.state != LoadState.READY) {
			this.listMaxScroll = 0;
			return null;
		}
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = Math.max(8, w - PAD * 2);
		int bottom = y + h - PAD;

		String type = this.sort.label().getString();
		String count = FormatUtil.commas(this.donatedCount) + "/" + FormatUtil.commas(this.totalCount);
		int countColor = this.donatedCount >= this.totalCount && this.totalCount > 0
			? ENABLED : PvDraw.COLOR_GOLD;
		PvDraw.text(g, font, type, lx, ly, PvDraw.COLOR_TEXT);
		PvDraw.text(g, font, count, lx + lw - font.width(count), ly, countColor);
		ly += font.lineHeight + 3;

		String costLabel = "Cost:";
		String costValue = FormatUtil.shortCoins(this.costToMax);
		PvDraw.text(g, font, costLabel, lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, costValue, lx + lw - font.width(costValue), ly, PvDraw.COLOR_GOLD);
		ly += font.lineHeight + 2;

		String totalLabel = "Total:";
		String totalValue = FormatUtil.shortCoins(this.donatedValue + this.costToMax);
		PvDraw.text(g, font, totalLabel, lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, totalValue, lx + lw - font.width(totalValue), ly, PvDraw.COLOR_GOLD);
		ly += font.lineHeight + 2;

		String missingLabel = "Missing:";
		String missingValue = FormatUtil.commas(this.totalCount - this.donatedCount);
		PvDraw.text(g, font, missingLabel, lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, missingValue, lx + lw - font.width(missingValue), ly,
			this.totalCount - this.donatedCount > 0 ? 0xFFFF5555 : ENABLED);
		ly += font.lineHeight + 2;

		if (this.appraisal != null) {
			String appLabel = "Appraisal:";
			String appValue = this.appraisal ? "Yes" : "No";
			PvDraw.text(g, font, appLabel, lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.text(g, font, appValue, lx + lw - font.width(appValue), ly,
				this.appraisal ? ENABLED : PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 2;
		}
		ly += 4;

		PvDraw.text(g, font, "Cheapest missing", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		this.listX = lx;
		this.listY = ly;
		this.listW = lw;
		this.listH = Math.max(0, bottom - ly);

		if (this.cheapest.isEmpty()) {
			this.listMaxScroll = 0;
			PvDraw.text(g, font, this.donatedCount >= this.totalCount && this.totalCount > 0
				? "Complete" : "None", lx, ly, PvDraw.COLOR_MUTED);
			return null;
		}

		int rowH = Math.max(LIST_ICON, font.lineHeight) + LIST_ROW_GAP;
		int contentH = this.cheapest.size() * rowH;
		this.listMaxScroll = Math.max(0, contentH - this.listH);
		this.listScroll = Math.min(this.listScroll, this.listMaxScroll);

		String hoveredKey = null;
		g.enableScissor(lx, this.listY, lx + lw + 3, this.listY + this.listH);
		for (int i = 0; i < this.cheapest.size(); i++) {
			CheapRow row = this.cheapest.get(i);
			int by = this.listY + i * rowH - this.listScroll;
			if (by + rowH < this.listY || by > this.listY + this.listH) {
				continue;
			}
			boolean hovered = mx >= lx && mx < lx + lw
				&& my >= by && my < by + rowH - LIST_ROW_GAP
				&& my >= this.listY && my < this.listY + this.listH;

			ItemStack icon = missingIcon(row);
			int iconY = by + Math.max(0, (Math.max(LIST_ICON, font.lineHeight) - LIST_ICON) / 2);
			drawSkyblockIcon(g, row.iconId(), icon, lx, iconY, LIST_ICON);

			String price = row.price() > 0 ? FormatUtil.shortCoins(row.price()) : "-";
			int priceW = font.width(price);
			int textX = lx + LIST_ICON + 3;
			int nameMax = Math.max(8, lw - LIST_ICON - 3 - priceW - 4);
			String name = trimToWidth(font, row.name(), nameMax);
			int textY = by + Math.max(0, (LIST_ICON - font.lineHeight) / 2);
			PvDraw.text(g, font, name, textX, textY, row.nameColor());
			PvDraw.text(g, font, price, lx + lw - priceW, textY, PvDraw.COLOR_GOLD);
			if (hovered) {
				hoveredKey = row.donationId();
			}
		}
		g.disableScissor();
		return hoveredKey;
	}

	private void ensureCache() {
		if (!this.cacheDirty) {
			return;
		}
		if (this.state != LoadState.READY) {
			this.slots = List.of();
			this.iconByIndex.clear();
			this.missingIconById.clear();
			this.cheapest = List.of();
			this.costToMax = 0;
			this.donatedValue = 0;
			this.donatedCount = 0;
			this.totalCount = 0;
			this.cacheDirty = false;
			return;
		}
		rebuildCache();
		// The catalog comes from Hypixel item defs; keep retrying until they arrive.
		this.cacheDirty = HypixelItemsCache.allItems().isEmpty();
		if (this.cacheDirty) {
			HypixelItemsCache.requestIfEmpty();
		}
	}

	private void rebuildCache() {
		MuseumCatalog.ensureBuilt();
		List<Slot> built = buildSlots();
		this.slots = built;
		List<String> missingIds = new ArrayList<>();
		for (Slot slot : built) {
			if (slot.missing() && slot.donationId() != null && !isSpecialKey(slot.donationId())) {
				missingIds.add(slot.donationId());
			}
		}
		NeuRepoCache.prefetch(missingIds);
		this.iconByIndex.clear();
		this.missingIconById.clear();
		this.hoverKey = "";
		this.hoverTip = List.of();
		this.totalCount = built.size();
		int donated = 0;
		double cost = 0;
		double owned = 0;
		List<CheapRow> cheap = new ArrayList<>();

		for (Slot slot : built) {
			if (slot.missing()) {
				double price = MuseumCatalog.marketPrice(slot.donationId());
				cost += price;
				String iconId = MuseumCatalog.iconId(slot.donationId());
				int color = SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(iconId));
				cheap.add(new CheapRow(
					slot.donationId(),
					iconId,
					MuseumCatalog.displayName(slot.donationId()),
					color,
					price
				));
			} else {
				donated++;
				String valueId = MuseumCatalog.valueId(slot.donationId(), slot.coveredBy(), slot.stack());
				if (valueId != null) {
					owned += MuseumCatalog.marketPrice(valueId);
				}
			}
		}
		// Priced ascending, unknowns (0) at the end.
		cheap.sort(Comparator
			.comparingInt((CheapRow r) -> r.price() > 0 ? 0 : 1)
			.thenComparingDouble(CheapRow::price)
			.thenComparing(CheapRow::name, String.CASE_INSENSITIVE_ORDER));
		this.donatedCount = donated;
		this.costToMax = cost;
		this.donatedValue = owned;
		this.cheapest = List.copyOf(cheap);

		List<String> warm = new ArrayList<>(built.size() + cheap.size());
		for (Slot slot : built) {
			String id = resolveIconId(slot);
			if (id != null && !id.isBlank()) {
				warm.add(id);
			}
		}
		for (CheapRow row : cheap) {
			warm.add(row.iconId());
		}
		SkyBlockItemFactory.prefetchIds(warm);
	}

	private ItemStack missingIcon(CheapRow row) {
		ItemStack cached = this.missingIconById.get(row.donationId());
		if (cached != null) {
			return cached;
		}
		ItemStack icon = SkyBlockItemFactory.iconStack(row.iconId());
		if (icon.isEmpty()) {
			icon = MISSING_ICON;
		}
		// Paper+item_model is normal until the pack texture resolves - keep retrying.
		if (!isWeakIcon(icon) || SkyBlockIconRenderer.hasKnownIcon(row.iconId())) {
			this.missingIconById.put(row.donationId(), icon);
		}
		return icon;
	}

	/** Prefer SkyBlock pack / NEU textures over paper stand-ins (modern custom-model items). */
	private static void drawSkyblockIcon(
		GuiGraphicsExtractor g, String iconId, ItemStack fallback, int x, int y, int size
	) {
		ItemStack stack = fallback == null ? ItemStack.EMPTY : fallback;
		SkyBlockIconRenderer.draw(g, stack, iconId, x, y, size);
	}

	private static String resolveIconId(Slot slot) {
		if (slot == null) {
			return "";
		}
		// Covered-by-higher: show this slot's catalog icon, not the donor's Hyperion/etc.
		if (!slot.missing() && !slot.coveredByHigherTier() && slot.stack() != null) {
			String id = slot.stack().id();
			if (id != null && !id.isBlank() && !isSpecialKey(id)) {
				return id;
			}
		}
		String catalog = MuseumCatalog.iconId(slot.donationId());
		if (catalog != null && !catalog.isBlank() && !isSpecialKey(catalog)) {
			return catalog;
		}
		if (!slot.missing() && slot.stack() != null && slot.stack().id() != null) {
			return slot.stack().id();
		}
		return slot.donationId() == null ? "" : slot.donationId();
	}

	private static boolean isSpecialKey(String id) {
		return id != null && id.toUpperCase(Locale.ROOT).startsWith("SPECIAL:");
	}

	private static String slotTier(Slot slot) {
		if (slot == null) {
			return "";
		}
		String id = resolveIconId(slot);
		String tier = SkyBlockItemFactory.resolveTier(id);
		if (!tier.isBlank()) {
			return bumpTier(tier, rarityUpgrades(slot));
		}
		if (slot.donationId() != null && !slot.donationId().equalsIgnoreCase(id)) {
			return bumpTier(SkyBlockItemFactory.resolveTier(slot.donationId()), rarityUpgrades(slot));
		}
		return "";
	}

	private static int rarityUpgrades(Slot slot) {
		if (slot == null || slot.missing() || slot.stack() == null) {
			return 0;
		}
		return NbtAttrs.intValue(slot.stack().extraAttributes(), "rarity_upgrades", 0);
	}

	private static String bumpTier(String tier, int upgrades) {
		if (tier == null || tier.isBlank() || upgrades <= 0) {
			return tier == null ? "" : tier;
		}
		String[] order = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
		String key = SkyBlockItemFactory.normalizeTier(tier);
		int idx = -1;
		for (int i = 0; i < order.length; i++) {
			if (order[i].equals(key)) {
				idx = i;
				break;
			}
		}
		if (idx < 0) {
			return key;
		}
		return order[Math.min(order.length - 1, idx + upgrades)];
	}

	/** Dark rarity-tinted slot fill (keeps icons readable). Missing slots use a softer tint. */
	private static int raritySlotBackground(int rarityArgb, boolean missing, boolean hovered) {
		int r = (rarityArgb >>> 16) & 0xFF;
		int green = (rarityArgb >>> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		// Unknown / white common: keep the neutral museum slot colour.
		if (r >= 240 && green >= 240 && b >= 240) {
			return hovered ? PvDraw.CELL : ITEM_SLOT_BG;
		}
		float tint = hovered ? 0.40f : missing ? 0.20f : 0.28f;
		int outR = Math.round(16 + r * tint);
		int outG = Math.round(16 + green * tint);
		int outB = Math.round(16 + b * tint);
		return 0xFF000000
			| (Math.max(0, Math.min(255, outR)) << 16)
			| (Math.max(0, Math.min(255, outG)) << 8)
			| Math.max(0, Math.min(255, outB));
	}

	private static boolean isWeakIcon(ItemStack icon) {
		return icon == null || icon.isEmpty() || icon.is(Items.PAPER) || icon.is(Items.BARRIER);
	}

	/** Lightweight icon only - never builds full lore stacks for the grid unless needed for skulls. */
	private ItemStack iconAt(Slot slot) {
		ItemStack cached = this.iconByIndex.get(slot);
		if (cached != null) {
			return cached;
		}
		if (slot.missing()) {
			this.iconByIndex.put(slot, MISSING_ICON);
			return MISSING_ICON;
		}
		ItemStack icon;
		String iconId = resolveIconId(slot);
		if (!slot.coveredByHigherTier() && slot.stack() != null
			&& ((slot.stack().skullValue() != null && !slot.stack().skullValue().isBlank())
			|| slot.stack().dyeColor() != null)) {
			// Donated NBT skulls / dyed leather - same path as tooltips.
			icon = SkyBlockItemFactory.toStack(toUiSlot(slot));
		} else if (iconId != null && !iconId.isBlank() && !isSpecialKey(iconId)) {
			icon = SkyBlockItemFactory.iconStack(iconId);
			if (isWeakIcon(icon)) {
				String fallback = MuseumCatalog.iconId(slot.donationId());
				if (fallback != null && !fallback.isBlank()
					&& !fallback.equalsIgnoreCase(iconId)
					&& !isSpecialKey(fallback)) {
					ItemStack alt = SkyBlockItemFactory.iconStack(fallback);
					if (!isWeakIcon(alt)) {
						icon = alt;
					}
				}
			}
		} else {
			icon = ItemStack.EMPTY;
		}
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.BARRIER);
		}
		// Don't freeze paper before pack/NEU textures arrive.
		if (!isWeakIcon(icon) || SkyBlockIconRenderer.hasKnownIcon(iconId)) {
			this.iconByIndex.put(slot, icon);
		}
		return icon;
	}

	private Slot slotByKey(String donationId) {
		if (donationId == null) {
			return null;
		}
		for (Slot slot : this.slots) {
			if (donationId.equals(slot.donationId())) {
				return slot;
			}
		}
		return null;
	}

	private List<Component> tooltipFor(Slot slot) {
		InventorySnapshot.Slot ui = toUiSlot(slot);
		ItemStack rendered = SkyBlockItemFactory.toStack(ui);
		List<Component> tip = new ArrayList<>(SkyBlockItemFactory.tooltipLines(ui, rendered, false));
		if (slot.coveredByHigherTier()) {
			tip.add(Component.empty());
			tip.add(SkyBlockItemFactory.legacyLine(
				"§7Donated via §a" + MuseumCatalog.displayName(slot.coveredBy())));
		}
		if (!slot.missing() && slot.donatedTimeMs() > 0L) {
			tip.add(Component.empty());
			String date = FormatUtil.prettyDate(slot.donatedTimeMs());
			String age = FormatUtil.ago(slot.donatedTimeMs());
			if (!date.isBlank() && !age.isBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f" + date + " §8(" + age + ")"));
			} else if (!age.isBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f" + age));
			} else if (!date.isBlank()) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Donated: §f" + date));
			}
		}
		if (!slot.missing()) {
			String valueId = MuseumCatalog.valueId(slot.donationId(), slot.coveredBy(), slot.stack());
			double value = valueId == null ? 0 : MuseumCatalog.marketPrice(valueId);
			if (value > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Value: §6" + FormatUtil.shortCoins(value)));
			}
		}
		if (slot.missing()) {
			tip.add(Component.empty());
			tip.add(SkyBlockItemFactory.legacyLine("§7Missing donation"));
			double price = MuseumCatalog.marketPrice(slot.donationId());
			if (price > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Price: §6" + FormatUtil.shortCoins(price)));
			}
			int xp = MuseumCatalog.donationXp(slot.donationId());
			if (xp > 0) {
				tip.add(SkyBlockItemFactory.legacyLine("§7Museum XP: §e" + FormatUtil.commas(xp)));
			}
			addRecipeLines(tip, slot.donationId());
		}
		return tip;
	}

	/** Only items NEU has loaded; armor sets and unknown ids get no recipe section at all. */
	private static void addRecipeLines(List<Component> tip, String donationId) {
		JsonObject item = donationId == null || isSpecialKey(donationId) ? null : NeuRepoCache.get(donationId);
		if (item == null) {
			return;
		}
		Map<String, Integer> ingredients = MinionCosts.ingredients(MinionCosts.recipe(item), null);
		tip.add(Component.empty());
		if (ingredients.isEmpty()) {
			tip.add(SkyBlockItemFactory.legacyLine("§8No crafting recipe"));
			return;
		}
		tip.add(SkyBlockItemFactory.legacyLine("§7Recipe:"));
		for (Map.Entry<String, Integer> e : ingredients.entrySet()) {
			tip.add(SkyBlockItemFactory.legacyLine("§8" + FormatUtil.commas(e.getValue()) + "x §f"
				+ SkyBlockItemFactory.plainDisplayName(e.getKey())));
		}
	}

	private static InventorySnapshot.Slot toUiSlot(Slot slot) {
		if (!slot.missing() && !slot.coveredByHigherTier() && slot.stack() != null) {
			InventoryDecoder.Stack s = slot.stack();
			return new InventorySnapshot.Slot(
				s.id(),
				Math.max(1, s.count()),
				s.lore() == null ? List.of() : s.lore(),
				s.displayName(),
				s.dyeColor(),
				s.skullValue(),
				s.skullSignature()
			);
		}
		String id = MuseumCatalog.iconId(slot.donationId());
		return new InventorySnapshot.Slot(id, 1, List.of(), null, null, null, null);
	}

	private List<Slot> buildSlots() {
		Map<String, MuseumCatalog.ResolvedDonation> donated =
			MuseumCatalog.resolveDonations(InventoryDecoder.parseMuseumById(this.museumMember));
		if (this.sort.isAll()) {
			List<Slot> out = new ArrayList<>();
			for (MuseumSort category : MuseumSort.categories()) {
				out.addAll(buildSlotsForCategory(category, donated));
			}
			return missingLast(out);
		}
		return missingLast(buildSlotsForCategory(this.sort, donated));
	}

	private static List<Slot> missingLast(List<Slot> slots) {
		List<Slot> out = new ArrayList<>(slots.size());
		for (Slot slot : slots) {
			if (!slot.missing()) {
				out.add(slot);
			}
		}
		for (Slot slot : slots) {
			if (slot.missing()) {
				out.add(slot);
			}
		}
		return out;
	}

	private List<Slot> buildSlotsForCategory(
		MuseumSort category,
		Map<String, MuseumCatalog.ResolvedDonation> donated
	) {
		List<Slot> out = new ArrayList<>();
		if (category == MuseumSort.SPECIAL) {
			for (var entry : donated.entrySet()) {
				String key = entry.getKey();
				MuseumCatalog.ResolvedDonation res = entry.getValue();
				if (key != null && key.toUpperCase(Locale.ROOT).startsWith("SPECIAL:")) {
					out.add(donatedSlot(key, res.stack(), null));
				} else if (res.direct() && !MuseumCatalog.isCataloged(key)) {
					out.add(donatedSlot(key, res.stack(), null));
				}
			}
			for (String id : MuseumCatalog.donationIds(MuseumSort.SPECIAL)) {
				MuseumCatalog.ResolvedDonation res = donated.get(id);
				if (res == null) {
					out.add(new Slot(id, null, true, null, 0L));
				} else {
					out.add(donatedSlot(id, res.stack(), res.coveredBy()));
				}
			}
			return out;
		}
		for (String id : MuseumCatalog.donationIds(category)) {
			MuseumCatalog.ResolvedDonation res = donated.get(id);
			if (res == null) {
				out.add(new Slot(id, null, true, null, 0L));
			} else {
				out.add(donatedSlot(id, res.stack(), res.coveredBy()));
			}
		}
		return out;
	}

	private Slot donatedSlot(String id, InventoryDecoder.Stack stack, String coveredBy) {
		return new Slot(id, stack, false, coveredBy, donatedTimeMs(id));
	}

	private JsonObject museumItemEntry(String donationId) {
		if (donationId == null || donationId.isBlank() || this.museumMember == null) {
			return null;
		}
		JsonObject items = this.museumMember.has("items") && this.museumMember.get("items").isJsonObject()
			? this.museumMember.getAsJsonObject("items")
			: null;
		if (items == null) {
			return null;
		}
		if (items.has(donationId) && items.get(donationId).isJsonObject()) {
			return items.getAsJsonObject(donationId);
		}
		String upper = donationId.toUpperCase(Locale.ROOT);
		for (var e : items.entrySet()) {
			if (e.getKey() != null && e.getKey().toUpperCase(Locale.ROOT).equals(upper)
				&& e.getValue().isJsonObject()) {
				return e.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static Boolean readAppraisal(JsonObject museumMember) {
		if (museumMember == null || !museumMember.has("appraisal")
			|| !museumMember.get("appraisal").isJsonPrimitive()) {
			return null;
		}
		try {
			return museumMember.get("appraisal").getAsBoolean();
		} catch (Exception ignored) {
			return null;
		}
	}

	private long donatedTimeMs(String donationId) {
		if (donationId == null || donationId.isBlank() || this.museumMember == null) {
			return 0L;
		}
		if (isSpecialKey(donationId)) {
			return readDonatedTime(specialEntry(donationId));
		}
		JsonObject items = this.museumMember.has("items") && this.museumMember.get("items").isJsonObject()
			? this.museumMember.getAsJsonObject("items")
			: null;
		if (items == null) {
			return 0L;
		}
		JsonObject entry = null;
		if (items.has(donationId) && items.get(donationId).isJsonObject()) {
			entry = items.getAsJsonObject(donationId);
		} else {
			String upper = donationId.toUpperCase(Locale.ROOT);
			for (var e : items.entrySet()) {
				if (e.getKey() != null && e.getKey().toUpperCase(Locale.ROOT).equals(upper)
					&& e.getValue().isJsonObject()) {
					entry = e.getValue().getAsJsonObject();
					break;
				}
			}
		}
		return readDonatedTime(entry);
	}

	/** {@code SPECIAL:<index>[:<stack>]} → {@code special[index]}. */
	private JsonObject specialEntry(String key) {
		if (!this.museumMember.has("special") || !this.museumMember.get("special").isJsonArray()) {
			return null;
		}
		String[] parts = key.split(":");
		if (parts.length < 2) {
			return null;
		}
		int index;
		try {
			index = Integer.parseInt(parts[1]);
		} catch (NumberFormatException ignored) {
			return null;
		}
		var special = this.museumMember.getAsJsonArray("special");
		if (index < 0 || index >= special.size() || !special.get(index).isJsonObject()) {
			return null;
		}
		return special.get(index).getAsJsonObject();
	}

	private static long readDonatedTime(JsonObject entry) {
		if (entry == null || !entry.has("donated_time") || !entry.get("donated_time").isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, entry.get("donated_time").getAsLong());
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static String trimToWidth(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (ellipsisW >= maxW) {
			return "";
		}
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end)) + ellipsisW > maxW) {
			end--;
		}
		return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
	}
}

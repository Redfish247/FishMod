/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page;

import fishmod.pv.data.BossCollections;
import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.collections.CollectionsUi;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelCollectionsCache;
import fishmod.pv.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static fishmod.pv.gui.collections.CollectionsUi.*;
import static fishmod.pv.gui.collections.page.CollectionPopupLayout.CELL;
import static fishmod.pv.gui.collections.page.CollectionPopupLayout.ROW_H;

/** One collection's tiers in a calculator-style popup over the board. */
final class CollectionDetailView {
	enum TierState { UNLOCKED, NEXT, UPCOMING }

	private static final int OPEN_MS = 220;
	private static final float OPEN_SCALE_START = 0.12F;
	private static final int CLOSE = 12;
	private static final int TRACK_GAP = 6;
	private static final int MAX_TRACK_GAP = 40;
	private static final int ARROW = 12;
	private static final int SLOT_BG = 0x1CFFFFFF;
	private static final int SLOT_BORDER = 0x30FFFFFF;
	private static final int LOCKED_SHADE = 0x88000000;
	private static final int ROW_HOVER = 0x24FFFFFF;
	private static final int ROW_CHOSEN = 0x1CFFFFFF;
	// Same navy as PvDraw.COLOR_PANEL, nearly opaque so the board doesn't read through text and icons.
	private static final int POPUP_BG = 0xF7101018;
	private static final int BACKDROP = 0xB0000000;

	private HypixelCollectionsCache.Item item;
	private long openStartMs;
	private int selected = -1;
	private int selectedReward = -1;
	private int page;
	private int perPage = 1;
	private int trackGap = TRACK_GAP;
	private int scroll;
	private int maxScroll;
	private CollectionPopupLayout layout;
	private CollectionPopupLayout.Content content;
	private boolean contentFinal;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeX;
	private int closeY;
	private final int[] list = new int[4];
	private final int[] track = new int[4];
	private int[] prevArrow;
	private int[] nextArrow;
	private final List<int[]> slots = new ArrayList<>();
	private final List<int[]> rewardRows = new ArrayList<>();
	private List<PvTooltip.Line> hoverTip = List.of();
	private String hoverItemId;
	private String hoverItemHint;
	private String cachedTipId;
	private List<Component> cachedTip = List.of();

	boolean isOpen() {
		return this.item != null;
	}

	void open(HypixelCollectionsCache.Item item) {
		this.item = item;
		this.openStartMs = System.currentTimeMillis();
		this.selected = -1;
		this.selectedReward = -1;
		this.page = 0;
		this.perPage = 0;
		this.scroll = 0;
		this.maxScroll = 0;
		this.content = null;
		this.contentFinal = false;
		this.slots.clear();
		this.rewardRows.clear();
	}

	void close() {
		this.item = null;
		this.hoverTip = List.of();
		this.hoverItemId = null;
		this.slots.clear();
		this.rewardRows.clear();
	}

	int selected() {
		return this.selected;
	}

	int selectedReward() {
		return this.selectedReward;
	}

	int page() {
		return this.page;
	}

	List<int[]> visibleSlots() {
		return this.slots;
	}

	int pageCount() {
		return this.item == null ? 0 : pageCount(this.item.tiers().size(), this.perPage);
	}

	boolean keyPressed(int key) {
		if (this.item != null && key == 256) {
			close();
			return true;
		}
		return false;
	}

	/** Every click is consumed while open; clicking outside the panel closes it. */
	boolean mouseClicked(double mx, double my) {
		if (this.item == null) {
			return false;
		}
		if (mx >= this.closeX && mx < this.closeX + CLOSE && my >= this.closeY && my < this.closeY + CLOSE) {
			close();
			return true;
		}
		if (hit(this.prevArrow, mx, my)) {
			setPage(this.page - 1);
			return true;
		}
		if (hit(this.nextArrow, mx, my)) {
			setPage(this.page + 1);
			return true;
		}
		for (int[] slot : this.slots) {
			if (hit(slot, mx, my)) {
				selectTier(slot[4]);
				return true;
			}
		}
		if (hit(this.list, mx, my)) {
			for (int[] row : this.rewardRows) {
				if (hit(row, mx, my)) {
					this.selectedReward = row[4];
					return true;
				}
			}
		}
		if (mx < this.panelX || mx >= this.panelX + this.panelW || my < this.panelY || my >= this.panelY + this.panelH) {
			close();
		}
		return true;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.item == null) {
			return false;
		}
		if (hit(this.list, mouseX, mouseY)) {
			CollectionsUi.scrollBy(scrollY, this.scroll, this.maxScroll, ROW_H, v -> this.scroll = v);
		} else if (hit(this.track, mouseX, mouseY) && scrollY != 0) {
			setPage(this.page + (scrollY < 0 ? 1 : -1));
		}
		return true;
	}

	private void selectTier(int index) {
		if (index != this.selected) {
			this.selected = index;
			this.selectedReward = -1;
			this.scroll = 0;
		}
	}

	private void setPage(int next) {
		int clamped = Math.max(0, Math.min(pageCount() - 1, next));
		if (clamped != this.page) {
			this.page = clamped;
			selectTier(Math.min(this.item.tiers().size() - 1, this.page * this.perPage));
		}
	}

	private static boolean hit(int[] r, double mx, double my) {
		return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	/** Fits the panel inside the board ({@code px, py, pw, ph}) it pops over. */
	void layoutPanel(int px, int py, int pw, int ph, int lineH, CollectionPopupLayout.Content content) {
		CollectionPopupLayout l = CollectionPopupLayout.compute(px, py, pw, ph, lineH, content);
		this.layout = l;
		this.panelX = l.x();
		this.panelY = l.y();
		this.panelW = l.w();
		this.panelH = l.h();
		this.closeX = l.innerX() + l.innerW() - CLOSE;
		this.closeY = l.headerY() + (CollectionPopupLayout.HEADER_H - CLOSE) / 2;
	}

	/** Tiers that fit across {@code width}; arrows only take room when paging is needed. */
	static int perPage(int tierCount, int width) {
		if (tierCount <= 0) {
			return 1;
		}
		if (tierCount * SLOT + (tierCount - 1) * TRACK_GAP <= width) {
			return tierCount;
		}
		int room = width - (ARROW + 4) * 2;
		return Math.max(1, (room + TRACK_GAP) / (SLOT + TRACK_GAP));
	}

	static int pageCount(int tierCount, int perPage) {
		return perPage <= 0 ? 1 : Math.max(1, (tierCount + perPage - 1) / perPage);
	}

	/** Spreads one page of tiers across the width so the connectors read as one track, up to a max gap. */
	static int trackGap(int count, int width) {
		if (count <= 1) {
			return TRACK_GAP;
		}
		return Math.max(TRACK_GAP, Math.min(MAX_TRACK_GAP, (width - count * SLOT) / (count - 1)));
	}

	/** The tier being worked towards, or the last tier once everything is unlocked. */
	static int defaultSelection(HypixelCollectionsCache.Item item, int displayTier) {
		List<HypixelCollectionsCache.Tier> tiers = item.tiers();
		for (int i = 0; i < tiers.size(); i++) {
			if (tiers.get(i).tier() > displayTier) {
				return i;
			}
		}
		return Math.max(0, tiers.size() - 1);
	}

	/** First reward with a recipe to preview, else the first item, else nothing. */
	static int defaultReward(List<CollectionRewards.Reward> rewards, java.util.function.Predicate<String> hasRecipe) {
		int firstItem = -1;
		for (int i = 0; i < rewards.size(); i++) {
			CollectionRewards.Reward r = rewards.get(i);
			if (!r.isItem()) {
				continue;
			}
			if (hasRecipe.test(r.itemId())) {
				return i;
			}
			if (firstItem < 0) {
				firstItem = i;
			}
		}
		return firstItem;
	}

	/** Places arrows and the visible slots; slot rects are {x, y, w, h, tierIndex}. */
	void layoutTrack(int x, int y, int w, int displayTier) {
		List<HypixelCollectionsCache.Tier> tiers = this.item.tiers();
		if (this.selected < 0 || this.selected >= tiers.size()) {
			this.selected = defaultSelection(this.item, displayTier);
		}
		int fit = perPage(tiers.size(), w);
		if (fit != this.perPage) {
			this.perPage = fit;
			this.page = this.selected / fit;
		}
		int pages = pageCount(tiers.size(), fit);
		this.page = Math.max(0, Math.min(pages - 1, this.page));
		this.slots.clear();
		this.prevArrow = null;
		this.nextArrow = null;
		this.track[0] = x;
		this.track[1] = y;
		this.track[2] = w;
		this.track[3] = SLOT + 12;
		int from = this.page * fit;
		int count = Math.min(fit, tiers.size() - from);
		int startX = x;
		this.trackGap = TRACK_GAP;
		if (pages > 1) {
			this.prevArrow = new int[] {x, y, ARROW, SLOT};
			this.nextArrow = new int[] {x + w - ARROW, y, ARROW, SLOT};
			startX = x + ARROW + 4;
		} else {
			this.trackGap = trackGap(count, w);
		}
		for (int i = 0; i < count; i++) {
			this.slots.add(new int[] {startX + i * (SLOT + this.trackGap), y, SLOT, SLOT, from + i});
		}
	}

	void render(
		CollectionSnapshot snapshot, GuiGraphicsExtractor g, Font font, int screenW, int screenH,
		int boardX, int boardY, int boardW, int boardH, int mx, int my
	) {
		HypixelCollectionsCache.Item item = this.item;
		if (item == null) {
			return;
		}
		this.hoverTip = List.of();
		this.hoverItemId = null;
		this.hoverItemHint = null;
		layoutPanel(boardX, boardY, boardW, boardH, font.lineHeight, content(snapshot, font, item));

		PvDraw.fill(g, 0, 0, screenW, screenH, BACKDROP);
		float scale = openScale();
		float pcx = this.panelX + this.panelW / 2F;
		float pcy = this.panelY + this.panelH / 2F;
		g.pose().pushMatrix();
		if (scale < 0.999F) {
			g.pose().translate(pcx, pcy);
			g.pose().scale(scale, scale);
			g.pose().translate(-pcx, -pcy);
		}
		PvDraw.roundedFill(g, this.panelX, this.panelY, this.panelW, this.panelH, 3, POPUP_BG);
		PvDraw.roundedRing(g, this.panelX, this.panelY, this.panelW, this.panelH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		drawContent(snapshot, g, font, item, mx, my);
		g.pose().popMatrix();

		g.nextStratum();
		if (this.hoverItemId != null) {
			List<Component> tip = new ArrayList<>(itemTip(this.hoverItemId));
			if (this.hoverItemHint != null) {
				tip.add(Component.empty());
				tip.add(SkyBlockItemFactory.legacyLine("\u00a78" + this.hoverItemHint));
			}
			PvTooltip.drawComponents(g, font, tip, mx, my, screenW, screenH);
		} else if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawStyled(g, font, this.hoverTip, mx, my, screenW, screenH);
		}
	}

	/** Measured once per collection, again only while NEU or the item list is still loading. */
	private CollectionPopupLayout.Content content(CollectionSnapshot snapshot, Font font, HypixelCollectionsCache.Item item) {
		if (this.content != null && this.contentFinal) {
			return this.content;
		}
		int maxRewards = 0;
		boolean recipes = false;
		int rewardW = 0;
		int titleW = 0;
		for (HypixelCollectionsCache.Tier t : item.tiers()) {
			maxRewards = Math.max(maxRewards, t.unlocks().size());
			for (String unlock : t.unlocks()) {
				CollectionRewards.Reward r = CollectionRewards.resolve(unlock);
				rewardW = Math.max(rewardW, rewardRowW(font, r));
				if (r.isItem() && CollectionRewards.recipe(r.itemId()) != null) {
					recipes = true;
					titleW = Math.max(titleW, font.width(r.label()));
				}
			}
		}
		long amount = snapshot.progressAmount(item);
		int tier = snapshot.displayTier(item);
		int headerW = 20 + PvDraw.widthBold(font, item.name()) + 8 + font.width(tierText(item, tier)) + 6 + CLOSE;
		int statsW = statsLeftW(snapshot, font, item, amount) + 12
			+ font.width(nextText(item, amount, tier, snapshot.legacyTier(item)));
		this.content = new CollectionPopupLayout.Content(
			item.tiers().size(), maxRewards, recipes, headerW, statsW, rewardW, titleW);
		this.contentFinal = NeuRepoCache.isReady() && !HypixelItemsCache.allItems().isEmpty();
		return this.content;
	}

	private static int rewardRowW(Font font, CollectionRewards.Reward r) {
		return 20 + font.width(r.label()) + (r.suffix().isEmpty() ? 0 : 6 + font.width(r.suffix())) + 4;
	}

	static List<PvTooltip.Line> legacyTip(HypixelCollectionsCache.Item item, int legacyTier) {
		return List.of(
			PvTooltip.Line.title(item.name() + " " + roman(legacyTier), PvDraw.COLOR_GOLD),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("This profile still lists tier " + roman(legacyTier) + " from before", PvDraw.COLOR_TEXT),
			PvTooltip.Line.of("Hypixel cut the collection to " + item.maxTiers() + " tiers.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.meta("Tiers above " + roman(item.maxTiers()) + " no longer exist.")
		);
	}

	static String tierText(HypixelCollectionsCache.Item item, int tier) {
		return "Tier " + tier + " / " + item.maxTiers();
	}

	private static boolean isMaxed(HypixelCollectionsCache.Item item, int tier) {
		return item.maxTiers() > 0 && tier >= item.maxTiers();
	}

	static String nextText(HypixelCollectionsCache.Item item, long amount, int tier, int legacyTier) {
		HypixelCollectionsCache.Tier next = isMaxed(item, tier) ? null : item.nextTier(amount);
		if (next == null) {
			return legacyTier > 0 ? "Maxed (old tier " + roman(legacyTier) + ")" : "Maxed";
		}
		return "Next tier: " + FormatUtil.shortXp(amount) + " / " + FormatUtil.shortXp(next.amountRequired())
			+ " (" + Math.round(item.progressToNext(amount) * 100f) + "%)";
	}

	private static boolean isCoop(CollectionSnapshot snapshot, HypixelCollectionsCache.Item item) {
		return !BossCollections.isBossId(item.id()) && snapshot.members().size() > 1;
	}

	private static String amountLabel(CollectionSnapshot snapshot, HypixelCollectionsCache.Item item) {
		return BossCollections.isBossId(item.id()) ? "Kills" : isCoop(snapshot, item) ? "Coop total" : "Collected";
	}

	private static int statsLeftW(CollectionSnapshot snapshot, Font font, HypixelCollectionsCache.Item item, long amount) {
		int w = font.width(amountLabel(snapshot, item)) + 4 + font.width(FormatUtil.commas(amount));
		if (isCoop(snapshot, item)) {
			w += 10 + font.width("You") + 4 + font.width(FormatUtil.commas(snapshot.viewedAmount(item.id())));
		}
		return w;
	}

	private void drawContent(
		CollectionSnapshot snapshot, GuiGraphicsExtractor g, Font font, HypixelCollectionsCache.Item item, int mx, int my
	) {
		CollectionPopupLayout l = this.layout;
		int x = l.innerX();
		int w = l.innerW();
		long amount = snapshot.progressAmount(item);
		int tier = snapshot.displayTier(item);
		boolean maxed = isMaxed(item, tier);

		String iconId = CollectionsUi.resolveIconId(item.id());
		int headTextY = PvDraw.IconTextAlign.of(l.headerY(), CollectionPopupLayout.HEADER_H, ITEM_ICON, font.lineHeight).textY();
		CollectionsUi.drawIcon(g, iconId, x, l.headerY(), ITEM_ICON);
		boolean closeHover = mx >= this.closeX && mx < this.closeX + CLOSE && my >= this.closeY && my < this.closeY + CLOSE;
		PvDraw.roundedFill(g, this.closeX, this.closeY, CLOSE, CLOSE, 3, closeHover ? 0xFF3A3A4A : 0x24FFFFFF);
		PvDraw.roundedRing(g, this.closeX, this.closeY, CLOSE, CLOSE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, "x", this.closeX + CLOSE / 2, this.closeY + 2, PvDraw.COLOR_MUTED);
		if (closeHover) {
			this.hoverTip = List.of(PvTooltip.Line.of("Close", PvDraw.COLOR_TEXT));
		}
		String tierText = tierText(item, tier);
		int tierRight = this.closeX - 6;
		PvDraw.textRight(g, font, tierText, tierRight, headTextY, maxed ? MINION_UNLOCKED : PvDraw.COLOR_TEXT);
		int nameW = tierRight - font.width(tierText) - 8 - (x + 20);
		String name = CollectionsUi.trim(font, item.name(), nameW);
		PvDraw.textBold(g, font, name, x + 20, headTextY, PvDraw.COLOR_GOLD);
		if (!name.equals(item.name()) && mx >= x && mx < x + 20 + nameW && my >= l.headerY()
			&& my < l.headerY() + CollectionPopupLayout.HEADER_H) {
			this.hoverTip = List.of(PvTooltip.Line.of(item.name(), PvDraw.COLOR_GOLD));
		}

		String label = amountLabel(snapshot, item);
		PvDraw.text(g, font, label, x, l.statsY(), PvDraw.COLOR_MUTED);
		int sx = x + font.width(label) + 4;
		String total = FormatUtil.commas(amount);
		PvDraw.text(g, font, total, sx, l.statsY(), PvDraw.COLOR_GOLD);
		if (isCoop(snapshot, item)) {
			sx += font.width(total) + 10;
			PvDraw.text(g, font, "You", sx, l.statsY(), PvDraw.COLOR_MUTED);
			PvDraw.text(g, font, FormatUtil.commas(snapshot.viewedAmount(item.id())), sx + font.width("You") + 4,
				l.statsY(), PvDraw.COLOR_ACCENT);
		}
		int legacy = snapshot.legacyTier(item);
		String nextText = nextText(item, amount, tier, legacy);
		int nextColor = maxed ? MINION_UNLOCKED : PvDraw.COLOR_MUTED;
		int nextW = Math.min(w, font.width(nextText));
		int nextX = l.nextInline() ? x + w - nextW : x;
		int nextY = l.nextInline() ? l.statsY() : l.nextY();
		PvDraw.text(g, font, CollectionsUi.trim(font, nextText, w), nextX, nextY, nextColor);
		if (legacy > 0 && mx >= nextX && mx < nextX + nextW && my >= nextY && my < nextY + font.lineHeight) {
			this.hoverTip = legacyTip(item, legacy);
		}
		PvDraw.progressBar(g, x, l.barY(), w, PvDraw.BAR_HEIGHT, maxed ? 1f : item.progressToNext(amount),
			PvDraw.COLOR_BAR_FILL, maxed);

		List<HypixelCollectionsCache.Tier> tiers = item.tiers();
		if (tiers.isEmpty()) {
			PvDraw.text(g, font, "No tier data", x, l.trackY(), PvDraw.COLOR_MUTED);
			this.slots.clear();
			this.rewardRows.clear();
			this.list[2] = 0;
			this.track[2] = 0;
			this.maxScroll = 0;
			return;
		}
		layoutTrack(x, l.trackY(), w, tier);
		if (l.hasTiersHeader()) {
			int pages = pageCount(tiers.size(), this.perPage);
			if (pages > 1) {
				String pageText = "Page " + (this.page + 1) + " / " + pages;
				PvDraw.sectionHeader(g, font, "Tiers", x, l.tiersY(), w - font.width(pageText) - 4);
				PvDraw.textRight(g, font, pageText, x + w, l.tiersY(), PvDraw.COLOR_MUTED);
			} else {
				PvDraw.sectionHeader(g, font, "Tiers", x, l.tiersY(), w);
			}
		}
		drawTrack(g, font, item, amount, tier, iconId, mx, my);

		List<CollectionRewards.Reward> rewards = drawSelected(g, font, item, amount, tier, mx, my);
		PvDraw.fill(g, l.recipeX() - CollectionPopupLayout.COL_GAP / 2, l.columnsY(), 1,
			Math.max(0, l.bottom() - l.columnsY()), SLOT_BORDER);
		drawRecipe(g, font, rewards, mx, my);
	}

	private void drawTrack(
		GuiGraphicsExtractor g, Font font, HypixelCollectionsCache.Item item, long amount, int tier, String iconId,
		int mx, int my
	) {
		List<HypixelCollectionsCache.Tier> tiers = item.tiers();
		for (int[] arrow : new int[][] {this.prevArrow, this.nextArrow}) {
			if (arrow == null) {
				continue;
			}
			boolean prev = arrow == this.prevArrow;
			boolean enabled = prev ? this.page > 0 : this.page < pageCount(tiers.size(), this.perPage) - 1;
			boolean hover = enabled && hit(arrow, mx, my);
			PvDraw.roundedFill(g, arrow[0], arrow[1], arrow[2], arrow[3], 3, hover ? 0xFF3A3A4A : 0x24FFFFFF);
			PvDraw.roundedRing(g, arrow[0], arrow[1], arrow[2], arrow[3], 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
			PvDraw.textCentered(g, font, prev ? "<" : ">", arrow[0] + arrow[2] / 2,
				arrow[1] + (arrow[3] - font.lineHeight) / 2 + 1, enabled ? PvDraw.COLOR_TEXT : SLOT_BORDER);
			if (hover) {
				this.hoverTip = List.of(PvTooltip.Line.of(prev ? "Previous tiers" : "More tiers", PvDraw.COLOR_TEXT));
			}
		}
		for (int i = 0; i < this.slots.size(); i++) {
			int[] s = this.slots.get(i);
			HypixelCollectionsCache.Tier t = tiers.get(s[4]);
			TierState state = tierState(item, t, amount, tier);
			if (i + 1 < this.slots.size()) {
				TierState after = tierState(item, tiers.get(s[4] + 1), amount, tier);
				PvDraw.fill(g, s[0] + SLOT, s[1] + SLOT / 2 - 1, this.trackGap, 2,
					after == TierState.UNLOCKED ? MINION_UNLOCKED : SLOT_BORDER);
			}
			boolean hover = hit(s, mx, my);
			boolean chosen = s[4] == this.selected;
			PvDraw.fill(g, s[0], s[1], SLOT, SLOT, SLOT_BG);
			CollectionsUi.drawIcon(g, iconId, s[0] + (SLOT - ITEM_ICON) / 2, s[1] + (SLOT - ITEM_ICON) / 2, ITEM_ICON);
			if (state == TierState.UPCOMING) {
				PvDraw.fill(g, s[0] + 1, s[1] + 1, SLOT - 2, SLOT - 2, LOCKED_SHADE);
			}
			int border = chosen ? PvDraw.COLOR_ACCENT : hover ? PvDraw.COLOR_TEXT : slotBorder(state);
			g.outline(s[0], s[1], SLOT, SLOT, border);
			if (state == TierState.NEXT) {
				float fill = item.progressToNext(amount);
				PvDraw.fill(g, s[0] + 1, s[1] + SLOT - 3, SLOT - 2, 2, PvDraw.COLOR_BAR_BG);
				PvDraw.fill(g, s[0] + 1, s[1] + SLOT - 3, Math.round((SLOT - 2) * Math.max(0f, Math.min(1f, fill))), 2, PvDraw.COLOR_BAR_FILL);
			}
			PvDraw.textCentered(g, font, roman(t.tier()), s[0] + SLOT / 2, s[1] + SLOT + 2, stateColor(state));
			if (hover) {
				List<PvTooltip.Line> tip = new ArrayList<>(tierTip(item, t, amount, tier));
				tip.add(PvTooltip.Line.meta(chosen ? "Shown below" : "Click to show rewards"));
				this.hoverTip = tip;
			}
		}
	}

	private List<CollectionRewards.Reward> drawSelected(
		GuiGraphicsExtractor g, Font font, HypixelCollectionsCache.Item item, long amount, int tier, int mx, int my
	) {
		CollectionPopupLayout l = this.layout;
		int x = l.innerX();
		int w = l.innerW();
		this.rewardRows.clear();
		this.list[2] = 0;
		if (this.selected < 0 || this.selected >= item.tiers().size()) {
			this.maxScroll = 0;
			return List.of();
		}
		HypixelCollectionsCache.Tier t = item.tiers().get(this.selected);
		TierState state = tierState(item, t, amount, tier);
		int color = stateColor(state);
		String stateText = switch (state) {
			case UNLOCKED -> "Unlocked";
			case NEXT -> "In progress";
			case UPCOMING -> "Locked";
		};
		PvDraw.textRight(g, font, stateText, x + w, l.selectedY(), color);
		PvDraw.textBold(g, font, CollectionsUi.trim(font, item.name() + " " + roman(t.tier()),
			w - font.width(stateText) - 8), x, l.selectedY(), color);

		long required = Math.max(1L, t.amountRequired());
		boolean unlocked = state == TierState.UNLOCKED;
		float fill = unlocked ? 1f : (float) Math.min(1D, Math.max(0D, amount / (double) required));
		String progress = unlocked
			? FormatUtil.commas(t.amountRequired()) + " / " + FormatUtil.commas(t.amountRequired())
			: FormatUtil.commas(amount) + " / " + FormatUtil.commas(t.amountRequired()) + " (" + Math.round(fill * 100f) + "%)";
		int progressW = font.width(progress);
		PvDraw.textRight(g, font, progress, x + w, l.selectedBarY(), unlocked ? MINION_UNLOCKED : PvDraw.COLOR_TEXT);
		int barW = w - progressW - 8;
		if (barW > 12) {
			PvDraw.progressBar(g, x, l.selectedBarY() + (font.lineHeight - PvDraw.BAR_HEIGHT) / 2, barW,
				PvDraw.BAR_HEIGHT, fill, PvDraw.COLOR_BAR_FILL);
		}

		int rw = l.rewardsW();
		PvDraw.sectionHeader(g, font, "Rewards", x, l.columnsY(), rw);
		int y = l.columnsBodyY(font.lineHeight);
		int bottom = l.bottom();
		List<CollectionRewards.Reward> rewards = new ArrayList<>();
		for (String unlock : t.unlocks()) {
			rewards.add(CollectionRewards.resolve(unlock));
		}
		if (this.selectedReward < 0 || this.selectedReward >= rewards.size()) {
			this.selectedReward = defaultReward(rewards, id -> CollectionRewards.recipe(id) != null);
		}
		int listH = Math.max(0, bottom - y);
		this.list[0] = x - 2;
		this.list[1] = y;
		this.list[2] = rw + 4;
		this.list[3] = listH;
		this.maxScroll = Math.max(0, rewards.size() * ROW_H - listH);
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));
		if (rewards.isEmpty()) {
			PvDraw.text(g, font, "Rewards not in the API", x, y + 2, PvDraw.COLOR_MUTED);
			return rewards;
		}
		boolean inList = hit(this.list, mx, my);
		g.enableScissor(this.list[0], y, this.list[0] + this.list[2], y + listH);
		int rowY = y - this.scroll;
		for (int i = 0; i < rewards.size(); i++, rowY += ROW_H) {
			if (rowY + ROW_H <= y || rowY >= y + listH) {
				continue;
			}
			CollectionRewards.Reward r = rewards.get(i);
			int[] row = {x - 2, rowY, rw + 4, ROW_H, i};
			this.rewardRows.add(row);
			boolean hover = inList && hit(row, mx, my);
			boolean chosen = r.isItem() && i == this.selectedReward;
			if (chosen || hover) {
				PvDraw.fill(g, row[0], row[1], row[2], row[3], chosen ? ROW_CHOSEN : ROW_HOVER);
			}
			if (chosen) {
				g.outline(row[0], row[1], row[2], row[3], PvDraw.COLOR_ACCENT);
			}
			PvDraw.IconTextAlign align = PvDraw.IconTextAlign.of(rowY, ROW_H, ITEM_ICON, font.lineHeight);
			if (r.hasIcon()) {
				CollectionsUi.drawIcon(g, r.iconId(), x, align.iconY(), ITEM_ICON);
			}
			int textW = rw - 20 - 2;
			boolean suffix = !r.suffix().isEmpty() && font.width(r.label()) + 6 + font.width(r.suffix()) <= textW;
			if (suffix) {
				PvDraw.textRight(g, font, r.suffix(), x + rw - 2, align.textY(), PvDraw.COLOR_MUTED);
			}
			String label = CollectionsUi.trim(font, r.label(), textW);
			PvDraw.text(g, font, label, x + 20, align.textY(), CollectionRewards.color(r));
			if (hover) {
				if (r.isItem()) {
					this.hoverItemId = r.itemId();
					this.hoverItemHint = chosen ? null : "Click to preview the recipe";
				} else if (!label.equals(r.text())) {
					this.hoverTip = List.of(PvTooltip.Line.of(r.text(), CollectionRewards.color(r)));
				}
			}
		}
		g.disableScissor();
		return rewards;
	}

	private void drawRecipe(GuiGraphicsExtractor g, Font font, List<CollectionRewards.Reward> rewards, int mx, int my) {
		CollectionPopupLayout l = this.layout;
		int x = l.recipeX();
		int w = l.recipeW();
		int bottom = l.bottom();
		CollectionRewards.Reward reward = this.selectedReward >= 0 && this.selectedReward < rewards.size()
			? rewards.get(this.selectedReward) : null;
		CollectionRewards.Recipe recipe = reward == null ? null : CollectionRewards.recipe(reward.itemId());
		PvDraw.sectionHeader(g, font, recipe != null && recipe.forge() ? "Forge recipe" : "Recipe", x, l.columnsY(), w);
		int y = l.columnsBodyY(font.lineHeight);
		if (reward == null || !reward.isItem()) {
			PvDraw.text(g, font, "No item reward", x, y + 2, PvDraw.COLOR_MUTED);
			return;
		}
		int room = bottom - y;
		boolean titled = recipe == null || room >= CollectionPopupLayout.recipeBodyH(font.lineHeight, true) || room < CELL * 3;
		if (titled) {
			String title = CollectionsUi.trim(font, reward.label(), w);
			PvDraw.text(g, font, title, x, y + 2, CollectionRewards.color(reward));
			if (!title.equals(reward.label()) && mx >= x && mx < x + w && my >= y && my < y + font.lineHeight + 2) {
				this.hoverItemId = reward.itemId();
			}
			y += font.lineHeight + 4;
		}
		if (recipe == null) {
			PvDraw.text(g, font, "No recipe data", x, y + 2, PvDraw.COLOR_MUTED);
			return;
		}
		if (y + CELL * 3 > bottom) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			int cx = x + (i % 3) * CELL;
			int cy = y + (i / 3) * CELL;
			drawCell(g, font, recipe.ids()[i], recipe.counts()[i], cx, cy, mx, my);
		}
		int arrowX = x + CELL * 3;
		PvDraw.textCentered(g, font, ">", arrowX + 8, y + CELL + (CELL - font.lineHeight) / 2 + 1, PvDraw.COLOR_MUTED);
		drawCell(g, font, reward.itemId(), recipe.outputCount(), arrowX + 16, y + CELL, mx, my);
	}

	private void drawCell(GuiGraphicsExtractor g, Font font, String id, int count, int x, int y, int mx, int my) {
		PvDraw.roundedFill(g, x, y, CELL, CELL, 3, SLOT_BG);
		PvDraw.roundedRing(g, x, y, CELL, CELL, 3, SLOT_BORDER, PvDraw.C_ALL);
		if (id == null) {
			return;
		}
		CollectionsUi.drawIcon(g, id, x + 1, y + 1, ITEM_ICON);
		if (count > 1) {
			String text = count > 999 ? FormatUtil.shortXp(count) : String.valueOf(count);
			PvDraw.textRight(g, font, text, x + CELL, y + CELL - font.lineHeight + 1, PvDraw.COLOR_TEXT);
		}
		if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
			g.outline(x, y, CELL, CELL, PvDraw.COLOR_TEXT);
			this.hoverItemId = id;
			this.hoverItemHint = count > 1 ? "x" + FormatUtil.commas(count) : null;
		}
	}

	private List<Component> itemTip(String id) {
		if (!id.equals(this.cachedTipId)) {
			InventorySnapshot.Slot slot = petSlot(id);
			if (slot == null) {
				slot = new InventorySnapshot.Slot(id, 1, List.of(), null, null, null, null);
			}
			this.cachedTip = SkyBlockItemFactory.tooltipLines(slot, SkyBlockItemFactory.toStack(slot), false);
			this.cachedTipId = id;
		}
		return this.cachedTip;
	}

	// NEU pet lore is full of {STAT} placeholders; build it like an auction pet so the numbers fill in.
	private static InventorySnapshot.Slot petSlot(String id) {
		int semi = id.indexOf(';');
		if (semi <= 0 || !CollectionRewards.isPet(id)) {
			return null;
		}
		String tier = SkyBlockItemFactory.resolveTier(id);
		return tier.isBlank() ? null : SkyBlockItemFactory.auctionPetSlot("PET_" + id.substring(0, semi), "[Lvl 1]", tier);
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openStartMs;
		if (elapsed >= OPEN_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_MS));
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private static int slotBorder(TierState state) {
		return switch (state) {
			case UNLOCKED -> MINION_UNLOCKED;
			case NEXT -> PvDraw.COLOR_GOLD;
			case UPCOMING -> SLOT_BORDER;
		};
	}

	private static int stateColor(TierState state) {
		return switch (state) {
			case UNLOCKED -> MINION_UNLOCKED;
			case NEXT -> PvDraw.COLOR_GOLD;
			case UPCOMING -> PvDraw.COLOR_MUTED;
		};
	}

	/** {@code displayTier} already includes tiers Hypixel lists as unlocked even when amounts lag. */
	static TierState tierState(HypixelCollectionsCache.Item item, HypixelCollectionsCache.Tier tier, long amount, int displayTier) {
		if (tier.tier() <= displayTier) {
			return TierState.UNLOCKED;
		}
		for (HypixelCollectionsCache.Tier t : item.tiers()) {
			if (t.tier() > displayTier) {
				return t.tier() == tier.tier() ? TierState.NEXT : TierState.UPCOMING;
			}
		}
		return TierState.UPCOMING;
	}

	/** Recipe / perk unlocks first; SkyBlock XP only when that's all the tier gives. */
	static String rewardSummary(HypixelCollectionsCache.Tier tier) {
		List<String> main = new ArrayList<>();
		String xp = null;
		for (String unlock : tier.unlocks()) {
			if (isSkyBlockXp(unlock)) {
				xp = unlock;
			} else {
				main.add(unlock);
			}
		}
		if (main.isEmpty()) {
			return xp == null ? "-" : xp;
		}
		return main.size() == 1 ? main.get(0) : main.get(0) + " +" + (main.size() - 1) + " more";
	}

	static List<PvTooltip.Line> tierTip(
		HypixelCollectionsCache.Item item,
		HypixelCollectionsCache.Tier tier,
		long amount,
		int displayTier
	) {
		TierState state = tierState(item, tier, amount, displayTier);
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(item.name() + " " + roman(tier.tier()), stateColor(state)));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row("Required", PvDraw.COLOR_MUTED, FormatUtil.commas(tier.amountRequired()), PvDraw.COLOR_TEXT));
		if (state == TierState.UNLOCKED) {
			lines.add(PvTooltip.Line.of("Unlocked", MINION_UNLOCKED));
		} else {
			long required = Math.max(1L, tier.amountRequired());
			int pct = (int) Math.min(100L, Math.max(0L, amount * 100L / required));
			lines.add(PvTooltip.Line.row("Progress", PvDraw.COLOR_MUTED,
				FormatUtil.commas(amount) + " / " + FormatUtil.commas(tier.amountRequired()) + " (" + pct + "%)",
				PvDraw.COLOR_TEXT));
			lines.add(PvTooltip.Line.row("Remaining", PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.max(0L, tier.amountRequired() - amount)), PvDraw.COLOR_TEXT));
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(PvTooltip.Line.of("Rewards", PvDraw.COLOR_MUTED));
		if (tier.unlocks().isEmpty()) {
			lines.add(PvTooltip.Line.meta("Rewards not in the API"));
		} else {
			for (String unlock : tier.unlocks()) {
				CollectionRewards.Reward reward = CollectionRewards.resolve(unlock);
				lines.add(PvTooltip.Line.of(reward.text(), CollectionRewards.color(reward)));
			}
		}
		return lines;
	}

	private static boolean isSkyBlockXp(String unlock) {
		return unlock != null && unlock.endsWith("SkyBlock XP");
	}

	static String roman(int n) {
		if (n <= 0) {
			return String.valueOf(n);
		}
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (n >= values[i]) {
				sb.append(numerals[i]);
				n -= values[i];
			}
		}
		return sb.toString();
	}
}

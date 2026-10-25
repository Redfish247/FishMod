/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.data.BossCollections;
import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.HypixelRanks;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.collections.CollectionCategoryIcons;
import fishmod.pv.gui.collections.CollectionsUi;
import fishmod.pv.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static fishmod.pv.gui.collections.CollectionsUi.*;

/** Collections board subpage. */
public final class CollectionsListPage {
	private int selectedCat;
	private int selectedItem;
	private int gridScroll;
	private int gridMaxScroll;
	private int gridTop;
	private int gridH;
	private final List<ItemHit> itemHits = new ArrayList<>();
	private final CollectionDetailView detail = new CollectionDetailView();
	private final int[] board = new int[4];
	private CollectionSnapshot snapshot;
	private List<PvTooltip.Line> hoverTip = List.of();

	public void reset(CollectionSnapshot snapshot) {
		this.selectedCat = 0;
		this.selectedItem = 0;
		this.gridScroll = 0;
		this.hoverTip = List.of();
		this.detail.close();
	}

	public void render(
		CollectionSnapshot snapshot,
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
		this.itemHits.clear();
		this.hoverTip = List.of();
		this.snapshot = snapshot;
		this.board[0] = x;
		this.board[1] = y;
		this.board[2] = w;
		this.board[3] = h;
		boolean popup = this.detail.isOpen();
		renderCollections(snapshot, g, font, x, y, w, h, popup ? -1 : mouseX, popup ? -1 : mouseY);
		if (popup) {
			this.hoverTip = List.of();
		}
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		if (this.detail.isOpen() && this.snapshot != null) {
			this.detail.render(this.snapshot, g, font, screenW, screenH,
				this.board[0], this.board[1], this.board[2], this.board[3], mouseX, mouseY);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawStyled(g, font, this.hoverTip, mouseX, mouseY, screenW, screenH);
		}
	}

	public boolean keyPressed(int key) {
		return this.detail.keyPressed(key);
	}

	public void closeDetail() {
		this.detail.close();
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.detail.isOpen()) {
			return this.detail.mouseClicked(mx, my);
		}
		for (ItemHit hit : this.itemHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selectedCat = hit.cat;
				this.selectedItem = hit.item;
				this.detail.open(hit.entry);
				return true;
			}
		}
		return false;
	}

	public void setCategory(int index) {
		if (index != this.selectedCat) {
			this.selectedCat = Math.max(0, index);
			this.selectedItem = 0;
			this.detail.close();
		}
	}

	public int category() {
		return this.selectedCat;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return this.detail.mouseScrolled(mouseX, mouseY, scrollY);
	}

	private void renderCollections(
		CollectionSnapshot snapshot,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		PvDraw.innerPanel(g, x, y, w, h);

		List<HypixelCollectionsCache.Category> categories = snapshot.categories();
		if (categories.isEmpty()) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.loading").getString(),
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			return;
		}
		clampSelection(categories);

		this.gridTop = y + PAD;
		this.gridH = Math.max(20, h - PAD * 2);
		drawAllCategories(snapshot, g, font, categories, x + PAD, this.gridTop, w - PAD * 2, this.gridH, mouseX, mouseY);
	}

	private void drawAllCategories(
		CollectionSnapshot snapshot,
		GuiGraphicsExtractor g,
		Font font,
		List<HypixelCollectionsCache.Category> categories,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		if (categories.isEmpty()) {
			return;
		}
		// One category at a time; the side tabs pick which.
		final int cat = Math.max(0, Math.min(this.selectedCat, categories.size() - 1));
		categories = List.of(categories.get(cat));
		int n = 1;

		BoardLayout layout = layoutBoard(categories, w, h);
		int slot = layout.slot;
		int icon = layout.icon;
		int gap = layout.gap;
		int cellH = layout.cellH();
		int header = slot;
		int headerH = header + layout.rowGap;
		int rowPitch = cellH + layout.rowGap;
		int contentH = headerH + layout.maxRows * cellH + Math.max(0, layout.maxRows - 1) * layout.rowGap;

		// No scrolling - board is fitted to the panel.
		this.gridMaxScroll = 0;
		this.gridScroll = 0;

		int startX = x + Math.max(0, (w - layout.neededW) / 2);
		int startY = y + Math.max(0, (h - contentH) / 2);
		float labelScale = icon >= 14 ? 1.0f : icon >= 12 ? 0.8f : 0.7f;

		g.enableScissor(x, y, x + w + 3, y + h);
		int cursorX = startX;
		for (int c = 0; c < n; c++) {
			HypixelCollectionsCache.Category category = categories.get(c);
			List<HypixelCollectionsCache.Item> items = category.items();
			int cols = layout.catCols[c];
			int blockW = cols * slot + Math.max(0, cols - 1) * gap;
			int headerX = cursorX + Math.max(0, (blockW - header) / 2);

			boolean catSelected = true;
			PvDraw.roundedFill(g, headerX, startY, header, header, 3, catSelected ? 0x50386CC8 : PvDraw.CELL);
			PvDraw.roundedRing(g, headerX, startY, header, header, 3, catSelected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
			ItemStack headerIcon = CollectionCategoryIcons.icon(category.id());
			if (!headerIcon.isEmpty()) {
				CollectionsUi.drawVanillaIcon(g, headerIcon, headerX + (header - icon) / 2, startY + (header - icon) / 2, icon);
			}

			for (int i = 0; i < items.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int sx = cursorX + col * (slot + gap);
				int sy = startY + headerH + row * rowPitch;
				HypixelCollectionsCache.Item item = items.get(i);
				int tier = snapshot.displayTier(item);
				boolean selected = i == this.selectedItem;
				boolean hover = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + cellH;
				PvDraw.roundedFill(g, sx, sy, slot, slot, 3, selected ? 0x50386CC8 : hover ? 0x24FFFFFF : PvDraw.CELL);
				PvDraw.roundedRing(g, sx, sy, slot, slot, 3, selected ? PvDraw.COLOR_ACCENT : hover ? 0xFF4A4A5A : 0x30FFFFFF, PvDraw.C_ALL);
				CollectionsUi.drawIcon(g, CollectionsUi.resolveIconId(item.id()), sx + (slot - icon) / 2, sy + (slot - icon) / 2, icon);
				boolean maxed = tier >= item.maxTiers() && item.maxTiers() > 0;
				String label = String.valueOf(tier);
				int labelColor = maxed ? PvDraw.COLOR_GOLD : tier > 0 ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED;
				int labelY = sy + slot + layout.labelGap;
				if (labelScale >= 0.99f) {
					PvDraw.textCentered(g, font, label, sx + slot / 2, labelY, labelColor);
				} else {
					int tw = Math.max(1, Math.round(font.width(label) * labelScale));
					PvDraw.textScaled(g, font, label, sx + (slot - tw) / 2, labelY, labelColor, labelScale);
				}
				this.itemHits.add(new ItemHit(sx, sy, slot, cellH, cat, i, item));
				if (hover) {
					this.hoverTip = collectionHover(snapshot, item);
				}
			}

			cursorX += blockW;
			if (c < n - 1) {
				int lineX = cursorX + layout.catSep / 2;
				PvDraw.fill(g, lineX, startY, 1, contentH, PvDraw.COLOR_DIVIDER);
				cursorX += layout.catSep;
			}
		}
		g.disableScissor();
	}

	private List<PvTooltip.Line> collectionHover(CollectionSnapshot snapshot, HypixelCollectionsCache.Item item) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		long amount = snapshot.progressAmount(item);
		long yours = snapshot.viewedAmount(item.id());
		int tier = snapshot.displayTier(item);
		boolean maxed = tier >= item.maxTiers() && item.maxTiers() > 0;
		boolean boss = BossCollections.isBossId(item.id());

		lines.add(PvTooltip.Line.bold(item.name(), PvDraw.COLOR_GOLD));
		lines.add(PvTooltip.Line.blank());
		if (boss) {
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Kills: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount), PvDraw.COLOR_TEXT)
			)));
		} else {
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Coop total: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount), PvDraw.COLOR_GOLD)
			)));
			if (snapshot.members().size() > 1) {
				lines.add(new PvTooltip.Line(List.of(
					PvTooltip.Span.of("You: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.bold(FormatUtil.shortXp(yours), PvDraw.COLOR_ACCENT)
				)));
			}
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Tier: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(
				tier + " / " + item.maxTiers(),
				maxed ? MINION_UNLOCKED : tier > 0 ? PvDraw.COLOR_TEXT : MINION_LOCKED
			)
		)));
		HypixelCollectionsCache.Tier next = item.nextTier(amount);
		if (maxed || next == null) {
			lines.add(PvTooltip.Line.bold("Maxed", MINION_UNLOCKED));
			int legacy = snapshot.legacyTier(item);
			if (legacy > 0) {
				lines.add(PvTooltip.Line.meta("Old tier " + CollectionDetailView.roman(legacy)
					+ " from before the " + item.maxTiers() + "-tier rework"));
			}
		} else {
			int pct = Math.round(item.progressToNext(amount) * 100f);
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Next: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(
					FormatUtil.shortXp(amount) + " / " + FormatUtil.shortXp(next.amountRequired()),
					PvDraw.COLOR_TEXT
				),
				PvTooltip.Span.of(" (" + pct + "%)", PvDraw.COLOR_ACCENT)
			)));
			HypixelCollectionsCache.Tier maxTier = item.tiers().isEmpty()
				? null
				: item.tiers().get(item.tiers().size() - 1);
			if (maxTier != null && maxTier.amountRequired() > next.amountRequired()) {
				lines.add(new PvTooltip.Line(List.of(
					PvTooltip.Span.of("Max: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(
						FormatUtil.shortXp(amount) + " / " + FormatUtil.shortXp(maxTier.amountRequired()),
						PvDraw.COLOR_TEXT
					)
				)));
			}
		}
		// Shared collections only - boss kills are personal per member.
		if (!boss && snapshot.members().size() > 1) {
			lines.add(PvTooltip.Line.blank());
			lines.add(PvTooltip.Line.bold(
				Component.translatable("betterpv.collections.coop").getString(),
				PvDraw.COLOR_ACCENT
			));
			for (CollectionSnapshot.Member member : snapshot.membersByAmount(item.id())) {
				boolean viewed = member.uuid().equals(snapshot.viewedUuid());
				List<PvTooltip.Span> spans = new ArrayList<>(memberNameSpans(snapshot, member));
				spans.add(PvTooltip.Span.of(": ", PvDraw.COLOR_MUTED));
				spans.add(PvTooltip.Span.bold(
					FormatUtil.shortXp(member.amount(item.id())),
					viewed ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_GOLD
				));
				lines.add(new PvTooltip.Line(spans));
			}
		}
		if (!item.tiers().isEmpty()) {
			lines.add(PvTooltip.Line.blank());
			lines.add(PvTooltip.Line.meta("Click to view tiers"));
		}
		return lines;
	}

	private List<PvTooltip.Span> memberNameSpans(CollectionSnapshot snapshot, CollectionSnapshot.Member member) {
		String name = snapshot.displayName(member);
		JsonObject rankPlayer = snapshot.playerRank(member.uuid());
		if (rankPlayer != null) {
			return HypixelRanks.nameSpans(name, rankPlayer);
		}

		boolean viewed = member.uuid().equals(snapshot.viewedUuid());
		return List.of(PvTooltip.Span.bold(name, viewed ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT));
	}

	private static int lerpRgb(int left, int right, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int lr = (left >> 16) & 0xFF;
		int lg = (left >> 8) & 0xFF;
		int lb = left & 0xFF;
		int rr = (right >> 16) & 0xFF;
		int rg = (right >> 8) & 0xFF;
		int rb = right & 0xFF;
		int r = Math.round(lr + (rr - lr) * t);
		int g = Math.round(lg + (rg - lg) * t);
		int b = Math.round(lb + (rb - lb) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static BoardLayout layoutBoard(List<HypixelCollectionsCache.Category> categories, int w, int h) {
		BoardLayout best = null;
		for (int icon : COLL_ICON_STEPS) {
			// Comfortable metrics first for this icon size, then tighter if needed.
			int[][] packs = icon >= 14
				? new int[][] {{4, 5, 4, 2, 9, 8}, {2, 4, 3, 2, 8, 6}, {2, 3, 2, 1, 8, 6}}
				: new int[][] {{2, 4, 3, 1, 8, 6}, {2, 3, 2, 1, 7, 5}, {2, 2, 2, 1, 7, 4}};
			for (int[] pack : packs) {
				int slotPad = pack[0];
				int gap = pack[1];
				int rowGap = pack[2];
				int labelGap = pack[3];
				int labelH = pack[4];
				int catSep = pack[5];
				int slot = icon + slotPad;
				BoardLayout candidate = packBoard(categories, w, h, icon, slot, gap, rowGap, labelGap, labelH, catSep);
				if (candidate.neededW <= w && candidate.contentH() <= h) {
					return candidate;
				}
				best = candidate;
			}
		}
		return best;
	}

	/** Build column counts for a fixed cell size; prefer fitting height without scroll. */
	private static BoardLayout packBoard(
		List<HypixelCollectionsCache.Category> categories,
		int w,
		int h,
		int icon,
		int slot,
		int gap,
		int rowGap,
		int labelGap,
		int labelH,
		int catSep
	) {
		int n = categories.size();
		int cellH = slot + labelGap + labelH;
		int headerH = slot + rowGap;
		int availRows = Math.max(1, (h - headerH + rowGap) / (cellH + rowGap));

		int[] catCols = new int[n];
		for (int c = 0; c < n; c++) {
			int items = categories.get(c).items().size();
			catCols[c] = Math.max(1, (items + availRows - 1) / availRows);
		}

		while (boardWidth(catCols, slot, gap, catSep) > w) {
			int best = -1;
			int bestCols = 1;
			for (int c = 0; c < n; c++) {
				if (catCols[c] > bestCols) {
					bestCols = catCols[c];
					best = c;
				}
			}
			if (best < 0 || bestCols <= 1) {
				break;
			}
			catCols[best]--;
		}

		boolean grew = true;
		while (grew) {
			grew = false;
			if (w - boardWidth(catCols, slot, gap, catSep) < slot + gap) {
				break;
			}
			int pick = -1;
			int pickRows = 0;
			for (int c = 0; c < n; c++) {
				int items = categories.get(c).items().size();
				int cols = catCols[c];
				int rows = (items + cols - 1) / cols;
				int newRows = (items + cols) / (cols + 1);
				if (newRows < rows && rows > pickRows) {
					pickRows = rows;
					pick = c;
				}
			}
			if (pick < 0) {
				break;
			}
			catCols[pick]++;
			if (boardWidth(catCols, slot, gap, catSep) > w) {
				catCols[pick]--;
				break;
			}
			grew = true;
		}

		int maxRows = 1;
		for (int c = 0; c < n; c++) {
			int items = categories.get(c).items().size();
			maxRows = Math.max(maxRows, (items + catCols[c] - 1) / catCols[c]);
		}
		return new BoardLayout(icon, slot, gap, rowGap, labelGap, labelH, catSep, catCols, boardWidth(catCols, slot, gap, catSep), maxRows);
	}

	private static int boardWidth(int[] catCols, int slot, int gap, int sep) {
		int neededW = 0;
		for (int c = 0; c < catCols.length; c++) {
			int cols = catCols[c];
			neededW += cols * slot + Math.max(0, cols - 1) * gap;
			if (c < catCols.length - 1) {
				neededW += sep;
			}
		}
		return neededW;
	}

	private record BoardLayout(
		int icon,
		int slot,
		int gap,
		int rowGap,
		int labelGap,
		int labelH,
		int catSep,
		int[] catCols,
		int neededW,
		int maxRows
	) {
		int cellH() {
			return slot + labelGap + labelH;
		}

		int contentH() {
			int headerH = slot + rowGap;
			return headerH + maxRows * cellH() + Math.max(0, maxRows - 1) * rowGap;
		}
	}

	private void clampSelection(List<HypixelCollectionsCache.Category> categories) {
		this.selectedCat = Math.max(0, Math.min(this.selectedCat, categories.size() - 1));
		List<HypixelCollectionsCache.Item> items = categories.get(this.selectedCat).items();
		if (items.isEmpty()) {
			this.selectedItem = 0;
			return;
		}
		this.selectedItem = Math.max(0, Math.min(this.selectedItem, items.size() - 1));
	}

	private record ItemHit(int x, int y, int w, int h, int cat, int item, HypixelCollectionsCache.Item entry) {
	}
}

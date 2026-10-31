/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page;

import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MinionCosts;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.collections.CollectionsUi;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelCollectionsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.collections.CollectionsUi.*;

/** Minions grid (grouped by category) or cheapest-next list, plus a tier checklist with upgrade costs. */
public final class MinionsPage {
	private static final long COST_RETRY_MS = 2_000L;

	private int selectedMinion;
	private int minionScroll;
	private int minionMaxScroll;
	private int tierScroll;
	private int tierMaxScroll;
	private int minionTop;
	private int minionH;
	private int tierListTop;
	private int tierListH;
	private boolean cheapestMode;
	private int toggleX;
	private int toggleY;
	private int toggleW;
	private int toggleH;
	private final List<SlotHit> minionHits = new ArrayList<>();
	private List<PvTooltip.Line> hoverTip = List.of();

	private CollectionSnapshot costsFor;
	private boolean costsFinal;
	private long costsAt;
	private final Map<String, List<MinionCosts.TierCost>> missingById = new HashMap<>();
	private final Map<String, Integer> indexById = new HashMap<>();
	private List<MinionCosts.MissingTier> cheapest = List.of();
	private Map<MinionCosts.Category, List<Integer>> groups = Map.of();

	public void reset(CollectionSnapshot snapshot) {
		this.selectedMinion = snapshot == null || snapshot.minions().isEmpty() ? -1 : 0;
		this.minionScroll = 0;
		this.tierScroll = 0;
		this.costsFor = null;
		this.hoverTip = List.of();
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
		int mouseY
	) {
		this.minionHits.clear();
		this.hoverTip = List.of();
		ensureCosts(snapshot);
		renderMinions(snapshot, g, font, x, y, w, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawStyled(g, font, this.hoverTip, mouseX, mouseY, screenW, screenH);
		}
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx >= this.toggleX && mx < this.toggleX + this.toggleW && my >= this.toggleY && my < this.toggleY + this.toggleH) {
			this.cheapestMode = !this.cheapestMode;
			this.minionScroll = 0;
			return true;
		}
		for (SlotHit hit : this.minionHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selectedMinion = hit.index;
				this.tierScroll = 0;
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int step = SLOT + SLOT_GAP;
		if (mouseY >= this.minionTop && mouseY < this.minionTop + this.minionH) {
			return CollectionsUi.scrollBy(scrollY, this.minionScroll, this.minionMaxScroll, step, v -> this.minionScroll = v);
		}
		if (mouseY >= this.tierListTop && mouseY < this.tierListTop + this.tierListH) {
			return CollectionsUi.scrollBy(scrollY, this.tierScroll, this.tierMaxScroll, 12, v -> this.tierScroll = v);
		}
		return false;
	}

	/** Recipes, prices and collection categories load async; recompute until all three are in. */
	private void ensureCosts(CollectionSnapshot snapshot) {
		long now = System.currentTimeMillis();
		if (snapshot == this.costsFor && (this.costsFinal || now - this.costsAt < COST_RETRY_MS)) {
			return;
		}
		this.costsFor = snapshot;
		this.costsAt = now;
		this.costsFinal = ItemPricer.isReady() && NeuRepoCache.isReady() && HypixelCollectionsCache.isReady();
		this.missingById.clear();
		this.indexById.clear();
		List<CollectionSnapshot.MinionEntry> minions = snapshot == null ? List.of() : snapshot.minions();
		Map<MinionCosts.Category, List<Integer>> byCategory = new EnumMap<>(MinionCosts.Category.class);
		for (int i = 0; i < minions.size(); i++) {
			CollectionSnapshot.MinionEntry minion = minions.get(i);
			byCategory.computeIfAbsent(MinionCosts.categoryOf(minion.id()), k -> new ArrayList<>()).add(i);
			this.missingById.put(minion.id(), MinionCosts.missingTiers(minion));
			this.indexById.put(minion.id(), i);
		}
		this.groups = byCategory;
		this.cheapest = MinionCosts.cheapestNext(minions);
	}

	private void renderMinions(
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
		int rightW = Math.max(96, Math.min(118, (int) Math.round(w * 0.24)));
		int leftW = w - rightW - GAP;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		List<CollectionSnapshot.MinionEntry> minions = snapshot.minions();
		PvDraw.text(g, font, Component.translatable("betterpv.collections.minions").getString(), x + PAD, y + PAD, PvDraw.COLOR_TEXT);
		this.minionTop = y + PAD + font.lineHeight + 4;
		this.minionH = Math.max(20, y + h - PAD - this.minionTop);

		if (minions.isEmpty()) {
			this.toggleW = 0;
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.minions_empty").getString(),
				x + leftW / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.minionMaxScroll = 0;
			this.selectedMinion = -1;
			drawMinionDetail(g, font, null, rightX, y, rightW, h, mouseX, mouseY);
			return;
		}
		this.selectedMinion = Math.max(0, Math.min(this.selectedMinion, minions.size() - 1));

		String toggle = Component.translatable("betterpv.collections.minions_cheapest").getString();
		this.toggleW = font.width(toggle);
		this.toggleH = font.lineHeight;
		this.toggleX = x + leftW - PAD - this.toggleW;
		this.toggleY = y + PAD;
		boolean toggleHover = mouseX >= this.toggleX && mouseX < this.toggleX + this.toggleW
			&& mouseY >= this.toggleY && mouseY < this.toggleY + this.toggleH;
		PvDraw.text(g, font, toggle, this.toggleX, this.toggleY,
			this.cheapestMode ? PvDraw.COLOR_ACCENT : toggleHover ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);

		int listX = x + PAD;
		int listW = leftW - PAD * 2;
		if (this.cheapestMode) {
			renderCheapest(snapshot, g, font, listX, listW, mouseX, mouseY);
		} else {
			renderGrid(minions, g, font, listX, listW, mouseX, mouseY);
		}

		drawMinionDetail(g, font, minions.get(this.selectedMinion), rightX, y, rightW, h, mouseX, mouseY);
	}

	private void renderGrid(
		List<CollectionSnapshot.MinionEntry> minions,
		GuiGraphicsExtractor g,
		Font font,
		int listX,
		int listW,
		int mouseX,
		int mouseY
	) {
		int slot = SLOT;
		int minGap = 5;
		int maxGap = 14;
		int cols = Math.max(1, (listW + minGap) / (slot + minGap));
		int gapX = cols <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (listW - cols * slot) / (cols - 1)));
		int gapY = minGap;
		int gridW = cols * slot + Math.max(0, cols - 1) * gapX;
		int startX = listX + Math.max(0, (listW - gridW) / 2);
		int headerH = font.lineHeight + 3;
		int groupGap = 4;

		int contentH = 0;
		for (List<Integer> members : this.groups.values()) {
			int rows = (members.size() + cols - 1) / cols;
			contentH += headerH + rows * slot + Math.max(0, rows - 1) * gapY + groupGap;
		}
		contentH = Math.max(0, contentH - groupGap);
		this.minionMaxScroll = Math.max(0, contentH - this.minionH);
		this.minionScroll = Math.max(0, Math.min(this.minionScroll, this.minionMaxScroll));
		boolean inList = mouseY >= this.minionTop && mouseY < this.minionTop + this.minionH;

		g.enableScissor(listX, this.minionTop, listX + listW, this.minionTop + this.minionH);
		int cy = this.minionTop - this.minionScroll;
		for (var group : this.groups.entrySet()) {
			List<Integer> members = group.getValue();
			int maxed = 0;
			for (int index : members) {
				if (isMaxed(minions.get(index))) {
					maxed++;
				}
			}
			String label = group.getKey().label() + " " + maxed + "/" + members.size();
			PvDraw.sectionHeader(g, font, label, listX, cy, listW);
			cy += headerH;
			for (int n = 0; n < members.size(); n++) {
				int index = members.get(n);
				int sx = startX + (n % cols) * (slot + gapX);
				int sy = cy + (n / cols) * (slot + gapY);
				if (sy + slot < this.minionTop || sy > this.minionTop + this.minionH) {
					continue;
				}
				CollectionSnapshot.MinionEntry minion = minions.get(index);
				boolean selected = index == this.selectedMinion;
				boolean hover = inList && mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
				PvDraw.roundedFill(g, sx, sy, slot, slot, 3, selected ? 0x50386CC8 : hover ? 0x24FFFFFF : PvDraw.CELL);
				PvDraw.roundedRing(g, sx, sy, slot, slot, 3, selected ? PvDraw.COLOR_ACCENT : hover ? 0xFF4A4A5A : 0x30FFFFFF, PvDraw.C_ALL);
				CollectionsUi.drawIcon(g, minion.iconId(), sx + (slot - ITEM_ICON) / 2, sy + (slot - ITEM_ICON) / 2 - 1);
				PvDraw.textRight(
					g, font,
					String.valueOf(minion.maxCrafted()),
					sx + slot - 1, sy + slot - font.lineHeight + 1,
					isMaxed(minion) ? PvDraw.COLOR_GOLD : MINION_LOCKED
				);
				if (inList) {
					this.minionHits.add(new SlotHit(sx, sy, slot, slot, index));
				}
				if (hover) {
					this.hoverTip = minionTip(minion);
				}
			}
			int rows = (members.size() + cols - 1) / cols;
			cy += rows * slot + Math.max(0, rows - 1) * gapY + groupGap;
		}
		g.disableScissor();
	}

	private void renderCheapest(
		CollectionSnapshot snapshot,
		GuiGraphicsExtractor g,
		Font font,
		int listX,
		int listW,
		int mouseX,
		int mouseY
	) {
		List<CollectionSnapshot.MinionEntry> minions = snapshot.minions();
		if (this.cheapest.isEmpty()) {
			this.minionMaxScroll = 0;
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.minions_all_crafted").getString(),
				listX + listW / 2, this.minionTop + this.minionH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED
			);
			return;
		}
		Map<String, Integer> indexById = this.indexById;
		int rowH = Math.max(ITEM_ICON, font.lineHeight) + 2;
		this.minionMaxScroll = Math.max(0, this.cheapest.size() * rowH - this.minionH);
		this.minionScroll = Math.max(0, Math.min(this.minionScroll, this.minionMaxScroll));
		boolean inList = mouseY >= this.minionTop && mouseY < this.minionTop + this.minionH;

		g.enableScissor(listX, this.minionTop, listX + listW, this.minionTop + this.minionH);
		int rowY = this.minionTop - this.minionScroll;
		for (MinionCosts.MissingTier row : this.cheapest) {
			if (rowY + rowH >= this.minionTop && rowY <= this.minionTop + this.minionH) {
				Integer index = indexById.get(row.minionId());
				boolean selected = index != null && index == this.selectedMinion;
				boolean hover = inList && mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + rowH;
				if (selected || hover) {
					PvDraw.fill(g, listX, rowY, listW, rowH, selected ? 0x50386CC8 : 0x24FFFFFF);
				}
				PvDraw.IconTextAlign align = PvDraw.IconTextAlign.of(rowY, rowH, ITEM_ICON, font.lineHeight);
				String tierId = row.minionId() + "_" + row.cost().tier();
				CollectionsUi.drawIcon(g, tierId, listX + 1, align.iconY());
				String cost = costText(row.cost());
				int costW = font.width(cost);
				PvDraw.textRight(g, font, cost, listX + listW - 2, align.textY(), costColor(row.cost()));
				int nameX = listX + ITEM_ICON + 5;
				PvDraw.text(g, font, CollectionsUi.trim(font, tierName(row.minionId(), row.cost().tier()), listX + listW - 2 - costW - 6 - nameX),
					nameX, align.textY(), PvDraw.COLOR_TEXT);
				if (inList && index != null) {
					this.minionHits.add(new SlotHit(listX, rowY, listW, rowH, index));
				}
				if (hover) {
					this.hoverTip = tierTip(row.minionId(), row.cost());
				}
			}
			rowY += rowH;
		}
		g.disableScissor();
	}

	private void drawMinionDetail(
		GuiGraphicsExtractor g,
		Font font,
		CollectionSnapshot.MinionEntry minion,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		int cx = x + PAD;
		int cy = y + PAD;
		int contentW = w - PAD * 2;
		if (minion == null) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.select_minion").getString(),
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.tierListTop = cy;
			this.tierListH = 0;
			this.tierMaxScroll = 0;
			return;
		}

		CollectionsUi.drawIcon(g, minion.iconId(), cx, cy);
		PvDraw.text(
			g, font,
			CollectionsUi.trim(font, minion.displayName(), contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT
		);
		cy += ITEM_ICON + 4;
		boolean maxed = isMaxed(minion);
		PvDraw.text(
			g, font,
			"T" + minion.maxCrafted() + " / " + minion.tierCap(),
			cx, cy,
			maxed ? PvDraw.COLOR_GOLD : MINION_LOCKED
		);
		cy += font.lineHeight + 2;
		PvDraw.text(
			g, font,
			Component.translatable("betterpv.collections.minions_shared").getString(),
			cx, cy,
			PvDraw.COLOR_MUTED
		);
		cy += font.lineHeight + 3;

		List<MinionCosts.TierCost> missing = this.missingById.getOrDefault(minion.id(), List.of());
		if (!missing.isEmpty()) {
			String remainingLabel = Component.translatable("betterpv.collections.minions_remaining").getString();
			String remaining = totalText(missing);
			PvDraw.text(g, font, CollectionsUi.trim(font, remainingLabel, contentW - font.width(remaining) - 4), cx, cy, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, remaining, cx + contentW, cy, totalComplete(missing) ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED);
			if (mouseX >= cx && mouseX < cx + contentW && mouseY >= cy && mouseY < cy + font.lineHeight) {
				this.hoverTip = remainingTip(minion, missing);
			}
			cy += font.lineHeight + 3;
		}

		PvDraw.text(g, font, Component.translatable("betterpv.collections.tiers").getString(), cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		this.tierListTop = cy;
		this.tierListH = Math.max(20, y + h - PAD - this.tierListTop);

		Map<Integer, MinionCosts.TierCost> costByTier = new HashMap<>();
		for (MinionCosts.TierCost cost : missing) {
			costByTier.put(cost.tier(), cost);
		}
		int tiers = Math.max(1, minion.tierCap());
		int naturalRow = font.lineHeight + 1;
		int fitRow = Math.max(8, this.tierListH / tiers);
		int rowH = Math.min(naturalRow, fitRow);
		float tierScale = rowH < font.lineHeight ? rowH / (float) font.lineHeight : 1.0f;
		this.tierMaxScroll = Math.max(0, tiers * rowH - this.tierListH);
		this.tierScroll = Math.max(0, Math.min(this.tierScroll, this.tierMaxScroll));
		boolean inTiers = mouseX >= cx && mouseX < cx + contentW
			&& mouseY >= this.tierListTop && mouseY < this.tierListTop + this.tierListH;

		g.enableScissor(cx, this.tierListTop, cx + contentW, this.tierListTop + this.tierListH);
		int rowY = this.tierListTop - this.tierScroll;
		for (int t = 1; t <= tiers; t++) {
			if (rowY + rowH >= this.tierListTop && rowY <= this.tierListTop + this.tierListH) {
				boolean crafted = minion.crafted(t);
				int color = crafted ? MINION_UNLOCKED : MINION_LOCKED;
				String label = "T" + t;
				MinionCosts.TierCost cost = crafted ? null : costByTier.get(t);
				if (tierScale >= 0.99f) {
					PvDraw.text(g, font, label, cx, rowY, color);
					if (cost != null) {
						PvDraw.textRight(g, font, costText(cost), cx + contentW, rowY, costColor(cost));
					} else {
						Component mark = PvDraw.styled(crafted ? MINION_TICK : MINION_CROSS, color, true);
						PvDraw.text(g, font, mark, cx + contentW - PvDraw.width(font, mark), rowY);
					}
				} else {
					PvDraw.textScaled(g, font, label, cx, rowY, color, tierScale);
					if (cost != null) {
						String text = costText(cost);
						int tw = Math.max(1, Math.round(font.width(text) * tierScale));
						PvDraw.textScaled(g, font, text, cx + contentW - tw, rowY, costColor(cost), tierScale);
					} else {
						String mark = crafted ? MINION_TICK : MINION_CROSS;
						int tw = Math.max(1, Math.round(PvDraw.widthBold(font, mark) * tierScale));
						PvDraw.textScaled(g, font, mark, cx + contentW - tw, rowY, color, tierScale);
					}
				}
				if (cost != null && inTiers && mouseY >= rowY && mouseY < rowY + rowH) {
					this.hoverTip = tierTip(minion.id(), cost);
				}
			}
			rowY += rowH;
		}
		g.disableScissor();
	}

	private List<PvTooltip.Line> minionTip(CollectionSnapshot.MinionEntry minion) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.bold(minion.displayName(), PvDraw.COLOR_GOLD));
		lines.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED,
			minion.maxCrafted() + " / " + minion.tierCap(), isMaxed(minion) ? MINION_UNLOCKED : PvDraw.COLOR_TEXT));
		List<MinionCosts.TierCost> missing = this.missingById.getOrDefault(minion.id(), List.of());
		if (!missing.isEmpty()) {
			lines.add(PvTooltip.Line.row(
				Component.translatable("betterpv.collections.minions_remaining").getString(), PvDraw.COLOR_MUTED,
				totalText(missing), totalComplete(missing) ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED));
		}
		return lines;
	}

	private List<PvTooltip.Line> remainingTip(CollectionSnapshot.MinionEntry minion, List<MinionCosts.TierCost> missing) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.bold(minion.displayName(), PvDraw.COLOR_GOLD));
		for (MinionCosts.TierCost cost : missing) {
			lines.add(PvTooltip.Line.row("T" + cost.tier(), PvDraw.COLOR_MUTED, costText(cost), costColor(cost)));
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(PvTooltip.Line.of(Component.translatable("betterpv.collections.minions_cost_note").getString(), PvDraw.COLOR_MUTED));
		return lines;
	}

	private static List<PvTooltip.Line> tierTip(String minionId, MinionCosts.TierCost cost) {
		return tierTipLines(
			tierName(minionId, cost.tier()),
			cost,
			MinionCosts.ingredientsFor(minionId, cost.tier()),
			MinionsPage::itemName,
			MinionsPage::itemColor,
			MinionsPage::itemIcon,
			id -> cost.unpriced().contains(id) ? null : ItemPricer.knownPrice(id),
			Component.translatable("betterpv.collections.minions_unpriced").getString(),
			Component.translatable("betterpv.collections.minions_no_recipe").getString()
		);
	}

	static List<PvTooltip.Line> tierTipLines(
		String title,
		MinionCosts.TierCost cost,
		Map<String, Integer> ingredients,
		Function<String, String> names,
		Function<String, Integer> colors,
		Function<String, ItemStack> icons,
		Function<String, Double> prices,
		String unpricedLabel,
		String noRecipeLabel
	) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.bold(title, PvDraw.COLOR_GOLD));
		if (cost.noRecipe()) {
			lines.add(PvTooltip.Line.of(noRecipeLabel, PvDraw.COLOR_MUTED));
			return lines;
		}
		lines.add(PvTooltip.Line.blank());
		for (var e : ingredients.entrySet()) {
			Integer color = colors.apply(e.getKey());
			List<PvTooltip.Span> left = List.of(
				PvTooltip.Span.of(e.getValue() + "x ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(names.apply(e.getKey()), color == null ? PvDraw.COLOR_TEXT : color)
			);
			Double unit = prices.apply(e.getKey());
			PvTooltip.Line row = PvTooltip.Line.row(left, List.of(unit == null || unit <= 0
				? PvTooltip.Span.of(unpricedLabel, PvDraw.COLOR_MUTED)
				: PvTooltip.Span.of(FormatUtil.shortCoins(unit * e.getValue()), PvDraw.COLOR_GOLD)));
			ItemStack icon = icons.apply(e.getKey());
			lines.add(icon == null ? row : row.withIcon(icon));
		}
		lines.add(PvTooltip.Line.blank());
		boolean unknown = !cost.complete() && cost.coins() <= 0;
		lines.add(PvTooltip.Line.row(
			List.of(PvTooltip.Span.of("Craft cost:", PvDraw.COLOR_MUTED)),
			List.of(PvTooltip.Span.bold(unknown ? "Unknown" : costText(cost), costColor(cost)))
		));
		if (!cost.complete() && !unknown) {
			lines.add(PvTooltip.Line.meta("Excludes materials with no price"));
		}
		return lines;
	}

	/** Partial totals get a trailing {@code +}: the real cost is at least this much. */
	private static String costText(MinionCosts.TierCost cost) {
		if (cost.noRecipe()) {
			return "?";
		}
		return FormatUtil.shortCoins(cost.coins()) + (cost.complete() ? "" : "+");
	}

	private static int costColor(MinionCosts.TierCost cost) {
		return cost.complete() ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED;
	}

	private static String totalText(List<MinionCosts.TierCost> missing) {
		double sum = 0;
		boolean any = false;
		for (MinionCosts.TierCost cost : missing) {
			if (!cost.noRecipe()) {
				sum += cost.coins();
				any = true;
			}
		}
		if (!any) {
			return "?";
		}
		return FormatUtil.shortCoins(sum) + (totalComplete(missing) ? "" : "+");
	}

	private static boolean totalComplete(List<MinionCosts.TierCost> missing) {
		for (MinionCosts.TierCost cost : missing) {
			if (!cost.complete()) {
				return false;
			}
		}
		return true;
	}

	private static String tierName(String minionId, int tier) {
		String name = SkyBlockItemFactory.plainDisplayName(minionId + "_" + tier);
		return name == null || name.isBlank() ? minionId + " " + tier : name;
	}

	private static Integer itemColor(String id) {
		return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(id.replace(':', '-')));
	}

	// NEU hands back paper while it's still loading; skip the icon rather than show a placeholder.
	private static ItemStack itemIcon(String id) {
		ItemStack icon = SkyBlockItemFactory.iconStack(id.replace(':', '-'));
		return icon.isEmpty() || icon.is(Items.PAPER) ? null : icon;
	}

	private static String itemName(String id) {
		String name = SkyBlockItemFactory.plainDisplayName(id.replace(':', '-'));
		return name == null || name.isBlank() ? id : name;
	}

	private static boolean isMaxed(CollectionSnapshot.MinionEntry minion) {
		return minion.maxCrafted() >= minion.tierCap() && minion.tierCap() > 0;
	}

	private record SlotHit(int x, int y, int w, int h, int index) {
	}
}

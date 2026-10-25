/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import static fishmod.pv.gui.foraging.ForagingUi.COLOR_MAXED;
import static fishmod.pv.gui.foraging.ForagingUi.GAP;
import static fishmod.pv.gui.foraging.ForagingUi.ICON;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_LOCKED_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_LOCKED_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.PAD;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT_GAP;

import com.google.gson.JsonObject;
import fishmod.pv.data.AttributeShardsData;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.foraging.ForagingUi;
import fishmod.pv.gui.foraging.ForagingUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Attribute Shards subtab with dual-scroll grid + cost list. */
public final class AttributeShardsPage {
	private final List<HoverZone> zones = new ArrayList<>();
	private final Map<String, AttributeShardTooltip.Info> shardInfo = new HashMap<>();

	private int attrGridScroll;
	private int attrGridMaxScroll;
	private int attrGridX;
	private int attrGridY;
	private int attrGridW;
	private int attrGridH;
	private int attrListScroll;
	private int attrListMaxScroll;
	private int attrListX;
	private int attrListY;
	private int attrListW;
	private int attrListH;
	private boolean sortByRarity;
	private List<ForagingSnapshot.AttributeShardRow> sortedSource;
	private List<ForagingSnapshot.AttributeShardRow> raritySorted = List.of();
	private int sortX;
	private int sortY;
	private int sortW;
	private int sortH;

	public void reset() {
		this.zones.clear();
		this.attrGridScroll = 0;
		this.attrListScroll = 0;
		this.sortedSource = null;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.sortW > 0 && ForagingUi.hit(mx, my, this.sortX, this.sortY, this.sortW, this.sortH)) {
			this.sortByRarity = !this.sortByRarity;
			this.attrGridScroll = 0;
			return true;
		}
		return false;
	}

	private List<ForagingSnapshot.AttributeShardRow> gridOrder(List<ForagingSnapshot.AttributeShardRow> rows) {
		if (!this.sortByRarity) {
			return rows;
		}
		if (this.sortedSource != rows) {
			this.sortedSource = rows;
			this.raritySorted = ForagingSnapshot.AttributeShardRow.byRarity(rows);
		}
		return this.raritySorted;
	}

	public void resetScroll() {
		this.attrGridScroll = 0;
		this.attrListScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int delta = scrollY > 0 ? -14 : 14;
		boolean overGrid = ForagingUi.hit(mouseX, mouseY, this.attrGridX, this.attrGridY, this.attrGridW, this.attrGridH);
		boolean overList = ForagingUi.hit(mouseX, mouseY, this.attrListX, this.attrListY, this.attrListW, this.attrListH);
		if (overGrid && this.attrGridMaxScroll > 0) {
			int next = Math.max(0, Math.min(this.attrGridMaxScroll, this.attrGridScroll + delta));
			if (next != this.attrGridScroll) {
				this.attrGridScroll = next;
			}
			return true;
		}
		if (overList && this.attrListMaxScroll > 0) {
			int next = Math.max(0, Math.min(this.attrListMaxScroll, this.attrListScroll + delta));
			if (next != this.attrListScroll) {
				this.attrListScroll = next;
			}
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int rightW = Math.max(210, w * 42 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		List<ForagingSnapshot.AttributeShardRow> rows = snapshot.attributeShards();
		drawAttributeGrid(g, font, x, y, leftW, h, mx, my, rows);
		drawAttributeCostList(g, font, x + leftW + GAP, y, rightW, h, mx, my, rows);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawAttributeGrid(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Attribute Shards", lx, ly, PvDraw.COLOR_MUTED);
		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No attribute data",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.attrGridMaxScroll = 0;
			this.attrGridScroll = 0;
			this.attrGridW = 0;
			this.attrGridH = 0;
			this.sortW = 0;
			return;
		}
		String sortLabel = this.sortByRarity ? "Sort: Rarity" : "Sort: Default";
		this.sortW = font.width(sortLabel);
		this.sortH = font.lineHeight;
		this.sortX = lx + lw - this.sortW;
		this.sortY = ly;
		boolean sortHovered = ForagingUi.hit(mx, my, this.sortX, this.sortY, this.sortW, this.sortH);
		PvDraw.text(g, font, sortLabel, this.sortX, this.sortY,
			sortHovered || this.sortByRarity ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED);
		this.zones.add(new HoverZone(this.sortX, this.sortY, this.sortW, this.sortH, List.of(
			PvTooltip.Line.of("Click to change sort", PvDraw.COLOR_TEXT),
			PvTooltip.Line.of("Default: unlocked first, then name", PvDraw.COLOR_MUTED),
			PvTooltip.Line.of("Rarity: highest first, then name", PvDraw.COLOR_MUTED)
		)));
		ly += font.lineHeight + 4;
		rows = gridOrder(rows);

		int cols = Math.max(6, Math.min(12, (lw + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int gridRows = (rows.size() + cols - 1) / cols;
		int contentH = gridRows * (SLOT + SLOT_GAP);
		this.attrGridX = x;
		this.attrGridY = ly;
		this.attrGridW = w;
		this.attrGridH = Math.max(0, bottom - ly);
		this.attrGridMaxScroll = Math.max(0, contentH - this.attrGridH);
		this.attrGridScroll = Math.min(this.attrGridScroll, this.attrGridMaxScroll);

		g.enableScissor(lx, this.attrGridY, lx + lw + 3, this.attrGridY + this.attrGridH);
		for (int i = 0; i < rows.size(); i++) {
			ForagingSnapshot.AttributeShardRow row = rows.get(i);
			int col = i % cols;
			int r = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + r * (SLOT + SLOT_GAP) - this.attrGridScroll;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& ForagingUi.hit(mx, my, lx, this.attrGridY, lw, this.attrGridH);
			boolean locked = !row.unlocked();
			PvDraw.fill(g, bx, by, SLOT, SLOT, locked ? ITEM_SLOT_LOCKED_BG : raritySlotBackground(rarityColor(row.rarity())));
			int border = locked
				? ITEM_SLOT_LOCKED_BORDER
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			g.outline(bx, by, SLOT, SLOT, border);
			ItemStack stack = attributeIcon(row);
			SkyBlockIconRenderer.draw(g, stack, row.iconId(), bx + 1, by + 1, 16);
			ForagingUi.addClippedHover(this.zones, bx, by, SLOT, SLOT, lx, this.attrGridY, lw, this.attrGridH,
				attributeTooltip(row));
		}
		g.disableScissor();
	}

	private void drawAttributeCostList(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Not maxed", rx, ry, PvDraw.COLOR_MUTED);

		List<ForagingSnapshot.AttributeShardRow> notMaxed = new ArrayList<>();
		for (ForagingSnapshot.AttributeShardRow row : rows) {
			// Reptiloid cannot realistically be maxed, so it never counts as "not maxed".
			boolean reptiloid = row.name() != null && row.name().equalsIgnoreCase("Reptiloid")
				|| row.stackId() != null && row.stackId().toUpperCase(java.util.Locale.ROOT).contains("REPTILOID");
			if (row.level() < row.maxLevel() && !reptiloid) {
				notMaxed.add(row);
			}
		}
		double totalCost = 0;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			totalCost += shardMaxCost(row);
		}
		String totalText = totalCost > 0 ? FormatUtil.compactCoins(totalCost) : "-";
		PvDraw.textRight(g, font, totalText, rx + rw, ry, PvDraw.COLOR_GOLD);
		ry += font.lineHeight + 2;
		PvDraw.text(g, font, "Cheapest to max", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);

		notMaxed.sort(Comparator
			.comparingDouble(AttributeShardsPage::shardMaxCost)
			.thenComparingInt(r -> Math.max(0, r.shardsForMax() - r.shardsOwned())));

		if (notMaxed.isEmpty()) {
			PvDraw.text(g, font, "All maxed", rx, ry, COLOR_MAXED);
			this.attrListMaxScroll = 0;
			this.attrListScroll = 0;
			this.attrListW = 0;
			this.attrListH = 0;
			return;
		}

		int rowH = Math.max(ICON + 2, font.lineHeight * 2 + 4);
		this.attrListX = x;
		this.attrListY = ry;
		this.attrListW = w;
		this.attrListH = Math.max(0, bottom - ry);
		int contentH = notMaxed.size() * rowH;
		this.attrListMaxScroll = Math.max(0, contentH - this.attrListH);
		this.attrListScroll = Math.min(this.attrListScroll, this.attrListMaxScroll);

		g.enableScissor(rx, this.attrListY, rx + rw + 3, this.attrListY + this.attrListH);
		int yy = this.attrListY - this.attrListScroll;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
			ItemStack stack = attributeIcon(row);
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(yy, rowH, ICON, font.lineHeight);
			SkyBlockIconRenderer.draw(g, stack, row.iconId(), rx, rowAlign.iconY(), ICON);
			String qty = "x" + FormatUtil.commas(need);
			int qtyW = font.width(qty);
			int nameColor = rarityColor(row.rarity());
			PvDraw.text(g, font, ForagingUi.trim(font, row.name(), Math.max(8, rw - ICON - 8 - qtyW)),
				rx + ICON + 4, rowAlign.textY(), nameColor);
			PvDraw.textRight(g, font, qty, rx + rw, rowAlign.textY(), PvDraw.COLOR_ACCENT);
			double cost = shardMaxCost(row);
			String costText = cost > 0 ? FormatUtil.compactCoins(cost) : "-";
			PvDraw.text(g, font, costText, rx + ICON + 4, rowAlign.textY() + font.lineHeight + 1, PvDraw.COLOR_GOLD);
			ForagingUi.addClippedHover(this.zones, rx, yy, rw, rowH, rx, this.attrListY, rw, this.attrListH,
				attributeTooltip(row));
			yy += rowH;
		}
		g.disableScissor();
	}

	private static ItemStack attributeIcon(ForagingSnapshot.AttributeShardRow row) {
		ItemStack stack = SkyBlockItemFactory.iconStack(row.iconId());
		if (stack == null || stack.isEmpty() || stack.is(Items.BARRIER)) {
			stack = new ItemStack(Items.PRISMARINE_SHARD);
		}
		return stack;
	}

	private List<PvTooltip.Line> attributeTooltip(ForagingSnapshot.AttributeShardRow row) {
		return AttributeShardTooltip.lines(row, shardInfo(row), unitPrice(row), rarityColor(row.rarity()));
	}

	private AttributeShardTooltip.Info shardInfo(ForagingSnapshot.AttributeShardRow row) {
		AttributeShardTooltip.Info cached = this.shardInfo.get(row.iconId());
		if (cached != null) {
			return cached;
		}
		JsonObject neuItem = NeuRepoCache.get(row.iconId());
		AttributeShardTooltip.Info info = AttributeShardTooltip.info(AttributeShardsData.def(row.stackId()), neuItem, row.name());
		// Only cache once NEU has the item, so the description fills in after the repo finishes loading.
		if (neuItem != null) {
			this.shardInfo.put(row.iconId(), info);
		}
		return info;
	}

	private static double shardMaxCost(ForagingSnapshot.AttributeShardRow row) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		if (need <= 0) {
			return 0;
		}
		double unit = unitPrice(row);
		return unit > 0 ? unit * need : 0;
	}

	private static double unitPrice(ForagingSnapshot.AttributeShardRow row) {
		double unit = ItemPricer.price(row.priceId());
		if (unit <= 0) {
			unit = ItemPricer.price(row.iconId());
		}
		if (unit <= 0) {
			String icon = row.iconId();
			int semi = icon == null ? -1 : icon.indexOf(';');
			if (semi > 0) {
				unit = ItemPricer.price(icon.substring(0, semi));
			}
		}
		return unit;
	}

	/** Dark rarity-tinted slot fill (keeps icons readable); white common keeps the neutral slot. */
	private static int raritySlotBackground(int rarityArgb) {
		int r = (rarityArgb >>> 16) & 0xFF;
		int green = (rarityArgb >>> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		if (r >= 240 && green >= 240 && b >= 240) {
			return ITEM_SLOT_BG;
		}
		return 0xFF000000
			| (Math.min(255, Math.round(16 + r * 0.28f)) << 16)
			| (Math.min(255, Math.round(16 + green * 0.28f)) << 8)
			| Math.min(255, Math.round(16 + b * 0.28f));
	}

	private static int rarityColor(String rarity) {
		if (rarity == null) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			default -> PvDraw.COLOR_TEXT;
		};
	}
}

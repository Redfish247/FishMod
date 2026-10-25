/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.bestiary;

import fishmod.pv.data.BestiaryData;
import fishmod.pv.data.BestiarySnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MobNames;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Bestiary left panel: family grid for the selected island/category.
 * Right island buttons live in {@code ProfileViewerScreen} (inventory split layout).
 */
public final class BestiaryPage {
	private static final int PAD = 6;
	private static final int SLOT = 32;
	private static final int SLOT_GAP = 4;
	private static final int SCROLL_STEP = (SLOT + SLOT_GAP) * 3;
	private static final int DIST_BAR_H = 24;
	private static final int DIST_BAR_GAP = 6;
	private static final int DIST_SEP = 1;
	private static final int DIST_BG = 0x1CFFFFFF;
	private static final int DIST_BORDER = 0x30FFFFFF;
	private static final int DIST_LOW = 0xFF555555;
	private static final int DIST_MID = 0xFFFFAA00;
	private static final int DIST_HIGH = 0xFF55FF55;
	private static final int DIST_MAXED = 0xFF9278C5;

	private BestiarySnapshot snapshot = BestiarySnapshot.empty();
	private String categoryId = "";
	private String searchQuery = "";
	private int scroll;
	private int maxScroll;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	private final Map<String, ItemStack> iconCache = new HashMap<>();
	private String hoverKey = "";
	private List<Component> hoverTip = List.of();
	private BestiarySnapshot.FamilyProgress tipFamily;
	private List<Component> tipCache = List.of();

	public void apply(BestiarySnapshot snapshot) {
		this.snapshot = snapshot == null ? BestiarySnapshot.empty() : snapshot;
		this.iconCache.clear();
		this.scroll = 0;
		if (this.categoryId.isBlank()) {
			List<BestiaryData.Category> cats = BestiaryData.categories();
			for (BestiaryData.Category cat : cats) {
				if (cat != null && !cat.families().isEmpty()) {
					this.categoryId = cat.id();
					break;
				}
			}
			if (this.categoryId.isBlank() && !cats.isEmpty()) {
				this.categoryId = cats.get(0).id();
			}
		}
	}

	public int totalUnlockedTiers() {
		return this.snapshot.totalUnlockedTiers();
	}

	public int totalMaxTiers() {
		return this.snapshot.totalMaxTiers();
	}

	public int claimedMilestone() {
		return this.snapshot.claimedMilestone();
	}

	public int milestone() {
		return this.snapshot.milestone();
	}

	public String lastKilledMob() {
		return this.snapshot.lastKilledMob();
	}

	public void setCategory(String categoryId) {
		String next = categoryId == null ? "" : categoryId;
		if (!next.equals(this.categoryId)) {
			this.categoryId = next;
			this.scroll = 0;
			this.hoverKey = "";
			this.hoverTip = List.of();
		}
	}

	public String categoryId() {
		return this.categoryId;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
	}

	public boolean searchHighlightsCategory(String categoryId) {
		if (this.searchQuery.isBlank() || categoryId == null) {
			return false;
		}
		BestiarySnapshot.CategoryProgress progress = this.snapshot.category(categoryId);
		if (progress == null) {
			return false;
		}
		for (BestiarySnapshot.FamilyProgress fam : progress.families()) {
			if (matchesSearch(fam)) {
				return true;
			}
		}
		BestiaryData.Category cat = progress.category();
		return cat != null && cat.name().toLowerCase(Locale.ROOT).contains(this.searchQuery);
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
		this.hoverKey = "";
		this.hoverTip = List.of();
		BestiaryData.ensureLoaded();
		BestiarySnapshot.CategoryProgress progress = this.snapshot.category(this.categoryId);
		if (progress == null && !BestiaryData.categories().isEmpty()) {
			progress = this.snapshot.category(BestiaryData.categories().get(0).id());
			if (progress != null) {
				this.categoryId = progress.category().id();
			}
		}

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = Math.max(8, w - PAD * 2);

		// Four stat boxes across the top: category, tiers, milestone, last kill.
		int boxGap = 4;
		int boxW = (lw - boxGap * 3) / 4;
		int boxH = font.lineHeight * 2 + 12;
		String title = progress == null ? "Bestiary" : progress.category().name();
		int famDone = progress == null ? 0 : progress.familiesUnlocked();
		int famTotal = progress == null ? 0 : progress.families().size();
		int tiers = progress == null ? 0 : progress.unlockedTiers();
		int tiersMax = progress == null ? 0 : progress.maxTiers();
		for (int i = 0; i < 4; i++) {
			int bx = lx + i * (boxW + boxGap);
			PvDraw.roundedFill(g, bx, ly, boxW, boxH, 3, PvDraw.CELL);
			PvDraw.roundedRing(g, bx, ly, boxW, boxH, 3, 0x30FFFFFF, PvDraw.C_ALL);
		}
		int tx = 5;
		int line1 = ly + 3;
		int line2 = line1 + font.lineHeight + 2;
		int hdrBarY = ly + boxH - 5;

		int b0 = lx;
		PvDraw.text(g, font, fit(font, title, boxW - tx * 2), b0 + tx, line1, PvDraw.COLOR_TEXT);
		PvDraw.text(g, font, famDone + "/" + famTotal + " families", b0 + tx, line2, PvDraw.COLOR_MUTED);
		float famPct = famTotal <= 0 ? 0F : famDone / (float) famTotal;
		PvDraw.progressBar(g, b0 + tx, hdrBarY, boxW - tx * 2, 3, famPct, 0xFF55FF55, famDone >= famTotal && famTotal > 0);

		int b1 = lx + (boxW + boxGap);
		PvDraw.text(g, font, "Tiers", b1 + tx, line1, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, FormatUtil.commas(tiers) + " / " + FormatUtil.commas(tiersMax), b1 + tx, line2, PvDraw.COLOR_TEXT);
		float tierPct = tiersMax <= 0 ? 0F : tiers / (float) tiersMax;
		PvDraw.progressBar(g, b1 + tx, hdrBarY, boxW - tx * 2, 3, tierPct, PvDraw.COLOR_ACCENT, tiers >= tiersMax && tiersMax > 0);

		int b2 = lx + 2 * (boxW + boxGap);
		PvDraw.text(g, font, "Milestone", b2 + tx, line1, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, FormatUtil.commas(this.snapshot.milestone()) + "  +"
			+ FormatUtil.commas(this.snapshot.claimedMilestone()) + " HP", b2 + tx, line2, PvDraw.COLOR_GOLD);

		int b3 = lx + 3 * (boxW + boxGap);
		PvDraw.text(g, font, "Last kill", b3 + tx, line1, PvDraw.COLOR_MUTED);
		String lastKill = this.snapshot.lastKilledMob().isBlank() ? "-" : prettyMobId(this.snapshot.lastKilledMob());
		PvDraw.text(g, font, fit(font, lastKill, boxW - tx * 2), b3 + tx, line2, PvDraw.COLOR_TEXT);
		ly += boxH + 6;

		List<BestiarySnapshot.FamilyProgress> families = progress == null ? List.of() : progress.families();
		boolean showDistBar = false;
		int distReserve = showDistBar ? DIST_BAR_H + DIST_BAR_GAP : 0;
		this.gridX = lx;
		this.gridY = ly;
		this.gridW = lw;
		this.gridH = Math.max(0, y + h - PAD - ly - distReserve);

		if (families.isEmpty()) {
			this.maxScroll = 0;
			PvDraw.textCentered(g, font, "No families",
				x + w / 2, this.gridY + Math.max(0, this.gridH / 2 - font.lineHeight / 2), PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP));
		int rows = (families.size() + cols - 1) / cols;
		int contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP;
		this.maxScroll = Math.max(0, contentH - this.gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		int barY = this.gridY + this.gridH + DIST_BAR_GAP;
		int[] barWidths = new int[0];
		BestiarySnapshot.FamilyProgress linked = findLinkedFamily(
			families, cols, lx, ly, barY, lw, barWidths, mouseX, mouseY
		);
		String linkedId = linked == null || linked.family() == null ? "" : linked.family().id();

		int rowPitch = SLOT + SLOT_GAP;
		int firstRow = Math.max(0, this.scroll / rowPitch);
		int lastRow = Math.min(rows - 1, (this.scroll + this.gridH) / rowPitch + 1);
		int first = firstRow * cols;
		int last = Math.min(families.size() - 1, (lastRow + 1) * cols - 1);

		g.enableScissor(lx, this.gridY, lx + lw + 3, this.gridY + this.gridH);
		for (int i = first; i <= last; i++) {
			BestiarySnapshot.FamilyProgress fam = families.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * rowPitch;
			int by = ly + row * rowPitch - this.scroll;
			if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
				continue;
			}
			boolean linkedHover = !linkedId.isBlank()
				&& fam.family() != null
				&& linkedId.equals(fam.family().id());
			boolean searchHit = matchesSearch(fam);
			PvDraw.fill(g, bx, by, SLOT, SLOT, slotColor(fam));
			int border = searchHit ? 0xFF55FF55 : linkedHover ? PvDraw.COLOR_ACCENT : 0x00FFFFFF;
			g.outline(bx, by, SLOT, SLOT, border);
			ItemStack icon = familyIcon(fam);
			if (!icon.isEmpty()) {
				float iconScale = (SLOT - 6) / 16f;
				g.pose().pushMatrix();
				g.pose().translate(bx + 3, by + 3);
				g.pose().scale(iconScale, iconScale);
				g.item(icon, 0, 0);
				g.pose().popMatrix();
			}
		}
		g.disableScissor();

		if (linked != null && linked.family() != null) {
			// Rebuild the tooltip only when the hovered family changes.
			if (linked != this.tipFamily) {
				this.tipFamily = linked;
				this.tipCache = tooltipFor(linked);
			}
			this.hoverKey = linked.family().id();
			this.hoverTip = this.tipCache;
		}
	}

	/**
	 * Family under the cursor on either the icon grid or the matching distribution segment.
	 */
	private BestiarySnapshot.FamilyProgress findLinkedFamily(
		List<BestiarySnapshot.FamilyProgress> families,
		int cols,
		int gridLeft,
		int gridTop,
		int barY,
		int barW,
		int[] barWidths,
		int mouseX,
		int mouseY
	) {
		int rowPitch = SLOT + SLOT_GAP;
		boolean overGrid = mouseX >= this.gridX && mouseX < this.gridX + this.gridW
			&& mouseY >= this.gridY && mouseY < this.gridY + this.gridH;
		if (overGrid) {
			for (int i = 0; i < families.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int bx = gridLeft + col * rowPitch;
				int by = gridTop + row * rowPitch - this.scroll;
				if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
					continue;
				}
				if (mouseX >= bx && mouseX < bx + SLOT && mouseY >= by && mouseY < by + SLOT) {
					return families.get(i);
				}
			}
		}

		boolean overBar = mouseY >= barY && mouseY < barY + DIST_BAR_H
			&& mouseX >= gridLeft && mouseX < gridLeft + barW;
		if (!overBar || barWidths == null) {
			return null;
		}
		int innerX = gridLeft + 1;
		int innerW = Math.max(0, barW - 2);
		int cursor = innerX;
		for (int i = 0; i < families.size() && i < barWidths.length; i++) {
			int segW = Math.max(1, barWidths[i]);
			if (cursor >= innerX + innerW) {
				break;
			}
			segW = Math.min(segW, innerX + innerW - cursor);
			if (mouseX >= cursor && mouseX < cursor + segW) {
				return families.get(i);
			}
			cursor += segW;
			if (i < families.size() - 1 && cursor < innerX + innerW) {
				cursor += DIST_SEP;
			}
		}
		return null;
	}

	/**
	 * Equal-width segments across the category.
	 * Colour = that family's own completion (tier / tiersMax).
	 */
	private void drawTierDistributionBar(
		GuiGraphicsExtractor g,
		List<BestiarySnapshot.FamilyProgress> families,
		int x,
		int y,
		int w,
		int[] widths,
		String linkedId
	) {
		if (families.isEmpty() || w <= 0) {
			return;
		}
		PvDraw.roundedFill(g, x, y, w, DIST_BAR_H, 3, DIST_BG);
		PvDraw.roundedRing(g, x, y, w, DIST_BAR_H, 3, DIST_BORDER, PvDraw.C_ALL);

		int innerX = x + 1;
		int innerY = y + 1;
		int innerW = Math.max(0, w - 2);
		int innerH = Math.max(0, DIST_BAR_H - 2);
		if (innerW <= 0 || innerH <= 0 || widths == null) {
			return;
		}

		int cursor = innerX;
		for (int i = 0; i < families.size() && i < widths.length; i++) {
			int segW = Math.max(1, widths[i]);
			if (cursor >= innerX + innerW) {
				break;
			}
			segW = Math.min(segW, innerX + innerW - cursor);
			BestiarySnapshot.FamilyProgress fam = families.get(i);
			PvDraw.fill(g, cursor, innerY, segW, innerH, completionColor(fam));
			boolean linkedHover = linkedId != null && !linkedId.isBlank()
				&& fam.family() != null
				&& linkedId.equals(fam.family().id());
			if (linkedHover) {
				g.outline(cursor, innerY, segW, innerH, PvDraw.COLOR_ACCENT);
			}
			cursor += segW;
			if (i < families.size() - 1 && cursor < innerX + innerW) {
				PvDraw.fill(g, cursor, innerY, DIST_SEP, innerH, DIST_BORDER);
				cursor += DIST_SEP;
			}
		}
	}

	private static int[] barSegmentWidths(int n, int innerW) {
		if (n <= 0 || innerW <= 0) {
			return new int[0];
		}
		int sepTotal = Math.max(0, (n - 1) * DIST_SEP);
		int fillW = Math.max(n, innerW - sepTotal);
		int[] weights = new int[n];
		for (int i = 0; i < n; i++) {
			weights[i] = 1;
		}
		return distributeWidths(weights, n, fillW);
	}

	private static int[] distributeWidths(int[] weights, int weightSum, int totalPx) {
		int n = weights.length;
		int[] out = new int[n];
		if (n == 0 || totalPx <= 0 || weightSum <= 0) {
			return out;
		}
		int assigned = 0;
		double[] frac = new double[n];
		for (int i = 0; i < n; i++) {
			double exact = totalPx * (weights[i] / (double) weightSum);
			out[i] = Math.max(1, (int) Math.floor(exact));
			frac[i] = exact - Math.floor(exact);
			assigned += out[i];
		}
		// If every segment got the 1px floor, we may overshoot - scale down largest.
		while (assigned > totalPx) {
			int best = 0;
			for (int i = 1; i < n; i++) {
				if (out[i] > out[best]) {
					best = i;
				}
			}
			if (out[best] <= 1) {
				break;
			}
			out[best]--;
			assigned--;
		}
		int remain = totalPx - assigned;
		while (remain > 0) {
			int best = 0;
			for (int i = 1; i < n; i++) {
				if (frac[i] > frac[best] || (frac[i] == frac[best] && out[i] < out[best])) {
					best = i;
				}
			}
			out[best]++;
			frac[best] = -1;
			remain--;
		}
		return out;
	}

	/** Slot background: green when maxed, yellow when started, gray when untouched. */
	private static String fit(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String shown = text;
		while (shown.length() > 1 && font.width(shown + "...") > maxW) {
			shown = shown.substring(0, shown.length() - 1);
		}
		return shown + "...";
	}

	private static int slotColor(BestiarySnapshot.FamilyProgress fam) {
		if (fam == null || fam.tier() <= 0) {
			return 0xFF2A2A32;
		}
		if (fam.maxed() || (fam.tiersMax() > 0 && fam.tier() >= fam.tiersMax())) {
			return 0xFF1E5A2A;
		}
		return 0xFF8A4A10;
	}

	private static int completionColor(BestiarySnapshot.FamilyProgress fam) {
		if (fam == null) {
			return DIST_LOW;
		}
		if (fam.maxed() || (fam.tiersMax() > 0 && fam.tier() >= fam.tiersMax())) {
			return DIST_MAXED;
		}
		float pct = fam.tiersMax() <= 0 ? 0F : fam.tier() / (float) fam.tiersMax();
		if (pct < 0.25F) {
			return DIST_LOW;
		}
		if (pct < 0.75F) {
			return DIST_MID;
		}
		return DIST_HIGH;
	}

	private List<Component> tooltipFor(BestiarySnapshot.FamilyProgress fam) {
		List<Component> tip = new ArrayList<>();
		tip.add(SkyBlockItemFactory.legacyLine("§a" + fam.family().name()));
		tip.add(SkyBlockItemFactory.legacyLine(
			"§7Tier: §e" + fam.tier() + "§7/§e" + fam.tiersMax() + (fam.maxed() ? " §a(MAX)" : "")));
		if (fam.maxed() || fam.nextNeed() <= 0L) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Kills: §aMAX"));
		} else {
			tip.add(SkyBlockItemFactory.legacyLine(
				"§7Kills: §f" + FormatUtil.commas(fam.intoTier())
					+ "§7/§f" + FormatUtil.commas(fam.nextNeed())));
		}
		tip.add(SkyBlockItemFactory.legacyLine("§7Total kills: §f" + FormatUtil.commas(fam.kills())));
		long cap = fam.family().cap();
		if (fam.maxed() && cap > 0 && fam.kills() > cap) {
			tip.add(SkyBlockItemFactory.legacyLine("§8+" + FormatUtil.commas(fam.kills() - cap) + " past final milestone"));
		}
		if (fam.deaths() > 0) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Deaths to family: §c" + FormatUtil.commas(fam.deaths())));
		}
		if (!fam.family().mobIds().isEmpty()) {
			tip.add(Component.empty());
			tip.add(SkyBlockItemFactory.legacyLine("§7Mobs:"));
			int shown = 0;
			for (String mob : fam.family().mobIds()) {
				if (shown >= 8) {
					tip.add(SkyBlockItemFactory.legacyLine("§8…" + (fam.family().mobIds().size() - shown) + " more"));
					break;
				}
				tip.add(SkyBlockItemFactory.legacyLine("§8• §7" + prettyMobId(mob)));
				shown++;
			}
		}
		return tip;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.gridW <= 0 || this.gridH <= 0) {
			return false;
		}
		if (mouseX < this.gridX || mouseX >= this.gridX + this.gridW
			|| mouseY < this.gridY || mouseY >= this.gridY + this.gridH) {
			return false;
		}
		if (this.maxScroll <= 0) {
			return true;
		}
		int delta = scrollY > 0 ? -SCROLL_STEP : SCROLL_STEP;
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll + delta));
		return true;
	}

	private boolean matchesSearch(BestiarySnapshot.FamilyProgress fam) {
		if (this.searchQuery.isBlank() || fam == null || fam.family() == null) {
			return false;
		}
		String name = fam.family().name().toLowerCase(Locale.ROOT);
		if (name.contains(this.searchQuery)) {
			return true;
		}
		String prettyName = prettyMobId(fam.family().name()).toLowerCase(Locale.ROOT);
		if (prettyName.contains(this.searchQuery)) {
			return true;
		}
		for (String mob : fam.family().mobIds()) {
			if (mob == null) {
				continue;
			}
			if (mob.toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
				return true;
			}
			if (prettyMobId(mob).toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
				return true;
			}
		}
		return false;
	}

	public ItemStack categoryIcon(String categoryId) {
		String key = "cat:" + (categoryId == null ? "" : categoryId);
		ItemStack cached = this.iconCache.get(key);
		if (cached != null) {
			return cached;
		}
		BestiaryData.Category cat = BestiaryData.category(categoryId);
		ItemStack icon = ItemStack.EMPTY;
		if (cat != null) {
			if (cat.textureValue() != null && !cat.textureValue().isBlank()) {
				icon = SkyBlockItemFactory.texturedHead(cat.textureValue());
			}
			if (icon.isEmpty() && cat.itemIcon() != null && !cat.itemIcon().isBlank()) {
				icon = vanillaItem(cat.itemIcon());
			}
		}
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.MAP);
		}
		this.iconCache.put(key, icon);
		return icon;
	}

	private ItemStack familyIcon(BestiarySnapshot.FamilyProgress fam) {
		String id = fam.family().id();
		ItemStack cached = this.iconCache.get(id);
		if (cached != null) {
			return cached;
		}
		ItemStack icon = ItemStack.EMPTY;
		String texture = fam.family().textureValue();
		if (texture != null && !texture.isBlank()) {
			icon = SkyBlockItemFactory.texturedHead(texture);
		}
		if (icon.isEmpty() && fam.family().itemIcon() != null && !fam.family().itemIcon().isBlank()) {
			icon = vanillaItem(fam.family().itemIcon());
		}
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.SPAWNER);
		}
		this.iconCache.put(id, icon);
		return icon;
	}

	/** Draw after island buttons / frame tabs so tips paint on top. */
	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.hoverTip, mouseX, mouseY, screenW, screenH);
		}
	}

	/** {@code bogged_10} / {@code magma_cube_rider_90} → readable title case, level suffix dropped. */
	static String prettyMobId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String cleaned = FORMAT_CODE.matcher(id.trim()).replaceAll("");
		cleaned = LEVEL_SUFFIX.matcher(cleaned).replaceAll("");
		return MobNames.pretty(cleaned);
	}

	private static final java.util.regex.Pattern FORMAT_CODE = java.util.regex.Pattern.compile("§[0-9a-fk-or]");
	private static final java.util.regex.Pattern LEVEL_SUFFIX = java.util.regex.Pattern.compile("(?i)_\\d+$");

	private static ItemStack vanillaItem(String name) {
		Identifier itemId = Identifier.tryParse(name.toLowerCase(Locale.ROOT).replace(' ', '_'));
		return itemId == null ? ItemStack.EMPTY
			: BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}
}

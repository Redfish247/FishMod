/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenCosts;
import fishmod.pv.data.GardenData;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared Garden UI helpers and hover zones. */
public final class GardenUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int BAR_LABEL_GAP = 2;
	public static final int BAR_AFTER = 4;
	public static final int STAT_ROW = 12;
	public static final int ICON = 16;
	public static final int SLOT = 20;
	public static final int SLOT_GAP = 4;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	public static final int ITEM_SLOT_HOVER = 0xFF4A4A5A;
	public static final int VISITOR_CELL_W = 22;
	public static final int VISITOR_CELL_H = 22;
	public static final int CHIP_CELL = 22;
	public static final int CHIP_GAP = 4;
	public static final int GH_CELL_W = 24;
	public static final int GH_CELL_H = 24;
	public static final int CELL_BORDER = 0xFF3A3A48;
	public static final int CREDIT_C = 0xFF4A4A58;
	public static final int BAR_CROP = 0xFF6BAA3D;
	public static final int BAR_GARDEN = 0xFF4CAF50;
	public static final int BAR_FARM = 0xFF8BC34A;
	public static final int BAR_VISITOR = 0xFF9C6B4A;
	public static final int BAR_COMPOST = 0xFF7CB342;
	public static final int BRONZE = 0xFFCD7F32;
	public static final int SILVER = 0xFFC0C0C0;
	public static final int GOLD = 0xFFFFD700;
	public static final int PLATINUM = 0xFF55FFFF;
	public static final int DIAMOND = 0xFFAAFFFF;
	public static final int MEDAL_ORB_EMPTY = 0x30FFFFFF;
	public static final int GHOST = 0xFF9A9AAC;
	public static final int COPPER = 0xFFE07A3D;
	public static final int VISITS_C = 0xFFE8E8F0;
	public static final int COMPLETED_C = 0xFF55FF55;
	public static final int REJECTED_C = 0xFFFF5555;
	public static final float CREDIT_SCALE = 0.75F;
	public static final int FLIP_MS = 480;
	public static final int PANEL_HOVER = 0x0AFFFFFF;
	public static final int ORB = 7;
	public static final int ORB_GAP = 3;

	public final List<HoverZone> zones = new ArrayList<>();

	public void clearZones() {
		this.zones.clear();
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}
	public int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, int mx, int my
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			this.zones.add(new HoverZone(x, y, w, bottom - y, fishmod.pv.data.Leveling.hoverTextLines(hover)));
		}
		return bottom;
	}
	public static int barRowH(Font font) {
		return font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER;
	}
	public static int statLine(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor) {
		drawPair(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor);
		return y + STAT_ROW;
	}
	public static void drawPair(
		GuiGraphicsExtractor g, Font font, String left, String right, int x, int y, int w, int leftColor, int rightColor
	) {
		String r = right == null ? "" : right;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, left, leftMax), x, y, leftColor);
		PvDraw.textRight(g, font, r, x + w, y, rightColor);
	}
	public static void drawCellBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.outline(x, y, w, h, CELL_BORDER);
	}
	public static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int size, String packModel) {
		ItemStack icon = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (id != null && !id.isBlank() && (packModel != null && !packModel.isBlank() || SkyBlockIconRenderer.hasKnownIcon(id))) {
			SkyBlockIconRenderer.draw(g, icon, id, packModel, x, y, size);
			return;
		}
		ItemStack vanilla = vanillaFallback(id);
		if (!vanilla.isEmpty()) {
			drawStack(g, vanilla, x, y, size);
			return;
		}
		if (isTexturedHead(icon) || (!icon.isEmpty() && !icon.is(Items.PAPER) && !icon.is(Items.PLAYER_HEAD))) {
			SkyBlockIconRenderer.draw(g, icon, id, x, y, size);
			return;
		}
		String upper = id == null ? "" : id.toUpperCase(Locale.ROOT);
		if (upper.endsWith("_NPC") || upper.contains("MAYOR") || upper.endsWith("_MONSTER")) {
			drawStack(g, new ItemStack(Items.VILLAGER_SPAWN_EGG), x, y, size);
			return;
		}
		drawStack(g, new ItemStack(Items.PAPER), x, y, size);
	}
	public static boolean isTexturedHead(ItemStack icon) {
		if (icon == null || icon.isEmpty() || !icon.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = icon.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return false;
			}
			for (var prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}
	public static void drawStack(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (size == ICON) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / (float) ICON;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}
	public static ItemStack vanillaFallback(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "CLOCK" -> new ItemStack(Items.CLOCK);
			case "GOLDEN_HOE" -> new ItemStack(Items.GOLDEN_HOE);
			case "COAL_BLOCK" -> new ItemStack(Items.COAL_BLOCK);
			case "GOLD_INGOT" -> new ItemStack(Items.GOLD_INGOT);
			case "COMPOSTER" -> new ItemStack(Items.COMPOSTER);
			case "WHEAT" -> new ItemStack(Items.WHEAT);
			case "CARROT_ITEM", "CARROT" -> new ItemStack(Items.CARROT);
			case "POTATO_ITEM", "POTATO" -> new ItemStack(Items.POTATO);
			case "PUMPKIN" -> new ItemStack(Items.PUMPKIN);
			case "SUGAR_CANE" -> new ItemStack(Items.SUGAR_CANE);
			case "MELON" -> new ItemStack(Items.MELON_SLICE);
			case "CACTUS" -> new ItemStack(Items.CACTUS);
			case "INK_SACK:3", "INK_SACK-3", "COCOA_BEANS" -> new ItemStack(Items.COCOA_BEANS);
			case "RED_MUSHROOM", "MUSHROOM" -> new ItemStack(Items.RED_MUSHROOM);
			case "NETHER_STALK", "NETHER_WART" -> new ItemStack(Items.NETHER_WART);
			case "DOUBLE_PLANT", "SUNFLOWER" -> new ItemStack(Items.SUNFLOWER);
			case "MOONFLOWER" -> new ItemStack(Items.BLUE_ORCHID);
			case "WILD_ROSE" -> new ItemStack(Items.ROSE_BUSH);
			case "SEEDS", "WHEAT_SEEDS" -> new ItemStack(Items.WHEAT_SEEDS);
			default -> ItemStack.EMPTY;
		};
	}
	public List<PvTooltip.Line> cropMilestoneTip(GardenSnapshot snapshot, GardenSnapshot.CropRow crop) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(crop.name(), PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of(
			"Milestone " + crop.milestone() + (crop.milestoneMaxed() ? " (MAX)" : ""),
			PvDraw.COLOR_ACCENT
		));
		GardenData.LevelProgress ms = GardenData.cropMilestone(crop.id(), crop.collected());
		if (crop.milestoneMaxed()) {
			tip.add(PvTooltip.Line.of("Overflow: " + FormatUtil.commas(ms.intoLevel()), PvDraw.COLOR_MUTED));
			tip.add(PvTooltip.Line.of("Total: " + FormatUtil.commas(crop.collected()), PvDraw.COLOR_MUTED));
		} else {
			tip.add(PvTooltip.Line.of(
				FormatUtil.commas(ms.intoLevel()) + " / " + FormatUtil.commas(ms.needForNext()),
				PvDraw.COLOR_MUTED
			));
		}
		tip.add(PvTooltip.Line.of("Upgrade: +" + crop.upgradeLevel(), PvDraw.COLOR_MUTED));
		GardenCosts.Progress copper = crop.upgradeCopper();
		tip.add(PvTooltip.Line.of(
			"Copper: " + FormatUtil.commas(copper.spent()) + " / " + FormatUtil.commas(copper.total())
				+ " (" + FormatUtil.percent(copper.fraction()) + ")",
			copper.maxed() ? COMPLETED_C : COPPER
		));
		if (snapshot.hasUniqueGold(crop.id())) {
			tip.add(PvTooltip.Line.of("Unique gold", GOLD));
		}
		return tip;
	}
	public static String fitValue(Font font, String label, String value, int w) {
		if (value == null || value.isBlank()) {
			return "";
		}
		int maxValue = w - font.width(label) - 10;
		if (maxValue < 12) {
			return "";
		}
		return font.width(value) <= maxValue ? value : trim(font, value, maxValue);
	}
	public static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ell = "...";
		int ellW = font.width(ell);
		if (maxW <= ellW) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() > 0 && font.width(sb.toString()) + ellW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ell;
	}
	public static String title(String raw) {
		if (raw == null || raw.isBlank()) {
			return "?";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).split("[\\s_]+");
		StringBuilder out = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(p.charAt(0)));
			if (p.length() > 1) {
				out.append(p.substring(1));
			}
		}
		return out.toString();
	}

	public static void addComposterRemaining(List<PvTooltip.Line> tip, GardenCosts.ComposterRemaining remaining) {
		if (remaining.none()) {
			tip.add(PvTooltip.Line.of("Nothing left to buy", COMPLETED_C));
			return;
		}
		tip.add(PvTooltip.Line.row("Copper", PvDraw.COLOR_MUTED, FormatUtil.commas(remaining.copper()), COPPER));
		if (!remaining.crops().isEmpty()) {
			tip.add(PvTooltip.Line.of("Crops", PvDraw.COLOR_ACCENT));
			for (var e : remaining.crops().entrySet()) {
				tip.add(costRow(e.getKey(), e.getValue(), PvDraw.COLOR_TEXT));
			}
		}
		if (!remaining.rare().isEmpty()) {
			tip.add(PvTooltip.Line.of("Rare crops", PvDraw.COLOR_GOLD));
			for (var e : remaining.rare().entrySet()) {
				tip.add(costRow(e.getKey(), e.getValue(), PvDraw.COLOR_GOLD));
			}
		}
		if (!remaining.crops().isEmpty() || !remaining.rare().isEmpty()) {
			double coins = remaining.coins();
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Coin cost", PvDraw.COLOR_MUTED,
				coins > 0D ? FormatUtil.shortCoins(coins) : "Unknown", coins > 0D ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED));
			if (coins > 0D && !remaining.fullyPriced()) {
				tip.add(PvTooltip.Line.meta("Some items have no bazaar price"));
			}
		}
	}

	private static PvTooltip.Line costRow(String itemId, long amount, int amountColor) {
		double coins = GardenCosts.itemCoins(itemId, amount);
		return PvTooltip.Line.row(
			List.of(PvTooltip.Span.of(costItemName(itemId), PvDraw.COLOR_MUTED)),
			List.of(PvTooltip.Span.of(FormatUtil.commas(amount), amountColor)),
			List.of(PvTooltip.Span.of(coins > 0D ? FormatUtil.shortCoins(coins) : "-", coins > 0D ? PvDraw.COLOR_GOLD : PvDraw.COLOR_MUTED))
		).withIcon(costIcon(itemId));
	}

	private static final Map<String, ItemStack> COST_ICONS = new HashMap<>();

	private static ItemStack costIcon(String itemId) {
		String key = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		ItemStack cached = COST_ICONS.get(key);
		if (cached != null) {
			return cached;
		}
		ItemStack icon = SkyBlockItemFactory.iconStack(key);
		// Don't pin the paper placeholder while NEU is still loading.
		if (!icon.isEmpty() && !icon.is(Items.PAPER)) {
			COST_ICONS.put(key, icon);
		}
		return icon;
	}

	public static String costItemName(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "ENCHANTED_HUGE_MUSHROOM_1" -> "Enchanted Brown Mushroom Block";
			case "ENCHANTED_HUGE_MUSHROOM_2" -> "Enchanted Red Mushroom Block";
			case "MUTANT_NETHER_STALK" -> "Mutant Nether Wart";
			default -> title(id);
		};
	}

	public record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}

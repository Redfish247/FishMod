/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import static fishmod.pv.gui.foraging.ForagingUi.DISABLED;
import static fishmod.pv.gui.foraging.ForagingUi.ENABLED;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT;
import static fishmod.pv.gui.foraging.ForagingUi.SLOT_GAP;
import static fishmod.pv.gui.foraging.ForagingUi.STAT_ROW;

import fishmod.pv.data.AttributeShardsData;
import fishmod.pv.data.BestiaryData;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.foraging.ForagingUi;
import fishmod.pv.gui.foraging.ForagingUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Safari stats and critter grid, drawn inside the Hunting page. */
final class SafariSection {
	private static final String[] TICKET_ORDER = { "basic", "economy", "premium", "first_class" };
	private static final String[] BIOME_ORDER = { "forest", "cavern", "haunted", "icy", "desert" };
	// Hypixel writes SPARKLING in bold gold (§6§l).
	private static final int SPARKLING = 0xFFFFAA00;
	private static final int SPARKLING_BORDER = 0x99FFAA00;

	private int gridScroll;
	private int gridMaxScroll;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	void resetScroll() {
		this.gridScroll = 0;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.gridMaxScroll <= 0) {
			return false;
		}
		if (!ForagingUi.hit(mouseX, mouseY, this.gridX, this.gridY, this.gridW, this.gridH)) {
			return false;
		}
		int next = Math.max(0, Math.min(this.gridMaxScroll, this.gridScroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.gridScroll) {
			return false;
		}
		this.gridScroll = next;
		return true;
	}

	/** Safari, Biome captures and Milestones sections. Returns the y after the last row. */
	int drawStats(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot.SafariInfo safari,
		int panelX, int panelW, int lx, int ly, int lw, List<HoverZone> zones
	) {
		PvDraw.text(g, font, "Safari", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;

		if (!safari.present()) {
			PvDraw.text(g, font, "No safari data", lx, ly, PvDraw.COLOR_MUTED);
			return ly + STAT_ROW;
		}

		ly = ForagingUi.statLine(g, font, "Critters", FormatUtil.commas(safari.discoveredCritters().size()),
			lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = ForagingUi.statLine(g, font, "Captures", FormatUtil.commas(safari.totalCaptures()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = drawSparkling(g, font, safari, lx, ly, lw, zones);
		ly = drawTickets(g, font, safari, lx, ly, lw, zones) + 2;

		ly = ForagingUi.sectionSeparator(g, font, panelX, ly, panelW);
		PvDraw.text(g, font, "Biome captures", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Map<String, Long> captures = orderedLongMap(safari.biomeCaptures(), BIOME_ORDER);
		if (captures.isEmpty()) {
			ly = ForagingUi.statLine(g, font, "None", "-", lx, ly, lw, PvDraw.COLOR_MUTED) + 2;
		} else {
			for (var e : captures.entrySet()) {
				ly = ForagingUi.statLine(g, font, ForagingUi.pretty(e.getKey()), FormatUtil.commas(e.getValue()),
					lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
			}
			ly += 2;
		}

		ly = ForagingUi.sectionSeparator(g, font, panelX, ly, panelW);
		PvDraw.text(g, font, "Milestones", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Map<String, Integer> miles = orderedIntMap(safari.milestoneTiers(), BIOME_ORDER);
		if (miles.isEmpty()) {
			return ForagingUi.statLine(g, font, "None", "-", lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		for (var e : miles.entrySet()) {
			ly = ForagingUi.statLine(g, font, ForagingUi.pretty(e.getKey()), "Tier " + e.getValue(),
				lx, ly, lw, ENABLED) + 1;
		}
		return ly;
	}

	void drawCritterGrid(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot.SafariInfo safari,
		int rx, int ry, int rw, int bottom, int mx, int my, List<HoverZone> zones
	) {
		List<Critter> critters = critterRoster(safari);
		int discovered = 0;
		for (Critter critter : critters) {
			if (critter.discovered()) {
				discovered++;
			}
		}
		PvDraw.text(g, font, "Critters", rx, ry, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, discovered + " / " + critters.size(), rx + rw, ry, PvDraw.COLOR_ACCENT);
		ry += font.lineHeight + 4;

		this.gridX = rx;
		this.gridY = ry;
		this.gridW = rw;
		this.gridH = Math.max(0, bottom - ry);

		if (critters.isEmpty()) {
			this.gridMaxScroll = 0;
			PvDraw.textCentered(g, font, safari.present() ? "None discovered" : "No safari data",
				rx + rw / 2, ry + this.gridH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.max(1, (rw + SLOT_GAP) / (SLOT + SLOT_GAP));
		int rows = (critters.size() + cols - 1) / cols;
		int contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP;
		this.gridMaxScroll = Math.max(0, contentH - this.gridH);
		this.gridScroll = Math.min(this.gridScroll, this.gridMaxScroll);

		ItemStack fallback = new ItemStack(fishmod.compat.CI.LIME_DYE);
		g.enableScissor(this.gridX, this.gridY, this.gridX + this.gridW, this.gridY + this.gridH);
		for (int i = 0; i < critters.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = rx + col * (SLOT + SLOT_GAP);
			int by = ry + row * (SLOT + SLOT_GAP) - this.gridScroll;
			if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
				continue;
			}
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= this.gridY && my < this.gridY + this.gridH;
			Critter critter = critters.get(i);
			PvDraw.roundedFill(g, bx, by, SLOT, SLOT, 3, ITEM_SLOT_BG);
			PvDraw.roundedRing(g, bx, by, SLOT, SLOT, 3, hovered ? PvDraw.COLOR_ACCENT : critter.sparkling() ? SPARKLING_BORDER : ITEM_SLOT_BORDER, PvDraw.C_ALL);
			if (!critter.discovered()) {
				g.item(UNDISCOVERED_ICON, bx + 1, by + 1);
			} else if (!critter.texture().isBlank()) {
				g.item(this.critterHeads.computeIfAbsent(critter.texture(), SkyBlockItemFactory::texturedHead), bx + 1, by + 1);
			} else {
				drawCritterIcon(g, critter.id(), bx + 1, by + 1, fallback);
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(critter.name(), critter.discovered() ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED,
				critter.discovered() ? "Discovered" : "Undiscovered", critter.discovered() ? ENABLED : DISABLED));
			if (critter.discovered() || critter.sparkling()) {
				tip.add(PvTooltip.Line.row("Sparkling", PvDraw.COLOR_MUTED,
					critter.sparkling() ? "Discovered" : "Undiscovered", critter.sparkling() ? SPARKLING : PvDraw.COLOR_MUTED));
			}
			ForagingUi.addClippedHover(zones, bx, by, SLOT, SLOT,
				this.gridX, this.gridY, this.gridW, this.gridH, tip);
		}
		g.disableScissor();
	}

	private static final ItemStack UNDISCOVERED_ICON = new ItemStack(fishmod.compat.CI.GRAY_DYE);
	private final Map<String, ItemStack> critterHeads = new java.util.HashMap<>();

	private record Critter(String id, String name, String texture, boolean discovered, boolean sparkling) {
	}

	/** Every NEU bestiary safari critter in biome order, plus any discovered id NEU doesn't list yet. */
	private static List<Critter> critterRoster(ForagingSnapshot.SafariInfo safari) {
		java.util.Set<String> found = new java.util.HashSet<>();
		for (String id : safari.discoveredCritters()) {
			found.add(critterKey(id));
		}
		java.util.Set<String> sparkling = new java.util.HashSet<>();
		for (String id : safari.sparklingDiscovered()) {
			sparkling.add(critterKey(id));
		}
		List<Critter> out = new ArrayList<>();
		java.util.Set<String> listed = new java.util.HashSet<>();
		BestiaryData.Category category = BestiaryData.category("safari");
		if (category != null) {
			for (BestiaryData.Family family : category.families()) {
				String key = critterKey(family.name());
				listed.add(key);
				out.add(new Critter(key, family.name(), family.textureValue(), found.contains(key), sparkling.contains(key)));
			}
		}
		List<String> extra = new ArrayList<>();
		for (String id : safari.discoveredCritters()) {
			if (!listed.contains(critterKey(id))) {
				extra.add(id);
			}
		}
		extra.sort(Comparator.comparing(s -> ForagingUi.pretty(s).toLowerCase(Locale.ROOT)));
		for (String id : extra) {
			out.add(new Critter(id, ForagingUi.pretty(id), "", true, sparkling.contains(critterKey(id))));
		}
		return out;
	}

	private static String critterKey(String raw) {
		return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static void drawCritterIcon(GuiGraphicsExtractor g, String critterId, int x, int y, ItemStack fallback) {
		ItemStack stack = critterSkull(critterId);
		if (stack != null && !stack.isEmpty()) {
			g.item(stack, x, y);
			return;
		}
		if (critterId != null && !critterId.isBlank() && SkyBlockIconRenderer.hasKnownIcon(critterId)) {
			SkyBlockIconRenderer.draw(g, fallback, critterId, x, y, 16);
			return;
		}
		g.item(fallback, x, y);
	}

	private static ItemStack critterSkull(String critterId) {
		if (critterId == null || critterId.isBlank()) {
			return ItemStack.EMPTY;
		}
		AttributeShardsData.ensureLoaded();
		AttributeShardsData.Def def = AttributeShardsData.byCritterId(critterId);
		if (def != null) {
			ItemStack fromDef = SkyBlockItemFactory.iconStack(def.iconId());
			if (usableIcon(fromDef)) {
				return fromDef;
			}
			if (def.bazaarName() != null) {
				ItemStack bazaar = SkyBlockItemFactory.iconStack(def.bazaarName());
				if (usableIcon(bazaar)) {
					return bazaar;
				}
			}
		}
		String upper = critterId.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		for (String id : List.of("SHARD_" + upper, upper, "ATTRIBUTE_SHARD_" + upper + ";1")) {
			ItemStack stack = SkyBlockItemFactory.iconStack(id);
			if (usableIcon(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean usableIcon(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER) || stack.is(Items.BARRIER)
			|| stack.is(fishmod.compat.CI.LIME_DYE)) {
			return false;
		}
		// Bare PLAYER_HEAD renders as Steve, so only accept textured skulls.
		if (stack.is(Items.PLAYER_HEAD)) {
			return SkyBlockItemFactory.isTexturedPlayerHead(stack);
		}
		return true;
	}

	private int drawSparkling(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot.SafariInfo safari, int x, int y, int w, List<HoverZone> zones
	) {
		List<String> found = safari.sparklingDiscovered();
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Sparkling Critters", SPARKLING));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Discovered", PvDraw.COLOR_MUTED, FormatUtil.commas(found.size()), SPARKLING));
		tip.add(PvTooltip.Line.row("Captured", PvDraw.COLOR_MUTED, FormatUtil.commas(safari.sparklingCaptured()), PvDraw.COLOR_TEXT));
		if (!found.isEmpty()) {
			tip.add(PvTooltip.Line.blank());
			for (String id : found) {
				tip.add(PvTooltip.Line.of(ForagingUi.pretty(id), SPARKLING));
			}
		}
		String value = FormatUtil.commas(found.size()) + " / " + FormatUtil.commas(safari.sparklingCaptured());
		PvDraw.text(g, font, "Sparkling", x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value, x + w, y, found.isEmpty() ? PvDraw.COLOR_MUTED : SPARKLING);
		zones.add(HoverZone.of(x, y, w, STAT_ROW, tip));
		return y + STAT_ROW + 1;
	}

	/** One row: {@code Tickets  basic/economy/premium/first_class}, each count in its tier colour. */
	private int drawTickets(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot.SafariInfo safari, int x, int y, int w, List<HoverZone> zones
	) {
		Map<String, Long> tickets = safari.tickets();
		net.minecraft.network.chat.MutableComponent value = net.minecraft.network.chat.Component.empty();
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Tickets", PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.divider());
		for (int i = 0; i < TICKET_ORDER.length; i++) {
			String id = TICKET_ORDER[i];
			long count = tickets == null ? 0L : tickets.getOrDefault(id, 0L);
			if (i > 0) {
				value.append(PvDraw.styled("/", PvDraw.COLOR_MUTED, false));
			}
			value.append(PvDraw.styled(FormatUtil.commas(count), ticketColor(id), false));
			tip.add(PvTooltip.Line.row(ForagingUi.pretty(id), PvDraw.COLOR_MUTED, FormatUtil.commas(count), ticketColor(id)));
		}
		PvDraw.text(g, font, "Tickets", x, y, PvDraw.COLOR_MUTED);
		g.text(font, value, x + w - font.width(value), y, PvDraw.COLOR_WHITE, false);
		zones.add(HoverZone.of(x, y, w, STAT_ROW, tip));
		return y + STAT_ROW;
	}

	private static int ticketColor(String id) {
		if (id == null) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "economy" -> 0xFF55FF55;
			case "premium" -> 0xFF6699FF;
			case "first_class" -> PvDraw.COLOR_GOLD;
			default -> 0xFFFFFFFF;
		};
	}

	private static Map<String, Long> orderedLongMap(Map<String, Long> source, String[] order) {
		LinkedHashMap<String, Long> out = new LinkedHashMap<>();
		if (source == null || source.isEmpty()) {
			return out;
		}
		for (String key : order) {
			Long v = source.get(key);
			if (v != null && v > 0L) {
				out.put(key, v);
			}
		}
		List<String> rest = new ArrayList<>();
		for (String key : source.keySet()) {
			if (!out.containsKey(key) && source.get(key) != null && source.get(key) > 0L) {
				rest.add(key);
			}
		}
		rest.sort(String.CASE_INSENSITIVE_ORDER);
		for (String key : rest) {
			out.put(key, source.get(key));
		}
		return out;
	}

	private static Map<String, Integer> orderedIntMap(Map<String, Integer> source, String[] order) {
		LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
		if (source == null || source.isEmpty()) {
			return out;
		}
		for (String key : order) {
			Integer v = source.get(key);
			if (v != null && v > 0) {
				out.put(key, v);
			}
		}
		List<String> rest = new ArrayList<>();
		for (String key : source.keySet()) {
			if (!out.containsKey(key) && source.get(key) != null && source.get(key) > 0) {
				rest.add(key);
			}
		}
		rest.sort(String.CASE_INSENSITIVE_ORDER);
		for (String key : rest) {
			out.put(key, source.get(key));
		}
		return out;
	}
}

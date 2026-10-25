/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NetworthBreakdown {
	/** SkyBlock gem shop: gems per booster cookie. */
	private static final double GEMS_PER_COOKIE = 325.0;
	/** Hypixel store pack: gems for $4.99. */
	private static final double GEMS_PER_PACK = 675.0;
	private static final double USD_PER_PACK = 4.99;
	private static final int MAX_ITEM_ROWS = 12;

	public record ItemLine(String id, String label, long count, double value) {
	}

	public record Line(String name, double value, List<ItemLine> items) {
		public Line(String name, double value) {
			this(name, value, List.of());
		}

		public Line {
			items = items == null ? List.of() : List.copyOf(items);
		}
	}

	private final double total;
	private final List<Line> categories;
	private final String note;

	public NetworthBreakdown(double total, List<Line> categories, String note) {
		this.total = total;
		this.categories = List.copyOf(categories);
		this.note = note == null ? "" : note;
	}

	public static NetworthBreakdown empty(String note) {
		return new NetworthBreakdown(0, List.of(), note);
	}

	public double total() {
		return this.total;
	}

	public List<Line> categories() {
		return this.categories;
	}

	public String note() {
		return this.note;
	}

	public List<String> tooltipLines() {
		return tooltipStyledLines().stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).reduce("", String::concat))
			.toList();
	}

	public List<PvTooltip.Line> tooltipStyledLines() {
		return tooltipStyledLines(null);
	}

	public List<PvTooltip.Line> tooltipStyledLines(NetworthMode mode) {
		return tooltipStyledLines(mode, false);
	}

	/** @param showItems per-item rows for every category (Home passes left shift) */
	public List<PvTooltip.Line> tooltipStyledLines(NetworthMode mode, boolean showItems) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Networth: ", PvDraw.COLOR_WHITE),
			PvTooltip.Span.bold(FormatUtil.commas(Math.round(this.total)), PvDraw.COLOR_GOLD)
		)));
		if (mode != null) {
			lines.add(PvTooltip.Line.of(modeLine(mode), PvDraw.COLOR_MUTED));
		}
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
		boolean anyItems = false;
		for (Line line : this.categories) {
			if (line.value() <= 0) {
				continue;
			}
			String prefix = title(line.name()) + ": ";
			String value = FormatUtil.commas(Math.round(line.value()));
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of(prefix, PvDraw.COLOR_WHITE),
				PvTooltip.Span.of(value, PvDraw.COLOR_GOLD),
				PvTooltip.Span.of(" (" + share(line.value()) + ")", PvDraw.COLOR_MUTED)
			)));
			List<ItemLine> items = line.items();
			if (items.isEmpty()) {
				continue;
			}
			anyItems = true;
			if (!showItems) {
				continue;
			}
			int shown = Math.min(MAX_ITEM_ROWS, items.size());
			for (int i = 0; i < shown; i++) {
				ItemLine item = items.get(i);
				String countBit = item.count() > 1L ? " ×" + FormatUtil.commas(item.count()) : "";
				lines.add(new PvTooltip.Line(List.of(
					PvTooltip.Span.of("  " + item.label() + countBit + ": ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(FormatUtil.shortCoins(Math.round(item.value())), PvDraw.COLOR_GOLD)
				)));
			}
			if (items.size() > shown) {
				double rest = 0;
				for (int i = shown; i < items.size(); i++) {
					rest += items.get(i).value();
				}
				lines.add(PvTooltip.Line.of(
					"  +" + (items.size() - shown) + " more items: " + FormatUtil.shortCoins(Math.round(rest)),
					PvDraw.COLOR_MUTED
				));
			}
		}
		if (anyItems && !showItems) {
			lines.add(PvTooltip.Line.of("Hold L-shift for item values", PvDraw.COLOR_MUTED));
		}
		if (!this.note.isBlank()) {
			lines.add(PvTooltip.Line.of(this.note, PvDraw.COLOR_MUTED));
		}
		lines.add(PvTooltip.Line.of("Prices: BetterPV, SkyHelper fallback", PvDraw.COLOR_MUTED));
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
		lines.add(profileValueLine());
		lines.add(PvTooltip.Line.of("(Price estimated with cookies)", PvDraw.COLOR_MUTED));
		lines.add(PvTooltip.Line.of("I do not condone IRL Trading", PvDraw.COLOR_MUTED));
		if (mode != null) {
			lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
			boolean cosmetic = mode.includeCosmetics();
			boolean unsoulbound = mode.soulboundFilter() == NetworthMode.SoulboundFilter.ONLY_UNSOULBOUND;
			lines.add(PvTooltip.Line.of(
				"Left click: " + (cosmetic ? "non-cosmetic" : "cosmetic"),
				PvDraw.COLOR_MUTED
			));
			lines.add(PvTooltip.Line.of(
				"Right click: " + (unsoulbound ? "all items" : "unsoulbound only"),
				PvDraw.COLOR_MUTED
			));
		}
		return lines;
	}

	/**
	 * NEU-style IRL estimate: networth → booster cookies (bazaar) → gems (325/cookie) → USD
	 * via the 675-gem / $4.99 pack.
	 */
	private PvTooltip.Line profileValueLine() {
		Double usd = profileValueUsd(this.total);
		if (usd == null) {
			return new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of("-", 0xFFFF5555)
			));
		}
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_WHITE),
			PvTooltip.Span.bold("$" + FormatUtil.commas(Math.round(usd)), 0xFF55FF55)
		));
	}

	private String share(double value) {
		return this.total <= 0 ? "0%" : FormatUtil.percent(value / this.total);
	}

	static String modeLine(NetworthMode mode) {
		String cosmetics = mode.includeCosmetics() ? "cosmetics included" : "cosmetics excluded";
		String soulbound = mode.soulboundFilter() == NetworthMode.SoulboundFilter.ONLY_UNSOULBOUND
			? "soulbound excluded"
			: "soulbound included";
		return "Mode: " + mode.display() + " (" + cosmetics + ", " + soulbound + ")";
	}

	public static Double profileValueUsd(double networthCoins) {
		if (networthCoins <= 0) {
			return 0.0;
		}
		double cookiePrice = ItemPricer.price("BOOSTER_COOKIE");
		if (cookiePrice <= 0) {
			return null;
		}
		double cookies = networthCoins / cookiePrice;
		return (cookies * GEMS_PER_COOKIE / GEMS_PER_PACK) * USD_PER_PACK;
	}

	private static String title(String id) {
		if ("storage".equalsIgnoreCase(id)) {
			return "Backpacks";
		}
		if ("sacks_bag".equalsIgnoreCase(id)) {
			return "Sacks Bag";
		}
		String[] parts = id.split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.AttributeShardsData;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.foraging.ForagingUi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/** Attribute shard hover: the shard item, its attribute, NEU description and progress to max. */
final class AttributeShardTooltip {
	private static final int MAX_DESCRIPTION_LINES = 8;
	private static final Pattern RARITY_FOOTER = Pattern.compile(
		"^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)\\b.*\\bSHARD\\b.*", Pattern.CASE_INSENSITIVE);

	/** Shard identity resolved from the NEU catalog entry and the shard's NEU item json. */
	record Info(String shardName, String attribute, List<String> description) {
		Info {
			description = description == null ? List.of() : List.copyOf(description);
		}
	}

	private AttributeShardTooltip() {
	}

	/**
	 * {@code def} maps the attribute to its shard ({@code displayName} = shard, {@code abilityName} = attribute);
	 * {@code neuItem} is the {@code ATTRIBUTE_SHARD_*} item whose display name and lore describe the shard.
	 */
	static Info info(AttributeShardsData.Def def, JsonObject neuItem, String fallbackAttribute) {
		String attribute = def != null && !def.abilityName().isBlank() ? def.abilityName() : blankToNull(fallbackAttribute);
		return new Info(shardName(def, neuItem), attribute, description(lore(neuItem), attribute));
	}

	static String shardName(AttributeShardsData.Def def, JsonObject neuItem) {
		String fromItem = neuItem != null && neuItem.has("displayname") && neuItem.get("displayname").isJsonPrimitive()
			? strip(neuItem.get("displayname").getAsString())
			: "";
		if (!fromItem.isBlank()) {
			return fromItem;
		}
		if (def != null && !def.displayName().isBlank()) {
			String name = def.displayName().trim();
			return name.toLowerCase(Locale.ROOT).endsWith(" shard") ? name : name + " Shard";
		}
		return "Unknown Shard";
	}

	/**
	 * Description is the lore block after the "Attribute I" line, up to the first blank line. That skips the
	 * generic Hunting Box text, click actions, requirements and the rarity footer, which the tip shows itself.
	 */
	static List<String> description(List<String> lore, String attribute) {
		List<String> out = new ArrayList<>();
		if (lore == null || lore.isEmpty()) {
			return out;
		}
		int start = 0;
		String first = strip(lore.get(0));
		if (attribute != null && first.toLowerCase(Locale.ROOT).startsWith(attribute.toLowerCase(Locale.ROOT))) {
			start = 1;
		}
		for (int i = start; i < lore.size() && out.size() < MAX_DESCRIPTION_LINES; i++) {
			String line = lore.get(i) == null ? "" : lore.get(i);
			String plain = strip(line);
			if (plain.isBlank()) {
				if (out.isEmpty()) {
					continue;
				}
				break;
			}
			if (RARITY_FOOTER.matcher(plain).matches() || plain.startsWith("You can Syphon")) {
				break;
			}
			out.add(line);
		}
		return out;
	}

	/** Remaining coin cost for {@code need} shards; null when the price is unknown (never shown as zero). */
	static String coins(int need, double unitPrice) {
		if (need <= 0) {
			return "0m";
		}
		return unitPrice > 0 ? FormatUtil.compactCoins(unitPrice * need) : null;
	}

	static List<PvTooltip.Line> lines(ForagingSnapshot.AttributeShardRow row, Info info, double unitPrice, int rarityColor) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(info.shardName(), rarityColor));
		tip.add(PvTooltip.Line.of(info.attribute() == null ? "Shard" : "Shard (" + info.attribute() + ")", PvDraw.COLOR_MUTED));
		if (info.description().isEmpty()) {
			tip.add(PvTooltip.Line.meta("No description available"));
		} else {
			for (String line : info.description()) {
				tip.add(PvTooltip.Line.text(legacySpans(line)));
			}
		}
		boolean maxed = row.level() >= row.maxLevel();
		tip.add(PvTooltip.Line.text(List.of(
			PvTooltip.Span.of("Level ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(row.level() + "/" + row.maxLevel(), maxed ? ForagingUi.COLOR_MAXED : PvDraw.COLOR_TEXT),
			PvTooltip.Span.of(" (" + FormatUtil.commas(row.shardsOwned()) + "/" + FormatUtil.commas(row.shardsForMax()) + ")",
				PvDraw.COLOR_MUTED)
		)));
		String cost = coins(need, unitPrice);
		tip.add(PvTooltip.Line.text(List.of(
			PvTooltip.Span.of("Shards to max: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(FormatUtil.commas(need), PvDraw.COLOR_ACCENT),
			cost == null
				? PvTooltip.Span.of(" (price unknown)", PvDraw.COLOR_MUTED)
				: PvTooltip.Span.of(" (" + cost + ")", PvDraw.COLOR_GOLD)
		)));
		tip.add(PvTooltip.Line.bold(ForagingUi.pretty(row.rarity()), rarityColor));
		return tip;
	}

	private static List<String> lore(JsonObject neuItem) {
		List<String> out = new ArrayList<>();
		if (neuItem == null || !neuItem.has("lore") || !neuItem.get("lore").isJsonArray()) {
			return out;
		}
		JsonArray arr = neuItem.getAsJsonArray("lore");
		for (JsonElement el : arr) {
			out.add(el != null && el.isJsonPrimitive() ? el.getAsString() : "");
		}
		return out;
	}

	private static List<PvTooltip.Span> legacySpans(String text) {
		List<PvTooltip.Span> spans = new ArrayList<>();
		int color = PvDraw.COLOR_MUTED;
		boolean bold = false;
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00a7' && i + 1 < text.length()) {
				if (!buf.isEmpty()) {
					spans.add(new PvTooltip.Span(buf.toString(), color, bold));
					buf.setLength(0);
				}
				ChatFormatting f = ChatFormatting.getByCode(text.charAt(++i));
				TextColor tc = f == null ? null : TextColor.fromLegacyFormat(f);
				if (tc != null) {
					color = 0xFF000000 | tc.getValue();
					bold = false;
				} else if (f == ChatFormatting.BOLD) {
					bold = true;
				} else if (f == ChatFormatting.RESET) {
					color = PvDraw.COLOR_MUTED;
					bold = false;
				}
			} else {
				buf.append(c);
			}
		}
		if (!buf.isEmpty()) {
			spans.add(new PvTooltip.Span(buf.toString(), color, bold));
		}
		return spans;
	}

	private static String strip(String text) {
		String plain = text == null ? null : ChatFormatting.stripFormatting(text);
		return plain == null ? "" : plain.trim();
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text;
	}
}

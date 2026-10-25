/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.gui.SkyBlockSymbols;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.ItemWorth;
import fishmod.pv.networth.NbtAttrs;
import fishmod.pv.util.LegacyChatFormatting;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Tooltip assembly, recombobulator markers, and legacy § lore parsing. */
final class ItemTooltipEnricher {
	private ItemTooltipEnricher() {
	}

	static List<Component> tooltipLines(
		InventorySnapshot.Slot slot,
		ItemStack rendered,
		boolean includeEstimatedValue
	) {
		List<Component> lines = new ArrayList<>();
		Component name = rendered.get(DataComponents.CUSTOM_NAME);
		if (name == null && slot != null && slot.displayName() != null) {
			name = legacyText(slot.displayName());
		}
		if (name == null && slot != null) {
			name = Component.literal(ItemTextFormat.prettyId(NeuItemResolver.canonicalId(slot.id())));
		}
		if (name != null) {
			if (slot != null && slot.count() > 1) {
				lines.add(
					name.copy().append(
						Component.literal(" x" + slot.count()).withStyle(ChatFormatting.DARK_GRAY)
					)
				);
			} else {
				lines.add(name);
			}
		}
		ItemLore lore = rendered.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}
		// Re-apply each frame so §k markers and max-enchant rainbow stay live.
		applyRecombobulatorMarkers(lines, slot);
		recolorizeEnchantLines(lines);
		if (includeEstimatedValue) {
			appendEstimatedValueHint(lines, slot);
		}
		appendCreatedDate(lines, slot);
		return lines;
	}

	/** Rebuild T7 rainbow enchants from current time (cached lore bakes a static rainbow). */
	static void recolorizeEnchantLines(List<Component> lines) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		for (int i = 0; i < lines.size(); i++) {
			Component line = lines.get(i);
			if (line == null) {
				continue;
			}
			Component colored = EnchantTooltip.colorize(line);
			if (colored != line) {
				lines.set(i, colored);
			}
		}
	}

	/** Hypixel {@code timestamp} on extraAttributes (item craft / obtain time). */
	static void appendCreatedDate(List<Component> lines, InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty() || lines == null) {
			return;
		}
		CompoundTag ea = slot.extraAttributes();
		if (ea == null) {
			return;
		}
		long ms = NbtAttrs.longValue(ea, "timestamp", 0L);
		if (ms <= 0L) {
			ms = NbtAttrs.longValue(ea, "TIMESTAMP", 0L);
		}
		if (ms <= 0L) {
			return;
		}
		// Some payloads store seconds.
		if (ms < 10_000_000_000L) {
			ms *= 1000L;
		}
		String date = FormatUtil.prettyDate(ms);
		if (date.isBlank()) {
			return;
		}
		lines.add(Component.literal("Created " + date)
			.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
	}

	/** Inserts estimated value + click hint under the rarity line when the item has a price. */
	static void appendEstimatedValueHint(List<Component> lines, InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty() || lines == null) {
			return;
		}
		InventoryDecoder.Stack worth = new InventoryDecoder.Stack(
			slot.id(),
			slot.count(),
			slot.extraAttributes() == null ? new CompoundTag() : slot.extraAttributes(),
			slot.lore() == null ? List.of() : slot.lore(),
			slot.soulbound(),
			slot.displayName(),
			slot.dyeColor(),
			slot.skullValue(),
			slot.skullSignature()
		);
		double value = ItemWorth.value(worth);
		if (value <= 0) {
			return;
		}
		String coins = FormatUtil.shortCoins(Math.round(value));
		// Fixed colours so this line does not steal the rarity / name colour.
		MutableComponent estimated = Component.literal("Estimated value: ")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
			.append(Component.literal(coins).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
		MutableComponent hint = Component.literal("Click to view value breakdown")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true));

		int rarityIndex = findRarityLineIndex(lines);
		int insertAt = rarityIndex >= 0 ? rarityIndex + 1 : lines.size();
		lines.add(insertAt, estimated);
		lines.add(insertAt + 1, hint);
	}

	/** Sack contents value in place of the sack item's own price; nothing when no contents are priced. */
	static void appendSackValue(List<Component> lines, SackValue value) {
		if (lines == null || value == null || !value.known()) {
			return;
		}
		int rarityIndex = findRarityLineIndex(lines);
		int insertAt = rarityIndex >= 0 ? rarityIndex + 1 : lines.size();
		for (Component line : sackValueLines(value, false)) {
			lines.add(insertAt++, line);
		}
	}

	static List<Component> sackValueLines(SackValue value, boolean clickHint) {
		List<Component> out = new ArrayList<>();
		if (value == null || !value.known()) {
			return out;
		}
		out.add(Component.literal("Sack value: ")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
			.append(Component.literal(FormatUtil.shortCoins(Math.round(value.total())))
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))));
		if (!value.unpriced().isEmpty()) {
			int n = value.unpriced().size();
			out.add(Component.literal(n + (n == 1 ? " item has" : " items have") + " no price")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
		}
		if (clickHint) {
			out.add(Component.literal("Click to view value breakdown")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
		}
		return out;
	}

	/** Rarity / item-name colour for recombobulated rarity lines. */
	static int estimatedHeaderColor(List<Component> lines, InventorySnapshot.Slot slot) {
		int rarityIndex = findRarityLineIndex(lines);
		if (rarityIndex >= 0) {
			Integer styled = firstNonWhiteColor(lines.get(rarityIndex));
			if (styled != null) {
				return styled;
			}
			String plain = lines.get(rarityIndex).getString();
			Matcher matcher = NeuItemResolver.RARITY_WORD.matcher(plain == null ? "" : plain);
			if (matcher.find()) {
				return SkyBlockItemFactory.tierArgb(matcher.group(1));
			}
		}
		if (slot != null && slot.id() != null) {
			return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(slot.id()));
		}
		return 0xFF55FF55;
	}

	private static Integer firstNonWhiteColor(Component component) {
		if (component == null) {
			return null;
		}
		Style style = component.getStyle();
		if (style.getColor() != null) {
			int rgb = style.getColor().getValue() & 0xFFFFFF;
			if (rgb != 0xFFFFFF && rgb != 0xE8E8F0) {
				return 0xFF000000 | rgb;
			}
		}
		for (Component sibling : component.getSiblings()) {
			Integer nested = firstNonWhiteColor(sibling);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}

	static int findRarityLineIndex(List<Component> lines) {
		for (int i = lines.size() - 1; i >= 0; i--) {
			String plain = lines.get(i).getString();
			if (plain != null && NeuItemResolver.RARITY_WORD.matcher(plain).find()) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Hypixel API lore often keeps the recomb "a" glyphs but drops {@code obfuscated:true}.
	 * Rebuild the rarity line from {@code rarity_upgrades} (or placeholder a's) with real obfuscation.
	 */
	static void applyRecombobulatorMarkers(List<Component> loreLines, InventorySnapshot.Slot slot) {
		if (loreLines == null || loreLines.isEmpty() || slot == null) {
			return;
		}
		int rarityIndex = findRarityLineIndex(loreLines);
		if (rarityIndex < 0) {
			return;
		}
		boolean recombed = NbtAttrs.intValue(slot.extraAttributes(), "rarity_upgrades", 0) > 0
			|| looksLikeRecombPlaceholders(loreLines.get(rarityIndex).getString());
		if (!recombed) {
			return;
		}
		loreLines.set(rarityIndex, recombobulatedRarityLine(loreLines.get(rarityIndex)));
	}

	private static boolean looksLikeRecombPlaceholders(String plain) {
		if (plain == null) {
			return false;
		}
		String trimmed = plain.trim();
		return trimmed.length() >= 5
			&& (trimmed.charAt(0) == 'a' || trimmed.charAt(0) == 'A')
			&& Character.isWhitespace(trimmed.charAt(1))
			&& (trimmed.charAt(trimmed.length() - 1) == 'a' || trimmed.charAt(trimmed.length() - 1) == 'A')
			&& Character.isWhitespace(trimmed.charAt(trimmed.length() - 2))
			&& NeuItemResolver.RARITY_WORD.matcher(trimmed).find();
	}

	private static Component recombobulatedRarityLine(Component line) {
		if (line == null) {
			return Component.empty();
		}
		String plain = line.getString();
		if (plain == null || plain.isBlank()) {
			return line;
		}
		String core = stripRecombPlaceholders(plain.trim());
		if (core.isBlank()) {
			return line;
		}
		int colorRgb = estimatedHeaderColor(List.of(line), null);
		Style body = Style.EMPTY
			.withItalic(false)
			.withBold(true)
			.withColor(colorRgb)
			.withObfuscated(false);
		Style marker = body.withObfuscated(true);
		return Component.empty()
			.append(Component.literal("a").withStyle(marker))
			.append(Component.literal(" " + core + " ").withStyle(body))
			.append(Component.literal("a").withStyle(marker));
	}

	/** Removes static leading/trailing recomb "a" placeholders left by API lore. */
	private static String stripRecombPlaceholders(String plain) {
		String core = plain;
		if (core.length() >= 2 && (core.charAt(0) == 'a' || core.charAt(0) == 'A')
			&& Character.isWhitespace(core.charAt(1))) {
			core = core.substring(2).stripLeading();
		}
		if (core.length() >= 2 && (core.charAt(core.length() - 1) == 'a' || core.charAt(core.length() - 1) == 'A')
			&& Character.isWhitespace(core.charAt(core.length() - 2))) {
			core = core.substring(0, core.length() - 1).stripTrailing();
		}
		return core;
	}

	static Component legacyLine(String text) {
		return EnchantTooltip.colorize(legacyText(text));
	}

	static Component legacyText(String text) {
		if (text == null || text.isEmpty()) {
			return Component.empty();
		}
		text = SkyBlockSymbols.replace(text);
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY.withItalic(false);
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '§' || c == '&') && i + 1 < text.length()) {
				if (!buf.isEmpty()) {
					root.append(Component.literal(buf.toString()).withStyle(style));
					buf.setLength(0);
				}
				char code = Character.toLowerCase(text.charAt(++i));
				if (code == 'k') {
					style = style.withObfuscated(true);
					continue;
				}
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					if (LegacyChatFormatting.isColor(formatting) || formatting == ChatFormatting.RESET) {
						style = Style.EMPTY.withItalic(false);
					}
					style = style.applyFormat(formatting);
				}
			} else {
				buf.append(c);
			}
		}
		if (!buf.isEmpty()) {
			root.append(Component.literal(buf.toString()).withStyle(style));
		}
		return root;
	}

	static String cleanLoreLine(String raw) {
		return InventoryDecoder.cleanJsonText(raw);
	}
}

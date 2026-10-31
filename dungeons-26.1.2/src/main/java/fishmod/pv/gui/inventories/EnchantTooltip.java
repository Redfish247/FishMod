/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.gui.PvDraw;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * T6/T7 enchant colours matching Athen/SkyHanni max-level tables.
 * Last tier is rainbow (T7); second-to-last is gold bold (T6).
 * A 7-tier book uses VI/VII; a 5-tier book uses IV/V.
 */
public final class EnchantTooltip {
	private static final Pattern TOKEN = Pattern.compile(
		"^(.*?)\\s+(I{1,3}|IV|VI{0,3}|IX|X{1,3}I{0,3}|1[0-2]|[1-9]|10|11|12)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final int T6_GOLD = 0xFFAA00;
	private static volatile Map<String, Integer> maxByName;
	private static volatile Map<String, Boolean> ultimateByName;

	private EnchantTooltip() {
	}

	public static Component colorize(Component line) {
		if (line == null) {
			return Component.empty();
		}
		String plain = line.getString();
		if (plain == null || plain.isBlank() || !looksLikeEnchantLine(plain)) {
			return line;
		}
		ensureLoaded();
		String[] parts = plain.split("\\s*,\\s*");
		MutableComponent out = Component.empty().setStyle(Style.EMPTY.withItalic(false));
		boolean any = false;
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				out.append(Component.literal(", ").withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)));
			}
			String part = parts[i].trim();
			Matcher matcher = TOKEN.matcher(part);
			if (!matcher.matches()) {
				out.append(Component.literal(part).withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)));
				continue;
			}
			String name = matcher.group(1).trim();
			int level = parseLevel(matcher.group(2));
			Integer max = maxLevel(name);
			if (max == null || max < 2 || level <= 0) {
				out.append(Component.literal(part).withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)));
				continue;
			}
			any = true;
			boolean ultimate = isUltimate(name);
			String label = name + " " + matcher.group(2).toUpperCase(Locale.ROOT);
			if (level >= max) {
				out.append(PvDraw.chromaText(label, true));
			} else if (level == max - 1) {
				out.append(PvDraw.styled(label, T6_GOLD, true));
			} else if (ultimate) {
				out.append(Component.literal(label).withStyle(
					Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false)
				));
			} else {
				out.append(Component.literal(label).withStyle(
					Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)
				));
			}
		}
		return any ? out : line;
	}

	private static boolean looksLikeEnchantLine(String plain) {
		return TOKEN.matcher(plain.split("\\s*,\\s*")[0].trim()).matches();
	}

	private static Integer maxLevel(String name) {
		if (name == null) {
			return null;
		}
		return maxByName.get(name.toLowerCase(Locale.ROOT).trim());
	}

	private static boolean isUltimate(String name) {
		if (name == null) {
			return false;
		}
		Boolean ultimate = ultimateByName.get(name.toLowerCase(Locale.ROOT).trim());
		return ultimate != null && ultimate;
	}

	private static int parseLevel(String raw) {
		if (raw == null) {
			return 0;
		}
		String token = raw.trim().toUpperCase(Locale.ROOT);
		return switch (token) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			case "VI" -> 6;
			case "VII" -> 7;
			case "VIII" -> 8;
			case "IX" -> 9;
			case "X" -> 10;
			case "XI" -> 11;
			case "XII" -> 12;
			default -> {
				try {
					yield Integer.parseInt(token);
				} catch (NumberFormatException ignored) {
					yield 0;
				}
			}
		};
	}

	private static void ensureLoaded() {
		if (maxByName != null) {
			return;
		}
		Map<String, Integer> maxes = new HashMap<>();
		Map<String, Boolean> ultimates = new HashMap<>();
		try {
			load(maxes, ultimates);
		} finally {
			// Publish only fully built maps (ultimate first: callers gate on maxByName).
			ultimateByName = ultimates;
			maxByName = maxes;
		}
	}

	private static void load(Map<String, Integer> maxByName, Map<String, Boolean> ultimateByName) {
		Minecraft client = Minecraft.getInstance();
		Identifier id = Identifier.fromNamespaceAndPath("betterpv", "data/enchants.json");
		try {
			InputStream in = null;
			if (client != null) {
				Optional<Resource> resource = client.getResourceManager().getResource(id);
				if (resource.isPresent()) {
					in = resource.get().open();
				}
			}
			if (in == null) {
				in = BetterPV.class.getResourceAsStream("/assets/betterpv/data/enchants.json");
			}
			if (in == null) {
				return;
			}
			try (InputStream stream = in;
				InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement rootEl = JsonParser.parseReader(reader);
				if (rootEl == null || !rootEl.isJsonObject()) {
					return;
				}
				JsonObject root = rootEl.getAsJsonObject();
				for (var entry : root.entrySet()) {
					JsonObject obj = entry.getValue() != null && entry.getValue().isJsonObject()
						? entry.getValue().getAsJsonObject() : null;
					if (obj == null) {
						continue;
					}
					int max = obj.has("max") ? obj.get("max").getAsInt() : 0;
					boolean ultimate = obj.has("ultimate") && obj.get("ultimate").getAsBoolean();
					put(maxByName, ultimateByName, entry.getKey(), max, ultimate);
					if (obj.has("lore") && obj.get("lore").isJsonPrimitive()) {
						put(maxByName, ultimateByName, obj.get("lore").getAsString(), max, ultimate);
					}
					if (obj.has("nbt") && obj.get("nbt").isJsonPrimitive()) {
						put(maxByName, ultimateByName, obj.get("nbt").getAsString(), max, ultimate);
					}
				}
			}
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to load enchant max levels", exception);
		}
	}

	private static void put(Map<String, Integer> maxByName, Map<String, Boolean> ultimateByName, String key, int max, boolean ultimate) {
		if (key == null || key.isBlank() || max < 1) {
			return;
		}
		String id = key.toLowerCase(Locale.ROOT).trim();
		maxByName.put(id, max);
		maxByName.put(id.replace('_', ' '), max);
		ultimateByName.put(id, ultimate);
		ultimateByName.put(id.replace('_', ' '), ultimate);
	}
}

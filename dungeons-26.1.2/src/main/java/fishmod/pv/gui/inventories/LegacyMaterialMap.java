/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/** Legacy 1.8 material / damage remaps used when building stacks from NEU or Hypixel defs. */
final class LegacyMaterialMap {
	private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");
	private static final Pattern LEGACY_DATA = Pattern.compile("\\d{1,5}");
	private static final Pattern LEATHER_COLOR = Pattern.compile(
		"color\\s*:\\s*(\\d+)",
		Pattern.CASE_INSENSITIVE
	);
	private static final String[] DYE_COLORS = {
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};
	static final String[] COLOR_NAMES = {
		"LIGHT_BLUE", "LIGHT_GRAY", "WHITE", "ORANGE", "MAGENTA", "YELLOW", "LIME", "PINK",
		"GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
	};
	static final Map<String, Integer> COLOR_DAMAGE = Map.ofEntries(
		Map.entry("WHITE", 0),
		Map.entry("ORANGE", 1),
		Map.entry("MAGENTA", 2),
		Map.entry("LIGHT_BLUE", 3),
		Map.entry("YELLOW", 4),
		Map.entry("LIME", 5),
		Map.entry("PINK", 6),
		Map.entry("GRAY", 7),
		Map.entry("LIGHT_GRAY", 8),
		Map.entry("CYAN", 9),
		Map.entry("PURPLE", 10),
		Map.entry("BLUE", 11),
		Map.entry("BROWN", 12),
		Map.entry("GREEN", 13),
		Map.entry("RED", 14),
		Map.entry("BLACK", 15)
	);

	private LegacyMaterialMap() {
	}

	static boolean isBanner(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return id.equals("banner") || id.endsWith("_banner");
	}

	static boolean isPlayerSkull(String itemId, int damage) {
		if (itemId == null) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		if (id.equals("player_head")) {
			return true;
		}
		return (id.equals("skull") || id.equals("skull_item")) && damage == 3;
	}

	static boolean isLegacySkull(String itemId) {
		if (itemId == null) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return id.equals("skull") || id.equals("skull_item") || id.equals("player_head");
	}

	static Item legacySkullByDamage(int damage) {
		return switch (damage) {
			case 0 -> Items.SKELETON_SKULL;
			case 1 -> Items.WITHER_SKELETON_SKULL;
			case 2 -> Items.ZOMBIE_HEAD;
			case 4 -> Items.CREEPER_HEAD;
			case 5 -> Items.DRAGON_HEAD;
			default -> Items.PLAYER_HEAD;
		};
	}

	static boolean isLeather(Item item) {
		return item == Items.LEATHER_HELMET
			|| item == Items.LEATHER_CHESTPLATE
			|| item == Items.LEATHER_LEGGINGS
			|| item == Items.LEATHER_BOOTS
			|| item == Items.LEATHER_HORSE_ARMOR;
	}

	static Integer extractLeatherColor(String nbt) {
		if (nbt == null || nbt.isBlank()) {
			return null;
		}
		Matcher matcher = LEATHER_COLOR.matcher(nbt);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	static void applyLeatherColor(ItemStack stack, String nbt) {
		Integer leather = extractLeatherColor(nbt);
		if (leather != null && isLeather(stack.getItem())) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(leather));
		}
	}

	static Item resolveItemId(String itemId, int damage) {
		if (itemId == null || itemId.isBlank()) {
			return Items.PAPER;
		}
		String normalized = itemId.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized;
		}
		normalized = remapLegacy(normalized, damage);
		try {
			Identifier id = Identifier.parse(normalized);
			return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
		} catch (Exception exception) {
			return Items.PAPER;
		}
	}

	static String remapLegacy(String id, int damage) {
		return switch (id) {
			case "minecraft:skull" -> "minecraft:player_head";
			case "minecraft:dye", "minecraft:ink_sack" -> dyeByDamage(damage);
			case "minecraft:potato_item" -> "minecraft:potato";
			case "minecraft:carrot_item" -> "minecraft:carrot";
			case "minecraft:melon" -> "minecraft:melon_slice";
			case "minecraft:reeds" -> "minecraft:sugar_cane";
			case "minecraft:nether_stalk" -> "minecraft:nether_wart";
			case "minecraft:double_plant" -> switch (damage) {
				case 0 -> "minecraft:sunflower";
				case 1 -> "minecraft:lilac";
				case 2 -> "minecraft:tall_grass";
				case 3 -> "minecraft:large_fern";
				case 4 -> "minecraft:rose_bush";
				case 5 -> "minecraft:peony";
				default -> "minecraft:sunflower";
			};
			case "minecraft:red_flower" -> damage == 1 ? "minecraft:blue_orchid" : "minecraft:poppy";
			case "minecraft:banner", "minecraft:white_banner" -> colorPrefixed("banner", damage);
			case "minecraft:stained_glass_pane" -> colorPrefixed("stained_glass_pane", damage);
			case "minecraft:stained_glass" -> colorPrefixed("stained_glass", damage);
			case "minecraft:wool" -> colorPrefixed("wool", damage);
			case "minecraft:carpet" -> colorPrefixed("carpet", damage);
			case "minecraft:stained_hardened_clay", "minecraft:stained_terracotta" -> colorPrefixed("terracotta", damage);
			default -> id;
		};
	}

	static String colorPrefixed(String suffix, int damage) {
		int idx = Math.max(0, Math.min(DYE_COLORS.length - 1, damage));
		return "minecraft:" + DYE_COLORS[idx] + "_" + suffix;
	}

	static String dyeByDamage(int damage) {
		return switch (damage) {
			case 1 -> "minecraft:red_dye";
			case 2 -> "minecraft:green_dye";
			case 3 -> "minecraft:cocoa_beans";
			case 4 -> "minecraft:lapis_lazuli";
			case 5 -> "minecraft:purple_dye";
			case 6 -> "minecraft:cyan_dye";
			case 7 -> "minecraft:light_gray_dye";
			case 8 -> "minecraft:gray_dye";
			case 9 -> "minecraft:pink_dye";
			case 10 -> "minecraft:lime_dye";
			case 11 -> "minecraft:yellow_dye";
			case 12 -> "minecraft:light_blue_dye";
			case 13 -> "minecraft:magenta_dye";
			case 14 -> "minecraft:orange_dye";
			case 15 -> "minecraft:bone_meal";
			default -> "minecraft:ink_sac";
		};
	}

	/** Hypixel item def material: legacy Bukkit name plus damage value. */
	record Material(String name, int damage) {
	}

	/**
	 * Parses a Hypixel item def material. Accepts {@code NAME} (damage from {@code durability}),
	 * Bukkit {@code NAME:data} and {@code minecraft:name}. Anything else is not a material:
	 * NEU's {@code TYPE;N} is a pet rarity / item level, not damage. Returns null when unusable.
	 */
	static Material parseMaterial(String raw, int durability) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String name = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (name.startsWith("minecraft:")) {
			name = name.substring("minecraft:".length());
		}
		int damage = durability;
		int colon = name.indexOf(':');
		if (colon >= 0) {
			String data = name.substring(colon + 1);
			if (!LEGACY_DATA.matcher(data).matches()) {
				return null;
			}
			damage = Integer.parseInt(data);
			name = name.substring(0, colon);
		}
		if (!VALID_PATH.matcher(name).matches()) {
			return null;
		}
		return new Material(name.toUpperCase(Locale.ROOT), damage);
	}

	/** Vanilla item lookup that never throws on bad ids. Accepts {@code name} or {@code minecraft:name}. */
	static Optional<Item> vanillaItem(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String path = id.toLowerCase(Locale.ROOT);
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length());
		}
		if (!VALID_PATH.matcher(path).matches()) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("minecraft", path));
	}

	static Item resolveMaterial(String material, String skyblockId, int durability) {
		Material parsed = parseMaterial(material, durability);
		if (parsed != null) {
			String upper = parsed.name();
			int damage = parsed.damage();
			// Legacy coloured blocks used material + durability (meta).
			String remapped = switch (upper) {
				case "STAINED_GLASS_PANE" -> colorPrefixed("stained_glass_pane", damage);
				case "STAINED_GLASS" -> colorPrefixed("stained_glass", damage);
				case "WOOL" -> colorPrefixed("wool", damage);
				case "CARPET" -> colorPrefixed("carpet", damage);
				case "STAINED_CLAY", "STAINED_HARDENED_CLAY", "HARDENED_CLAY" -> colorPrefixed("terracotta", damage);
				case "CONCRETE" -> colorPrefixed("concrete", damage);
				case "CONCRETE_POWDER" -> colorPrefixed("concrete_powder", damage);
				case "INK_SACK", "DYE" -> dyeByDamage(damage);
				default -> null;
			};
			if (remapped != null) {
				Optional<Item> colored = vanillaItem(remapped);
				if (colored.isPresent()) {
					return colored.get();
				}
			}
			Item mapped = legacyMaterial(upper);
			if (mapped != null) {
				return mapped;
			}
			Optional<Item> item = vanillaItem(upper);
			if (item.isPresent()) {
				return item.get();
			}
		}
		// Modern Hypixel ids like RED_STAINED_GLASS_PANE. Drop NEU's ;N / -N suffix first.
		String upperId = NeuItemResolver.canonicalId(skyblockId);
		for (String color : COLOR_NAMES) {
			String prefix = color + "_";
			if (upperId.startsWith(prefix)) {
				Optional<Item> direct = vanillaItem(upperId);
				if (direct.isPresent()) {
					return direct.get();
				}
				break;
			}
		}
		return guessFromId(skyblockId);
	}

	private static Item legacyMaterial(String material) {
		return switch (material.toUpperCase(Locale.ROOT)) {
			case "SKULL_ITEM", "SKULL" -> Items.PLAYER_HEAD;
			case "INK_SACK" -> Items.INK_SAC;
			case "RAW_FISH" -> Items.COD;
			case "COOKED_FISH" -> Items.COOKED_COD;
			case "WATCH" -> Items.CLOCK;
			case "EMPTY_MAP" -> Items.MAP;
			case "BOOK_AND_QUILL" -> Items.WRITABLE_BOOK;
			case "FIREBALL" -> Items.FIRE_CHARGE;
			case "SPECKLED_MELON" -> Items.GLISTERING_MELON_SLICE;
			case "SULPHUR" -> Items.GUNPOWDER;
			case "NETHER_STALK" -> Items.NETHER_WART;
			case "WATER_LILY" -> Items.LILY_PAD;
			case "CARROT_ITEM" -> Items.CARROT;
			case "POTATO_ITEM" -> Items.POTATO;
			case "GRILLED_PORK" -> Items.COOKED_PORKCHOP;
			case "PORK" -> Items.PORKCHOP;
			case "EXP_BOTTLE" -> Items.EXPERIENCE_BOTTLE;
			case "FIREWORK" -> Items.FIREWORK_ROCKET;
			case "RED_ROSE" -> Items.POPPY;
			case "YELLOW_FLOWER" -> Items.DANDELION;
			case "WEB" -> Items.COBWEB;
			case "LEATHER_HELMET" -> Items.LEATHER_HELMET;
			case "LEATHER_CHESTPLATE" -> Items.LEATHER_CHESTPLATE;
			case "LEATHER_LEGGINGS" -> Items.LEATHER_LEGGINGS;
			case "LEATHER_BOOTS" -> Items.LEATHER_BOOTS;
			default -> null;
		};
	}

	static Item guessFromId(String id) {
		if (id == null) {
			return Items.PAPER;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		if (upper.contains("SWORD")) return Items.DIAMOND_SWORD;
		if (upper.contains("BOW")) return Items.BOW;
		if (upper.contains("PICKAXE")) return Items.DIAMOND_PICKAXE;
		if (upper.contains("AXE")) return Items.DIAMOND_AXE;
		if (upper.contains("HOE")) return Items.DIAMOND_HOE;
		if (upper.contains("SHOVEL")) return Items.DIAMOND_SHOVEL;
		if (upper.contains("HELMET") || upper.contains("HOOD")) return Items.DIAMOND_HELMET;
		if (upper.contains("CHESTPLATE") || upper.contains("TUNIC")) return Items.DIAMOND_CHESTPLATE;
		if (upper.contains("LEGGINGS") || upper.contains("PANTS")) return Items.DIAMOND_LEGGINGS;
		if (upper.contains("BOOTS")) return Items.DIAMOND_BOOTS;
		if (upper.contains("ARROW")) return Items.ARROW;
		if (upper.contains("POTION")) return Items.POTION;
		if (upper.contains("FISH") || upper.contains("SEA")) return Items.COD;
		if (upper.contains("SACK")) return Items.BUNDLE;
		return Items.PAPER;
	}
}

package fishmod.features.croesus;

import fishmod.utils.SkyblockItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Croesus reward-chest tooltip (item display name + lore lines, in that order) into
 * SkyBlock item ids, quantities and display names — one {@link RewardItem} per reward line, up to
 * (but not including) the "Cost" line and anything after it.
 * <p>
 * Ported from FishModAddons' {@code fishmodaddons.util.ItemParser} (itself ported from
 * AutoCroesus's ItemParser, originally by UnclaimedBloom6, ported with permission) — same regexes,
 * same enchanted-book/essence/hardcoded-name handling, same cost-line detection. Trimmed for the
 * passive loot tracker: no cost/value/profit math (that's the addon's job for auto-claiming), and
 * display-name → item-id resolution goes through {@link SkyblockItems#idFor} (this mod's own
 * async-loaded name table) instead of the addon's CroesusDataStore price list.
 */
public final class CroesusRewardParser {
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    private static final Set<String> ULTIMATE_ENCHANTS = new HashSet<>(Arrays.asList(
            "Bank", "Bobbin Time", "Chimera", "Combo", "Duplex", "Fatal Tempo", "Flash",
            "Habanero Tactics", "Inferno", "Last Stand", "Legion", "No Pain No Gain", "One For All",
            "Rend", "Soul Eater", "Swarm", "The One", "Ultimate Jerry", "Ultimate Wise", "Wisdom"
    ));
    private static final Map<String, String> ITEM_REPLACEMENTS = new HashMap<>();
    private static final Pattern BOOK_PATTERN = Pattern.compile("Enchanted Book \\((?:§.)*([\\w' ]+?) ((?:[IVX]+|\\d+))(?:§.)*\\)");
    private static final Pattern ESSENCE_PATTERN = Pattern.compile("^(\\w+) Essence x(\\d+)$");
    private static final Map<Character, Integer> ROMAN_VALUES = new HashMap<>();

    private CroesusRewardParser() {}

    public static int decodeRoman(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            int curr = ROMAN_VALUES.getOrDefault(s.charAt(i), 0);
            int next = i < s.length() - 1 ? ROMAN_VALUES.getOrDefault(s.charAt(i + 1), 0) : 0;
            if (curr < next) {
                sum += next - curr;
                i++;
            } else {
                sum += curr;
            }
        }
        return sum;
    }

    private static String strip(String s) { return COLOR_STRIP.matcher(s).replaceAll(""); }

    private static String[] tryParseBook(String line) {
        Matcher m = BOOK_PATTERN.matcher(line);
        if (!m.find()) return null;
        String bookName = strip(m.group(1).trim()).trim();
        String tierStr = m.group(2).trim();
        boolean isUltimate = ULTIMATE_ENCHANTS.contains(bookName);

        int tier;
        try {
            tier = Integer.parseInt(tierStr);
        } catch (NumberFormatException e) {
            tier = decodeRoman(tierStr);
        }

        String enchantPart = bookName.toUpperCase().replace(" ", "_").replace("'", "");
        String sbId = "ENCHANTMENT_" + (isUltimate ? "ULTIMATE_" : "") + enchantPart + "_" + tier;
        sbId = sbId.replace("ULTIMATE_ULTIMATE_", "ULTIMATE_");
        return new String[]{sbId, "1"};
    }

    private static String[] tryParseEssence(String line) {
        Matcher m = ESSENCE_PATTERN.matcher(line);
        return !m.matches() ? null : new String[]{"ESSENCE_" + m.group(1).toUpperCase(), m.group(2)};
    }

    /** @return {id, qty} on success, or {"false", errorMessage} when the line couldn't be resolved. */
    public static String[] parseLine(String line) {
        String[] book = tryParseBook(line);
        if (book != null) return book;

        String clean = strip(line).trim();
        String[] essence = tryParseEssence(clean);
        if (essence != null) return essence;
        if (ITEM_REPLACEMENTS.containsKey(clean)) return new String[]{ITEM_REPLACEMENTS.get(clean), "1"};

        String id = SkyblockItems.idFor(clean);
        if (id != null && !id.startsWith("STARRED_")) return new String[]{id, "1"};

        return new String[]{"false", "Could not find item ID for line \"" + clean + "\""};
    }

    /**
     * @param fullTooltip item display name followed by its lore lines, in render order
     * @param errorOut    optional 1-element out-param; set to a human-readable reason on failure
     * @return parsed rewards (everything before the "Cost" line), or null if the tooltip couldn't
     *         be parsed (no Cost line found yet — e.g. the container hasn't finished loading — or
     *         a reward line's item id couldn't be resolved).
     */
    public static ChestInfo parseRewards(List<String> fullTooltip, String[] errorOut) {
        int costIdx = -1;
        for (int i = 0; i < fullTooltip.size(); i++) {
            if (strip(fullTooltip.get(i)).contains("Cost")) {
                costIdx = i;
                break;
            }
        }
        if (costIdx < 0) {
            if (errorOut != null) errorOut[0] = "Could not find Cost line";
            return null;
        }

        ChestInfo info = new ChestInfo();
        int lootEnd = costIdx - 1;
        for (int ix = 2; ix < lootEnd; ix++) {
            String line = fullTooltip.get(ix);
            String clean = strip(line).trim();
            if (clean.isEmpty()) continue;

            String[] result = parseLine(line);
            if (result[0].equals("false")) {
                if (errorOut != null) errorOut[0] = result[1];
                return null;
            }

            RewardItem ri = new RewardItem();
            ri.id = result[0];
            ri.qty = Integer.parseInt(result[1]);
            ri.displayName = line.replaceAll("^§5§o", "").trim();
            info.items.add(ri);
        }
        return info;
    }

    static {
        ITEM_REPLACEMENTS.put("Shiny Wither Boots", "WITHER_BOOTS");
        ITEM_REPLACEMENTS.put("Shiny Wither Leggings", "WITHER_LEGGINGS");
        ITEM_REPLACEMENTS.put("Shiny Wither Chestplate", "WITHER_CHESTPLATE");
        ITEM_REPLACEMENTS.put("Shiny Wither Helmet", "WITHER_HELMET");
        ITEM_REPLACEMENTS.put("Shiny Necron's Handle", "NECRON_HANDLE");
        ITEM_REPLACEMENTS.put("Wither Shard", "SHARD_WITHER");
        ITEM_REPLACEMENTS.put("Thorn Shard", "SHARD_THORN");
        ITEM_REPLACEMENTS.put("Apex Dragon Shard", "SHARD_APEX_DRAGON");
        ITEM_REPLACEMENTS.put("Power Dragon Shard", "SHARD_POWER_DRAGON");
        ITEM_REPLACEMENTS.put("Scarf Shard", "SHARD_SCARF");
        ITEM_REPLACEMENTS.put("Necron Dye", "DYE_NECRON");
        ITEM_REPLACEMENTS.put("Livid Dye", "DYE_LIVID");
        ROMAN_VALUES.put('I', 1);
        ROMAN_VALUES.put('V', 5);
        ROMAN_VALUES.put('X', 10);
        ROMAN_VALUES.put('L', 50);
        ROMAN_VALUES.put('C', 100);
        ROMAN_VALUES.put('D', 500);
        ROMAN_VALUES.put('M', 1000);
    }

    public static class ChestInfo {
        public List<RewardItem> items = new ArrayList<>();
    }

    public static class RewardItem {
        public String id;
        public int qty;
        public String displayName;
    }
}

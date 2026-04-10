package blade.addon.features.other;

import blade.addon.utils.HypixelApi;
import blade.addon.utils.Misc;
import blade.addon.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartyFinderEnhancer {

    private static final Pattern MEMBER_PATTERN = Pattern.compile(
            "([A-Za-z0-9_]{1,16}): (Berserk|Mage|Healer|Archer|Tank) (?:\\(\\d+\\)|\\d+)"
    );
    private static final Pattern FLOOR_PATTERN = Pattern.compile(
            "Floor:\\s*(?:Floor\\s+)?(\\w+)"
    );

    public static final Map<String, HypixelApi.DungeonData> cache   = new ConcurrentHashMap<>();
    public static final Map<String, Boolean>                pending = new ConcurrentHashMap<>();

    // Snapshot updated every tick while in Party Finder — readable after closing
    private static final List<String> lastDebugLines = new ArrayList<>();
    private static String lastTitle = "none";

    public static void init() {
        HypixelApi.loadPfCache(cache);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.partyFinderStats) return;
            if (!(client.currentScreen instanceof HandledScreen<?>)) return;
            HandledScreen<?> screen = (HandledScreen<?>) client.currentScreen;
            String title = screen.getTitle().getString().replaceAll("§.", "").trim();
            if (!title.contains("Party Finder")) return;

            // Update debug snapshot every tick
            lastTitle = title;
            lastDebugLines.clear();
            lastDebugLines.add("title: \"" + title + "\" | isMatch: " + title.contains("Party Finder"));
            lastDebugLines.add("cache: " + cache.size() + " | pending: " + pending.keySet());

            int size = screen.getScreenHandler().slots.size() - 36;
            lastDebugLines.add("slots (excl inv): " + size);
            for (int i = 0; i < size; i++) {
                ItemStack stack = screen.getScreenHandler().slots.get(i).getStack();
                if (stack.isEmpty() || !stack.isOf(Items.PLAYER_HEAD)) continue;
                List<Text> dbgLines = getLoreLines(client, stack);
                lastDebugLines.add("[" + i + "] " + stack.getName().getString().replaceAll("§.", "") +
                        " lore=" + dbgLines.size() + " comp=" + (stack.get(DataComponentTypes.LORE) != null));
                for (Text l : dbgLines) {
                    String raw = l.getString();
                    String p   = raw.replaceAll("§.", "").trim();
                    if (p.isEmpty()) {
                        // dump raw bytes to diagnose styled Text content
                        lastDebugLines.add("  [empty raw=" + raw.length() + " chars, sample=" +
                            (raw.length() > 0 ? (int)raw.charAt(0) : -1) + "]");
                        continue;
                    }
                    lastDebugLines.add("  [" + (MEMBER_PATTERN.matcher(p).find() ? "HIT" : "miss") + "] " + p);
                }
            }
            lastDebugLines.add("fetched: " + cache.keySet());
            for (Map.Entry<String, HypixelApi.DungeonData> e : cache.entrySet()) {
                HypixelApi.DungeonData d = e.getValue();
                String pbSample = d.masterPbs[7] != null ? d.masterPbs[7] : (d.cataPbs[7] != null ? d.cataPbs[7] : "null");
                lastDebugLines.add("  " + e.getKey() + ": cata=" + d.cataLevel + " secrets=" + d.totalSecrets + " runs=" + d.totalRuns + " avg=" + d.secretAverage + " m7pb=" + pbSample);
            }

            for (int i = 0; i < size; i++) {
                ItemStack stack = screen.getScreenHandler().slots.get(i).getStack();
                if (stack.isEmpty() || !stack.isOf(Items.PLAYER_HEAD)) continue;

                // Tooltip: SkyHanni-processed, readable getString() — used for player detection only
                List<Text> tooltipLines = getLoreLines(client, stack);
                if (tooltipLines.isEmpty()) continue;

                // Raw lore: what's actually stored — we write stats here so SkyHanni
                // re-applies its own overlay cleanly on top every tick
                LoreComponent rawComp = stack.get(DataComponentTypes.LORE);
                if (rawComp == null) continue;
                List<Text> rawLines = new ArrayList<>(rawComp.lines());
                // SkyHanni may add extra lines (e.g. "Missing: ...") at the end of the tooltip.
                // Raw lore only has the original Hypixel lines — process only those indices.
                int matchSize = Math.min(tooltipLines.size(), rawLines.size());

                boolean hasMember = tooltipLines.stream().anyMatch(l ->
                        MEMBER_PATTERN.matcher(l.getString().replaceAll("§.", "").trim()).find());
                if (!hasMember) continue;

                // Start fetches for unknown players
                for (Text line : tooltipLines) {
                    String plain = line.getString().replaceAll("§.", "").trim();
                    Matcher m = MEMBER_PATTERN.matcher(plain);
                    if (!m.find()) continue;
                    String player = m.group(1);
                    if (player.equalsIgnoreCase("Empty")) continue;
                    if (!cache.containsKey(player) && pending.putIfAbsent(player, true) == null)
                        HypixelApi.getByNameSilent(player, d -> {
                            cache.put(player, d);
                            HypixelApi.dataTimestamp.put(player, System.currentTimeMillis());
                            pending.remove(player);
                            HypixelApi.savePfCacheAsync(cache);
                        });
                }

                // Detect floor and dungeon type
                int detectedFloor = 7;
                boolean isMaster = false;
                for (Text line : tooltipLines) {
                    String p = line.getString().replaceAll("§.", "").trim();
                    if (p.contains("Master Mode")) isMaster = true;
                    Matcher fm = FLOOR_PATTERN.matcher(p);
                    if (fm.find()) {
                        int f = decodeFloor(fm.group(1));
                        if (f >= 0) detectedFloor = f;
                    }
                }

                // Patch raw lore lines (not tooltip lines) so SkyHanni's styled Text overlay
                // is never baked in or doubled. We use the tooltip for readable player names
                // (index-aligned since SkyHanni only appends lines at the end), then append
                // our plain-text stats onto the corresponding raw lore line.
                // Already-patched check: getString() on a composite Text returns concatenated
                // children — our appended plain text is visible even when SkyHanni's part isn't.
                boolean anyPatched = false;
                for (int j = 0; j < matchSize; j++) {
                    String plain = tooltipLines.get(j).getString().replaceAll("§.", "").trim();
                    Matcher m = MEMBER_PATTERN.matcher(plain);
                    if (!m.find() || m.group(1).equalsIgnoreCase("Empty")) continue;
                    String player = m.group(1);

                    // Already patched — our stats are already on the raw line
                    if (rawLines.get(j).getString().contains("§8[")) continue;

                    if (pending.containsKey(player)) {
                        lastDebugLines.add("  [skip-pending] " + player);
                        continue;
                    }

                    HypixelApi.DungeonData data = cache.get(player);
                    if (data == null) {
                        lastDebugLines.add("  [skip-nodata] " + player);
                        continue;
                    }

                    StringBuilder sb = new StringBuilder();
                    if (data.totalSecrets > 0 && data.secretAverage != null)
                        sb.append(" §8[§a").append(data.totalSecrets).append("§8/§b").append(data.secretAverage).append("§8]§r");
                    String pb = isMaster ? data.masterPbs[detectedFloor] : data.cataPbs[detectedFloor];
                    if (pb != null)
                        sb.append(" §8[§9").append(pb).append("§8]§r");
                    if (sb.isEmpty()) {
                        lastDebugLines.add("  [skip-emptyStats] " + player + " cata=" + data.cataLevel + " sec=" + data.totalSecrets + " avg=" + data.secretAverage + " pb=" + pb);
                        continue;
                    }

                    lastDebugLines.add("  [patch] " + player + " master=" + isMaster + " floor=" + detectedFloor);
                    rawLines.set(j, rawLines.get(j).copy().append(Text.literal(sb.toString())));
                    anyPatched = true;
                }

                if (anyPatched) {
                    stack.set(DataComponentTypes.LORE, new LoreComponent(rawLines));
                }
            }
        });
    }

    private static int decodeFloor(String token) {
        switch (token.toUpperCase()) {
            case "ENTRANCE": return 0;
            case "I":        return 1;
            case "II":       return 2;
            case "III":      return 3;
            case "IV":       return 4;
            case "V":        return 5;
            case "VI":       return 6;
            case "VII":      return 7;
            default: try { return Integer.parseInt(token); } catch (NumberFormatException e) { return -1; }
        }
    }

    /** Gets lore lines — tooltip first (handles SkyHanni/ViaVersion styled Text), then raw LORE component. */
    private static List<Text> getLoreLines(MinecraftClient mc, ItemStack stack) {
        // Tooltip processes the full rendering pipeline — styled Text children are included properly
        try {
            List<Text> tooltip = stack.getTooltip(
                    Item.TooltipContext.create(mc.world),
                    mc.player,
                    TooltipType.BASIC
            );
            if (tooltip.size() > 1) return tooltip.subList(1, tooltip.size());
        } catch (Exception ignored) {}

        // Fallback: raw LORE component
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) return lore.lines();
        return List.of();
    }

    /** /pfdbg — prints snapshot from the last time Party Finder was open */
    public static void debug(MinecraftClient mc) {
        Misc.addChatMessage(Text.literal("§b--- PF Debug (last seen: " + lastTitle + ") ---"));
        if (lastDebugLines.isEmpty()) {
            Misc.addChatMessage(Text.literal("§cNo Party Finder data captured yet. Open Party Finder first."));
            return;
        }
        for (String line : lastDebugLines)
            Misc.addChatMessage(Text.literal("§7" + line));
        Misc.addChatMessage(Text.literal("§b--- End ---"));
    }
}

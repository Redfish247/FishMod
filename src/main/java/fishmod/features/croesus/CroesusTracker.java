package fishmod.features.croesus;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Croesus reward chests, snapshots their contents while open, and
 * commits them to {@link CroesusStore} when the player claims a chest.
 *
 * Detection is intentionally lenient: any chest UI titled with a Catacombs
 * floor identifier is treated as a potential reward chest. The "Open Reward
 * Chest" item (usually slot 31) provides chest cost + chest type via its
 * lore; the loot itself sits in the reward row (slots 11..15).
 */
public class CroesusTracker {

    private static final Pattern FLOOR_TITLE = Pattern.compile(
            "(?i)(?:Master Mode )?Catacombs(?:\\s*-\\s*)?(?:Floor\\s*(\\w+)|(F[0-7]|M[1-7]|E))?");
    private static final Pattern FLOOR_LORE  = Pattern.compile("(?i)Floor:?\\s*(F[0-7]|M[1-7]|Entrance|E)");
    private static final Pattern RUN_AGO     = Pattern.compile(
            "(?i)Run Completed:?\\s*(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?\\s*ago");
    private static final Pattern COST_LORE   = Pattern.compile("(?i)Cost:?\\s*([\\d,]+)\\s*coins?");
    private static final Pattern CLAIM_CHAT  = Pattern.compile(
            "(?i)^\\s*(WOOD|GOLD|DIAMOND|EMERALD|OBSIDIAN|BEDROCK)\\s+CHEST\\s+REWARDS\\s*$");
    // The run's chest-SELECTION grid lists the six chest tiers as items; they are never loot. Used to
    // belt-and-suspenders skip them in case a tier item ever slips into the loot row.
    private static final Pattern CHEST_TIER_ITEM = Pattern.compile(
            "(?i)^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?:\\s+Chest)?$");

    // Reward slots on the 5-row chest UI (rows 1-3 middle). Hypixel currently
    // places loot in row 2 (slots 9-17); we conservatively scan that whole row.
    private static final int LOOT_SLOT_MIN = 9;
    private static final int LOOT_SLOT_MAX = 17;
    private static final int OPEN_CHEST_SLOT = 31;

    private static Screen lastScreen = null;
    private static Snapshot pendingClaim = null;

    public static void init() {
        CroesusPrices.refreshIfStale(); // warm cache so first claim has prices
        int purged = CroesusStore.purgeChestTierEntries(); // clean up bogus chest-tier "drops"
        if (purged > 0) fishmod.utils.debug.Debug.LOGGER.info("[Croesus] purged {} bogus chest-tier entries", purged);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        Events.ON_GAME_MESSAGE.register(text -> {
            String plain = text.getString().replaceAll("§.", "");
            if (CLAIM_CHAT.matcher(plain).find()) {
                commitPending();
            }
            return false;
        });
    }

    private static void tick(MinecraftClient mc) {
        Screen scr = mc.currentScreen;
        if (scr == lastScreen) return;
        lastScreen = scr;
        if (!(scr instanceof HandledScreen<?> hs)) return;
        ScreenHandler handler = hs.getScreenHandler();
        if (!(handler instanceof GenericContainerScreenHandler)) return;
        String title = scr.getTitle().getString().replaceAll("§.", "").trim();
        if (!looksLikeRewardChest(handler)) return;
        snapshot(title, handler);
    }

    private static boolean looksLikeRewardChest(ScreenHandler handler) {
        // Require the "Open Reward Chest" button (slot 31). That button only exists inside a single
        // chest's reward preview — it is what separates a real reward chest from the run's chest
        // SELECTION grid (the Wood/Gold/.../Bedrock list), which is ALSO titled with the floor.
        // Without this guard we snapshot the grid, record the chest tiers as "loot", and that bogus
        // snapshot then commits in place of the actual drops when the chest is claimed.
        ItemStack openSlot = slotStack(handler, OPEN_CHEST_SLOT);
        if (openSlot == null || openSlot.isEmpty()) return false;
        return openSlot.getName().getString().toLowerCase().contains("reward chest");
    }

    private static void snapshot(String title, ScreenHandler handler) {
        Snapshot s = new Snapshot();
        s.title = title;

        // Try parsing floor from title first.
        Matcher tm = FLOOR_TITLE.matcher(title);
        if (tm.find() && tm.group(2) != null) s.floor = tm.group(2).toUpperCase();

        ItemStack openSlot = slotStack(handler, OPEN_CHEST_SLOT);
        if (openSlot != null && !openSlot.isEmpty()) {
            s.chestType = openSlot.getName().getString().replaceAll("§.", "").trim();
            for (String line : lore(openSlot)) {
                if (s.floor == null) {
                    Matcher fm = FLOOR_LORE.matcher(line);
                    if (fm.find()) s.floor = fm.group(1).toUpperCase().replace("ENTRANCE", "E");
                }
                Matcher rm = RUN_AGO.matcher(line);
                if (rm.find() && s.runCompletedAgoSec < 0) {
                    long sec = 0;
                    if (rm.group(1) != null) sec += Long.parseLong(rm.group(1)) * 86400;
                    if (rm.group(2) != null) sec += Long.parseLong(rm.group(2)) * 3600;
                    if (rm.group(3) != null) sec += Long.parseLong(rm.group(3)) * 60;
                    if (rm.group(4) != null) sec += Long.parseLong(rm.group(4));
                    s.runCompletedAgoSec = sec;
                }
                Matcher cm = COST_LORE.matcher(line);
                if (cm.find() && s.claimCost == 0) {
                    try { s.claimCost = Long.parseLong(cm.group(1).replace(",", "")); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        for (int i = LOOT_SLOT_MIN; i <= LOOT_SLOT_MAX; i++) {
            ItemStack st = slotStack(handler, i);
            if (st == null || st.isEmpty()) continue;
            // Skip filler glass panes and clearly non-loot decoratives.
            String regId = net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString();
            if (regId.contains("stained_glass_pane") || regId.contains("barrier")) continue;

            CroesusStore.Item it = new CroesusStore.Item();
            it.id = nullToEmpty(CroesusItemId.resolve(st));
            String displayName = CroesusItemId.displayName(st);
            // Hypixel encodes essence-style counts in the display name as
            // "Wither Essence x99" — strip that and use it as the real count.
            Matcher countM = Pattern.compile("(?i)\\s*x\\s*(\\d+)\\s*$").matcher(displayName);
            int nameCount = -1;
            if (countM.find()) {
                try { nameCount = Integer.parseInt(countM.group(1)); } catch (NumberFormatException ignored) {}
                displayName = displayName.substring(0, countM.start()).trim();
            }
            // Never record a chest-selection tile (Wood/Gold/.../Bedrock) as loot.
            if (CHEST_TIER_ITEM.matcher(displayName).matches()) continue;
            it.name = displayName;
            it.count = nameCount > 0 ? nameCount : st.getCount();
            s.items.add(it);
        }

        if (s.items.isEmpty()) return; // nothing to record
        pendingClaim = s;
        // Warm the price cache so commit is fast.
        CroesusPrices.refreshIfStale();
    }

    private static synchronized void commitPending() {
        if (pendingClaim == null) return;
        Snapshot s = pendingClaim;
        pendingClaim = null;

        // Show immediately with whatever's in the price cache; refresh in
        // background so subsequent claims have accurate prices.
        CroesusPrices.refreshIfStale();
        CroesusStore.Entry e = new CroesusStore.Entry();
        e.timestamp = System.currentTimeMillis();
        e.floor = s.floor == null ? "?" : s.floor;
        e.chestType = s.chestType;
        e.runCompletedAgoSec = s.runCompletedAgoSec;
        e.claimCost = s.claimCost;
        for (CroesusStore.Item it : s.items) {
            CroesusStore.Item out = new CroesusStore.Item();
            out.id = it.id;
            out.name = it.name;
            out.count = it.count;
            out.priceAtClaim = CroesusPrices.price(it.id);
            e.items.add(out);
        }
        CroesusStore.add(e);
        CroesusOverlay.show(e);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static ItemStack slotStack(ScreenHandler handler, int idx) {
        try {
            Slot slot = handler.slots.get(idx);
            return slot.getStack();
        } catch (Exception e) { return null; }
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        LoreComponent lc = stack.get(DataComponentTypes.LORE);
        if (lc == null) return out;
        for (Text line : lc.lines()) out.add(line.getString().replaceAll("§.", ""));
        return out;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static class Snapshot {
        String title = "";
        String floor = null;
        String chestType = "";
        long runCompletedAgoSec = -1;
        long claimCost = 0;
        List<CroesusStore.Item> items = new ArrayList<>();
    }
}

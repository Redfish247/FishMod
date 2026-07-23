package fishmod.features.croesus;

import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passively auto-populates {@link LootTrackerStore} from real Croesus chest claims — no typing
 * required, and it works the same whether the chest was opened by hand or by another mod automating
 * the clicks (this is pure passive tooltip-reading triggered by screens opening).
 * <p>
 * Two-phase, mirroring how {@code fishmodaddons.croesus.CroesusClaimer} actually reads this UI:
 * <ol>
 *   <li>Hypixel's run-selection GUI (title matching {@link #RUN_GUI_PATTERN}, e.g.
 *       "Catacombs - Floor 7") shows up to 6 chest-tier icons (items literally named
 *       "Wood"/"Gold"/.../"Bedrock"), each with a lore tooltip listing every reward followed by a
 *       "Cost" line — a <em>preview</em> of what each tier currently contains. All 6 are visible at
 *       once, before the player has chosen one, so these get cached by chest-type name rather than
 *       logged immediately (logging all 6 would count loot the player never actually claimed).</li>
 *   <li>Once the player clicks a tier, a confirmation screen opens whose <em>title</em> alone
 *       (color-stripped) is that tier's name (matching {@link #CHEST_SCREEN_PATTERN}) — that title
 *       is enough to know which cached preview to log and clear; its own contents aren't read.</li>
 * </ol>
 * Also auto-increments {@link LootTrackerStore#runs()}, once per run-selection GUI visit (not once
 * per chest — a Chest Key can open a second chest on the same run): armed fresh every time that GUI
 * transitions from closed to open, and the actual bump happens on the first reward logged during
 * that visit (rather than on the GUI closing, which fires before the chosen chest's screen opens).
 */
public final class CroesusLootDetector {
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");
    private static final Pattern CHEST_SCREEN_PATTERN = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$");
    private static final Pattern CHEST_ITEM_PATTERN = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)$");
    private static final Pattern RUN_GUI_PATTERN = Pattern.compile("^(?:Master )?Catacombs - .+$");

    /** Chest-tier name (e.g. "Wood") -> its parsed preview, cached while the run-selection GUI is open. */
    private static final Map<String, CroesusRewardParser.ChestInfo> pendingChests = new HashMap<>();

    private static boolean runGuiOpenPrev = false;
    private static boolean loggedThisVisit = false;

    private CroesusLootDetector() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CroesusLootDetector::onTick);
    }

    /** Call once from {@code ScreenEvents.AFTER_INIT} for every newly opened container screen. */
    public static void onScreenInit(Screen screen) {
        if (screen == null || !FishSettings.lootTrackerEnabled) return;
        String title = strip(screen.getTitle().getString());

        if (RUN_GUI_PATTERN.matcher(title).matches()) {
            // Contents may not have arrived the instant the screen is constructed (Hypixel sends
            // them in a follow-up packet) — retry every render frame; scanRunGuiPreviews() itself
            // skips chest types already cached, so this is cheap and self-dedupes.
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> scanRunGuiPreviews());
            return;
        }

        Matcher m = CHEST_SCREEN_PATTERN.matcher(title);
        if (m.matches()) logPending(m.group(1));
    }

    private static void scanRunGuiPreviews() {
        if (!FishSettings.lootTrackerEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) return;

        int slotCount = Math.min(27, menu.slots.size());
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = getSlot(menu, i);
            if (stack.isEmpty()) continue;
            String name = strip(stack.getHoverName().getString());
            if (!CHEST_ITEM_PATTERN.matcher(name).matches()) continue;
            if (pendingChests.containsKey(name)) continue; // already cached this visit

            CroesusRewardParser.ChestInfo info = CroesusRewardParser.parseRewards(getTooltip(stack), new String[]{null});
            if (info != null) pendingChests.put(name, info);
        }
    }

    private static void logPending(String chestName) {
        CroesusRewardParser.ChestInfo info = pendingChests.remove(chestName);
        if (info == null) return; // no cached preview for this tier (e.g. detection hadn't caught up) — nothing to log

        for (CroesusRewardParser.RewardItem ri : info.items) {
            LootTrackerStore.addOrIncrement(ri.displayName, ri.id, ri.qty);
        }
        if (!loggedThisVisit) {
            loggedThisVisit = true;
            LootTrackerStore.setRuns(LootTrackerStore.runs() + 1);
        }
    }

    private static void onTick(Minecraft mc) {
        String title = mc.screen == null ? "" : strip(mc.screen.getTitle().getString());
        boolean runGuiOpenNow = RUN_GUI_PATTERN.matcher(title).matches();
        if (runGuiOpenNow && !runGuiOpenPrev) {
            // Fresh visit to the run-selection GUI — stale previews from a previous visit (or a
            // previous floor) must not leak into this one, and the once-per-visit runs++ rearms.
            pendingChests.clear();
            loggedThisVisit = false;
        }
        runGuiOpenPrev = runGuiOpenNow;
    }

    private static String strip(String s) { return COLOR_STRIP.matcher(s).replaceAll(""); }

    private static ItemStack getSlot(AbstractContainerMenu menu, int index) {
        return index >= 0 && index < menu.slots.size() ? menu.slots.get(index).getItem() : ItemStack.EMPTY;
    }

    private static List<String> getTooltip(ItemStack stack) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(stack.getHoverName().getString());
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component c : lore.lines()) tooltip.add(c.getString());
        }
        return tooltip;
    }
}

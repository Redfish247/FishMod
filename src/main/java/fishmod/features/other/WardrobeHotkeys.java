package fishmod.features.other;

import fishmod.utils.Keybinds;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Wardrobe/Loadouts quick-swap: pressing FishMod's slot-N hotkey (keyboard or mouse button,
 * whatever it's bound to in Controls) clicks the matching slot in the currently open Wardrobe
 * (Armor Sets) or Loadouts GUI.
 *
 * Loadouts uses a fixed slot layout (verified in-game): 3 columns x 4 rows of "select this
 * loadout" icons at raw slot indices 14/15/16, 23/24/25, 32/33/34, 41/42/43 — hardcoded below.
 *
 * Wardrobe's clickable "select this set" icon moves depending on how many sets are on the page,
 * so instead of a fixed index it's found each time by scanning the hotkey's column for
 * Hypixel's wool/dye/barrier icon.
 *
 * The actual click is deferred by one client tick after the key/click event, since firing it
 * synchronously in the same tick as the input event was causing visual glitches in Hypixel's GUI.
 */
public class WardrobeHotkeys {

    private static final int PLAYER_INV_SLOTS = 36;

    /** Raw slot index for Loadout hotkeys 1-12, in the same row-major order as Keybinds.wardrobeSlots. */
    private static final int[] LOADOUT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    private static Runnable pendingClick;
    private static int pendingTicks;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingClick == null) return;
            if (pendingTicks > 0) {
                pendingTicks--;
                return;
            }
            Runnable action = pendingClick;
            pendingClick = null;
            action.run();
        });
    }

    public static boolean keyPressed(KeyEvent input, AbstractContainerScreen<?> screen) {
        return tryActivate(screen, mapping -> mapping.matches(input));
    }

    public static boolean mouseClicked(MouseButtonEvent click, AbstractContainerScreen<?> screen) {
        return tryActivate(screen, mapping -> mapping.matchesMouse(click));
    }

    private static boolean tryActivate(AbstractContainerScreen<?> screen, java.util.function.Predicate<KeyMapping> matches) {
        if (!FishSettings.wardrobeHotkeysEnabled) return false;

        String title = screen.getTitle().getString().replaceAll("§.", "").trim();
        boolean isWardrobe = title.contains("Armor Sets") || title.equals("Wardrobe");
        boolean isLoadout = title.contains("Loadouts");
        if (!isWardrobe && !isLoadout) return false;

        AbstractContainerMenu handler = screen.getMenu();
        int containerSize = handler.slots.size() - PLAYER_INV_SLOTS;
        // Sanity check: this must actually be a chest-style GUI, not some other screen
        // that happens to share a title substring.
        if (containerSize < 27 || containerSize % 9 != 0) return false;

        for (int i = 0; i < Keybinds.wardrobeSlots.length; i++) {
            KeyMapping mapping = Keybinds.wardrobeSlots[i];
            if (mapping == null || mapping.isUnbound() || !matches.test(mapping)) continue;

            Slot target = resolveTarget(handler, containerSize, isWardrobe, i);
            if (target == null) return false;

            int containerId = handler.containerId;
            int stateId = handler.getStateId();
            int slotId = target.index;
            pendingClick = () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.gameMode == null) return;
                mc.gameMode.handleContainerInput(containerId, stateId, slotId, ContainerInput.PICKUP, mc.player);
                // screen.onClose() (not player.closeContainer()) — matches what pressing Escape
                // does: sends the close packet AND actually dismisses the on-screen GUI. Calling
                // just closeContainer() left the GUI widget on screen out of sync with the
                // now-reset player.containerMenu, which showed up as a close/reopen/close
                // flicker once the server's own state caught up.
                if (FishSettings.wardrobeHotkeysAutoClose && mc.screen == screen) {
                    screen.onClose();
                }
            };
            pendingTicks = 1;
            return true;
        }

        return false;
    }

    private static Slot resolveTarget(AbstractContainerMenu handler, int containerSize, boolean isWardrobe, int hotkeyIndex) {
        if (isWardrobe) {
            return findSelectSlot(handler, containerSize, hotkeyIndex);
        }
        if (hotkeyIndex >= LOADOUT_SLOTS.length) return null;
        int slotIndex = LOADOUT_SLOTS[hotkeyIndex];
        return slotIndex < handler.slots.size() ? handler.slots.get(slotIndex) : null;
    }

    /** Scans column {@code column} (of a 9-wide grid) for Hypixel's wool/dye/barrier "select" icon. */
    private static Slot findSelectSlot(AbstractContainerMenu handler, int containerSize, int column) {
        if (column >= 9) return null;
        for (int i = column; i < containerSize; i += 9) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String id = vanillaId(stack);
            if (id == null) continue;

            if (id.endsWith("_wool") || id.endsWith("_dye") || id.endsWith("_stained_glass_pane") || id.equals("minecraft:barrier")) {
                return slot;
            }
        }
        return null;
    }

    private static String vanillaId(ItemStack stack) {
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Exception e) {
            return null;
        }
    }
}

package fishmod.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Keybinds {

    /** Shared keybind category for all FishMod binds — reused by features that register their own keys. */
    public static KeyMapping.Category category;

    private static KeyMapping openConfig;
    private static KeyMapping trades;
    private static KeyMapping potions;

    /** Wardrobe/Loadouts quick-swap hotkeys 1-12, row-major (matches WardrobeHotkeys' slot layout). */
    public static KeyMapping[] wardrobeSlots;

    /** Wardrobe/Loadouts pagination — clicks whichever arrow icon reads "Next Page"/"Previous Page". */
    public static KeyMapping wardrobeNextPage;
    public static KeyMapping wardrobePrevPage;

    /** Hold-key for Slot Binds: hold + click a hotbar slot then an inventory slot to link them. */
    public static KeyMapping slotBind;

    /** Cycles Slot Binds to the next saved profile. */
    public static KeyMapping slotBindCycleProfile;

    /** Dungeon class ability: ult = tap-drop (one item), mini ult = ctrl-drop (whole stack). */
    public static KeyMapping dungeonAbility;
    public static KeyMapping dungeonAbilityMini;

    /** Toggles the Chat Search field on the open chat screen (unbound by default). */
    public static KeyMapping chatSearchToggle;

    /** Hold to force the chat HUD fully opaque + scrollable, without opening the real chat screen (unbound by default). */
    public static KeyMapping chatPeek;

    /** Backs up bound keys to our own config file so a keybind isn't silently lost when options.txt comes back empty/regenerated. */
    private static final Path KEYBIND_BACKUP_FILE = Paths.get(fishmod.utils.config.FolderUtility.CONFIG_PATH + "keybinds.txt");
    private static final Map<String, KeyMapping> TRACKED = new LinkedHashMap<>();
    private static final Map<String, String> lastKnown = new LinkedHashMap<>();

    /** Held-key + master-toggle gate for the chat peek feature; shared by the render and scroll mixins. */
    public static boolean chatPeekActive() {
        return chatPeek != null && chatPeek.isDown()
                && fishmod.utils.config.values.FishSettings.chatFeatureEnabled
                && fishmod.utils.config.values.FishSettings.chatPeek;
    }

    /** Lazily creates the shared category exactly once, since multiple classes (e.g. DungeonWaypoints) need it before init order guarantees this ran. */
    public static synchronized KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.parse(Constants.NAMESPACE + ":keys"));
        }
        return category;
    }

    public static void init() {

        category();

        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Open Config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
        TRACKED.put("open_config", openConfig);

        trades = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Trades Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("trades", trades);

        potions = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Potion Bag",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("potions", potions);

        wardrobeSlots = new KeyMapping[12];
        for (int i = 0; i < wardrobeSlots.length; i++) {
            wardrobeSlots[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Wardrobe Slot " + (i + 1),
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("wardrobe_slot_" + i, wardrobeSlots[i]);
        }

        wardrobeNextPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Wardrobe Next Page",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("wardrobe_next_page", wardrobeNextPage);

        wardrobePrevPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Wardrobe Previous Page",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("wardrobe_prev_page", wardrobePrevPage);

        slotBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Slot Bind (Hold)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));
        TRACKED.put("slot_bind", slotBind);

        slotBindCycleProfile = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Slot Bind - Cycle Profile",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("slot_bind_cycle_profile", slotBindCycleProfile);

        dungeonAbility = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Dungeon Ability - Ult (Drop)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability", dungeonAbility);

        dungeonAbilityMini = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Dungeon Ability - Mini Ult (Ctrl+Drop)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability_mini", dungeonAbilityMini);

        // read by ChatSearchMixin off the chat screen — consumeClick never fires while a screen is up
        chatSearchToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Toggle Chat Search",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("chat_search_toggle", chatSearchToggle);

        chatPeek = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Chat Peek (Hold to View Chat)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("chat_peek", chatPeek);

        restoreKeybindBackup();

        ClientTickEvents.END_CLIENT_TICK.register(Keybinds::checkInputs);
    }

    /** Reapplies any backed-up key that differs from what vanilla just loaded from options.txt. */
    private static void restoreKeybindBackup() {
        if (!Files.exists(KEYBIND_BACKUP_FILE)) return;
        try {
            boolean changed = false;
            for (String line : Files.readAllLines(KEYBIND_BACKUP_FILE)) {
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) continue;
                String id = parts[0].trim();
                String keyName = parts[1].trim();
                lastKnown.put(id, keyName);

                KeyMapping kb = TRACKED.get(id);
                if (kb == null || keyName.isEmpty()) continue;
                if (!kb.saveString().equals(keyName)) {
                    kb.setKey(InputConstants.getKey(keyName));
                    changed = true;
                }
            }
            if (changed) {
                KeyMapping.resetMapping();
                // options is null this early in startup — rebound keys are already live in memory, options.txt catches up later
                Options options = Minecraft.getInstance().options;
                if (options != null) options.save();
            }
        } catch (IOException ignored) {}
    }

    /** Mirrors any bound-key change (from either screen) into our backup file. Cheap: only writes when something actually changed. */
    private static void syncKeybindBackup() {
        boolean changed = false;
        for (Map.Entry<String, KeyMapping> e : TRACKED.entrySet()) {
            String cur = e.getValue().saveString();
            if (!cur.equals(lastKnown.get(e.getKey()))) {
                lastKnown.put(e.getKey(), cur);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            Files.createDirectories(KEYBIND_BACKUP_FILE.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, KeyMapping> e : TRACKED.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue().saveString()).append('\n');
            }
            Files.writeString(KEYBIND_BACKUP_FILE, sb.toString());
        } catch (IOException ignored) {}
    }

    public static void checkInputs(Minecraft client) {

        syncKeybindBackup();

        if (openConfig.consumeClick()) {
            client.setScreen(new fishmod.features.FishModScreen());
        }

        while (slotBindCycleProfile.consumeClick()) {
            fishmod.features.SlotBinds.cycleProfile();
        }

        if (trades.consumeClick()) {
            Misc.executeCommand("trades");
        }
        if (potions.consumeClick()) {
            Misc.executeCommand("potionbag");
        }
    }
}

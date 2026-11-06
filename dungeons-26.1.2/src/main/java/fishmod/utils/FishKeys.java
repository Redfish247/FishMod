package fishmod.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import fishmod.compat.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class FishKeys {

    public static KeyMapping.Category category;

    private static KeyMapping openConfig;
    private static KeyMapping trades;
    private static KeyMapping potions;

    public static KeyMapping[] wardrobeSlots;

    public static KeyMapping[] petKeybinds;

    public static KeyMapping[] gardenKeys;

    public static KeyMapping wardrobeNextPage;
    public static KeyMapping wardrobePrevPage;

    public static KeyMapping slotBind;

    public static KeyMapping slotLock;

    public static KeyMapping slotBindCycleProfile;

    public static KeyMapping dungeonAbility;
    public static KeyMapping dungeonAbilityMini;

    public static KeyMapping chatSearchToggle;

    public static KeyMapping chatPeek;

    public static KeyMapping zoom;

    private static final Path KEYBIND_BACKUP_FILE = Paths.get(fishmod.utils.config.FishPaths.CONFIG_PATH + "keybinds.txt");
    private static final Map<String, KeyMapping> TRACKED = new LinkedHashMap<>();
    private static final Map<String, String> lastKnown = new LinkedHashMap<>();

    public static boolean chatPeekActive() {
        return chatPeek != null && chatPeek.isDown()
                && fishmod.utils.config.values.FishSettings.chatFeatureEnabled
                && fishmod.utils.config.values.FishSettings.chatPeek;
    }

    public static synchronized KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.parse(FishConstants.NAMESPACE + ":keys"));
        }
        return category;
    }

    public static void init() {

        category();

        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Open Config",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
        TRACKED.put("open_config", openConfig);

        trades = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Trades Menu",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("trades", trades);

        potions = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Potion Bag",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("potions", potions);

        wardrobeSlots = new KeyMapping[12];
        for (int i = 0; i < wardrobeSlots.length; i++) {
            wardrobeSlots[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Wardrobe Slot " + (i + 1),
                    fishmod.compat.GLFW.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("wardrobe_slot_" + i, wardrobeSlots[i]);
        }

        petKeybinds = new KeyMapping[fishmod.features.other.PetKeybinds.COUNT];
        for (int i = 0; i < petKeybinds.length; i++) {
            petKeybinds[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Pet: " + fishmod.features.other.PetKeybinds.PETS[i],
                    fishmod.compat.GLFW.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("pet_key_" + fishmod.features.other.PetKeybinds.PETS[i].toLowerCase().replace(' ', '_'), petKeybinds[i]);
        }

        gardenKeys = new KeyMapping[fishmod.features.garden.GardenKeybinds.NAMES.length];
        for (int i = 0; i < gardenKeys.length; i++) {
            gardenKeys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Garden: " + fishmod.features.garden.GardenKeybinds.NAMES[i],
                    fishmod.compat.GLFW.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("garden_key_" + fishmod.features.garden.GardenKeybinds.NAMES[i].toLowerCase().replace(' ', '_'), gardenKeys[i]);
        }

        wardrobeNextPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Wardrobe Next Page",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("wardrobe_next_page", wardrobeNextPage);

        wardrobePrevPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Wardrobe Previous Page",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("wardrobe_prev_page", wardrobePrevPage);

        slotBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Slot Bind (Hold)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));
        TRACKED.put("slot_bind", slotBind);

        slotLock = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Lock Slot (in inventory)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_L,
                category));
        TRACKED.put("slot_lock", slotLock);

        slotBindCycleProfile = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Slot Bind - Cycle Profile",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("slot_bind_cycle_profile", slotBindCycleProfile);

        dungeonAbility = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Dungeon Ability - Ult (Drop)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability", dungeonAbility);

        dungeonAbilityMini = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Dungeon Ability - Mini Ult (Ctrl+Drop)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability_mini", dungeonAbilityMini);

        chatSearchToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Toggle Chat Search",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("chat_search_toggle", chatSearchToggle);

        chatPeek = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Chat Peek (Hold to View Chat)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("chat_peek", chatPeek);

        zoom = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Zoom (Hold)",
                fishmod.compat.GLFW.KEYSYM,
                GLFW.GLFW_KEY_C,
                category));
        TRACKED.put("zoom", zoom);

        restoreKeybindBackup();

        ClientTickEvents.END_CLIENT_TICK.register(FishKeys::checkInputs);
        // Flush on exit so a rebind/unbind in the last 2s isn't reverted by the stale backup.
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> syncKeybindBackup());
    }

    private static void restoreKeybindBackup() {
        if (!Files.exists(KEYBIND_BACKUP_FILE)) return;
        try {
            boolean changed = false;
            for (String line : Files.readAllLines(KEYBIND_BACKUP_FILE)) {
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) {
                    if (!line.isBlank()) fishmod.utils.debug.FishDiag.fail("FishKeys.1", "malformed keybind backup line: " + line);
                    continue;
                }
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
                Options options = Minecraft.getInstance().options;
                if (options != null) options.save();
            }
        } catch (IOException e) {
            fishmod.utils.debug.FishDiag.fail("FishKeys.2", "reading keybind backup failed", e);
        }
    }

    public static void syncKeybindBackup() {
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
        } catch (IOException e) {
            fishmod.utils.debug.FishDiag.fail("FishKeys.3", "writing keybind backup failed", e);
        }
    }

    private static int syncTick = 0;

    public static void checkInputs(Minecraft client) {

        try {
            if (++syncTick >= 40) {
                syncTick = 0;
                syncKeybindBackup();
            }

            if (openConfig.consumeClick()) {
                fishmod.compat.McCompatKt.showScreen(client, new fishmod.features.FishModScreen());
            }

            while (slotBindCycleProfile.consumeClick()) {
                fishmod.features.SlotBinds.cycleProfile();
            }

            if (trades.consumeClick()) {
                FishUtil.executeCommand("trades");
            }
            if (potions.consumeClick()) {
                FishUtil.executeCommand("potionbag");
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishKeys.4", "keybind input tick failed", t);
        }
    }
}

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

    public static KeyMapping.Category category;

    private static KeyMapping openConfig;
    private static KeyMapping trades;
    private static KeyMapping potions;

    public static KeyMapping[] wardrobeSlots;

    public static KeyMapping[] petKeybinds;

    public static KeyMapping wardrobeNextPage;
    public static KeyMapping wardrobePrevPage;

    public static KeyMapping slotBind;

    public static KeyMapping slotLock;

    public static KeyMapping slotBindCycleProfile;

    public static KeyMapping dungeonAbility;
    public static KeyMapping dungeonAbilityMini;

    public static KeyMapping chatSearchToggle;

    public static KeyMapping chatPeek;

    private static final Path KEYBIND_BACKUP_FILE = Paths.get(fishmod.utils.config.FolderUtility.CONFIG_PATH + "keybinds.txt");
    private static final Map<String, KeyMapping> TRACKED = new LinkedHashMap<>();
    private static final Map<String, String> lastKnown = new LinkedHashMap<>();

    public static boolean chatPeekActive() {
        return chatPeek != null && chatPeek.isDown()
                && fishmod.utils.config.values.FishSettings.chatFeatureEnabled
                && fishmod.utils.config.values.FishSettings.chatPeek;
    }

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

        petKeybinds = new KeyMapping[fishmod.features.other.PetKeybinds.COUNT];
        for (int i = 0; i < petKeybinds.length; i++) {
            petKeybinds[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Pet: " + fishmod.features.other.PetKeybinds.PETS[i],
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("pet_key_" + fishmod.features.other.PetKeybinds.PETS[i].toLowerCase().replace(' ', '_'), petKeybinds[i]);
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

        slotLock = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Lock Slot (in inventory)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category));
        TRACKED.put("slot_lock", slotLock);

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
                Options options = Minecraft.getInstance().options;
                if (options != null) options.save();
            }
        } catch (IOException ignored) {}
    }

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

package fishmod.utils;

import fishmod.features.FishModScreen;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Keybinds {

    /** Shared keybind category for all FishMod binds — reused by features that register their own keys. */
    public static KeyMapping.Category category;

    private static KeyMapping openConfig;
    private static KeyMapping trades;
    private static KeyMapping potions;
    public  static KeyMapping openItemWiki;

    private static KeyMapping getItemLore;
    private static KeyMapping getItemCustomData;
    private static KeyMapping getBlockInfo;

    /** Wardrobe/Loadouts quick-swap hotkeys 1-12, row-major (matches WardrobeHotkeys' slot layout). */
    public static KeyMapping[] wardrobeSlots;

    /** Wardrobe/Loadouts pagination — clicks whichever arrow icon reads "Next Page"/"Previous Page". */
    public static KeyMapping wardrobeNextPage;
    public static KeyMapping wardrobePrevPage;

    /** Hold-key for Slot Binds: hold + click a hotbar slot then an inventory slot to link them. */
    public static KeyMapping slotBind;

    /** Dungeon class ability: ult = tap-drop (one item), mini ult = ctrl-drop (whole stack). */
    public static KeyMapping dungeonAbility;
    public static KeyMapping dungeonAbilityMini;

    /** Opens the read-only Storage Viewer. */
    public static KeyMapping storageViewer;

    /** Backs up bound keys to our own config file so a keybind isn't silently lost when options.txt comes back empty/regenerated. */
    private static final Path KEYBIND_BACKUP_FILE = Paths.get(fishmod.utils.config.FolderUtility.CONFIG_PATH + "keybinds.txt");
    private static final Map<String, KeyMapping> TRACKED = new LinkedHashMap<>();
    private static final Map<String, String> lastKnown = new LinkedHashMap<>();

    /** Lazily creates the shared category exactly once, since multiple classes (e.g. DungeonWaypoints) need it before init order guarantees this ran. */
    public static synchronized KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.parse(Constants.NAMESPACE + ":keys"));
        }
        return category;
    }

    public static void init() {

        category();

        //normal keybinds
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Open Config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
        TRACKED.put("open_config", openConfig);

        trades = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Open trades menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("trades", trades);

        potions = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Open potion bag",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("potions", potions);

        openItemWiki = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Open item wiki",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("open_item_wiki", openItemWiki);

        //debug keybinds
        getItemLore = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Copy item lore",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_item_lore", getItemLore);

        getItemCustomData = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Copy item NBT",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_item_custom_data", getItemCustomData);

        getBlockInfo = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Copy block data",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_block_info", getBlockInfo);

        wardrobeSlots = new KeyMapping[12];
        for (int i = 0; i < wardrobeSlots.length; i++) {
            wardrobeSlots[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "FishMod: Wardrobe slot " + (i + 1),
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("wardrobe_slot_" + i, wardrobeSlots[i]);
        }

        wardrobeNextPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Wardrobe next page",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        wardrobePrevPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Wardrobe previous page",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        slotBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Slot Bind (hold)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));
        TRACKED.put("slot_bind", slotBind);

        dungeonAbility = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Dungeon Ability - Ult (drop)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability", dungeonAbility);

        dungeonAbilityMini = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Dungeon Ability - Mini Ult (ctrl+drop)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("dungeon_ability_mini", dungeonAbilityMini);

        storageViewer = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Open Storage Viewer",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("storage_viewer", storageViewer);

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
                // options is null this early in startup (Minecraft's own constructor hasn't run
                // yet); the rebound keys are already live in memory, options.txt just catches up
                // whenever vanilla next saves on its own.
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

        if (storageViewer.consumeClick()) {
            fishmod.features.storage.StorageViewerScreen.open();
        }

        if (trades.consumeClick()) {
            Misc.executeCommand("trades");
        }
        if (potions.consumeClick()) {
            Misc.executeCommand("potionbag");
        }

        if (getItemLore.consumeClick()) {
            LocalPlayer player = client.player;
            if (player == null) {
                Misc.addChatMessage(Component.literal("player is null"));
                return;
            }

            ItemStack heldStack = player.getMainHandItem();
            ItemLore lore = heldStack.get(DataComponents.LORE);
            if (lore == null) {
                Misc.addChatMessage(Component.literal("lore is null"));
                return;
            }

            List<Component> lines = lore.lines();
            for (Component line : lines) {
                Misc.addChatMessage(line);
            }

            Misc.addChatMessage(Component.literal("(item rarity display removed)"));
        }

        if (getItemCustomData.consumeClick()) {
            LocalPlayer player = client.player;
            if (player == null) {
                Misc.addChatMessage(Component.literal("player is null"));
                return;
            }

            ItemStack heldStack = player.getMainHandItem();
            CustomData nbt = heldStack.get(DataComponents.CUSTOM_DATA);
            if (nbt == null) {
                Misc.addChatMessage(Component.literal("nbt is null"));
                return;
            }

            Misc.addChatMessage(Component.literal(nbt.toString()));

        }

        if (getBlockInfo.consumeClick()) {
            LocalPlayer player = client.player;
            ClientLevel world = client.level;
            if (player == null || world == null) {
                Misc.addChatMessage(Component.literal("player or world is null"));
                return;
            }

            HitResult result = player.pick(4, client.getDeltaTracker().getGameTimeDeltaPartialTick(false), true);

            if (result instanceof BlockHitResult blockHitResult) {
                BlockPos pos = blockHitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                Misc.addChatMessage(Component.literal("Pos: " + pos));
                if (state.hasBlockEntity()) {
                    BlockEntity entity = world.getBlockEntity(pos);
                    Misc.addChatMessage(Component.literal(entity.toString()));

                    if (entity instanceof SkullBlockEntity skullEntity) {
                        ResolvableProfile component = skullEntity.getOwnerProfile();
                        if (component != null) {
                            GameProfile profile = component.partialProfile();
                            Misc.addChatMessage(Component.literal("name: " + profile.name() + " id: " + profile.id()));
                        }
                    }
                }
            }

            //wither essence uuid
            //e0f3e929-869e-3dca-9504-54c666ee6f23
        }
    }
}

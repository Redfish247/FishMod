package fishmod.utils;

import fishmod.features.FishModScreen;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
    public static KeyBinding.Category category;

    private static KeyBinding openConfig;
    private static KeyBinding trades;
    private static KeyBinding potions;
    public  static KeyBinding openItemWiki;

    private static KeyBinding getItemLore;
    private static KeyBinding getItemCustomData;
    private static KeyBinding getBlockInfo;

    /** Wardrobe/Loadouts quick-swap hotkeys 1-12, row-major (matches WardrobeHotkeys' slot layout). */
    public static KeyBinding[] wardrobeSlots;

    /**
     * Backup of every FishMod keybind's bound key, written to our own config file instead of
     * relying solely on vanilla's options.txt. Several users reported their GUI keybind silently
     * reverting to the RIGHT_SHIFT default after updating the mod — options.txt persists keys by
     * the KeyBinding's translation-key string, and anything that causes that lookup to miss on a
     * fresh launch (a regenerated/blank options.txt, a per-version profile folder, etc.) falls back
     * to the hardcoded default with no way to recover the old binding. Mirroring the bound key here
     * lets {@link #init()} restore it even when options.txt comes back empty.
     */
    private static final Path KEYBIND_BACKUP_FILE = Paths.get(fishmod.utils.config.FolderUtility.CONFIG_PATH + "keybinds.txt");
    private static final Map<String, KeyBinding> TRACKED = new LinkedHashMap<>();
    private static final Map<String, String> lastKnown = new LinkedHashMap<>();

    /**
     * Lazily creates the shared category exactly once. {@code DungeonWaypoints.init()} runs
     * before {@code Keybinds.init()} in the mod's init order and needs a category too — if each
     * class created its own {@code KeyBinding.Category} for the same Identifier independently,
     * whichever ran first would claim it and the second would throw "already registered" (this
     * used to happen every launch, leaving {@code category}/{@code wardrobeSlots} permanently
     * null). Everyone must go through this single accessor instead.
     */
    public static synchronized KeyBinding.Category category() {
        if (category == null) {
            category = KeyBinding.Category.create(Identifier.of(Constants.NAMESPACE, "keys"));
        }
        return category;
    }

    public static void init() {

        category();

        //normal keybinds
        openConfig = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Open Config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
        TRACKED.put("open_config", openConfig);

        trades = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Open trades menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("trades", trades);

        potions = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Open potion bag",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("potions", potions);

        openItemWiki = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Open item wiki",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("open_item_wiki", openItemWiki);

        //debug keybinds
        getItemLore = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Copy item lore",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_item_lore", getItemLore);

        getItemCustomData = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Copy item NBT",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_item_custom_data", getItemCustomData);

        getBlockInfo = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Copy block data",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        TRACKED.put("get_block_info", getBlockInfo);

        wardrobeSlots = new KeyBinding[12];
        for (int i = 0; i < wardrobeSlots.length; i++) {
            wardrobeSlots[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "FishMod: Wardrobe slot " + (i + 1),
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category));
            TRACKED.put("wardrobe_slot_" + i, wardrobeSlots[i]);
        }

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

                KeyBinding kb = TRACKED.get(id);
                if (kb == null || keyName.isEmpty()) continue;
                if (!kb.getBoundKeyTranslationKey().equals(keyName)) {
                    kb.setBoundKey(InputUtil.fromTranslationKey(keyName));
                    changed = true;
                }
            }
            if (changed) {
                KeyBinding.updateKeysByCode();
                MinecraftClient.getInstance().options.write();
            }
        } catch (IOException ignored) {}
    }

    /** Mirrors any bound-key change (from either screen) into our backup file. Cheap: only writes when something actually changed. */
    private static void syncKeybindBackup() {
        boolean changed = false;
        for (Map.Entry<String, KeyBinding> e : TRACKED.entrySet()) {
            String cur = e.getValue().getBoundKeyTranslationKey();
            if (!cur.equals(lastKnown.get(e.getKey()))) {
                lastKnown.put(e.getKey(), cur);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            Files.createDirectories(KEYBIND_BACKUP_FILE.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, KeyBinding> e : TRACKED.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue().getBoundKeyTranslationKey()).append('\n');
            }
            Files.writeString(KEYBIND_BACKUP_FILE, sb.toString());
        } catch (IOException ignored) {}
    }

    public static void checkInputs(MinecraftClient client) {

        syncKeybindBackup();

        if (openConfig.wasPressed()) {
            client.setScreen(new fishmod.features.FishModScreen());
        }

        if (trades.wasPressed()) {
            Misc.executeCommand("trades");
        }
        if (potions.wasPressed()) {
            Misc.executeCommand("potionbag");
        }

        if (getItemLore.wasPressed()) {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                Misc.addChatMessage(Text.literal("player is null"));
                return;
            }

            ItemStack heldStack = player.getMainHandStack();
            LoreComponent lore = heldStack.get(DataComponentTypes.LORE);
            if (lore == null) {
                Misc.addChatMessage(Text.literal("lore is null"));
                return;
            }

            List<Text> lines = lore.lines();
            for (Text line : lines) {
                Misc.addChatMessage(line);
            }

            Misc.addChatMessage(Text.literal("(item rarity display removed)"));
        }

        if (getItemCustomData.wasPressed()) {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                Misc.addChatMessage(Text.literal("player is null"));
                return;
            }

            ItemStack heldStack = player.getMainHandStack();
            NbtComponent nbt = heldStack.get(DataComponentTypes.CUSTOM_DATA);
            if (nbt == null) {
                Misc.addChatMessage(Text.literal("nbt is null"));
                return;
            }

            Misc.addChatMessage(Text.literal(nbt.toString()));

        }

        if (getBlockInfo.wasPressed()) {
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            if (player == null || world == null) {
                Misc.addChatMessage(Text.literal("player or world is null"));
                return;
            }

            HitResult result = player.raycast(4, client.getRenderTickCounter().getTickProgress(false), true);

            if (result instanceof BlockHitResult blockHitResult) {
                BlockPos pos = blockHitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                Misc.addChatMessage(Text.literal("Pos: " + pos));
                if (state.hasBlockEntity()) {
                    BlockEntity entity = world.getBlockEntity(pos);
                    Misc.addChatMessage(Text.literal(entity.toString()));

                    if (entity instanceof SkullBlockEntity skullEntity) {
                        ProfileComponent component = skullEntity.getOwner();
                        if (component != null) {
                            GameProfile profile = component.getGameProfile();
                            Misc.addChatMessage(Text.literal("name: " + profile.name() + " id: " + profile.id()));
                        }
                    }
                }
            }

            //wither essence uuid
            //e0f3e929-869e-3dca-9504-54c666ee6f23
        }
    }
}

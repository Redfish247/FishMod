package fishmod.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side item customization: rename an item and/or render it with another item's model.
 * Stored per item (keyed by SkyBlock uuid → SkyBlock id → vanilla registry id) and re-applied every
 * tick to inventory + open-container slots, since server slot updates overwrite our component edits.
 */
public final class ItemCustomizer {
    private ItemCustomizer() {}

    /** name (&-codes ok), model item id, dungeon star count (0-10), armor dye RGB (-1 = none). */
    public record Custom(String name, String modelId, int stars, int dye) {}

    private static final char[] MASTER = {'➊', '➋', '➌', '➍', '➎'}; // ➊➋➌➍➎

    /** SkyBlock-style star suffix: 1-5 gold ✪, 6-10 = 5 gold ✪ + red master-star count glyph. */
    public static String starSuffix(int s) {
        if (s <= 0) return "";
        if (s <= 5) return " &6" + "✪".repeat(s);
        int masters = Math.min(s, 10) - 5;
        return " &6✪✪✪✪✪&c" + MASTER[masters - 1];
    }

    private static final Path SAVE = Paths.get("config/fishmod/item_customs.json");
    private static final Map<String, Custom> DATA = new LinkedHashMap<>();

    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (DATA.isEmpty() || mc.player == null) return;
            try {
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.size(); i++) apply(inv.getStack(i));
                ScreenHandler h = mc.player.currentScreenHandler;
                if (h != null) for (var slot : h.slots) apply(slot.getStack());
            } catch (Exception ignored) {}
        });
    }

    /**
     * Resolves a stable key for a stack: SkyBlock uuid (per-stack) or SkyBlock id (per-item-type).
     * No vanilla-registry fallback — that's too broad (all player_heads would share a key, so
     * customizing one would apply to every fishing trophy frog you ever catch). If the stack has
     * neither a uuid nor a SkyBlock id, it can't be customized.
     */
    public static String keyFor(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        try {
            NbtComponent cd = st.get(DataComponentTypes.CUSTOM_DATA);
            if (cd != null) {
                NbtCompound nbt = cd.copyNbt();
                NbtCompound ea = nbt.getCompound("ExtraAttributes").orElse(null);
                if (ea != null) {
                    String u = ea.getString("uuid", "");
                    if (!u.isEmpty()) return "uuid:" + u;
                    String id = ea.getString("id", "");
                    if (!id.isEmpty()) return "id:" + id;
                }
                String u = nbt.getString("uuid", "");
                if (!u.isEmpty()) return "uuid:" + u;
                String id = nbt.getString("id", "");
                if (!id.isEmpty()) return "id:" + id;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Wipes every saved customization (recovery for accidentally-broad keys from older builds). */
    public static void clearAll() {
        DATA.clear();
        save();
    }

    public static Custom get(ItemStack st) {
        String k = keyFor(st);
        return k == null ? null : DATA.get(k);
    }

    public static void set(ItemStack st, String name, String modelId, int stars, int dye) {
        String k = keyFor(st);
        if (k == null) return;
        boolean empty = (name == null || name.isEmpty()) && (modelId == null || modelId.isEmpty())
                && stars <= 0 && dye < 0;
        if (empty) DATA.remove(k);
        else DATA.put(k, new Custom(name, modelId, stars, dye));
        save();
        apply(st);
    }

    /** Mutates the client stack's CUSTOM_NAME / ITEM_MODEL / DYED_COLOR components. */
    public static void apply(ItemStack st) {
        if (st == null || st.isEmpty()) return;
        Custom c = get(st);
        if (c == null) return;
        try {
            boolean hasName = c.name != null && !c.name.isEmpty();
            if (hasName || c.stars > 0) {
                String base = hasName ? c.name : st.getItem().getName().getString();
                net.minecraft.text.Text styled = fishmod.cosmetic.NickState.parse(base + starSuffix(c.stars));
                // Vanilla auto-italicizes CUSTOM_NAME (anvil-rename behavior); explicitly clear it.
                net.minecraft.text.MutableText name = net.minecraft.text.Text.empty()
                        .append(styled)
                        .setStyle(net.minecraft.text.Style.EMPTY.withItalic(false));
                st.set(DataComponentTypes.CUSTOM_NAME, name);
            }
            if (c.modelId != null && !c.modelId.isEmpty()) {
                Identifier id = c.modelId.contains(":") ? Identifier.of(c.modelId) : Identifier.ofVanilla(c.modelId);
                st.set(DataComponentTypes.ITEM_MODEL, id);
            }
            if (c.dye >= 0)
                st.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(c.dye & 0xFFFFFF));
        } catch (Exception ignored) {}
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE.getParent());
            JsonArray arr = new JsonArray();
            for (var e : DATA.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("key", e.getKey());
                if (e.getValue().name() != null) o.addProperty("name", e.getValue().name());
                if (e.getValue().modelId() != null) o.addProperty("model", e.getValue().modelId());
                o.addProperty("stars", e.getValue().stars());
                o.addProperty("dye", e.getValue().dye());
                arr.add(o);
            }
            Files.writeString(SAVE, arr.toString());
        } catch (Exception ignored) {}
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE)) return;
            JsonArray arr = JsonParser.parseString(Files.readString(SAVE)).getAsJsonArray();
            DATA.clear();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                DATA.put(o.get("key").getAsString(), new Custom(
                        o.has("name") ? o.get("name").getAsString() : null,
                        o.has("model") ? o.get("model").getAsString() : null,
                        o.has("stars") ? o.get("stars").getAsInt() : 0,
                        o.has("dye") ? o.get("dye").getAsInt() : -1));
            }
        } catch (Exception ignored) {}
    }
}

package fishmod.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side item customization: rename an item, render it with another item's model, add dungeon
 * stars, dye leather, and apply an armor trim. Stored per item (keyed by SkyBlock uuid → SkyBlock
 * id → vanilla registry id) and re-applied every tick to inventory + open-container slots, since
 * server slot updates overwrite our component edits.
 *
 * Customizations are also published to the mod proxy so other mod users see them on your worn armor
 * and held items — see {@link fishmod.cosmetic.RemoteItems}.
 */
public final class ItemCustomizer {
    private ItemCustomizer() {}

    /** name (&-codes ok), model item id, dungeon star count (0-10), armor dye RGB (-1 = none),
     *  armor trim material id + pattern id (null/empty = none), and the source item's vanilla
     *  registry id (e.g. "minecraft:diamond_sword"). The vanilla id is how OTHER players match this
     *  custom: Hypixel strips SkyBlock NBT (the uuid/id key) off other players' items, so the only
     *  thing a viewer can identify is the vanilla item type. */
    public record Custom(String name, String modelId, int stars, int dye, String trimMat, String trimPat,
                         String skin, String dyeAnim, String vanilla) {
        public Custom withVanilla(String v) { return new Custom(name, modelId, stars, dye, trimMat, trimPat, skin, dyeAnim, v); }
    }

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
        uploadOwn(); // publish persisted customs on startup
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (DATA.isEmpty() || mc.player == null) return;
            try {
                boolean dirty = false;
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.size(); i++) dirty |= applyAndBackfill(inv.getStack(i));
                ScreenHandler h = mc.player.currentScreenHandler;
                if (h != null) for (var slot : h.slots) dirty |= applyAndBackfill(slot.getStack());
                // A custom captured its vanilla type for the first time → persist + republish so other
                // players (who can only match by vanilla type) start seeing it.
                if (dirty) { save(); uploadOwn(); }
            } catch (Exception ignored) {}
        });
    }

    /** The vanilla registry id of a stack's base item (e.g. "minecraft:diamond_sword"). */
    public static String vanillaId(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        try { return Registries.ITEM.getId(st.getItem()).toString(); } catch (Exception e) { return null; }
    }

    /**
     * Applies the local custom for this stack and, if the custom doesn't yet know its source vanilla
     * type, records it from the stack. Returns true when a vanilla type was newly captured (so the
     * caller saves + re-uploads). Backfills pre-existing customs as their items pass through inventory.
     */
    private static boolean applyAndBackfill(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        String key = keyFor(st);
        if (key == null) return false;
        Custom c = DATA.get(key);
        if (c == null) return false;
        boolean captured = false;
        if (c.vanilla() == null || c.vanilla().isEmpty()) {
            String v = vanillaId(st);
            if (v != null) { c = c.withVanilla(v); DATA.put(key, c); captured = true; }
        }
        applyCustom(st, c);
        return captured;
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

    /** The SkyBlock pet type of a pet item (e.g. "ENDER_DRAGON") from its ExtraAttributes petInfo, or null. */
    public static String petType(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        try {
            NbtComponent cd = st.get(DataComponentTypes.CUSTOM_DATA);
            if (cd == null) return null;
            NbtCompound ea = cd.copyNbt().getCompound("ExtraAttributes").orElse(null);
            if (ea == null) return null;
            String petInfo = ea.getString("petInfo", "");
            if (petInfo.isEmpty()) return null;
            JsonObject o = JsonParser.parseString(petInfo).getAsJsonObject();
            return o.has("type") ? o.get("type").getAsString() : null;
        } catch (Exception e) { return null; }
    }

    /** The current head texture (base64 "textures" value) on a player_head stack, or null. */
    public static String currentHeadTexture(ItemStack st) {
        if (st == null || !st.isOf(net.minecraft.item.Items.PLAYER_HEAD)) return null;
        try {
            net.minecraft.component.type.ProfileComponent pc = st.get(DataComponentTypes.PROFILE);
            if (pc == null) return null;
            for (com.mojang.authlib.properties.Property p : pc.getGameProfile().properties().get("textures"))
                return p.value();
        } catch (Exception ignored) {}
        return null;
    }

    /** Wipes every saved customization (recovery for accidentally-broad keys from older builds). */
    public static void clearAll() {
        DATA.clear();
        save();
        uploadOwn();
    }

    public static Custom get(ItemStack st) {
        String k = keyFor(st);
        return k == null ? null : DATA.get(k);
    }

    public static void set(ItemStack st, String name, String modelId, int stars, int dye,
                           String trimMat, String trimPat, String skin, String dyeAnim) {
        String k = keyFor(st);
        if (k == null) return;
        boolean noTrim = (trimMat == null || trimMat.isEmpty()) || (trimPat == null || trimPat.isEmpty());
        boolean noSkin = (skin == null || skin.isEmpty());
        boolean noAnim = (dyeAnim == null || dyeAnim.isEmpty());
        boolean empty = (name == null || name.isEmpty()) && (modelId == null || modelId.isEmpty())
                && stars <= 0 && dye < 0 && noTrim && noSkin && noAnim;
        if (empty) DATA.remove(k);
        else DATA.put(k, new Custom(name, modelId, stars, dye,
                noTrim ? null : trimMat, noTrim ? null : trimPat, noSkin ? null : skin,
                noAnim ? null : dyeAnim, vanillaId(st)));
        save();
        apply(st);
        uploadOwn();
    }

    /** Looks up the local customization for this stack (if any) and applies it. */
    public static void apply(ItemStack st) {
        if (st == null || st.isEmpty()) return;
        Custom c = get(st);
        if (c != null) applyCustom(st, c);
    }

    /**
     * Mutates a stack's CUSTOM_NAME / ITEM_MODEL / DYED_COLOR / TRIM components from a Custom.
     * Used for both the local player's items and (via {@link fishmod.cosmetic.RemoteItems}) other
     * players' worn armor / held items. Names are run through the profanity filter so a shared
     * custom name can never display a slur on your screen.
     */
    public static void applyCustom(ItemStack st, Custom c) {
        applyCustom(st, c, true);
    }

    /**
     * @param applySkin whether to apply a player-head skin override. Skins are local-only: remote
     *   customs are matched by vanilla item type, and many distinct items share the player_head type
     *   (pets, talismans, …), so a shared skin couldn't be matched to the right head — see
     *   {@link fishmod.cosmetic.RemoteItems}. Local items are keyed precisely by SkyBlock uuid/id, so
     *   the skin lands on exactly the intended item.
     */
    public static void applyCustom(ItemStack st, Custom c, boolean applySkin) {
        if (st == null || st.isEmpty() || c == null) return;
        try {
            boolean hasName = c.name() != null && !c.name().isEmpty();
            if (hasName || c.stars() > 0) {
                String base = hasName ? fishmod.cosmetic.ProfanityFilter.censor(c.name())
                                      : st.getItem().getName().getString();
                net.minecraft.text.Text styled = fishmod.cosmetic.NickState.parse(base + starSuffix(c.stars()));
                // Vanilla auto-italicizes CUSTOM_NAME (anvil-rename behavior); explicitly clear it.
                net.minecraft.text.MutableText name = net.minecraft.text.Text.empty()
                        .append(styled)
                        .setStyle(net.minecraft.text.Style.EMPTY.withItalic(false));
                st.set(DataComponentTypes.CUSTOM_NAME, name);
            }
            if (c.modelId() != null && !c.modelId().isEmpty()) {
                st.set(DataComponentTypes.ITEM_MODEL, ident(c.modelId()));
            }
            if (c.dye() >= 0)
                st.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(c.dye() & 0xFFFFFF));
            // Animated dye: recomputed every tick (this runs in the per-tick reapply for both local and
            // remote items), so it animates smoothly and can't be left flickering by server resyncs.
            if (c.dyeAnim() != null && !c.dyeAnim().isEmpty())
                st.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(animColor(c.dyeAnim(), c.dye())));
            applyTrim(st, c);
            if (applySkin) applyHeadSkin(st, c);
        } catch (Exception ignored) {}
    }

    /**
     * Repaints a player-head's profile texture from the custom's skin (a SkyBlock pet/cosmetic head
     * skin). Accepts a texture hash, a textures.minecraft.net URL, or a raw base64 textures value.
     * No-op for non-head items so other customized items are untouched.
     */
    private static void applyHeadSkin(ItemStack st, Custom c) {
        if (c.skin() == null || c.skin().isEmpty()) return;
        if (!st.isOf(net.minecraft.item.Items.PLAYER_HEAD)) return;
        try {
            net.minecraft.component.type.ProfileComponent pc = buildSkinProfile(c.skin());
            if (pc != null) st.set(DataComponentTypes.PROFILE, pc);
        } catch (Exception ignored) {}
    }

    /** Builds a PROFILE component carrying the given head texture, or null if it can't be resolved. */
    public static net.minecraft.component.type.ProfileComponent buildSkinProfile(String skin) {
        String value = texturesValue(skin);
        if (value == null) return null;
        // A stable UUID per texture keeps the profile cache-friendly; the name is cosmetic.
        java.util.UUID id = java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(id, "FishModSkin");
        gp.properties().put("textures", new com.mojang.authlib.properties.Property("textures", value));
        return net.minecraft.component.type.ProfileComponent.ofStatic(gp);
    }

    /**
     * Normalizes a skin string to a base64 "textures" property value. Recognizes a full URL, a bare
     * texture hash (the part after .../texture/), or an already-encoded base64 value (used as-is).
     */
    private static String texturesValue(String skin) {
        if (skin == null) return null;
        skin = skin.trim();
        if (skin.isEmpty()) return null;
        String url = null;
        if (skin.startsWith("http://") || skin.startsWith("https://")) {
            url = skin;
        } else if (skin.startsWith("textures.minecraft.net")) {
            url = "http://" + skin;
        } else if (skin.matches("[0-9a-fA-F]{16,128}")) {
            url = "http://textures.minecraft.net/texture/" + skin.toLowerCase();
        }
        if (url != null) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            return java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // Otherwise treat the input as a base64 textures value already (what Mojang stores).
        return skin;
    }

    /** Resolves trim material + pattern from the world's data registries and sets the TRIM component. */
    private static void applyTrim(ItemStack st, Custom c) {
        if (c.trimMat() == null || c.trimMat().isEmpty() || c.trimPat() == null || c.trimPat().isEmpty()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            var drm = mc.world.getRegistryManager();
            var matReg = drm.getOptional(RegistryKeys.TRIM_MATERIAL).orElse(null);
            var patReg = drm.getOptional(RegistryKeys.TRIM_PATTERN).orElse(null);
            if (matReg == null || patReg == null) return;
            var mat = matReg.getEntry(ident(c.trimMat())).orElse(null);
            var pat = patReg.getEntry(ident(c.trimPat())).orElse(null);
            if (mat != null && pat != null) st.set(DataComponentTypes.TRIM, new ArmorTrim(mat, pat));
        } catch (Exception ignored) {}
    }

    private static Identifier ident(String id) {
        return id.contains(":") ? Identifier.of(id) : Identifier.ofVanilla(id);
    }

    // ── animated dyes (generic styles) ──────────────────────────────────────────
    /** The animated-dye presets offered by the customizer (key → display label). */
    public static final String[][] ANIM_DYES = {
        {"rainbow", "Rainbow"}, {"fire", "Fire"}, {"ice", "Ice"},
        {"toxic", "Toxic"}, {"galaxy", "Galaxy"}, {"pulse", "Pulse"},
    };

    /** Time-based RGB for an animated dye. {@code baseDye} (>=0) is the pulse/fallback color. */
    public static int animColor(String anim, int baseDye) {
        long t = System.currentTimeMillis();
        int base = baseDye >= 0 ? (baseDye & 0xFFFFFF) : 0xFFFFFF;
        return switch (anim) {
            case "rainbow" -> hsvToRgb((t % 6000L) / 6000f, 0.9f, 1f);
            case "fire"    -> cycle(t, 0xFF3300, 0xFF8800, 0xFFDD00);
            case "ice"     -> cycle(t, 0x00CCFF, 0x3366FF, 0xFFFFFF);
            case "toxic"   -> cycle(t, 0x00FF66, 0x99FF00, 0x33CC00);
            case "galaxy"  -> cycle(t, 0x6600FF, 0xCC00FF, 0x3300AA);
            case "pulse"   -> scale(base, 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(t / 600.0)));
            default        -> base;
        };
    }

    /** Representative swatch color for an animated dye (for the dropdown), sampled "now". */
    public static int animSwatch(String anim) { return animColor(anim, 0xFFFFFF); }

    private static int cycle(long t, int... cols) {
        double period = 2500.0; // ms per color leg
        double pos = (t % (long) (period * cols.length)) / period; // 0..cols.length
        int i = (int) pos;
        return lerp(cols[i % cols.length], cols[(i + 1) % cols.length], pos - i);
    }
    private static int lerp(int a, int b, double f) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }
    private static int scale(int c, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        float p = v * (1 - s), q = v * (1 - f * s), tt = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = tt; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = tt; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ── persistence + sharing ──────────────────────────────────────────────────

    /** Serializes the local customization map to the shared JSON-array format. */
    public static synchronized String serialize() {
        return toJson().toString();
    }

    /** Debug: the keys of the local player's own customizations (what gets uploaded). */
    public static java.util.Set<String> debugKeys() {
        return new java.util.LinkedHashSet<>(DATA.keySet());
    }

    private static JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (var e : DATA.entrySet()) {
            Custom v = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("key", e.getKey());
            if (v.name() != null) o.addProperty("name", v.name());
            if (v.modelId() != null) o.addProperty("model", v.modelId());
            o.addProperty("stars", v.stars());
            o.addProperty("dye", v.dye());
            if (v.trimMat() != null) o.addProperty("trimMat", v.trimMat());
            if (v.trimPat() != null) o.addProperty("trimPat", v.trimPat());
            if (v.skin() != null) o.addProperty("skin", v.skin());
            if (v.dyeAnim() != null) o.addProperty("dyeAnim", v.dyeAnim());
            if (v.vanilla() != null) o.addProperty("vanilla", v.vanilla());
            arr.add(o);
        }
        return arr;
    }

    /** Parses a shared JSON-array payload into a key→Custom map (used for remote players). */
    public static Map<String, Custom> parsePayload(String json) {
        Map<String, Custom> out = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("key")) continue;
                out.put(o.get("key").getAsString(), new Custom(
                        o.has("name") ? o.get("name").getAsString() : null,
                        o.has("model") ? o.get("model").getAsString() : null,
                        o.has("stars") ? o.get("stars").getAsInt() : 0,
                        o.has("dye") ? o.get("dye").getAsInt() : -1,
                        o.has("trimMat") ? o.get("trimMat").getAsString() : null,
                        o.has("trimPat") ? o.get("trimPat").getAsString() : null,
                        o.has("skin") ? o.get("skin").getAsString() : null,
                        o.has("dyeAnim") ? o.get("dyeAnim").getAsString() : null,
                        o.has("vanilla") ? o.get("vanilla").getAsString() : null));
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Publishes the local player's customizations to the proxy so other mod users see them. */
    public static void uploadOwn() {
        if (!fishmod.utils.config.values.FishSettings.remoteItemsEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        java.util.UUID id = mc.getSession().getUuidOrNull();
        if (id == null) return;
        fishmod.utils.HypixelApi.uploadItems(id.toString().replace("-", ""), serialize());
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE.getParent());
            Files.writeString(SAVE, toJson().toString());
        } catch (Exception ignored) {}
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE)) return;
            DATA.clear();
            DATA.putAll(parsePayload(Files.readString(SAVE)));
        } catch (Exception ignored) {}
    }
}

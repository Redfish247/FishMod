package fishmod.features;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * /fm customize — a clearer, friendlier item customizer.
 *
 * <p>PICK an item (worn armor or inventory slot), then CUSTOMIZE it:
 * <ul>
 *   <li><b>Name</b> &amp; <b>Model</b> are pre-filled with the item's real values so you edit a base,
 *       not a blank. <b>Reset</b> returns to base instead of wiping.</li>
 *   <li>A clickable <b>&amp;-code key</b> inserts colors/formats into the name at the cursor. Type
 *       <b>&amp;*</b> to add a ✪ star in whatever color precedes it (no more star counter).</li>
 *   <li><b>Dye</b> is a Hypixel-dye dropdown (with a free hex box) — only for leather armor.</li>
 *   <li><b>Trim</b> material + pattern are dropdowns.</li>
 * </ul>
 */
public class ItemCustomizeScreen extends Screen {

    private static final int CELL = 18;
    // palette
    private static final int BG_PANEL   = 0xF20E1016;
    private static final int BG_SECTION = 0xFF171A22;
    private static final int BG_FIELD   = 0xFF1B1E27;
    private static final int BORDER     = 0xFF2A2D38;
    private static final int BORDER_LT  = 0xFF3A3E4A;
    private static final int ACCENT     = 0xFF55FFFF;
    private static final int TEXT_PRIM  = 0xFFE8ECF2;
    private static final int TEXT_HINT  = 0xFF8A8F9C;
    private static final int SLOT_BG    = 0xFF2A2D38;
    private static final int SLOT_SEL   = 0xFF55FF55;
    private static final int ROW_HOVER  = 0xFF2A2D38;
    private static final int LIST_BG    = 0xFF14161D;

    // &-code → RGB for the clickable color key (matches the main /fm legend).
    private static final int[][] CODE_COLORS = {
            {'0', 0x000000}, {'1', 0x0000AA}, {'2', 0x00AA00}, {'3', 0x00AAAA},
            {'4', 0xAA0000}, {'5', 0xAA00AA}, {'6', 0xFFAA00}, {'7', 0xAAAAAA},
            {'8', 0x555555}, {'9', 0x5555FF}, {'a', 0x55FF55}, {'b', 0x55FFFF},
            {'c', 0xFF5555}, {'d', 0xFF55FF}, {'e', 0xFFFF55}, {'f', 0xFFFFFF},
    };
    // Format codes shown as live styled samples (code char, sample text).
    private static final String[][] CODE_FORMATS = {
            {"l", "§lB"}, {"o", "§oI"}, {"n", "§nU"}, {"m", "§mS"}, {"k", "§kMM"}, {"r", "§rR"},
    };

    // Hypixel SkyBlock dyes (name, RRGGBB). Not exhaustive — the hex box covers anything missing.
    private static final String[][] DYES = {
            {"Pure White", "FFFFFF"}, {"Pure Black", "000000"}, {"Pure Yellow", "FFF700"}, {"Pure Blue", "0013FF"},
            {"Aquamarine", "7FFFD4"}, {"Bingo Blue", "002FA7"}, {"Bone", "E3DAC9"}, {"Brick Red", "CB4154"},
            {"Byzantium", "702963"}, {"Carmine", "960018"}, {"Celadon", "ACE1AF"}, {"Celeste", "B2FFFF"},
            {"Cyclamen", "F56FA1"}, {"Dark Purple", "301934"}, {"Emerald", "50C878"}, {"Flame", "E25822"},
            {"Holly", "3C6746"}, {"Iceberg", "71A6D2"}, {"Livid", "6699CC"}, {"Mango", "FDBE02"},
            {"Midnight", "702670"}, {"Nadeshiko", "F6ADC6"}, {"Necron", "E7413C"}, {"Nyanza", "E9FFDB"},
            {"Tentacle", "324D6C"}, {"Wild Strawberry", "FF43A4"},
            // vanilla leather dyes
            {"White", "F9FFFE"}, {"Light Gray", "999999"}, {"Gray", "4C4C4C"}, {"Ink Sac (Black)", "191919"},
            {"Rose Red", "993333"}, {"Orange", "D87F33"}, {"Dandelion Yellow", "E5E533"}, {"Lime", "7FCC19"},
            {"Cactus Green", "667F33"}, {"Light Blue", "6699D8"}, {"Cyan", "4C7F99"}, {"Lapis (Blue)", "334CB2"},
            {"Purple", "7F3FB2"}, {"Magenta", "B24CD8"}, {"Pink", "F27FA5"}, {"Cocoa (Brown)", "664C33"},
    };
    private static final int[] DYE_RGB = new int[DYES.length];
    static {
        for (int i = 0; i < DYES.length; i++) DYE_RGB[i] = (int) Long.parseLong(DYES[i][1], 16);
    }

    // Vanilla armor trim registry ids.
    private static final String[] TRIM_MATERIALS = {
            "quartz", "iron", "netherite", "redstone", "copper", "gold",
            "emerald", "diamond", "lapis", "amethyst", "resin",
    };
    private static final String[] TRIM_PATTERNS = {
            "sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
            "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt",
    };

    private int panelX, panelY;
    private final int panelW = 360, panelH = 460;
    private int gridX, gridY, armorX, armorY;
    private int legendX, legendY;
    private int selectedIndex = 0;
    private TextFieldWidget nameField, modelField, dyeField, skinField;
    private Dropdown dyeDropdown, trimMatDropdown, trimPatDropdown, skinDropdown;
    private ButtonWidget grabButton;
    private String selectedAnim = null; // chosen animated-dye key, or null for a static dye
    private final List<PetSkins.Skin> skinChoices = new ArrayList<>();

    // &-code key hit-boxes, rebuilt each frame, consumed by mouseClicked. {x,y,w,h} + parallel code.
    private final List<int[]> keyRects = new ArrayList<>();
    private final List<String> keyCodes = new ArrayList<>();

    public ItemCustomizeScreen() { super(Text.literal("Item Customize")); }

    private PlayerInventory inv() { return client.player.getInventory(); }
    private int mainCount() { return Math.min(36, inv().size()); }

    @Override
    protected void init() {
        if (client == null || client.player == null) return;

        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);

        // Default selection = currently held item.
        ItemStack held = client.player.getMainHandStack();
        for (int i = 0; i < mainCount(); i++) if (inv().getStack(i) == held) { selectedIndex = i; break; }

        int p = panelX + 14;
        int contentW = panelW - 28;

        // PICK ITEM
        int pickY = panelY + 54;
        armorX = p;
        armorY = pickY + 14;
        gridX  = p + 4 * CELL + 12;
        gridY  = armorY;

        // CUSTOMIZE
        legendX = p;
        legendY = panelY + 172;

        int labelW = 52;
        int fx = p + labelW;
        int fw = contentW - labelW;

        int nameY  = legendY + 50;
        int modelY = nameY + 24;
        int dyeY   = modelY + 24;
        int trimY  = dyeY + 24;
        int skinY  = trimY + 24;

        nameField = new TextFieldWidget(this.textRenderer, fx, nameY, fw, 14, Text.literal("Name"));
        nameField.setMaxLength(128);
        addDrawableChild(nameField);

        modelField = new TextFieldWidget(this.textRenderer, fx, modelY, fw, 14, Text.literal("Model"));
        modelField.setMaxLength(64);
        addDrawableChild(modelField);

        // Dye: hex box on the right, dropdown on the left (built below).
        dyeField = new TextFieldWidget(this.textRenderer, fx + 156, dyeY, fw - 156, 14, Text.literal("Hex"));
        dyeField.setMaxLength(6);
        addDrawableChild(dyeField);

        // Dye dropdown: animated presets first (marked ✦), then the static Hypixel dyes.
        dyeDropdown = new Dropdown("Pick a dye…", fx, dyeY, 150);
        int A = ItemCustomizer.ANIM_DYES.length;
        int[] swatches = new int[A + DYE_RGB.length];
        for (int i = 0; i < A; i++) {
            dyeDropdown.labels.add("✦ " + ItemCustomizer.ANIM_DYES[i][1]);
            swatches[i] = ItemCustomizer.animSwatch(ItemCustomizer.ANIM_DYES[i][0]);
        }
        for (int i = 0; i < DYES.length; i++) { dyeDropdown.labels.add(DYES[i][0]); swatches[A + i] = DYE_RGB[i]; }
        dyeDropdown.swatches = swatches;
        dyeDropdown.onChange = () -> {
            int s = dyeDropdown.selected;
            if (s < 0) { selectedAnim = null; return; }
            if (s < A) { selectedAnim = ItemCustomizer.ANIM_DYES[s][0]; dyeField.setText(""); }
            else { selectedAnim = null; dyeField.setText(DYES[s - A][1]); }
        };

        int half = (fw - 6) / 2;
        trimMatDropdown = new Dropdown("Material", fx, trimY, half);
        for (String s : TRIM_MATERIALS) trimMatDropdown.labels.add(cap(s));
        trimPatDropdown = new Dropdown("Pattern", fx + half + 6, trimY, half);
        for (String s : TRIM_PATTERNS) trimPatDropdown.labels.add(cap(s));

        // Skin: a head texture for player_head items (e.g. apply a Hypixel pet/cosmetic skin). Accepts
        // a texture hash, a textures.minecraft.net URL, or a raw base64 textures value. A "Grab" button
        // copies the head's current texture in, and a dropdown offers curated skins for this pet type.
        skinField = new TextFieldWidget(this.textRenderer, fx, skinY, fw - 54, 14, Text.literal("Skin"));
        skinField.setMaxLength(2048);
        addDrawableChild(skinField);
        grabButton = ButtonWidget.builder(Text.literal("Grab"), b -> grabSkin())
                .dimensions(fx + fw - 50, skinY - 1, 50, 16)
                .tooltip(Tooltip.of(Text.literal("Copy the texture from the selected head into the Skin box.")))
                .build();
        addDrawableChild(grabButton);

        skinDropdown = new Dropdown("Pick a pet skin…", fx, skinY + 18, fw);
        skinDropdown.onChange = () -> {
            if (skinDropdown.selected < 0 || skinDropdown.selected >= skinChoices.size()) return;
            String tex = skinChoices.get(skinDropdown.selected).tex();
            String hash = PetSkins.hashFromValue(tex);
            skinField.setText(hash != null ? hash : tex); // prefer the tidy hash, fall back to raw value
        };

        // Bottom buttons
        int btnY = panelY + panelH - 26;
        int btnW = 70;
        int btnX = panelX + (panelW - btnW * 3 - 12) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), b -> apply())
                .dimensions(btnX, btnY, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("§eReset"), b -> reset())
                .dimensions(btnX + btnW + 6, btnY, btnW, 20)
                .tooltip(Tooltip.of(Text.literal("Restore this item to its original look and re-fill the fields with its base values.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(btnX + (btnW + 6) * 2, btnY, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("§cClear All"), b -> { ItemCustomizer.clearAll(); loadFields(); })
                .dimensions(panelX + panelW - 60 - 8, btnY, 60, 20)
                .tooltip(Tooltip.of(Text.literal("Wipes every saved customization on every item.")))
                .build());

        loadFields();
    }

    // ── load / apply / reset ───────────────────────────────────────────────────

    private void loadFields() {
        ItemStack sel = inv().getStack(selectedIndex);
        ItemCustomizer.Custom c = ItemCustomizer.get(sel);

        // Name + Model pre-fill with the item's BASE values so the user edits, not starts blank.
        nameField.setText(c != null && c.name() != null && !c.name().isEmpty() ? c.name() : sel.getName().getString());
        String baseModel = ItemCustomizer.vanillaId(sel);
        modelField.setText(c != null && c.modelId() != null && !c.modelId().isEmpty()
                ? c.modelId() : (baseModel != null ? baseModel : ""));

        int A = ItemCustomizer.ANIM_DYES.length;
        selectedAnim = (c != null && c.dyeAnim() != null && !c.dyeAnim().isEmpty()) ? c.dyeAnim() : null;
        if (selectedAnim != null) {
            dyeField.setText("");
            dyeDropdown.selected = animIndex(selectedAnim); // 0..A-1
        } else {
            int dye = c != null ? c.dye() : -1;
            dyeField.setText(dye >= 0 ? String.format("%06X", dye & 0xFFFFFF) : "");
            int di = dye >= 0 ? dyeIndex(dye) : -1;
            dyeDropdown.selected = di >= 0 ? A + di : -1;
        }

        trimMatDropdown.selected = c != null && c.trimMat() != null ? indexOf(TRIM_MATERIALS, c.trimMat()) : -1;
        trimPatDropdown.selected = c != null && c.trimPat() != null ? indexOf(TRIM_PATTERNS, c.trimPat()) : -1;

        skinField.setText(c != null && c.skin() != null ? c.skin() : "");
        skinField.visible = isHead(sel);
        grabButton.visible = isHead(sel);

        // Populate the pet-skin dropdown with skins matching this pet (or all heads for a generic head).
        skinChoices.clear();
        skinDropdown.labels.clear();
        skinDropdown.selected = -1;
        if (isHead(sel)) {
            skinChoices.addAll(PetSkins.forPet(ItemCustomizer.petType(sel)));
            for (PetSkins.Skin s : skinChoices) skinDropdown.labels.add(s.name());
        }

        dyeDropdown.close(); trimMatDropdown.close(); trimPatDropdown.close(); skinDropdown.close();
        dyeField.visible = dyeAllowed(sel);
    }

    private boolean isHead(ItemStack st) {
        return st != null && !st.isEmpty() && st.isOf(Items.PLAYER_HEAD);
    }

    /** Copies the selected head's current texture into the Skin box (tidy hash when possible). */
    private void grabSkin() {
        ItemStack sel = inv().getStack(selectedIndex);
        if (!isHead(sel)) return;
        String tex = ItemCustomizer.currentHeadTexture(sel);
        if (tex == null || tex.isEmpty()) return;
        String hash = PetSkins.hashFromValue(tex);
        skinField.setText(hash != null ? hash : tex);
    }

    private void apply() {
        ItemStack sel = inv().getStack(selectedIndex);

        // "equals base" → store empty so the item keeps its original (colored) name / model.
        String name = nameField.getText();
        if (name.equals(sel.getName().getString())) name = "";
        String baseModel = ItemCustomizer.vanillaId(sel);
        String model = modelField.getText().trim();
        if (baseModel != null && model.equals(baseModel)) model = "";

        // Animated dye wins over a static hex when one is selected.
        String dyeAnim = (dyeAllowed(sel) && selectedAnim != null) ? selectedAnim : "";
        int dye = -1;
        if (dyeAllowed(sel) && dyeAnim.isEmpty()) {
            String d = dyeField.getText().trim();
            if (d.length() == 6) try { dye = (int) Long.parseLong(d, 16); } catch (NumberFormatException ignored) {}
        }

        String mat = trimMatDropdown.selected >= 0 ? TRIM_MATERIALS[trimMatDropdown.selected] : "";
        String pat = trimPatDropdown.selected >= 0 ? TRIM_PATTERNS[trimPatDropdown.selected] : "";

        String skin = isHead(sel) ? skinField.getText().trim() : "";

        // Stars are now encoded as "&*" inside the name, so the stored star count is always 0.
        ItemCustomizer.set(sel, name, model, 0, dye, mat, pat, skin, dyeAnim);
    }

    /** Restore the item to its original appearance, then re-fill the fields with its base values. */
    private void reset() {
        selectedAnim = null;
        ItemCustomizer.set(inv().getStack(selectedIndex), "", "", 0, -1, "", "", "", "");
        loadFields();
    }

    /** Index of an animated-dye key within ItemCustomizer.ANIM_DYES, or -1. */
    private static int animIndex(String key) {
        for (int i = 0; i < ItemCustomizer.ANIM_DYES.length; i++)
            if (ItemCustomizer.ANIM_DYES[i][0].equals(key)) return i;
        return -1;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean dyeAllowed(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        var it = st.getItem();
        if (it == Items.LEATHER_HELMET || it == Items.LEATHER_CHESTPLATE
                || it == Items.LEATHER_LEGGINGS || it == Items.LEATHER_BOOTS) return true;
        try { return st.contains(DataComponentTypes.DYED_COLOR); } catch (Exception e) { return false; }
    }

    private static int dyeIndex(int rgb) {
        rgb &= 0xFFFFFF;
        for (int i = 0; i < DYE_RGB.length; i++) if ((DYE_RGB[i] & 0xFFFFFF) == rgb) return i;
        return -1;
    }

    private static int indexOf(String[] arr, String v) {
        if (v == null) return -1;
        for (int i = 0; i < arr.length; i++) if (arr[i].equalsIgnoreCase(v)) return i;
        return -1;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void insertIntoName(String code) {
        String t = nameField.getText();
        int cur = Math.min(nameField.getCursor(), t.length());
        String nt = t.substring(0, cur) + code + t.substring(cur);
        if (nt.length() > 128) return;
        nameField.setText(nt);
        nameField.setCursor(cur + code.length(), false);
        nameField.setFocused(true);
        setFocused(nameField);
    }

    private String idOf(ItemStack st) {
        try {
            NbtComponent cd = st.get(DataComponentTypes.CUSTOM_DATA);
            if (cd != null) {
                String id = cd.copyNbt().getString("id", "");
                if (!id.isEmpty()) return id;
            }
        } catch (Exception ignored) {}
        return Registries.ITEM.getId(st.getItem()).toString();
    }

    // ── render ─────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        drawPanel(ctx, mouseX, mouseY);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // renderBackground (panel + fields) + buttons
        // Open dropdown lists float above everything else.
        ItemStack sel = inv().getStack(selectedIndex);
        if (dyeAllowed(sel)) dyeDropdown.renderOpen(ctx, mouseX, mouseY);
        trimMatDropdown.renderOpen(ctx, mouseX, mouseY);
        trimPatDropdown.renderOpen(ctx, mouseX, mouseY);
        if (isHead(sel)) skinDropdown.renderOpen(ctx, mouseX, mouseY);
    }

    private void drawPanel(DrawContext ctx, int mouseX, int mouseY) {
        ItemStack sel = inv().getStack(selectedIndex);
        int p = panelX + 14;

        // Outer panel + title
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION);
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§b§lItem Customize", panelX + panelW / 2, panelY + 7, 0xFFFFFF);

        // Current readout
        int curY = panelY + 28;
        ctx.drawTextWithShadow(this.textRenderer, "§7Editing:", p, curY, TEXT_HINT);
        ctx.drawTextWithShadow(this.textRenderer, sel.getName(), p + 46, curY, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8" + idOf(sel), p, curY + 11, TEXT_HINT);

        // PICK ITEM
        int pickY = panelY + 54;
        ctx.drawTextWithShadow(this.textRenderer, "§b▍ §fPICK ITEM §8— click armor or a slot", p, pickY, ACCENT);

        int[] armorSlots = {39, 38, 37, 36};
        String[] armorTags = {"H", "C", "L", "B"};
        for (int r = 0; r < 4; r++) {
            int s = armorSlots[r];
            int x = armorX + r * CELL, y = armorY;
            if (s == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL);
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG);
            if (s < inv().size()) {
                ItemStack a = inv().getStack(s);
                if (!a.isEmpty()) ctx.drawItem(a, x, y);
            }
            ctx.drawTextWithShadow(this.textRenderer, "§8" + armorTags[r], x + 5, y + 18, TEXT_HINT);
        }
        for (int i = 0; i < mainCount(); i++) {
            int col = i % 9;
            int row = (i < 9) ? 3 : (i - 9) / 9;
            int x = gridX + col * CELL, y = gridY + row * CELL;
            if (i == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL);
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG);
            ItemStack st = inv().getStack(i);
            if (!st.isEmpty()) { ctx.drawItem(st, x, y); ctx.drawStackOverlay(this.textRenderer, st, x, y); }
        }

        // CUSTOMIZE header + &-code key
        ctx.drawTextWithShadow(this.textRenderer, "§b▍ §fCUSTOMIZE", p, panelY + 150, ACCENT);
        drawColorKey(ctx, mouseX, mouseY);

        int labelW = 52;
        int fx = p + labelW;
        int fw = panelW - 28 - labelW;
        int nameY  = legendY + 50;
        int modelY = nameY + 24;
        int dyeY   = modelY + 24;
        int trimY  = dyeY + 24;
        int skinY  = trimY + 24;

        drawLabel(ctx, "Name", p, nameY);
        drawLabel(ctx, "Model", p, modelY);
        drawLabel(ctx, "Dye", p, dyeY);
        drawLabel(ctx, "Trim", p, trimY);
        drawLabel(ctx, "Skin", p, skinY);

        // Dye row: dropdown + hex, or a hint when the item can't be dyed.
        if (dyeAllowed(sel)) {
            dyeDropdown.renderClosed(ctx, mouseX, mouseY);
            ctx.drawTextWithShadow(this.textRenderer, "§8#", fx + 150, dyeY + 3, TEXT_HINT);
        } else {
            ctx.fill(fx, dyeY, fx + fw, dyeY + 14, BG_FIELD);
            ctx.drawTextWithShadow(this.textRenderer, "§8leather armor only", fx + 4, dyeY + 3, TEXT_HINT);
        }

        // Trim dropdowns
        trimMatDropdown.renderClosed(ctx, mouseX, mouseY);
        trimPatDropdown.renderClosed(ctx, mouseX, mouseY);

        // Skin row: editable texture box + Grab button + a pet-skin dropdown, or a hint for non-heads.
        if (isHead(sel)) {
            skinDropdown.renderClosed(ctx, mouseX, mouseY);
        } else {
            ctx.fill(fx, skinY, fx + fw, skinY + 14, BG_FIELD);
            ctx.drawTextWithShadow(this.textRenderer, "§8player heads only (pets)", fx + 4, skinY + 3, TEXT_HINT);
        }

        // Live preview (name with &* stars resolved)
        int prevY = skinY + 40;
        String nameTxt = nameField.getText();
        ctx.drawTextWithShadow(this.textRenderer, "§7Preview:", p, prevY, TEXT_HINT);
        ctx.drawTextWithShadow(this.textRenderer, fishmod.cosmetic.NickState.parse(nameTxt), fx, prevY, 0xFFFFFFFF);
    }

    private void drawLabel(DrawContext ctx, String s, int x, int y) {
        ctx.drawTextWithShadow(this.textRenderer, "§f" + s + ":", x, y + 3, TEXT_PRIM);
    }

    /** Clickable color/format key. Click a color → inserts its code into the name at the cursor;
     *  the ✪ button inserts "&*". Rebuilds the hit-boxes each frame. */
    private void drawColorKey(DrawContext ctx, int mouseX, int mouseY) {
        keyRects.clear();
        keyCodes.clear();

        ctx.drawTextWithShadow(this.textRenderer,
                "§8&-codes (click to insert) — §7&* §8= ✪ star in the color before it", legendX, legendY - 11, TEXT_HINT);

        int sw = 16, sh = 11, gap = 2;
        // 16 colors, 8 per row
        for (int i = 0; i < CODE_COLORS.length; i++) {
            char code = (char) CODE_COLORS[i][0];
            int rgb = CODE_COLORS[i][1];
            int colX = legendX + (i % 8) * (sw + gap);
            int colY = legendY + (i / 8) * (sh + gap);
            boolean hov = inBox(mouseX, mouseY, colX, colY, sw, sh);
            ctx.fill(colX - 1, colY - 1, colX + sw + 1, colY + sh + 1, hov ? ACCENT : BORDER_LT);
            ctx.fill(colX, colY, colX + sw, colY + sh, 0xFF000000 | rgb);
            int textCol = brightness(rgb) > 140 ? 0xFF000000 : 0xFFFFFFFF;
            ctx.drawCenteredTextWithShadow(this.textRenderer, String.valueOf(code), colX + sw / 2, colY + 2, textCol);
            keyRects.add(new int[]{colX, colY, sw, sh});
            keyCodes.add("&" + code);
        }

        // format codes + star button, on the row to the right of the two color rows
        int fxr = legendX + 8 * (sw + gap) + 8;
        int fyr = legendY;
        for (String[] f : CODE_FORMATS) {
            String sample = f[1];
            int w = this.textRenderer.getWidth(sample) + 6;
            boolean hov = inBox(mouseX, mouseY, fxr, fyr, w, sh);
            ctx.fill(fxr, fyr, fxr + w, fyr + sh, hov ? ROW_HOVER : BG_FIELD);
            ctx.drawTextWithShadow(this.textRenderer, sample, fxr + 3, fyr + 2, 0xFFFFFFFF);
            keyRects.add(new int[]{fxr, fyr, w, sh});
            keyCodes.add("&" + f[0]);
            fxr += w + gap;
        }
        // ✪ star button on the second row under the format codes
        int starX = legendX + 8 * (sw + gap) + 8;
        int starY = legendY + sh + gap;
        int starW = this.textRenderer.getWidth("&* ✪") + 8;
        boolean hovStar = inBox(mouseX, mouseY, starX, starY, starW, sh);
        ctx.fill(starX, starY, starX + starW, starY + sh, hovStar ? ROW_HOVER : BG_FIELD);
        ctx.drawTextWithShadow(this.textRenderer, "§7&* §6✪", starX + 4, starY + 2, 0xFFFFFFFF);
        keyRects.add(new int[]{starX, starY, starW, sh});
        keyCodes.add("&*");
    }

    private static boolean inBox(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int brightness(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    // ── input ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();
        ItemStack sel = inv().getStack(selectedIndex);

        // Dropdowns get first crack (open lists sit on top of everything).
        if (dyeAllowed(sel) && dyeDropdown.click(mx, my)) { closeOthers(dyeDropdown); return true; }
        if (trimMatDropdown.click(mx, my)) { closeOthers(trimMatDropdown); return true; }
        if (trimPatDropdown.click(mx, my)) { closeOthers(trimPatDropdown); return true; }
        if (isHead(sel) && skinDropdown.click(mx, my)) { closeOthers(skinDropdown); return true; }

        // &-code key
        for (int i = 0; i < keyRects.size(); i++) {
            int[] r = keyRects.get(i);
            if (inBox(mx, my, r[0], r[1], r[2], r[3])) { insertIntoName(keyCodes.get(i)); return true; }
        }

        // Pick item — armor row
        int[] armorSlots = {39, 38, 37, 36};
        for (int r = 0; r < 4; r++) {
            int x = armorX + r * CELL;
            if (mx >= x && mx <= x + 16 && my >= armorY && my <= armorY + 16) {
                selectedIndex = armorSlots[r]; loadFields(); return true;
            }
        }
        // Pick item — inventory grid
        for (int i = 0; i < mainCount(); i++) {
            int col = i % 9;
            int row = (i < 9) ? 3 : (i - 9) / 9;
            int x = gridX + col * CELL, y = gridY + row * CELL;
            if (mx >= x && mx <= x + 16 && my >= y && my <= y + 16) {
                selectedIndex = i; loadFields(); return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (dyeDropdown.scrolled(mx, my, verticalAmount)) return true;
        if (trimMatDropdown.scrolled(mx, my, verticalAmount)) return true;
        if (trimPatDropdown.scrolled(mx, my, verticalAmount)) return true;
        if (skinDropdown.scrolled(mx, my, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void closeOthers(Dropdown keep) {
        if (dyeDropdown != keep) dyeDropdown.close();
        if (trimMatDropdown != keep) trimMatDropdown.close();
        if (trimPatDropdown != keep) trimPatDropdown.close();
        if (skinDropdown != keep) skinDropdown.close();
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── lightweight dropdown ─────────────────────────────────────────────────────

    private final class Dropdown {
        final int x, y, w;
        final int boxH = 14, rowH = 13, maxVisible = 7;
        final String placeholder;
        final List<String> labels = new ArrayList<>();
        int[] swatches = null; // optional per-row RGB; null = none
        int selected = -1;
        boolean open = false;
        int scroll = 0;
        Runnable onChange;

        Dropdown(String placeholder, int x, int y, int w) {
            this.placeholder = placeholder; this.x = x; this.y = y; this.w = w;
        }

        void close() { open = false; scroll = 0; }

        private int rowsShown() { return Math.min(maxVisible, labels.size()); }

        void renderClosed(DrawContext ctx, int mx, int my) {
            boolean hov = inBox(mx, my, x, y, w, boxH);
            ctx.fill(x, y, x + w, y + boxH, hov ? BORDER_LT : BORDER);
            ctx.fill(x + 1, y + 1, x + w - 1, y + boxH - 1, BG_FIELD);
            int tx = x + 4;
            if (swatches != null && selected >= 0) {
                ctx.fill(x + 4, y + 3, x + 12, y + 11, 0xFF000000 | swatches[selected]);
                tx = x + 16;
            }
            String label = selected >= 0 ? labels.get(selected) : placeholder;
            int col = selected >= 0 ? TEXT_PRIM : TEXT_HINT;
            ctx.drawTextWithShadow(textRenderer, clip(label, w - (tx - x) - 12), tx, y + 3, col);
            ctx.drawTextWithShadow(textRenderer, "§7▾", x + w - 9, y + 3, TEXT_HINT);
        }

        void renderOpen(DrawContext ctx, int mx, int my) {
            if (!open) return;
            int rows = rowsShown();
            int ly = y + boxH;
            int lh = rows * rowH;
            ctx.fill(x - 1, ly, x + w + 1, ly + lh + 1, BORDER_LT);
            ctx.fill(x, ly, x + w, ly + lh, LIST_BG);
            for (int r = 0; r < rows; r++) {
                int idx = scroll + r;
                if (idx >= labels.size()) break;
                int ry = ly + r * rowH;
                if (inBox(mx, my, x, ry, w, rowH)) ctx.fill(x, ry, x + w, ry + rowH, ROW_HOVER);
                int tx = x + 4;
                if (swatches != null) {
                    ctx.fill(x + 4, ry + 3, x + 12, ry + 11, 0xFF000000 | swatches[idx]);
                    tx = x + 16;
                }
                int col = idx == selected ? ACCENT : TEXT_PRIM;
                ctx.drawTextWithShadow(textRenderer, clip(labels.get(idx), w - (tx - x) - 4), tx, ry + 3, col);
            }
            if (labels.size() > maxVisible) {
                // simple scroll indicator
                int trackY = ly + 1, trackH = lh - 2;
                int thumbH = Math.max(8, trackH * rows / labels.size());
                int max = labels.size() - maxVisible;
                int thumbY = trackY + (max == 0 ? 0 : (trackH - thumbH) * scroll / max);
                ctx.fill(x + w - 3, trackY, x + w - 1, trackY + trackH, 0xFF20242E);
                ctx.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, BORDER_LT);
            }
        }

        /** @return true if this dropdown consumed the click. */
        boolean click(int mx, int my) {
            if (inBox(mx, my, x, y, w, boxH)) { open = !open; if (open) scroll = 0; return true; }
            if (open) {
                int rows = rowsShown();
                int ly = y + boxH;
                if (inBox(mx, my, x, ly, w, rows * rowH)) {
                    int idx = scroll + (my - ly) / rowH;
                    if (idx >= 0 && idx < labels.size()) {
                        selected = idx;
                        open = false;
                        if (onChange != null) onChange.run();
                    }
                    return true;
                }
                open = false; // click outside → close (don't consume)
            }
            return false;
        }

        boolean scrolled(int mx, int my, double amount) {
            if (!open) return false;
            int rows = rowsShown();
            int ly = y + boxH;
            if (inBox(mx, my, x, ly, w, rows * rowH)) {
                int max = Math.max(0, labels.size() - maxVisible);
                scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
                return true;
            }
            return false;
        }

        private String clip(String s, int maxW) {
            if (textRenderer.getWidth(s) <= maxW) return s;
            while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
    }
}

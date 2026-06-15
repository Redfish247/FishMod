package fishmod.features;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/**
 * /fm customize — clearly-sectioned screen: PICK ITEM (armor row + inventory grid),
 * CUSTOMIZE (name, model, stars, dye) with inline labels. Per-item, persistent.
 */
public class ItemCustomizeScreen extends Screen {

    private static final int CELL = 18;
    // section colors
    private static final int BG_PANEL    = 0xCC15171F;
    private static final int BG_SECTION  = 0xFF1B1E27;
    private static final int BORDER      = 0xFF2A2D38;
    private static final int ACCENT      = 0xFF55FFFF;
    private static final int TEXT_PRIM   = 0xFFE8ECF2;
    private static final int TEXT_DIM    = 0xFFAAAAAA;
    private static final int TEXT_HINT   = 0xFF8A8F9C;
    private static final int SLOT_BG     = 0xFF2A2D38;
    private static final int SLOT_SEL    = 0xFF55FF55;

    private int panelX, panelY, panelW = 380, panelH = 392;
    private int gridX, gridY, armorX, armorY;
    private int selectedIndex = 0;
    private int stars = 0;
    private TextFieldWidget nameField, modelField, dyeField, trimMatField, trimPatField;

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

        int p = panelX + 12;
        // PICK ITEM section: armor row left, grid right
        int pickY = panelY + 56;
        armorX = p;
        armorY = pickY + 16;
        gridX  = p + 4 * CELL + 8;
        gridY  = armorY;

        // CUSTOMIZE section fields
        int custY = pickY + 16 + 4 * CELL + 16;
        int labelW = 70;
        int fieldX = p + labelW;
        int fieldW = panelW - 24 - labelW;

        nameField  = new TextFieldWidget(this.textRenderer, fieldX, custY + 14,        fieldW, 16, Text.literal("Name"));
        nameField.setMaxLength(128);
        addDrawableChild(nameField);

        modelField = new TextFieldWidget(this.textRenderer, fieldX, custY + 14 + 36,   fieldW, 16, Text.literal("Model"));
        modelField.setMaxLength(64);
        addDrawableChild(modelField);

        // Stars row: [-] N [+]
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> { stars = Math.max(0, stars - 1); })
                .dimensions(fieldX, custY + 14 + 72, 18, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> { stars = Math.min(10, stars + 1); })
                .dimensions(fieldX + 50, custY + 14 + 72, 18, 16).build());

        dyeField = new TextFieldWidget(this.textRenderer, fieldX, custY + 14 + 108,    80, 16, Text.literal("Dye"));
        dyeField.setMaxLength(6);
        addDrawableChild(dyeField);

        // Trim row: material + pattern side by side (armor only; ids like diamond / sentry)
        trimMatField = new TextFieldWidget(this.textRenderer, fieldX, custY + 14 + 144, 120, 16, Text.literal("Trim Material"));
        trimMatField.setMaxLength(32);
        addDrawableChild(trimMatField);
        trimPatField = new TextFieldWidget(this.textRenderer, fieldX + 126, custY + 14 + 144, 120, 16, Text.literal("Trim Pattern"));
        trimPatField.setMaxLength(32);
        addDrawableChild(trimPatField);

        // Bottom buttons
        int btnY = panelY + panelH - 26;
        int btnW = 70;
        int btnX = panelX + (panelW - btnW * 3 - 12) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), b -> apply())
                .dimensions(btnX, btnY, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> reset())
                .dimensions(btnX + btnW + 6, btnY, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(btnX + (btnW + 6) * 2, btnY, btnW, 20).build());
        // Small "Clear All" button bottom-right to wipe every saved customization at once.
        addDrawableChild(ButtonWidget.builder(Text.literal("§cClear All"), b -> { ItemCustomizer.clearAll(); loadFields(); })
                .dimensions(panelX + panelW - 60 - 8, btnY, 60, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Wipes every saved customization on every item.")))
                .build());

        loadFields();
    }

    private void loadFields() {
        ItemCustomizer.Custom c = ItemCustomizer.get(inv().getStack(selectedIndex));
        nameField.setText(c != null && c.name() != null ? c.name() : "");
        modelField.setText(c != null && c.modelId() != null ? c.modelId() : "");
        dyeField.setText(c != null && c.dye() >= 0 ? String.format("%06X", c.dye() & 0xFFFFFF) : "");
        trimMatField.setText(c != null && c.trimMat() != null ? c.trimMat() : "");
        trimPatField.setText(c != null && c.trimPat() != null ? c.trimPat() : "");
        stars = c != null ? c.stars() : 0;
    }

    private void apply() {
        int dye = -1;
        String d = dyeField.getText().trim();
        if (d.length() == 6) try { dye = (int) Long.parseLong(d, 16); } catch (NumberFormatException ignored) {}
        ItemCustomizer.set(inv().getStack(selectedIndex), nameField.getText(), modelField.getText().trim(), stars, dye,
                trimMatField.getText().trim(), trimPatField.getText().trim());
    }

    private void reset() {
        ItemCustomizer.set(inv().getStack(selectedIndex), "", "", 0, -1, "", "");
        nameField.setText(""); modelField.setText(""); dyeField.setText(""); stars = 0;
        trimMatField.setText(""); trimPatField.setText("");
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

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta); // standard screen dim
        // Draw our panel + content BENEATH the widgets so text fields aren't covered.
        drawPanel(ctx, mouseX, mouseY);
    }

    private void drawPanel(DrawContext ctx, int mouseX, int mouseY) {
        // Outer panel
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);

        // Title bar
        int titleH = 22;
        ctx.fill(panelX, panelY, panelX + panelW, panelY + titleH, BG_SECTION);
        ctx.fill(panelX, panelY + titleH, panelX + panelW, panelY + titleH + 1, ACCENT);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§bItem Customize", panelX + panelW / 2, panelY + 7, 0xFFFFFF);

        // CURRENT readout
        int curY = panelY + titleH + 4;
        ItemStack sel = inv().getStack(selectedIndex);
        ctx.drawTextWithShadow(this.textRenderer, "§7Current:", panelX + 12, curY, TEXT_HINT);
        ctx.drawTextWithShadow(this.textRenderer, sel.getName(), panelX + 12 + 50, curY, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8id: §7" + idOf(sel), panelX + 12, curY + 12, TEXT_HINT);

        // ── PICK ITEM section ──
        int pickY = panelY + 56;
        ctx.drawTextWithShadow(this.textRenderer, "§b▍ §fPICK ITEM  §8(click an armor piece or inventory slot)",
                panelX + 12, pickY, ACCENT);

        // Armor column (helmet → boots, slots 39..36).
        int[] armorSlots = {39, 38, 37, 36};
        String[] armorTags = {"H", "C", "L", "B"};
        for (int r = 0; r < 4; r++) {
            int s = armorSlots[r];
            int x = armorX + r * CELL;
            int y = armorY;
            if (s == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL);
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG);
            if (s < inv().size()) {
                ItemStack a = inv().getStack(s);
                if (!a.isEmpty()) ctx.drawItem(a, x, y);
            }
            ctx.drawTextWithShadow(this.textRenderer, "§8" + armorTags[r], x + 5, y + 18, TEXT_HINT);
        }

        // Main inventory grid (9x4) — hotbar (slots 0-8) goes on the bottom row, vanilla style.
        for (int i = 0; i < mainCount(); i++) {
            int col = i % 9;
            int row = (i < 9) ? 3 : (i - 9) / 9;       // hotbar → row 3, main 9-35 → rows 0..2
            int x = gridX + col * CELL, y = gridY + row * CELL;
            if (i == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL);
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG);
            ItemStack st = inv().getStack(i);
            if (!st.isEmpty()) { ctx.drawItem(st, x, y); ctx.drawStackOverlay(this.textRenderer, st, x, y); }
        }

        // ── CUSTOMIZE section ──
        int custY = pickY + 16 + 4 * CELL + 16;
        ctx.drawTextWithShadow(this.textRenderer, "§b▍ §fCUSTOMIZE", panelX + 12, custY - 12, ACCENT);

        int labelX = panelX + 12;
        ctx.drawTextWithShadow(this.textRenderer, "§fName:",  labelX, custY + 18, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8(& for colors, e.g. §6&6Bling§8)",
                labelX, custY + 30, TEXT_HINT);

        ctx.drawTextWithShadow(this.textRenderer, "§fModel:", labelX, custY + 18 + 36, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8(item id, e.g. §fcooked_beef§8)",
                labelX, custY + 30 + 36, TEXT_HINT);

        ctx.drawTextWithShadow(this.textRenderer, "§fStars:", labelX, custY + 18 + 72, TEXT_PRIM);
        // stars value between [-] and [+]
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§e" + stars,
                panelX + 12 + 70 + 34, custY + 14 + 72 + 4, 0xFFFFFFFF);
        String starHint = stars == 0 ? "§8(0 = none, 1-5 = ✪, 6-10 = master)"
                : stars <= 5 ? "§8(" + stars + " gold star" + (stars > 1 ? "s" : "") + ")"
                : "§8(5 gold + " + (stars - 5) + " master)";
        ctx.drawTextWithShadow(this.textRenderer, starHint, panelX + 12 + 70 + 80, custY + 18 + 72, TEXT_HINT);

        ctx.drawTextWithShadow(this.textRenderer, "§fDye:",   labelX, custY + 18 + 108, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8(hex e.g. §fFF5555§8, leather armor)",
                panelX + 12 + 70 + 90, custY + 18 + 108, TEXT_HINT);

        ctx.drawTextWithShadow(this.textRenderer, "§fTrim:",  labelX, custY + 18 + 144, TEXT_PRIM);
        ctx.drawTextWithShadow(this.textRenderer, "§8(material + pattern, e.g. §fdiamond§8 / §fsentry§8)",
                labelX, custY + 30 + 144, TEXT_HINT);

        // Live preview of name + stars
        String previewBase = nameField.getText().isEmpty() ? sel.getName().getString() : nameField.getText();
        ctx.drawTextWithShadow(this.textRenderer, "§7Preview:", labelX, custY + 182, TEXT_HINT);
        ctx.drawTextWithShadow(this.textRenderer,
                fishmod.cosmetic.NickState.parse(previewBase + ItemCustomizer.starSuffix(stars)),
                labelX + 50, custY + 182, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();
        // Armor row
        int[] armorSlots = {39, 38, 37, 36};
        for (int r = 0; r < 4; r++) {
            int x = armorX + r * CELL;
            if (mx >= x && mx <= x + 16 && my >= armorY && my <= armorY + 16) {
                selectedIndex = armorSlots[r]; loadFields(); return true;
            }
        }
        // Inventory grid (same hotbar-at-bottom mapping as render)
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
    public boolean shouldPause() { return false; }
}

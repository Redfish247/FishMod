package fishmod.features.croesus;

import fishmod.mixin.accessors.HandledScreenAccessor;
import fishmod.mixin.accessors.KeyBindingAccessor;
import fishmod.utils.Location;
import fishmod.utils.SkyblockItems;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Manual loot/profit tracker drawn on top of the player's inventory while in the Dungeon Hub.
 * Type an item name to add a drop row (autocompletes over Skyblock item names); each row has an
 * editable count (click the count cell to type, or use +/-), plus a runs counter, total value,
 * per-run average and a total drop count. The panel can be dragged by its title bar; its position
 * persists in config. Persists rows/runs via {@link LootTrackerStore}. Modeled on the in-inventory
 * text input in {@code fishmod.features.other.SearchBar} and the overlay in {@code SessionStats}.
 */
public final class LootTrackerOverlay {

    // palette (matches FishModScreen slate/teal, square corners)
    private static final int ACCENT  = 0xFF24B6B0;
    private static final int ACCENT2 = 0xFF3AD8D1;
    private static final int BG      = 0xF00C1318;
    private static final int BORDER  = 0xFF24333C;
    private static final int DIVIDER = 0xFF18222C;
    private static final int TEXT    = 0xFFEDF1F5;
    private static final int SUB     = 0xFF7E8A98;
    private static final int GOLD    = 0xFFFFD479;
    private static final int BTN_BG  = 0xFF1B2228;
    private static final int BTN_HOV = 0xFF24333C;

    private static final int MAX_SUGGEST = 6;
    private static final DecimalFormat NUM = new DecimalFormat("#,###");

    // text widgets (lazy, like SearchBar). searchBox = item search, numberBox = active count/runs edit.
    private static TextFieldWidget searchBox;
    private static TextFieldWidget numberBox;
    private static String query = "";
    private static List<String> suggestions = List.of();
    private static boolean dropdownOpen = false;

    // which numeric value numberBox is editing: 0 none, 1 runs, 2 a drop row
    private static int editKind = 0;
    private static String editId = "", editName = "";

    // dragging
    private static boolean dragging = false;
    private static int dragGrabX, dragGrabY;

    // geometry captured each frame for click hit-testing
    private static boolean visible = false;
    private static int panelX, panelY, panelW, panelH;
    private static int titleBarY, titleBarH;
    private static int searchX, searchY, searchW, searchH;
    private static boolean dropVisible = false;
    private static int sugStartY, sugRowH = 11;
    private static int rowMinusX, rowCountX, rowPlusX, rowCountW = 26;
    private static int[] rowY = new int[0];
    private static int runsMinusX, runsCountX, runsPlusX, runsRowY;
    private static int clearX, clearY, clearW, clearH;
    private static int numX, numY, numW, numH; // last-rendered numberBox rect

    // layout constants
    private static final int PAD = 6, BTN = 11, COUNT_H = 12;
    private static final int TITLE_H = 13, SEARCH_H = 14, SEARCH_GAP = 3;
    private static final int ROW_H = 15, DIV_GAP = 4, RUNS_H = 15, LINE_H = 10, CLEAR_H = 15;
    private static final int PANEL_W = 190;

    private LootTrackerOverlay() {}

    // ── gates ────────────────────────────────────────────────────────────────
    private static boolean active() {
        if (!FishSettings.lootTrackerEnabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof InventoryScreen)) return false;
        return Location.getCurrentLocation() == Location.DUNGEON_HUB;
    }

    private static boolean exists() {
        if (searchBox != null) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null || mc.getWindow() == null) return false;
        searchBox = new TextFieldWidget(mc.textRenderer, 0, 0, 100, SEARCH_H, Text.literal(""));
        searchBox.setMaxLength(64);
        searchBox.setChangedListener(s -> {
            query = s.toLowerCase();
            recomputeSuggestions();
            dropdownOpen = !query.isEmpty();
        });
        numberBox = new TextFieldWidget(mc.textRenderer, 0, 0, rowCountW, COUNT_H, Text.literal(""));
        numberBox.setMaxLength(9);
        numberBox.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{1,9}"));
        return true;
    }

    // ── render ───────────────────────────────────────────────────────────────
    public static void renderInScreen(DrawContext ctx, int mx, int my) {
        visible = false;
        if (!active() || !exists()) return;
        CroesusPrices.refreshIfStale(); // fire-and-forget; warms price cache
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        HandledScreenAccessor s = (HandledScreenAccessor) mc.currentScreen;
        int bgX = s.getBgX(), bgY = s.getBgY(), bgW = s.getBgWidth();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        List<LootTrackerStore.Row> rows = LootTrackerStore.rows();
        int runsCount = LootTrackerStore.runs();
        int drawnRows = Math.max(rows.size(), 1);

        dropVisible = searchBox.isFocused() && !query.isEmpty()
                && (!suggestions.isEmpty() || !SkyblockItems.isLoaded());
        int dropRows = dropVisible ? (suggestions.isEmpty() ? 1 : suggestions.size()) : 0;
        int dropH = dropVisible ? dropRows * sugRowH + 2 : 0;

        panelW = PANEL_W;
        panelH = PAD + TITLE_H + SEARCH_H + SEARCH_GAP + dropH
                + drawnRows * ROW_H + DIV_GAP + RUNS_H + LINE_H * 3 + 2 + CLEAR_H + PAD;

        // stop a drag once the mouse button is released
        boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (dragging && !mouseDown) { dragging = false; fishmod.utils.config.FishConfig.manager.save(); }

        // position: dragging > saved position > auto-anchor beside the inventory
        if (dragging) {
            panelX = mx - dragGrabX;
            panelY = my - dragGrabY;
        } else if (FishSettings.lootTrackerX >= 0) {
            panelX = FishSettings.lootTrackerX;
            panelY = FishSettings.lootTrackerY;
        } else {
            panelX = bgX + bgW + 6;
            if (panelX + panelW > screenW) panelX = bgX - panelW - 6;
            panelY = bgY;
        }
        panelX = clamp(panelX, 2, Math.max(2, screenW - panelW - 2));
        panelY = clamp(panelY, 2, Math.max(2, screenH - panelH - 2));
        if (dragging) { FishSettings.lootTrackerX = panelX; FishSettings.lootTrackerY = panelY; }

        // background + frame + top accent
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT);

        int y = panelY + PAD;
        // title bar (drag handle)
        titleBarY = panelY; titleBarH = PAD + 9;
        ctx.drawText(tr, "§l⠿ Loot Tracker", panelX + PAD, y, ACCENT, true);
        y += TITLE_H;

        // search box
        searchX = panelX + PAD; searchY = y; searchW = panelW - PAD * 2; searchH = SEARCH_H;
        searchBox.setX(searchX); searchBox.setY(searchY); searchBox.setWidth(searchW);
        searchBox.render(ctx, mx, my, 0f);
        if (!searchBox.isFocused() && searchBox.getText().isEmpty())
            ctx.drawText(tr, "§8+ add drop…", searchX + 4, searchY + 3, SUB, false);
        y += SEARCH_H + SEARCH_GAP;

        // autocomplete dropdown
        if (dropVisible) {
            sugStartY = y;
            if (suggestions.isEmpty()) {
                ctx.fill(searchX, y, searchX + searchW, y + sugRowH, 0xFF0E151B);
                ctx.drawText(tr, "§8loading items…", searchX + 4, y + 2, SUB, false);
                y += sugRowH;
            } else {
                for (int i = 0; i < suggestions.size(); i++) {
                    boolean hov = hit(mx, my, searchX, y, searchW, sugRowH);
                    ctx.fill(searchX, y, searchX + searchW, y + sugRowH, hov ? 0xFF142028 : 0xFF0E151B);
                    ctx.drawText(tr, tr.trimToWidth(suggestions.get(i), searchW - 6),
                            searchX + 4, y + 2, hov ? ACCENT2 : TEXT, false);
                    y += sugRowH;
                }
            }
            y += 2;
        }

        // drop rows: [-] [count] [+]  Name ............ value
        int x0 = panelX + PAD;
        rowMinusX = x0;
        rowCountX = x0 + BTN + 2;
        rowPlusX  = rowCountX + rowCountW + 2;
        int nameX = rowPlusX + BTN + 4;
        rowY = new int[rows.size()];
        if (rows.isEmpty()) {
            ctx.drawText(tr, "§8no drops yet — type above", x0, y + 4, SUB, true);
            y += ROW_H;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                LootTrackerStore.Row r = rows.get(i);
                int ct = y + 2;            // controls top
                rowY[i] = ct;
                drawMini(ctx, tr, rowMinusX, ct, "-", hit(mx, my, rowMinusX, ct, BTN, BTN));
                drawMini(ctx, tr, rowPlusX, ct, "+", hit(mx, my, rowPlusX, ct, BTN, BTN));
                if (isEditingRow(r)) {
                    renderNumberBox(ctx, mx, my, rowCountX, ct);
                } else {
                    drawCountCell(ctx, tr, rowCountX, ct, String.valueOf(r.count),
                            hit(mx, my, rowCountX, ct, rowCountW, COUNT_H));
                }
                double v = rowValue(r);
                String val = v > 0 ? fmtCoins(v) : "—";
                int vw = tr.getWidth(val);
                int valX = panelX + panelW - PAD - vw;
                ctx.drawText(tr, val, valX, y + 4, v > 0 ? GOLD : SUB, true);
                int maxNameW = Math.max(10, valX - nameX - 4);
                ctx.drawText(tr, tr.trimToWidth(r.name, maxNameW), nameX, y + 4, TEXT, true);
                y += ROW_H;
            }
        }

        // divider
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER);
        y += DIV_GAP;

        // runs row: Runs:  [-] [count] [+]
        runsRowY = y;
        int rct = y + 2;
        ctx.drawText(tr, "§7Runs:", x0, y + 4, TEXT, true);
        runsPlusX  = panelX + panelW - PAD - BTN;
        runsCountX = runsPlusX - 2 - rowCountW;
        runsMinusX = runsCountX - 2 - BTN;
        drawMini(ctx, tr, runsMinusX, rct, "-", hit(mx, my, runsMinusX, rct, BTN, BTN));
        drawMini(ctx, tr, runsPlusX, rct, "+", hit(mx, my, runsPlusX, rct, BTN, BTN));
        if (editKind == 1) renderNumberBox(ctx, mx, my, runsCountX, rct);
        else drawCountCell(ctx, tr, runsCountX, rct, String.valueOf(runsCount),
                hit(mx, my, runsCountX, rct, rowCountW, COUNT_H));
        y += RUNS_H;

        // totals
        int totalDrops = 0;
        for (LootTrackerStore.Row r : rows) totalDrops += r.count;
        double total = totalValue();
        double perRun = total / Math.max(1, runsCount);
        ctx.drawText(tr, "§7Drops: §f" + totalDrops + " §8(" + rows.size() + " types)", x0, y, TEXT, true);
        y += LINE_H;
        ctx.drawText(tr, "§7Total: §6" + fmtCoins(total), x0, y, TEXT, true);
        y += LINE_H;
        ctx.drawText(tr, "§7Per run: §6" + fmtCoins(perRun), x0, y, TEXT, true);
        y += LINE_H + 2;

        // clear button
        clearX = x0; clearY = y; clearW = panelW - PAD * 2; clearH = CLEAR_H;
        boolean ch = hit(mx, my, clearX, clearY, clearW, clearH);
        ctx.fill(clearX, clearY, clearX + clearW, clearY + clearH, ch ? 0xFF3A1414 : BTN_BG);
        String cl = "§l[ Clear ]";
        int clw = tr.getWidth(cl);
        ctx.drawText(tr, ch ? "§c§l[ Clear ]" : cl, clearX + (clearW - clw) / 2,
                clearY + (clearH - 8) / 2, ch ? 0xFFFF6B6B : TEXT, true);

        visible = true;
    }

    private static void renderNumberBox(DrawContext ctx, int mx, int my, int x, int y) {
        numberBox.setX(x); numberBox.setY(y); numberBox.setWidth(rowCountW);
        numberBox.render(ctx, mx, my, 0f);
        numX = x; numY = y; numW = rowCountW; numH = COUNT_H;
    }

    private static void drawCountCell(DrawContext ctx, TextRenderer tr, int x, int y, String text, boolean hov) {
        ctx.fill(x, y, x + rowCountW, y + COUNT_H, BORDER);
        ctx.fill(x + 1, y + 1, x + rowCountW - 1, y + COUNT_H - 1, hov ? BTN_HOV : BTN_BG);
        int tw = tr.getWidth(text);
        ctx.drawText(tr, text, x + (rowCountW - tw) / 2, y + (COUNT_H - 8) / 2, hov ? ACCENT2 : TEXT, false);
    }

    private static void drawMini(DrawContext ctx, TextRenderer tr, int x, int y, String glyph, boolean hov) {
        ctx.fill(x, y, x + BTN, y + BTN, hov ? BTN_HOV : BTN_BG);
        int gw = tr.getWidth(glyph);
        ctx.drawText(tr, glyph, x + (BTN - gw) / 2, y + (BTN - 8) / 2, hov ? ACCENT2 : TEXT, false);
    }

    // ── click ────────────────────────────────────────────────────────────────
    public static boolean handleScreenClick(double mx, double my) {
        if (!visible) return false;

        // commit a pending number edit if the click is outside the number box
        if (editKind != 0) {
            if (hit(mx, my, numX, numY, numW, numH)) return true; // keep editing
            commitNumber();
        }

        // title bar -> start dragging
        if (hit(mx, my, panelX, titleBarY, panelW, titleBarH)) {
            dragging = true;
            dragGrabX = (int) mx - panelX;
            dragGrabY = (int) my - panelY;
            return true;
        }

        // autocomplete suggestions
        if (dropVisible && !suggestions.isEmpty()) {
            for (int i = 0; i < suggestions.size(); i++) {
                if (hit(mx, my, searchX, sugStartY + i * sugRowH, searchW, sugRowH)) {
                    addRowFromSuggestion(suggestions.get(i));
                    searchBox.setText(""); query = ""; suggestions = List.of();
                    dropdownOpen = false; searchBox.setFocused(false);
                    return true;
                }
            }
        }
        // search box -> focus + consume
        if (hit(mx, my, searchX, searchY, searchW, searchH)) { searchBox.setFocused(true); return true; }

        // runs controls
        if (hit(mx, my, runsMinusX, runsRowY + 2, BTN, BTN)) { LootTrackerStore.setRuns(LootTrackerStore.runs() - 1); return true; }
        if (hit(mx, my, runsPlusX, runsRowY + 2, BTN, BTN)) { LootTrackerStore.setRuns(LootTrackerStore.runs() + 1); return true; }
        if (hit(mx, my, runsCountX, runsRowY + 2, rowCountW, COUNT_H)) { openNumberEditor(1, "", "", LootTrackerStore.runs()); return true; }

        // per-row controls
        List<LootTrackerStore.Row> rows = LootTrackerStore.rows();
        for (int i = 0; i < rowY.length && i < rows.size(); i++) {
            LootTrackerStore.Row r = rows.get(i);
            if (hit(mx, my, rowMinusX, rowY[i], BTN, BTN)) { LootTrackerStore.addOrIncrement(r.name, r.id, -1); return true; }
            if (hit(mx, my, rowPlusX, rowY[i], BTN, BTN)) { LootTrackerStore.addOrIncrement(r.name, r.id, +1); return true; }
            if (hit(mx, my, rowCountX, rowY[i], rowCountW, COUNT_H)) { openNumberEditor(2, r.id, r.name, r.count); return true; }
        }

        // clear
        if (hit(mx, my, clearX, clearY, clearW, clearH)) { LootTrackerStore.clear(); return true; }
        // anywhere else inside the panel -> unfocus + consume
        if (hit(mx, my, panelX, panelY, panelW, panelH)) { searchBox.setFocused(false); return true; }
        // outside -> release focus, let the click reach the inventory
        searchBox.setFocused(false);
        return false;
    }

    // ── keyboard (mirrors SearchBar; routes to whichever field is focused) ─────
    public static boolean keyPressed(KeyInput input) {
        if (!active() || !exists()) return false;
        TextFieldWidget f = focusedField();
        if (f == null) return false;
        // never eat the drop key — fall through
        try {
            int dropCode = ((KeyBindingAccessor) (Object) MinecraftClient.getInstance().options.dropKey)
                    .getBoundKey().getCode();
            if (input.key() == dropCode) {
                if (f == numberBox) commitNumber(); else f.setFocused(false);
                return false;
            }
        } catch (Exception ignored) {}
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (f == numberBox) cancelNumber(); else f.setFocused(false);
            return false;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (f == searchBox) {
                if (!suggestions.isEmpty()) { addRowFromSuggestion(suggestions.get(0)); searchBox.setText(""); }
            } else if (f == numberBox) {
                commitNumber();
            }
            return true;
        }
        f.keyPressed(input);
        return true; // consume -> mixin returns false -> no inventory close / hotbar swap
    }

    public static void charTyped(CharInput input) {
        if (!active() || !exists()) return;
        TextFieldWidget f = focusedField();
        if (f != null) f.charTyped(input);
    }

    private static TextFieldWidget focusedField() {
        if (numberBox != null && numberBox.isFocused()) return numberBox;
        if (searchBox != null && searchBox.isFocused()) return searchBox;
        return null;
    }

    // ── number editor ──────────────────────────────────────────────────────────
    private static void openNumberEditor(int kind, String id, String name, int current) {
        editKind = kind;
        editId = id == null ? "" : id;
        editName = name == null ? "" : name;
        numberBox.setText(String.valueOf(current));
        numberBox.setFocused(true);
        searchBox.setFocused(false);
    }

    private static void commitNumber() {
        if (editKind == 0) return;
        String t = numberBox.getText().trim();
        try {
            int n = t.isEmpty() ? 0 : Integer.parseInt(t);
            if (editKind == 1) LootTrackerStore.setRuns(n);
            else if (editKind == 2) LootTrackerStore.setCount(editName, editId, n);
        } catch (NumberFormatException ignored) {}
        editKind = 0;
        numberBox.setFocused(false);
    }

    private static void cancelNumber() {
        editKind = 0;
        numberBox.setFocused(false);
    }

    private static boolean isEditingRow(LootTrackerStore.Row r) {
        if (editKind != 2) return false;
        return (editId != null && !editId.isEmpty()) ? editId.equals(r.id) : editName.equalsIgnoreCase(r.name);
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private static void recomputeSuggestions() {
        suggestions = query.isEmpty() ? List.of() : SkyblockItems.searchNames(query, MAX_SUGGEST);
    }

    private static void addRowFromSuggestion(String displayName) {
        String id = SkyblockItems.idFor(displayName);
        LootTrackerStore.addOrIncrement(displayName, id, +1);
        CroesusPrices.refreshIfStale();
    }

    private static double rowValue(LootTrackerStore.Row r) {
        if (r.id == null || r.id.isEmpty()) return 0;
        return CroesusPrices.price(r.id) * r.count;
    }

    private static double totalValue() {
        double sum = 0;
        for (LootTrackerStore.Row r : LootTrackerStore.rows()) sum += rowValue(r);
        return sum;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String fmtCoins(double v) {
        if (v < 0) return "—";
        if (v == 0) return "0";
        if (v >= 1_000_000_000d) return String.format("%.2fB", v / 1_000_000_000d);
        if (v >= 1_000_000d)     return String.format("%.2fM", v / 1_000_000d);
        if (v >= 1_000d)         return String.format("%.1fk", v / 1_000d);
        return NUM.format(v);
    }

    // public helpers for the .dprofit party command
    public static double totalValueForChat() { return totalValue(); }
    public static int runsForChat() { return LootTrackerStore.runs(); }
    public static String fmtCoinsPublic(double v) { return fmtCoins(v); }
}

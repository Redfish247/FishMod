package fishmod.features.croesus;

import fishmod.mixin.accessors.HandledScreenAccessor;
import fishmod.mixin.accessors.KeyBindingAccessor;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Loot/profit tracker drawn on top of the player's inventory while in the Dungeon Hub. Rows are
 * populated automatically by {@link CroesusLootDetector} from real Croesus chest opens (no typing
 * required); each row's count can still be nudged with +/- or edited directly (click the count
 * cell to type a correction), alongside a runs counter (also auto-incremented, but still
 * manually adjustable the same way), total value, per-run average and a total drop count. The
 * panel can be dragged by its title bar; its position persists in config. Persists rows/runs via
 * {@link LootTrackerStore}. Modeled on the overlay in {@code SessionStats}.
 */
public final class LootTrackerOverlay {

    // palette (matches FishModScreen slate/teal, square corners)
    private static final int ACCENT  = 0xFF24B6B0;
    private static final int ACCENT2 = 0xFF3AD8D1;
    private static final int BG      = 0xF00C1318;
    private static final int PANEL2  = 0xF0101923; // slightly lighter than BG, for the stats footer
    private static final int ROW_ALT = 0x14FFFFFF; // faint stripe on odd rows
    private static final int BORDER  = 0xFF24333C;
    private static final int DIVIDER = 0xFF18222C;
    private static final int TEXT    = 0xFFEDF1F5;
    private static final int SUB     = 0xFF7E8A98;
    private static final int GOLD    = 0xFFFFD479;
    private static final int BTN_BG  = 0xFF1B2228;
    private static final int BTN_HOV = 0xFF24333C;
    private static final int CLEAR_BG = 0xFF1B1414;
    private static final int CLEAR_HOV = 0xFF3A1414;

    private static final DecimalFormat NUM = new DecimalFormat("#,###");

    // text widget (lazy, like SearchBar). numberBox = active count/runs edit.
    private static EditBox numberBox;
    private static boolean numberBoxFiltering = false;

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
    private static int rowMinusX, rowCountX, rowPlusX, rowCountW = 26;
    private static int[] rowY = new int[0];
    private static int runsMinusX, runsCountX, runsPlusX, runsRowY;
    private static int clearX, clearY, clearW, clearH;
    private static int numX, numY, numW, numH; // last-rendered numberBox rect

    // layout constants
    private static final int PAD = 7, BTN = 12, COUNT_H = 13;
    private static final int TITLE_H = 16;
    private static final int ROW_H = 16, DIV_GAP = 6, RUNS_H = 17, LINE_H = 11, CLEAR_H = 16;
    private static final int STATS_PAD = 5;
    private static final int PANEL_W = 200;

    private LootTrackerOverlay() {}

    // ── gates ────────────────────────────────────────────────────────────────
    private static boolean active() {
        if (!FishSettings.lootTrackerEnabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof InventoryScreen)) return false;
        return Location.getCurrentLocation() == Location.DUNGEON_HUB;
    }

    private static boolean exists() {
        if (numberBox != null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.getWindow() == null) return false;
        numberBox = new EditBox(mc.font, 0, 0, rowCountW, COUNT_H, Component.literal(""));
        numberBox.setMaxLength(9);
        // EditBox lost setFilter(Predicate<String>) in 26.2 — enforce digits-only reactively instead.
        numberBox.setResponder(s -> {
            if (numberBoxFiltering || s.isEmpty() || s.matches("\\d{1,9}")) return;
            numberBoxFiltering = true;
            numberBox.setValue(s.replaceAll("[^\\d]", ""));
            numberBoxFiltering = false;
        });
        return true;
    }

    // ── render ───────────────────────────────────────────────────────────────
    public static void renderInScreen(GuiGraphicsExtractor ctx, int mx, int my) {
        visible = false;
        if (!active() || !exists()) return;
        CroesusPrices.refreshIfStale(); // fire-and-forget; warms price cache
        Minecraft mc = Minecraft.getInstance();
        Font tr = mc.font;
        HandledScreenAccessor s = (HandledScreenAccessor) mc.screen;
        int bgX = s.getBgX(), bgY = s.getBgY(), bgW = s.getBgWidth();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        List<LootTrackerStore.Row> rows = LootTrackerStore.rows();
        int runsCount = LootTrackerStore.runs();
        int drawnRows = Math.max(rows.size(), 1);

        panelW = PANEL_W;
        panelH = PAD + TITLE_H + 2
                + drawnRows * ROW_H + DIV_GAP + RUNS_H + DIV_GAP
                + STATS_PAD * 2 + LINE_H * 3 + STATS_PAD + CLEAR_H + PAD;

        // stop a drag once the mouse button is released
        boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().handle(),
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
        titleBarY = panelY; titleBarH = PAD + TITLE_H - 3;
        ctx.text(tr, "§l⠿ Loot Tracker", panelX + PAD, y + 2, ACCENT, true);
        String hdrCount = rows.size() + " types";
        int hdrW = tr.width(hdrCount);
        ctx.text(tr, "§8" + hdrCount, panelX + panelW - PAD - hdrW, y + 2, SUB, true);
        y += TITLE_H;
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER);
        y += 2;

        // drop rows: [-] [count] [+]  Name ............ value
        int x0 = panelX + PAD;
        rowMinusX = x0;
        rowCountX = x0 + BTN + 3;
        rowPlusX  = rowCountX + rowCountW + 3;
        int nameX = rowPlusX + BTN + 5;
        rowY = new int[rows.size()];
        if (rows.isEmpty()) {
            ctx.text(tr, "§8no drops yet — open a Croesus chest", x0, y + 4, SUB, true);
            y += ROW_H;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                LootTrackerStore.Row r = rows.get(i);
                if ((i & 1) == 1) ctx.fill(panelX + 1, y, panelX + panelW - 1, y + ROW_H, ROW_ALT);
                int ct = y + (ROW_H - BTN) / 2;   // controls top, vertically centered in the row
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
                int vw = tr.width(val);
                int valX = panelX + panelW - PAD - vw;
                int textY = y + (ROW_H - 8) / 2;
                ctx.text(tr, val, valX, textY, v > 0 ? GOLD : SUB, true);
                int maxNameW = Math.max(10, valX - nameX - 4);
                ctx.text(tr, tr.plainSubstrByWidth(r.name, maxNameW), nameX, textY, TEXT, true);
                y += ROW_H;
            }
        }

        // divider
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER);
        y += DIV_GAP;

        // runs row: Runs:  [-] [count] [+]
        runsRowY = y;
        int rct = y + (RUNS_H - BTN) / 2;
        ctx.text(tr, "§7Runs", x0, y + (RUNS_H - 8) / 2, TEXT, true);
        runsPlusX  = panelX + panelW - PAD - BTN;
        runsCountX = runsPlusX - 3 - rowCountW;
        runsMinusX = runsCountX - 3 - BTN;
        drawMini(ctx, tr, runsMinusX, rct, "-", hit(mx, my, runsMinusX, rct, BTN, BTN));
        drawMini(ctx, tr, runsPlusX, rct, "+", hit(mx, my, runsPlusX, rct, BTN, BTN));
        if (editKind == 1) renderNumberBox(ctx, mx, my, runsCountX, rct);
        else drawCountCell(ctx, tr, runsCountX, rct, String.valueOf(runsCount),
                hit(mx, my, runsCountX, rct, rowCountW, COUNT_H));
        y += RUNS_H;
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER);
        y += DIV_GAP;

        // stats footer — its own shaded card so totals read as a distinct block from the rows
        int totalDrops = 0;
        for (LootTrackerStore.Row r : rows) totalDrops += r.count;
        double total = totalValue();
        double perRun = total / Math.max(1, runsCount);
        int statsH = STATS_PAD * 2 + LINE_H * 3;
        ctx.fill(panelX + 1, y, panelX + panelW - 1, y + statsH, PANEL2);
        y += STATS_PAD;
        ctx.text(tr, "§7Drops  §f" + totalDrops + " §8· " + rows.size() + " types", x0, y, TEXT, true);
        y += LINE_H;
        ctx.text(tr, "§7Total  §6" + fmtCoins(total), x0, y, TEXT, true);
        y += LINE_H;
        ctx.text(tr, "§7Per run  §6" + fmtCoins(perRun), x0, y, TEXT, true);
        y += LINE_H + STATS_PAD;

        // clear button
        clearX = x0; clearY = y; clearW = panelW - PAD * 2; clearH = CLEAR_H;
        boolean ch = hit(mx, my, clearX, clearY, clearW, clearH);
        ctx.fill(clearX, clearY, clearX + clearW, clearY + clearH, ch ? CLEAR_HOV : CLEAR_BG);
        String cl = "Clear";
        int clw = tr.width(cl);
        ctx.text(tr, ch ? "§c" + cl : "§7" + cl, clearX + (clearW - clw) / 2,
                clearY + (clearH - 8) / 2, ch ? 0xFFFF6B6B : SUB, true);

        visible = true;
    }

    private static void renderNumberBox(GuiGraphicsExtractor ctx, int mx, int my, int x, int y) {
        numberBox.setX(x); numberBox.setY(y); numberBox.setWidth(rowCountW);
        numberBox.extractRenderState(ctx, mx, my, 0f);
        numX = x; numY = y; numW = rowCountW; numH = COUNT_H;
    }

    private static void drawCountCell(GuiGraphicsExtractor ctx, Font tr, int x, int y, String text, boolean hov) {
        ctx.fill(x, y, x + rowCountW, y + COUNT_H, BORDER);
        ctx.fill(x + 1, y + 1, x + rowCountW - 1, y + COUNT_H - 1, hov ? BTN_HOV : BTN_BG);
        int tw = tr.width(text);
        ctx.text(tr, text, x + (rowCountW - tw) / 2, y + (COUNT_H - 8) / 2, hov ? ACCENT2 : TEXT, false);
    }

    private static void drawMini(GuiGraphicsExtractor ctx, Font tr, int x, int y, String glyph, boolean hov) {
        ctx.fill(x, y, x + BTN, y + BTN, BORDER);
        ctx.fill(x + 1, y + 1, x + BTN - 1, y + BTN - 1, hov ? BTN_HOV : BTN_BG);
        int gw = tr.width(glyph);
        ctx.text(tr, glyph, x + (BTN - gw) / 2, y + (BTN - 8) / 2, hov ? ACCENT2 : SUB, false);
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
        // anywhere else inside the panel -> consume
        if (hit(mx, my, panelX, panelY, panelW, panelH)) return true;
        // outside -> let the click reach the inventory
        return false;
    }

    // ── keyboard (mirrors SearchBar; routes to the number box when it's focused) ─────
    public static boolean keyPressed(KeyEvent input) {
        if (!active() || !exists()) return false;
        EditBox f = focusedField();
        if (f == null) return false;
        // never eat the drop key — fall through
        try {
            int dropCode = ((KeyBindingAccessor) (Object) Minecraft.getInstance().options.keyDrop)
                    .getBoundKey().getValue();
            if (input.key() == dropCode) {
                commitNumber();
                return false;
            }
        } catch (Exception ignored) {}
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancelNumber();
            return false;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitNumber();
            return true;
        }
        f.keyPressed(input);
        return true; // consume -> mixin returns false -> no inventory close / hotbar swap
    }

    public static void charTyped(CharacterEvent input) {
        if (!active() || !exists()) return;
        EditBox f = focusedField();
        if (f != null) f.charTyped(input);
    }

    private static EditBox focusedField() {
        if (numberBox != null && numberBox.isFocused()) return numberBox;
        return null;
    }

    // ── number editor ──────────────────────────────────────────────────────────
    private static void openNumberEditor(int kind, String id, String name, int current) {
        editKind = kind;
        editId = id == null ? "" : id;
        editName = name == null ? "" : name;
        numberBox.setValue(String.valueOf(current));
        numberBox.setFocused(true);
    }

    private static void commitNumber() {
        if (editKind == 0) return;
        String t = numberBox.getValue().trim();
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

package fishmod.features.croesus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-page /fmloot screen — a real dedicated page (fills the window, like the main FishMod
 * config screen) instead of a small centered card. Adds a search bar to filter tracked drops
 * by name. Rows are populated automatically by {@link CroesusLootDetector} from real Croesus
 * chest opens; everything here is just the view.
 */
public class LootTrackerScreen extends Screen {

    // palette — dark slate with a teal accent, matches the rest of FishMod's screens
    private static final int BG_TOP     = 0xEE0A0E12;
    private static final int BG_BOT     = 0xF2050709;
    private static final int PANEL_BG   = 0xFF11161C;
    private static final int PANEL_BORDER = 0xFF232D36;
    private static final int TILE_BG    = 0xFF161D24;
    private static final int TILE_BORDER = 0xFF232D36;
    private static final int ROW_BG     = 0xFF14191F;
    private static final int ROW_BG_ALT = 0xFF171D24;
    private static final int ROW_HOVER  = 0xFF1E262E;
    private static final int ACCENT     = 0xFF2FD1C4;
    private static final int TEXT       = 0xFFEFF4F7;
    private static final int SUBTEXT    = 0xFF8A97A3;
    private static final int GOLD       = 0xFFFFD479;
    private static final int DANGER     = 0xFFE0574B;
    private static final int DANGER_BG  = 0xFF241213;
    private static final int DANGER_HOV = 0xFF3A1517;
    private static final int BTN_BG     = 0xFF1B2229;
    private static final int BTN_HOV    = 0xFF25313A;
    private static final int SEARCH_BG  = 0xFF161D24;
    private static final int SEARCH_BG_FOCUS = 0xFF1B2530;

    private static final DecimalFormat NUM = new DecimalFormat("#,###");

    private static final int MARGIN = 20;
    private static final int HEADER_H = 40;
    private static final int STAT_H = 50;
    private static final int SEARCH_H = 24;
    private static final int ROW_H = 24;
    private static final int FOOTER_H = 26;
    private static final int GAP = 10;
    private static final int PAD = 14;

    // computed each frame
    private int contentX0, contentX1, contentY0, contentY1;
    private int listTop, listH, listX0, listX1;
    private int scroll = 0;
    private int curMx, curMy;

    // click hit-rects captured each frame
    private int closeX, closeY, closeS;
    private int clearX, clearY, clearW, clearH;
    private int runsY, runsTileX;
    private int[] rowY = new int[0];
    private int rowCountX, rowCountW = 30, rowCountH = 16;
    private int searchX, searchY, searchW, searchH;

    private boolean clearArmed = false;
    private long clearArmedAt = 0;

    private EditBox editBox;
    private boolean editBoxFiltering = false;
    private int editKind = 0; // 0 none, 1 runs, 2 row
    private String editId = "", editName = "";

    private EditBox searchField;

    public LootTrackerScreen() {
        super(Component.literal("Loot Tracker"));
    }

    @Override
    protected void init() {
        CroesusPrices.refreshIfStale();
        editBox = new EditBox(this.font, 0, 0, rowCountW, rowCountH, Component.literal(""));
        editBox.setMaxLength(9);
        editBox.setResponder(s -> {
            if (editBoxFiltering || s.isEmpty() || s.matches("\\d{1,9}")) return;
            editBoxFiltering = true;
            editBox.setValue(s.replaceAll("[^\\d]", ""));
            editBoxFiltering = false;
        });

        searchField = new EditBox(this.font, 0, 0, 100, SEARCH_H - 8, Component.literal(""));
        searchField.setMaxLength(64);
        searchField.setBordered(false);
        searchField.setResponder(s -> scroll = 0);
    }

    @Override public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d) { }
    @Override public void extractTransparentBackground(GuiGraphicsExtractor ctx) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        curMx = mouseX; curMy = mouseY;
        ctx.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOT);

        contentX0 = MARGIN;
        contentX1 = this.width - MARGIN;
        contentY0 = MARGIN;
        contentY1 = this.height - MARGIN;

        ctx.fill(contentX0 - 1, contentY0 - 1, contentX1 + 1, contentY1 + 1, PANEL_BORDER);
        ctx.fill(contentX0, contentY0, contentX1, contentY1, PANEL_BG);
        ctx.fill(contentX0, contentY0, contentX1, contentY0 + 3, ACCENT);

        List<LootTrackerStore.Row> allRows = LootTrackerStore.rows();
        int runs = LootTrackerStore.runs();
        List<LootTrackerStore.Row> rows = filterRows(allRows);

        renderHeader(ctx);
        int statBottom = renderStats(ctx, allRows, runs);
        int searchBottom = renderSearch(ctx, statBottom + GAP);

        listTop = searchBottom + GAP;
        listX0 = contentX0 + PAD;
        listX1 = contentX1 - PAD;
        listH = (contentY1 - PAD - FOOTER_H) - listTop;
        renderList(ctx, rows);
        renderFooter(ctx, allRows, rows);

        if (clearArmed && System.currentTimeMillis() - clearArmedAt > 3000) clearArmed = false;

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private List<LootTrackerStore.Row> filterRows(List<LootTrackerStore.Row> rows) {
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase();
        if (q.isEmpty()) return rows;
        List<LootTrackerStore.Row> out = new ArrayList<>();
        for (LootTrackerStore.Row r : rows) {
            if (r.name != null && r.name.toLowerCase().contains(q)) out.add(r);
        }
        return out;
    }

    private void renderHeader(GuiGraphicsExtractor ctx) {
        int y = contentY0 + PAD;
        ctx.text(this.font, "§l§bLoot Tracker", contentX0 + PAD, y, TEXT, true);
        ctx.text(this.font, "§8Auto-tracked from Croesus chests", contentX0 + PAD, y + 11, SUBTEXT, true);

        closeS = 18;
        closeX = contentX1 - PAD - closeS;
        closeY = contentY0 + PAD - 3;
        boolean hov = hit(curMx, curMy, closeX, closeY, closeS, closeS);
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS, hov ? BTN_HOV : BTN_BG);
        ctx.centeredText(this.font, Component.literal("§7x"), closeX + closeS / 2, closeY + 5, hov ? 0xFFFFFFFF : SUBTEXT);
    }

    private int renderStats(GuiGraphicsExtractor ctx, List<LootTrackerStore.Row> rows, int runs) {
        int y = contentY0 + HEADER_H;
        int totalDrops = 0;
        double total = 0;
        for (LootTrackerStore.Row r : rows) {
            totalDrops += r.count;
            total += rowValue(r);
        }
        double perRun = total / Math.max(1, runs);

        int usableW = contentX1 - contentX0 - PAD * 2;
        int tileW = (usableW - 6 * 3) / 4;
        int x = contentX0 + PAD;
        statTile(ctx, x, y, tileW, "TOTAL", fmtCoins(total), GOLD); x += tileW + 6;
        statTile(ctx, x, y, tileW, "PER RUN", fmtCoins(perRun), GOLD); x += tileW + 6;
        statTile(ctx, x, y, tileW, "DROPS", String.valueOf(totalDrops), TEXT); x += tileW + 6;

        // runs tile — click the value to edit it directly (no +/- steppers)
        runsY = y;
        runsTileX = x;
        int rx = x;
        boolean rhov = hit(curMx, curMy, rx, y, tileW, STAT_H - 6);
        ctx.fill(rx, y, rx + tileW, y + STAT_H - 6, rhov ? BTN_HOV : TILE_BG);
        ctx.fill(rx, y, rx + tileW, y + 1, TILE_BORDER);
        ctx.text(this.font, "§8RUNS", rx + 6, y + 5, SUBTEXT, false);
        if (editKind == 1) {
            renderEditBox(ctx, rx + 6, y + 17);
        } else {
            String rs = String.valueOf(runs);
            ctx.text(this.font, "§f" + rs, rx + 6, y + 18, TEXT, false);
        }

        return y + STAT_H;
    }

    private void statTile(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String value, int color) {
        ctx.fill(x, y, x + w, y + STAT_H - 6, TILE_BG);
        ctx.fill(x, y, x + w, y + 1, TILE_BORDER);
        ctx.text(this.font, "§8" + label, x + 6, y + 5, SUBTEXT, false);
        String v = this.font.plainSubstrByWidth(value, w - 10);
        ctx.text(this.font, v, x + 6, y + 18, color, false);
    }

    private int renderSearch(GuiGraphicsExtractor ctx, int y) {
        searchX = contentX0 + PAD;
        searchY = y;
        searchW = contentX1 - contentX0 - PAD * 2;
        searchH = SEARCH_H;

        boolean focused = searchField != null && searchField.isFocused();
        ctx.fill(searchX, searchY, searchX + searchW, searchY + searchH, focused ? SEARCH_BG_FOCUS : SEARCH_BG);
        ctx.fill(searchX, searchY, searchX + searchW, searchY + 1, focused ? ACCENT : TILE_BORDER);

        ctx.text(this.font, "§8🔍", searchX + 6, searchY + (searchH - 8) / 2, SUBTEXT, false);

        if (searchField != null) {
            searchField.setX(searchX + 16);
            searchField.setY(searchY + (searchH - (searchH - 8)) / 2);
            searchField.setWidth(searchW - 22);
            searchField.extractRenderState(ctx, curMx, curMy, 0f);
            if (searchField.getValue().isEmpty() && !searchField.isFocused()) {
                ctx.text(this.font, "§8Search drops...", searchX + 16 + 2, searchY + (searchH - 8) / 2, SUBTEXT, false);
            }
        }

        return searchY + searchH;
    }

    private void renderList(GuiGraphicsExtractor ctx, List<LootTrackerStore.Row> rows) {
        int x0 = listX0;
        int x1 = listX1;
        ctx.enableScissor(x0, listTop, x1, listTop + listH);

        boolean searching = searchField != null && !searchField.getValue().trim().isEmpty();

        if (rows.isEmpty()) {
            String msg = searching ? "§8no drops match your search" : "§8no drops tracked yet — open a Croesus chest";
            ctx.text(this.font, msg, x0, listTop + 6, SUBTEXT, true);
            rowY = new int[0];
        } else {
            int maxScroll = Math.max(0, rows.size() * ROW_H - listH);
            scroll = Mth.clamp(scroll, 0, maxScroll);

            rowY = new int[rows.size()];

            int y = listTop - scroll;
            rowCountX = x0 + 4;
            int nameX = rowCountX + rowCountW + 8;

            for (int i = 0; i < rows.size(); i++) {
                LootTrackerStore.Row r = rows.get(i);
                int rowTop = y + i * ROW_H;
                if (rowTop + ROW_H < listTop || rowTop > listTop + listH) continue;

                boolean hov = hit(curMx, curMy, x0, rowTop, x1 - x0, ROW_H);
                int bg = hov ? ROW_HOVER : ((i & 1) == 1 ? ROW_BG_ALT : ROW_BG);
                ctx.fill(x0, rowTop, x1, rowTop + ROW_H - 1, bg);

                rowY[i] = rowTop;

                int countY = rowTop + (ROW_H - rowCountH) / 2;
                if (isEditingRow(r)) {
                    renderEditBox(ctx, rowCountX, countY);
                } else {
                    boolean chov = hit(curMx, curMy, rowCountX, countY, rowCountW, rowCountH);
                    ctx.fill(rowCountX, countY, rowCountX + rowCountW, countY + rowCountH, chov ? BTN_HOV : BTN_BG);
                    String cs = String.valueOf(r.count);
                    int cw = this.font.width(cs);
                    ctx.text(this.font, cs, rowCountX + (rowCountW - cw) / 2, countY + 4, chov ? ACCENT : TEXT, false);
                }

                double v = rowValue(r);
                String val = v > 0 ? fmtCoins(v) : "—";
                int vw = this.font.width(val);
                int valX = x1 - 8 - vw;
                int textY = rowTop + (ROW_H - 8) / 2;
                ctx.text(this.font, val, valX, textY, v > 0 ? GOLD : SUBTEXT, false);
                int maxNameW = Math.max(10, valX - nameX - 6);
                ctx.text(this.font, this.font.plainSubstrByWidth(r.name, maxNameW), nameX, textY, TEXT, false);
            }

            if (maxScroll > 0) {
                int barX = x1 - 2;
                int trackH = listH;
                int barH = Math.max(10, trackH * listH / (rows.size() * ROW_H));
                int barY = listTop + (trackH - barH) * scroll / Math.max(1, maxScroll);
                ctx.fill(barX, listTop, barX + 2, listTop + trackH, 0x33FFFFFF);
                ctx.fill(barX, barY, barX + 2, barY + barH, ACCENT);
            }
        }

        ctx.disableScissor();
    }

    private void renderFooter(GuiGraphicsExtractor ctx, List<LootTrackerStore.Row> allRows, List<LootTrackerStore.Row> shownRows) {
        int y = contentY1 - PAD - FOOTER_H + 8;

        int clearW0 = 140;
        clearX = contentX0 + PAD; clearY = y; clearW = clearW0; clearH = FOOTER_H - 8;
        boolean hov = hit(curMx, curMy, clearX, clearY, clearW, clearH);
        ctx.fill(clearX, clearY, clearX + clearW, clearY + clearH, hov ? DANGER_HOV : DANGER_BG);
        String label = clearArmed ? "Click again to confirm" : "Clear All";
        int lw = this.font.width(label);
        ctx.text(this.font, label, clearX + (clearW - lw) / 2, clearY + (clearH - 8) / 2, DANGER, false);

        if (shownRows.size() != allRows.size()) {
            String info = shownRows.size() + " / " + allRows.size() + " drops shown";
            int iw = this.font.width(info);
            ctx.text(this.font, "§8" + info, contentX1 - PAD - iw, y + (clearH - 8) / 2, SUBTEXT, false);
        }
    }

    private void renderEditBox(GuiGraphicsExtractor ctx, int x, int y) {
        editBox.setX(x); editBox.setY(y); editBox.setWidth(rowCountW);
        editBox.extractRenderState(ctx, curMx, curMy, 0f);
    }

    private void drawMini(GuiGraphicsExtractor ctx, int x, int y, int size, String glyph) {
        boolean hov = hit(curMx, curMy, x, y, size, size);
        ctx.fill(x, y, x + size, y + size, hov ? BTN_HOV : BTN_BG);
        int gw = this.font.width(glyph);
        ctx.text(this.font, glyph, x + (size - gw) / 2, y + (size - 8) / 2, hov ? ACCENT : SUBTEXT, false);
    }

    // ── input ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();

        if (editKind != 0) {
            int ex = editBox.getX(), ey = editBox.getY();
            if (!hit(mx, my, ex, ey, rowCountW, rowCountH)) commitEdit();
        }

        if (hit(mx, my, closeX, closeY, closeS, closeS)) { onClose(); return true; }

        if (hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchField.setFocused(true);
            scroll = 0;
            return searchField.mouseClicked(click, doubled);
        } else if (searchField != null) {
            searchField.setFocused(false);
        }

        if (hit(mx, my, runsTileX, runsY, (contentX1 - contentX0 - PAD * 2 - 6 * 3) / 4, STAT_H - 6)) {
            openEdit(1, "", "", LootTrackerStore.runs()); return true;
        }

        List<LootTrackerStore.Row> rows = filterRows(LootTrackerStore.rows());
        for (int i = 0; i < rowY.length && i < rows.size(); i++) {
            LootTrackerStore.Row r = rows.get(i);
            int countY = rowY[i] + (ROW_H - rowCountH) / 2;
            if (hit(mx, my, rowCountX, countY, rowCountW, rowCountH)) { openEdit(2, r.id, r.name, r.count); return true; }
        }

        if (hit(mx, my, clearX, clearY, clearW, clearH)) {
            if (clearArmed) {
                LootTrackerStore.clear();
                clearArmed = false;
            } else {
                clearArmed = true;
                clearArmedAt = System.currentTimeMillis();
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hit(mouseX, mouseY, listX0, listTop, listX1 - listX0, listH)) {
            scroll -= (int) (verticalAmount * ROW_H);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (editKind != 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { cancelEdit(); return true; }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); return true; }
            editBox.keyPressed(input);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchField.getValue().isEmpty()) { searchField.setValue(""); return true; }
                searchField.setFocused(false);
                return true;
            }
            searchField.keyPressed(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (editKind != 0) { editBox.charTyped(input); return true; }
        if (searchField != null && searchField.isFocused()) { searchField.charTyped(input); return true; }
        return super.charTyped(input);
    }

    private void openEdit(int kind, String id, String name, int current) {
        editKind = kind;
        editId = id == null ? "" : id;
        editName = name == null ? "" : name;
        editBox.setValue(String.valueOf(current));
        editBox.setFocused(true);
    }

    private void commitEdit() {
        if (editKind == 0) return;
        String t = editBox.getValue().trim();
        try {
            int n = t.isEmpty() ? 0 : Integer.parseInt(t);
            if (editKind == 1) LootTrackerStore.setRuns(n);
            else if (editKind == 2) LootTrackerStore.setCount(editName, editId, n);
        } catch (NumberFormatException ignored) {}
        editKind = 0;
        editBox.setFocused(false);
    }

    private void cancelEdit() {
        editKind = 0;
        editBox.setFocused(false);
    }

    private boolean isEditingRow(LootTrackerStore.Row r) {
        if (editKind != 2) return false;
        return (editId != null && !editId.isEmpty()) ? editId.equals(r.id) : editName.equalsIgnoreCase(r.name);
    }

    @Override
    public void onClose() {
        if (editKind != 0) commitEdit();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static double rowValue(LootTrackerStore.Row r) {
        if (r.id == null || r.id.isEmpty()) return 0;
        return CroesusPrices.price(r.id) * r.count;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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
    public static double totalValueForChat() {
        double sum = 0;
        for (LootTrackerStore.Row r : LootTrackerStore.rows()) sum += rowValue(r);
        return sum;
    }
    public static int runsForChat() { return LootTrackerStore.runs(); }
    public static String fmtCoinsPublic(double v) { return fmtCoins(v); }
}

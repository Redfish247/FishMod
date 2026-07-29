package fishmod.features.dungeon;

import fishmod.utils.dungeon.waypoints.DungeonWaypointStore;
import fishmod.utils.dungeon.waypoints.StoredWaypoint;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /fmwp gui — full-page list of every waypoint in {@link DungeonWaypointStore}, grouped into
 * folders by where they live: one folder per dungeon room signature, one per Skyblock island /
 * server+dimension freeform bucket ({@link DungeonWaypoints#globalKey}-style keys), and one per
 * route. Lets you rename or delete waypoints without needing to stand in the room / at the location
 * where they were placed. Follows the same look as {@link fishmod.features.croesus.LootTrackerScreen}.
 */
public class DungeonWaypointListScreen extends Screen {

    private static final int BG_TOP     = 0xEE0A0E12;
    private static final int BG_BOT     = 0xF2050709;
    private static final int PANEL_BG   = 0xFF11161C;
    private static final int PANEL_BORDER = 0xFF232D36;
    private static final int TILE_BORDER = 0xFF232D36;
    private static final int ROW_BG     = 0xFF14191F;
    private static final int ROW_BG_ALT = 0xFF171D24;
    private static final int ROW_HOVER  = 0xFF1E262E;
    private static final int FOLDER_BG  = 0xFF1B222B;
    private static final int ACCENT     = 0xFF2FD1C4;
    private static final int TEXT       = 0xFFEFF4F7;
    private static final int SUBTEXT    = 0xFF8A97A3;
    private static final int DANGER     = 0xFFE0574B;
    private static final int DANGER_BG  = 0xFF241213;
    private static final int DANGER_HOV = 0xFF3A1517;
    private static final int BTN_BG     = 0xFF1B2229;
    private static final int BTN_HOV    = 0xFF25313A;
    private static final int SEARCH_BG  = 0xFF161D24;
    private static final int SEARCH_BG_FOCUS = 0xFF1B2530;

    private static final int MARGIN = 20;
    private static final int HEADER_H = 34;
    private static final int SEARCH_H = 24;
    private static final int ROW_H = 30;
    private static final int FOLDER_ROW_H = 20;
    private static final int GAP = 10;
    private static final int PAD = 14;
    private static final int DEL_W = 44;

    private static final class Entry {
        final String key; final int index; final StoredWaypoint wp;
        Entry(String key, int index, StoredWaypoint wp) { this.key = key; this.index = index; this.wp = wp; }
    }

    /** One display row: either a folder header (no entry) or a waypoint (entry set). */
    private static final class Row {
        final String folderLabel; final int folderCount; final Entry entry;
        Row(String folderLabel, int folderCount) { this.folderLabel = folderLabel; this.folderCount = folderCount; this.entry = null; }
        Row(Entry entry) { this.folderLabel = null; this.folderCount = 0; this.entry = entry; }
        boolean isFolder() { return entry == null; }
    }

    private int contentX0, contentX1, contentY0, contentY1;
    private int listTop, listH, listX0, listX1;
    private int scroll = 0;
    private int curMx, curMy;

    private int closeX, closeY, closeS;
    private int searchX, searchY, searchW, searchH;
    private int[] rowY = new int[0];
    private int[] rowH = new int[0];
    private int[] delX = new int[0];

    private EditBox searchField;
    private EditBox renameBox;
    private int renameRow = -1;

    public DungeonWaypointListScreen() {
        super(Component.literal("Dungeon Waypoints"));
    }

    @Override
    protected void init() {
        searchField = new EditBox(this.font, 0, 0, 100, SEARCH_H - 8, Component.literal(""));
        searchField.setMaxLength(64);
        searchField.setBordered(false);
        searchField.setResponder(s -> scroll = 0);

        renameBox = new EditBox(this.font, 0, 0, 100, 14, Component.literal(""));
        renameBox.setMaxLength(48);
        renameBox.setBordered(false);
    }

    private List<Entry> allEntries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, List<StoredWaypoint>> e : DungeonWaypointStore.allData().entrySet()) {
            List<StoredWaypoint> list = e.getValue();
            for (int i = 0; i < list.size(); i++) out.add(new Entry(e.getKey(), i, list.get(i)));
        }
        return out;
    }

    private static int routeSize(String routeId) {
        int n = 0;
        for (List<StoredWaypoint> list : DungeonWaypointStore.allData().values())
            for (StoredWaypoint w : list) if (routeId.equals(w.routeId)) n++;
        return n;
    }

    /** Folder key an entry belongs in: one per route, else one per room/location. */
    private static String folderKey(Entry e) {
        return e.wp.routeId != null ? "route:" + e.wp.routeId : "loc:" + e.key;
    }

    private static String folderLabel(Entry e) {
        return e.wp.routeId != null ? "§b🔗 Route '" + e.wp.routeId + "'" : "§e" + locationLabel(e.key);
    }

    /** Builds the folder-grouped, search-filtered display list. Routes first, then locations, each in first-seen order. */
    private List<Row> buildRows() {
        List<Entry> all = allEntries();
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase();
        if (!q.isEmpty()) {
            List<Entry> filtered = new ArrayList<>();
            for (Entry e : all) {
                String title = e.wp.title == null ? "" : e.wp.title.toLowerCase();
                if (title.contains(q) || locationLabel(e.key).toLowerCase().contains(q)
                        || (e.wp.routeId != null && e.wp.routeId.toLowerCase().contains(q))) filtered.add(e);
            }
            all = filtered;
        }

        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : all) groups.computeIfAbsent(folderKey(e), k -> new ArrayList<>()).add(e);
        for (List<Entry> group : groups.values()) {
            group.sort((a, b) -> {
                if (a.wp.routeId != null) return Integer.compare(a.wp.routeOrder, b.wp.routeOrder);
                return 0;
            });
        }

        List<Row> rows = new ArrayList<>();
        for (List<Entry> group : groups.values()) {
            rows.add(new Row(folderLabel(group.get(0)), group.size()));
            for (Entry e : group) rows.add(new Row(e));
        }
        return rows;
    }

    private static String locationLabel(String key) {
        if (key.startsWith("global:")) {
            String rest = key.substring("global:".length());
            int idx = rest.lastIndexOf(':');
            if (idx > 0) return rest.substring(0, idx) + " §8/ " + rest.substring(idx + 1);
            return rest;
        }
        return "Room " + (key.length() > 16 ? key.substring(0, 16) + "…" : key);
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

        renderHeader(ctx);
        int searchBottom = renderSearch(ctx, contentY0 + HEADER_H);

        listTop = searchBottom + GAP;
        listX0 = contentX0 + PAD;
        listX1 = contentX1 - PAD;
        listH = contentY1 - PAD - listTop;

        renderList(ctx, buildRows());

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor ctx) {
        int y = contentY0 + PAD;
        ctx.text(this.font, "§l§bDungeon Waypoints", contentX0 + PAD, y, TEXT, true);
        ctx.text(this.font, "§8" + allEntries().size() + " waypoint(s) — click a name to rename, §c×§8 to delete §8(deletes the whole route for §7🔗§8 entries)", contentX0 + PAD, y + 11, SUBTEXT, true);

        closeS = 18;
        closeX = contentX1 - PAD - closeS;
        closeY = contentY0 + PAD - 3;
        boolean hov = hit(curMx, curMy, closeX, closeY, closeS, closeS);
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS, hov ? BTN_HOV : BTN_BG);
        ctx.centeredText(this.font, Component.literal("§7x"), closeX + closeS / 2, closeY + 5, hov ? 0xFFFFFFFF : SUBTEXT);
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
                ctx.text(this.font, "§8Search waypoints...", searchX + 16 + 2, searchY + (searchH - 8) / 2, SUBTEXT, false);
            }
        }
        return searchY + searchH;
    }

    private void renderList(GuiGraphicsExtractor ctx, List<Row> rows) {
        int x0 = listX0, x1 = listX1;
        ctx.enableScissor(x0, listTop, x1, listTop + listH);

        boolean searching = searchField != null && !searchField.getValue().trim().isEmpty();

        if (rows.isEmpty()) {
            String msg = searching ? "§8no waypoints match your search" : "§8no waypoints placed yet — /fmwp edit, then right-click to place";
            ctx.text(this.font, msg, x0, listTop + 6, SUBTEXT, true);
            rowY = new int[0];
            rowH = new int[0];
            delX = new int[0];
        } else {
            int totalH = 0;
            for (Row r : rows) totalH += r.isFolder() ? FOLDER_ROW_H : ROW_H;
            int maxScroll = Math.max(0, totalH - listH);
            scroll = Mth.clamp(scroll, 0, maxScroll);

            rowY = new int[rows.size()];
            rowH = new int[rows.size()];
            delX = new int[rows.size()];

            int y = listTop - scroll;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int h = r.isFolder() ? FOLDER_ROW_H : ROW_H;
                int rowTop = y;
                rowY[i] = rowTop;
                rowH[i] = h;
                y += h;
                if (rowTop + h < listTop || rowTop > listTop + listH) continue;

                if (r.isFolder()) {
                    ctx.fill(x0, rowTop, x1, rowTop + h - 1, FOLDER_BG);
                    String label = r.folderLabel + " §8(" + r.folderCount + ")";
                    ctx.text(this.font, this.font.plainSubstrByWidth(label, x1 - x0 - 8), x0 + 6, rowTop + (h - 8) / 2, TEXT, false);
                    continue;
                }

                Entry e = r.entry;
                boolean hov = hit(curMx, curMy, x0, rowTop, x1 - x0, h);
                int bg = hov ? ROW_HOVER : ((i & 1) == 1 ? ROW_BG_ALT : ROW_BG);
                ctx.fill(x0, rowTop, x1, rowTop + h - 1, bg);

                int delXHere = x1 - DEL_W;
                delX[i] = delXHere;
                boolean dhov = hit(curMx, curMy, delXHere, rowTop, DEL_W, h - 1);
                ctx.fill(delXHere, rowTop, delXHere + DEL_W, rowTop + h - 1, dhov ? DANGER_HOV : DANGER_BG);
                int xw = this.font.width("×");
                ctx.text(this.font, "×", delXHere + (DEL_W - xw) / 2, rowTop + (h - 8) / 2, DANGER, false);

                int nameX = x0 + 16;
                int nameMaxW = delXHere - nameX - 8;

                if (renameRow == i) {
                    renameBox.setX(nameX);
                    renameBox.setY(rowTop + 4);
                    renameBox.setWidth(nameMaxW);
                    renameBox.extractRenderState(ctx, curMx, curMy, 0f);
                } else {
                    String name = (e.wp.title == null || e.wp.title.isBlank()) ? "(unnamed)" : e.wp.title;
                    ctx.text(this.font, this.font.plainSubstrByWidth("§f" + name, nameMaxW), nameX, rowTop + 4, TEXT, false);
                }

                String loc = e.wp.routeId != null
                        ? "§8#" + (e.wp.routeOrder + 1) + "/" + routeSize(e.wp.routeId)
                        : "§8@ §7" + fmt(e.wp.x) + ", " + fmt(e.wp.y) + ", " + fmt(e.wp.z);
                ctx.text(this.font, this.font.plainSubstrByWidth(loc, nameMaxW), nameX, rowTop + 16, SUBTEXT, false);
            }

            if (maxScroll > 0) {
                int barX = x1 - 2;
                int barH = Math.max(10, listH * listH / Math.max(1, totalH));
                int barY = listTop + (listH - barH) * scroll / Math.max(1, maxScroll);
                ctx.fill(barX, listTop, barX + 2, listTop + listH, 0x33FFFFFF);
                ctx.fill(barX, barY, barX + 2, barY + barH, ACCENT);
            }
        }

        ctx.disableScissor();
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    // ── input ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();

        if (renameRow != -1) {
            int rx = renameBox.getX(), ry = renameBox.getY(), rw = renameBox.getWidth();
            if (!hit(mx, my, rx, ry, rw, 14)) commitRename();
        }

        if (hit(mx, my, closeX, closeY, closeS, closeS)) { onClose(); return true; }

        if (hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchField.setFocused(true);
            scroll = 0;
            return searchField.mouseClicked(click, doubled);
        } else if (searchField != null) {
            searchField.setFocused(false);
        }

        List<Row> rows = buildRows();
        for (int i = 0; i < rowY.length && i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.isFolder()) continue;
            Entry e = r.entry;
            if (hit(mx, my, delX[i], rowY[i], DEL_W, rowH[i] - 1)) {
                if (e.wp.routeId != null) {
                    DungeonWaypoints.deleteRoute(e.wp.routeId);
                } else {
                    DungeonWaypointStore.removeAt(e.key, e.index);
                    DungeonWaypoints.refreshLive();
                }
                if (renameRow == i) renameRow = -1;
                return true;
            }
            int nameX = listX0 + 16;
            int nameMaxW = delX[i] - nameX - 8;
            if (hit(mx, my, nameX, rowY[i] + 4, nameMaxW, 14)) {
                openRename(i, e);
                return true;
            }
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
        if (renameRow != -1) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { renameRow = -1; return true; }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { commitRename(); return true; }
            renameBox.keyPressed(input);
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
        if (renameRow != -1) { renameBox.charTyped(input); return true; }
        if (searchField != null && searchField.isFocused()) { searchField.charTyped(input); return true; }
        return super.charTyped(input);
    }

    private void openRename(int rowIndex, Entry e) {
        renameRow = rowIndex;
        renameBox.setValue(e.wp.title == null ? "" : e.wp.title);
        renameBox.setFocused(true);
    }

    private void commitRename() {
        if (renameRow == -1) return;
        List<Row> rows = buildRows();
        if (renameRow < rows.size() && !rows.get(renameRow).isFolder()) {
            Entry e = rows.get(renameRow).entry;
            DungeonWaypointStore.setTitle(e.key, e.index, renameBox.getValue().trim());
            DungeonWaypoints.refreshLive();
        }
        renameRow = -1;
        renameBox.setFocused(false);
    }

    @Override
    public void onClose() {
        if (renameRow != -1) commitRename();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

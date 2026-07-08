package fishmod.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Compact custom tab list. Hypixel packs every column (Players / Info / Trophy Frogs / Active
 * Effects) into the player-list entries, ordered column-major by their {@code !A-a}/{@code !B-a}
 * sort keys — so we render those entries verbatim into a translucent panel rather than re-deriving
 * data. Adds a header stat bar (Players / Server / TPS / Ping) and a footer. Opt-in via config.
 */
public final class CompactTab {
    private CompactTab() {}

    private static final int BG_RGB  = 0x0B0D13;

    /** Panel background alpha derived from the FishSettings slider (0..100). */
    private static int bgPanel() {
        int pct = Math.max(0, Math.min(100, fishmod.utils.config.values.FishSettings.compactTabOpacity));
        int a = (int) Math.round(pct * 2.55); // 0..255
        return (a << 24) | BG_RGB;
    }
    /** Header strip uses ~45% of the panel alpha so it reads as a lighter band. */
    private static int bgHead() {
        int pct = Math.max(0, Math.min(100, fishmod.utils.config.values.FishSettings.compactTabOpacity));
        int a = (int) Math.round(pct * 2.55 * 0.45); // 0..115
        return (a << 24) | BG_RGB;
    }
    private static final int BORDER  = 0x66303440;
    private static final int DIVIDER = 0x44454a58;
    private static final int LABEL   = 0xFF8A8F9C;
    private static final int VALUE   = 0xFF55FF55;
    private static final int GOLD    = 0xFFFFD700;
    private static final int NAME    = 0xFFE8ECF2;
    private static final int BAR_ON  = 0xFF55E05A;
    private static final int BAR_OFF = 0x55202530;

    private static final Pattern COL_KEY  = Pattern.compile("^!([A-Za-z])");
    private static final Pattern SERVER_ID = Pattern.compile("\\b((?:mini|mega|m)\\d+[A-Za-z]{1,3})\\b");

    /**
     * Returns true if the current tab uses Hypixel's lobby column-major encoding
     * (entries named "!A-…"/"!B-…"). Dungeons, Kuudra, Rift, Garden, Crimson Isle
     * sub-servers etc. don't use this — for those we fall back to vanilla rendering
     * via {@link #shouldRender()} so we just draw whatever Hypixel sent verbatim.
     */
    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            if (COL_KEY.matcher(nameOf(e)).find()) return true;
        }
        return false;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, String tabHeader, String tabFooter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        Font tr = mc.font;
        int lh = 10;

        // ── group entries into Hypixel's tab columns (by their !X- sort key) ──
        List<PlayerInfo> all = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        all.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
        Map<String, List<PlayerInfo>> grouped = new LinkedHashMap<>();
        for (PlayerInfo e : all) {
            Matcher m = COL_KEY.matcher(nameOf(e));
            if (m.find()) grouped.computeIfAbsent(m.group(1).toUpperCase(), k -> new ArrayList<>()).add(e);
        }
        // Non-lobby tabs (dungeons / Kuudra / Rift / Garden / etc.) have no !X- keys —
        // caller should have routed to vanilla via shouldRender(); guard anyway.
        if (grouped.isEmpty()) return;

        // ── smart sizing: trim trailing blank rows, drop empty columns, width = content ──
        List<List<PlayerInfo>> columns = new ArrayList<>();
        List<Integer> colWidths = new ArrayList<>();
        int rows = 0;
        boolean first = true;
        for (var col : grouped.values()) {
            int last = -1, nonBlank = 0;
            for (int i = 0; i < col.size(); i++) if (!blank(col.get(i))) { last = i; nonBlank++; }
            // Drop empty columns AND header-only columns (e.g. an "Info" column with no content).
            if (last < 0 || nonBlank <= 1) { first = false; continue; }
            List<PlayerInfo> trimmed = new ArrayList<>(col.subList(0, last + 1));
            boolean playersCol = first; first = false;
            int maxW = 0;
            for (PlayerInfo e : trimmed) { Component dn = e.getTabListDisplayName(); if (dn != null) maxW = Math.max(maxW, tr.width(dn)); }
            int extra = playersCol ? 26 : 8;                       // head + signal bars on players col
            int w = Math.max(playersCol ? 116 : 60, Math.min(maxW + extra, 230));
            columns.add(trimmed);
            colWidths.add(w);
            rows = Math.max(rows, trimmed.size());
        }
        if (columns.isEmpty()) return;
        rows = Math.min(rows, 22);

        int ping = realPing(mc);
        int fps = mc.getFps();
        double tps = fishmod.features.dungeon.PartyCommandHandler.currentTps();
        String server = findServer(mc, tabFooter, tabHeader);

        // ── panel geometry ──
        int pad = 8, gap = 8;
        int contentW = 0; for (int w : colWidths) contentW += w;
        contentW += gap * (columns.size() - 1);
        int pw = Math.min(screenW - 12, contentW + pad * 2);
        int x0 = (screenW - pw) / 2;
        int y0 = 4;
        int headH = 28;
        int bodyH = rows * lh + 6;
        int footH = 12;
        int totalH = headH + bodyH + footH;

        // rounded translucent panel
        roundRect(ctx, x0, y0, x0 + pw, y0 + totalH, bgPanel());
        roundRect(ctx, x0, y0, x0 + pw, y0 + headH, bgHead());

        // ── header stat bar (PLAYERS cell removed — count already shown atop the Players column) ──
        String[] labels = {"SERVER", "TPS", "FPS", "PING"};
        String[] values = {
                server,
                tps < 0 ? "—" : String.format("%.2f", tps),
                String.valueOf(fps),
                ping < 0 ? "—" : ping + "ms"
        };
        int cellW = pw / labels.length;
        for (int i = 0; i < labels.length; i++) {
            int cxL = x0 + i * cellW;
            if (i > 0) ctx.fill(cxL, y0 + 5, cxL + 1, y0 + headH - 5, DIVIDER);
            int cxC = cxL + cellW / 2;
            ctx.centeredText(tr, "§7" + labels[i], cxC, y0 + 5, LABEL);
            int vc = (i == 1 && tps >= 0 && tps < 19) ? 0xFFFF5555 : VALUE;
            ctx.centeredText(tr, values[i], cxC, y0 + 16, vc);
        }
        ctx.fill(x0 + 4, y0 + headH - 1, x0 + pw - 4, y0 + headH, DIVIDER);

        // ── columns ──
        int cy = y0 + headH + 3;
        int colX = x0 + pad;
        for (int c = 0; c < columns.size(); c++) {
            int w = colWidths.get(c);
            if (c > 0) ctx.fill(colX - gap / 2, cy, colX - gap / 2 + 1, cy + rows * lh, DIVIDER);
            boolean playersCol = (c == 0);
            List<PlayerInfo> entries = columns.get(c);
            for (int r = 0; r < entries.size() && r < rows; r++) {
                PlayerInfo e = entries.get(r);
                Component dn = e.getTabListDisplayName();
                if (dn == null) continue;
                int ry = cy + r * lh;
                int tx = colX;
                if (playersCol && r > 0) {
                    try { PlayerFaceExtractor.extractRenderState(ctx, e.getSkin(), colX, ry - 1, 8); } catch (Exception ignored) {}
                    tx = colX + 10;
                }
                // draw styled text directly (no plain-string trim → keeps rank colors)
                ctx.text(tr, dn, tx, ry, NAME, true);
                if (playersCol && r > 0 && e.getLatency() > 0)
                    drawSignal(ctx, colX + w - 13, ry, e.getLatency());
            }
            colX += w + gap;
        }

        ctx.centeredText(tr, footerLine(tabFooter), x0 + pw / 2, y0 + totalH - footH + 2, GOLD);
    }

    private static boolean blank(PlayerInfo e) {
        Component dn = e.getTabListDisplayName();
        if (dn == null) return true;
        // Strip color codes AND invisible formatting chars (NBSP, LRM/RLM, ZWJ, separators) so
        // Hypixel's hidden-character padding rows are recognized as blank.
        String s = dn.getString().replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Z}\\s]", "");
        return s.isEmpty();
    }

    /** 2px-radius rounded rectangle fill. */
    private static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        ctx.fill(x1, y1 + 2, x2, y2 - 2, color);
        ctx.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        ctx.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    private static void drawSignal(GuiGraphicsExtractor ctx, int x, int y, int latency) {
        int filled = latency <= 75 ? 4 : latency <= 150 ? 3 : latency <= 300 ? 2 : 1;
        for (int b = 0; b < 4; b++) {
            int h = 2 + b * 2;
            int bx = x + b * 3;
            ctx.fill(bx, y + 8 - h, bx + 2, y + 8, b < filled ? BAR_ON : BAR_OFF);
        }
    }

    private static String nameOf(PlayerInfo e) {
        try { return e.getProfile() != null && e.getProfile().name() != null ? e.getProfile().name() : ""; }
        catch (Exception ex) { return ""; }
    }

    /**
     * Real ping. The vanilla ping/pong round trip ({@link fishmod.utils.PingTracker}) is the most
     * accurate, freshest end-to-end source — a true client→server→client measurement, the same one
     * Odin uses. Fall back to server-measured tab latency, then server-list join ping, only when a
     * live measurement isn't available yet (briefly after join).
     */
    private static int realPing(Minecraft mc) {
        int live = fishmod.utils.PingTracker.latest();
        if (live > 0) return live;
        try {
            var self = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (self != null && self.getLatency() > 0) return self.getLatency();
        } catch (Exception ignored) {}
        try {
            var si = mc.getCurrentServer();
            if (si != null && si.ping > 0) return (int) si.ping;
        } catch (Exception ignored) {}
        return -1;
    }

    private static String findServer(Minecraft mc, String footer, String header) {
        String hay = (footer == null ? "" : footer) + " " + (header == null ? "" : header);
        // sidebar often carries the server id (e.g. "05/27/26 m108AB")
        try {
            if (mc.level != null) {
                var sb = mc.level.getScoreboard();
                var obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                if (obj != null) for (var en : sb.listPlayerScores(obj)) {
                    var team = sb.getPlayersTeam(en.owner());
                    String raw = team != null ? team.getPlayerPrefix().getString() + en.owner() + team.getPlayerSuffix().getString() : en.ownerName().getString();
                    hay += " " + raw.replaceAll("§.", "");
                }
            }
        } catch (Exception ignored) {}
        Matcher m = SERVER_ID.matcher(hay);
        return m.find() ? m.group(1) : "—";
    }

    private static String footerLine(String footer) {
        if (footer != null && !footer.isEmpty()) for (String line : footer.split("\n")) {
            String s = line.replaceAll("§.", "").trim();
            if (s.toUpperCase().contains("STORE") || s.toUpperCase().contains("RANKS")) return "§6" + s;
        }
        return "§6Ranks, Boosters & MORE! §eSTORE.HYPIXEL.NET";
    }
}

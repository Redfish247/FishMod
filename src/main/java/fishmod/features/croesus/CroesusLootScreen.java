package fishmod.features.croesus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Read-only viewer for Croesus chest claims. Two tabs: per-item summary + run list. */
public class CroesusLootScreen extends Screen {

    private static final int BG_COLOR     = 0xFF1A1A1A;
    private static final int SIDEBAR_COLOR= 0xFF111111;
    private static final int PANEL_COLOR  = 0xFF1E1E1E;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int TEXT_COLOR   = 0xFFFFFFFF;
    private static final int SUBTEXT_COLOR= 0xFF888888;
    private static final int ACCENT       = 0xFF00AACC;
    private static final int ACCENT_HOVER = 0xFF00CCEE;
    private static final int GOLD         = 0xFFFFAA00;

    private static final int SIDEBAR_WIDTH= 160;
    private static final int HEADER_HEIGHT= 50;
    private static final int PADDING      = 16;
    private static final int ROW_H        = 22;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DecimalFormat NUM = new DecimalFormat("#,###");

    private final Screen parent;
    private int tab = 0; // 0=summary 1=runs
    private int scroll = 0;
    private boolean confirmClear = false;

    public CroesusLootScreen(Screen parent) {
        super(Text.literal("Croesus Loot"));
        this.parent = parent;
        CroesusPrices.refreshIfStale();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = this.width, h = this.height;
        ctx.fill(0, 0, w, h, BG_COLOR);
        ctx.fill(0, 0, SIDEBAR_WIDTH, h, SIDEBAR_COLOR);
        ctx.fill(SIDEBAR_WIDTH, 0, SIDEBAR_WIDTH + 1, h, BORDER_COLOR);
        ctx.fill(SIDEBAR_WIDTH + 1, 0, w, HEADER_HEIGHT, PANEL_COLOR);
        ctx.fill(SIDEBAR_WIDTH + 1, HEADER_HEIGHT, w, HEADER_HEIGHT + 1, BORDER_COLOR);

        ctx.drawText(textRenderer, "Croesus Loot", SIDEBAR_WIDTH + PADDING, 10, ACCENT, false);
        List<CroesusStore.Entry> entries = CroesusStore.all();
        String sub = entries.size() + " claims • Total: " + fmtCoins(CroesusStore.totalValue())
                + " • Avg/run: " + fmtCoins(entries.isEmpty() ? 0 : CroesusStore.totalValue() / entries.size());
        ctx.drawText(textRenderer, sub, SIDEBAR_WIDTH + PADDING, 24, SUBTEXT_COLOR, false);

        // Sidebar tabs
        String[] tabs = {"Summary", "Runs"};
        int ty = 40;
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = i == tab;
            boolean hov = mx >= 0 && mx < SIDEBAR_WIDTH && my >= ty && my < ty + 28;
            ctx.fill(0, ty, SIDEBAR_WIDTH, ty + 28, sel ? 0xFF252525 : hov ? 0xFF1D1D1D : 0xFF000000);
            if (sel) ctx.fill(0, ty, 3, ty + 28, ACCENT);
            ctx.drawText(textRenderer, tabs[i], PADDING, ty + 10, sel ? ACCENT : TEXT_COLOR, false);
            ty += 28;
        }

        ctx.enableScissor(SIDEBAR_WIDTH + 1, HEADER_HEIGHT + 1, w, h);
        if (tab == 0) renderSummary(ctx, mx, my);
        else renderRuns(ctx, mx, my);
        ctx.disableScissor();

        // Clear + Back buttons
        int btnW = SIDEBAR_WIDTH - PADDING * 2, btnH = 20;
        int btnX = PADDING;
        int clearY = h - PADDING - btnH;
        int backY  = clearY - btnH - 6;

        boolean backHov  = mx >= btnX && mx <= btnX + btnW && my >= backY  && my <= backY  + btnH;
        boolean clearHov = mx >= btnX && mx <= btnX + btnW && my >= clearY && my <= clearY + btnH;

        ctx.fill(btnX, backY, btnX + btnW, backY + btnH, backHov ? ACCENT_HOVER : ACCENT);
        ctx.drawText(textRenderer, "Back", btnX + (btnW - textRenderer.getWidth("Back")) / 2,
                backY + (btnH - 8) / 2, 0xFFFFFFFF, false);

        int clearColor = confirmClear ? 0xFFCC2222 : clearHov ? 0xFFAA1111 : 0xFF661111;
        ctx.fill(btnX, clearY, btnX + btnW, clearY + btnH, clearColor);
        String clearLabel = confirmClear ? "Confirm Clear" : "Clear All Data";
        ctx.drawText(textRenderer, clearLabel, btnX + (btnW - textRenderer.getWidth(clearLabel)) / 2,
                clearY + (btnH - 8) / 2, 0xFFFFFFFF, false);

        super.render(ctx, mx, my, delta);
    }

    private void renderSummary(DrawContext ctx, int mx, int my) {
        Map<String, CroesusStore.Agg> agg = CroesusStore.aggregateByItem();
        List<CroesusStore.Agg> list = new ArrayList<>(agg.values());
        list.sort(Comparator.comparingDouble((CroesusStore.Agg a) -> -a.totalValue));

        int x = SIDEBAR_WIDTH + PADDING;
        int y = HEADER_HEIGHT + PADDING - scroll;
        int w = this.width;
        // Column headers
        ctx.drawText(textRenderer, "Item", x, y, SUBTEXT_COLOR, false);
        ctx.drawText(textRenderer, "Count", w - PADDING - 220, y, SUBTEXT_COLOR, false);
        ctx.drawText(textRenderer, "Total Value", w - PADDING - 120, y, SUBTEXT_COLOR, false);
        y += 14;
        ctx.fill(x, y, w - PADDING, y + 1, BORDER_COLOR);
        y += 6;

        for (CroesusStore.Agg a : list) {
            if (y + ROW_H >= HEADER_HEIGHT && y <= this.height) {
                ctx.drawText(textRenderer, a.name, x, y + 6, TEXT_COLOR, false);
                String cnt = NUM.format(a.count);
                ctx.drawText(textRenderer, cnt, w - PADDING - 220, y + 6, TEXT_COLOR, false);
                ctx.drawText(textRenderer, fmtCoins(a.totalValue), w - PADDING - 120, y + 6, GOLD, false);
                ctx.fill(x, y + ROW_H - 1, w - PADDING, y + ROW_H, 0xFF222222);
            }
            y += ROW_H;
        }
    }

    private void renderRuns(DrawContext ctx, int mx, int my) {
        List<CroesusStore.Entry> entries = new ArrayList<>(CroesusStore.all());
        entries.sort(Comparator.comparingLong((CroesusStore.Entry e) -> -e.timestamp));

        int x = SIDEBAR_WIDTH + PADDING;
        int y = HEADER_HEIGHT + PADDING - scroll;
        int w = this.width;

        for (CroesusStore.Entry e : entries) {
            int boxH = 18 + e.items.size() * 11 + 8;
            if (y + boxH < HEADER_HEIGHT || y > this.height) { y += boxH + 6; continue; }
            ctx.fill(x, y, w - PADDING, y + boxH, PANEL_COLOR);
            ctx.fill(x, y, w - PADDING, y + 1, BORDER_COLOR);
            ctx.fill(x, y + boxH - 1, w - PADDING, y + boxH, BORDER_COLOR);

            String header = "§b" + e.floor + " §7• §f" + DT.format(Instant.ofEpochMilli(e.timestamp))
                    + " §7• §f" + e.chestType;
            ctx.drawText(textRenderer, Text.literal(header), x + 6, y + 5, TEXT_COLOR, false);

            double total = 0;
            for (CroesusStore.Item it : e.items) total += it.priceAtClaim * it.count;
            String right = fmtCoins(total);
            ctx.drawText(textRenderer, right, w - PADDING - 6 - textRenderer.getWidth(right), y + 5, GOLD, false);

            int iy = y + 18;
            for (CroesusStore.Item it : e.items) {
                String left = " • " + (it.count > 1 ? it.count + "x " : "") + it.name;
                ctx.drawText(textRenderer, left, x + 6, iy, 0xFFCCCCCC, false);
                String price = fmtCoins(it.priceAtClaim * it.count);
                ctx.drawText(textRenderer, price, w - PADDING - 6 - textRenderer.getWidth(price),
                        iy, SUBTEXT_COLOR, false);
                iy += 11;
            }
            y += boxH + 6;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();

        // Tab clicks
        int ty = 40;
        for (int i = 0; i < 2; i++) {
            if (mx >= 0 && mx < SIDEBAR_WIDTH && my >= ty && my < ty + 28) {
                tab = i; scroll = 0; return true;
            }
            ty += 28;
        }

        int btnW = SIDEBAR_WIDTH - PADDING * 2, btnH = 20, btnX = PADDING;
        int clearY = this.height - PADDING - btnH;
        int backY  = clearY - btnH - 6;

        if (mx >= btnX && mx <= btnX + btnW && my >= backY && my <= backY + btnH) {
            confirmClear = false;
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        if (mx >= btnX && mx <= btnX + btnW && my >= clearY && my <= clearY + btnH) {
            if (confirmClear) {
                CroesusStore.clear();
                confirmClear = false;
                scroll = 0;
            } else {
                confirmClear = true;
            }
            return true;
        }
        confirmClear = false;
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = MathHelper.clamp((int)(scroll - verticalAmount * 12), 0, 100_000);
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    private static String fmtCoins(double v) {
        if (v < 0) return "—";
        if (v == 0) return "0";
        if (v >= 1_000_000_000d) return String.format("%.2fB", v / 1_000_000_000d);
        if (v >= 1_000_000d)     return String.format("%.2fM", v / 1_000_000d);
        if (v >= 1_000d)         return String.format("%.1fk", v / 1_000d);
        return NUM.format(v);
    }
}

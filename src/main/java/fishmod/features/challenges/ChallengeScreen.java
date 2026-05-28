package fishmod.features.challenges;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** GUI screen — pick a tier, view active challenges, reroll, see the leaderboard. */
public class ChallengeScreen extends Screen {

    // Palette mirrors FishModScreen so the two screens feel like one app.
    static final int PANEL_BG    = 0xE6080A10;
    static final int CARD_BG     = 0xFF15171C;
    static final int CARD_HOVER  = 0xFF1E2129;
    static final int ACCENT      = 0xFF0D7377;
    static final int ACCENT_HOV  = 0xFF119BA0;
    static final int BORDER      = 0xFF2A2D38;
    static final int BORDER_HI   = 0xFF3A3D48;
    static final int TEXT        = 0xFFFFFFFF;
    static final int SUBTEXT     = 0xFF8B92A5;
    static final int DIM         = 0xFF5A6275;
    static final int BAR_BG      = 0xFF1B1D24;
    static final int OK_GREEN    = 0xFF4ADE80;
    static final int DANGER      = 0xFFB04848;
    static final int DANGER_HOV  = 0xFFD15555;
    static final int DISABLED    = 0xFF3A3D48;

    // Tier accent colors
    private static int tierColor(Tier t) {
        return switch (t) {
            case DAILY   -> 0xFF55FF55; // green
            case WEEKLY  -> 0xFF55DCFF; // aqua
            case MONTHLY -> 0xFFD580FF; // purple
        };
    }

    static final float TEXT_SCALE = 0.85f;
    static final long  REVEAL_MS  = 260L;

    private final Screen parent;
    private final long openMs = System.currentTimeMillis();
    private List<ChallengeApi.LbEntry> lbCache = java.util.List.of();
    private long lbFetched = 0;

    public ChallengeScreen(Screen parent) {
        super(Text.literal("Challenges"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (System.currentTimeMillis() - lbFetched > 30_000) {
            lbFetched = System.currentTimeMillis();
            ChallengeApi.fetchLeaderboard(10, e -> lbCache = e);
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderInGameBackground(DrawContext ctx) {}

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Full-screen dim, plus subtle vignette band at top/bottom.
        ctx.fill(0, 0, this.width, this.height, PANEL_BG);
        ctx.fill(0, 0, this.width, 1, ACCENT);
        ctx.fill(0, this.height - 1, this.width, this.height, ACCENT);
    }

    private float reveal(float delayMs) {
        float t = MathHelper.clamp(((System.currentTimeMillis() - openMs) - delayMs) / REVEAL_MS, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
    private static int fade(int argb, float a) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * MathHelper.clamp(a, 0f, 1f));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
    private static int slide(float r) { return Math.round((1f - r) * -10f); }

    private static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 4 || y2 - y1 < 4) { ctx.fill(x1, y1, x2, y2, color); return; }
        ctx.fill(x1 + 2, y1,     x2 - 2, y1 + 1, color);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        ctx.fill(x1,     y1 + 2, x2,     y2 - 2, color);
        ctx.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        ctx.fill(x1 + 2, y2 - 1, x2 - 2, y2,     color);
    }

    private void scaledText(DrawContext ctx, String s, int x, int y, int color, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(this.textRenderer, s, 0, 0, color, false);
        ctx.getMatrices().popMatrix();
    }
    private int scaledWidth(String s, float scale) {
        return (int) Math.ceil(this.textRenderer.getWidth(s) * scale);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ChallengeProgress p = ChallengeProgress.get();

        // ── Header ───────────────────────────────────────────────────────────
        float hr = reveal(0);
        int hdy = slide(hr);
        int headerY = 22 + hdy;
        String title = "§l✦ FishMod Challenges ✦";
        int titleW = this.textRenderer.getWidth(title);
        ctx.drawText(this.textRenderer, title, (this.width - titleW) / 2, headerY, fade(0xFFFFD580, hr), false);

        String rerollNote;
        int rerollsThisMonth = ChallengeProgress.currentMonthKey().equals(p.rerollMonthKey) ? p.rerollsUsedThisMonth : 0;
        rerollNote = "§7Rerolls this month: §e" + rerollsThisMonth + "§7/1";

        // Stat chips row under the title
        int chipY = headerY + 14;
        String pointsChip = "§a★ " + p.totalPoints + " §7pts";
        String activeChip = "§b● " + p.active.size() + " §7active";
        int gap = 14;
        int pcw = scaledWidth(pointsChip, TEXT_SCALE) + 14;
        int acw = scaledWidth(activeChip, TEXT_SCALE) + 14;
        int rcw = scaledWidth(rerollNote, TEXT_SCALE) + 14;
        int chipTotal = pcw + acw + rcw + gap * 2;
        int chipX = (this.width - chipTotal) / 2;
        drawChip(ctx, pointsChip, chipX, chipY, pcw, hr);
        drawChip(ctx, activeChip, chipX + pcw + gap, chipY, acw, hr);
        drawChip(ctx, rerollNote, chipX + pcw + gap + acw + gap, chipY, rcw, hr);

        // ── Tier cards ───────────────────────────────────────────────────────
        int cardW = 230, cardH = 150, cardGap = 14;
        int totalW = cardW * 3 + cardGap * 2;
        int startX = (this.width - totalW) / 2;
        int cardY = chipY + 30;
        Tier[] tiers = Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            float cr = reveal(70 + i * 60);
            int dy = slide(cr);
            int x = startX + i * (cardW + cardGap);
            renderCard(ctx, tiers[i], p.active.get(tiers[i]), x, cardY + dy, cardW, cardH, mouseX, mouseY, cr);
        }

        // ── Leaderboard ──────────────────────────────────────────────────────
        float lr = reveal(260);
        int lbY = cardY + cardH + 26 + slide(lr);
        int lbW = totalW;
        int lbH = Math.min(this.height - lbY - 28, 210);
        roundRect(ctx, startX, lbY, startX + lbW, lbY + lbH, fade(CARD_BG, lr));
        ctx.fill(startX, lbY, startX + lbW, lbY + 2, fade(ACCENT, lr));
        ctx.fill(startX, lbY + lbH - 1, startX + lbW, lbY + lbH, fade(BORDER, lr));
        ctx.fill(startX, lbY, startX + 1, lbY + lbH, fade(BORDER, lr));
        ctx.fill(startX + lbW - 1, lbY, startX + lbW, lbY + lbH, fade(BORDER, lr));

        scaledText(ctx, "§e§l⚡ LEADERBOARD", startX + 12, lbY + 8, fade(TEXT, lr), TEXT_SCALE);
        String lbCount = "§7(top " + Math.max(1, lbCache.size()) + ")";
        scaledText(ctx, lbCount, startX + 12 + scaledWidth("§e§l⚡ LEADERBOARD ", TEXT_SCALE), lbY + 8, fade(SUBTEXT, lr), TEXT_SCALE);

        // Column header
        int colY = lbY + 24;
        scaledText(ctx, "§7§lRANK", startX + 12, colY, fade(DIM, lr), 0.75f);
        scaledText(ctx, "§7§lPLAYER", startX + 60, colY, fade(DIM, lr), 0.75f);
        scaledText(ctx, "§7§lPOINTS", startX + lbW - 60, colY, fade(DIM, lr), 0.75f);
        ctx.fill(startX + 8, colY + 10, startX + lbW - 8, colY + 11, fade(BORDER, lr));

        if (lbCache.isEmpty()) {
            String empty = "§7(no scores yet — be the first to submit one)";
            int ew = scaledWidth(empty, TEXT_SCALE);
            scaledText(ctx, empty, startX + (lbW - ew) / 2, lbY + lbH / 2 - 4, fade(SUBTEXT, lr), TEXT_SCALE);
        } else {
            int rowH = 14;
            int rowY = colY + 16;
            for (int i = 0; i < lbCache.size(); i++) {
                if (rowY + rowH > lbY + lbH - 4) break;
                ChallengeApi.LbEntry e = lbCache.get(i);
                if (i % 2 == 1) ctx.fill(startX + 4, rowY - 2, startX + lbW - 4, rowY + rowH - 2, fade(0x18FFFFFF, lr));
                String medal = i == 0 ? "§e§l①" : i == 1 ? "§f§l②" : i == 2 ? "§6§l③" : "§8#" + (i + 1);
                scaledText(ctx, medal, startX + 12, rowY, fade(TEXT, lr), TEXT_SCALE);
                // Render the cosmetic name through NickState.parse so &#rrggbb hex actually colors.
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate((float)(startX + 60), (float) rowY);
                ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE);
                ctx.drawText(this.textRenderer, ChallengeApi.renderName(e.name).asOrderedText(), 0, 0, fade(TEXT, lr), false);
                ctx.getMatrices().popMatrix();
                String pts = "§a§l" + e.totalPoints;
                scaledText(ctx, pts, startX + lbW - 12 - scaledWidth(pts, TEXT_SCALE), rowY, fade(TEXT, lr), TEXT_SCALE);
                rowY += rowH;
            }
        }

        // Footer hint
        String hint = "§8esc to close  §7•  §8right-click a tier card to reroll";
        int hw = this.textRenderer.getWidth(hint);
        ctx.drawText(this.textRenderer, hint, (this.width - hw) / 2, this.height - 14, fade(SUBTEXT, lr), false);
    }

    private void drawChip(DrawContext ctx, String label, int x, int y, int w, float a) {
        int h = 14;
        roundRect(ctx, x, y, x + w, y + h, fade(CARD_BG, a));
        ctx.fill(x, y, x + 2, y + h, fade(ACCENT, a)); // left rail
        scaledText(ctx, label, x + 7, y + (h - 8) / 2, fade(TEXT, a), TEXT_SCALE);
    }

    private void renderCard(DrawContext ctx, Tier tier, Challenge active, int x, int y, int w, int h, int mx, int my, float a) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        roundRect(ctx, x, y, x + w, y + h, fade(hover ? CARD_HOVER : CARD_BG, a));
        // tier color accent strip
        int tc = tierColor(tier);
        ctx.fill(x, y, x + w, y + 3, fade(tc, a));
        ctx.fill(x, y + h - 1, x + w, y + h, fade(BORDER, a));
        ctx.fill(x, y, x + 1, y + h, fade(BORDER, a));
        ctx.fill(x + w - 1, y, x + w, y + h, fade(BORDER, a));

        // Title row
        String title = "§l" + tier.label.toUpperCase();
        scaledText(ctx, title, x + 12, y + 10, fade(tc, a), TEXT_SCALE);
        String reward = "§7§l" + tier.basePoints + "§7-§e§l" + (tier.basePoints * 2) + " §7pts";
        int rw = scaledWidth(reward, 0.75f);
        scaledText(ctx, reward, x + w - rw - 12, y + 12, fade(SUBTEXT, a), 0.75f);

        // Separator
        ctx.fill(x + 12, y + 24, x + w - 12, y + 25, fade(BORDER, a));

        if (active == null) {
            // Idle state — pitch the action
            String tag = switch (tier) {
                case DAILY   -> "§7Quick challenges, refresh §fevery 24h";
                case WEEKLY  -> "§7Mid-effort goals, refresh §fevery 7d";
                case MONTHLY -> "§7Big grind goals, refresh §fevery 30d";
            };
            scaledText(ctx, tag, x + 12, y + 34, fade(SUBTEXT, a), TEXT_SCALE);
            scaledText(ctx, "§8Picked from your weakest stats.", x + 12, y + 46, fade(DIM, a), 0.75f);

            // Start button (full width)
            int bw = w - 24, bh = 26, bx = x + 12, by = y + h - bh - 12;
            boolean bhov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            roundRect(ctx, bx, by, bx + bw, by + bh, fade(bhov ? ACCENT_HOV : ACCENT, a));
            String lbl = "§f§l▶  START " + tier.label.toUpperCase();
            int lw = scaledWidth(lbl, TEXT_SCALE);
            scaledText(ctx, lbl, bx + (bw - lw) / 2, by + (bh - 8) / 2, fade(TEXT, a), TEXT_SCALE);
        } else {
            // Active state — describe + progress bar + timing + actions
            String desc = active.describe();
            // truncate to one line scaled to card width
            int maxW = w - 24;
            while (scaledWidth(desc, TEXT_SCALE) > maxW && desc.length() > 1) desc = desc.substring(0, desc.length() - 2) + "…";
            scaledText(ctx, desc, x + 12, y + 32, fade(TEXT, a), TEXT_SCALE);

            // Progress bar
            int pBarX = x + 12, pBarY = y + 48, pBarW = w - 24, pBarH = 7;
            double pct = active.progressPct();
            roundRect(ctx, pBarX, pBarY, pBarX + pBarW, pBarY + pBarH, fade(BAR_BG, a));
            int fillW = (int) (pBarW * pct);
            if (fillW > 0) roundRect(ctx, pBarX, pBarY, pBarX + fillW, pBarY + pBarH, fade(pct >= 1.0 ? OK_GREEN : tc, a));
            String pctStr = (int) Math.round(pct * 100) + "%";
            int psw = scaledWidth(pctStr, 0.75f);
            scaledText(ctx, "§f§l" + pctStr, pBarX + pBarW - psw, pBarY - 9, fade(TEXT, a), 0.75f);
            // Show progress as delta against baseline — 0/3 pets, 0/500k XP, etc.
            double done = Math.max(0, active.current - active.baseline);
            double goal = Math.max(0, active.target  - active.baseline);
            scaledText(ctx, "§7" + fmtNum(done) + " §8/ §7" + fmtNum(goal), pBarX, pBarY - 9, fade(SUBTEXT, a), 0.75f);

            // Stats row
            int statY = pBarY + pBarH + 6;
            scaledText(ctx, "§7active: §f" + formatMs(active.activeMs), x + 12, statY, fade(SUBTEXT, a), 0.75f);
            String exp = "§7expires: §e" + formatMs(active.expiresAtMs - System.currentTimeMillis());
            int ew = scaledWidth(exp, 0.75f);
            scaledText(ctx, exp, x + w - ew - 12, statY, fade(SUBTEXT, a), 0.75f);

            // Buttons: Reroll + Abandon
            int bw = (w - 32) / 2, bh = 22;
            int by = y + h - bh - 12;
            int b1x = x + 12, b2x = x + 12 + bw + 8;
            boolean canReroll = ChallengeProgress.get().canReroll();
            boolean h1 = canReroll && mx >= b1x && mx <= b1x + bw && my >= by && my <= by + bh;
            boolean h2 = mx >= b2x && mx <= b2x + bw && my >= by && my <= by + bh;
            roundRect(ctx, b1x, by, b1x + bw, by + bh, fade(canReroll ? (h1 ? ACCENT_HOV : ACCENT) : DISABLED, a));
            roundRect(ctx, b2x, by, b2x + bw, by + bh, fade(h2 ? DANGER_HOV : DANGER, a));
            String l1 = canReroll ? "§f§l↻ REROLL" : "§8↻ used";
            String l2 = "§f§l✕ ABANDON";
            int l1w = scaledWidth(l1, TEXT_SCALE);
            int l2w = scaledWidth(l2, TEXT_SCALE);
            scaledText(ctx, l1, b1x + (bw - l1w) / 2, by + (bh - 8) / 2, fade(TEXT, a), TEXT_SCALE);
            scaledText(ctx, l2, b2x + (bw - l2w) / 2, by + (bh - 8) / 2, fade(TEXT, a), TEXT_SCALE);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();
        int btn = click.button();

        int cardW = 230, cardH = 150, cardGap = 14;
        int totalW = cardW * 3 + cardGap * 2;
        int startX = (this.width - totalW) / 2;
        int chipY = 22 + 14;
        int cardY = chipY + 30;
        Tier[] tiers = Tier.values();
        ChallengeProgress p = ChallengeProgress.get();
        for (int i = 0; i < tiers.length; i++) {
            int x = startX + i * (cardW + cardGap);
            int y = cardY;
            Tier tier = tiers[i];
            Challenge active = p.active.get(tier);

            // Right-click anywhere on card = reroll (if active and reroll available)
            boolean inCard = mx >= x && mx <= x + cardW && my >= y && my <= y + cardH;
            if (inCard && btn == 1 && active != null && ChallengeProgress.get().canReroll()) {
                ChallengeManager.reroll(tier);
                return true;
            }

            if (active == null) {
                int bw = cardW - 24, bh = 26, bx = x + 12, by = y + cardH - bh - 12;
                if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                    ChallengeManager.acceptNew(tier, null);
                    return true;
                }
            } else {
                int bw = (cardW - 32) / 2, bh = 22;
                int by = y + cardH - bh - 12;
                int b1x = x + 12, b2x = x + 12 + bw + 8;
                if (mx >= b1x && mx <= b1x + bw && my >= by && my <= by + bh) {
                    ChallengeManager.reroll(tier);
                    return true;
                }
                if (mx >= b2x && mx <= b2x + bw && my >= by && my <= by + bh) {
                    ChallengeManager.abandon(tier);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static String formatMs(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static String fmtNum(double n) {
        long v = (long) n;
        if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1e9);
        if (v >= 1_000_000L)     return String.format("%.1fM", v / 1e6);
        if (v >= 1_000L)         return String.format("%.1fk", v / 1e3);
        return Long.toString(v);
    }
}

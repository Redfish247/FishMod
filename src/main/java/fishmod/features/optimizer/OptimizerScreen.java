package fishmod.features.optimizer;

import fishmod.features.challenges.ChallengeApi;
import fishmod.features.challenges.ProfileSnapshot;
import fishmod.utils.HypixelApi;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Profile Optimizer — on-demand snapshot of the player's progression with concrete next steps.
 * Sections: summary chips (net worth / SB level / cata / MP / pets / fairy souls), a skill
 * roadmap, and a ranked "what to do next" list. Opened via {@code /po} or {@code /fm optimize}.
 *
 * Style mirrors FishModScreen: square corners (plain fills) + slate-teal palette.
 */
public class OptimizerScreen extends Screen {

    // ── palette (matches FishModScreen) ──────────────────────────────────────
    private static final int BG        = 0xE6080A10;
    private static final int PANEL_BG  = 0xFF0E151B;
    private static final int CHIP_BG   = 0xFF10282B;
    private static final int BAR_BG    = 0xFF1B1D24;
    private static final int ACCENT    = 0xFF24B6B0;
    private static final int BORDER    = 0xFF1E2A33;
    private static final int TEXT      = 0xFFEDF1F5;
    private static final int SUBTEXT   = 0xFF7E8A98;
    private static final int DIM       = 0xFF566273;
    private static final int GOLD      = 0xFFFFD580;
    private static final int OK_GREEN  = 0xFF5FBF6A;

    private final Screen parent;

    private volatile ProfileSnapshot snap;
    private volatile List<OptimizerAnalyzer.SkillRow>   skills;
    private volatile List<OptimizerAnalyzer.Suggestion> sugg;
    private volatile boolean snapFailed = false;
    private volatile String  error = "";

    private volatile String  profileName = null;
    private volatile boolean nwDone = false;
    private volatile double  networth = -1;

    public OptimizerScreen(Screen parent) {
        super(Component.literal("Profile Optimizer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (snap == null && !snapFailed) fetch();
    }

    private void fetch() {
        snap = null; skills = null; sugg = null; snapFailed = false; error = "";
        nwDone = false; networth = -1; profileName = null;

        ChallengeApi.fetchLocal(s -> Minecraft.getInstance().execute(() -> {
            if (s == null) { snapFailed = true; error = ChallengeApi.lastFetchError; return; }
            snap   = s;
            skills = OptimizerAnalyzer.skillRoadmap(s);
            sugg   = OptimizerAnalyzer.suggestions(s, 14);
        }));

        Minecraft mc = Minecraft.getInstance();
        String ign = mc.player != null ? mc.player.getName().getString() : null;
        if (ign != null) {
            HypixelApi.getNetworth(mc, ign, (nw, profile) -> mc.execute(() -> {
                networth = nw;
                nwDone = true;
                if (profile != null) profileName = profile;
            }));
        } else { nwDone = true; }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractTransparentBackground(GuiGraphicsExtractor ctx) {}

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, BG);
        ctx.fill(0, 0, this.width, 1, ACCENT);
        ctx.fill(0, this.height - 1, this.width, this.height, ACCENT);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_R) { fetch(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        int contentW = Math.min(this.width - 40, 580);
        int startX = (this.width - contentW) / 2;

        // ── Header ───────────────────────────────────────────────────────────
        String title = "§l✦ Profile Optimizer ✦";
        ctx.text(this.font, title, (this.width - this.font.width(title)) / 2, 14, GOLD, false);
        String sub = profileName != null ? "§7Profile: §f" + profileName : "§8your active SkyBlock profile";
        int sw = sw(sub, 0.85f);
        sst(ctx, sub, (this.width - sw) / 2, 28, SUBTEXT, 0.85f);

        // ── Loading / error states ─────────────────────────────────────────────
        if (snapFailed) {
            String msg = "§cCouldn't load your profile";
            String why = "§8" + (error == null || error.isEmpty() ? "unknown error" : error);
            String tip = "§7Make sure your SkyBlock API access is on, then press §fR §7to retry.";
            ctx.text(this.font, msg, (this.width - this.font.width(msg)) / 2, this.height / 2 - 14, TEXT, false);
            int ww = sw(why, 0.85f);
            sst(ctx, why, (this.width - ww) / 2, this.height / 2, DIM, 0.85f);
            int tw = sw(tip, 0.85f);
            sst(ctx, tip, (this.width - tw) / 2, this.height / 2 + 14, SUBTEXT, 0.85f);
            return;
        }
        if (snap == null) {
            String dots = ".".repeat((int) ((System.currentTimeMillis() / 350) % 4));
            String msg = "§7Analyzing your profile" + dots;
            ctx.text(this.font, msg, (this.width - this.font.width(msg)) / 2, this.height / 2 - 4, TEXT, false);
            return;
        }

        // ── Summary chips ──────────────────────────────────────────────────────
        int chipY = 44, chipH = 26, chipGap = 6, chipCount = 6;
        int chipW = (contentW - chipGap * (chipCount - 1)) / chipCount;
        int cataLvl = HypixelApi.calcCataLevel(snap.cataXp);
        String nwVal = !nwDone ? "§7…" : (networth < 0 ? "§8n/a" : "§a" + fmtNum(networth));
        drawChip(ctx, startX + 0 * (chipW + chipGap), chipY, chipW, chipH, "NET WORTH", nwVal);
        drawChip(ctx, startX + 1 * (chipW + chipGap), chipY, chipW, chipH, "SB LEVEL",  "§f" + snap.sbLevel);
        drawChip(ctx, startX + 2 * (chipW + chipGap), chipY, chipW, chipH, "CATACOMBS", "§f" + cataLvl);
        drawChip(ctx, startX + 3 * (chipW + chipGap), chipY, chipW, chipH, "MAGIC PWR", "§f" + snap.magicalPower);
        drawChip(ctx, startX + 4 * (chipW + chipGap), chipY, chipW, chipH, "PETS @100", "§f" + snap.petsAt100);
        drawChip(ctx, startX + 5 * (chipW + chipGap), chipY, chipW, chipH, "FAIRY SOULS", "§f" + snap.fairySouls);

        // ── Panels ─────────────────────────────────────────────────────────────
        int panelY = chipY + chipH + 10;
        int panelBottom = this.height - 26;
        int gap = 12;
        int leftW = (int) ((contentW - gap) * 0.44);
        int rightW = contentW - gap - leftW;
        renderSkillPanel(ctx, startX, panelY, leftW, panelBottom);
        renderSuggestPanel(ctx, startX + leftW + gap, panelY, rightW, panelBottom);

        // ── Footer ───────────────────────────────────────────────────────────
        String hint = "§8esc close  §7•  §8press §7R §8to refresh";
        sst(ctx, hint, (this.width - sw(hint, 0.85f)) / 2, this.height - 14, SUBTEXT, 0.85f);
    }

    private void renderSkillPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int bottom) {
        panel(ctx, x, y, x + w, bottom);
        sst(ctx, "§e§lSKILL ROADMAP", x + 10, y + 8, TEXT, 0.85f);
        sst(ctx, "§8weakest first", x + w - sw("§8weakest first", 0.7f) - 10, y + 9, DIM, 0.7f);
        ctx.fill(x + 8, y + 20, x + w - 8, y + 21, BORDER);

        if (skills == null) return;
        int rowY = y + 26, rowH = 18;
        for (OptimizerAnalyzer.SkillRow r : skills) {
            if (rowY + rowH > bottom - 4) break;
            String name = "§f" + cap(r.name);
            sst(ctx, name, x + 10, rowY, TEXT, 0.85f);
            String lvl = r.maxed ? "§aMAX" : "§7Lv §f" + r.level;
            sst(ctx, lvl, x + w - sw(lvl, 0.85f) - 10, rowY, SUBTEXT, 0.85f);
            int barX = x + 10, barY = rowY + 11, barW = w - 20, barH = 4;
            ctx.fill(barX, barY, barX + barW, barY + barH, BAR_BG);
            int fill = (int) (barW * r.frac);
            if (fill > 0) ctx.fill(barX, barY, barX + fill, barY + barH, r.maxed ? OK_GREEN : ACCENT);
            rowY += rowH;
        }
    }

    private void renderSuggestPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int bottom) {
        panel(ctx, x, y, x + w, bottom);
        sst(ctx, "§b§lWHAT TO DO NEXT", x + 10, y + 8, TEXT, 0.85f);
        sst(ctx, "§8nearest milestones", x + w - sw("§8nearest milestones", 0.7f) - 10, y + 9, DIM, 0.7f);
        ctx.fill(x + 8, y + 20, x + w - 8, y + 21, BORDER);

        if (sugg == null) return;
        if (sugg.isEmpty()) {
            String done = "§aEverything's maxed — nice. 🎉";
            sst(ctx, done, x + 10, y + 30, OK_GREEN, 0.85f);
            return;
        }
        int rowY = y + 26, rowH = 23;
        int idx = 1;
        for (OptimizerAnalyzer.Suggestion s : sugg) {
            if (rowY + rowH > bottom - 4) break;
            // rank + label (truncated to fit, leaving room for the reward on the right)
            String num = "§8" + idx + ".";
            sst(ctx, num, x + 8, rowY, DIM, 0.85f);
            String reward = "§8+" + s.sbxp + " SB XP";
            int rewW = sw(reward, 0.7f);
            sst(ctx, reward, x + w - rewW - 10, rowY, DIM, 0.7f);

            int labelX = x + 22;
            int labelMax = (x + w - rewW - 12) - labelX;
            String label = truncate("§f" + s.label, labelMax, 0.85f);
            // colorize the label with its category accent via a leading bar instead of text color
            ctx.fill(x + 8, rowY + 11, x + 11, rowY + 17, s.color);
            sst(ctx, label, labelX, rowY, TEXT, 0.85f);

            // progress bar + numbers
            int barX = x + 22, barY = rowY + 13, barW = w - 32, barH = 4;
            double frac = s.target > 0 ? Math.min(1.0, s.cur / s.target) : 0;
            ctx.fill(barX, barY, barX + barW, barY + barH, BAR_BG);
            int fill = (int) (barW * frac);
            if (fill > 0) ctx.fill(barX, barY, barX + fill, barY + barH, s.color);
            String prog = s.levelLike
                    ? "§7" + (long) s.cur + " §8/ §7" + (long) s.target
                    : "§7" + fmtNum(s.cur) + " §8/ §7" + fmtNum(s.target);
            sst(ctx, prog, x + w - sw(prog, 0.7f) - 10, rowY + 18, SUBTEXT, 0.7f);

            rowY += rowH;
            idx++;
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void panel(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
        ctx.fill(x1, y1, x2, y2, PANEL_BG);
        ctx.fill(x1, y1, x2, y1 + 2, ACCENT);            // top rail
        ctx.fill(x1, y2 - 1, x2, y2, BORDER);
        ctx.fill(x1, y1, x1 + 1, y2, BORDER);
        ctx.fill(x2 - 1, y1, x2, y2, BORDER);
    }

    private void drawChip(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, String value) {
        ctx.fill(x, y, x + w, y + h, CHIP_BG);
        ctx.fill(x, y, x + 2, y + h, ACCENT);            // left rail
        int vw = sw(value, 0.9f);
        sst(ctx, value, x + (w - vw) / 2, y + 4, TEXT, 0.9f);
        String lab = "§7" + label;
        int lw = sw(lab, 0.6f);
        sst(ctx, lab, x + (w - lw) / 2, y + 16, SUBTEXT, 0.6f);
    }

    private void sst(GuiGraphicsExtractor ctx, String s, int x, int y, int color, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(scale, scale);
        ctx.text(this.font, s, 0, 0, color, false);
        ctx.pose().popMatrix();
    }
    private int sw(String s, float scale) { return (int) Math.ceil(this.font.width(s) * scale); }

    private String truncate(String s, int maxW, float scale) {
        if (sw(s, scale) <= maxW) return s;
        while (s.length() > 1 && sw(s + "…", scale) > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String fmtNum(double n) {
        double v = Math.abs(n);
        if (v >= 1_000_000_000d) return String.format("%.2fB", n / 1e9);
        if (v >= 1_000_000d)     return String.format("%.1fM", n / 1e6);
        if (v >= 1_000d)         return String.format("%.1fk", n / 1e3);
        return Long.toString((long) n);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

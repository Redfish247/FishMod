package fishmod.features.streams;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.text.DecimalFormat;
import java.util.List;

/** Overlay showing live Hypixel SkyBlock Twitch streams as a grid of thumbnail cards. */
public class StreamsScreen extends Screen {

    private static final int BG_COLOR     = 0xEE101014;
    private static final int HEADER_COLOR = 0xFF18181F;
    private static final int CARD_COLOR   = 0xFF1B1B22;
    private static final int CARD_HOVER   = 0xFF2A2A3A;
    private static final int THUMB_BG     = 0xFF0A0A0E;
    private static final int BORDER_COLOR = 0xFF2A2A33;
    private static final int TEXT_COLOR   = 0xFFFFFFFF;
    private static final int SUBTEXT      = 0xFF9A9AA8;
    private static final int TWITCH       = 0xFF9146FF;
    private static final int TWITCH_HOVER = 0xFFA970FF;
    private static final int LIVE_RED     = 0xFFE91916;

    private static final int HEADER_H = 44;
    private static final int PADDING  = 16;
    private static final int COLS     = 4;
    private static final int GAP      = 12;
    private static final int INFO_H   = 30; // text area below the thumbnail

    private static final DecimalFormat NUM = new DecimalFormat("#,###");

    private final Screen parent;
    private int scroll = 0;

    public StreamsScreen(Screen parent) {
        super(Text.literal("SkyBlock Streams"));
        this.parent = parent;
        if (TwitchStreams.state() != TwitchStreams.State.LOADING) TwitchStreams.refresh();
    }

    private int contentTop() { return HEADER_H + PADDING; }
    private int cardW()  { return (this.width - PADDING * 2 - GAP * (COLS - 1)) / COLS; }
    private int thumbH() { return cardW() * 9 / 16; }
    private int cardH()  { return thumbH() + INFO_H; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = this.width, h = this.height;
        ctx.fill(0, 0, w, h, BG_COLOR);

        List<TwitchStreams.Stream> list = TwitchStreams.streams();
        TwitchStreams.State st = TwitchStreams.state();

        // ── content grid (scissored) ──
        ctx.enableScissor(0, HEADER_H + 1, w, h);
        int cw = cardW(), th = thumbH(), ch = cardH();
        int top = contentTop() - scroll;

        if (st == TwitchStreams.State.LOADING) {
            ctx.drawText(textRenderer, Text.literal("§7Loading streams…"), PADDING, contentTop(), SUBTEXT, false);
        } else if (st == TwitchStreams.State.DONE && list.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("§7No live Hypixel SkyBlock streams found."), PADDING, contentTop(), SUBTEXT, false);
        }

        for (int i = 0; i < list.size(); i++) {
            int col = i % COLS, rowIdx = i / COLS;
            int x = PADDING + col * (cw + GAP);
            int y = top + rowIdx * (ch + GAP);
            if (y + ch < HEADER_H || y > h) continue; // cull off-screen

            TwitchStreams.Stream s = list.get(i);
            boolean hov = mx >= x && mx <= x + cw && my >= y && my <= y + ch && my > HEADER_H;

            // card background
            ctx.fill(x, y, x + cw, y + ch, hov ? CARD_HOVER : CARD_COLOR);

            // thumbnail
            ctx.fill(x, y, x + cw, y + th, THUMB_BG);
            Identifier tex = TwitchThumbnails.get(s.previewUrl());
            if (tex != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, cw, th, cw, th);
            } else {
                String err = TwitchThumbnails.error(s.previewUrl());
                String msg = err == null ? "§8loading…" : "§cno preview";
                ctx.drawText(textRenderer, Text.literal(msg),
                        x + (cw - textRenderer.getWidth(msg.replaceAll("§.", ""))) / 2, y + th / 2 - 4, SUBTEXT, false);
            }

            // LIVE + viewers pill (top-left of thumbnail)
            String viewers = NUM.format(s.viewers());
            ctx.fill(x + 4, y + 4, x + 4 + 10 + textRenderer.getWidth(viewers) + 8, y + 16, 0xCC000000);
            ctx.fill(x + 8, y + 8, x + 12, y + 12, LIVE_RED);
            ctx.drawText(textRenderer, Text.literal("§f" + viewers), x + 16, y + 6, TEXT_COLOR, false);

            // streamer name
            ctx.drawText(textRenderer, Text.literal((hov ? "§d" : "§f") + trim(s.displayName(), cw - 12)),
                    x + 6, y + th + 5, TEXT_COLOR, false);
            // title (1 line, trimmed)
            ctx.drawText(textRenderer, Text.literal("§7" + trim(s.title(), cw - 12)),
                    x + 6, y + th + 16, SUBTEXT, false);
        }
        ctx.disableScissor();

        // ── header (drawn after grid so it overlaps cleanly) ──
        ctx.fill(0, 0, w, HEADER_H, HEADER_COLOR);
        ctx.fill(0, HEADER_H, w, HEADER_H + 1, BORDER_COLOR);
        ctx.drawText(textRenderer, Text.literal("§dHypixel SkyBlock §fLive Streams"), PADDING, 12, TEXT_COLOR, false);

        String sub;
        if (st == TwitchStreams.State.LOADING) sub = "Loading…";
        else if (st == TwitchStreams.State.ERROR) sub = "§cError: " + TwitchStreams.error();
        else sub = list.size() + " live" + (TwitchStreams.fullCoverage() ? "" : " (limited — proxy unavailable)");
        ctx.drawText(textRenderer, Text.literal("§7" + sub), PADDING, 26, SUBTEXT, false);

        drawButton(ctx, mx, my, refreshX(), 12, 70, 20, "Refresh", TWITCH, TWITCH_HOVER);
        drawButton(ctx, mx, my, closeX(), 12, 60, 20, "Close", 0xFF55555F, 0xFF6A6A77);

        super.render(ctx, mx, my, delta);
    }

    private int refreshX() { return this.width - PADDING - 60 - 8 - 70; }
    private int closeX()   { return this.width - PADDING - 60; }

    private void drawButton(DrawContext ctx, int mx, int my, int x, int y, int bw, int bh,
                            String label, int col, int hover) {
        boolean hov = mx >= x && mx <= x + bw && my >= y && my <= y + bh;
        ctx.fill(x, y, x + bw, y + bh, hov ? hover : col);
        ctx.drawText(textRenderer, label, x + (bw - textRenderer.getWidth(label)) / 2, y + (bh - 8) / 2, 0xFFFFFFFF, false);
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();

        if (hit(mx, my, refreshX(), 12, 70, 20)) { TwitchStreams.refresh(); return true; }
        if (hit(mx, my, closeX(), 12, 60, 20)) { MinecraftClient.getInstance().setScreen(parent); return true; }

        // card clicks
        List<TwitchStreams.Stream> list = TwitchStreams.streams();
        int cw = cardW(), ch = cardH();
        int top = contentTop() - scroll;
        for (int i = 0; i < list.size(); i++) {
            int col = i % COLS, rowIdx = i / COLS;
            int x = PADDING + col * (cw + GAP);
            int y = top + rowIdx * (ch + GAP);
            if (my > HEADER_H && mx >= x && mx <= x + cw && my >= y && my <= y + ch) {
                Util.getOperatingSystem().open(list.get(i).url());
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    private boolean hit(int mx, int my, int x, int y, int bw, int bh) {
        return mx >= x && mx <= x + bw && my >= y && my <= y + bh;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horiz, double v) {
        int rows = (TwitchStreams.streams().size() + COLS - 1) / COLS;
        int contentH = rows * (cardH() + GAP);
        int maxScroll = Math.max(0, contentH - (this.height - contentTop()) + PADDING);
        scroll = MathHelper.clamp((int) (scroll - v * 28), 0, maxScroll);
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}

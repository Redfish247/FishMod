package fishmod.features;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Small centered credits panel (matches the FishMod overlay style) with a clickable Discord link. */
public class CreditsScreen extends Screen {

    private static final int ACCENT       = 0xFF24B6B0;
    private static final int ACCENT_HOVER = 0xFF3AD8D1;
    private static final int BG_TOP       = 0xFF0C1318;
    private static final int BG_BOT       = 0xFF06090C;
    private static final int BORDER       = 0xFF24333C;
    private static final int DIVIDER      = 0xFF18222C;
    private static final int TEXT         = 0xFFEDF1F5;
    private static final int SUBTEXT      = 0xFF7E8A98;
    private static final int SCRIM        = 0xB3000000;
    private static final int DISCORD_BLURPLE = 0xFF5865F2;

    private static final String DISCORD     = "discord.gg/3mSuQUB8kk";
    private static final String DISCORD_URL = "https://discord.gg/3mSuQUB8kk";

    private final Screen parent;

    // hit rects (set during render, read on click)
    private int linkX, linkY, linkW, linkH;
    private int backX, backY, backW, backH;

    public CreditsScreen(Screen parent) {
        super(Component.literal("Credits"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d) { }
    @Override public void extractTransparentBackground(GuiGraphicsExtractor ctx) { }

    private int pw() { return Math.min(360, this.width  - 20); }
    private int ph() { return Math.min(232, this.height - 20); }
    private int px() { return (this.width  - pw()) / 2; }
    private int py() { return (this.height - ph()) / 2; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, SCRIM);

        int lx = px(), ty = py(), rx = lx + pw(), by = ty + ph();
        int cx = (lx + rx) / 2;
        ctx.fillGradient(lx, ty, rx, by, BG_TOP, BG_BOT);
        ctx.fill(lx, ty, rx, ty + 1, BORDER);
        ctx.fill(lx, by - 1, rx, by, BORDER);
        ctx.fill(lx, ty, lx + 1, by, BORDER);
        ctx.fill(rx - 1, ty, rx, by, BORDER);

        // wordmark
        ctx.centeredText(this.font, Component.literal("§lFish§b§lMod"), cx, ty + 14, TEXT);
        ctx.centeredText(this.font, Component.literal("Credits"), cx, ty + 26, SUBTEXT);
        ctx.fill(lx + 24, ty + 40, rx - 24, ty + 41, DIVIDER);

        int y = ty + 52;
        drawCredit(ctx, lx + 26, y, "RedFish", "creator — everything else");        y += 28;
        drawCredit(ctx, lx + 26, y, "BladeMasterGabe", "splits & dungeon features");  y += 28;
        drawCredit(ctx, lx + 26, y, "Sushiest", "dungeon help & UI changes");         y += 28;

        // Discord link button
        linkW = this.font.width(DISCORD) + 24;
        linkH = 18;
        linkX = cx - linkW / 2;
        linkY = by - 60;
        boolean linkHov = inside(mouseX, mouseY, linkX, linkY, linkW, linkH);
        ctx.fill(linkX, linkY, linkX + linkW, linkY + linkH, linkHov ? 0xFF1B2733 : 0xFF131B22);
        ctx.fill(linkX, linkY, linkX + linkW, linkY + 1, linkHov ? ACCENT_HOVER : DISCORD_BLURPLE);
        ctx.centeredText(this.font,
                Component.literal((linkHov ? "§b" : "§9") + DISCORD), cx, linkY + 5, DISCORD_BLURPLE);

        // Back button
        backW = 72; backH = 22;
        backX = cx - backW / 2; backY = by - 32;
        boolean backHov = inside(mouseX, mouseY, backX, backY, backW, backH);
        ctx.fill(backX, backY, backX + backW, backY + backH, backHov ? ACCENT_HOVER : ACCENT);
        ctx.centeredText(this.font, Component.literal("Back"), cx, backY + (backH - 8) / 2, 0xFF052A29);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawCredit(GuiGraphicsExtractor ctx, int x, int y, String name, String role) {
        ctx.text(this.font, Component.literal(name), x, y, TEXT, false);
        ctx.text(this.font, Component.literal("§7" + role), x + 6, y + 11, SUBTEXT, false);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        int mx = (int) click.x(), my = (int) click.y();
        if (inside(mx, my, backX, backY, backW, backH)) { onClose(); return true; }
        if (inside(mx, my, linkX, linkY, linkW, linkH)) {
            try { Util.getPlatform().openUri(DISCORD_URL); } catch (Throwable ignored) {}
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

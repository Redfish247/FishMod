package fishmod.utils.rendering;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;

import java.util.ArrayList;
import java.util.List;

/**
 * Records draw commands issued while FishModScreen.render() populates the vanilla
 * GuiRenderState, then replays them against the real NanoVG context later in the frame (see
 * GameRendererNvgMixin / FishModScreen.paintNvgOverlay). NanoVG can't draw immediately during
 * render() since that call only builds a deferred descriptor, not immediate GL — see the plan's
 * timing finding. Static (not instance state) because the Setting subclasses that call into this
 * are static nested classes with no outer-FishModScreen reference.
 */
public final class NvgRecorder {

    private static final List<Runnable> commands = new ArrayList<>();
    private static final NVGColor colorA = NVGColor.create();
    private static final NVGColor colorB = NVGColor.create();

    private NvgRecorder() {}

    public static void clear() { commands.clear(); }
    public static void replay() { for (Runnable r : commands) r.run(); }
    private static void record(Runnable r) { commands.add(r); }

    private static NVGColor argb(int argb, NVGColor out) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return out.r(r).g(g).b(b).a(a);
    }

    // ----- shapes -----

    public static void fillRoundedRect(float x, float y, float w, float h, float r, int color) {
        record(() -> {
            long ctx = NvgContext.get();
            NanoVG.nvgBeginPath(ctx);
            NanoVG.nvgRoundedRect(ctx, x, y, w, h, r);
            NanoVG.nvgFillColor(ctx, argb(color, colorA));
            NanoVG.nvgFill(ctx);
        });
    }

    public static void fillRect(float x, float y, float w, float h, int color) {
        fillRoundedRect(x, y, w, h, 0, color);
    }

    /** Hollow ring stroked in {@code ringColor} around a rect filled with {@code fillColor}. */
    public static void roundedRectRing(float x, float y, float w, float h, float r, float strokeW, int fillColor, int ringColor) {
        record(() -> {
            long ctx = NvgContext.get();
            float half = strokeW / 2f;
            NanoVG.nvgBeginPath(ctx);
            NanoVG.nvgRoundedRect(ctx, x + half, y + half, w - strokeW, h - strokeW, Math.max(0, r - half));
            NanoVG.nvgFillColor(ctx, argb(fillColor, colorA));
            NanoVG.nvgFill(ctx);
            NanoVG.nvgStrokeWidth(ctx, strokeW);
            NanoVG.nvgStrokeColor(ctx, argb(ringColor, colorA));
            NanoVG.nvgStroke(ctx);
        });
    }

    public static void disc(float cx, float cy, float r, int color) {
        record(() -> {
            long ctx = NvgContext.get();
            NanoVG.nvgBeginPath(ctx);
            NanoVG.nvgCircle(ctx, cx, cy, r);
            NanoVG.nvgFillColor(ctx, argb(color, colorA));
            NanoVG.nvgFill(ctx);
        });
    }

    /** Soft drop shadow behind a rounded rect — draw before the rect itself so the opaque
     *  rect covers the shadow's center, leaving only the soft edge visible around it. */
    public static void dropShadow(float x, float y, float w, float h, float r, float spread, int shadowColor) {
        record(() -> {
            long ctx = NvgContext.get();
            NVGPaint paint = NVGPaint.calloc();
            try {
                NVGColor from = argb(shadowColor, colorA);
                NVGColor to = argb(shadowColor & 0x00FFFFFF, colorB);
                NanoVG.nvgBoxGradient(ctx, x, y + spread * 0.5f, w, h, r + spread * 0.5f, spread, from, to, paint);
                NanoVG.nvgBeginPath(ctx);
                NanoVG.nvgRect(ctx, x - spread, y - spread, w + spread * 2, h + spread * 2);
                NanoVG.nvgFillPaint(ctx, paint);
                NanoVG.nvgFill(ctx);
            } finally {
                paint.free();
            }
        });
    }

    /** Vertical linear-gradient fill over a rect, top color to bottom color. */
    public static void fillRectVGradient(float x, float y, float w, float h, int topColor, int botColor) {
        record(() -> {
            long ctx = NvgContext.get();
            NVGPaint paint = NVGPaint.calloc();
            try {
                NVGColor from = argb(topColor, colorA);
                NVGColor to = argb(botColor, colorB);
                NanoVG.nvgLinearGradient(ctx, x, y, x, y + h, from, to, paint);
                NanoVG.nvgBeginPath(ctx);
                NanoVG.nvgRect(ctx, x, y, w, h);
                NanoVG.nvgFillPaint(ctx, paint);
                NanoVG.nvgFill(ctx);
            } finally {
                paint.free();
            }
        });
    }

    /** Small filled triangle: pointing down when {@code open}, right when closed. */
    public static void chevron(float gx, float cy, boolean open, int color) {
        record(() -> {
            long ctx = NvgContext.get();
            NanoVG.nvgBeginPath(ctx);
            if (open) {
                NanoVG.nvgMoveTo(ctx, gx, cy - 2.5f);
                NanoVG.nvgLineTo(ctx, gx + 7, cy - 2.5f);
                NanoVG.nvgLineTo(ctx, gx + 3.5f, cy + 3);
            } else {
                NanoVG.nvgMoveTo(ctx, gx, cy - 3.5f);
                NanoVG.nvgLineTo(ctx, gx, cy + 3.5f);
                NanoVG.nvgLineTo(ctx, gx + 5, cy);
            }
            NanoVG.nvgClosePath(ctx);
            NanoVG.nvgFillColor(ctx, argb(color, colorA));
            NanoVG.nvgFill(ctx);
        });
    }

    // ----- text -----

    public static void text(String s, float x, float y, float size, int color) {
        record(() -> {
            long ctx = NvgContext.get();
            NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME);
            NanoVG.nvgFontSize(ctx, size);
            NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP);
            NanoVG.nvgFillColor(ctx, argb(color, colorA));
            NanoVG.nvgText(ctx, x, y, s);
        });
    }

    /** Text width at a given size, for layout/centering/truncation — must be measured with
     *  NanoVG's own font metrics since it draws with a different font than Minecraft's Font. */
    public static float textWidth(String s, float size) {
        long ctx = NvgContext.get();
        NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME);
        NanoVG.nvgFontSize(ctx, size);
        float[] bounds = new float[4];
        return NanoVG.nvgTextBounds(ctx, 0, 0, s, bounds);
    }

    // ----- scissor (nested via NanoVG's own save/restore state stack) -----

    public static void pushScissor(float x, float y, float w, float h) {
        record(() -> {
            long ctx = NvgContext.get();
            NanoVG.nvgSave(ctx);
            NanoVG.nvgIntersectScissor(ctx, x, y, w, h);
        });
    }

    public static void popScissor() {
        record(() -> NanoVG.nvgRestore(NvgContext.get()));
    }
}

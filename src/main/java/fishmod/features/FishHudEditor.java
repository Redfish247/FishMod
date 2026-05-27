package fishmod.features;

import fishmod.utils.config.FishConfig;
import config.practical.hud.HUDComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class FishHudEditor extends Screen {

    public record HudEntry(String name,
                           IntSupplier getX, IntConsumer setX,
                           IntSupplier getY, IntConsumer setY,
                           int w, int h,
                           boolean locked,
                           DoubleSupplier getScale, DoubleConsumer setScale,
                           BooleanSupplier visible) {
        HudEntry(String name, IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY, int w, int h) {
            this(name, getX, setX, getY, setY, w, h, false, null, null, null);
        }
        HudEntry(String name, IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY, int w, int h, boolean locked) {
            this(name, getX, setX, getY, setY, w, h, locked, null, null, null);
        }
        HudEntry(String name, IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY, int w, int h, boolean locked, DoubleSupplier gs, DoubleConsumer ss) {
            this(name, getX, setX, getY, setY, w, h, locked, gs, ss, null);
        }
        public double scale() { return getScale != null ? getScale.getAsDouble() : 1.0; }
        public boolean isVisible() { return visible == null || visible.getAsBoolean(); }
    }

    private static final List<HudEntry> ENTRIES = new ArrayList<>();

    public static void register(String name,
                                IntSupplier getX, IntConsumer setX,
                                IntSupplier getY, IntConsumer setY,
                                int w, int h) {
        ENTRIES.add(new HudEntry(name, getX, setX, getY, setY, w, h));
    }

    /** Register with a scale getter/setter so the editor can scroll-resize the element. */
    public static void register(String name,
                                IntSupplier getX, IntConsumer setX,
                                IntSupplier getY, IntConsumer setY,
                                int w, int h,
                                DoubleSupplier getScale, DoubleConsumer setScale) {
        ENTRIES.add(new HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, null));
    }

    /** Register with scale + visibility predicate — only shown when the HUD is actually rendering. */
    public static void register(String name,
                                IntSupplier getX, IntConsumer setX,
                                IntSupplier getY, IntConsumer setY,
                                int w, int h,
                                DoubleSupplier getScale, DoubleConsumer setScale,
                                BooleanSupplier visible) {
        ENTRIES.add(new HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, visible));
    }

    /** Register a read-only locked entry that shows where a HUD element will appear but can't be dragged. */
    public static void registerLocked(String name, IntSupplier getX, IntSupplier getY, int w, int h) {
        ENTRIES.add(new HudEntry(name, getX, v -> {}, getY, v -> {}, w, h, true));
    }

    /** Register a HUDComponent — position get/set bridges through component.move(). */
    public static void register(String name, HUDComponent component) {
        ENTRIES.add(new HudEntry(name,
            component::getScaledX,
            v -> {
                int ww = MinecraftClient.getInstance().getWindow().getScaledWidth();
                component.move((double)(v - component.getScaledX()) * component.getScale() / ww, 0);
            },
            component::getScaledY,
            v -> {
                int wh = MinecraftClient.getInstance().getWindow().getScaledHeight();
                component.move(0, (double)(v - component.getScaledY()) * component.getScale() / wh);
            },
            component.getWidth(), component.getHeight(),
            false,
            () -> (double) component.getScale(),
            v -> component.setScale((float) v)
        ));
    }

    private static final int ACCENT       = 0xFF00AACC;
    private static final int ACCENT_HOVER = 0xFF00CCEE;
    private static final int BOX_FILL     = 0x5500AACC;
    private static final int BOX_HOVER    = 0x7700AACC;
    private static final int BOX_DRAG     = 0x9900CCEE;

    private final Screen parent;
    private HudEntry dragging = null;
    private int dragOffX, dragOffY;

    public FishHudEditor(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Drag elements to reposition", this.width / 2, 10, 0xFFAAAAAA);

        // Done button
        int btnW = 60, btnH = 18;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height - 28;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW
                      && mouseY >= btnY && mouseY <= btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? ACCENT_HOVER : ACCENT);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Done",
                btnX + btnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF);

        // HUD element boxes — only show entries that are actually active right now (feature enabled
        // and in the right context). Hiding inactive ones keeps the editor uncluttered so elements
        // don't overlap and get moved by accident.
        for (HudEntry e : ENTRIES) {
            if (!e.isVisible()) continue;
            boolean active = true;
            int x = e.getX().getAsInt();
            int y = e.getY().getAsInt();
            int scaledW = Math.max(8, (int)(e.w() * e.scale()));
            int scaledH = Math.max(8, (int)(e.h() * e.scale()));
            boolean hov = !e.locked() && mouseX >= x && mouseX <= x + scaledW
                       && mouseY >= y && mouseY <= y + scaledH;
            boolean drag = e == dragging;

            int fill    = e.locked() ? 0x33888888 : (drag ? BOX_DRAG : (hov ? BOX_HOVER : (active ? BOX_FILL : 0x33444444)));
            int outline = e.locked() ? 0xFF555555 : (drag ? ACCENT_HOVER : (active ? ACCENT : 0xFF666666));

            ctx.fill(x, y, x + scaledW, y + scaledH, fill);
            ctx.fill(x,             y,             x + scaledW, y + 1,           outline);
            ctx.fill(x,             y + scaledH-1, x + scaledW, y + scaledH,     outline);
            ctx.fill(x,             y,             x + 1,       y + scaledH,     outline);
            ctx.fill(x + scaledW-1, y,             x + scaledW, y + scaledH,     outline);

            int labelX = x + 3;
            int labelY = y + (scaledH - 8) / 2;
            int labelColor = e.locked() ? 0xFF888888 : (active ? 0xFFFFFFFF : 0xFFAAAAAA);
            String label = e.name() + (e.scale() != 1.0 ? String.format(" §7(%.2fx)", e.scale()) : "");
            ctx.drawText(this.textRenderer, label, labelX, labelY, labelColor, true);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();

        int btnW = 60, btnH = 18;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height - 28;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            this.close();
            return true;
        }

        for (HudEntry e : ENTRIES) {
            if (e.locked() || !e.isVisible()) continue;
            int x = e.getX().getAsInt();
            int y = e.getY().getAsInt();
            if (mx >= x && mx <= x + e.w() && my >= y && my <= y + e.h()) {
                dragging = e;
                dragOffX = mx - x;
                dragOffY = my - y;
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging != null) {
            int nx = (int) click.x() - dragOffX;
            int ny = (int) click.y() - dragOffY;
            nx = Math.max(0, Math.min(this.width  - dragging.w(), nx));
            ny = Math.max(0, Math.min(this.height - dragging.h(), ny));
            dragging.setX().accept(nx);
            dragging.setY().accept(ny);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (HudEntry e : ENTRIES) {
            if (e.locked() || e.setScale == null || !e.isVisible()) continue;
            int x = e.getX().getAsInt();
            int y = e.getY().getAsInt();
            int scaledW = Math.max(8, (int)(e.w() * e.scale()));
            int scaledH = Math.max(8, (int)(e.h() * e.scale()));
            if (mouseX >= x && mouseX <= x + scaledW && mouseY >= y && mouseY <= y + scaledH) {
                double cur = e.scale();
                double step = vertical > 0 ? 0.1 : -0.1;
                double next = Math.max(0.5, Math.min(3.0, Math.round((cur + step) * 100.0) / 100.0));
                e.setScale.accept(next);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void close() {
        FishConfig.manager.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}

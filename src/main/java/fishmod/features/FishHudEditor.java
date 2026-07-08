package fishmod.features;

import fishmod.utils.config.FishConfig;
import config.practical.hud.HUDComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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

    /**
     * Register a HUDComponent — position get/set bridges through component.move().
     * <p>We work in TRUE pixel space ({@code getScaledX() * scale == x * screenWidth}), not raw
     * {@code getScaledX()}: the latter is scale-dependent, so the editor box would drift when you
     * resize. Using the scale-independent pixel position keeps the box anchored at its top-left
     * while scaling — matching where the HUD actually renders.
     */
    public static void register(String name, HUDComponent component) {
        ENTRIES.add(new HudEntry(name,
            () -> Math.round(component.getScaledX() * component.getScale()),
            v -> {
                int ww = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int cur = Math.round(component.getScaledX() * component.getScale());
                component.move((double)(v - cur) / ww, 0);
            },
            () -> Math.round(component.getScaledY() * component.getScale()),
            v -> {
                int wh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                int cur = Math.round(component.getScaledY() * component.getScale());
                component.move(0, (double)(v - cur) / wh);
            },
            component.getWidth(), component.getHeight(),
            false,
            () -> (double) component.getScale(),
            v -> component.setScale((float) v)
        ));
    }

    /**
     * Default position (pixels) and scale for each registered HUD, mirroring the defaults in its
     * {@code register(...)} call (FishSettings field initializers / HUDComponent constructors).
     * Used by the "Reset positions" button. Keep in sync when a HUD's default changes.
     */
    private static final java.util.Map<String, double[]> DEFAULTS = java.util.Map.ofEntries(
            java.util.Map.entry("Farming Coins",      new double[]{10, 240, 1.0}),
            java.util.Map.entry("Pet",                new double[]{10,  80, 1.0}),
            java.util.Map.entry("Harvest Feast",      new double[]{10, 280, 1.0}),
            java.util.Map.entry("Session Stats",      new double[]{10, 120, 1.0}),
            java.util.Map.entry("Dungeon Score",      new double[]{10, 200, 1.0}),
            java.util.Map.entry("Simon Says",         new double[]{10, 360, 1.0}),
            java.util.Map.entry("Soulflow",           new double[]{10,  60, 1.0}),
            java.util.Map.entry("Mining Coins",       new double[]{10, 320, 1.0}),
            java.util.Map.entry("Trophy Frogs",       new double[]{10,  60, 1.0}),
            java.util.Map.entry("Bobber Reminder",    new double[]{10, 140, 1.5}),
            java.util.Map.entry("Sea Creatures",      new double[]{10, 160, 1.0}),
            java.util.Map.entry("Trophy Fish",        new double[]{10, 200, 1.0}),
            java.util.Map.entry("Slayer Drops",       new double[]{10, 240, 1.0}),
            java.util.Map.entry("Desk-Buddy",         new double[]{10, 440, 1.5}),
            java.util.Map.entry("PB Pace",            new double[]{10, 300, 1.0}),
            java.util.Map.entry("Slayer XP",          new double[]{10,  80, 1.0}),
            java.util.Map.entry("Skill XP",           new double[]{10, 360, 1.0}),
            java.util.Map.entry("Powder",             new double[]{10, 100, 1.0}),
            java.util.Map.entry("Challenges",         new double[]{10, 400, 1.0}),
            java.util.Map.entry("Maxor Tick Timer",   new double[]{10,  80, 1.0}),
            java.util.Map.entry("Crystal Spawn Time", new double[]{10,  92, 1.0}),
            java.util.Map.entry("Crystal Reminder",   new double[]{ 0,   0, 1.0}),
            java.util.Map.entry("Storm Tick Timer",   new double[]{10,  80, 1.0}),
            java.util.Map.entry("Storm Death Time",   new double[]{10,  92, 1.0}),
            java.util.Map.entry("Storm Crushed",      new double[]{ 0,   0, 1.0}),
            java.util.Map.entry("Goldor Tick Timer",  new double[]{10,  80, 1.0}),
            java.util.Map.entry("Term Start Timer",   new double[]{10, 104, 1.0}),
            java.util.Map.entry("Section Progress",   new double[]{10, 116, 1.0}),
            java.util.Map.entry("Splits",             new double[]{ 0,   0, 1.0}),
            java.util.Map.entry("Puzzles",            new double[]{ 0,   0, 1.0}));

    /** Restore every registered HUD to its default position/scale and persist the change. */
    public static void resetAll() {
        for (HudEntry e : ENTRIES) {
            double[] d = DEFAULTS.get(e.name());
            if (d == null || e.locked()) continue;
            e.setX().accept((int) d[0]);
            e.setY().accept((int) d[1]);
            if (e.setScale != null) e.setScale.accept(d[2]);
        }
        FishConfig.manager.save();
    }

    private static final int RESET_X = 10;
    private static final int RESET_W = 120;

    private static final int ACCENT       = 0xFF00AACC;
    private static final int ACCENT_HOVER = 0xFF00CCEE;
    private static final int BOX_FILL     = 0x5500AACC;
    private static final int BOX_HOVER    = 0x7700AACC;
    private static final int BOX_DRAG     = 0x9900CCEE;

    private final Screen parent;
    private HudEntry dragging = null;
    private int dragOffX, dragOffY;
    /** First click on Reset arms it; a second click confirms. Any drag/other click disarms. */
    private boolean resetArmed = false;

    public FishHudEditor(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000);

        ctx.drawCenteredString(this.font,
                "Drag to move · scroll to resize", this.width / 2, 10, 0xFFAAAAAA);

        // Done button
        int btnW = 60, btnH = 18;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height - 28;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW
                      && mouseY >= btnY && mouseY <= btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? ACCENT_HOVER : ACCENT);
        ctx.drawCenteredString(this.font, "Done",
                btnX + btnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF);

        // Reset-positions button (bottom-left). First click arms, second click confirms.
        boolean rHov = mouseX >= RESET_X && mouseX <= RESET_X + RESET_W
                    && mouseY >= btnY && mouseY <= btnY + btnH;
        int rFill = resetArmed ? 0xFFAA3333 : (rHov ? 0xFF553333 : 0xFF442222);
        ctx.fill(RESET_X, btnY, RESET_X + RESET_W, btnY + btnH, rFill);
        ctx.drawCenteredString(this.font,
                resetArmed ? "§fClick to confirm" : "Reset positions",
                RESET_X + RESET_W / 2, btnY + (btnH - 8) / 2, 0xFFFFCCCC);

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

            int labelColor = e.locked() ? 0xFF888888 : (active ? 0xFFFFFFFF : 0xFFAAAAAA);
            String label = e.name() + (e.scale() != 1.0 ? String.format(" §7(%.2fx)", e.scale()) : "");
            int labelW = this.font.width(label);
            int labelX, labelY;
            if (labelW + 6 <= scaledW) {
                // fits inside the box
                labelX = x + 3;
                labelY = y + (scaledH - 8) / 2;
            } else {
                // too wide for the box — drop the label just below it (or above if it would run off
                // the bottom of the screen), with a dark backing so it stays readable over other boxes
                labelX = x;
                labelY = (y + scaledH + 10 <= this.height) ? y + scaledH + 1 : y - 10;
                ctx.fill(labelX - 1, labelY - 1, labelX + labelW + 1, labelY + 9, 0xC0000000);
            }
            ctx.drawString(this.font, label, labelX, labelY, labelColor, true);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();

        int btnW = 60, btnH = 18;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height - 28;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            this.onClose();
            return true;
        }

        // Reset-positions button: first click arms, second click performs the reset.
        if (mx >= RESET_X && mx <= RESET_X + RESET_W && my >= btnY && my <= btnY + btnH) {
            if (resetArmed) { resetAll(); resetArmed = false; }
            else resetArmed = true;
            return true;
        }
        resetArmed = false; // any other click disarms the confirm

        for (HudEntry e : ENTRIES) {
            if (e.locked() || !e.isVisible()) continue;
            int x = e.getX().getAsInt();
            int y = e.getY().getAsInt();
            int sw = Math.max(8, (int)(e.w() * e.scale()));
            int sh = Math.max(8, (int)(e.h() * e.scale()));
            if (mx >= x && mx <= x + sw && my >= y && my <= y + sh) {
                dragging = e;
                dragOffX = mx - x;
                dragOffY = my - y;
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dragging != null) {
            int nx = (int) click.x() - dragOffX;
            int ny = (int) click.y() - dragOffY;
            int sw = Math.max(8, (int)(dragging.w() * dragging.scale()));
            int sh = Math.max(8, (int)(dragging.h() * dragging.scale()));
            nx = Math.max(0, Math.min(this.width  - sw, nx));
            ny = Math.max(0, Math.min(this.height - sh, ny));
            dragging.setX().accept(nx);
            dragging.setY().accept(ny);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
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
    public void onClose() {
        FishConfig.manager.save();
        Minecraft.getInstance().setScreen(parent);
    }
}

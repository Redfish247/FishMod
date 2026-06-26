package fishmod.features.dungeon;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.DungeonClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Odin-style Spirit Leap overlay. Replaces the vanilla "Spirit Leap" chest with a clean panel of the
 * teammates you can leap to — each as a head + class-colored name, sorted by class, with a number-key
 * (1-5) and click to leap instantly. Pure Fabric screen events (no mixins): it paints over the vanilla
 * menu and routes input to the underlying slots.
 */
public final class LeapOverlay {
    private LeapOverlay() {}

    // Odin's leap ordering.
    private static final DungeonClass[] ORDER = {
        DungeonClass.HEALER, DungeonClass.MAGE, DungeonClass.BERSERK, DungeonClass.ARCHER, DungeonClass.TANK
    };

    private static final class Cell {
        final int slot; final String name; final DungeonClass clazz;
        int x, y, w, h;
        Cell(int slot, String name, DungeonClass clazz) { this.slot = slot; this.name = name; this.clazz = clazz; }
    }

    private static final List<Cell> cells = new ArrayList<>();
    private static final boolean[] keyDown = new boolean[5]; // edge-detection for number keys 1-5

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof GenericContainerScreen gc)) return;
            if (!isLeapMenu(gc)) return;

            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                if (!FishSettings.leapOverlayEnabled) return;
                rebuild(gc);
                render(ctx, gc, mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                if (!FishSettings.leapOverlayEnabled) return true;
                if (click.button() != 0) return true;
                Cell c = at((int) click.x(), (int) click.y());
                if (c != null) leap(gc, c.slot);
                return false; // swallow all clicks so the covered vanilla slots never fire
            });
        });

        // Number-key leaping (1-5). Polled via InputUtil with edge-detection — the Fabric keyboard
        // event's record signature is mapping-volatile, so we read raw GLFW state instead.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.leapOverlayEnabled
                    || !(mc.currentScreen instanceof GenericContainerScreen gc) || !isLeapMenu(gc)) {
                Arrays.fill(keyDown, false);
                return;
            }
            rebuild(gc);
            net.minecraft.client.util.Window window = mc.getWindow();
            for (int i = 0; i < keyDown.length; i++) {
                boolean down = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_1 + i);
                if (down && !keyDown[i] && i < cells.size()) leap(gc, cells.get(i).slot);
                keyDown[i] = down;
            }
        });
    }

    private static boolean isLeapMenu(GenericContainerScreen gc) {
        String title = gc.getTitle().getString().replaceAll("§.", "").trim();
        return title.equalsIgnoreCase("Spirit Leap");
    }

    /** Rebuilds the sorted leap targets from the chest's player-head slots. */
    private static void rebuild(GenericContainerScreen gc) {
        cells.clear();
        GenericContainerScreenHandler h = gc.getScreenHandler();
        int chestSlots = h.getRows() * 9;
        List<Cell> found = new ArrayList<>();
        for (int i = 0; i < chestSlots && i < h.slots.size(); i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (st == null || st.isEmpty() || !st.isOf(Items.PLAYER_HEAD)) continue;
            String name = st.getName().getString().replaceAll("§.", "").trim();
            if (name.isEmpty()) continue;
            found.add(new Cell(i, name, DungeonClass.getClass(name)));
        }
        // Sort by Odin's class order; unknown classes fall to the end, then by name.
        found.sort((a, b) -> {
            int oa = order(a.clazz), ob = order(b.clazz);
            return oa != ob ? Integer.compare(oa, ob) : a.name.compareToIgnoreCase(b.name);
        });
        cells.addAll(found);
    }

    private static int order(DungeonClass c) {
        if (c == null) return ORDER.length;
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == c) return i;
        return ORDER.length;
    }

    private static void render(DrawContext ctx, GenericContainerScreen gc, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = gc.width, sh = gc.height;

        // Opaque backdrop so the vanilla chest underneath is fully hidden.
        ctx.fill(0, 0, sw, sh, 0xF00E1016);

        int n = cells.size();
        ctx.drawCenteredTextWithShadow(mc.textRenderer, "§b§lSpirit Leap", sw / 2, sh / 2 - (rows(n) * 46) / 2 - 22, 0xFFFFFFFF);
        if (n == 0) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "§7No teammates to leap to", sw / 2, sh / 2, 0xFFAAAAAA);
            return;
        }

        int cols = n <= 1 ? 1 : 2;
        int cw = 150, ch = 42, gap = 8;
        int rows = rows(n);
        int gridW = cols * cw + (cols - 1) * gap;
        int gridH = rows * ch + (rows - 1) * gap;
        int startX = (sw - gridW) / 2;
        int startY = (sh - gridH) / 2;

        for (int i = 0; i < n; i++) {
            Cell c = cells.get(i);
            int col = i % cols, row = i / cols;
            c.x = startX + col * (cw + gap);
            c.y = startY + row * (ch + gap);
            c.w = cw; c.h = ch;

            boolean hov = mx >= c.x && mx <= c.x + cw && my >= c.y && my <= c.y + ch;
            ctx.fill(c.x - 1, c.y - 1, c.x + cw + 1, c.y + ch + 1, hov ? 0xFF55FFFF : 0xFF2A2D38);
            ctx.fill(c.x, c.y, c.x + cw, c.y + ch, 0xFF171A22);

            ItemStack head = headStack(gc, c.slot);
            if (head != null) ctx.drawItem(head, c.x + 6, c.y + ch / 2 - 8);

            int classColor = c.clazz != null ? (0xFF000000 | (DungeonClass.getColor(c.clazz) & 0xFFFFFF)) : 0xFFFFFFFF;
            ctx.drawTextWithShadow(mc.textRenderer, c.name, c.x + 28, c.y + 8, classColor);
            String cls = c.clazz != null ? cap(c.clazz.name()) : "?";
            ctx.drawTextWithShadow(mc.textRenderer, "§7" + cls, c.x + 28, c.y + 22, 0xFFAAAAAA);
            if (i < 9) ctx.drawTextWithShadow(mc.textRenderer, "§e[" + (i + 1) + "]", c.x + cw - 22, c.y + ch / 2 - 4, 0xFFFFFF55);
        }
    }

    private static int rows(int n) {
        int cols = n <= 1 ? 1 : 2;
        return Math.max(1, (n + cols - 1) / cols);
    }

    private static ItemStack headStack(GenericContainerScreen gc, int slot) {
        GenericContainerScreenHandler h = gc.getScreenHandler();
        return slot >= 0 && slot < h.slots.size() ? h.slots.get(slot).getStack() : null;
    }

    private static Cell at(int mx, int my) {
        for (Cell c : cells) if (mx >= c.x && mx <= c.x + c.w && my >= c.y && my <= c.y + c.h) return c;
        return null;
    }

    private static void leap(GenericContainerScreen gc, int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(gc.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }
}

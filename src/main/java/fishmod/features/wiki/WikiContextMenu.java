package fishmod.features.wiki;

import fishmod.utils.data.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class WikiContextMenu {

    private static boolean active = false;
    private static int     menuX, menuY;
    private static String  displayName = "";
    private static String  wikiQuery   = "";

    private static final int W  = 108;
    private static final int H  = 22;
    private static final int BG = 0xF0111827;
    private static final int BD = 0xFF2a3a6a;

    public static void show(int x, int y, ItemStack stack) {
        active      = true;
        menuX       = x;
        menuY       = y;
        displayName = stack.getHoverName().getString().replaceAll("§.", "");
        String id   = ItemUtil.getId(stack);
        // Prefer Skyblock item ID (converts underscores to spaces for search)
        wikiQuery   = id != null ? id.replace("_", " ") : displayName;
    }

    public static void hide() { active = false; }

    public static boolean isActive() { return active; }

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (!active || mc.gui.screen() == null) return;
        int sw = mc.gui.screen().width;
        int sh = mc.gui.screen().height;
        // Keep menu on screen
        int rx = Math.min(menuX, sw - W - 2);
        int ry = Math.min(menuY, sh - H - 2);

        ctx.fill(rx, ry, rx + W, ry + H, BG);
        ctx.fill(rx, ry, rx + W, ry + 1, BD);
        ctx.fill(rx, ry, rx + 1, ry + H, BD);
        ctx.fill(rx + W - 1, ry, rx + W, ry + H, BD);
        ctx.fill(rx, ry + H - 1, rx + W, ry + H, BD);

        String label = "§bOpen Wiki§7: §f" + truncate(displayName, 11);
        ctx.text(mc.font, label, rx + 4, ry + (H - 8) / 2, 0xFFFFFFFF);
    }

    public static boolean handleClick(double cx, double cy, Minecraft mc) {
        if (!active) return false;
        int sw = mc.gui.screen() != null ? mc.gui.screen().width  : 0;
        int sh = mc.gui.screen() != null ? mc.gui.screen().height : 0;
        int rx = Math.min(menuX, sw - W - 2);
        int ry = Math.min(menuY, sh - H - 2);

        if (cx >= rx && cx <= rx + W && cy >= ry && cy <= ry + H) {
            mc.gui.setScreen(new WikiScreen(mc.gui.screen(), wikiQuery));
            hide();
            return true;
        }
        hide();
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}

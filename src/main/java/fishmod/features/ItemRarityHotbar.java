package fishmod.features;

import fishmod.utils.Location;
import fishmod.utils.config.values.Visual;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

public class ItemRarityHotbar {
    private static final int COMMON = 0xFFFFFFFF;
    private static final int UNCOMMON = 0xFF55FF55;
    private static final int RARE = 0xFF172452;
    private static final int EPIC = 0xFF241228;
    private static final int LEGENDARY = 0xFFFFAA00;
    private static final int MYTHIC = 0xFF241428;
    private static final int DIVINE = 0xFF2F8E8E;
    private static final int SPECIAL = 0xFFFF5555;
    private static final int SUPREME = 0xFFAA0000;
    private static final int VERY_SPECIAL = 0xFF241428;

    public static void renderHotbar(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!Visual.itemRarityBackground) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;
        if (!Location.inSkyblock()) return;
        if (mc.options.hudHidden) return;
        if (mc.getDebugHud() != null && mc.getDebugHud().shouldShowDebugHud()) return;

        int hotbarX = (mc.getWindow().getScaledWidth() - 182) / 2;
        int hotbarY = mc.getWindow().getScaledHeight() - 22;

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            int color = rarityColor(stack);
            if (color == 0) continue;

            int x = hotbarX + 1 + slot * 20;
            int y = hotbarY + 2;
            drawSlotColor(ctx, x, y, color);
        }
    }

    private static void drawSlotColor(DrawContext ctx, int x, int y, int color) {
        int fill = (color & 0x00FFFFFF) | 0x0A000000;
        ctx.fill(x, y, x + 18, y + 18, fill);
    }

    private static int rarityColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return 0;

        List<Text> lines = lore.lines();
        if (lines.isEmpty()) return 0;

        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).getString().trim().toUpperCase(Locale.ROOT);
            if (line.isEmpty()) continue;

            if (line.startsWith("VERY SPECIAL")) return VERY_SPECIAL;
            if (line.startsWith("SPECIAL")) return SPECIAL;
            if (line.startsWith("SUPREME")) return SUPREME;
            if (line.startsWith("DIVINE")) return DIVINE;
            if (line.startsWith("MYTHIC")) return MYTHIC;
            if (line.startsWith("LEGENDARY")) return LEGENDARY;
            if (line.startsWith("EPIC")) return EPIC;
            if (line.startsWith("RARE")) return RARE;
            if (line.startsWith("UNCOMMON")) return UNCOMMON;
            if (line.startsWith("COMMON")) return COMMON;
        }

        return 0;
    }
}

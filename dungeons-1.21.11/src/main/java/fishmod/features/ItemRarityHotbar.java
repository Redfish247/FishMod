package fishmod.features;

import fishmod.features.item.ItemRarity;
import fishmod.features.item.ItemRarityHolder;
import fishmod.utils.config.values.Visual;
import fishmod.utils.rendering.DrawEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Rarity background — draws a tinted sprite (square or circle) BEHIND every item, coloured by its
 * SkyBlock rarity. Blade-addons' sprite approach: inventory slots via {@link DrawEvents#INVENTORY_SLOT_BEFORE}
 * and the hotbar via {@code DrawContextMixin} (both before the item draw). Rarity is parsed once and
 * cached per ItemStack ({@link ItemRarityHolder}).
 */
public class ItemRarityHotbar {

    private static final Identifier SQUARE = Identifier.of("fishmod", "rarity-background");
    private static final Identifier CIRCLE = Identifier.of("fishmod", "rarity-background-circle");

    // The raw rarity colors are very bright/saturated. Tone them WAY down so the backing is a subtle
    // hint rather than a glaring block: drop to a low alpha and blend halfway toward grey.
    private static final int   TINT_ALPHA = 0xFF;  // ~100% opacity
    private static final float DESATURATE = 0.55f; // 0 = full color, 1 = grey

    /** Inventory coverage — the slot-before event fires for every rendered inventory slot. */
    public static void init() {
        DrawEvents.INVENTORY_SLOT_BEFORE.register(ItemRarityHotbar::drawRarity);
    }

    /** Shared draw: parse (cached) rarity and blit the tinted sprite behind the 16x16 item icon. */
    public static void drawRarity(DrawContext ctx, ItemStack stack, int x, int y) {
        if (!Visual.itemRarityBackground || stack == null || stack.isEmpty()) return;

        ItemRarityHolder holder = (ItemRarityHolder) (Object) stack;
        if (!holder.fishmod$hasScanned()) holder.fishmod$setItemRarity(getRarity(stack));
        if (!holder.fishmod$hasItemRarity()) return;

        Identifier sprite = Visual.circularRarityBackground ? CIRCLE : SQUARE;
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, getTintColor(holder.fishmod$getItemRarity()));
    }

    private static int getTintColor(ItemRarity rarity) {
        int color = rarity.getColor();
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int grey = (red + green + blue) / 3;

        red = desaturate(red, grey);
        green = desaturate(green, grey);
        blue = desaturate(blue, grey);

        return (TINT_ALPHA << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int desaturate(int channel, int grey) {
        return Math.round(channel + (grey - channel) * DESATURATE);
    }

    /** Reads the rarity keyword from the last lore lines (e.g. "LEGENDARY DUNGEON SWORD"). */
    public static ItemRarity getRarity(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return ItemRarity.NONE;
        List<Text> lines = lore.lines();
        if (lines.isEmpty()) return ItemRarity.NONE;
        for (int i = lines.size() - 1; i >= 0; i--) {
            for (String word : lines.get(i).getString().split(" ")) {
                try { return ItemRarity.valueOf(word); } catch (IllegalArgumentException ignored) {}
            }
        }
        return ItemRarity.NONE;
    }
}

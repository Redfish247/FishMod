package fishmod.features.other;

import fishmod.utils.Misc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Clickable command buttons drawn over the survival inventory's empty space — a 1:1 port of
 * blade-addons' InventoryButton. Each button runs a configurable command when clicked and is
 * numbered by registration order. A button with an empty command string renders nothing.
 *
 * <p>Coordinates are relative to the inventory background's top-left; the {@code InventoryScreenMixin}
 * translates the matrix to that origin before calling {@link #renderAll}, and offsets mouse clicks by
 * the same amount before calling {@link #parseClicks}.
 */
public class InventoryButton {

    private static final Identifier BUTTON_TEXTURE = Identifier.ofVanilla("widget/button");
    private static final int SIZE = 18;
    private static final ArrayList<InventoryButton> BUTTONS = new ArrayList<>();

    private final int x;
    private final int y;
    private final int index;
    private final Supplier<String> command;

    public InventoryButton(int x, int y, Supplier<String> command) {
        this.x = x;
        this.y = y;
        this.command = command;
        BUTTONS.add(this);
        this.index = BUTTONS.size();
    }

    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        String str = command.get();
        if (str == null || str.isEmpty()) return;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, x, y, SIZE, SIZE);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        String label = "" + index;
        int center = textRenderer.getWidth(label);
        context.drawText(textRenderer, label,
                x + (SIZE - center) / 2 + 1, y + (SIZE - textRenderer.fontHeight) / 2 + 1, 0xffffffff, true);
    }

    public void onClick() {
        String str = command.get();
        if (str == null || str.isEmpty()) return;
        Misc.executeCommand(str);
    }

    public boolean inBounds(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + SIZE && mouseY >= y && mouseY <= y + SIZE;
    }

    public static void parseClicks(double mouseX, double mouseY) {
        for (InventoryButton button : BUTTONS) {
            if (button.inBounds(mouseX, mouseY)) {
                button.onClick();
            }
        }
    }

    public static void renderAll(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        for (InventoryButton button : BUTTONS) {
            button.render(context, mouseX, mouseY, deltaTicks);
        }
    }
}

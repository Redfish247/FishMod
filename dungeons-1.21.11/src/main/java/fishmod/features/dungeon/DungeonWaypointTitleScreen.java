package fishmod.features.dungeon;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Minimal one-field title-entry screen, opened when sneak-right-clicking to place a waypoint —
 * mirrors OdinLegacy's {@code GuiSign} but built on this mod's own Fabric/Screen APIs.
 */
public class DungeonWaypointTitleScreen extends Screen {

    private final Consumer<String> onSubmit;
    private TextFieldWidget field;

    public DungeonWaypointTitleScreen(Consumer<String> onSubmit) {
        super(Text.literal("Dungeon Waypoint Title"));
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        int w = 220, h = 20;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 10;

        field = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Title"));
        field.setMaxLength(64);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Waypoint"), b -> submit())
                .dimensions(x, y + 26, w, 20).build());
    }

    private void submit() {
        String text = field.getText();
        close();
        if (onSubmit != null) onSubmit.accept(text == null || text.isBlank() ? null : text);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(this.textRenderer, Text.literal("Waypoint title (Enter to confirm):"),
                (this.width - 220) / 2, this.height / 2 - 24, 0xFFFFFFFF, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

package fishmod.features.dungeon;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Minimal one-field title-entry screen, opened when sneak-right-clicking to place a waypoint —
 * mirrors OdinLegacy's {@code GuiSign} but built on this mod's own Fabric/Screen APIs.
 */
public class DungeonWaypointTitleScreen extends Screen {

    private final Consumer<String> onSubmit;
    private EditBox field;

    public DungeonWaypointTitleScreen(Consumer<String> onSubmit) {
        super(Component.literal("Dungeon Waypoint Title"));
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        int w = 220, h = 20;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 10;

        field = new EditBox(this.font, x, y, w, h, Component.literal("Title"));
        field.setMaxLength(64);
        addRenderableWidget(field);
        setInitialFocus(field);

        addRenderableWidget(Button.builder(Component.literal("Add Waypoint"), b -> submit())
                .bounds(x, y + 26, w, 20).build());
    }

    private void submit() {
        String text = field.getValue();
        onClose();
        if (onSubmit != null) onSubmit.accept(text == null || text.isBlank() ? null : text);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.text(this.font, Component.literal("Waypoint title (Enter to confirm):"),
                (this.width - 220) / 2, this.height / 2 - 24, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

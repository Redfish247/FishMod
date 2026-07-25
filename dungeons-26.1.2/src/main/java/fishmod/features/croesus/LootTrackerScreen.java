package fishmod.features.croesus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Standalone /fmloot menu. Just hosts {@link LootTrackerOverlay}'s existing render/click/key
 * logic in "standalone" mode (centered on screen, no vanilla inventory required) instead of the
 * old behavior of toggling loot tracking on/off and painting over the player's inventory screen.
 */
public class LootTrackerScreen extends Screen {

    public LootTrackerScreen() {
        super(Component.literal("Loot Tracker"));
    }

    @Override
    protected void init() {
        LootTrackerOverlay.setStandalone(true);
    }

    @Override
    public void onClose() {
        LootTrackerOverlay.setStandalone(false);
        super.onClose();
    }

    @Override public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d) { }
    @Override public void extractTransparentBackground(GuiGraphicsExtractor ctx) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0xC0101014, 0xE0080809);
        LootTrackerOverlay.renderInScreen(ctx, mouseX, mouseY);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (LootTrackerOverlay.handleScreenClick(click.x(), click.y())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (LootTrackerOverlay.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        LootTrackerOverlay.charTyped(input);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

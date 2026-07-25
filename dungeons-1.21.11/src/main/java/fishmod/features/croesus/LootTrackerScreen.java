package fishmod.features.croesus;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Standalone /fmloot menu. Just hosts {@link LootTrackerOverlay}'s existing render/click/key
 * logic in "standalone" mode (centered on screen, no vanilla inventory required) instead of the
 * old behavior of toggling loot tracking on/off and painting over the player's inventory screen.
 */
public class LootTrackerScreen extends Screen {

    public LootTrackerScreen() {
        super(Text.literal("Loot Tracker"));
    }

    @Override
    protected void init() {
        LootTrackerOverlay.setStandalone(true);
    }

    @Override
    public void removed() {
        LootTrackerOverlay.setStandalone(false);
        super.removed();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void renderInGameBackground(DrawContext ctx) { }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0xC0101014, 0xE0080809);
        LootTrackerOverlay.renderInScreen(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (LootTrackerOverlay.handleScreenClick(click.x(), click.y())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (LootTrackerOverlay.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        LootTrackerOverlay.charTyped(input);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

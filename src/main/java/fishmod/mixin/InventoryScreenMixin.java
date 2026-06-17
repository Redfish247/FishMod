package fishmod.mixin;

import fishmod.features.other.InventoryButton;
import fishmod.utils.config.values.Buttons;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends RecipeBookScreen<PlayerScreenHandler> {

    public InventoryScreenMixin(PlayerScreenHandler handler, RecipeBookWidget<?> recipeBook, PlayerInventory inventory, Text title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
    protected void drawForeground(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }

    // Inventory command buttons (1:1 with blade-addons): render at the inventory's top-left origin.
    @Inject(method = "render", at = @At("TAIL"))
    private void fishmod$renderInventoryButtons(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!Buttons.enableInventoryButtons) return;
        Matrix3x2fStack stack = context.getMatrices();
        stack.pushMatrix();
        stack.translate(this.x, this.y);
        InventoryButton.renderAll(context, mouseX, mouseY, deltaTicks);
        stack.popMatrix();
    }

    // Override (like blade-addons) rather than @Inject — InventoryScreen doesn't declare mouseClicked,
    // so an inject can't remap. Buttons sit over empty GUI space, so we still defer to super for slots.
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (Buttons.enableInventoryButtons) {
            InventoryButton.parseClicks(click.x() - this.x, click.y() - this.y);
        }
        return super.mouseClicked(click, doubled);
    }
}

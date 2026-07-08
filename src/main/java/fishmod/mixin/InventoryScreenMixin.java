package fishmod.mixin;

import fishmod.features.other.InventoryButton;
import fishmod.utils.config.values.Buttons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu handler, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    protected void drawForeground(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }

    // Inventory command buttons (1:1 with blade-addons): render at the inventory's top-left origin.
    @Inject(method = "render", at = @At("TAIL"))
    private void fishmod$renderInventoryButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!Buttons.enableInventoryButtons) return;
        Matrix3x2fStack stack = context.pose();
        stack.pushMatrix();
        stack.translate(this.leftPos, this.topPos);
        InventoryButton.renderAll(context, mouseX, mouseY, deltaTicks);
        stack.popMatrix();
    }

    // Override (like blade-addons) rather than @Inject — InventoryScreen doesn't declare mouseClicked,
    // so an inject can't remap. Buttons sit over empty GUI space, so we still defer to super for slots.
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (Buttons.enableInventoryButtons) {
            InventoryButton.parseClicks(click.x() - this.leftPos, click.y() - this.topPos);
        }
        return super.mouseClicked(click, doubled);
    }
}

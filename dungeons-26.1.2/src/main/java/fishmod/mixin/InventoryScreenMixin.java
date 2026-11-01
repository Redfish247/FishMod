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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu handler, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    protected void fishmod$hideLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.utils.config.values.Visual.renderOptimizer && fishmod.utils.config.values.Visual.roHideInventoryLabels) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$renderInventoryButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!Buttons.enableInventoryButtons && !Buttons.showEquipment) return;
        Matrix3x2fStack stack = context.pose();
        stack.pushMatrix();
        stack.translate(this.leftPos, this.topPos);
        try {
            if (Buttons.showEquipment) fishmod.features.other.EquipmentDisplay.render(context, mouseX - this.leftPos, mouseY - this.topPos, this.leftPos, this.topPos);
            if (Buttons.enableInventoryButtons) InventoryButton.renderAll(context, mouseX - this.leftPos, mouseY - this.topPos, deltaTicks);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("InventoryScreenMixin.1", "inventory buttons render failed", t);
        }
        stack.popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (Buttons.showEquipment && click.button() == 0 && fishmod.features.other.EquipmentDisplay.click(click.x() - this.leftPos, click.y() - this.topPos)) return true;
        if (Buttons.enableInventoryButtons) {
            try {
                if (InventoryButton.parseClicks(click.x() - this.leftPos, click.y() - this.topPos, click.button())) return true;
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("InventoryScreenMixin.2", "inventory button click failed", t);
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (Buttons.enableInventoryButtons && InventoryButton.drag(click.x() - this.leftPos, click.y() - this.topPos)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    // InventoryScreen defines mouseReleased itself, so inject instead of overriding
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void fishmod$releaseButtons(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryButton.release()) cir.setReturnValue(true);
    }

    @Override
    public void removed() {
        InventoryButton.onClose();
        super.removed();
    }
}

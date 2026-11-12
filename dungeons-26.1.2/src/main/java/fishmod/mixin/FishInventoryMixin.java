package fishmod.mixin;

import fishmod.features.other.InvShortcutButtons;
import fishmod.utils.config.values.InvButtonSettings;
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
public abstract class FishInventoryMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    public FishInventoryMixin(InventoryMenu handler, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    protected void fishmod$hideLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.utils.config.values.VisualSettings.renderOptimizer && fishmod.utils.config.values.VisualSettings.roHideInventoryLabels) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$renderInventoryButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!InvButtonSettings.enableInventoryButtons && !InvButtonSettings.showEquipment) return;
        Matrix3x2fStack stack = context.pose();
        stack.pushMatrix();
        stack.translate(this.leftPos, this.topPos);
        try {
            if (InvButtonSettings.showEquipment) fishmod.features.other.EquipmentDisplay.render(context, mouseX - this.leftPos, mouseY - this.topPos, this.leftPos, this.topPos);
            if (InvShortcutButtons.on()) InvShortcutButtons.renderAll(context, mouseX - this.leftPos, mouseY - this.topPos, deltaTicks);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishInventoryMixin.1", "inventory buttons render failed", t);
        }
        stack.popMatrix();
        if (InvButtonSettings.showEquipment) fishmod.features.other.EquipmentDisplay.flushTooltip(context);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (InvButtonSettings.showEquipment && fishmod.compat.GLFW.btn(click) == 0 && fishmod.features.other.EquipmentDisplay.click(click.x() - this.leftPos, click.y() - this.topPos)) return true;
        if (InvShortcutButtons.on()) {
            try {
                if (InvShortcutButtons.parseClicks(click.x() - this.leftPos, click.y() - this.topPos, fishmod.compat.GLFW.btn(click))) return true;
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("FishInventoryMixin.2", "inventory button click failed", t);
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (InvShortcutButtons.on() && InvShortcutButtons.drag(click.x() - this.leftPos, click.y() - this.topPos)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    // InventoryScreen defines mouseReleased itself, so inject instead of overriding
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void fishmod$releaseButtons(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (InvShortcutButtons.release()) cir.setReturnValue(true);
    }

    @Override
    public void removed() {
        InvShortcutButtons.onClose();
        super.removed();
    }
}

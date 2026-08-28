package fishmod.mixin;

import fishmod.features.dungeon.SessionStats;
import fishmod.features.other.SearchBar;
import fishmod.features.other.WardrobeHotkeys;
import fishmod.utils.rendering.DrawEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen {

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        SearchBar.render(context, mouseX, mouseY, deltaTicks);
        fishmod.features.dungeon.LeapMenu.render(context, mouseX, mouseY, (AbstractContainerScreen<?>) (Object) this);
        fishmod.features.storage.StorageOverlay.render(context, mouseX, mouseY, (AbstractContainerScreen<?>) (Object) this);
        if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) {
            fishmod.features.dungeon.f7.terminal.TermCustomGui.render(context, this.width, this.height);
        }
        if ((Object) this instanceof fishmod.features.dungeon.f7.terminal.TermSimScreen ts) ts.overlay(context);
    }

    // Custom GUI mode: hide every vanilla slot (items + hover highlight + the solver's own overlay);
    // TermCustomGui draws its board on top in extractRenderState instead.
    @Inject(method = "extractSlots", at = @At("HEAD"), cancellable = true)
    private void fishmod$customTermHideSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    public void drawBackground(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        DrawEvents.INVENTORY_SLOT_BEFORE.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "extractSlot", at = @At(value = "TAIL"))
    public void drawAfter(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        DrawEvents.INVENTORY_SLOT_AFTER.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (SearchBar.keyPressed(input)) { cir.setReturnValue(false); return; }
        if (fishmod.features.storage.StorageOverlay.keyPressed(input.key(), (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
        if (fishmod.features.dungeon.LeapMenu.keyPressed(input.key(), (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
        if (WardrobeHotkeys.keyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double cx = click.x(), cy = click.y();

        if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) {
            int idx = fishmod.features.dungeon.f7.terminal.TermCustomGui.slotAt((int) cx, (int) cy);
            fishmod.features.dungeon.f7.terminal.TermCustomGui.handleClick(
                    (AbstractContainerScreen<?>) (Object) this, idx, click.button());
            cir.setReturnValue(true);
            return;
        }

        if (fishmod.features.dungeon.f7.terminal.TerminalSolver.onMouseClick(
                click.button(), (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }

        if (fishmod.features.storage.StorageOverlay.mouseClicked(click.button(), cx, cy, (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }

        if (fishmod.features.SlotBinds.onMouseClick(click, (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }

        if (fishmod.features.dungeon.LeapMenu.mouseClicked(click.button(), cx, cy, (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }

        if (SessionStats.handleScreenClick(cx, cy)) {
            cir.setReturnValue(true);
            return;
        }

        if (WardrobeHotkeys.mouseClicked(click, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }

        SearchBar.onMouseClick(click);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void fishmod$storageScroll(double mx, double my, double hz, double vt, CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.features.storage.StorageOverlay.mouseScrolled(vt, (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void fishmod$storageClosed(CallbackInfo ci) {
        fishmod.features.storage.StorageOverlay.onClosed();
    }
}

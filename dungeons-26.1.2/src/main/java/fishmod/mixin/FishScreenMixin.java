package fishmod.mixin;

import fishmod.features.dungeon.SessionStats;
import fishmod.features.other.InvSearchBar;
import fishmod.features.other.WardrobeHotkeys;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.debug.MixinGuard;
import fishmod.utils.rendering.DrawHooks;
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
public abstract class FishScreenMixin<T extends AbstractContainerMenu> extends Screen {

    protected FishScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$drawOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        MixinGuard.run("FishScreenMixin.9", "hovered slot tracking failed", () -> {
            Slot hs = ((fishmod.mixin.accessors.HandledScreenAccessor) (Object) this).fishmod$getHoveredSlot();
            fishmod.features.ScrollableTooltip.trackHoveredSlot(hs != null && !hs.getItem().isEmpty() ? hs.index : -1);
        });
        // Setting checks up front skip the per-frame lambda when a feature is off
        if (FishSettings.inventorySearchEnabled) MixinGuard.run("FishScreenMixin.1", "InvSearchBar.render failed", () -> InvSearchBar.render(context, mouseX, mouseY, deltaTicks));
        MixinGuard.run("FishScreenMixin.2", "LeapMenu.render failed", () -> fishmod.features.dungeon.LeapMenu.render(context, mouseX, mouseY, self));
        MixinGuard.run("FishScreenMixin.3", "PartyFinderPanel.render failed", () -> fishmod.features.dungeon.PartyFinderPanel.render(context, mouseX, mouseY, self));
        MixinGuard.run("FishScreenMixin.4", "StorageOverlay.render failed", () -> fishmod.features.storage.StorageOverlay.render(context, mouseX, mouseY, self));
        MixinGuard.run("FishScreenMixin.5", "CroesusProfit.render failed", () -> fishmod.features.croesus.CroesusProfit.render(context, self));
        if (FishSettings.anitaProfitEnabled) MixinGuard.run("FishScreenMixin.20", "AnitaShop.render failed", () -> fishmod.features.item.AnitaShop.render(context, self));
        if (FishSettings.containerValueEnabled) MixinGuard.run("FishScreenMixin.6", "ContainerValue.render failed", () -> fishmod.features.item.ContainerValue.render(context, self));
        MixinGuard.run("FishScreenMixin.16", "Visitors.renderShopping failed", () -> fishmod.features.garden.Visitors.renderShopping(context, self));
        if (FishSettings.auctionPriceAutofillEnabled) MixinGuard.run("FishScreenMixin.7", "AuctionPriceAutofill.trackScreen failed", () -> fishmod.features.item.AuctionPriceAutofill.trackScreen(self));
        MixinGuard.run("FishScreenMixin.8", "TermCustomGui.render failed", () -> {
            if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(self)) {
                fishmod.features.dungeon.f7.terminal.TermCustomGui.render(context, this.width, this.height);
            }
        });
        if (self instanceof fishmod.features.dungeon.f7.terminal.TermSimScreen ts) {
            MixinGuard.run("FishScreenMixin.10", "term sim overlay failed", () -> ts.overlay(context));
        }
    }

    @Inject(method = "extractSlots", at = @At("HEAD"), cancellable = true)
    private void fishmod$customTermHideSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) { ci.cancel(); return; }
        if (fishmod.features.storage.StorageOverlay.isActive((AbstractContainerScreen<?>) (Object) this)) { ci.cancel(); return; }
        if (fishmod.features.dungeon.LeapMenu.isActive((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void fishmod$hideLeapMenuLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.features.dungeon.LeapMenu.isActive((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void fishmod$hideTooltipInCustomTermGui(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) { ci.cancel(); return; }
        // Hidden vanilla slots still resolve hoveredSlot; the overlay paints over that tooltip and draws its own on top.
        if (fishmod.features.storage.StorageOverlay.isActive((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    public void fishmod$slotBackground(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (DrawHooks.INVENTORY_SLOT_BEFORE.isEmpty()) return;
        ItemStack stack = slot.getItem();
        DrawHooks.currentSlot = slot;
        try {
            DrawHooks.INVENTORY_SLOT_BEFORE.invoke(event -> event.draw(context, stack, slot.x, slot.y));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.11", "slot before-draw event failed slot=" + slot.index, t);
        }
        DrawHooks.currentSlot = null;
    }

    @com.llamalad7.mixinextras.injector.WrapWithCondition(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private boolean fishmod$hideClickedNumbers(GuiGraphicsExtractor context, net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text, GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY) {
        try {
            return !fishmod.features.dungeon.f7.terminal.TerminalSolver.hideSlotCount(slot);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.18", "terminal slot count hide check failed", t);
            return true;
        }
    }

    @Inject(method = "extractSlot", at = @At(value = "TAIL"))
    public void drawAfter(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (DrawHooks.INVENTORY_SLOT_AFTER.isEmpty()) return;
        ItemStack stack = slot.getItem();
        DrawHooks.currentSlot = slot;
        try {
            DrawHooks.INVENTORY_SLOT_AFTER.invoke(event -> event.draw(context, stack, slot.x, slot.y));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.12", "slot after-draw event failed slot=" + slot.index, t);
        }
        DrawHooks.currentSlot = null;
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void fishmod$slotLock(Slot slot, int slotId, int button, net.minecraft.world.inventory.ContainerInput input, CallbackInfo ci) {
        try {
            fishmod.features.ExperimentSolver.onSlotClicked(slotId);
            if (fishmod.features.SlotLocking.onSlotClicked(slot, slotId, input)) ci.cancel();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.13", "slot lock click check failed slotId=" + slotId, t);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fishmod$screenKey(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (fishmod.features.storage.StorageOverlay.searchKeyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (InvSearchBar.keyPressed(input)) { cir.setReturnValue(true); return; }
            if (fishmod.features.SlotLocking.keyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (fishmod.features.storage.StorageOverlay.keyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (fishmod.features.dungeon.LeapMenu.keyPressed(input.key(), (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (fishmod.features.other.PetKeybinds.keyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (WardrobeHotkeys.keyPressed(input, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.14", "container keyPressed hooks failed key=" + input.key(), t);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fishmod$screenClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            double cx = click.x(), cy = click.y();

            if (fishmod.features.dungeon.f7.terminal.TermCustomGui.suppressVanilla(this)) {
                int idx = fishmod.features.dungeon.f7.terminal.TermCustomGui.slotAt((int) cx, (int) cy);
                fishmod.features.dungeon.f7.terminal.TermCustomGui.handleClick(
                        (AbstractContainerScreen<?>) (Object) this, idx, fishmod.compat.GLFW.btn(click));
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.dungeon.f7.terminal.TerminalSolver.onMouseClick(
                    fishmod.compat.GLFW.btn(click), (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.storage.StorageOverlay.onOverlayClick(click, doubled, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.SlotBinds.onMouseClick(click, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.dungeon.LeapMenu.mouseClicked(fishmod.compat.GLFW.btn(click), cx, cy, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.dungeon.PartyFinderPanel.mouseClicked(fishmod.compat.GLFW.btn(click), cx, cy, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.garden.Visitors.mouseClicked(click, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }

            if (SessionStats.handleScreenClick(cx, cy)) {
                cir.setReturnValue(true);
                return;
            }

            if (fishmod.features.other.PetKeybinds.mouseClicked(click, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }
            if (WardrobeHotkeys.mouseClicked(click, (AbstractContainerScreen<?>) (Object) this)) { cir.setReturnValue(true); return; }

            InvSearchBar.onMouseClick(click);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.15", "container mouseClicked hooks failed button=" + fishmod.compat.GLFW.btn(click), t);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void fishmod$storageScroll(double mx, double my, double hz, double vt, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (fishmod.features.storage.StorageOverlay.mouseScrolled(vt, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }
            if (fishmod.features.dungeon.PartyFinderPanel.mouseScrolled(mx, my, vt, (AbstractContainerScreen<?>) (Object) this)) {
                cir.setReturnValue(true);
                return;
            }
            if (fishmod.features.ScrollableTooltip.isEnabled()) {
                Slot hs = ((fishmod.mixin.accessors.HandledScreenAccessor) (Object) this).fishmod$getHoveredSlot();
                if (hs != null && !hs.getItem().isEmpty()) {
                    long win = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
                    boolean shift = fishmod.compat.GLFW.glfwGetKey(win, fishmod.compat.GLFW.GLFW_KEY_LEFT_SHIFT) == fishmod.compat.GLFW.GLFW_PRESS
                            || fishmod.compat.GLFW.glfwGetKey(win, fishmod.compat.GLFW.GLFW_KEY_RIGHT_SHIFT) == fishmod.compat.GLFW.GLFW_PRESS;
                    boolean ctrl = fishmod.compat.GLFW.glfwGetKey(win, fishmod.compat.GLFW.GLFW_KEY_LEFT_CONTROL) == fishmod.compat.GLFW.GLFW_PRESS
                            || fishmod.compat.GLFW.glfwGetKey(win, fishmod.compat.GLFW.GLFW_KEY_RIGHT_CONTROL) == fishmod.compat.GLFW.GLFW_PRESS;
                    if (fishmod.features.ScrollableTooltip.onScroll(vt, hs.index, shift, ctrl)) {
                        cir.setReturnValue(true);
                    }
                }
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.16", "container mouseScrolled hooks failed", t);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void fishmod$storageRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.features.storage.StorageOverlay.mouseReleased((AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void fishmod$storageDrag(MouseButtonEvent click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.features.storage.StorageOverlay.mouseDragged(click.x(), click.y(), (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void fishmod$storageClosed(CallbackInfo ci) {
        try {
            fishmod.features.storage.StorageOverlay.onClosed();
            fishmod.features.ScrollableTooltip.resetScroll();
            fishmod.features.dungeon.f7.terminal.TerminalSolver.onScreenClosed();
            fishmod.features.SlotBinds.onClose();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishScreenMixin.17", "container close hooks failed", t);
        }
    }
}

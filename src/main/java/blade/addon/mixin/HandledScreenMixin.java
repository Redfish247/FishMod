package blade.addon.mixin;

import blade.addon.features.other.SearchBar;
import blade.addon.utils.rendering.DrawEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        SearchBar.render(context, mouseX, mouseY, deltaTicks);
    }

    @Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
    public void drawBackground(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        DrawEvents.INVENTORY_SLOT_BEFORE.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "drawSlot", at = @At(value = "TAIL"))
    public void drawAfter(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        DrawEvents.INVENTORY_SLOT_AFTER.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (SearchBar.keyPressed(input)) cir.setReturnValue(false);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void onMouseClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        SearchBar.onMouseClick(click);
    }
}

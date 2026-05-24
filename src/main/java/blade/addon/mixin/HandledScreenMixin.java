package blade.addon.mixin;

import blade.addon.features.PowderTracker;
import blade.addon.features.SlayerXpTracker;
import blade.addon.features.dungeon.SessionStats;
import blade.addon.features.other.SearchBar;
import blade.addon.features.wiki.WikiContextMenu;
import blade.addon.mixin.accessors.KeyBindingAccessor;
import blade.addon.utils.Keybinds;
import blade.addon.utils.data.ItemUtil;
import blade.addon.utils.rendering.DrawEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {

    @Shadow @Nullable protected Slot focusedSlot;

    @Unique private int lastMx = 0;
    @Unique private int lastMy = 0;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        lastMx = mouseX;
        lastMy = mouseY;
        SearchBar.render(context, mouseX, mouseY, deltaTicks);
        WikiContextMenu.render(context, MinecraftClient.getInstance());
    }

    @Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
    public void drawBackground(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        DrawEvents.INVENTORY_SLOT_BEFORE.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "drawSlot", at = @At(value = "TAIL"))
    public void drawAfter(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        DrawEvents.INVENTORY_SLOT_AFTER.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (SearchBar.keyPressed(input)) { cir.setReturnValue(false); return; }

        // Wiki slot keybind — configurable, default unbound
        KeyBinding wikiKey = Keybinds.openItemWiki;
        if (wikiKey != null && focusedSlot != null && !focusedSlot.getStack().isEmpty()) {
            InputUtil.Key bound = ((KeyBindingAccessor) (Object) wikiKey).getBoundKey();
            if (bound.getCode() != InputUtil.UNKNOWN_KEY.getCode()
                    && bound.getCode() == input.key()
                    && ItemUtil.getId(focusedSlot.getStack()) != null) {
                WikiContextMenu.show(lastMx, lastMy, focusedSlot.getStack());
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double cx = click.x(), cy = click.y();

        if (WikiContextMenu.isActive()) {
            WikiContextMenu.handleClick(cx, cy, MinecraftClient.getInstance());
            cir.setReturnValue(true);
            return;
        }

        if (SessionStats.handleScreenClick(cx, cy)
                || PowderTracker.handleScreenClick(cx, cy)
                || SlayerXpTracker.handleScreenClick(cx, cy)) {
            cir.setReturnValue(true);
            return;
        }

        SearchBar.onMouseClick(click);
    }
}

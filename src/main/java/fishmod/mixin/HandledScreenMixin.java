package fishmod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.features.PowderTracker;
import fishmod.features.SlayerXpTracker;
import fishmod.features.croesus.LootTrackerOverlay;
import fishmod.features.dungeon.SessionStats;
import fishmod.features.other.SearchBar;
import fishmod.features.wiki.WikiContextMenu;
import fishmod.mixin.accessors.KeyBindingAccessor;
import fishmod.utils.Keybinds;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.rendering.DrawEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen {

    @Shadow @Nullable protected Slot hoveredSlot;

    @Unique private int lastMx = 0;
    @Unique private int lastMy = 0;

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        lastMx = mouseX;
        lastMy = mouseY;
        SearchBar.render(context, mouseX, mouseY, deltaTicks);
        WikiContextMenu.render(context, Minecraft.getInstance());
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    public void drawBackground(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        DrawEvents.INVENTORY_SLOT_BEFORE.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "renderSlot", at = @At(value = "TAIL"))
    public void drawAfter(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        DrawEvents.INVENTORY_SLOT_AFTER.invoke(event -> event.draw(context, stack, slot.x, slot.y));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (SearchBar.keyPressed(input)) { cir.setReturnValue(false); return; }
        if (LootTrackerOverlay.keyPressed(input)) { cir.setReturnValue(false); return; }

        // Wiki slot keybind — configurable, default unbound
        KeyMapping wikiKey = Keybinds.openItemWiki;
        if (wikiKey != null && hoveredSlot != null && !hoveredSlot.getItem().isEmpty()) {
            InputConstants.Key bound = ((KeyBindingAccessor) (Object) wikiKey).getBoundKey();
            if (bound.getValue() != InputConstants.UNKNOWN.getValue()
                    && bound.getValue() == input.key()
                    && ItemUtil.getId(hoveredSlot.getItem()) != null) {
                WikiContextMenu.show(lastMx, lastMy, hoveredSlot.getItem());
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double cx = click.x(), cy = click.y();

        if (WikiContextMenu.isActive()) {
            WikiContextMenu.handleClick(cx, cy, Minecraft.getInstance());
            cir.setReturnValue(true);
            return;
        }

        if (SessionStats.handleScreenClick(cx, cy)
                || PowderTracker.handleScreenClick(cx, cy)
                || SlayerXpTracker.handleScreenClick(cx, cy)
                || LootTrackerOverlay.handleScreenClick(cx, cy)) {
            cir.setReturnValue(true);
            return;
        }

        SearchBar.onMouseClick(click);
    }
}

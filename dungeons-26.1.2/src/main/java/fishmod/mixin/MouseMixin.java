package fishmod.mixin;

import fishmod.features.NoCursorReset;
import fishmod.utils.config.values.ExtraOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;

    @Unique private double fishmod$beforeX;
    @Unique private double fishmod$beforeY;

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"), cancellable = true)
    private void stopScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ExtraOptions.disableScrollHotbar) {
            ci.cancel();
        }
    }

    // grabMouse() recentres xpos/ypos when a GUI hands control back; capture the real position first
    @Inject(method = "grabMouse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void fishmod$captureCursor(CallbackInfo ci) {
        this.fishmod$beforeX = this.xpos;
        this.fishmod$beforeY = this.ypos;
    }

    // @Inject sits right before vanilla warps the OS cursor to centre; swap in the saved position
    @Inject(method = "releaseMouse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void fishmod$restoreCursor(CallbackInfo ci) {
        if (NoCursorReset.shouldHook() && Minecraft.getInstance().screen instanceof AbstractContainerScreen) {
            this.xpos = this.fishmod$beforeX;
            this.ypos = this.fishmod$beforeY;
        }
    }
}

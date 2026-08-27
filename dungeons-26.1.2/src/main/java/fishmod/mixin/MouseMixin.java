package fishmod.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import fishmod.features.NoCursorReset;
import fishmod.utils.config.values.ExtraOptions;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"), cancellable = true)
    private void stopScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ExtraOptions.disableScrollHotbar) {
            ci.cancel();
        }
    }

    // No Cursor Reset — skip the grabMouse() xpos/ypos recentre, but only within a short window
    // around a container close (Odin's model), so normal gameplay still recentres.
    @WrapWithCondition(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = Opcodes.PUTFIELD))
    private boolean fishmod$keepXpos(MouseHandler instance, double value) {
        return !NoCursorReset.shouldHook();
    }

    @WrapWithCondition(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;ypos:D", opcode = Opcodes.PUTFIELD))
    private boolean fishmod$keepYpos(MouseHandler instance, double value) {
        return !NoCursorReset.shouldHook();
    }
}

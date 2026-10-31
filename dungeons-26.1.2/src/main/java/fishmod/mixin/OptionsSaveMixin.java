package fishmod.mixin;

import fishmod.utils.Keybinds;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsSaveMixin {

    // Keep keybinds.txt in step with options.txt so a crash/kill can't revert a rebind.
    @Inject(method = "save", at = @At("TAIL"))
    private void fishmod$syncKeybindBackup(CallbackInfo ci) {
        try {
            Keybinds.syncKeybindBackup();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("OptionsSaveMixin.1", "keybind backup sync on options save failed", t);
        }
    }
}

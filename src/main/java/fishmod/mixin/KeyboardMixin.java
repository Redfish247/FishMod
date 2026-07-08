package fishmod.mixin;

import fishmod.features.croesus.LootTrackerOverlay;
import fishmod.features.other.SearchBar;
import fishmod.utils.config.values.ExtraOptions;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "charTyped", at= @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
    private void onChar(long window, CharacterEvent input, CallbackInfo ci, @Local Screen screen) {
        if (screen instanceof AbstractContainerScreen<?>) {
            if (ExtraOptions.toggleableSearchBar) SearchBar.CharTyped(input);
            LootTrackerOverlay.charTyped(input);
        }
    }


}

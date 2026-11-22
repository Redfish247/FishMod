package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonItemsMixin {

    @Shadow private float oMainHandHeight;
    @Shadow private float mainHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private float offHandHeight;

    @Inject(method = "shouldInstantlyReplaceVisibleItem", require = 0, at = @At("HEAD"), cancellable = true)
    private void fishmod$noEquip(ItemStack current, ItemStack expected, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (FishSettings.animEnabled && FishSettings.animNoEquip) cir.setReturnValue(true);
    }

    @Inject(method = "tick", require = 0, at = @At("TAIL"))
    private void fishmod$noEquipHeights(LocalPlayer player, CallbackInfo ci) {
        if (FishSettings.animEnabled && FishSettings.animNoEquip) {
            oMainHandHeight = 1f;
            mainHandHeight = 1f;
            oOffHandHeight = 1f;
            offHandHeight = 1f;
        }
    }
}

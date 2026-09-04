package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.ArrowFix;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrow Fix (shortbow pull-back) + Animations swing-speed, both on the local player only. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArrowFixMixin {

    @Shadow protected ItemStack useItem;
    @Shadow protected int useItemRemaining;

    @Inject(method = "tick", at = @At("HEAD"))
    private void fishmod$arrowFix(CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;
        if (ArrowFix.isShortbow(useItem)) {
            useItem = ItemStack.EMPTY;
            useItemRemaining = 0;
        }
    }

    // animIgnoreHaste pins the base swing duration to 6 ticks before the multiplier
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int fishmod$swingSpeed(int original) {
        if (!FishSettings.animEnabled) return original;
        if ((Object) this != Minecraft.getInstance().player) return original;
        int base = FishSettings.animIgnoreHaste ? 6 : original;
        double s = FishSettings.animSwingSpeed;
        if (s == 0.0 && !FishSettings.animIgnoreHaste) return original;
        double mult = s >= 0 ? 1.0 / (1.0 + s) : 1.0 + (-s);
        return Math.max(1, (int) Math.round(base * mult));
    }
}

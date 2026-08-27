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

    // Animations "Swing Speed" (-2..1): 0 keeps vanilla, 1 -> instant, negative -> slower.
    // "Ignore Haste" pins the base duration to 6 before the multiplier.
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int fishmod$swingSpeed(int original) {
        if (!FishSettings.animEnabled) return original;
        if ((Object) this != Minecraft.getInstance().player) return original;
        int base = FishSettings.animIgnoreHaste ? 6 : original;
        if (FishSettings.animSwingSpeed == 0.0 && !FishSettings.animIgnoreHaste) return original;
        int scaled = (int) Math.round(base * (1.0 - FishSettings.animSwingSpeed));
        return Math.max(1, scaled);
    }
}

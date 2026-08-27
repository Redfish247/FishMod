package fishmod.mixin;

import fishmod.features.ArrowFix;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrow Fix: clear the active use-item each tick while the local player draws a shortbow. */
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
}

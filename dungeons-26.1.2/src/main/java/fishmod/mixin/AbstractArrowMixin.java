package fishmod.mixin;

import fishmod.features.ArrowHitSound;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires the ArrowHitSound cue only when the local player's arrow hits a living entity. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void fishmod$arrowHitMob(EntityHitResult result, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (self.getOwner() != Minecraft.getInstance().player) return;
        Entity hit = result.getEntity();
        if (!(hit instanceof LivingEntity) || hit == Minecraft.getInstance().player) return;
        ArrowHitSound.onArrowHitMob();
    }
}

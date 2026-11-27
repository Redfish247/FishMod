package fishmod.mixin;

import fishmod.cosmetic.PlayerModel;
import fishmod.cosmetic.ScaleHolder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class PlayerModelRenderMixin {

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void fishmod$mobModel(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        if (!(entity instanceof AbstractClientPlayer p)) return;
        try {
            String key = PlayerModel.keyFor(p);
            if (key.equals("PLAYER")) return;
            LivingEntity fake = PlayerModel.fakeFor(p, key);
            if (fake == null) return;
            PlayerModel.syncFake(fake, p);
            EntityRenderState st = ((EntityRenderDispatcher) (Object) this).extractEntity(fake, partialTick);
            if (st instanceof LivingEntityRenderState ls) {
                ls.walkAnimationPos = p.walkAnimation.position(partialTick);
                ls.walkAnimationSpeed = p.walkAnimation.speed(partialTick);
            }
            float[] sc = fishmod.cosmetic.PlayerSize.scaleFor(p);
            ((ScaleHolder) st).fishmod$setScale(sc[0], sc[1], sc[2]);
            cir.setReturnValue(st);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("PlayerModelRenderMixin.1", "mob model extract failed", t);
        }
    }
}

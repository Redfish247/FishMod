package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.BossBarFeature;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Appends the boss health number to the boss-bar name by wrapping the getName() call inside
 * extractRenderState, so vanilla positions/draws it for us.
 */
@Mixin(BossHealthOverlay.class)
public class FishBossBarHudMixin {

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;")
    )
    private Component fishmod$bossHealth(LerpingBossEvent instance, Operation<Component> original) {
        return BossBarFeature.appendHealth(instance, original.call(instance));
    }
}

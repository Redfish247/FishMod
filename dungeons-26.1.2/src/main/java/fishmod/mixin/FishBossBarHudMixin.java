package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.BossBarFeature;
import fishmod.features.RenderOptimizer;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.Map;

@Mixin(BossHealthOverlay.class)
public class FishBossBarHudMixin {

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;")
    )
    private Component fishmod$bossHealth(LerpingBossEvent instance, Operation<Component> original) {
        Component name = original.call(instance);
        try {
            return BossBarFeature.appendHealth(instance, name);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishBossBarHudMixin.1", "boss bar health append failed", t);
            return name;
        }
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;")
    )
    private Collection<LerpingBossEvent> fishmod$hideObjective(Map<?, LerpingBossEvent> map, Operation<Collection<LerpingBossEvent>> original) {
        Collection<LerpingBossEvent> bars = original.call(map);
        try {
            return RenderOptimizer.filterBossBars(bars);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishBossBarHudMixin.2", "boss bar filter failed", t);
            return bars;
        }
    }
}

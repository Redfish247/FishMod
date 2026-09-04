package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.sound.FishLoudSound;
import fishmod.utils.sound.IMonoAudioChannel;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Shadow @Final private ChannelAccess channelAccess;

    // play(SoundInstance) is the single chokepoint every sound funnels through
    @Inject(
        method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
        at = @At("HEAD"), cancellable = true)
    private void fishmod$arrowHitCue(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (fishmod.features.ArrowHitSound.onLocalSound(instance)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    // MC clamps volume to [0,1] before channel gain; ChannelMixin lifts AL_MAX_GAIN so the boost past 1.0 survives
    @WrapOperation(
        method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float fishmod$loudInitialVolume(SoundEngine self, float volume, SoundSource source, Operation<Float> original,
                                            @Local(argsOnly = true) SoundInstance instance) {
        float v = original.call(self, volume, source);
        return instance instanceof FishLoudSound loud ? v * loud.fishmod$boost() : v;
    }

    @ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private float fishmod$loudTickVolume(float original, SoundInstance instance) {
        return instance instanceof FishLoudSound loud ? original * loud.fishmod$boost() : original;
    }

    @Inject(method = "updateSource", at = @At("TAIL"))
    private void fishmod$refreshMonoChannels(Camera camera, CallbackInfo ci) {
        if (!FishSettings.monoAudioEnabled) return;
        channelAccess.executeOnChannels(stream ->
            stream.forEach(channel -> ((IMonoAudioChannel) channel).fishmod$refreshPosition())
        );
    }
}

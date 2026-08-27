package fishmod.mixin;

import fishmod.features.MonoAudio;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.sound.IMonoAudioChannel;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class ChannelMixin implements IMonoAudioChannel {

    @Shadow @Final private int source;

    @Unique private Vec3 fishmod$lastPos = Vec3.ZERO;
    @Unique private boolean fishmod$relative;

    @Inject(method = "setSelfPosition", at = @At("HEAD"), cancellable = true)
    private void fishmod$monoPosition(Vec3 pos, CallbackInfo ci) {
        if (!FishSettings.monoAudioEnabled) return;
        fishmod$lastPos = pos;
        fishmod$refreshPosition();
        ci.cancel();
    }

    @Inject(method = "setRelative", at = @At("HEAD"), cancellable = true)
    private void fishmod$monoRelative(boolean relative, CallbackInfo ci) {
        if (!FishSettings.monoAudioEnabled) return;
        fishmod$relative = relative;
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        fishmod$refreshPosition();
        ci.cancel();
    }

    @Override
    public void fishmod$refreshPosition() {
        if (!FishSettings.monoAudioEnabled) return;
        double d = fishmod$relative ? fishmod$lastPos.length() : MonoAudio.distanceToListener(fishmod$lastPos);
        MonoAudio.applyCenteredPosition(source, d);
    }
}

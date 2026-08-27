package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Shadow @Final private ChannelAccess channelAccess;

    @Inject(method = "updateSource", at = @At("TAIL"))
    private void fishmod$refreshMonoChannels(Camera camera, CallbackInfo ci) {
        if (!FishSettings.monoAudioEnabled) return;
        channelAccess.executeOnChannels(stream ->
            stream.forEach(channel -> ((IMonoAudioChannel) channel).fishmod$refreshPosition())
        );
    }
}

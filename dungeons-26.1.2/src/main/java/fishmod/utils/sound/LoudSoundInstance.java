package fishmod.utils.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A non-positional cue: relative, no attenuation, at the listener — i.e. "in your ear", no panning
 * or distance falloff. Its effective gain may also exceed 1.0 (see {@link FishLoudSound}): the
 * instance volume is capped at 1.0 and any excess is applied as a multiplier by the sound-engine
 * mixins, so the ">100%" cue sliders actually get louder.
 */
public class LoudSoundInstance extends SimpleSoundInstance implements FishLoudSound {

    private final float boost;

    /** @param volume 0..~8 as a linear factor (1.0 = 100%). */
    public LoudSoundInstance(SoundEvent event, float volume, float pitch) {
        super(event.location(), SoundSource.PLAYERS, Math.min(Math.max(volume, 0f), 1.0f), pitch,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        this.boost = Math.max(1.0f, Math.min(volume, 8.0f));
    }

    @Override
    public float fishmod$boost() {
        return boost;
    }
}

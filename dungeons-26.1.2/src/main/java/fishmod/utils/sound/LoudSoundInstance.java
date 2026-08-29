package fishmod.utils.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A non-positional cue (relative, no attenuation, at the listener) whose effective gain may go past
 * 1.0 — see {@link FishLoudSound}. The instance volume stays 1.0; the extra loudness is applied as a
 * multiplier by the sound-engine mixins so it stacks correctly with the player's category sliders.
 */
public class LoudSoundInstance extends SimpleSoundInstance implements FishLoudSound {

    private final float boost;

    public LoudSoundInstance(SoundEvent event, float pitch, float boost) {
        super(event.location(), SoundSource.PLAYERS, 1.0f, pitch, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        this.boost = Math.max(1.0f, Math.min(boost, 8.0f));
    }

    @Override
    public float fishmod$boost() {
        return boost;
    }
}

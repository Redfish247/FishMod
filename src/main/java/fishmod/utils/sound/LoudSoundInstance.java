package fishmod.utils.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class LoudSoundInstance extends SimpleSoundInstance implements FishLoudSound {

    private final float boost;

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

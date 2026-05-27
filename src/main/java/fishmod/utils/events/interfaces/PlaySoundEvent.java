package fishmod.utils.events.interfaces;

import net.minecraft.sound.SoundEvent;

public interface PlaySoundEvent {
    boolean onSound(SoundEvent soundEvent, float volume, float pitch);
}

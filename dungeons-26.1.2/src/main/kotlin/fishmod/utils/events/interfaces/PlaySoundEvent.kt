package fishmod.utils.events.interfaces

import net.minecraft.sounds.SoundEvent

fun interface PlaySoundEvent {
    fun onSound(soundEvent: SoundEvent, volume: Float, pitch: Float): Boolean
}

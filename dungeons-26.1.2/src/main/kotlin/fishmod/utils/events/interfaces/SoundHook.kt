package fishmod.utils.events.interfaces

import net.minecraft.sounds.SoundEvent

fun interface SoundHook {
    fun onSound(soundEvent: SoundEvent, volume: Float, pitch: Float): Boolean
}

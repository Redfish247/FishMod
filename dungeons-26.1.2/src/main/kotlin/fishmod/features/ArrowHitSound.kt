package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.sound.SoundManager
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvents

object ArrowHitSound {

    @JvmStatic
    fun init() {
    }

    @JvmStatic
    fun onLocalSound(instance: SoundInstance): Boolean {
        if (!FishSettings.arrowHitSoundEnabled) return false
        if (instance.identifier != SoundEvents.ARROW_HIT_PLAYER.location) return false

        SoundManager.play2D(
            SoundManager.preset(FishSettings.arrowHitSoundName),
            FishSettings.arrowHitSoundVolume.coerceIn(0, 500) / 100f,
            FishSettings.arrowHitSoundPitch.toFloat().coerceIn(0f, 2f),
            "arrowHit", 40,
        )
        return FishSettings.arrowHitSoundSuppress
    }
}

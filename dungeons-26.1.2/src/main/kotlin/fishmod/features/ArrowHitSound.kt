package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.sound.SoundManager
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvents

/** Hooks `SoundEngine.play(SoundInstance)` via [fishmod.mixin.SoundEngineMixin] since the arrow-hit sound never arrives as a networked packet. */
object ArrowHitSound {

    @JvmStatic
    fun init() {
        // Nothing to register — driven entirely by SoundEngineMixin -> onLocalSound().
    }

    /**
     * Called from [fishmod.mixin.SoundEngineMixin] for every sound the client is about to play.
     * @return true to swallow the vanilla `arrow.hit_player` tick (the "Suppress" setting).
     */
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

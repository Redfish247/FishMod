package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.sounds.SoundEvents

/**
 * Plays a configurable cue when one of your arrows lands a hit on a player/mob (ported from
 * NoammAddons' ArrowHitSound — the `entity.arrow.hit_player` sound). Optionally suppresses the
 * vanilla hit tick.
 */
object ArrowHitSound {

    @JvmStatic
    fun init() {
        Events.ON_SOUND.register { event, _, _ ->
            if (!FishSettings.arrowHitSoundEnabled) return@register false
            if (event !== SoundEvents.ARROW_HIT_PLAYER) return@register false
            SoundManager.play(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 1.4f, "arrowHit", 40)
            FishSettings.arrowHitSoundSuppress
        }
    }
}

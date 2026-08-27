package fishmod.utils.sound

import config.practical.data.SoundData
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/**
 * Single client-sound entry point. Every feature cue should route through here so it shares one
 * master toggle + volume and one per-key debounce, instead of each feature re-implementing
 * `player.playSound` plus its own ad-hoc spam guard.
 *
 * [play] returns whether the sound was actually emitted (false = muted or debounced), so callers
 * that also flash a title/particle can mirror the debounce for free.
 */
object SoundManager {

    private val enabled: Boolean get() = FishSettings.soundMasterEnabled
    private val masterVol: Float get() = FishSettings.soundMasterVolume.coerceIn(0, 100) / 100f

    private val lastPlayed = HashMap<String, Long>()

    /**
     * @param key       debounce bucket; repeat plays of the same key inside [debounceMs] are dropped.
     * @param debounceMs 0 disables debounce (key ignored).
     */
    @JvmStatic
    @JvmOverloads
    fun play(
        sound: SoundEvent,
        volume: Float = 1f,
        pitch: Float = 1f,
        key: String? = null,
        debounceMs: Long = 0L,
    ): Boolean {
        if (!enabled) return false
        val v = volume * masterVol
        if (v <= 0f) return false
        if (key != null && debounceMs > 0L) {
            val now = System.currentTimeMillis()
            val prev = lastPlayed[key]
            if (prev != null && now - prev < debounceMs) return false
            lastPlayed[key] = now
        }
        Misc.sendSound(sound, v, pitch)
        return true
    }

    @JvmStatic
    @JvmOverloads
    fun play(data: SoundData, key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvent.createVariableRangeEvent(data.sound), data.volume, data.pitch, key, debounceMs)

    /** High-pitched confirmation blip. */
    @JvmStatic
    @JvmOverloads
    fun ping(key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f, key, debounceMs)

    /** Low attention tone. */
    @JvmStatic
    @JvmOverloads
    fun alert(key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f, key, debounceMs)

    /** Drop debounce history — call on world/dungeon change so a cue can fire again immediately. */
    @JvmStatic
    fun reset() = lastPlayed.clear()
}

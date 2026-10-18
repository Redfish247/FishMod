package fishmod.features.extras

import fishmod.utils.sound.SoundManager

internal object ExtrasSounds {
    // Volume above 100% replays the sound, same as the Diana dug sound
    fun play(name: String, volumePct: Int, key: String) {
        val snd = SoundManager.preset(name)
        var vol = volumePct.coerceIn(0, 500) / 100f
        if (!SoundManager.play(snd, minOf(vol, 1f), key = key, debounceMs = 300)) return
        vol -= 1f
        while (vol > 0.01f) { SoundManager.play(snd, minOf(vol, 1f)); vol -= 1f }
    }
}

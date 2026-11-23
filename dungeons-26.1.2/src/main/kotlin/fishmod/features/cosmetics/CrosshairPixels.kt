package fishmod.features.cosmetics

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag

// 16x16 hand-drawn crosshair, stored in crosshairPixels as 256 ARGB hex words (00000000 = empty)
object CrosshairPixels {
    const val SIZE = 16

    private var cacheRaw: String? = null
    private var cache = IntArray(SIZE * SIZE)

    // decoded pixel grid, re-parsed only when the stored string changes
    @JvmStatic
    fun get(): IntArray {
        val raw = FishSettings.crosshairPixels
        if (raw == cacheRaw) return cache
        val out = IntArray(SIZE * SIZE)
        FishDiag.guard("CrosshairPixels.1", "custom crosshair decode failed") {
            for (i in 0 until SIZE * SIZE) {
                val s = i * 8
                if (s + 8 > raw.length) break
                out[i] = java.lang.Long.parseLong(raw.substring(s, s + 8), 16).toInt()
            }
        }
        cacheRaw = raw
        cache = out
        return out
    }

    @JvmStatic
    fun save(px: IntArray) {
        val sb = StringBuilder(SIZE * SIZE * 8)
        for (c in px) sb.append(String.format("%08x", c))
        FishSettings.crosshairPixels = sb.toString()
    }

    @JvmStatic
    fun isEmpty(): Boolean = get().all { (it ushr 24) == 0 }
}

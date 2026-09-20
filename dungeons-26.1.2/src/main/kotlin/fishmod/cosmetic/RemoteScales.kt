package fishmod.cosmetic

import fishmod.utils.config.values.FishSettings
import java.util.concurrent.ConcurrentHashMap

object RemoteScales {

    private val byUuid: MutableMap<String, FloatArray> = ConcurrentHashMap()

    @JvmStatic
    fun get(uuidNoDashes: String): FloatArray? = byUuid[uuidNoDashes]

    @JvmStatic
    fun clearAll() {
        byUuid.clear()
    }

    @JvmStatic
    fun acceptScales(queried: Set<String>, scales: Map<String, String>) {
        if (!FishSettings.playerSizeShared) {
            byUuid.clear()
            return
        }
        for (u in queried) {
            val xyz = parse(scales[u])
            if (xyz == null) byUuid.remove(u) else byUuid[u] = xyz
        }
    }

    private fun parse(raw: String?): FloatArray? {
        if (raw == null || raw.isEmpty()) return null
        val parts = raw.split(",")
        try {
            var x: Float
            var y: Float
            var z: Float
            if (parts.size == 1) {
                x = parts[0].trim().toFloat()
                y = x
                z = x
            } else if (parts.size == 3) {
                x = parts[0].trim().toFloat()
                y = parts[1].trim().toFloat()
                z = parts[2].trim().toFloat()
            } else {
                return null
            }
            x = clamp(x)
            y = clamp(y)
            z = clamp(z)
            if (x <= 0f || y <= 0f || z <= 0f) return null
            if (x == 1.0f && y == 1.0f && z == 1.0f) return null
            return floatArrayOf(x, y, z)
        } catch (e: NumberFormatException) {
            return null
        }
    }

    private fun clamp(s: Float): Float = maxOf(PlayerSize.MIN, minOf(PlayerSize.MAX, s))
}

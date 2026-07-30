package fishmod.cosmetic

import fishmod.utils.config.values.FishSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds other players' shared render sizes (uuid without dashes -> {x,y,z} scale), filled by
 * [RemoteSync]. The local counterpart is just the `playerSize*` config; this is the
 * multiplayer view, mirroring [RemoteNicks]/`RemoteItems`.
 *
 * The wire value is a comma string: "x,y,z" (or a single "s" for legacy uniform sizes).
 */
object RemoteScales {

    private val byUuid: MutableMap<String, FloatArray> = ConcurrentHashMap()

    /** Shared {x,y,z} for a player, or null if they have none (render at 1,1,1). */
    @JvmStatic
    fun get(uuidNoDashes: String): FloatArray? = byUuid[uuidNoDashes]

    /** Drop all remotely-sourced sizes (called when sharing is toggled off). */
    @JvmStatic
    fun clearAll() {
        byUuid.clear()
    }

    /**
     * Apply a [RemoteSync] poll. `queried` is every on-server uuid we asked about;
     * `scales` holds only those with a size set. A uuid in `queried` but absent from
     * `scales` (or a 1,1,1 value) means "no custom size", so any stale entry is dropped.
     */
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

    /** Parse "x,y,z" (or "s") -> clamped {x,y,z}, or null when absent/identity/malformed. */
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

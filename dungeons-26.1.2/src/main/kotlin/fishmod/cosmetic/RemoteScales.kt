package fishmod.cosmetic

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.FishSettings
import java.util.concurrent.ConcurrentHashMap

object RemoteScales {

    private val byUuid: MutableMap<String, FloatArray> = ConcurrentHashMap()

    @JvmStatic
    fun get(uuidNoDashes: String): FloatArray? = byUuid[uuidNoDashes]

    @JvmStatic
    fun clearAll() {
        byUuid.clear()
        PlayerModel.clearRemote()
    }

    @JvmStatic
    fun acceptScales(queried: Set<String>, scales: Map<String, String>) {
        if (!FishSettings.playerSizeShared) {
            byUuid.clear()
            PlayerModel.clearRemote()
            return
        }
        byUuid.keys.retainAll(queried)
        PlayerModel.retainRemote(queried)
        for (u in queried) {
            val raw = scales[u]
            val parts = raw?.split(",")
            PlayerModel.acceptRemote(u, if (parts != null && parts.size == 4) parts[3].trim().uppercase() else null)
            val xyz = parse(if (parts != null && parts.size == 4) parts.take(3).joinToString(",") else raw)
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
                FishDiag.fail("RemoteScales.1", "scale has ${parts.size} parts: '$raw'")
                return null
            }
            if (x.isNaN() || y.isNaN() || z.isNaN()) return null
            x = PlayerSize.clamp(x)
            y = PlayerSize.clamp(y)
            z = PlayerSize.clamp(z)
            if (x <= 0f || y <= 0f || z <= 0f) return null
            if (x == 1.0f && y == 1.0f && z == 1.0f) return null
            return floatArrayOf(x, y, z)
        } catch (e: NumberFormatException) {
            FishDiag.fail("RemoteScales.2", "non-numeric scale '$raw'", e)
            return null
        }
    }
}

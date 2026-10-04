package fishmod.features.diana

import net.minecraft.sounds.SoundEvent

// Mutes Hypixel's ambient burrow noises and the spade's note-block echo
object DianaSoundMute {

    @JvmStatic
    fun shouldMute(event: SoundEvent, x: Double, y: Double, z: Double): Boolean {
        if (!Diana.inHub()) return false
        val path = event.location().path
        if (DianaSettings.dianaMuteSpadeSounds && Diana.holdingSpade && (path.startsWith("block.note_block.") || path == "entity.enderman.teleport")) return true
        if (DianaSettings.dianaMuteBurrowSounds) {
            for (w in DianaWaypoints.list) {
                if (w.type != WpType.BURROW) continue
                val c = w.center
                val dx = c.x - x; val dy = c.y - y; val dz = c.z - z
                if (dx * dx + dy * dy + dz * dz <= 2.5 * 2.5) return true
            }
        }
        return false
    }
}

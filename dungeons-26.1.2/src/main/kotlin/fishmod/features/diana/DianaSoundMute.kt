package fishmod.features.diana

import net.minecraft.sounds.SoundEvent
import kotlin.math.abs

// Mutes specific Hypixel burrow/spade sounds only; everything else (mobs, players) always plays
object DianaSoundMute {

    @JvmStatic
    fun shouldMute(event: SoundEvent, pitch: Float, x: Double, y: Double, z: Double): Boolean {
        if (!Diana.inHub()) return false
        val path = event.location().path
        // Spade echo: ascending note-block harp
        if (DianaSettings.dianaMuteSpadeSounds && Diana.holdingSpade && path == "block.note_block.harp") return true
        if (DianaSettings.dianaMuteBurrowSounds) {
            // Digging noise
            if (path == "entity.zombie.infect" && abs(pitch - 1.968f) < 0.02f) return true
            // A burrow's ping, only right next to a burrow or guess
            if (path == "block.note_block.pling" && nearWaypoint(x, y, z, 4.0)) return true
        }
        return false
    }

    private fun nearWaypoint(x: Double, y: Double, z: Double, r: Double): Boolean =
        DianaWaypoints.list.any {
            (it.type == WpType.BURROW || it.type == WpType.GUESS) &&
                it.center.distanceToSqr(x, y, z) <= r * r
        }
}

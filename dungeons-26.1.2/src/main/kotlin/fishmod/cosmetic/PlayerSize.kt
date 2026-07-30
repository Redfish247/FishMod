package fishmod.cosmetic

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import java.util.UUID

/**
 * Customizable player model size with independent X (width), Y (height) and Z (depth) axes. This is
 * purely a RENDER scale (a `matrices.scale()` applied in the player renderer) — it never touches
 * the scale attribute, hitbox or any packet, so it's safe on Hypixel and works offline too.
 *
 * Your own size is always shown to you locally when enabled. When "Share" is on it is published to the
 * shared store so other mod users render you at that size, and you render theirs — the multiplayer
 * counterpart, riding the same version-gated [RemoteSync] poll as nicks/items.
 */
object PlayerSize {

    const val MIN = 0.25f
    const val MAX = 5.0f

    private val IDENTITY = floatArrayOf(1.0f, 1.0f, 1.0f)

    @JvmStatic
    fun init() {
        // Re-publish our size on join (only does anything when sharing is enabled).
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> uploadOwn() }
    }

    /** Effective render scale {x,y,z} for a player: own config locally, others' shared size when on. */
    @JvmStatic
    fun scaleFor(p: Player): FloatArray {
        val mc = Minecraft.getInstance()
        val isSelf = mc.player != null && p.getUUID() == mc.player!!.getUUID()
        if (isSelf) {
            return if (FishSettings.playerSizeEnabled) localSelfValue() else IDENTITY
        }
        if (!FishSettings.playerSizeShared) return IDENTITY
        val s = RemoteScales.get(p.getUUID().toString().replace("-", ""))
        return s ?: IDENTITY
    }

    /**
     * What YOU see for your own model: the raw config X/Y/Z, floored to a tiny positive so the model never
     * inverts/vanishes, but with NO upper cap. Edit `playerSizeScale*` in config/fishmod-settings.json
     * to any value (e.g. 100) to go huge — the GUI slider is still bounded 0.25–5.0, hand-editing isn't.
     */
    @JvmStatic
    fun localSelfValue(): FloatArray {
        if (!FishSettings.playerSizeEnabled) return IDENTITY
        return floatArrayOf(
            floorPos(FishSettings.playerSizeScaleX.toFloat()),
            floorPos(FishSettings.playerSizeScaleY.toFloat()),
            floorPos(FishSettings.playerSizeScaleZ.toFloat())
        )
    }

    /** The size we broadcast to OTHER mod users: clamped to MIN..MAX so we never force a giant on them. */
    @JvmStatic
    fun ownShareValue(): FloatArray {
        if (!FishSettings.playerSizeEnabled) return IDENTITY
        return floatArrayOf(
            clamp(FishSettings.playerSizeScaleX.toFloat()),
            clamp(FishSettings.playerSizeScaleY.toFloat()),
            clamp(FishSettings.playerSizeScaleZ.toFloat())
        )
    }

    /** Publish (or clear) the local player's size to the shared store. No-op when not sharing. */
    @JvmStatic
    fun uploadOwn() {
        if (!FishSettings.playerSizeShared) return
        upload(ownShareValue())
    }

    /** Force-clear our shared size ({1,1,1} = delete server-side). Used when turning Share off. */
    @JvmStatic
    fun clearOwnShare() {
        upload(IDENTITY)
    }

    private fun upload(xyz: FloatArray) {
        val mc = Minecraft.getInstance()
        if (mc.user == null) return
        val id: UUID = mc.user.profileId ?: return
        HypixelApi.uploadScale(id.toString().replace("-", ""), xyz[0], xyz[1], xyz[2])
    }

    @JvmStatic
    fun clamp(s: Float): Float = maxOf(MIN, minOf(MAX, s))

    /** Floor to a tiny positive so an out-of-range/zero config value never inverts or hides the model. */
    @JvmStatic
    fun floorPos(s: Float): Float = maxOf(0.01f, s)
}

package fishmod.cosmetic

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import java.util.UUID

object PlayerSize {

    const val MIN = 0.25f
    const val MAX = 5.0f

    private val IDENTITY = floatArrayOf(1.0f, 1.0f, 1.0f)

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> uploadOwn() }
    }

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

    @JvmStatic
    fun localSelfValue(): FloatArray {
        if (!FishSettings.playerSizeEnabled) return IDENTITY
        return floatArrayOf(
            floorPos(FishSettings.playerSizeScaleX.toFloat()),
            floorPos(FishSettings.playerSizeScaleY.toFloat()),
            floorPos(FishSettings.playerSizeScaleZ.toFloat())
        )
    }

    @JvmStatic
    fun ownShareValue(): FloatArray {
        if (!FishSettings.playerSizeEnabled) return IDENTITY
        return floatArrayOf(
            clamp(FishSettings.playerSizeScaleX.toFloat()),
            clamp(FishSettings.playerSizeScaleY.toFloat()),
            clamp(FishSettings.playerSizeScaleZ.toFloat())
        )
    }

    @JvmStatic
    fun uploadOwn() {
        if (!FishSettings.playerSizeShared) return
        upload(ownShareValue())
    }

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

    @JvmStatic
    fun floorPos(s: Float): Float = maxOf(0.01f, s)
}

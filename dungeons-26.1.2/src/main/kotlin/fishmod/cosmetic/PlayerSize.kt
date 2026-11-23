package fishmod.cosmetic

import fishmod.compat.curScreen
import fishmod.utils.debug.FishDiag
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
    private val KEYS = java.util.concurrent.ConcurrentHashMap<UUID, String>()

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> FishDiag.guard("PlayerSize.1", "player size upload on join failed") { uploadOwn() } }
    }

    @JvmStatic
    fun scaleFor(p: Player): FloatArray {
        val mc = Minecraft.getInstance()
        val isSelf = mc.player != null && p.getUUID() == mc.player!!.getUUID()
        if (isSelf) {
            if (mc.curScreen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) return IDENTITY
            return if (FishSettings.playerSizeEnabled) selfScale() else IDENTITY
        }
        if (FishSettings.forceOthersSizeEnabled) {
            val f = floorPos(FishSettings.forceOthersSizeScale.toFloat())
            if (f != forcedFor) { forcedFor = f; forcedScale = floatArrayOf(f, f, f) }
            return forcedScale
        }
        if (!FishSettings.playerSizeShared) return IDENTITY
        val id = p.getUUID()
        if (KEYS.size > 512) KEYS.clear()
        val key = KEYS.getOrPut(id) { id.toString().replace("-", "") }
        return RemoteScales.get(key) ?: IDENTITY
    }

    private var forcedFor = Float.NaN
    private var forcedScale = IDENTITY
    private var selfScale = IDENTITY
    private var selfX = Float.NaN
    private var selfY = Float.NaN
    private var selfZ = Float.NaN

    private fun selfScale(): FloatArray {
        val x = floorPos(FishSettings.playerSizeScaleX.toFloat())
        val y = floorPos(FishSettings.playerSizeScaleY.toFloat())
        val z = floorPos(FishSettings.playerSizeScaleZ.toFloat())
        if (x != selfX || y != selfY || z != selfZ) { selfX = x; selfY = y; selfZ = z; selfScale = floatArrayOf(x, y, z) }
        return selfScale
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

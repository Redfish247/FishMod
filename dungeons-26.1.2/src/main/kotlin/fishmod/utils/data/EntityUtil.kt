package fishmod.utils.data

import fishmod.utils.Misc
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object EntityUtil {

    @JvmStatic
    fun isClientPlayer(entity: Entity?): Boolean {
        val clientPlayer = Minecraft.getInstance().player ?: return false
        return clientPlayer === entity
    }

    @JvmStatic
    fun isClientPlayer(name: String?): Boolean {
        val clientPlayer = Minecraft.getInstance().player ?: return false
        return clientPlayer.name.string == name
    }

    @JvmStatic
    fun isClientPlayer(id: Int): Boolean {
        val clientPlayer = Minecraft.getInstance().player ?: return false
        return clientPlayer.id == id
    }

    @JvmStatic
    fun getLerpedPos(entity: Entity): Vec3 {
        val tickProgress = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
        if (tickProgress.isNaN() || tickProgress < 0.0 || tickProgress > 1.0) FishDiag.fail("EntityUtil.1", "partial tick out of range: $tickProgress")
        return Misc.getPos(entity, tickProgress)
    }
}

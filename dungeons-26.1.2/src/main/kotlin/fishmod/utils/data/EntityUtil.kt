package fishmod.utils.data

import fishmod.utils.Misc
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object EntityUtil {

    private val playerMap = ConcurrentHashMap<Player, Boolean>()

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register {
            playerMap.clear()
            false
        }

        Events.ON_LOCATION_CHANGE.register { _ ->
            playerMap.clear()
            false
        }

        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is Player) {
                playerMap.remove(entity)
            }
        }
    }

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
    fun isARealPlayer(entity: Entity?): Boolean {
        if (entity is Player) {
            if (playerMap.containsKey(entity)) {
                return playerMap[entity] == true
            }

            val networkHandler: ClientPacketListener = Minecraft.getInstance().connection ?: return false
            val result = checkPlayer(entity, networkHandler)
            playerMap[entity] = result
            return result
        }
        return false
    }

    private fun checkPlayer(player: Player, networkHandler: ClientPacketListener): Boolean {
        val entry: PlayerInfo? = networkHandler.getPlayerInfo(player.uuid)

        // this is a hack which will fail if someone has a really old bugged ign that includes a space
        if (entry != null) {
            val name = entry.profile.name
            return name.isNotEmpty() && !name.contains(" ")
        }

        return false
    }

    @JvmStatic
    fun isWearing(entity: LivingEntity?, slot: EquipmentSlot?, name: String?): Boolean {
        if (entity == null || slot == null || name == null) return false
        val equippedStack = entity.getItemBySlot(slot)
        return equippedStack.hoverName.string.contains(name)
    }

    @JvmStatic
    fun getBox(entity: Entity): AABB {
        val tickProgress = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()

        val pos = Misc.getPos(entity, tickProgress)

        val dimension = entity.getDimensions(entity.pose)
        return dimension.makeBoundingBox(pos)
    }

    @JvmStatic
    fun getLerpedPos(entity: Entity): Vec3 {
        val tickProgress = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
        return Misc.getPos(entity, tickProgress)
    }
}

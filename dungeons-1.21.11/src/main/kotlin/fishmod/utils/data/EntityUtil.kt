package fishmod.utils.data

import fishmod.utils.Misc
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap

object EntityUtil {

    private val playerMap = ConcurrentHashMap<PlayerEntity, Boolean>()

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
            if (entity is PlayerEntity) {
                playerMap.remove(entity)
            }
        }
    }

    @JvmStatic
    fun isClientPlayer(entity: Entity?): Boolean {
        val clientPlayer = MinecraftClient.getInstance().player ?: return false
        return clientPlayer === entity
    }

    @JvmStatic
    fun isClientPlayer(name: String?): Boolean {
        val clientPlayer = MinecraftClient.getInstance().player ?: return false
        return clientPlayer.name.string == name
    }

    @JvmStatic
    fun isClientPlayer(id: Int): Boolean {
        val clientPlayer = MinecraftClient.getInstance().player ?: return false
        return clientPlayer.id == id
    }

    @JvmStatic
    fun isARealPlayer(entity: Entity?): Boolean {
        if (entity is PlayerEntity) {
            if (playerMap.containsKey(entity)) {
                return playerMap[entity] == true
            }

            val networkHandler: ClientPlayNetworkHandler = MinecraftClient.getInstance().networkHandler ?: return false
            val result = checkPlayer(entity, networkHandler)
            playerMap[entity] = result
            return result
        }
        return false
    }

    private fun checkPlayer(player: PlayerEntity, networkHandler: ClientPlayNetworkHandler): Boolean {
        val entry: PlayerListEntry? = networkHandler.getPlayerListEntry(player.uuid)

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
        val equippedStack = entity.getEquippedStack(slot)
        return equippedStack.name.string.contains(name)
    }

    @JvmStatic
    fun getBox(entity: Entity): Box {
        val tickProgress = MinecraftClient.getInstance().renderTickCounter.getTickProgress(false).toDouble()

        val pos = Misc.getPos(entity, tickProgress)

        val dimension = entity.getDimensions(entity.pose)
        return dimension.getBoxAt(pos)
    }

    @JvmStatic
    fun getLerpedPos(entity: Entity): Vec3d {
        val tickProgress = MinecraftClient.getInstance().renderTickCounter.getTickProgress(false).toDouble()
        return Misc.getPos(entity, tickProgress)
    }
}

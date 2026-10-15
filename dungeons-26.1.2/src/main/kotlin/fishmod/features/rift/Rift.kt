package fishmod.features.rift

import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

// Shared Rift state + init of every Rift helper
object Rift {

    fun inRift(): Boolean = Location.`in`(Location.THE_RIFT)

    // Sidebar area name (e.g. "Dreadfarm"), maintained by Mining's sidebar poll
    fun area(): String = fishmod.features.mining.Mining.area

    fun inArea(vararg names: String): Boolean = inRift() && names.any { area().contains(it, ignoreCase = true) }

    fun strip(s: String) = s.replace(Constants.STRIP_COLOR_REGEX, "")

    // Named armor stands within r blocks of the player
    fun namedStands(r: Double): List<Pair<ArmorStand, String>> {
        val mc = Minecraft.getInstance()
        val me = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        return level.getEntitiesOfClass(ArmorStand::class.java, AABB(me.blockPosition()).inflate(r))
            .mapNotNull { s -> s.customName?.string?.let { s to strip(it) } }
    }

    // Closest non-stand mob just below a nametag stand
    fun mobUnder(stand: Entity): Entity? {
        val level = Minecraft.getInstance().level ?: return null
        return level.getEntities(stand, stand.boundingBox.inflate(1.0, 3.0, 1.0)) { it !is ArmorStand && it.isAlive }
            .minByOrNull { it.distanceToSqr(stand) }
    }

    @JvmStatic
    fun init() {
        FishDiag.guard("Rift.1", "RiftNametag init failed") { RiftNametag.init() }
        FishDiag.guard("Rift.2", "RiftDreadfarm init failed") { RiftDreadfarm.init() }
        FishDiag.guard("Rift.3", "RiftMobs init failed") { RiftMobs.init() }
        FishDiag.guard("Rift.4", "RiftSnake init failed") { RiftSnake.init() }
        FishDiag.guard("Rift.5", "RiftVampire init failed") { RiftVampire.init() }
    }
}

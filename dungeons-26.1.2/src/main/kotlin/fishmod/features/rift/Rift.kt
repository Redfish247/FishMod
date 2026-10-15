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

    private val KNOWN = listOf(
        "Dreadfarm", "Living Cave", "Living Stillness", "West Village", "Infested House", "Wyld Woods", "Mirrorverse",
        "Black Lagoon", "Stillgore", "Oubliette", "Colosseum", "Mountaintop", "Continuum", "Trial Grounds", "Time-Torn Isles",
        "Wizardman Bureau", "Wizard Brawl", "Walk of Fame", "Time Chamber", "Taylor", "Barry Center", "Village Plaza",
        "Cake House", "Hemovibe", "Broken Cage", "Lonely Island", "Wizard Tower", "Dolphin", "Enigma",
    )
    @Volatile private var cachedArea = ""
    private var tick = 0

    // Tab "Area:" widget first, then any sidebar line naming a known Rift area, then Mining's raw sidebar glyph line
    private fun pollArea() {
        val tab = fishmod.features.mining.Mining.tabLine("Area:")?.removePrefix("Area:")?.trim().orEmpty()
        if (tab.isNotEmpty()) { cachedArea = tab; return }
        val lines = fishmod.features.mining.Mining.sidebar
        val hit = lines.firstOrNull { l -> KNOWN.any { l.contains(it, ignoreCase = true) } }
        cachedArea = hit?.dropWhile { !it.isLetter() }?.trim() ?: fishmod.features.mining.Mining.area
    }

    // Current Rift area name (e.g. "Dreadfarm")
    fun area(): String = cachedArea

    fun heldId(): String? = Minecraft.getInstance().player?.mainHandItem?.let { fishmod.utils.data.ItemUtil.getId(it) }

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
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player != null && tick++ % 20 == 0) { if (inRift()) pollArea() else cachedArea = "" }
        }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { cachedArea = ""; false }
        FishDiag.guard("Rift.1", "RiftNametag init failed") { RiftNametag.init() }
        FishDiag.guard("Rift.2", "RiftDreadfarm init failed") { RiftDreadfarm.init() }
        FishDiag.guard("Rift.3", "RiftMobs init failed") { RiftMobs.init() }
        FishDiag.guard("Rift.4", "RiftSnake init failed") { RiftSnake.init() }
        FishDiag.guard("Rift.5", "RiftVampire init failed") { RiftVampire.init() }
        FishDiag.guard("Rift.6", "RiftParkour init failed") { RiftParkour.init() }
    }
}

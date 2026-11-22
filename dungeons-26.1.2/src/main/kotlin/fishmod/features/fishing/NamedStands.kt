package fishmod.features.fishing

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.decoration.ArmorStand

// One named-armor-stand scan per game tick, shared by the fishing scanners
object NamedStands {
    private var level: ClientLevel? = null
    private var tick = Long.MIN_VALUE
    private val stands = ArrayList<ArmorStand>()

    init { fishmod.utils.events.FishEvents.worldChange.register { level = null; stands.clear(); false } }

    @JvmStatic
    fun of(lvl: ClientLevel): List<ArmorStand> {
        val t = lvl.gameTime
        if (lvl !== level || t != tick) {
            level = lvl; tick = t
            stands.clear()
            for (e in lvl.entitiesForRendering()) if (e is ArmorStand && e.hasCustomName()) stands.add(e)
        }
        return stands
    }
}

package fishmod.features.mining

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Creeper

// Glacite corpse highlight + Dwarven Mines ghost box (ghosts = charged creepers)
object MiningBoxes {

    private var corpses: List<Entity> = emptyList()
    private var ghosts: List<Entity> = emptyList()
    private var tick = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            FishDiag.guard("MiningBoxes.1", "mining box scan threw") { scan() }
        }
        RenderingEvents.GIZMO.register { _ ->
            FishDiag.guard("MiningBoxes.2", "mining box render threw") { render() }
        }
    }

    private fun scan() {
        if (tick++ % 5 != 0) return
        val level = Minecraft.getInstance().level
        val doCorpse = FishSettings.corpseHighlightEnabled && Mining.inShaft()
        val doGhost = FishSettings.ghostBoxEnabled && Location.`in`(Location.DWARVEN_MINES)
        if (level == null || (!doCorpse && !doGhost)) { corpses = emptyList(); ghosts = emptyList(); return }
        val c = ArrayList<Entity>(); val g = ArrayList<Entity>()
        for (e in level.entitiesForRendering()) {
            if (doCorpse && e is ArmorStand && Corpses.typeOf(e) != null) c += e
            else if (doGhost && isGhost(e)) g += e
        }
        corpses = c; ghosts = g
    }

    @JvmStatic
    fun isGhost(e: Entity): Boolean = e is Creeper && e.isPowered

    @JvmStatic
    fun hideGhost(e: Entity): Boolean =
        FishSettings.ghostBoxEnabled && FishSettings.ghostBoxHideModel && isGhost(e) && Location.`in`(Location.DWARVEN_MINES)

    private fun render() {
        if (FishSettings.corpseHighlightEnabled) for (e in corpses) if (e.isAlive)
            RenderUtils.gizmoBox(e.boundingBox, 0, FishSettings.corpseHighlightColor, true)
        if (FishSettings.ghostBoxEnabled) for (e in ghosts) if (e.isAlive)
            RenderUtils.gizmoBox(e.boundingBox, 0, FishSettings.ghostBoxColor)
    }
}

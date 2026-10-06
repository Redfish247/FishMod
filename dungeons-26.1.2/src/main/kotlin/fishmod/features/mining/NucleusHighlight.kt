package fishmod.features.mining

import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.world.phys.AABB
import fishmod.features.mining.MiningSettings as S

// Boxes around the five barrier pockets in the Crystal Nucleus where crystals get placed
object NucleusHighlight {

    // SkyHanni NucleusBarriersBox coords (inclusive block corners)
    private class Pocket(val box: AABB, val color: () -> Int)
    private fun box(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        AABB(minOf(x1, x2).toDouble(), minOf(y1, y2).toDouble(), minOf(z1, z2).toDouble(),
            maxOf(x1, x2) + 1.0, maxOf(y1, y2) + 1.0, maxOf(z1, z2) + 1.0)

    private val POCKETS = listOf(
        Pocket(box(474, 123, 524, 484, 111, 534)) { S.miningNucleusAmber },
        Pocket(box(474, 123, 492, 484, 111, 502)) { S.miningNucleusAmethyst },
        Pocket(box(508, 123, 473, 518, 111, 483)) { S.miningNucleusTopaz },
        Pocket(box(542, 123, 492, 552, 111, 502)) { S.miningNucleusJade },
        Pocket(box(542, 123, 524, 552, 111, 534)) { S.miningNucleusSapphire },
    )

    // Area name, or position as a fallback if the sidebar line doesn't parse
    private fun inNucleus(): Boolean {
        if (Mining.area.contains("Nucleus")) return true
        val p = net.minecraft.client.Minecraft.getInstance().player ?: return false
        return p.x in 460.0..567.0 && p.z in 460.0..567.0 && p.y in 100.0..190.0
    }

    fun init() {
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.miningNucleusBoxes || !Mining.inHollows()) return@register
            if (S.miningNucleusOnlyInside && !inNucleus()) return@register
            for (p in POCKETS) {
                val c = p.color()
                if (S.miningNucleusFilled) RenderUtils.fillBox(ps, vc, p.box, Mining.alpha(c, S.miningNucleusOpacity))
                Mining.outline(ps, vc, p.box, Mining.alpha(c, 100), 2f)
            }
        }
    }
}

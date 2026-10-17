package fishmod.features.kuudra

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.kuudra.Kuudra.Supply
import fishmod.features.mining.Mining
import fishmod.utils.data.EntityUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.features.kuudra.KuudraSettings as S
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object KuudraRender {

    private class Lineup(val supply: Supply, val start: List<BlockPos>, val aims: List<BlockPos>, val color: Int)

    // Pearl lineups (Odin PearlWaypoints)
    private val LINEUPS = listOf(
        Lineup(Supply.Shop, listOf(BlockPos(-71, 79, -135), BlockPos(-86, 78, -129)), listOf(BlockPos(-146, 107, -60), BlockPos(-147, 111, -69)), 0xFFFF5555.toInt()),
        Lineup(Supply.Triangle, listOf(BlockPos(-68, 77, -123)), listOf(BlockPos(-149, 104, -70)), 0xFFFF55FF.toInt()),
        Lineup(Supply.X, listOf(BlockPos(-135, 77, -139)), listOf(BlockPos(-59, 115, -71)), 0xFFFFFF55.toInt()),
        Lineup(Supply.xCannon, listOf(BlockPos(-131, 79, -114)), listOf(BlockPos(-59, 106, -59), BlockPos(-51, 108, -67), BlockPos(-39, 93, -76)), 0xFFFFFFFF.toInt()),
        Lineup(Supply.Square, listOf(BlockPos(-141, 78, -91)), listOf(BlockPos(-59, 106, -59), BlockPos(-58, 127, -148), BlockPos(-146, 107, -60),
            BlockPos(-149, 104, -70), BlockPos(-168, 124, -118), BlockPos(-65, 109, -162)), 0xFF55FFFF.toInt()),
        Lineup(Supply.Equals, listOf(BlockPos(-66, 76, -88)), listOf(BlockPos(-168, 124, -118)), 0xFF55FF55.toInt()),
        Lineup(Supply.Slash, listOf(BlockPos(-115, 77, -69)), listOf(BlockPos(-65, 109, -162)), 0xFF5555FF.toInt()),
    )
    private val SQUARE_AIM = mapOf(
        Supply.xCannon to BlockPos(-59, 106, -59), Supply.X to BlockPos(-58, 127, -148), Supply.Shop to BlockPos(-146, 107, -60),
        Supply.Triangle to BlockPos(-149, 104, -70), Supply.Equals to BlockPos(-168, 124, -118), Supply.Slash to BlockPos(-65, 109, -162)
    )

    fun init() {
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (Kuudra.inKuudra()) FishDiag.guard("KuudraRender.1", "kuudra fill render failed") { fills(ps, vc) }
        }
        RenderingEvents.NO_DEPTH_LINE.register { ctx, ps, vc ->
            if (!Kuudra.inKuudra()) return@register
            FishDiag.guard("KuudraRender.2", "kuudra line render failed") { lines(ps, vc) }
            FishDiag.guard("KuudraRender.3", "kuudra text render failed") {
                fun txt(s: String, p: Vec3, sc: Float) = RenderUtils.renderText(ctx, ps, Component.literal(s), p, sc)
                val ph = Kuudra.phase
                if (ph == 1) {
                    if ((S.kuudraSupplyMaster && S.kuudraSupplyWaypoints)) for (g in Kuudra.giants) txt("§ePick Up!", supplyPos(g).add(0.0, 2.5, 0.0), 1.5f)
                    if ((S.kuudraSupplyMaster && S.kuudraDropWaypoints)) for (s in drops()) txt("§ePlace Here!", Vec3.atCenterOf(s.drop).add(0.0, 1.5, 0.0), 1.5f)
                    if ((S.kuudraSupplyMaster && S.kuudraPreAreaLabels)) for (s in Supply.entries) if (s != Supply.None) txt("§e${s.name}", Vec3.atCenterOf(s.pick), 2f)
                }
                if (ph == 2) {
                    if ((S.kuudraBuildMaster && S.kuudraPileWaypoints)) for (a in Kuudra.piles) txt(a.name.string, a.position().add(0.0, 3.0, 0.0), 1.5f)
                    if ((S.kuudraBuildMaster && S.kuudraBallistaText)) {
                        txt("§bBuild §c${Kuudra.buildPct}%", Vec3(-101.5, 82.0, -105.5), 3f)
                        txt("§bBuilders ${Kuudra.builders}", Vec3(-101.5, 81.0, -105.5), 3f)
                    }
                }
                val b = Kuudra.boss
                if ((S.kuudraBossMaster && S.kuudraHpWorld) && b != null && b.isAlive) txt(KuudraHuds.hpText(b.health), b.position().add(b.lookAngle.scale(13.0)).add(0.0, 10.0, 0.0), 4f)
            }
        }
    }

    // Odin's offset from the giant to the crate on the ground
    fun supplyPos(g: Entity): Vec3 {
        val p = EntityUtil.getLerpedPos(g)
        val r = Math.toRadians(g.yRot + 130.0)
        return Vec3(p.x + 3.7 * cos(r), 73.0, p.z + 3.7 * sin(r))
    }

    private fun drops() = Supply.entries.filter { it != Supply.None && it != Supply.Square && it.active }

    private fun beam(ps: PoseStack, vc: VertexConsumer, x: Double, y: Double, z: Double, argb: Int) {
        RenderUtils.fillBox(ps, vc, AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5), Mining.alpha(argb, 45))
        RenderUtils.fillBox(ps, vc, AABB(x - 0.15, y + 1.0, z - 0.15, x + 0.15, y + 60.0, z + 0.15), Mining.alpha(argb, 35))
    }

    private fun fills(ps: PoseStack, vc: VertexConsumer) {
        val ph = Kuudra.phase
        if (ph == 1) {
            if ((S.kuudraSupplyMaster && S.kuudraSupplyWaypoints)) for (g in Kuudra.giants) { val p = supplyPos(g); beam(ps, vc, p.x, p.y, p.z, S.kuudraSupplyColor) }
            if ((S.kuudraSupplyMaster && S.kuudraDropWaypoints)) for (s in drops()) {
                val c = if (Kuudra.missing == s) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
                beam(ps, vc, s.drop.x + 0.5, s.drop.y.toDouble(), s.drop.z + 0.5, c)
            }
            if ((S.kuudraSupplyMaster && S.kuudraPearlWaypoints)) pearls { box, c, filled -> if (filled) RenderUtils.fillBox(ps, vc, box, Mining.alpha(c, 60)) }
        }
        if (ph == 2 && (S.kuudraBuildMaster && S.kuudraPileWaypoints)) for (a in Kuudra.piles) beam(ps, vc, a.x, a.y, a.z, S.kuudraPileColor)
    }

    private fun lines(ps: PoseStack, vc: VertexConsumer) {
        val ph = Kuudra.phase
        if (ph == 1 && (S.kuudraSupplyMaster && S.kuudraGiantBox)) for (g in Kuudra.giants) Mining.outline(ps, vc, g.boundingBox, Mining.alpha(S.kuudraSupplyColor, 100), 2f)
        if (ph == 1 && (S.kuudraSupplyMaster && S.kuudraPearlWaypoints)) pearls { box, c, filled -> if (!filled) Mining.outline(ps, vc, box, c, 2f) }
        val b = Kuudra.boss
        if ((S.kuudraBossMaster && S.kuudraHighlight) && b != null && b.isAlive) Mining.outline(ps, vc, b.boundingBox, S.kuudraHighlightColor, 3f)
        if ((S.kuudraBossMaster && S.kuudraTeamHighlight)) for (p in Kuudra.players) if (p.isAlive) Mining.outline(ps, vc, p.boundingBox, S.kuudraTeamColor, 2f)
        val e = Kuudra.elle
        if ((S.kuudraBossMaster && S.kuudraElleHighlight) && e != null && e.isAlive) {
            val p = e.position()
            Mining.outline(ps, vc, AABB(p.x - 0.4, p.y - 2.0, p.z - 0.4, p.x + 0.4, p.y, p.z + 0.4), S.kuudraElleColor, 2f)
        }
    }

    // Closest lineup only when "hide far" is on; Square aims only at the called-missing crate
    private inline fun pearls(draw: (AABB, Int, Boolean) -> Unit) {
        val me = Minecraft.getInstance().player?.blockPosition() ?: return
        val sorted = LINEUPS.sortedBy { l -> l.start.minOf { it.distSqr(me) } }
        for ((i, l) in sorted.withIndex()) {
            for (s in l.start) draw(AABB(s), l.color, false)
            if (S.kuudraPearlHideFar && i > 0) continue
            val miss = Kuudra.missing
            for (a in l.aims) {
                if (l.supply == Supply.Square && miss != Supply.None && miss != Supply.Square && SQUARE_AIM[miss] != a) continue
                draw(AABB(a), l.color, true)
            }
        }
    }
}

package fishmod.features.dungeon.puzzles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

/**
 * Blaze puzzle solver (ported from Odin's BlazeSolver). Finds the Blaze armor stands by their
 * "[Lv15] ♨ Blaze cur/max❤" nametag, reads max HP, and orders them: **Lower Blaze** kills highest
 * HP first (descending), **Higher Blaze** kills lowest first (ascending). Boxes them in that order —
 * next = green, second = gold, rest = white — with an optional connecting line.
 *
 * Registered twice (one per room) since the framework keys solvers by a single room name.
 */
class BlazeSolver(override val roomName: String, private val ascending: Boolean) : PuzzleSolver {

    private val HP = Regex("^\\[Lv15] ♨ Blaze [\\d,]+/([\\d,]+)❤$")
    private var ordered: List<ArmorStand> = emptyList()
    private var tickAcc = 0

    override fun onExit() = reset()
    override fun reset() { ordered = emptyList() }

    override fun onTick(mc: Minecraft) {
        if (!FishSettings.blazeSolver) { ordered = emptyList(); return }
        if (++tickAcc < 10) return
        tickAcc = 0
        val level = mc.level ?: return
        val hp = HashMap<ArmorStand, Int>()
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val name = e.customName!!.string.replace(Regex("§."), "")
            val max = HP.find(name)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: continue
            hp[e] = max
        }
        ordered = if (ascending) hp.keys.sortedBy { hp[it] } else hp.keys.sortedByDescending { hp[it] }
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.blazeSolver || ordered.isEmpty()) return
        val alive = ordered.filter { it.isAlive }
        val w = 1.0
        val h = 2.0
        alive.forEachIndexed { i, e ->
            val argb = when (i) {
                0 -> FishSettings.blazeFirstColor
                1 -> FishSettings.blazeSecondColor
                else -> FishSettings.blazeOtherColor
            }
            val box = AABB(
                e.x - w / 2, e.y - 1 - h / 2, e.z - w / 2,
                e.x + w / 2, e.y - 1 + h / 2, e.z + w / 2,
            )
            val rgba = RenderUtils.toFloats(argb)
            val style = FishSettings.puzzleSolverStyle
            if (style != "Outline") RenderUtils.renderFilled(matrices, vc, box, rgba)
            if (style != "Filled") RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))

            if (FishSettings.blazeLine && i in 1..FishSettings.blazeLineCount) {
                val prev = alive[i - 1]
                RenderUtils.renderLine(
                    matrices, vc,
                    net.minecraft.world.phys.Vec3(prev.x, prev.y - 1, prev.z),
                    net.minecraft.world.phys.Vec3(e.x, e.y - 1, e.z),
                    rgba,
                )
            }
        }
    }

    override fun onEnter(room: Room) = reset()
}

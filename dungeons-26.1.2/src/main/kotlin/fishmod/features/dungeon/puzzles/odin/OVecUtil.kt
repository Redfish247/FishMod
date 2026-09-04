package fishmod.features.dungeon.puzzles.odin

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Vec helpers the puzzle solvers need (`isXZInterceptable` + friends). */
object OVecUtil {

    fun isXZInterceptable(box: AABB, range: Double, pos: Vec3, yaw: Float, pitch: Float): Boolean {
        val eyeY = Minecraft.getInstance().player?.eyeY ?: 0.0
        val start = Vec3(pos.x, pos.y + eyeY, pos.z)
        val goal = start.add(getLook(yaw, pitch).multiply(range, range, range))
        return isVecInZ(intermediateWithX(start, goal, box.minX), box) ||
            isVecInZ(intermediateWithX(start, goal, box.maxX), box) ||
            isVecInX(intermediateWithZ(start, goal, box.minZ), box) ||
            isVecInX(intermediateWithZ(start, goal, box.maxZ), box)
    }

    private fun getLook(yaw: Float, pitch: Float): Vec3 {
        val f2 = -cos(-pitch * 0.017453292f).toDouble()
        return Vec3(
            sin(-yaw * 0.017453292f - 3.1415927f) * f2,
            sin(-pitch * 0.017453292f).toDouble(),
            cos(-yaw * 0.017453292f - 3.1415927f) * f2,
        )
    }

    private fun isVecInX(v: Vec3?, box: AABB) = v != null && v.x >= box.minX && v.x <= box.maxX
    private fun isVecInZ(v: Vec3?, box: AABB) = v != null && v.z >= box.minZ && v.z <= box.maxZ

    private fun intermediateWithX(a: Vec3, goal: Vec3, x: Double): Vec3? {
        val dx = goal.x - a.x
        if (dx * dx < 1e-8) return null
        val t = (x - a.x) / dx
        return if (t in 0.0..1.0) Vec3(a.x + dx * t, a.y + (goal.y - a.y) * t, a.z + (goal.z - a.z) * t) else null
    }

    private fun intermediateWithZ(a: Vec3, goal: Vec3, z: Double): Vec3? {
        val dz = goal.z - a.z
        if (dz * dz < 1e-8) return null
        val t = (z - a.z) / dz
        return if (t in 0.0..1.0) Vec3(a.x + (goal.x - a.x) * t, a.y + (goal.y - a.y) * t, a.z + dz * t) else null
    }
}

package fishmod.utils

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.rendering.RenderUtils
import net.minecraft.world.phys.AABB

class Waypoint(
    private val x: Double, private val y: Double, private val z: Double,
    private val dx: Double, private val dy: Double, private val dz: Double,
    private val r: Float, private val g: Float, private val b: Float, private val a: Float,
    private val throughWall: Boolean
) {

    fun samePosition(x: Double, y: Double, z: Double): Boolean {
        return this.x == x && this.y == y && this.z == z
    }

    fun inRange(x: Double, y: Double, z: Double, range: Double): Boolean {
        val distanceSquared = (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y) + (this.z - z) * (this.z - z)
        return distanceSquared < range * range
    }

    fun isThroughWall(): Boolean {
        return throughWall
    }

    fun Render(consumer: VertexConsumer, matrixStack: PoseStack) {
        RenderUtils.renderFilled(matrixStack, consumer, AABB(x, y, z, x + dx, y + dy, z + dz), floatArrayOf(r, g, b, a))
    }

    override fun toString(): String {
        return "Waypoint{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", dx=" + dx +
                ", dy=" + dy +
                ", dz=" + dz +
                ", r=" + r +
                ", g=" + g +
                ", b=" + b +
                ", a=" + a +
                ", throughWall=" + throughWall +
                '}'
    }
}

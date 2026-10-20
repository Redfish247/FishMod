package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.VertexConsumer

// Records position+colour vertices so world layers can be built at collect time and replayed when the dispatcher draws them.
class RecordingConsumer : VertexConsumer {
    private var xyz = FloatArray(3 * 512)
    private var col = IntArray(512)
    private var wid = FloatArray(512)
    var count = 0
        private set

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        if (count == col.size) { xyz = xyz.copyOf(xyz.size * 2); col = col.copyOf(col.size * 2); wid = wid.copyOf(wid.size * 2) }
        xyz[count * 3] = x; xyz[count * 3 + 1] = y; xyz[count * 3 + 2] = z
        col[count] = -1
        wid[count] = 2f
        count++
        return this
    }

    override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = setColor((a shl 24) or (r shl 16) or (g shl 8) or b)
    override fun setColor(color: Int): VertexConsumer { if (count > 0) col[count - 1] = color; return this }
    override fun setUv(u: Float, v: Float): VertexConsumer = this
    override fun setUv1(u: Int, v: Int): VertexConsumer = this
    override fun setUv2(u: Int, v: Int): VertexConsumer = this
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
    override fun setLineWidth(width: Float): VertexConsumer { if (count > 0) wid[count - 1] = width; return this }

    fun x(i: Int) = xyz[i * 3].toDouble()
    fun y(i: Int) = xyz[i * 3 + 1].toDouble()
    fun z(i: Int) = xyz[i * 3 + 2].toDouble()
    fun color(i: Int) = col[i]
    fun width(i: Int) = wid[i]

    fun replay(vc: VertexConsumer) {
        for (i in 0 until count) vc.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(col[i])
    }
}

package fishmod.features

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Bakes anti-aliased crosshair preset shapes (Dot/Cross/Plus/Square) into small textures, supersampled
 *  and rendered at their exact final pixel size so they blit 1:1 with no GPU scaling artifacts. */
object CrosshairPresetTextures {

    private const val SUPERSAMPLE = 4

    private data class Key(val style: String, val scaleKey: Int)
    private val cache = HashMap<Key, Pair<Identifier, Int>>()

    /** Returns (textureId, halfExtent) — the texture is (halfExtent*2+1) square, centered on its own middle pixel. */
    @JvmStatic
    fun getTexture(style: String, scaleRaw: Double): Pair<Identifier, Int> {
        val scale = scaleRaw.coerceIn(0.1, 8.0)
        val key = Key(style, Math.round(scale * 100).toInt())
        cache[key]?.let { return it }

        val thickness = (2 * scale).coerceAtLeast(1.0)
        val armLen = (6 * scale).coerceAtLeast(2.0)
        val gap = (2 * scale).coerceAtLeast(0.0)
        val radius = (3 * scale).coerceAtLeast(1.0)

        val halfExtent = when (style) {
            "Dot" -> ceil(radius).toInt() + 1
            "Plus" -> ceil(armLen).toInt() + 1
            "Square" -> ceil(armLen + thickness / 2).toInt() + 1
            else -> ceil(gap + armLen).toInt() + 1 // "Cross"
        }
        val size = halfExtent * 2 + 1

        val img = NativeImage(NativeImage.Format.RGBA, size, size, true)
        for (py in 0 until size) {
            for (px in 0 until size) {
                var hits = 0
                for (sy in 0 until SUPERSAMPLE) {
                    for (sx in 0 until SUPERSAMPLE) {
                        val x = (px - halfExtent) + ((sx + 0.5) / SUPERSAMPLE - 0.5)
                        val y = (py - halfExtent) + ((sy + 0.5) / SUPERSAMPLE - 0.5)
                        if (inside(style, x, y, thickness, armLen, gap, radius)) hits++
                    }
                }
                val alpha = (hits * 255 / (SUPERSAMPLE * SUPERSAMPLE)).coerceIn(0, 255)
                img.setPixelABGR(px, py, (alpha shl 24) or 0xFFFFFF)
            }
        }

        val id = Identifier.fromNamespaceAndPath("fishmod", "crosshair_preset_${style.lowercase()}_${key.scaleKey}")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ id.toString() }, img))

        val result = id to halfExtent
        cache[key] = result
        return result
    }

    private fun inside(style: String, x: Double, y: Double, thickness: Double, armLen: Double, gap: Double, radius: Double): Boolean {
        val ax = abs(x)
        val ay = abs(y)
        val halfT = thickness / 2
        return when (style) {
            "Dot" -> x * x + y * y <= radius * radius
            "Plus" -> (ax <= halfT && ay <= armLen) || (ay <= halfT && ax <= armLen)
            "Square" -> abs(armLen - max(ax, ay)) <= halfT
            else -> (ax <= halfT && ay in gap..(gap + armLen)) || (ay <= halfT && ax in gap..(gap + armLen)) // "Cross"
        }
    }
}

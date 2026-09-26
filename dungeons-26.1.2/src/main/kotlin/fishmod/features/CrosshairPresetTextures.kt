package fishmod.features

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CrosshairPresetTextures {

    private val SDF_STYLES = setOf("Dot", "Circle Dot", "Target")

    private data class Key(val style: String, val scaleKey: Int)
    private val cache = object : LinkedHashMap<Key, Pair<Identifier, Int>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Pair<Identifier, Int>>): Boolean {
            if (size <= 16) return false
            Minecraft.getInstance().textureManager.release(eldest.value.first)
            return true
        }
    }

    @JvmStatic
    fun getTexture(style: String, scaleRaw: Double): Pair<Identifier, Int> {
        val scale = scaleRaw.coerceIn(0.1, 8.0)
        val key = Key(style, Math.round(scale * 100).toInt())
        cache[key]?.let { return it }

        val p = Params(scale)
        val baseHalfExtent = when (style) {
            "Dot" -> ceil(p.dotRadius).toInt()
            "Plus" -> ceil(p.armLen).toInt()
            "Square" -> ceil(p.armLen + p.thickness / 2).toInt()
            "Cross" -> ceil(p.gap + p.armLen).toInt()
            "Circle Dot" -> ceil(p.ringRadius + p.armLen + p.thickness / 2).toInt()
            "Target" -> ceil(p.ringRadius + p.thickness / 2).toInt()
            "Brackets", "Corners" -> ceil(p.bracketSize + p.thickness / 2).toInt()
            else -> ceil(p.armLen).toInt() + 1
        }
        val halfExtent = baseHalfExtent + 2
        val size = halfExtent * 2 + 1

        val img = NativeImage(NativeImage.Format.RGBA, size, size, true)
        if (style in SDF_STYLES) {
            for (py in 0 until size) {
                for (px in 0 until size) {
                    val x = (px - halfExtent).toDouble()
                    val y = (py - halfExtent).toDouble()
                    val dist = signedDistance(style, x, y, p)
                    val alpha = ((0.5 - dist).coerceIn(0.0, 1.0) * 255).toInt().coerceIn(0, 255)
                    img.setPixelABGR(px, py, (alpha shl 24) or 0xFFFFFF)
                }
            }
        } else {
            val fill = Array(size) { py -> BooleanArray(size) { px -> insideFill(style, px - halfExtent, py - halfExtent, p) } }
            for (py in 0 until size) {
                for (px in 0 until size) {
                    img.setPixelABGR(px, py, if (fill[py][px]) (0xFF shl 24) or 0xFFFFFF else 0)
                }
            }
        }

        val id = Identifier.fromNamespaceAndPath("fishmod", "crosshair_preset_${style.lowercase().replace(" ", "_")}_${key.scaleKey}")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ id.toString() }, img))

        val result = id to halfExtent
        cache[key] = result
        return result
    }

    private class Params(scale: Double) {
        val thickness: Double = (2 * scale).coerceAtLeast(1.0)
        val armLen: Double = (6 * scale).coerceAtLeast(2.0)
        val gap: Double = (2 * scale).coerceAtLeast(0.0)
        val dotRadius: Double = (3 * scale).coerceAtLeast(1.0)
        val ringRadius: Double = (7 * scale).coerceAtLeast(3.0)
        val bracketSize: Double = (7 * scale).coerceAtLeast(3.0)
        val bracketArm: Double = (4 * scale).coerceAtLeast(2.0)
    }

    private fun sdCircle(x: Double, y: Double, r: Double): Double = hypot(x, y) - r

    private fun sdRing(x: Double, y: Double, r: Double, halfThick: Double): Double = abs(hypot(x, y) - r) - halfThick

    private fun sdBox(x: Double, y: Double, ox: Double, oy: Double, halfW: Double, halfH: Double): Double {
        val qx = abs(x - ox) - halfW
        val qy = abs(y - oy) - halfH
        return hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0)
    }

    private fun signedDistance(style: String, x: Double, y: Double, p: Params): Double {
        val half = p.thickness / 2
        return when (style) {
            "Dot" -> sdCircle(x, y, p.dotRadius)
            "Target" -> min(sdRing(x, y, p.ringRadius, half), sdCircle(x, y, p.dotRadius))
            else -> {
                val armCenter = p.ringRadius + p.armLen / 2
                min(
                    min(sdRing(x, y, p.ringRadius, half), sdCircle(x, y, p.dotRadius)),
                    min(sdBox(x, y, armCenter, 0.0, p.armLen / 2, half), sdBox(x, y, -armCenter, 0.0, p.armLen / 2, half))
                )
            }
        }
    }

    private fun insideFill(style: String, x: Int, y: Int, p: Params): Boolean {
        val ax = abs(x).toDouble()
        val ay = abs(y).toDouble()
        val half = p.thickness / 2
        return when (style) {
            "Plus" -> (ax <= half && ay <= p.armLen) || (ay <= half && ax <= p.armLen)
            "Square" -> abs(p.armLen - max(ax, ay)) <= half
            "Cross" -> (ax <= half && ay in p.gap..(p.gap + p.armLen)) || (ay <= half && ax in p.gap..(p.gap + p.armLen))
            "Brackets", "Corners" -> {
                val cornerHit = (ay in (p.bracketSize - half)..(p.bracketSize + half) && ax >= p.bracketSize - p.bracketArm && ax <= p.bracketSize) ||
                    (ax in (p.bracketSize - half)..(p.bracketSize + half) && ay >= p.bracketSize - p.bracketArm && ay <= p.bracketSize)
                if (style == "Brackets") {
                    cornerHit || (ax <= half && ay <= p.bracketArm) || (ay <= half && ax <= p.bracketArm)
                } else {
                    cornerHit
                }
            }
            else -> {
                val c = cos(Math.PI / 4)
                val s = sin(Math.PI / 4)
                val rx = abs(x * c - y * s)
                val ry = abs(x * s + y * c)
                (rx <= half && ry <= p.armLen) || (ry <= half && rx <= p.armLen)
            }
        }
    }
}

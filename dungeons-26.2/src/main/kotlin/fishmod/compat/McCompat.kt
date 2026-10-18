package fishmod.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

// 26.2 side of the Minecraft API differences; overlays the 26.1.2 file of the same path.
typealias HudT = net.minecraft.client.gui.Hud

val Minecraft.curScreen: Screen? get() = gui.screen()
fun Minecraft.showScreen(s: Screen?) = gui.setScreen(s)
val Minecraft.hud: HudT get() = gui.hud
val Minecraft.hudHidden: Boolean get() = gui.hud.isHidden
val Minecraft.cam: Camera get() = gameRenderer.mainCamera()

typealias SlimeT = net.minecraft.world.entity.monster.cubemob.Slime
typealias MagmaCubeT = net.minecraft.world.entity.monster.cubemob.MagmaCube

object ET {
    @JvmField val ENDER_DRAGON = net.minecraft.world.entity.EntityTypes.ENDER_DRAGON
    @JvmField val FALLING_BLOCK = net.minecraft.world.entity.EntityTypes.FALLING_BLOCK
    @JvmField val LIGHTNING_BOLT = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT
    @JvmField val EXPERIENCE_ORB = net.minecraft.world.entity.EntityTypes.EXPERIENCE_ORB
}

val net.minecraft.core.BlockPos.center: net.minecraft.world.phys.Vec3 get() = net.minecraft.world.phys.Vec3(x + 0.5, y + 0.5, z + 0.5)

fun Minecraft.reloadChunks() { level?.let { levelRenderer.invalidateCompiledGeometry(it, options, cam, blockColors) } }

val Minecraft.mainTarget: com.mojang.blaze3d.pipeline.RenderTarget get() = gameRenderer.mainRenderTarget()

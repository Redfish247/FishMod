package fishmod.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

// 26.1 side of the Minecraft API differences; the 26.2 module overlays this file.
typealias HudT = net.minecraft.client.gui.Gui

val Minecraft.curScreen: Screen? get() = screen
fun Minecraft.showScreen(s: Screen?) = setScreen(s)
val Minecraft.hud: HudT get() = gui
val Minecraft.hudHidden: Boolean get() = options.hideGui
val Minecraft.cam: Camera get() = gameRenderer.mainCamera

typealias SlimeT = net.minecraft.world.entity.monster.Slime
typealias MagmaCubeT = net.minecraft.world.entity.monster.MagmaCube

object ET {
    @JvmField val ENDER_DRAGON = net.minecraft.world.entity.EntityType.ENDER_DRAGON
    @JvmField val FALLING_BLOCK = net.minecraft.world.entity.EntityType.FALLING_BLOCK
    @JvmField val LIGHTNING_BOLT = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT
    @JvmField val EXPERIENCE_ORB = net.minecraft.world.entity.EntityType.EXPERIENCE_ORB
}

val net.minecraft.core.BlockPos.center: net.minecraft.world.phys.Vec3 get() = net.minecraft.world.phys.Vec3(x + 0.5, y + 0.5, z + 0.5)

fun Minecraft.reloadChunks() { levelRenderer.allChanged() }

val Minecraft.mainTarget: com.mojang.blaze3d.pipeline.RenderTarget get() = mainRenderTarget

package fishmod.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

//? if <26.2 {
typealias HudT = net.minecraft.client.gui.Gui
//?} else {
/*typealias HudT = net.minecraft.client.gui.Hud
*///?}

//? if <26.2 {
val Minecraft.curScreen: Screen? get() = screen
fun Minecraft.showScreen(s: Screen?) = setScreen(s)
val Minecraft.hud: HudT get() = gui
val Minecraft.hudHidden: Boolean get() = options.hideGui
val Minecraft.cam: Camera get() = gameRenderer.mainCamera
//?} else {
/*val Minecraft.curScreen: Screen? get() = gui.screen()
fun Minecraft.showScreen(s: Screen?) = gui.setScreen(s)
val Minecraft.hud: HudT get() = gui.hud
val Minecraft.hudHidden: Boolean get() = gui.hud.isHidden
val Minecraft.cam: Camera get() = gameRenderer.mainCamera()
*///?}

//? if <26.2 {
typealias SlimeT = net.minecraft.world.entity.monster.Slime
typealias MagmaCubeT = net.minecraft.world.entity.monster.MagmaCube
//?} else {
/*typealias SlimeT = net.minecraft.world.entity.monster.cubemob.Slime
typealias MagmaCubeT = net.minecraft.world.entity.monster.cubemob.MagmaCube
*///?}

object ET {
    //? if <26.2 {
    @JvmField val ENDER_DRAGON = net.minecraft.world.entity.EntityType.ENDER_DRAGON
    @JvmField val FALLING_BLOCK = net.minecraft.world.entity.EntityType.FALLING_BLOCK
    @JvmField val LIGHTNING_BOLT = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT
    @JvmField val EXPERIENCE_ORB = net.minecraft.world.entity.EntityType.EXPERIENCE_ORB
    //?} else {
    /*@JvmField val ENDER_DRAGON = net.minecraft.world.entity.EntityTypes.ENDER_DRAGON
    @JvmField val FALLING_BLOCK = net.minecraft.world.entity.EntityTypes.FALLING_BLOCK
    @JvmField val LIGHTNING_BOLT = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT
    @JvmField val EXPERIENCE_ORB = net.minecraft.world.entity.EntityTypes.EXPERIENCE_ORB
    *///?}
}

val net.minecraft.core.BlockPos.center: net.minecraft.world.phys.Vec3 get() = net.minecraft.world.phys.Vec3(x + 0.5, y + 0.5, z + 0.5)

//? if <26.2 {
fun Minecraft.reloadChunks() { levelRenderer.allChanged() }
//?} else {
/*fun Minecraft.reloadChunks() { level?.let { levelRenderer.invalidateCompiledGeometry(it, options, cam, blockColors) } }
*///?}

//? if <26.2 {
val Minecraft.mainTarget: com.mojang.blaze3d.pipeline.RenderTarget get() = mainRenderTarget
fun Minecraft.clearHudTitles() = gui.clearTitles()
//?} else {
/*val Minecraft.mainTarget: com.mojang.blaze3d.pipeline.RenderTarget get() = gameRenderer.mainRenderTarget()
fun Minecraft.clearHudTitles() = gui.hud.clearTitles()
*///?}

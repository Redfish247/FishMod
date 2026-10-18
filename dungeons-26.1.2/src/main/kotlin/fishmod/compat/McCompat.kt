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
}

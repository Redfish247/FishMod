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
}

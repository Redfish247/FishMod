package fishmod.utils

import fishmod.shaded.practicalconfig.data.SoundData
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.debug.Debug
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object Misc {
    @JvmField val INSTANCE: Minecraft = Minecraft.getInstance()
    private val ON: Component = Component.literal("ON").withStyle(ChatFormatting.GREEN)
    private val OFF: Component = Component.literal("OFF").withStyle(ChatFormatting.RED)

    @JvmStatic
    fun abbr(v: Double): String = when {
        v >= 1_000_000_000 -> "%.2fB".format(v / 1_000_000_000)
        v >= 1_000_000 -> "%.2fM".format(v / 1_000_000)
        v >= 1_000 -> "%.1fk".format(v / 1_000)
        else -> "%,d".format(v.toLong())
    }

    @JvmStatic
    fun getPos(entity: Entity, tickProgress: Double): Vec3 {
        val x = Mth.lerp(tickProgress, entity.xOld, entity.x)
        val y = Mth.lerp(tickProgress, entity.yOld, entity.y)
        val z = Mth.lerp(tickProgress, entity.zOld, entity.z)
        return Vec3(x, y, z)
    }

    @JvmStatic
    fun getDistanceSq(e1: Entity, e2: Entity): Double {
        return getDistanceSq(e1.x, e1.z, e2.x, e2.z)
    }

    @JvmStatic
    fun getDistanceSq(x1: Double, z1: Double, x2: Double, z2: Double): Double {
        return ((x1 - x2) * (x1 - x2)) + ((z1 - z2) * (z1 - z2))
    }

    @JvmStatic
    fun addChatMessage(text: Component) {
        try {
            val instance = INSTANCE ?: return
            val gameHud = instance.gui
            val hud = gameHud.chat
            forceMainThread { hud.addClientSystemMessage(Component.literal(ExtraOptions.textPrefix).append(text)) }
        } catch (ignored: IndexOutOfBoundsException) {
            Debug.LOGGER.error("Chat message failed to get added")
        }
    }

    @JvmStatic
    fun getStatusText(status: Boolean): Component {
        return if (status) ON else OFF
    }

    @JvmStatic
    fun setTitle(text: Component) {
        forceMainThread { INSTANCE.gui.setTitle(text) }
    }

    @JvmStatic
    fun forceTitle(title: Component, subtitle: Component) {
        forceMainThread {
            INSTANCE.gui.setTitle(title)
            INSTANCE.gui.setSubtitle(subtitle)
        }
    }

    @JvmStatic
    fun forceTitle(title: Component, subtitle: Component, stayMs: Int) {
        forceMainThread {
            val gui = INSTANCE.gui
            val acc = gui as fishmod.mixin.accessors.GuiAccessor
            acc.`fishmod$setTitleFadeInTime`(5)
            acc.`fishmod$setTitleStayTime`((stayMs / 50).coerceIn(1, 20 * 120))
            acc.`fishmod$setTitleFadeOutTime`(10)
            gui.setTitle(title)
            gui.setSubtitle(subtitle)
        }
    }

    @JvmStatic
    fun executeCommand(string: String) {
        val networkHandler = INSTANCE.connection ?: return
        val trimmed = string.trim()
        val command = if (trimmed.startsWith("/")) trimmed.substring(1) else trimmed
        forceMainThread { networkHandler.sendCommand(command) }
    }

    @JvmStatic
    fun sendSound(soundEvent: SoundEvent, volume: Float, pitch: Float) {
        val player: LocalPlayer = INSTANCE.player ?: return
        forceMainThread {
            if (volume <= 1f) {
                player.playSound(soundEvent, volume, pitch)
            } else {
                INSTANCE.soundManager.play(fishmod.utils.sound.LoudSoundInstance(soundEvent, volume, pitch))
            }
        }
    }

    @JvmStatic
    fun sendSound2D(soundEvent: SoundEvent, volume: Float, pitch: Float) {
        if (INSTANCE.player == null) return
        forceMainThread {
            INSTANCE.soundManager.play(fishmod.utils.sound.LoudSoundInstance(soundEvent, volume, pitch))
        }
    }

    @JvmStatic
    fun sendSound(soundData: SoundData) {
        sendSound(SoundEvent.createVariableRangeEvent(soundData.sound), soundData.volume, soundData.pitch)
    }

    @JvmStatic
    fun forceMainThread(runnable: Runnable) {
        if (INSTANCE.isSameThread) {
            runnable.run()
        } else {
            INSTANCE.executeIfPossible(runnable)
        }
    }
}

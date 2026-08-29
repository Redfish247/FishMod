package fishmod.utils

import config.practical.data.SoundData
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
    fun getPos(entity: Entity, tickProgress: Double): Vec3 {
        val x = Mth.lerp(tickProgress, entity.xOld, entity.x)
        val y = Mth.lerp(tickProgress, entity.yOld, entity.y)
        val z = Mth.lerp(tickProgress, entity.zOld, entity.z)
        return Vec3(x, y, z)
    }

    @JvmStatic
    fun getDistance(e1: Entity, e2: Entity): Double {
        return getDistance(e1.x, e1.z, e2.x, e2.z)
    }

    @JvmStatic
    fun getDistance(x1: Double, z1: Double, x2: Double, z2: Double): Double {
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
    fun executeCommand(string: String) {
        val networkHandler = INSTANCE.connection ?: return
        // sendCommand() expects no leading slash — strip one if the caller typed the command
        // the way they'd type it in chat.
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
                // Minecraft clamps a sound instance's gain to 1.0, so volume > 1 via playSound()
                // only widens the falloff radius, never the loudness. Route through a
                // LoudSoundInstance whose gain the SoundEngine/Channel mixins let exceed 1.0.
                INSTANCE.soundManager.play(fishmod.utils.sound.LoudSoundInstance(soundEvent, pitch, volume))
            }
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

package fishmod.utils

import config.practical.data.SoundData
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.debug.Debug
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

object Misc {
    @JvmField val INSTANCE: MinecraftClient = MinecraftClient.getInstance()
    private val ON: Text = Text.literal("ON").formatted(Formatting.GREEN)
    private val OFF: Text = Text.literal("OFF").formatted(Formatting.RED)

    @JvmStatic
    fun getPos(entity: Entity, tickProgress: Double): Vec3d {
        val x = MathHelper.lerp(tickProgress, entity.lastRenderX, entity.x)
        val y = MathHelper.lerp(tickProgress, entity.lastRenderY, entity.y)
        val z = MathHelper.lerp(tickProgress, entity.lastRenderZ, entity.z)
        return Vec3d(x, y, z)
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
    fun addChatMessage(text: Text) {
        try {
            val instance = INSTANCE ?: return
            val gameHud = instance.inGameHud
            val hud = gameHud.chatHud
            forceMainThread { hud.addMessage(Text.literal(ExtraOptions.textPrefix).append(text)) }
        } catch (ignored: IndexOutOfBoundsException) {
            Debug.LOGGER.error("Chat message failed to get added")
        }
    }

    @JvmStatic
    fun getStatusText(status: Boolean): Text {
        return if (status) ON else OFF
    }

    @JvmStatic
    fun setTitle(text: Text) {
        forceMainThread { INSTANCE.inGameHud.setTitle(text) }
    }

    @JvmStatic
    fun forceTitle(title: Text, subtitle: Text) {
        forceMainThread {
            INSTANCE.inGameHud.setTitle(title)
            INSTANCE.inGameHud.setSubtitle(subtitle)
        }
    }

    @JvmStatic
    fun executeCommand(string: String) {
        val networkHandler = INSTANCE.networkHandler ?: return
        // sendChatCommand() expects no leading slash — strip one if the caller typed the command
        // the way they'd type it in chat.
        val trimmed = string.trim()
        val command = if (trimmed.startsWith("/")) trimmed.substring(1) else trimmed
        forceMainThread { networkHandler.sendChatCommand(command) }
    }

    @JvmStatic
    fun sendSound(soundEvent: SoundEvent, volume: Float, pitch: Float) {
        val player = INSTANCE.player ?: return
        forceMainThread { player.playSound(soundEvent, volume, pitch) }
    }

    @JvmStatic
    fun sendSound(soundData: SoundData) {
        sendSound(SoundEvent.of(soundData.sound), soundData.volume, soundData.pitch)
    }

    @JvmStatic
    fun forceMainThread(runnable: Runnable) {
        if (INSTANCE.isOnThread) {
            runnable.run()
        } else {
            INSTANCE.executeSync(runnable)
        }
    }
}

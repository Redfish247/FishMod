package fishmod.utils

import com.mojang.brigadier.Command
import config.practical.data.SoundData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.sounds.SoundEvent
import java.util.concurrent.CopyOnWriteArrayList

object Scheduler {

    private var scheduledScreen: Screen? = null
    private var screenTicks: Int = 0
    private var scheduledCommand: String? = null

    private class Task(val task: Runnable, var delay: Int)

    private val tasks = CopyOnWriteArrayList<Task>()

    @JvmStatic
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { minecraftClient ->
            if (scheduledScreen != null) {
                screenTicks--
                if (screenTicks <= 0) {
                    minecraftClient.setScreen(scheduledScreen)
                    scheduledScreen = null
                }
            }

            val cmd = scheduledCommand
            if (cmd != null) {
                val player: LocalPlayer? = minecraftClient.player
                if (player != null && player.connection != null) {
                    player.connection.sendCommand(cmd)
                    scheduledCommand = null
                }
            }

            for (i in tasks.size - 1 downTo 0) {
                val task = tasks[i]
                task.delay--
                if (task.delay <= 0) {
                    minecraftClient.execute(task.task)
                    tasks.removeAt(i)
                }
            }
        }
    }

    @JvmStatic
    fun scheduleScreen(screen: Screen?): Int {
        if (screen == null) return -1
        scheduledScreen = screen
        screenTicks = 1
        return Command.SINGLE_SUCCESS
    }

    @JvmOverloads
    @JvmStatic
    fun scheduleSound(soundEvent: SoundEvent, volume: Float, pitch: Float, delay: Int = 1) {
        tasks.add(Task(Runnable {
            val player = Minecraft.getInstance().player ?: return@Runnable
            player.playSound(soundEvent, volume, pitch)
        }, delay))
    }

    @JvmOverloads
    @JvmStatic
    fun scheduleSound(soundData: SoundData, tick: Int = 1) {
        scheduleSound(SoundEvent.createVariableRangeEvent(soundData.sound), soundData.volume, soundData.pitch, tick)
    }

    @JvmStatic
    fun scheduleCommand(command: String) {
        scheduledCommand = command
    }

    @JvmStatic
    fun scheduleTask(runnable: Runnable, ticks: Int) {
        tasks.add(Task(runnable, ticks))
    }
}

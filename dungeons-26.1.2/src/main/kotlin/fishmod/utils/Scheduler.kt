package fishmod.utils

import config.practical.data.SoundData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent
import java.util.concurrent.CopyOnWriteArrayList

object Scheduler {

    private class Task(val task: Runnable, var delay: Int)

    private val tasks = CopyOnWriteArrayList<Task>()

    @JvmStatic
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { minecraftClient ->
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
    fun scheduleTask(runnable: Runnable, ticks: Int) {
        tasks.add(Task(runnable, ticks))
    }
}

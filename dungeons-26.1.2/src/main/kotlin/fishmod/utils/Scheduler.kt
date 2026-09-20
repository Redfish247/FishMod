package fishmod.utils

import fishmod.shaded.practicalconfig.data.SoundData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent
import java.util.ArrayDeque

object Scheduler {

    private class Task(val task: Runnable, var delay: Int)

    private val tasks = ArrayDeque<Task>()

    @JvmStatic
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { minecraftClient ->
            val due = ArrayList<Task>()
            synchronized(tasks) {
                val it = tasks.iterator()
                while (it.hasNext()) {
                    val task = it.next()
                    task.delay--
                    if (task.delay <= 0) {
                        due.add(task)
                        it.remove()
                    }
                }
            }
            for (task in due) minecraftClient.execute(task.task)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun scheduleSound(soundEvent: SoundEvent, volume: Float, pitch: Float, delay: Int = 1) {
        val task = Task(Runnable {
            val player = Minecraft.getInstance().player ?: return@Runnable
            player.playSound(soundEvent, volume, pitch)
        }, delay)
        synchronized(tasks) { tasks.add(task) }
    }

    @JvmOverloads
    @JvmStatic
    fun scheduleSound(soundData: SoundData, tick: Int = 1) {
        scheduleSound(SoundEvent.createVariableRangeEvent(soundData.sound), soundData.volume, soundData.pitch, tick)
    }

    @JvmStatic
    fun scheduleTask(runnable: Runnable, ticks: Int) {
        synchronized(tasks) { tasks.add(Task(runnable, ticks)) }
    }
}

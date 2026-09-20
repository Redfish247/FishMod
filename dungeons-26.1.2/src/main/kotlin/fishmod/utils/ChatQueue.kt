package fishmod.utils

import fishmod.features.dungeon.ChatCommandState
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.ArrayDeque

object ChatQueue {

    private const val MIN_GAP_MS = 1100L
    private const val DEDUP_MS = 3000L
    private const val MAX_PENDING = 5

    private val pending = ArrayDeque<String>()
    private var lastSentAt = 0L
    private var lastSentText = ""

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { flush() }
        Events.ON_WORLD_CHANGE.register { synchronized(pending) { pending.clear() }; false }
    }

    @JvmStatic
    fun enqueue(command: String) {
        val cmd = command.trim().removePrefix("/").trim()
        if (cmd.isEmpty()) return
        synchronized(pending) {
            if (pending.contains(cmd)) return
            if (cmd == lastSentText && System.currentTimeMillis() - lastSentAt < DEDUP_MS) return
            pending.addLast(cmd)
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    private fun flush() {
        val mc = Minecraft.getInstance()
        if (mc.connection == null || mc.player == null) return
        if (System.currentTimeMillis() - lastSentAt < MIN_GAP_MS) return

        val cmd = synchronized(pending) { pending.pollFirst() } ?: return
        mc.connection!!.sendCommand(cmd)
        lastSentAt = System.currentTimeMillis()
        lastSentText = cmd
        ChatCommandState.lastPartyCommandAt = lastSentAt
    }
}

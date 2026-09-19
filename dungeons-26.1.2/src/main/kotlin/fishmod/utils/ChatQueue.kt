package fishmod.utils

import fishmod.features.dungeon.ChatCommandState
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.ArrayDeque

/**
 * Single throttled outbound queue for mod-issued party-chat commands, so multiple auto-announcers
 * firing on the same event don't hit Hypixel's chat spam filter. Cleared on world change.
 */
object ChatQueue {

    private const val MIN_GAP_MS = 1100L   // Hypixel's repeat-message cooldown is ~1s
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

    /** Queue a command (no leading slash), e.g. `enqueue("pc 3.20s lost to lag.")`. */
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
        // keep Hypixel's error-reply suppression window warm
        ChatCommandState.lastPartyCommandAt = lastSentAt
    }
}

package fishmod.utils

import fishmod.features.dungeon.ChatCommandState
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.ArrayDeque

/**
 * Single throttled outbound queue for mod-issued party-chat / commands (`pc ...` and friends).
 *
 * Every event-driven auto-announcer (death message, lag, invinc procs, leap, score milestones,
 * warp-kick, ping, explosive shot, ...) routes through here instead of calling
 * `connection.sendCommand` directly. Without this, two announcers firing on the same wipe
 * (death + lag + "score missing") hit Hypixel's chat spam filter and the messages get dropped
 * or you eat a short chat mute.
 *
 * Guarantees: at most one send per [MIN_GAP_MS], identical text suppressed within [DEDUP_MS],
 * queue capped at [MAX_PENDING] (oldest dropped) so a backlog can never "catch up" and dump a
 * wall of stale lines minutes later. Cleared on world change.
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

package fishmod.features.storage

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu

/**
 * Pages through every storage page for you so the overlay/viewer has them all cached — instead of
 * having to open each `/enderchest` / `/backpack` by hand. Opens `/storage` first to learn which
 * pages you own, then walks them one at a time with a small delay (Hypixel command rate limit).
 */
object StorageAutoLoader {

    private enum class Phase { IDLE, OVERVIEW, PAGES }

    private var phase = Phase.IDLE
    private val queue = ArrayDeque<Int>()
    private var total = 0
    private var loaded = 0
    private var skipped = 0

    private var waitingFor = -1
    private var sentAt = 0L
    private var matchedAt = 0L
    private var nextAllowedAt = 0L

    private const val STEP_DELAY = 550L   // between page-open commands
    private const val OPEN_TIMEOUT = 3500L // give up on a page that never opens (you don't own it)
    private const val SETTLE = 150L        // let the container fill before snapshotting

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick() })
    }

    @JvmStatic
    fun running(): Boolean = phase != Phase.IDLE

    @JvmStatic
    fun start() {
        val mc = Minecraft.getInstance()
        if (!FishSettings.storageOverlayEnabled) {
            Misc.addChatMessage(Component.literal("§c[Storage] Enable the Storage Overlay feature first."))
            return
        }
        if (mc.player == null || mc.connection == null) {
            Misc.addChatMessage(Component.literal("§c[Storage] Not in a world."))
            return
        }
        if (running()) {
            Misc.addChatMessage(Component.literal("§e[Storage] Already loading… §7(/storageload stop to cancel)"))
            return
        }
        loaded = 0; skipped = 0
        queue.clear()
        phase = Phase.OVERVIEW
        matchedAt = 0L
        sendCommand("storage")
        Misc.addChatMessage(Component.literal("§b[Storage] Reading your storage overview…"))
    }

    @JvmStatic
    fun stop() {
        if (!running()) return
        finish(cancelled = true)
    }

    private fun sendCommand(cmd: String) {
        Minecraft.getInstance().connection?.sendCommand(cmd)
        sentAt = System.currentTimeMillis()
    }

    private fun tick() {
        if (phase == Phase.IDLE) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) { finish(cancelled = true); return }
        val now = System.currentTimeMillis()
        val screen = mc.screen as? AbstractContainerScreen<*>
        val title = screen?.title?.string?.replace(Regex("§."), "")

        when (phase) {
            Phase.OVERVIEW -> {
                if (title == "Storage") {
                    if (matchedAt == 0L) matchedAt = now
                    // StorageCache.tick() scans the overview while it's open; give it a beat.
                    if (now - matchedAt >= 500L) { buildQueue(); beginPages(now) }
                } else if (now - sentAt > OPEN_TIMEOUT) {
                    // Overview never opened — fall back to trying every page.
                    buildQueue()
                    beginPages(now)
                }
            }

            Phase.PAGES -> {
                if (waitingFor >= 0) {
                    val idx = title?.let { StoragePage.fromTitle(it)?.index }
                    if (screen != null && idx == waitingFor) {
                        if (matchedAt == 0L) matchedAt = now
                        val menu = screen.menu
                        val rows = (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)
                        if (rows > 1 && now - matchedAt >= SETTLE) {
                            val items = menu.slots.subList(9, rows * 9).map { it.item.copy() }
                            StorageCache.put(waitingFor, items)
                            loaded++
                            advance(now)
                        }
                    } else if (now - sentAt > OPEN_TIMEOUT) {
                        skipped++
                        advance(now)
                    }
                } else {
                    if (queue.isEmpty()) { finish(cancelled = false); return }
                    if (now >= nextAllowedAt) {
                        val next = queue.removeFirst()
                        waitingFor = next
                        matchedAt = 0L
                        sendCommand(StoragePage(next).let { if (it.isEnderChest) "enderchest ${next + 1}" else "backpack ${next - 9 + 1}" })
                    }
                }
            }

            Phase.IDLE -> {}
        }
    }

    private fun buildQueue() {
        val known = StorageCache.knownPages().sorted()
        queue.clear()
        queue.addAll(if (known.isNotEmpty()) known else (0..26).toList())
        total = queue.size
    }

    private fun beginPages(now: Long) {
        phase = Phase.PAGES
        waitingFor = -1
        matchedAt = 0L
        nextAllowedAt = now
        Misc.addChatMessage(Component.literal("§b[Storage] Loading $total page(s)…"))
    }

    private fun advance(now: Long) {
        waitingFor = -1
        matchedAt = 0L
        nextAllowedAt = now + STEP_DELAY
    }

    private fun finish(cancelled: Boolean) {
        phase = Phase.IDLE
        queue.clear()
        waitingFor = -1
        matchedAt = 0L
        Minecraft.getInstance().player?.closeContainer()
        val msg = if (cancelled) "§e[Storage] Stopped — cached $loaded page(s)."
        else "§a[Storage] Done — cached $loaded page(s)" + (if (skipped > 0) " §7($skipped not owned)" else "") + "."
        Misc.addChatMessage(Component.literal(msg))
    }
}

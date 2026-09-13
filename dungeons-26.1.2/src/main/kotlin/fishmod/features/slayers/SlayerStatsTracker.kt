package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Session Slayer statistics: XP gained, boss kills, and the derived per-hour rates.
 *
 * XP tracking
 * -----------
 * Every completed Slayer quest is exactly one boss kill of a known type+tier, and Hypixel's boss
 * Slayer-XP payout per tier is a fixed, long-stable table ([SlayerType.bossXpByTier]). So on each
 * `SLAYER QUEST COMPLETE!` we add that tier's payout — an exact figure, not a scoreboard estimate.
 *
 * Kill tracking
 * -------------
 * Incremented once per `SLAYER QUEST COMPLETE!`. Minibosses never trigger it. The chat line is
 * one-shot so there are no client-side duplicates to guard against.
 *
 * Coins are NOT tracked here — see [SlayerProfitTracker], which prices the actual drops.
 *
 * Active time
 * -----------
 * Wall-clock is only accumulated while [SlayerManager.isActiveSlayer] AND the player has moved/acted
 * within [IDLE_MS]. Per-tick deltas are clamped so a stall/alt-tab can't inflate rates. World /
 * island / quest changes stop the clock; it never counts menu/AFK/hub time.
 */
object SlayerStatsTracker {

    private const val IDLE_MS = 90_000L
    private const val MAX_TICK_MS = 2_000L

    private val SAVE_FILE: Path = Paths.get("config/fishmod/slayer_stats.json")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic @Volatile var xpGained: Long = 0; private set
    @JvmStatic @Volatile var kills: Int = 0; private set
    private var activeMs: Long = 0
    private var everStarted = false

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var lastX = 0.0; private var lastY = 0.0; private var lastZ = 0.0; private var havePos = false

    private class SaveData {
        var xpGained: Long = 0
        var kills: Int = 0
        var activeMs: Long = 0
        var everStarted = false
    }

    @JvmStatic
    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    private fun tick(mc: Minecraft) {
        val now = System.currentTimeMillis()
        val prev = lastTickMs
        lastTickMs = now
        if (!FishSettings.slayerAnyEnabled()) return

        val p = mc.player
        if (p != null && SlayerManager.isActiveSlayer()) {
            val x = p.x; val y = p.y; val z = p.z
            if (!havePos) {
                lastX = x; lastY = y; lastZ = z; havePos = true
                lastActivityMs = now
            } else if (Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > 0.05) {
                lastX = x; lastY = y; lastZ = z
                lastActivityMs = now
            }
            val idle = lastActivityMs > 0 && now - lastActivityMs > IDLE_MS
            if (!idle && prev > 0) {
                val delta = (now - prev).coerceIn(0, MAX_TICK_MS)
                if (delta > 0) { activeMs += delta; everStarted = true }
            }
        } else {
            havePos = false
        }
    }

    // ---------------------------------------------------------------- hooks from SlayerManager

    fun onQuestStarted() { lastActivityMs = System.currentTimeMillis() }

    fun onQuestChange(type: SlayerType, tier: Int) {
        havePos = false
        lastActivityMs = System.currentTimeMillis()
    }

    fun onQuestEnded() { havePos = false }

    fun onBossKill(type: SlayerType, tier: Int) {
        kills++
        xpGained += type.bossXp(tier)
        lastActivityMs = System.currentTimeMillis()
        everStarted = true
        save()
    }

    // ---------------------------------------------------------------- derived

    fun activeSeconds(): Double = activeMs / 1000.0

    private fun perHour(total: Double): Double {
        if (activeMs < 5_000L || total <= 0.0) return 0.0
        return total * 3_600_000.0 / activeMs
    }

    fun xpPerHour(): Double = perHour(xpGained.toDouble())
    fun killsPerHour(): Double = perHour(kills.toDouble())
    fun hasData(): Boolean = everStarted

    @JvmStatic
    fun reset() {
        xpGained = 0
        kills = 0
        activeMs = 0
        everStarted = false
        lastActivityMs = 0
        havePos = false
        save()
    }

    /** Compact number: 1,234 / 12.3K / 4.56M / 1.23B. */
    @JvmStatic
    fun short(v: Double): String {
        val a = Math.abs(v)
        return when {
            a < 1_000 -> String.format("%,d", v.toLong())
            a < 1_000_000 -> trim(v / 1_000.0) + "K"
            a < 1_000_000_000 -> trim(v / 1_000_000.0) + "M"
            else -> trim(v / 1_000_000_000.0) + "B"
        }
    }

    private fun trim(v: Double): String = String.format("%.2f", v).trimEnd('0').trimEnd('.')

    // ---------------------------------------------------------------- persistence

    @Synchronized
    private fun load() {
        try {
            if (!Files.exists(SAVE_FILE)) return
            val d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData::class.java) ?: return
            xpGained = d.xpGained
            kills = d.kills
            activeMs = d.activeMs
            everStarted = d.everStarted
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        try {
            Files.createDirectories(SAVE_FILE.parent)
            val d = SaveData()
            d.xpGained = xpGained
            d.kills = kills
            d.activeMs = activeMs
            d.everStarted = everStarted
            Files.writeString(SAVE_FILE, GSON.toJson(d))
        } catch (_: Exception) {
        }
    }
}

package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object SlayerStatsTracker {

    private const val IDLE_MS = 90_000L
    private const val MAX_TICK_MS = 2_000L

    private val SAVE_FILE: Path = Paths.get("config/fishmod/slayer_stats.json")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private class Stats {
        var xpGained: Long = 0
        var kills: Int = 0
        var activeMs: Long = 0
        var everStarted = false
        var rng: String? = null
        var rngStored: Long = -1
    }

    private val RNG_STORED = Regex("([\\d,]+) Stored XP")
    private const val RNG_WINDOW_MS = 10_000L
    private var pendingType: SlayerType? = null
    private var pendingXp = 0
    private var pendingAt = 0L

    // keyed by SlayerType.name so slayers don't share counters
    private val byType = HashMap<String, Stats>()

    private fun cur(): Stats? = SlayerManager.type?.let { byType[it.name] }
    private fun of(type: SlayerType): Stats = byType.getOrPut(type.name) { Stats() }

    @JvmStatic val xpGained: Long get() = cur()?.xpGained ?: 0
    @JvmStatic val kills: Int get() = cur()?.kills ?: 0

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var lastX = 0.0; private var lastY = 0.0; private var lastZ = 0.0; private var havePos = false

    private class SaveData {
        var byType: Map<String, Stats>? = null
    }

    @JvmStatic
    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try {
                tick(mc)
            } catch (e: Exception) {
                FishDiag.fail("SlayerStatsTracker.3", "slayer stats tick failed", e)
            }
        })
    }

    private fun tick(mc: Minecraft) {
        val now = System.currentTimeMillis()
        val prev = lastTickMs
        lastTickMs = now
        if (!FishSettings.slayerAnyEnabled()) return

        val p = mc.player
        val type = SlayerManager.type
        if (p != null && type != null && SlayerManager.isActiveSlayer()) {
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
                if (delta > 0) { val s = of(type); s.activeMs += delta; s.everStarted = true }
            }
        } else {
            havePos = false
        }
    }

    fun onQuestStarted() { lastActivityMs = System.currentTimeMillis() }

    fun onQuestChange(type: SlayerType, tier: Int) {
        havePos = false
        lastActivityMs = System.currentTimeMillis()
    }

    fun onQuestEnded() { havePos = false }

    fun onBossKill(type: SlayerType, tier: Int) {
        val s = of(type)
        s.kills++
        s.xpGained += type.bossXp(tier)
        s.everStarted = true
        lastActivityMs = System.currentTimeMillis()
        pendingType = type; pendingXp = type.bossXp(tier); pendingAt = lastActivityMs
        save()
    }

    // RNG meter jump after a kill is the real slayer XP (boosts included), so it replaces the table value
    fun onRng(value: String) {
        val type = SlayerManager.type ?: return
        val s = of(type)
        s.rng = value
        val stored = RNG_STORED.find(value)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        if (stored != null) {
            val delta = stored - s.rngStored
            if (s.rngStored >= 0 && pendingType == type && System.currentTimeMillis() - pendingAt < RNG_WINDOW_MS && delta in 1..50_000) {
                s.xpGained += delta - pendingXp
                pendingType = null
            }
            s.rngStored = stored
        }
        save()
    }

    fun rngText(): String? = cur()?.rng
    fun hasData(): Boolean = cur()?.everStarted == true

    @JvmStatic
    fun reset() {
        byType.clear()
        lastActivityMs = 0
        havePos = false
        save()
    }

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

    @Synchronized
    private fun load() {
        try {
            if (!Files.exists(SAVE_FILE)) return
            val d = FishDiag.notNull(GSON.fromJson(Files.readString(SAVE_FILE), SaveData::class.java), "SlayerStatsTracker.4") { "$SAVE_FILE decoded to null (empty file?)" } ?: return
            d.byType?.let { byType.putAll(it) }
        } catch (e: Exception) {
            FishDiag.fail("SlayerStatsTracker.1", "failed to load $SAVE_FILE", e)
        }
    }

    @Synchronized
    private fun save() {
        val d = SaveData()
        d.byType = HashMap(byType)
        val json = GSON.toJson(d)
        fishmod.utils.IoExecutor.execute {
            try {
                fishmod.utils.SafeFiles.writeAtomic(SAVE_FILE, json)
            } catch (e: Exception) {
                FishDiag.fail("SlayerStatsTracker.2", "failed to save $SAVE_FILE", e)
            }
        }
    }
}

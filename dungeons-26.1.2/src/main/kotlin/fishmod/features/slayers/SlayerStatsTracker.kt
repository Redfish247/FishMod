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
        // lifetime totals from the profile API, plus what we counted since that pull
        var apiXp: Long = -1
        var apiKills: Long = 0
        var sinceXp: Long = 0
        var sinceKills: Long = 0
    }

    private const val API_REFRESH_MS = 10 * 60_000L
    private var lastApiFetch = 0L
    @Volatile private var apiFetching = false

    // profile JSON key per slayer
    private fun apiKey(t: SlayerType) = when (t) {
        SlayerType.REVENANT -> "zombie"; SlayerType.TARANTULA -> "spider"; SlayerType.SVEN -> "wolf"
        SlayerType.VOIDGLOOM -> "enderman"; SlayerType.INFERNO -> "blaze"
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

    @JvmStatic val xpGained: Long get() = cur()?.let { if (it.apiXp >= 0) it.apiXp + it.sinceXp else it.xpGained } ?: 0
    @JvmStatic val kills: Long get() = cur()?.let { if (it.apiXp >= 0) it.apiKills + it.sinceKills else it.kills.toLong() } ?: 0
    fun fromApi(): Boolean = (cur()?.apiXp ?: -1) >= 0

    private fun maybeFetchApi(mc: Minecraft) {
        val now = System.currentTimeMillis()
        if (apiFetching || now - lastApiFetch < API_REFRESH_MS) return
        val uuid = mc.player?.uuid?.toString()?.replace("-", "") ?: return
        lastApiFetch = now
        apiFetching = true
        fishmod.utils.HypixelApi.fetchSelectedProfile(uuid) { profile ->
            val bosses = profile?.getAsJsonObject("members")?.getAsJsonObject(uuid)
                ?.getAsJsonObject("slayer")?.getAsJsonObject("slayer_bosses")
            mc.execute {
                apiFetching = false
                if (bosses == null) return@execute
                FishDiag.guard("SlayerStatsTracker.6", "apply slayer API totals failed") { applyApi(bosses) }
            }
        }
    }

    private fun applyApi(bosses: com.google.gson.JsonObject) {
        for (t in SlayerType.entries) {
            val b = bosses.getAsJsonObject(apiKey(t)) ?: continue
            val xp = b.get("xp")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
            var k = 0L
            for ((key, v) in b.entrySet()) if (key.startsWith("boss_kills_tier_") && v.isJsonPrimitive) k += v.asLong
            val s = of(t)
            // API lags a few minutes; only take it once it has caught up with our own count
            if (s.apiXp < 0 || xp >= s.apiXp + s.sinceXp) {
                s.apiXp = xp; s.apiKills = maxOf(k, s.apiKills + s.sinceKills); s.sinceXp = 0; s.sinceKills = 0
            }
        }
        save()
    }

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
            if (FishSettings.slayerStatsHudEnabled) maybeFetchApi(mc)
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
        s.sinceKills++
        s.sinceXp += type.bossXp(tier)
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
                s.sinceXp += delta - pendingXp
                pendingType = null
            }
            s.rngStored = stored
        }
        save()
    }

    fun rngText(): String? = cur()?.rng
    fun hasData(): Boolean = cur()?.let { it.everStarted || it.apiXp >= 0 } == true

    // clears our own counts; API totals stay and refresh on the next pull
    @JvmStatic
    fun reset() {
        for (s in byType.values) { s.xpGained = 0; s.kills = 0; s.activeMs = 0; s.everStarted = false }
        lastApiFetch = 0L
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

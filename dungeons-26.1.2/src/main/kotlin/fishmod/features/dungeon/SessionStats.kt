package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayDeque
import java.util.regex.Pattern

object SessionStats {

    private val DEATH_PAT = Pattern.compile("☠ \\S+ (?:was|were) killed by|☠ \\S+ (?:died|quit)")

    // Mort's intro line — fires the moment the dungeon run actually starts (same trigger LagTracker uses).
    private const val MORT_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."

    private const val WINDOW_MS = 3_600_000L // 1 hour for R/hr
    private const val IDLE_MS = 5 * 60_000L // pause after 5 min idle in-dungeon

    // Auto-pause reason: 0 = none, 1 = location (hub/lobby/pre-Mort), 2 = idle.
    private var autoPauseReason = 0

    // Movement tracking for idle detection
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var havePos = false

    private var sessionStartMs: Long = -1
    private var runs = 0
    private var deaths = 0
    private val runTimes = ArrayDeque<Long>()

    // Persistence
    private val SAVE_FILE: Path = Paths.get("config/fishmod/session_stats.json")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // Reset button hitbox state (set during inventory render, read during click)
    private var btnX = 0
    private var btnY = 0
    private var btnW = 0
    private var btnH = 0
    private var btnVisible = false
    private var pauseBtnX = 0
    private var pauseBtnY = 0
    private var pauseBtnW = 0
    private var pauseBtnH = 0
    private var paused = false
    private var pauseStartedMs: Long = 0
    private var lastActivityMs: Long = 0
    private var autoPaused = false

    private class SaveData {
        var sessionStartMs: Long = 0
        var runs = 0
        var deaths = 0
        var runTimes: LongArray? = null
        var paused = false
        var pauseStartedMs: Long = 0
        var autoPaused = false
        var lastActivityMs: Long = 0
    }

    /** Clears any auto-pause and advances the session start by the paused duration. */
    private fun autoResume() {
        val now = System.currentTimeMillis()
        if (paused && autoPaused) {
            if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs)
            pauseStartedMs = 0
            paused = false
            autoPaused = false
            autoPauseReason = 0
        }
        lastActivityMs = now
    }

    /** Player moved / acted in-dungeon: resume only if we were idle-paused (not hub/pre-Mort paused). */
    private fun noteMovement() {
        if (paused && autoPaused && autoPauseReason == 2) autoResume()
        else lastActivityMs = System.currentTimeMillis()
    }

    private fun autoPause(reason: Int, freezeAtMs: Long) {
        if (paused) return
        paused = true
        autoPaused = true
        autoPauseReason = reason
        pauseStartedMs = if (freezeAtMs > 0) freezeAtMs else System.currentTimeMillis()
    }

    private fun tickAutoPause() {
        if (paused || sessionStartMs <= 0 || lastActivityMs <= 0) return
        if (System.currentTimeMillis() - lastActivityMs >= IDLE_MS) autoPause(2, lastActivityMs)
    }

    @JvmStatic
    fun init() {
        load()

        FishHudEditor.register("Session Stats",
            { FishSettings.sessionStatsHudX }, { v -> FishSettings.sessionStatsHudX = v },
            { FishSettings.sessionStatsHudY }, { v -> FishSettings.sessionStatsHudY = v },
            80, 14 * 4,
            { FishSettings.sessionStatsScale }, { v -> FishSettings.sessionStatsScale = v },
            {
                if (!FishSettings.sessionStatsEnabled) false
                else {
                    val loc = Location.getCurrentLocation()
                    (loc == Location.DUNGEON && FishSettings.sessionStatsInDungeon)
                        || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub)
                }
            })

        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ -> autoPause(1, System.currentTimeMillis()) })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (!FishSettings.sessionStatsEnabled) return@EndTick
            val loc = Location.getCurrentLocation()
            // Pause whenever not actively inside a dungeon run (dungeon hub, lobby, etc.).
            if (loc != Location.DUNGEON) {
                havePos = false
                autoPause(1, System.currentTimeMillis())
                return@EndTick
            }
            // Inside the dungeon: track movement so we can pause after 5 min idle (AFK).
            if (client.player != null) {
                val x = client.player!!.x
                val y = client.player!!.y
                val z = client.player!!.z
                if (!havePos) {
                    lastX = x; lastY = y; lastZ = z; havePos = true
                    if (lastActivityMs <= 0) lastActivityMs = System.currentTimeMillis()
                } else if (Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > 0.05) {
                    lastX = x; lastY = y; lastZ = z
                    noteMovement()
                }
            }
            tickAutoPause()
        })

        Events.ON_WORLD_CHANGE.register {
            havePos = false
            if (FishSettings.sessionStatsResetOnRelog) reset() else autoPause(1, System.currentTimeMillis())
            false
        }

        Events.ON_LOCATION_CHANGE.register { _ ->
            havePos = false // recalibrate movement baseline on every location change
            false
        }

        Events.ON_RUN_END.register {
            if (!FishSettings.sessionStatsEnabled) return@register false
            if (paused && !autoPaused) return@register false
            val now = System.currentTimeMillis()
            if (sessionStartMs < 0) sessionStartMs = now
            lastActivityMs = now
            runs++
            runTimes.addLast(now)
            save()
            false
        }

        Events.ON_GAME_MESSAGE.register { message ->
            if (!FishSettings.sessionStatsEnabled) return@register false
            val s = message.string.replace(Regex("§."), "")

            // Dungeon run started (Mort's intro) — start the clock and resume any auto-pause.
            if (s == MORT_START) {
                if (sessionStartMs < 0) sessionStartMs = System.currentTimeMillis()
                autoResume()
                havePos = false
                save()
                return@register false
            }

            if (paused && !autoPaused) return@register false
            val loc = Location.getCurrentLocation()
            val track = (loc == Location.DUNGEON && FishSettings.sessionStatsInDungeon)
                || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub)
            if (!track) return@register false
            if (DEATH_PAT.matcher(s).find()) {
                if (sessionStartMs < 0) sessionStartMs = System.currentTimeMillis()
                lastActivityMs = System.currentTimeMillis()
                deaths++
                save()
            }
            false
        }
    }

    @JvmStatic
    fun getRuns(): Int = runs

    @JvmStatic
    fun getDeaths(): Int = deaths

    @JvmStatic
    fun getRunsPerHour(): Double = runsPerHour()

    @JvmStatic
    fun getSessionStartMs(): Long = sessionStartMs

    @JvmStatic
    fun formatDuration(): String {
        if (sessionStartMs < 0) return "—"
        val ref = if (paused && pauseStartedMs > 0) pauseStartedMs else System.currentTimeMillis()
        // Defensive clamp: if Mort start fires while manually paused, sessionStartMs can be set
        // newer than pauseStartedMs, producing a transient negative duration. Show 0s instead.
        return formatTime(Math.max(0, ref - sessionStartMs))
    }

    @JvmStatic
    fun reset() {
        sessionStartMs = -1
        runs = 0
        deaths = 0
        runTimes.clear()
        paused = false
        pauseStartedMs = 0
        autoPaused = false
        autoPauseReason = 0
        lastActivityMs = 0
        havePos = false
        save()
    }

    @Synchronized
    private fun load() {
        try {
            if (!Files.exists(SAVE_FILE)) return
            val json = Files.readString(SAVE_FILE)
            val d = GSON.fromJson(json, SaveData::class.java) ?: return
            sessionStartMs = d.sessionStartMs
            runs = d.runs
            deaths = d.deaths
            runTimes.clear()
            if (d.runTimes != null) for (t in d.runTimes!!) runTimes.addLast(t)
            paused = d.paused
            pauseStartedMs = d.pauseStartedMs
            autoPaused = d.autoPaused
            lastActivityMs = d.lastActivityMs
            // If the session was still "running" when the client closed, freeze it at the
            // last real activity now rather than letting the next tick pause at the current
            // (post-relaunch) time, which would count the entire offline gap as session time.
            if (!paused && sessionStartMs > 0) {
                autoPause(1, if (lastActivityMs > 0) lastActivityMs else sessionStartMs)
            }
        } catch (ignored: IOException) {
        } catch (ignored: RuntimeException) {
        }
    }

    @Synchronized
    private fun save() {
        try {
            Files.createDirectories(SAVE_FILE.parent)
            val d = SaveData()
            d.sessionStartMs = sessionStartMs
            d.runs = runs
            d.deaths = deaths
            d.runTimes = runTimes.map { it }.toLongArray()
            d.paused = paused
            d.pauseStartedMs = pauseStartedMs
            d.autoPaused = autoPaused
            d.lastActivityMs = lastActivityMs
            Files.writeString(SAVE_FILE, GSON.toJson(d))
        } catch (ignored: IOException) {
        }
    }

    private fun runsPerHour(): Double {
        val now = if (paused && pauseStartedMs > 0) pauseStartedMs else System.currentTimeMillis()
        val cutoff = now - WINDOW_MS
        while (runTimes.isNotEmpty() && runTimes.peekFirst() < cutoff) runTimes.pollFirst()
        if (runTimes.size < 2) return if (runTimes.size == 1) 0.0 else 0.0
        val window = runTimes.peekLast() - runTimes.peekFirst()
        if (window < 1000) return 0.0
        return (runTimes.size - 1) * 3_600_000.0 / window
    }

    private fun formatTime(ms: Long): String {
        var s = ms / 1000
        var m = s / 60; s %= 60
        val h = m / 60; m %= 60
        if (h > 0) return "${h}h ${m}m"
        if (m > 0) return "${m}m ${s}s"
        return "${s}s"
    }

    private fun buildLines(): Array<String> {
        val rhr = runsPerHour()
        val ref = if (paused && pauseStartedMs > 0) pauseStartedMs else System.currentTimeMillis()
        val timeStr = if (sessionStartMs > 0)
            formatTime(Math.max(0, ref - sessionStartMs))
        else "—"
        return arrayOf(
            "§7Runs: §a" + runs + (if (paused) " §e§l(PAUSED)" else ""),
            "§7Deaths: §c" + deaths,
            "§7R/hr: §e" + (if (rhr == 0.0) "§8—" else String.format("%.1f", rhr)),
            "§7Time: §f" + timeStr
        )
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        btnVisible = false
        if (!FishSettings.sessionStatsEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        if (mc.screen != null && mc.screen !is net.minecraft.client.gui.screens.ChatScreen) return
        val loc = Location.getCurrentLocation()
        val show = (loc == Location.DUNGEON && FishSettings.sessionStatsInDungeon)
            || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub)
        if (!show) return

        val x = FishSettings.sessionStatsHudX
        val y = FishSettings.sessionStatsHudY
        val lh = Constants.TEXT_HEIGHT + 2
        val lines = buildLines()
        val sc = FishSettings.sessionStatsScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in lines.indices)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    /** Rendered on top of any HandledScreen (chest/inventory) with a clickable reset button. */
    @JvmStatic
    fun renderInScreen(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        btnVisible = false
        if (!FishSettings.sessionStatsEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.screen !is AbstractContainerScreen<*>) return
        val loc = Location.getCurrentLocation()
        val show = (loc == Location.DUNGEON && FishSettings.sessionStatsInDungeon)
            || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub)
        if (!show) return

        val x = FishSettings.sessionStatsHudX
        val y = FishSettings.sessionStatsHudY
        val lh = Constants.TEXT_HEIGHT + 2
        val lines = buildLines()
        val sc = FishSettings.sessionStatsScale.toFloat()

        val resetLabel = "§l[ Reset ]"
        val pauseLabel = if (paused) "§l[ Resume ]" else "§l[ Pause ]"
        val resetW = mc.font.width(resetLabel)
        val pauseW = mc.font.width(pauseLabel)
        val padX = 4
        val padY = 3
        val localBtnY = lh * lines.size - 2
        val localResetW = resetW + padX * 2
        val localPauseW = pauseW + padX * 2
        val localBtnH = Constants.TEXT_HEIGHT + padY * 2 + 1
        val gap = 4
        btnX = x
        btnY = y + (localBtnY * sc).toInt()
        btnW = (localResetW * sc).toInt()
        btnH = (localBtnH * sc).toInt()
        val localPauseX = localResetW + gap
        pauseBtnX = x + (localPauseX * sc).toInt()
        pauseBtnY = btnY
        pauseBtnW = (localPauseW * sc).toInt()
        pauseBtnH = btnH
        val resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        val pauseHover = mouseX >= pauseBtnX && mouseX <= pauseBtnX + pauseBtnW && mouseY >= pauseBtnY && mouseY <= pauseBtnY + pauseBtnH
        val shownReset = if (resetHover) "§c§l[ Reset ]" else resetLabel
        val shownPause = if (pauseHover) (if (paused) "§a§l[ Resume ]" else "§e§l[ Pause ]") else pauseLabel

        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in lines.indices)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.text(mc.font, shownReset, padX, localBtnY + padY, 0xFFFFFFFF.toInt(), true)
        ctx.text(mc.font, shownPause, localPauseX + padX, localBtnY + padY, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
        btnVisible = true
    }

    /** Returns true if click landed on the reset button (consume the click). */
    @JvmStatic
    fun handleScreenClick(mx: Double, my: Double): Boolean {
        if (!btnVisible) return false
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            reset()
            return true
        }
        if (mx >= pauseBtnX && mx <= pauseBtnX + pauseBtnW && my >= pauseBtnY && my <= pauseBtnY + pauseBtnH) {
            val now = System.currentTimeMillis()
            if (!paused) {
                paused = true
                autoPaused = false
                pauseStartedMs = now
            } else {
                if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs)
                pauseStartedMs = 0
                paused = false
                autoPaused = false
                lastActivityMs = now
            }
            save()
            return true
        }
        return false
    }
}

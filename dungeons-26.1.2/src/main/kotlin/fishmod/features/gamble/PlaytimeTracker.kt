package fishmod.features.gamble

import com.google.gson.JsonObject
import fishmod.compat.curScreen
import fishmod.utils.FishMsg
import fishmod.utils.SkyblockLocation
import net.minecraft.client.Minecraft

// 5 min windows of input counts for POST /heartbeat; counts only, nothing is read from or sent to Hypixel
object PlaytimeTracker {

    @Volatile var lastBalance = -1L

    private const val WINDOW_MS = 5 * 60_000L
    private const val MINUTE_MS = 60_000L

    private var windowStart = 0L
    private var moves = 0
    private var clicks = 0
    private var blocks = 0.0
    private var activeMinutes = 0
    private var lastActiveMinute = -1
    private var onSkyblock = true

    private var hasLast = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var keysDown = BooleanArray(0)

    private fun reset(now: Long) {
        windowStart = now; moves = 0; clicks = 0; blocks = 0.0
        activeMinutes = 0; lastActiveMinute = -1; onSkyblock = true
    }

    fun onTick(mc: Minecraft) {
        val p = mc.player
        if (p == null) { windowStart = 0L; hasLast = false; return }
        if (!GambleSettings.gambleEnabled || !GambleSettings.gamblePlaytime) { windowStart = 0L; return }
        val now = System.currentTimeMillis()
        if (windowStart == 0L) reset(now)
        if (!SkyblockLocation.inSkyblock()) onSkyblock = false

        var active = false
        val yaw = p.yRot
        val pitch = p.xRot
        if (hasLast) {
            val dx = p.x - lastX
            val dz = p.z - lastZ
            val dy = p.y - lastY
            val dist = Math.sqrt(dx * dx + dz * dz)
            val moved = dist > 0.01 || Math.abs(dy) > 0.01
            val turned = yaw != lastYaw || pitch != lastPitch
            if (moved || turned) { moves++; active = true }
            // Teleports and warps are not walking
            if (dist in 0.01..8.0 && !p.isPassenger) blocks += dist
        }
        hasLast = true
        lastX = p.x; lastY = p.y; lastZ = p.z; lastYaw = yaw; lastPitch = pitch

        if (mc.curScreen == null) {
            val maps = mc.options.keyMappings
            if (keysDown.size != maps.size) keysDown = BooleanArray(maps.size)
            for (i in maps.indices) {
                val d = maps[i].isDown
                if (d && !keysDown[i]) { clicks++; active = true }
                keysDown[i] = d
            }
        }

        if (active) {
            val minute = ((now - windowStart) / MINUTE_MS).toInt()
            if (minute != lastActiveMinute) { lastActiveMinute = minute; activeMinutes++ }
        }
        if (now - windowStart >= WINDOW_MS) flush(now)
    }

    private fun flush(now: Long) {
        val body = JsonObject().apply {
            addProperty("windowStartMs", windowStart)
            addProperty("moves", moves)
            addProperty("clicks", clicks)
            addProperty("blocks", blocks.toInt())
            addProperty("activeMinutes", activeMinutes)
            addProperty("onSkyblock", onSkyblock)
        }
        reset(now)
        GambleApi.call("POST", "/heartbeat", body) { res ->
            val o = res?.json ?: return@call
            if (!GambleSettings.gambleShowPoints) return@call
            val bal = o.get("balance")?.let { runCatching { it.asLong }.getOrNull() }
            val prev = lastBalance
            if (bal != null) lastBalance = bal
            // Worker sends no amount: awarded = balance change since the last heartbeat
            val awarded = if (bal != null && prev >= 0) bal - prev else if (o.get("credited")?.asBoolean == true) 5L else 0L
            if (awarded <= 0) return@call
            Minecraft.getInstance().execute {
                FishMsg.send("§a+%,d gamble points".format(awarded) + (if (bal != null) " §7(balance §e%,d§7)".format(bal) else ""))
            }
        }
    }
}

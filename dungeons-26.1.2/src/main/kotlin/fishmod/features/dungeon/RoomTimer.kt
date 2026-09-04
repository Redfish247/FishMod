package fishmod.features.dungeon

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.DungeonState
import fishmod.features.dungeon.map.Room
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * On-screen "Cleared (time)" / "Secrets done (time)" title when the room you're standing in gets a
 * green / white checkmark, timed from when you entered it. Optionally shows the time value and a
 * personal best per room name (persisted to `config/fishmod/room_timers.json`).
 */
object RoomTimer {

    private val FILE: Path = Paths.get("config/fishmod/room_timers.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private class Pb {
        @JvmField var clear: Long = Long.MAX_VALUE
        @JvmField var secrets: Long = Long.MAX_VALUE
    }

    private val pbs = HashMap<String, Pb>()
    private var loaded = false

    private var room: String? = null
    private var enterMs = 0L
    private var toldClear = false
    private var toldSecrets = false

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { room = null; toldClear = false; toldSecrets = false; false }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            if (!FishSettings.roomTimerEnabled || !DungeonState.isInDungeon() || DungeonState.isInBoss()) return@EndTick
            val name = DungeonMap.roomPlayerIn()?.owner?.data?.name ?: return@EndTick
            if (name != room) {
                room = name
                enterMs = System.currentTimeMillis()
                toldClear = false
                toldSecrets = false
            }
        })

        DungeonMap.onRoomStateChange { u ->
            if (!FishSettings.roomTimerEnabled) return@onRoomStateChange
            val here = DungeonMap.roomPlayerIn()?.owner ?: return@onRoomStateChange
            if (u.room !== here) return@onRoomStateChange
            val name = here.data?.name ?: return@onRoomStateChange
            if (name != room) return@onRoomStateChange
            val took = System.currentTimeMillis() - enterMs
            val secrets = here.data?.secrets ?: 0

            // Green check = every secret found; white check = room fully cleared.
            if (!toldSecrets && u.neu == Room.State.GREEN && secrets > 0) {
                toldSecrets = true
                if (FishSettings.roomTimerSecrets) announce("§bSecrets done", took, name, isSecrets = true)
            }
            if (!toldClear && u.neu == Room.State.CLEARED) {
                toldClear = true
                if (FishSettings.roomTimerClear) announce("§aCleared", took, name, isSecrets = false)
            }
        }
    }

    private fun announce(label: String, tookMs: Long, roomName: String, isSecrets: Boolean) {
        ensureLoaded()
        val p = pbs.getOrPut(roomName) { Pb() }
        val prev = if (isSecrets) p.secrets else p.clear
        val beat = tookMs < prev
        if (beat) {
            if (isSecrets) p.secrets = tookMs else p.clear = tookMs
            save()
        }

        val sb = StringBuilder(label)
        if (FishSettings.roomTimerShowTime) sb.append(" §7(§f").append(fmt(tookMs)).append("§7)")
        if (FishSettings.roomTimerPb) {
            if (beat && prev != Long.MAX_VALUE) sb.append(" §6§lPB!")
            else if (!beat && prev != Long.MAX_VALUE) sb.append(" §8[PB ").append(fmt(prev)).append("]")
        }
        Misc.forceTitle(Component.literal(sb.toString()), Component.empty(), 1500)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000.0
        return if (s < 60.0) "%.1fs".format(s)
        else "%d:%02d".format((s / 60).toInt(), (s % 60).toInt())
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (Files.exists(FILE)) {
                val t = object : TypeToken<HashMap<String, Pb>>() {}.type
                GSON.fromJson<HashMap<String, Pb>>(Files.readString(FILE), t)?.let { pbs.putAll(it) }
            }
        } catch (e: Exception) {
            Debug.LOGGER.warn("[RoomTimer] load failed: {}", e.toString())
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            Files.writeString(FILE, GSON.toJson(pbs))
        } catch (e: Exception) {
            Debug.LOGGER.warn("[RoomTimer] save failed: {}", e.toString())
        }
    }
}

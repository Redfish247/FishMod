package fishmod.features.dungeon.puzzles

import fishmod.features.dungeon.map.MapVec2i
import fishmod.features.dungeon.map.Room
import fishmod.features.dungeon.map.Scan
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Shared detection + lifecycle for [PuzzleSolver]s. Resolves which puzzle room the player is
 * standing in (via [Scan]'s room grid) and forwards tick / chat / render / reset to that room's
 * registered solver only — so a solver never has to re-implement "am I in my room?".
 *
 * Concrete solvers register in [init] and are gated behind [FishSettings.puzzleSolversEnabled].
 */
object PuzzleSolvers {

    private val byRoom = HashMap<String, PuzzleSolver>()
    private var active: PuzzleSolver? = null
    private var activeRoom: Room? = null
    private var tickAcc = 0
    private val COLOR = Regex("§.")

    fun register(vararg solvers: PuzzleSolver) {
        for (s in solvers) byRoom[s.roomName] = s
    }

    private fun enabled(): Boolean = FishSettings.puzzleSolversEnabled && Location.inDungeon()

    @JvmStatic
    fun init() {
        // register(...) concrete solvers here as they land (Phase 3).

        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }

        Events.ON_GAME_MESSAGE.register { text ->
            val a = active
            if (a == null || !enabled()) false else a.onChat(COLOR.replace(text.string, ""))
        }

        Events.ON_WORLD_CHANGE.register {
            clearActive()
            byRoom.values.forEach { it.reset() }
            false
        }

        RenderingEvents.FILLED_BLOCK.register { _, matrices, vc ->
            if (enabled()) active?.renderWorld(matrices, vc)
        }
    }

    private fun currentRoom(mc: Minecraft): Room? {
        val p = mc.player ?: return null
        return Scan.roomsList.getOrNull(MapVec2i(p.blockX, p.blockZ).index())?.owner
    }

    private fun clearActive() {
        active?.onExit()
        active = null
        activeRoom = null
    }

    private fun tick(mc: Minecraft) {
        if (!enabled()) {
            if (active != null) clearActive()
            return
        }
        if (++tickAcc >= 5) {
            tickAcc = 0
            val room = currentRoom(mc)
            if (room !== activeRoom) {
                active?.onExit()
                activeRoom = room
                active = if (room?.type == Room.Type.PUZZLE) room.data?.name?.let { byRoom[it] } else null
                if (active != null && room != null) active!!.onEnter(room)
            }
        }
        active?.onTick(mc)
    }
}

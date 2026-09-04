package fishmod.features.dungeon.puzzles

import fishmod.features.dungeon.puzzles.odin.BeamsSolver
import fishmod.features.dungeon.puzzles.odin.BlazeSolver
import fishmod.features.dungeon.puzzles.odin.BoulderSolver
import fishmod.features.dungeon.puzzles.odin.IceFillSolver
import fishmod.features.dungeon.puzzles.odin.ORoomType
import fishmod.features.dungeon.puzzles.odin.OdinScan
import fishmod.features.dungeon.puzzles.odin.QuizSolver
import fishmod.features.dungeon.puzzles.odin.TPMazeSolver
import fishmod.features.dungeon.puzzles.odin.WaterSolver
import fishmod.features.dungeon.puzzles.odin.WeirdosSolver
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

/**
 * Dispatcher for the puzzle solvers: owns the event wiring and forwards to the individual solver
 * objects in [fishmod.features.dungeon.puzzles.odin].
 *
 * TicTacToe keeps its own solver in [TicTacToeSolver].
 */
object PuzzleSolvers {

    private val weirdosRegex = Regex("\\[NPC] (.+): (.+).?")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private var tickAcc = 0

    private val enabled: Boolean get() = FishSettings.puzzleSolversEnabled
    private val inClear: Boolean get() = Location.inDungeon() && !Phase.inBoss()
    private val isInPuzzle: Boolean get() = OdinScan.currentRoom?.data?.type == ORoomType.PUZZLE

    @JvmStatic
    fun init() {
        OdinScan.init()

        OdinScan.onRoomEnter { room ->
            BoulderSolver.onRoomEnter(room)
            IceFillSolver.onRoomEnter(room, FishSettings.iceFillOptimized)
            TPMazeSolver.onRoomEnter(room)
            BeamsSolver.onRoomEnter(room)
            QuizSolver.onRoomEnter(room)
            WaterSolver.onRoomEnter(room)
        }

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (!enabled) return@register
            if (isInPuzzle && ++tickAcc >= 10) {
                tickAcc = 0
                if (FishSettings.blazeSolver) BlazeSolver.getBlaze()
                if (FishSettings.waterSolver) WaterSolver.onTick()
            }
            if (!inClear) return@register
            if (FishSettings.beamsSolver) BeamsSolver.onTick()
            if (FishSettings.boulderSolver) BoulderSolver.onTick()
            if (FishSettings.iceFillSolver) IceFillSolver.onTick(FishSettings.iceFillOptimized)
            if (FishSettings.tttSolver) TicTacToeSolver.onTick()
        }

        Events.ON_SERVER_TICK.register {
            if (inClear && FishSettings.waterSolver) WaterSolver.onServerTick()
            false
        }

        Events.ON_WORLD_CHANGE.register {
            IceFillSolver.reset(); WeirdosSolver.reset(); BoulderSolver.reset(); TPMazeSolver.reset()
            WaterSolver.reset(); BlazeSolver.reset(); BeamsSolver.reset(); QuizSolver.reset()
            TicTacToeSolver.reset()
            false
        }

        Events.ON_PACKET.register { packet ->
            if (enabled && isInPuzzle && FishSettings.tpMazeSolver && packet is ClientboundPlayerPositionPacket) {
                Minecraft.getInstance().execute { TPMazeSolver.tpPacket(packet) }
            }
            false
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (enabled && inClear && hand == InteractionHand.MAIN_HAND) {
                if (FishSettings.tttSolver && TicTacToeSolver.shouldBlock(hit.blockPos)) {
                    return@UseBlockCallback InteractionResult.FAIL
                }
                if (FishSettings.waterSolver) WaterSolver.waterInteract(hit.blockPos)
                if (FishSettings.boulderSolver) BoulderSolver.playerInteract(hit.blockPos)
            }
            InteractionResult.PASS
        })

        Events.ON_GAME_MESSAGE.register { text ->
            // Quiz/Weirdos are chat-driven and self-validating; don't gate on isInPuzzle, or the
            // first Oruo question (which fires the instant you step in, before the room resolves)
            // gets dropped.
            if (!enabled || !Location.inDungeon()) return@register false
            val msg = COLOR.replace(text.string, "")
            if (FishSettings.weirdosSolver) weirdosRegex.find(msg)?.let {
                WeirdosSolver.onNPCMessage(it.groupValues[1], it.groupValues[2])
            }
            if (FishSettings.quizSolver) QuizSolver.onMessage(msg)
            false
        }

        RenderingEvents.GIZMO.register { _ ->
            if (!enabled || !inClear) return@register
            if (FishSettings.iceFillSolver) IceFillSolver.onRenderWorld()
            if (FishSettings.weirdosSolver) WeirdosSolver.onRenderWorld()
            if (FishSettings.boulderSolver) BoulderSolver.onRenderWorld()
            if (FishSettings.blazeSolver) BlazeSolver.onRenderWorld()
            if (FishSettings.beamsSolver) BeamsSolver.onRenderWorld()
            if (FishSettings.waterSolver) WaterSolver.onRenderWorld()
            if (FishSettings.quizSolver) QuizSolver.onRenderWorld()
            if (FishSettings.tpMazeSolver) TPMazeSolver.onRenderWorld()
            if (FishSettings.tttSolver) TicTacToeSolver.onRenderWorld()
        }
    }

    /** Solvers call this on completion (currently just a chat line). */
    @JvmStatic
    fun onPuzzleComplete(puzzleName: String) {
        fishmod.utils.Misc.addChatMessage(
            net.minecraft.network.chat.Component.literal("§b[Puzzle] §a$puzzleName solved"))
    }
}

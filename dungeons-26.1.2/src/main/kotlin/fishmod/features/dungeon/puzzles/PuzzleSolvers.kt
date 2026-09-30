package fishmod.features.dungeon.puzzles

import fishmod.utils.debug.FishDiag
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
            FishDiag.guard("PuzzleSolvers.1", "boulder room enter failed") { BoulderSolver.onRoomEnter(room) }
            FishDiag.guard("PuzzleSolvers.2", "ice fill room enter failed") { IceFillSolver.onRoomEnter(room, FishSettings.iceFillOptimized) }
            FishDiag.guard("PuzzleSolvers.3", "tp maze room enter failed") { TPMazeSolver.onRoomEnter(room) }
            FishDiag.guard("PuzzleSolvers.4", "beams room enter failed") { BeamsSolver.onRoomEnter(room) }
            FishDiag.guard("PuzzleSolvers.5", "water board room enter failed") { WaterSolver.onRoomEnter(room) }
        }

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (!enabled) return@register
            if (isInPuzzle && ++tickAcc >= 10) {
                tickAcc = 0
                if (FishSettings.blazeSolver) FishDiag.guard("PuzzleSolvers.6", "blaze solver tick failed") { BlazeSolver.getBlaze() }
                if (FishSettings.waterSolver) FishDiag.guard("PuzzleSolvers.7", "water solver tick failed") { WaterSolver.onTick() }
            }
            if (!inClear) return@register
            if (FishSettings.beamsSolver) FishDiag.guard("PuzzleSolvers.8", "beams solver tick failed") { BeamsSolver.onTick() }
            if (FishSettings.boulderSolver) FishDiag.guard("PuzzleSolvers.9", "boulder solver tick failed") { BoulderSolver.onTick() }
            if (FishSettings.iceFillSolver) FishDiag.guard("PuzzleSolvers.10", "ice fill solver tick failed") { IceFillSolver.onTick(FishSettings.iceFillOptimized) }
            if (FishSettings.tttSolver) FishDiag.guard("PuzzleSolvers.11", "tic tac toe solver tick failed") { TicTacToeSolver.onTick() }
        }

        Events.ON_SERVER_TICK.register {
            if (inClear && FishSettings.waterSolver) FishDiag.guard("PuzzleSolvers.12", "water solver server tick failed") { WaterSolver.onServerTick() }
            false
        }

        Events.ON_WORLD_CHANGE.register {
            FishDiag.guard("PuzzleSolvers.13", "puzzle solver reset on world change failed") {
                IceFillSolver.reset(); WeirdosSolver.reset(); BoulderSolver.reset(); TPMazeSolver.reset()
                WaterSolver.reset(); BlazeSolver.reset(); BeamsSolver.reset(); QuizSolver.reset()
                TicTacToeSolver.reset()
            }
            false
        }

        Events.ON_PACKET.register { packet ->
            if (enabled && isInPuzzle && FishSettings.tpMazeSolver && packet is ClientboundPlayerPositionPacket) {
                val from = Minecraft.getInstance().player?.position()
                Minecraft.getInstance().execute { FishDiag.guard("PuzzleSolvers.14", "tp maze teleport handling failed") { TPMazeSolver.tpPacket(packet, from) } }
            }
            false
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (enabled && inClear && hand == InteractionHand.MAIN_HAND) {
                if (FishSettings.tttSolver && FishDiag.guard("PuzzleSolvers.15", "tic tac toe click check failed") { TicTacToeSolver.shouldBlock(hit.blockPos) } == true) {
                    return@UseBlockCallback InteractionResult.FAIL
                }
                if (FishSettings.waterSolver) FishDiag.guard("PuzzleSolvers.16", "water lever interact failed at ${hit.blockPos}") { WaterSolver.waterInteract(hit.blockPos) }
                if (FishSettings.boulderSolver) FishDiag.guard("PuzzleSolvers.17", "boulder interact failed at ${hit.blockPos}") { BoulderSolver.playerInteract(hit.blockPos) }
            }
            InteractionResult.PASS
        })

        Events.ON_GAME_MESSAGE.register { text ->
            if (!enabled || !Location.inDungeon()) return@register false
            val msg = COLOR.replace(text.string, "")
            if (FishSettings.weirdosSolver) weirdosRegex.find(msg)?.let {
                FishDiag.guard("PuzzleSolvers.18", "weirdos NPC message handling failed '$msg'") { WeirdosSolver.onNPCMessage(it.groupValues[1], it.groupValues[2]) }
            }
            if (FishSettings.quizSolver) FishDiag.guard("PuzzleSolvers.19", "quiz message handling failed '$msg'") { QuizSolver.onMessage(msg) }
            if (msg.contains("killed a Blaze in the wrong order")) FishDiag.guard("PuzzleSolvers.20", "blaze fail handling failed") { BlazeSolver.onFail() }
            false
        }

        RenderingEvents.GIZMO.register { _ ->
            if (!enabled || !inClear) return@register
            if (FishSettings.iceFillSolver) FishDiag.guard("PuzzleSolvers.21", "ice fill render failed") { IceFillSolver.onRenderWorld() }
            if (FishSettings.weirdosSolver) FishDiag.guard("PuzzleSolvers.22", "weirdos render failed") { WeirdosSolver.onRenderWorld() }
            if (FishSettings.boulderSolver) FishDiag.guard("PuzzleSolvers.23", "boulder render failed") { BoulderSolver.onRenderWorld() }
            if (FishSettings.blazeSolver) FishDiag.guard("PuzzleSolvers.24", "blaze render failed") { BlazeSolver.onRenderWorld() }
            if (FishSettings.beamsSolver) FishDiag.guard("PuzzleSolvers.25", "beams render failed") { BeamsSolver.onRenderWorld() }
            if (FishSettings.waterSolver) FishDiag.guard("PuzzleSolvers.26", "water board render failed") { WaterSolver.onRenderWorld() }
            if (FishSettings.quizSolver) FishDiag.guard("PuzzleSolvers.27", "quiz render failed") { QuizSolver.onRenderWorld() }
            if (FishSettings.tpMazeSolver) FishDiag.guard("PuzzleSolvers.28", "tp maze render failed") { TPMazeSolver.onRenderWorld() }
            if (FishSettings.tttSolver) FishDiag.guard("PuzzleSolvers.29", "tic tac toe render failed") { TicTacToeSolver.onRenderWorld() }
        }
    }

    @JvmStatic
    fun onPuzzleComplete(puzzleName: String) {
        fishmod.utils.Misc.addChatMessage(
            net.minecraft.network.chat.Component.literal("§b[Puzzle] §a$puzzleName solved"))
    }
}

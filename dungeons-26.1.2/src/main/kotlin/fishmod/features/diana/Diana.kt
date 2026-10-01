package fishmod.features.diana

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.utils.FishMsg
import fishmod.utils.Location
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult

// Shared Diana state: hub/spade gating, block clicks, init of all Diana parts
object Diana {

    private val SPADES = setOf("ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE")

    // -Dfishmod.dianaTest=true treats any world as the hub (singleplayer testing)
    @JvmField val testMode: Boolean = java.lang.Boolean.getBoolean("fishmod.dianaTest")

    @JvmStatic var hasSpade = false; private set
    @JvmStatic var holdingSpade = false; private set
    private var holdStartMs = 0L
    private var lastHoldMs = 0L
    private var tick = 0

    val recentClicks = HashMap<BlockPos, Long>()
    var lastClickedWaypoint: BlockPos? = null

    @JvmStatic fun inHub(): Boolean = testMode || Location.`in`(Location.HUB)

    @JvmStatic fun active(): Boolean = inHub() && hasSpade

    // Held a spade continuously for at least ms (200ms grace after switching away)
    fun heldSpadeFor(ms: Long): Boolean {
        val now = System.currentTimeMillis()
        return holdStartMs != 0L && now - lastHoldMs < 200 && now - holdStartMs >= ms
    }

    fun clickedRecently(pos: BlockPos): Boolean =
        recentClicks[pos]?.let { System.currentTimeMillis() - it < 4000 } ?: false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val p = mc.player ?: return@register
            if (tick++ % 20 == 0) hasSpade = p.inventory.nonEquipmentItems.any { ItemUtil.getId(it) in SPADES }
            holdingSpade = ItemUtil.getId(p.mainHandItem)?.contains("SPADE") == true
            val now = System.currentTimeMillis()
            if (holdingSpade) {
                if (now - lastHoldMs > 200) holdStartMs = now
                lastHoldMs = now
            }
            if (tick % 100 == 0) recentClicks.entries.removeIf { now - it.value > 4000 }
        }
        AttackBlockCallback.EVENT.register(AttackBlockCallback { _, level, _, pos, _ ->
            if (level.isClientSide && inHub()) onBlockClick(pos)
            InteractionResult.PASS
        })
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, hit ->
            if (level.isClientSide && inHub()) {
                val name = player.getItemInHand(hand).hoverName.string
                if (name.contains("Spade")) {
                    onBlockClick(hit.blockPos)
                    SpadeGuess.onSpadeUse()
                }
            }
            InteractionResult.PASS
        })
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            if (level.isClientSide && inHub() && player.getItemInHand(hand).hoverName.string.contains("Spade")) SpadeGuess.onSpadeUse()
            InteractionResult.PASS
        })
        Events.ON_WORLD_CHANGE.register {
            recentClicks.clear(); lastClickedWaypoint = null
            false
        }

        DianaTitles.init()
        DianaWaypoints.init()
        BurrowDetector.init()
        SpadeGuess.init()
        ArrowGuess.init()
        DianaWarp.init()
        RareMobs.init()
        DianaTracker.init()
        SphinxSolver.init()
        CrownOfAvarice.init()
        if (testMode) DianaTest.init()
    }

    fun onBlockClick(pos: BlockPos) {
        recentClicks[pos] = System.currentTimeMillis()
        val wp = DianaWaypoints.findDiggable(pos) ?: return
        wp.clicked = true
        lastClickedWaypoint = wp.pos
    }

    fun player() = Minecraft.getInstance().player

    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> {
        fun run(f: () -> Unit) = Command<FabricClientCommandSource> { f(); 1 }
        return ClientCommands.literal("diana")
            .then(ClientCommands.literal("clear").executes(run { DianaWaypoints.clearAll(); FishMsg.send("§aDiana waypoints cleared.") }))
            .then(ClientCommands.literal("resetsession").executes(run { DianaTracker.resetSession() }))
    }
}

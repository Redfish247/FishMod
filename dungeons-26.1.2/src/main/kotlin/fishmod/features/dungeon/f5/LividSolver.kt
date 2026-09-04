package fishmod.features.dungeon.f5

import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Livid Solver. In the F5/M5 boss room a wool block at a fixed position takes the colour of the
 * real Livid; read it, find the "<Name> Livid" entity and box it.
 */
object LividSolver {

    private val LIVID_START = Regex(
        "^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$")
    private val WOOL_POS = BlockPos(5, 108, 43)

    private enum class Livid(val entityName: String, val colorCode: Char, val wool: Block) {
        VENDETTA("Vendetta", 'f', Blocks.WHITE_WOOL),
        CROSSED("Crossed", 'd', Blocks.MAGENTA_WOOL),
        ARCADE("Arcade", 'e', Blocks.YELLOW_WOOL),
        SMILE("Smile", 'a', Blocks.LIME_WOOL),
        DOCTOR("Doctor", '7', Blocks.GRAY_WOOL),
        PURPLE("Purple", '5', Blocks.PURPLE_WOOL),
        SCREAM("Scream", '9', Blocks.BLUE_WOOL),
        FROG("Frog", '2', Blocks.GREEN_WOOL),
        HOCKEY("Hockey", 'c', Blocks.RED_WOOL);

        var entity: Player? = null
    }

    private var current = Livid.HOCKEY
    private var invulnTime = 0

    private fun active(): Boolean =
        FishSettings.lividSolverEnabled && DungeonState.isInBoss() && DungeonState.floorNumber() == 5

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (DungeonState.floorNumber() == 5 && LIVID_START.matches(text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")))
                invulnTime = 390
            false
        }

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket ->
                    Minecraft.getInstance().execute { onBlock(packet.pos, packet.blockState) }
                is ClientboundSectionBlocksUpdatePacket ->
                    Minecraft.getInstance().execute { packet.runUpdates(::onBlock) }
                is ClientboundSetEntityDataPacket ->
                    Minecraft.getInstance().execute { bindEntity(packet.id) }
            }
            false
        }

        Events.ON_SERVER_TICK.register {
            if (active() && invulnTime > 0) invulnTime--
            false
        }

        Events.ON_WORLD_CHANGE.register {
            current = Livid.HOCKEY
            Livid.entries.forEach { it.entity = null }
            invulnTime = 0
            false
        }

        RenderingEvents.GIZMO.register { _ ->
            if (!active()) return@register
            if (Minecraft.getInstance().player?.getEffect(MobEffects.BLINDNESS) != null) return@register
            current.entity?.let { RenderUtils.gizmoBox(it.boundingBox, 0, FishSettings.lividSolverColor) }
        }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!active() || pos != WOOL_POS) return
        val hit = Livid.entries.find { it.wool == state.block } ?: return
        if (hit != current) {
            current = hit
            Misc.addChatMessage(Component.literal("§b[Livid] §7real Livid: §${hit.colorCode}${hit.entityName}"))
        }
    }

    private fun bindEntity(id: Int) {
        if (!active()) return
        val e = Minecraft.getInstance().level?.getEntity(id) as? Player ?: return
        if (e.name.string == "${current.entityName} Livid") current.entity = e
    }
}

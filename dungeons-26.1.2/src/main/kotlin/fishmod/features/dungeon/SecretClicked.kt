package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Secret Clicked: a box + chime when you trigger a dungeon secret.
 * Three trigger routes, all handled here:
 *  - **interact**: right-click a chest / lever / skull ([UseBlockCallback]).
 *  - **bat kill**: a dungeon secret bat you were next to gets removed ([Bat] + [ClientboundRemoveEntitiesPacket]).
 *  - **item pickup**: you walk over a ground item ([ClientboundTakeItemEntityPacket] for your own player).
 * Bat / item routes are gated by [FishSettings.secretClickedBats] / [FishSettings.secretClickedItems].
 */
object SecretClicked {

    /** [blockPos] non-null → recompute the box from live block shape each frame; null → fixed [box]. */
    private class Secret(val box: AABB, val blockPos: BlockPos?, @JvmField var locked: Boolean = false)

    private const val BAT_RANGE = 6.0    // you can't kill a secret bat from further than melee reach
    private const val ITEM_RANGE = 4.0   // slack over the ~1-block vanilla pickup radius

    private val clicked = CopyOnWriteArrayList<Secret>()
    private var lastChime = 0L
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    // entityId -> last seen world position, refreshed each client tick (main thread).
    private val batPos = HashMap<Int, Vec3>()
    private val itemPos = HashMap<Int, Vec3>()   // non-arrow ground items only
    // bats we've actually damaged (hurtTime/deathTime seen ticking); only engaged bats chime
    private val batEngaged = HashSet<Int>()

    // Filled from ON_PACKET (netty thread), drained on the next client tick.
    private val pickedItemIds = ConcurrentLinkedQueue<Int>()
    private val removedIds = ConcurrentLinkedQueue<Int>()
    @Volatile private var selfId = -1

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) onInteract(hit.blockPos)
            InteractionResult.PASS
        })

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundTakeItemEntityPacket -> if (packet.playerId == selfId) pickedItemIds.add(packet.itemId)
                is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach { removedIds.add(it) }
            }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })

        Events.ON_GAME_MESSAGE.register { text ->
            if (COLOR.replace(text.string, "").trim() == "That chest is locked!") {
                clicked.lastOrNull()?.locked = true
            }
            false
        }
        Events.ON_WORLD_CHANGE.register {
            clicked.clear(); batPos.clear(); itemPos.clear(); batEngaged.clear()
            pickedItemIds.clear(); removedIds.clear()
            false
        }

        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.secretClickedDepthCheck) renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.secretClickedDepthCheck) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.secretClickedDepthCheck) render(m, vc, fill = false) }
    }

    private fun onTick(mc: net.minecraft.client.Minecraft) {
        val player = mc.player
        selfId = player?.id ?: -1
        val level = mc.level
        if (player == null || level == null || !active()) {
            pickedItemIds.clear(); removedIds.clear()
            return
        }
        val eye = player.eyePosition

        // 1. item pickups by us: a stationary ground item we walked over is a secret (coins/drops stay airborne, skipped)
        while (true) {
            val id = pickedItemIds.poll() ?: break
            if (!FishSettings.secretClickedItems) continue
            val pos = itemPos[id]
                ?: (level.getEntity(id) as? ItemEntity)?.takeIf { it.isFloorSecret() }?.position()
                ?: continue
            if (pos.distanceToSqr(eye) <= ITEM_RANGE * ITEM_RANGE) addLooseSecret(pos)
        }

        // 2. Bat kills — a bat we damaged and were standing next to that just got removed.
        while (true) {
            val id = removedIds.poll() ?: break
            if (!FishSettings.secretClickedBats) continue
            val engaged = batEngaged.remove(id)
            val pos = batPos[id] ?: continue
            if (engaged && pos.distanceToSqr(eye) <= BAT_RANGE * BAT_RANGE) addLooseSecret(pos)
        }

        // 3. Refresh position tracking for anything still in range.
        batPos.clear(); itemPos.clear()
        val scan = player.boundingBox.inflate(BAT_RANGE + 2.0)
        for (e in level.getEntities(player, scan)) {
            when (e) {
                is Bat -> {
                    batPos[e.id] = e.position()
                    if (e.hurtTime > 0 || e.deathTime > 0) batEngaged.add(e.id)
                }
                is ItemEntity -> if (e.isFloorSecret()) itemPos[e.id] = e.position()
            }
        }
    }

    /** A secret item lies still on the floor; a coin/drop is airborne, moving, and pickup-delayed. */
    private fun ItemEntity.isFloorSecret(): Boolean {
        if (item.item == Items.ARROW || hasPickUpDelay() || age < 10) return false
        val d = deltaMovement
        return onGround() && d.horizontalDistanceSqr() < 0.003 && kotlin.math.abs(d.y) < 0.05
    }

    private fun addLooseSecret(pos: Vec3) {
        chime()
        if (!FishSettings.secretClickedBoxes) return
        if (clicked.any { it.blockPos == null && it.box.center.distanceToSqr(pos) < 0.25 }) return
        val box = AABB.ofSize(pos, 0.9, 0.9, 0.9)
        clicked.add(Secret(box, null))
        Scheduler.scheduleTask({ clicked.removeFirstOrNull() }, FishSettings.secretClickedTimeToStay.coerceIn(1, 20) * 20)
    }

    /** (box, fillArgb, strokeArgb) for every secret currently showing. */
    private fun boxes(): List<Triple<AABB, Int, Int>> {
        if (!active() || !FishSettings.secretClickedBoxes || clicked.isEmpty()) return emptyList()
        val level = net.minecraft.client.Minecraft.getInstance().level
        val style = FishSettings.secretClickedStyle
        return clicked.map { s ->
            val argb = if (s.locked) FishSettings.secretClickedLockedColor else FishSettings.secretClickedColor
            val box = if (s.blockPos != null) {
                val state = level?.getBlockState(s.blockPos)
                val shape = state?.getShape(level, s.blockPos)
                val local = if (shape != null && !shape.isEmpty) shape.bounds() else AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                local.move(s.blockPos.x.toDouble(), s.blockPos.y.toDouble(), s.blockPos.z.toDouble()).inflate(0.002)
            } else s.box
            val fillA = minOf(argb ushr 24, 0x60)
            val fill = if (style != "Outline") (fillA shl 24) or (argb and 0xFFFFFF) else 0
            val stroke = if (style != "Filled") (0xFF shl 24) or (argb and 0xFFFFFF) else 0
            Triple(box, fill, stroke)
        }
    }

    private fun renderGizmo() {
        for ((box, fill, stroke) in boxes()) RenderUtils.gizmoBox(box, fill, stroke)
    }

    private fun active(): Boolean =
        FishSettings.secretClickedEnabled && Location.inDungeon() &&
            (FishSettings.secretClickedInBoss || !Phase.inBoss())

    private fun onInteract(pos: BlockPos) {
        if (!active()) return
        val mc = net.minecraft.client.Minecraft.getInstance()
        val block = mc.level?.getBlockState(pos)?.block ?: return
        if (block !is ChestBlock && block !is LeverBlock && block !is AbstractSkullBlock) return
        chime()
        if (!FishSettings.secretClickedBoxes || clicked.any { it.blockPos == pos }) return
        val box = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        clicked.add(Secret(box, pos.immutable()))
        Scheduler.scheduleTask({ clicked.removeFirstOrNull() }, FishSettings.secretClickedTimeToStay.coerceIn(1, 20) * 20)
    }

    private fun chime() {
        if (!FishSettings.secretClickedChime) return
        if (Phase.inBoss() && !FishSettings.secretClickedChimeInBoss) return
        val now = System.currentTimeMillis()
        if (now - lastChime <= 10) return
        lastChime = now
        SoundManager.play(
            SoundManager.preset(FishSettings.secretClickedSoundName),
            FishSettings.secretClickedVolume.coerceIn(0, 500) / 100f,
            FishSettings.secretClickedPitch.toFloat().coerceIn(0f, 2f),
            "secretChime", 0
        )
    }

    // box the block's real shape, not a full cube; never mix fill/line topologies on one VertexConsumer
    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        for ((box, fillArgb, strokeArgb) in boxes()) {
            if (fill) {
                if ((fillArgb ushr 24) != 0) RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(fillArgb))
            } else {
                if ((strokeArgb ushr 24) != 0) RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(strokeArgb))
            }
        }
    }
}
